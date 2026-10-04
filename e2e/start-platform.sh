#!/bin/sh
# Starts the built jar for the end-to-end tests (playwright.config.ts): a throwaway platform home and
# TradingAgents data dir, and the real ta-runner replaying a recording instead of calling an LLM
# (artifact/ta-runner/tests/fixtures/replay-run). Needs the jar (./gradlew :backend:bootJar) and
# ta-runner's venv (uv sync in artifact/ta-runner).
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

# From the repository root: the default runner command is relative to it. No external key files,
# so a developer's own keys never reach the tests.
cd "$root"
exec java -jar "$jar" --server.port="${E2E_PORT:-8090}" --platform.secrets.external-env-files=
