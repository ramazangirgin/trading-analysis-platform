#!/usr/bin/env bash
# The platform's PostgreSQL 18 for local runs (mise run db, run, dev) and the end-to-end tests.
# Needs Docker or Podman: CONTAINER_ENGINE picks one, else docker when it is on PATH, else podman.
# Run it with bash: `bash scripts/postgres.sh <command>`.
#
#   postgres.sh start     Start the local container trading-analysis-platform-db (created the first
#                          time, its data in the volume of the same name) and wait until it accepts
#                          connections. Does nothing when PLATFORM_DB_CONTAINER=false (your own
#                          PostgreSQL on localhost) or when PLATFORM_DB_HOST names another server
#                          than localhost / 127.0.0.1: that is your own PostgreSQL, used as it is.
#   postgres.sh stop       Stop the container; the volume keeps the data.
#   postgres.sh reset      Remove the container and its volume: the next start is an empty database.
#   postgres.sh throwaway  A disposable container on a random port for the end-to-end tests; prints
#                          "<container id> <host port>" and removes leftovers of earlier runs first.
#   postgres.sh throwaway-stop <container id>
#                          Remove that disposable container.
#
# The container publishes its port on 127.0.0.1 only and trusts every connection on it, so the
# application's default (user platform, empty password) works. The image is the Compose file's.
set -euo pipefail

IMAGE=postgres:18.6
NAME=trading-analysis-platform-db
VOLUME=trading-analysis-platform-db
E2E_LABEL=trading-analysis-platform.e2e=true

if [ -n "${CONTAINER_ENGINE:-}" ]; then
  engine=$CONTAINER_ENGINE
elif command -v docker >/dev/null 2>&1; then
  engine=docker
elif command -v podman >/dev/null 2>&1; then
  engine=podman
else
  echo "postgres: neither docker nor podman is on PATH (or set CONTAINER_ENGINE)." >&2
  exit 1
fi

# Waits up to 60 s until the container's server accepts TCP connections (the image's temporary
# server during the first initialisation listens on its socket only), else prints its log and fails.
wait_ready() {
  local container=$1 i
  for i in $(seq 1 60); do
    if "$engine" exec "$container" pg_isready -q -h 127.0.0.1 -U platform -d platform; then
      return 0
    fi
    sleep 1
  done
  echo "postgres: $container is not ready after 60 s; its last log lines:" >&2
  "$engine" logs --tail 20 "$container" >&2 || true
  return 1
}

start() {
  if [ "${PLATFORM_DB_CONTAINER:-true}" = false ]; then
    echo "postgres: PLATFORM_DB_CONTAINER=false, using your own server; no local container started."
    return 0
  fi
  case "${PLATFORM_DB_HOST:-localhost}" in
    localhost | 127.0.0.1) ;;
    *)
      echo "postgres: PLATFORM_DB_HOST=$PLATFORM_DB_HOST, using that server; no local container started."
      return 0
      ;;
  esac
  local running
  if running=$("$engine" container inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null); then
    if [ "$running" != true ]; then
      echo "postgres: starting $NAME"
      "$engine" start "$NAME" >/dev/null
    fi
  else
    echo "postgres: creating $NAME ($IMAGE)"
    if ! "$engine" run -d --name "$NAME" \
      -v "$VOLUME":/var/lib/postgresql \
      -p "127.0.0.1:${PLATFORM_DB_PORT:-5432}:5432" \
      -e POSTGRES_DB=platform -e POSTGRES_USER=platform -e POSTGRES_HOST_AUTH_METHOD=trust \
      "$IMAGE" >/dev/null; then
      # A failed run (e.g. the port is taken) leaves a created container behind; remove it so the
      # next start does not try to start that one.
      "$engine" rm -f "$NAME" >/dev/null 2>&1 || true
      echo "postgres: could not create $NAME. If port ${PLATFORM_DB_PORT:-5432} is taken by your own" \
        "PostgreSQL, set PLATFORM_DB_CONTAINER=false to use it, or PLATFORM_DB_PORT to a free port." >&2
      return 1
    fi
  fi
  wait_ready "$NAME"
  echo "postgres: $NAME is ready on 127.0.0.1:${PLATFORM_DB_PORT:-5432}"
}

stop() {
  "$engine" stop "$NAME" >/dev/null 2>&1 || true
  echo "postgres: $NAME stopped, its data is kept in the volume $VOLUME"
}

reset() {
  "$engine" rm -f "$NAME" >/dev/null 2>&1 || true
  "$engine" volume rm -f "$VOLUME" >/dev/null 2>&1 || true
  echo "postgres: $NAME and the volume $VOLUME removed"
}

throwaway() {
  local leftovers id port
  leftovers=$("$engine" ps -aq --filter "label=$E2E_LABEL")
  if [ -n "$leftovers" ]; then
    # shellcheck disable=SC2086 # one id per line, no spaces
    "$engine" rm -f $leftovers >/dev/null
  fi
  id=$("$engine" run -d --rm --label "$E2E_LABEL" -p 127.0.0.1::5432 \
    -e POSTGRES_DB=platform -e POSTGRES_USER=platform -e POSTGRES_HOST_AUTH_METHOD=trust \
    "$IMAGE")
  if ! wait_ready "$id" >&2; then
    "$engine" rm -f "$id" >/dev/null 2>&1 || true
    return 1
  fi
  # "127.0.0.1:49153": the host port is what follows the last colon of the first line.
  port=$("$engine" port "$id" 5432/tcp | head -n 1)
  echo "$id ${port##*:}"
}

case "${1:-}" in
  start) start ;;
  stop) stop ;;
  reset) reset ;;
  throwaway) throwaway ;;
  throwaway-stop) "$engine" rm -f "${2:?usage: postgres.sh throwaway-stop <container id>}" >/dev/null 2>&1 || true ;;
  *)
    echo "usage: postgres.sh start|stop|reset|throwaway|throwaway-stop <id>" >&2
    exit 2
    ;;
esac
