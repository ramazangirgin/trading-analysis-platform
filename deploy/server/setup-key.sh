#!/usr/bin/env bash
# Step A of deploy/server/README.md, on your computer: the SSH key the deploy logs in with.
#
#   deploy/server/setup-key.sh [--no-passphrase]
#
# Makes ~/.ssh/platform-deploy (DEPLOY_KEY names another path) unless it exists, and prints its
# public key and the next step. ssh-keygen asks for a passphrase; --no-passphrase sets none.
# Running it again changes nothing.
set -euo pipefail

KEY=${DEPLOY_KEY:-$HOME/.ssh/platform-deploy}
DIR=$(cd "$(dirname "$0")" && pwd)

die() {
  echo "setup-key: $*" >&2
  exit 1
}

passphrase=ask
for arg in "$@"; do
  case "$arg" in
    --no-passphrase) passphrase=none ;;
    -h | --help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown argument: $arg (see --help)" ;;
  esac
done

command -v ssh-keygen > /dev/null || die "ssh-keygen not found: install the OpenSSH client"

if [ ! -d "$(dirname "$KEY")" ]; then
  mkdir -p -m 700 "$(dirname "$KEY")"
fi

if [ -e "$KEY" ]; then
  echo "setup-key: $KEY exists, using it."
else
  args=(-t ed25519 -C platform-deploy -f "$KEY")
  [ "$passphrase" = none ] && args+=(-N "")
  ssh-keygen "${args[@]}"
fi
# A private key without its .pub (copied from elsewhere): derive it.
[ -e "$KEY.pub" ] || ssh-keygen -y -f "$KEY" > "$KEY.pub"

pub=$(cat "$KEY.pub")
cat <<EOF

The public key (it goes on the server; the private key $KEY never leaves this computer):

$pub

Next, put it on the server (README, steps B and C), one of:
  - you can log in to the server over SSH already:
      $DIR/setup-remote.sh <you>@<server>
  - you can only reach the server's console: copy setup-server.sh there, then
      sudo bash setup-server.sh '$pub'
EOF
