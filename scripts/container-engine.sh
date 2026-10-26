#!/usr/bin/env bash
# The container engine the repository needs (Docker or Podman): the local PostgreSQL (run, dev),
# the backend tests (Testcontainers), the images, the end-to-end tests and server:test-setup.
#
#   scripts/container-engine.sh            check, and install one when there is none (mise run setup)
#   scripts/container-engine.sh check      check only; exit 1 when no engine works
#
# An engine that works (`docker info` or `podman info` succeeds; CONTAINER_ENGINE picks one) is
# left alone. When there is none:
#   - Ubuntu or Debian: Docker from the distribution's packages (docker.io, docker-compose-v2,
#     docker-buildx), with sudo, and your user in the docker group;
#   - macOS with Homebrew: Podman, and a Podman machine that shares your home folder;
#   - anything else: how to install one.
# PLATFORM_INSTALL_ENGINE=0 skips the install (the check still reports). It never fails the setup
# for a missing engine: the parts that need one say so when they start.
set -euo pipefail

mode=${1:-ensure}
case "$mode" in
  ensure | check) ;;
  -h | --help) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
  *) echo "container-engine: unknown argument: $mode (see --help)" >&2; exit 2 ;;
esac

say() { echo "container-engine: $*"; }

# The first engine whose CLI is on PATH, as scripts/postgres.sh picks it.
pick() {
  if [ -n "${CONTAINER_ENGINE:-}" ]; then
    echo "$CONTAINER_ENGINE"
  elif command -v docker > /dev/null 2>&1; then
    echo docker
  elif command -v podman > /dev/null 2>&1; then
    echo podman
  fi
}

works() { "$1" info > /dev/null 2>&1; }

# Why an installed engine does not work, and what to do; starts a stopped Podman machine.
diagnose() {
  local engine=$1 out
  out=$("$engine" info 2>&1 || true)
  if [ "$engine" = podman ] && [ "$(uname -s)" = Darwin ]; then
    if [ -z "$(podman machine list --format '{{.Name}}' 2> /dev/null)" ]; then
      say "creating a Podman machine that shares your home folder"
      podman machine init --volume "$HOME:$HOME"
    fi
    say "starting the Podman machine"
    podman machine start || true
    works podman && return 0
  fi
  if grep -qi 'permission denied' <<< "$out"; then
    say "$engine is installed, but your user may not use it. Run:"
    say "  sudo usermod -aG docker \"\$USER\"    then log out and in again (or: newgrp docker)"
  elif grep -qi 'cannot connect\|is the docker daemon running\|connection refused' <<< "$out"; then
    say "$engine is installed, but its daemon is not running. Run: sudo systemctl enable --now docker"
  else
    say "$engine is installed, but '$engine info' fails:"
    echo "$out" | tail -n 5 >&2
  fi
  return 1
}

install_engine() {
  if [ "$(uname -s)" = Linux ] && command -v apt-get > /dev/null 2>&1; then
    say "no container engine found; installing Docker from the distribution's packages (sudo)"
    sudo apt-get update
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2 docker-buildx
    sudo systemctl enable --now docker 2> /dev/null || true
    sudo usermod -aG docker "$USER"
    say "Docker is installed and your user is in the docker group."
    say "Log out and in again (or run: newgrp docker) before using it from this shell."
  elif [ "$(uname -s)" = Darwin ] && command -v brew > /dev/null 2>&1; then
    say "no container engine found; installing Podman with Homebrew"
    brew install podman
    podman machine init --volume "$HOME:$HOME"
    podman machine start
    say "Podman is installed and its machine runs (docs/docker-compose.md, Notes)."
  else
    say "no container engine found, and none can be installed here automatically."
    say "Install Docker (https://docs.docker.com/engine/install/) or Podman (https://podman.io/docs/installation)."
  fi
}

engine=$(pick)
if [ -n "$engine" ] && works "$engine"; then
  say "$engine works ($("$engine" --version 2> /dev/null | head -n 1))"
  exit 0
fi

if [ "$mode" = check ]; then
  if [ -n "$engine" ]; then diagnose "$engine" || true; else say "neither docker nor podman is on PATH"; fi
  exit 1
fi

if [ -n "$engine" ]; then
  diagnose "$engine" && say "$engine works now" || true
elif [ "${PLATFORM_INSTALL_ENGINE:-1}" = 0 ]; then
  say "no container engine found; PLATFORM_INSTALL_ENGINE=0, so nothing is installed."
elif [ "${CI:-}" = true ]; then
  say "no container engine found; not installing one in CI."
else
  install_engine
fi
exit 0
