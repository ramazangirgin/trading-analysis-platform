#!/usr/bin/env bash
# Step 3 of deploy/server/README.md, on your computer: checks that the deploy user logs
# in to the server with the key and may use sudo without a password, then writes the inventory.
#
#   deploy/server/setup-inventory.sh <server> [--port N] [--inventory FILE] [--force]
#
# <server> is the server's IP address or DNS name. The user is deploy (DEPLOY_USER) and the key
# ~/.ssh/platform-deploy (DEPLOY_KEY), as setup-key.sh and setup-server.sh made them. The inventory
# is deploy/server/inventory.ini unless --inventory names another file; an existing one is kept
# unless --force, or unless it already says the same.
set -euo pipefail

KEY=${DEPLOY_KEY:-$HOME/.ssh/platform-deploy}
USER_NAME=${DEPLOY_USER:-deploy}
DIR=$(cd "$(dirname "$0")" && pwd)

die() {
  echo "setup-inventory: $*" >&2
  exit 1
}

server=""
port=""
inventory=$DIR/inventory.ini
force=false
while [ $# -gt 0 ]; do
  case "$1" in
    --port) port=${2:?--port needs a number}; shift ;;
    --inventory) inventory=${2:?--inventory needs a file}; shift ;;
    --force) force=true ;;
    -h | --help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
    -*) die "unknown option: $1 (see --help)" ;;
    *) [ -z "$server" ] || die "one server only"; server=$1 ;;
  esac
  shift
done
[ -n "$server" ] || die "usage: $0 <server> [--port N] [--inventory FILE] [--force]"
[ -f "$KEY" ] || die "no key $KEY: run $DIR/setup-key.sh first"
[ -z "$port" ] || [[ "$port" =~ ^[0-9]+$ ]] || die "not a port: $port"
# Absolute: `mise run server:deploy` runs in deploy/server, not where this script was started.
[ -d "$(dirname "$inventory")" ] || die "no such directory: $(dirname "$inventory")"
inventory=$(cd "$(dirname "$inventory")" && pwd)/$(basename "$inventory")

echo "setup-inventory: logging in as $USER_NAME@$server with $KEY"
# Key only: a password prompt here would mean the key is not accepted. A passphrase is still asked
# for, unless the key is in your SSH agent.
ssh_opts=(-i "$KEY" -o IdentitiesOnly=yes -o PasswordAuthentication=no
  -o KbdInteractiveAuthentication=no -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15)
[ -z "$port" ] || ssh_opts+=(-p "$port")
# DEPLOY_KNOWN_HOSTS: another known_hosts file (the tests' throwaway servers get a new host key
# every run).
[ -z "${DEPLOY_KNOWN_HOSTS:-}" ] || ssh_opts+=(-o UserKnownHostsFile="$DEPLOY_KNOWN_HOSTS")
if ! out=$(ssh "${ssh_opts[@]}" "$USER_NAME@$server" 'sudo -n true && echo "deploy user ok"' 2>&1); then
  echo "$out" >&2
  die "the login test failed; see Troubleshooting in $DIR/README.md"
fi
echo "$out" | grep -qx "deploy user ok" || { echo "$out" >&2; die "logged in, but sudo asked for a password"; }
echo "deploy user ok"

line="platform ansible_host=$server ansible_user=$USER_NAME ansible_ssh_private_key_file=$KEY"
[ -z "$port" ] || line="$line ansible_port=$port"
content="# Written by deploy/server/setup-inventory.sh; see inventory.ini.example for the fields.
[server]
$line"

if [ -e "$inventory" ] && ! $force; then
  if [ "$(cat "$inventory")" = "$content" ]; then
    echo "setup-inventory: $inventory is up to date"
  else
    die "$inventory exists and says something else; --force overwrites it"
  fi
else
  printf '%s\n' "$content" > "$inventory"
  echo "setup-inventory: wrote $inventory"
fi

deploy_args=""
[ "$inventory" = "$DIR/inventory.ini" ] || deploy_args=" -- -i $inventory"
cat <<EOF

Next (README, step 4):
  mise run server:deploy$deploy_args
EOF
