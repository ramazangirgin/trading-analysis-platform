#!/usr/bin/env bash
# Step 4 of deploy/server/README.md (mise run server:deploy): runs the playbook from this machine,
# which logs in to the server over SSH and does the work there (packages, Docker, the image build,
# docker compose up). Only Ansible runs here; mise installs it from mise.toml on first use.
#
#   mise run server:deploy [-- ansible-playbook options, e.g. -e app_ref=v4.4.0 or -i other.ini]
#
# Without -i it needs deploy/server/inventory.ini (setup-inventory.sh writes it), and stops when it
# is missing: Ansible itself would only warn that no host matched and report success.
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)

die() {
  echo "server:deploy: $*" >&2
  exit 1
}

# -i/--inventory paths are taken relative to where the command was started (mise: the repository
# root), then made absolute, since the playbook runs in deploy/server (its ansible.cfg).
args=()
inventory_given=false
expect_inventory=false
for arg in "$@"; do
  if $expect_inventory; then
    case "$arg" in /*) ;; *) arg=$PWD/$arg ;; esac
    expect_inventory=false
  else
    case "$arg" in
      -i | --inventory) inventory_given=true; expect_inventory=true ;;
      -i?* | --inventory=*) inventory_given=true ;;
    esac
  fi
  args+=("$arg")
done

if ! $inventory_given && [ ! -f "$DIR/inventory.ini" ]; then
  die "no deploy/server/inventory.ini. Write it with
  deploy/server/setup-inventory.sh <server>
(see deploy/server/README.md, Steps), or name another one: mise run server:deploy -- -i <file>"
fi

cd "$DIR"
exec ansible-playbook site.yml ${args[@]+"${args[@]}"}
