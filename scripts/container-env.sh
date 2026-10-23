#!/usr/bin/env bash
# Sourced by mise for every task (mise.toml, [env]): points DOCKER_HOST at a running Podman
# machine's socket, so Testcontainers and the Docker client libraries find the engine on macOS,
# where Podman does not listen on /var/run/docker.sock. Does nothing when DOCKER_HOST is set already,
# when a Docker socket exists, or when there is no Podman machine.
if [ -z "${DOCKER_HOST:-}" ] && [ ! -S /var/run/docker.sock ] && command -v podman > /dev/null 2>&1; then
  podman_socket=$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}' 2> /dev/null || true)
  if [ -S "$podman_socket" ]; then
    export DOCKER_HOST="unix://$podman_socket"
  fi
fi
