#!/usr/bin/env bash
# Step 2 of deploy/server/README.md, from your computer, when you can already log in to the
# server over SSH (as root, or as a user who can sudo; with a password or a key).
#
#   deploy/server/setup-remote.sh <you>@<server> [ssh options, e.g. -p 2222 or -i ~/.ssh/other]
#   deploy/server/setup-remote.sh <you>@<server> --key-only-ssh [ssh options]
#
# Copies setup-server.sh to the server and runs it there with sudo, with the public key of
# setup-key.sh (~/.ssh/platform-deploy.pub, or DEPLOY_KEY.pub). One SSH connection is reused, so a
# password login asks for its password once, and sudo for its own when it needs one.
# --key-only-ssh is passed on to setup-server.sh (see there).
set -euo pipefail

KEY=${DEPLOY_KEY:-$HOME/.ssh/platform-deploy}
DIR=$(cd "$(dirname "$0")" && pwd)

die() {
  echo "setup-remote: $*" >&2
  exit 1
}

[ $# -ge 1 ] || die "usage: $0 <you>@<server> [--key-only-ssh] [ssh options]"
case "$1" in -h | --help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;; esac
target=$1
shift
extra=()
ssh_opts=()
for arg in "$@"; do
  case "$arg" in
    --key-only-ssh) extra+=("$arg") ;;
    *) ssh_opts+=("$arg") ;;
  esac
done

[ -f "$KEY.pub" ] || die "no $KEY.pub: run $DIR/setup-key.sh first"
pub=$(cat "$KEY.pub")
[[ "$pub" != *"'"* ]] || die "the public key contains a quote: $KEY.pub"

control=$(mktemp -u "${TMPDIR:-/tmp}/setup-remote.XXXXXX")
trap 'ssh -o ControlPath="$control" -O exit "$target" 2> /dev/null || true' EXIT
# Options before the host: ssh reads anything after it as the remote command. The ${a[@]+...}
# form keeps an empty array from failing under set -u in macOS's bash 3.2.
ssh_base=(-o ControlMaster=auto -o ControlPath="$control" -o ControlPersist=60
  -o StrictHostKeyChecking=accept-new ${ssh_opts[@]+"${ssh_opts[@]}"})

echo "setup-remote: copying setup-server.sh to $target"
remote=$(ssh "${ssh_base[@]}" "$target" 'mktemp /tmp/setup-server.XXXXXX')
ssh "${ssh_base[@]}" "$target" "cat > '$remote'" < "$DIR/setup-server.sh"

echo "setup-remote: running it with sudo"
user_env=""
[ -z "${DEPLOY_USER:-}" ] || user_env="DEPLOY_USER='$DEPLOY_USER' "
# -t: a terminal, so sudo can ask for a password.
status=0
ssh "${ssh_base[@]}" -t "$target" \
  "sudo ${user_env}bash '$remote' '$pub' ${extra[*]:-}; s=\$?; rm -f '$remote'; exit \$s" || status=$?
[ "$status" = 0 ] || die "setup-server.sh failed on $target (exit $status); see its output above"
