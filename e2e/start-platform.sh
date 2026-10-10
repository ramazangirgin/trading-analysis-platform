#!/bin/sh
# Starts the built jar for the end-to-end tests (playwright.config.ts): a throwaway platform home and
# TradingAgents data dir, and the real ta-runner replaying a recording instead of calling an LLM
# (artifact/ta-runner/tests/fixtures/replay-run), on a throwaway PostgreSQL container
# (scripts/postgres.sh throwaway) that is removed when the platform stops. It allows four analyses at
# once, for the tests that run in parallel. Needs the jar
# (./gradlew :backend:bootJar), ta-runner's venv (uv sync in artifact/ta-runner), and Docker or Podman.
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
jar="$root/artifact/backend/build/libs/platform.jar"
if [ ! -f "$jar" ]; then
  echo "$jar is missing: build it first (./gradlew :backend:bootJar)." >&2
  exit 1
fi

home=$(mktemp -d "${TMPDIR:-/tmp}/platform-e2e.XXXXXX")
echo "Platform home for the end-to-end tests: $home"
export PLATFORM_HOME="$home"
export TRADINGAGENTS_HOME="$home/tradingagents"
# Upstream reads these two, not TRADINGAGENTS_HOME; the runner inherits them from the platform.
export TRADINGAGENTS_RESULTS_DIR="$TRADINGAGENTS_HOME/logs"
export TRADINGAGENTS_CACHE_DIR="$TRADINGAGENTS_HOME/cache"
export TA_RUNNER_REPLAY="$root/artifact/ta-runner/tests/fixtures/replay-run"

postgres="$root/scripts/postgres.sh"
database=$("$postgres" throwaway)
container=${database%% *}
export PLATFORM_DB_HOST=127.0.0.1
export PLATFORM_DB_PORT=${database##* }
echo "PostgreSQL for the end-to-end tests: container $container on port $PLATFORM_DB_PORT"

# The jar runs in the background so the trap can remove the container however the script ends;
# the script waits for it, so Playwright still sees one long-running process.
jar_pid=
cleanup() {
  trap - EXIT INT TERM
  if [ -n "$jar_pid" ]; then
    kill "$jar_pid" 2>/dev/null || true
    wait "$jar_pid" 2>/dev/null || true
  fi
  "$postgres" throwaway-stop "$container"
}
trap cleanup EXIT
trap 'exit 143' INT TERM

# From the repository root: the default runner command is relative to it. No external key files,
# so a developer's own keys never reach the tests.
cd "$root"
#
# Four analyses at once, not the production default of 2: the parallel tests start up to four
# (compare's two, reports, analysis), and queued ones would bring back the waiting. Replays use
# almost no CPU.
java -jar "$jar" --server.port="${E2E_PORT:-8090}" --platform.secrets.external-env-files= \
  --platform.analysis.max-concurrent-runs=4 &
jar_pid=$!
wait "$jar_pid"
