#!/usr/bin/env bash
# Step 2 of deploy/server/README.md, on the server: the user the deploy logs in as.
#
#   sudo bash setup-server.sh '<public key line>' [--key-only-ssh]
#
# Run it as root (sudo), on the server: from its console, or through setup-remote.sh from your
# computer. Running it again changes only what is missing. It
#   - installs and starts the SSH server when there is none (a home server, say);
#   - creates the user deploy (DEPLOY_USER names another), without a password: it logs in with the
#     key only;
#   - lets it use sudo without a password (/etc/sudoers.d/90-deploy, checked with visudo first);
#   - adds the public key (the line setup-key.sh printed) to its ~/.ssh/authorized_keys, with the
#     modes SSH requires.
# --key-only-ssh also turns off password and root logins for every user (README, after step 3).
# Use it only once the deploy user's key login works, and only if you have a key
# for your own login too.
set -euo pipefail

USER_NAME=${DEPLOY_USER:-deploy}
SUDOERS=/etc/sudoers.d/90-$USER_NAME
KEY_ONLY_CONF=/etc/ssh/sshd_config.d/01-keys-only.conf

die() {
  echo "setup-server: $*" >&2
  exit 1
}
step() { printf '\n== %s\n' "$*"; }

key=""
key_only=false
for arg in "$@"; do
  case "$arg" in
    --key-only-ssh) key_only=true ;;
    -h | --help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
    ssh-* | ecdsa-* | sk-*) key=$arg ;;
    *) die "unknown argument: $arg (the public key goes in quotes, as one argument; see --help)" ;;
  esac
done

[ "$(id -u)" = 0 ] || die "run it as root: sudo bash $0 '<public key>'"
[ -n "$key" ] || die "give the public key, in quotes: sudo bash $0 'ssh-ed25519 AAAA... platform-deploy'"
[[ "$key" != *$'\n'* ]] || die "the public key must be a single line"
[[ "$USER_NAME" =~ ^[a-z_][a-z0-9_-]*$ ]] || die "not a valid user name: $USER_NAME"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

step "SSH server"
if [ ! -x /usr/sbin/sshd ]; then
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install -y openssh-server
fi
# Ubuntu 24.04 starts sshd through ssh.socket; either unit running is enough.
if systemctl is-active --quiet ssh || systemctl is-active --quiet ssh.socket; then
  echo "running"
else
  systemctl enable --now ssh
  echo "started"
fi
echo "$key" > "$work/key.pub"
ssh-keygen -l -f "$work/key.pub" > /dev/null 2>&1 || die "not a valid public key: $key"
# sshd -t and -T need their privilege separation directory, which only a running sshd creates.
install -d -m 755 /run/sshd

step "User $USER_NAME"
if id "$USER_NAME" > /dev/null 2>&1; then
  echo "exists"
else
  adduser --disabled-password --gecos "" "$USER_NAME"
fi
home=$(getent passwd "$USER_NAME" | cut -d: -f6)

step "Passwordless sudo ($SUDOERS)"
echo "$USER_NAME ALL=(ALL) NOPASSWD:ALL" > "$work/sudoers"
visudo -cqf "$work/sudoers" || die "visudo rejected the sudoers line; nothing was changed"
if cmp -s "$work/sudoers" "$SUDOERS" 2> /dev/null; then
  echo "in place"
else
  install -m 440 -o root -g root "$work/sudoers" "$SUDOERS"
  echo "written"
fi

step "Key in $home/.ssh/authorized_keys"
install -d -m 700 -o "$USER_NAME" -g "$USER_NAME" "$home/.ssh"
touch "$home/.ssh/authorized_keys"
chown "$USER_NAME:$USER_NAME" "$home/.ssh/authorized_keys"
chmod 600 "$home/.ssh/authorized_keys"
if grep -qxF "$key" "$home/.ssh/authorized_keys"; then
  echo "already there"
else
  echo "$key" >> "$home/.ssh/authorized_keys"
  echo "added"
fi

if $key_only; then
  step "Key-only SSH ($KEY_ONLY_CONF)"
  # sshd keeps the first value it reads; the files of sshd_config.d are read in name order, so
  # 01- comes before the cloud images' 50-cloud-init.conf.
  printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin no\n' > "$KEY_ONLY_CONF"
  sshd -t || { rm -f "$KEY_ONLY_CONF"; die "sshd rejected the configuration; it was removed again"; }
  systemctl reload ssh 2> /dev/null || systemctl restart ssh
  sshd -T | grep -E '^(passwordauthentication|kbdinteractiveauthentication|permitrootlogin) '
fi

ports=$(sshd -T 2> /dev/null | awk '$1 == "port" { print $2 }' | paste -sd, -)
cat <<EOF

Done. SSH listens on port ${ports:-22}. Next, on your computer (README, step 3):
  deploy/server/setup-inventory.sh <server>$([ "${ports:-22}" = 22 ] || echo " --port ${ports%%,*}")
EOF
