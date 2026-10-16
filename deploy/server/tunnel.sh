#!/usr/bin/env bash
# Opens the platform's UI from your computer (mise run server:tunnel): an SSH tunnel from a port on
# this machine to the server's 127.0.0.1:8080, where the UI listens. Keep it running while you use
# the UI at the address it prints; Ctrl+C closes it.
#
#   mise run server:tunnel [-- --port N] [--inventory FILE] [--remote-port N]
#
# The server, user, key and SSH port come from deploy/server/inventory.ini (setup-inventory.sh
# writes it), or the file --inventory names. --port is the port on this machine (default 8080;
# another one when 8080 is taken here). --remote-port is the platform's port on the server
# (platform_port of the deploy, default 8080).
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)

die() {
  echo "server:tunnel: $*" >&2
  exit 1
}

local_port=8080
remote_port=8080
inventory=$DIR/inventory.ini
while [ $# -gt 0 ]; do
  case "$1" in
    --port) local_port=${2:?--port needs a number}; shift ;;
    --remote-port) remote_port=${2:?--remote-port needs a number}; shift ;;
    --inventory) inventory=${2:?--inventory needs a file}; shift ;;
    -h | --help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
    *) die "unknown argument: $1 (see --help)" ;;
  esac
  shift
done
[[ "$local_port" =~ ^[0-9]+$ ]] || die "not a port: $local_port"
[[ "$remote_port" =~ ^[0-9]+$ ]] || die "not a port: $remote_port"
[ -f "$inventory" ] || die "no $inventory. Write it with deploy/server/setup-inventory.sh <server> (deploy/server/README.md, step 3)"

# The first host line of the inventory: "<name> ansible_host=... ansible_user=... ...".
line=$(grep -vE '^[[:space:]]*(#|;|\[|$)' "$inventory" | head -n 1)
[ -n "$line" ] || die "no host in $inventory"
value() { tr ' ' '\n' <<< "$line" | sed -n "s/^$1=//p" | head -n 1; }
host=$(value ansible_host)
host=${host:-$(awk '{ print $1 }' <<< "$line")}
user=$(value ansible_user)
key=$(value ansible_ssh_private_key_file)
port=$(value ansible_port)

ssh_opts=(-N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ConnectTimeout=15
  -o StrictHostKeyChecking=accept-new -L "$local_port:127.0.0.1:$remote_port")
# shellcheck disable=SC2088 # the inventory may write the key path with ~, as SSH users do.
[ -z "$key" ] || ssh_opts+=(-i "${key/#\~/$HOME}" -o IdentitiesOnly=yes)
[ -z "$port" ] || ssh_opts+=(-p "$port")
# DEPLOY_KNOWN_HOSTS: another known_hosts file (the tests' throwaway servers get a new host key
# every run), as in setup-inventory.sh.
[ -z "${DEPLOY_KNOWN_HOSTS:-}" ] || ssh_opts+=(-o UserKnownHostsFile="$DEPLOY_KNOWN_HOSTS")
target=${user:+$user@}$host

cat <<EOF
server:tunnel: $target, this machine's port $local_port to the server's 127.0.0.1:$remote_port
Open http://127.0.0.1:$local_port in your browser. Keep this running while you use it; Ctrl+C closes it.
EOF

status=0
ssh "${ssh_opts[@]}" "$target" || status=$?
# 130: Ctrl+C, the normal way out.
[ "$status" = 0 ] || [ "$status" = 130 ] || die "the tunnel failed (ssh exit $status): the server did not answer, the login was refused (see the ssh message above; deploy/server/README.md, Troubleshooting), or port $local_port is taken on this machine (then: mise run server:tunnel -- --port 18080)"
