#!/usr/bin/env bash
# Quick test of the setup scripts of deploy/server/README.md (steps 1 to 3), in a throwaway Ubuntu
# 24.04 container that stands in for the server (mise run server:test-setup). This machine is
# "your computer"; nothing outside the container and a temporary folder is changed.
#
#   1    setup-key.sh          a new key, no passphrase, in a temporary folder
#   2    setup-server.sh       in the container, as root (as from a console): the SSH server, the
#                              deploy user, passwordless sudo, the key; run twice, nothing changes
#                              the second time
#   3    setup-inventory.sh    from here: the key login and sudo -n, the inventory
#   tunnel.sh                  reads the inventory and forwards a local port (to the container's
#                              sshd here: there is no platform in the container)
#   key-only SSH               setup-server.sh --key-only-ssh: passwords off, the key still works
#
# The container has no systemd, so systemctl is a no-op there and sshd is started by hand. The
# deploy itself is tested by test-deploy.sh (mise run server:test). Needs Docker or Podman
# (scripts/container-engine.sh); about a minute.
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
NAME=platform-setup-test-$$
PORT=${SERVER_TEST_SSH_PORT:-2222}

step() { printf '\n== %s\n' "$*"; }
fail() {
  printf '\nFAILED: %s\n' "$*" >&2
  exit 1
}

"$ROOT/scripts/container-engine.sh" check > /dev/null \
  || fail "no working container engine: run scripts/container-engine.sh (or mise run setup)"
if [ -n "${CONTAINER_ENGINE:-}" ]; then
  engine=$CONTAINER_ENGINE
elif command -v docker > /dev/null 2>&1; then
  engine=docker
else
  engine=podman
fi

work=$(mktemp -d)
cleanup() {
  "$engine" rm -f "$NAME" > /dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
export DEPLOY_KEY=$work/platform-deploy
# The container's host key is new every run: keep it out of ~/.ssh/known_hosts.
export DEPLOY_KNOWN_HOSTS=$work/known_hosts

in_server() { "$engine" exec "$NAME" "$@"; }
start_sshd() {
  in_server pkill -x sshd > /dev/null 2>&1 || true
  in_server /usr/sbin/sshd
  for _ in $(seq 1 20); do
    ssh-keyscan -p "$PORT" 127.0.0.1 > /dev/null 2>&1 && return 0
    sleep 0.5
  done
  fail "sshd in the container does not answer on port $PORT"
}

step "The server: an Ubuntu 24.04 container on 127.0.0.1:$PORT"
"$engine" run -d --name "$NAME" -p "127.0.0.1:$PORT:22" -v "$DIR:/setup:ro" ubuntu:24.04 sleep infinity > /dev/null
# What a minimal server has: sudo and adduser. No SSH server: setup-server.sh installs it.
in_server bash -c 'apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq sudo adduser procps > /dev/null'
in_server bash -c 'printf "#!/bin/sh\nexit 0\n" > /usr/local/bin/systemctl && chmod +x /usr/local/bin/systemctl'

step "1: setup-key.sh"
"$DIR/setup-key.sh" --no-passphrase
pub=$(cat "$DEPLOY_KEY.pub")

step "2: setup-server.sh, as root in the container"
in_server bash /setup/setup-server.sh "$pub"
in_server mkdir -p /run/sshd
start_sshd

step "2 again: nothing changes"
in_server bash /setup/setup-server.sh "$pub" | tee "$work/again.log"
grep -q "already there" "$work/again.log" || fail "the second run added the key again"
grep -q "in place" "$work/again.log" || fail "the second run rewrote the sudoers file"
[ "$(in_server grep -c . /home/deploy/.ssh/authorized_keys)" = 1 ] || fail "authorized_keys holds more than one key"
[ "$(in_server stat -c %a /home/deploy/.ssh)" = 700 ] || fail "/home/deploy/.ssh is not mode 700"
[ "$(in_server stat -c %a /home/deploy/.ssh/authorized_keys)" = 600 ] || fail "authorized_keys is not mode 600"
[ "$(in_server stat -c %a /etc/sudoers.d/90-deploy)" = 440 ] || fail "the sudoers file is not mode 440"

step "Wrong input is refused"
! in_server bash /setup/setup-server.sh "ssh-ed25519 not-a-key" 2> /dev/null || fail "an invalid key was accepted"
! in_server bash /setup/setup-server.sh 2> /dev/null || fail "a run without a key was accepted"
echo "ok"

step "3: setup-inventory.sh"
"$DIR/setup-inventory.sh" 127.0.0.1 --port "$PORT" --inventory "$work/inventory.ini" \
  2>&1 | tee "$work/inventory.log"
grep -qx "deploy user ok" "$work/inventory.log" || fail "the login test did not pass"
grep -q "^platform ansible_host=127.0.0.1 ansible_user=deploy ansible_ssh_private_key_file=$DEPLOY_KEY ansible_port=$PORT$" \
  "$work/inventory.ini" || fail "the inventory is not as expected: $(cat "$work/inventory.ini")"
# Output first, then grep: `| grep -q` would end the pipe early and fail it under pipefail.
again=$("$DIR/setup-inventory.sh" 127.0.0.1 --port "$PORT" --inventory "$work/inventory.ini")
grep -q "up to date" <<< "$again" || fail "a second run did not keep the inventory: $again"
! "$DIR/setup-inventory.sh" 127.0.0.2 --port "$PORT" --inventory "$work/inventory.ini" > /dev/null 2>&1 \
  || fail "an inventory for another server was overwritten without --force"

step "tunnel.sh: the inventory's server, user, key and port"
tunnel_port=$((PORT + 1))
"$DIR/tunnel.sh" --inventory "$work/inventory.ini" --port "$tunnel_port" --remote-port 22 > "$work/tunnel.log" 2>&1 &
tunnel=$!
for _ in $(seq 1 20); do
  ssh-keyscan -p "$tunnel_port" 127.0.0.1 > /dev/null 2>&1 && break
  sleep 0.5
done
ssh-keyscan -p "$tunnel_port" 127.0.0.1 > /dev/null 2>&1 \
  || fail "nothing answers through the tunnel: $(cat "$work/tunnel.log")"
pkill -P "$tunnel" 2> /dev/null || true
kill "$tunnel" 2> /dev/null || true
grep -q "http://127.0.0.1:$tunnel_port" "$work/tunnel.log" || fail "tunnel.sh did not print the address"
echo "ok"

step "A sudo password is caught"
in_server sh -c 'echo "deploy ALL=(ALL) ALL" > /etc/sudoers.d/90-deploy'
! "$DIR/setup-inventory.sh" 127.0.0.1 --port "$PORT" --inventory "$work/other.ini" > /dev/null 2>&1 \
  || fail "a deploy user that needs a sudo password passed the login test"
in_server bash /setup/setup-server.sh "$pub" > /dev/null
echo "ok"

step "Key-only SSH: setup-server.sh --key-only-ssh"
in_server bash /setup/setup-server.sh "$pub" --key-only-ssh
start_sshd
in_server sshd -T | grep -x 'passwordauthentication no' > /dev/null || fail "password logins are still on"
"$DIR/setup-inventory.sh" 127.0.0.1 --port "$PORT" --inventory "$work/inventory.ini" > /dev/null \
  || fail "the key login fails after --key-only-ssh"
echo "ok"

printf '\nAll setup script checks passed.\n'
