#!/usr/bin/env bash
# End-to-end test of the server deployment, as CI runs it (mise run server:test): the machine it
# runs on is the server, reached over SSH at 127.0.0.1. It follows deploy/server/README.md with
# its scripts, then checks what the deploy left:
#
#   A   setup-key.sh               a new key, no passphrase
#   B,C setup-server.sh            the deploy user, passwordless sudo, the key (run with sudo here,
#                                  as from the server's console)
#   D1,D2 setup-inventory.sh       the login test, an inventory for 127.0.0.1
#   D3  mise run server:deploy     of this checkout's commit, then:
#       - a second deploy changes nothing (changed=0)
#       - the UI answers through the SSH tunnel the deploy prints
#       - port 8080 listens on 127.0.0.1 only, ufw lets only SSH in
#       - the backup script writes a dump
#       - a deploy of another commit rebuilds and comes up again
#   key-only SSH (setup-server.sh --key-only-ssh): passwords off, the deploy user still logs in
#
# It changes the machine for good (users, sudoers, firewall, Docker, swap): run it only on a
# throwaway one, such as a CI runner or a fresh VM (multipass launch 24.04). It refuses to start
# unless CI=true or SERVER_TEST_THROWAWAY=1. Needs Ubuntu 24.04, sudo without a password, and the
# tools of mise.toml. Takes 20 to 30 minutes: the images are built on the "server".
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
PORT=18080

step() { printf '\n== %s\n' "$*"; }
fail() {
  printf '\nFAILED: %s\n' "$*" >&2
  exit 1
}

[ "${CI:-}" = true ] || [ "${SERVER_TEST_THROWAWAY:-}" = 1 ] \
  || fail "this test changes the machine for good; run it on a throwaway one with SERVER_TEST_THROWAWAY=1 (CI sets CI=true)"
sudo -n true 2> /dev/null || fail "needs sudo without a password"

work=$(mktemp -d)
chmod 755 "$work"
tunnel=""
trap '[ -z "$tunnel" ] || kill "$tunnel" 2> /dev/null || true' EXIT
export DEPLOY_KEY=$work/platform-deploy

# The values in a PLAY RECAP line, e.g. "changed" of "platform : ok=60 changed=0 ...".
recap() { grep -E '^platform +: ' "$1" | tail -n 1 | grep -oE "$2=[0-9]+" | cut -d= -f2; }

deploy() {
  local ref=$1 name=$2
  step "D3: mise run server:deploy ($name, $ref)"
  (cd "$ROOT" && mise run server:deploy -- -i "$work/inventory.ini" \
    -e app_repo="$work/repo.git" -e app_ref="$ref" -e swap_size=1G) 2>&1 | tee "$work/$name.log"
  [ "$(recap "$work/$name.log" failed)" = 0 ] && [ "$(recap "$work/$name.log" unreachable)" = 0 ] \
    || fail "$name: the deploy failed"
}

health() {
  for _ in $(seq 1 30); do
    curl -fsS "$1/actuator/health" 2> /dev/null | grep -q '"status":"UP"' && return 0
    sleep 2
  done
  fail "$1/actuator/health is not UP"
}

# A runner comes with Docker's apt source under another name; the docker role adds its own, and
# two sources with different keys make apt fail. A fresh server has neither.
for f in /etc/apt/sources.list.d/*.list; do
  if [ -e "$f" ] && grep -q download.docker.com "$f"; then
    sudo rm -f "$f"
    echo "removed $f (the runner's Docker source)"
  fi
done

step "A: setup-key.sh"
"$DIR/setup-key.sh" --no-passphrase

step "B, C: setup-server.sh (with sudo, as on the server's console)"
sudo bash "$DIR/setup-server.sh" "$(cat "$DEPLOY_KEY.pub")"
step "B, C again: nothing changes"
sudo bash "$DIR/setup-server.sh" "$(cat "$DEPLOY_KEY.pub")" | tee "$work/setup-again.log"
grep -q "already there" "$work/setup-again.log" || fail "the second run added the key again"
[ "$(sudo grep -c . /home/deploy/.ssh/authorized_keys)" = 1 ] || fail "authorized_keys holds more than one key"

step "D1, D2: setup-inventory.sh"
"$DIR/setup-inventory.sh" 127.0.0.1 --inventory "$work/inventory.ini"
cat "$work/inventory.ini"

# The commit under test, and a second one with the same files, in a repository the server clones
# from: nothing comes from GitHub. The platform user clones a repository another user owns, which
# Git refuses without safe.directory (only on this throwaway machine).
step "The repository the server deploys from"
git init -q --bare "$work/repo.git"
git -C "$ROOT" push -q "$work/repo.git" "HEAD:refs/heads/server-test"
second=$(GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com GIT_COMMITTER_NAME=test \
  GIT_COMMITTER_EMAIL=test@example.com git -C "$ROOT" commit-tree "HEAD^{tree}" -p HEAD -m "server test: another commit")
git -C "$ROOT" push -q "$work/repo.git" "$second:refs/heads/server-test-2"
chmod -R a+rX "$work/repo.git"
sudo git config --system --add safe.directory '*'

deploy server-test first
grep -q -- "-N -L 8080:127.0.0.1:8080 deploy@127.0.0.1" "$work/first.log" || fail "the deploy did not print the tunnel command"

deploy server-test second
[ "$(recap "$work/second.log" changed)" = 0 ] || fail "the second deploy changed something; it must report changed=0"

step "The UI through the SSH tunnel (local port $PORT)"
ssh -i "$DEPLOY_KEY" -o IdentitiesOnly=yes -o ExitOnForwardFailure=yes -N \
  -L "$PORT:127.0.0.1:8080" deploy@127.0.0.1 &
tunnel=$!
health "http://127.0.0.1:$PORT"
curl -fsS -o /dev/null "http://127.0.0.1:$PORT/" || fail "the UI does not load through the tunnel"
kill "$tunnel"
tunnel=""
echo "ok"

step "Only SSH is open"
listen=$(sudo ss -Hltn 'sport = :8080' | awk '{ print $4 }')
echo "8080 listens on: $listen"
[ -n "$listen" ] || fail "nothing listens on 8080"
! grep -qv '^127\.0\.0\.1:8080$' <<< "$listen" || fail "8080 listens on more than 127.0.0.1"
sudo ufw status | tee "$work/ufw.txt"
grep -q '^Status: active' "$work/ufw.txt" || fail "ufw is not active"
! grep ALLOW "$work/ufw.txt" | grep -qv '^22/tcp' || fail "ufw allows more than 22/tcp"

step "Backup"
sudo -u platform /usr/local/bin/trading-analysis-platform-backup
dump=$(sudo sh -c 'ls -t /srv/trading-analysis-platform/backups/*.sql.gz | head -n 1')
sudo zcat "$dump" | grep -q 'PostgreSQL database dump' || fail "$dump is not a database dump"
echo "ok: $dump"

deploy server-test-2 third
[ "$(recap "$work/third.log" changed)" != 0 ] || fail "a deploy of another commit changed nothing"
[ "$(sudo cat /etc/trading-analysis-platform/built-commit)" = "$second" ] || fail "the images were not rebuilt from $second"
health "http://127.0.0.1:8080"
echo "ok: rebuilt from $second and up"

step "Key-only SSH (setup-server.sh --key-only-ssh)"
sudo bash "$DIR/setup-server.sh" "$(cat "$DEPLOY_KEY.pub")" --key-only-ssh
sudo sshd -T | grep -qx 'passwordauthentication no' || fail "password logins are still on"
"$DIR/setup-inventory.sh" 127.0.0.1 --inventory "$work/inventory.ini"

printf '\nAll server deployment checks passed.\n'
