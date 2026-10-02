#!/usr/bin/env bash
# Smoke test of the Docker Compose setup, as CI runs it: the stack comes up, the platform reports its
# runner healthy, an analysis runs in its own container (without a provider key, so it fails fast
# at the first LLM call, which is the expected outcome here), and the data survives a database
# restart and a full down/up.
#
#   docker build ... (or mise run docker-build)
#   deploy/smoke-test.sh /absolute/path/for/data     (a path the engine can mount)
#
# Needs docker with Compose v2, curl and jq. Leaves the stack down and the data folder behind.
set -euo pipefail

DATA=${1:?usage: deploy/smoke-test.sh /absolute/path/for/data}
PORT=${PLATFORM_PORT:-18080}
DIR=$(cd "$(dirname "$0")" && pwd)
ENV_FILE=$(mktemp)
API="http://127.0.0.1:$PORT/api"

cat > "$ENV_FILE" <<EOF
PLATFORM_DATA=$DATA
POSTGRES_PASSWORD=smoke-test-only
PLATFORM_PORT=$PORT
DOCKER_SOCKET=${DOCKER_SOCKET:-/var/run/docker.sock}
PLATFORM_IMAGE=${PLATFORM_IMAGE:-trading-analysis-platform:latest}
TA_RUNNER_IMAGE=${TA_RUNNER_IMAGE:-ta-runner:latest}
EOF

compose() { docker compose -f "$DIR/docker-compose.yml" --env-file "$ENV_FILE" "$@"; }
step() { printf '\n== %s\n' "$*"; }
fail() { printf '\nFAILED: %s\n' "$*"; compose ps -a || true; compose logs --tail 80 || true; exit 1; }

wait_for_platform() {
    for _ in $(seq 1 90); do
        curl -fsS "$API/analyses" > /dev/null 2>&1 && return 0
        sleep 2
    done
    fail "the platform did not answer on $API within 3 minutes"
}

analysis_count() { curl -fsS "$API/analyses" | jq length; }

trap 'compose down > /dev/null 2>&1 || true; rm -f "$ENV_FILE"' EXIT

step "Starting the stack (data in $DATA)"
# Compose creates the folder if needed (it may live inside a VM, e.g. with Podman on macOS).
compose up -d
wait_for_platform

step "Health"
health=$(curl -fsS "$API/health")
echo "$health" | jq -c '.checks[] | {name, status, code}'
[ "$(echo "$health" | jq -r '.checks[] | select(.name == "runner") | .status')" = UP ] \
    || fail "the runner check is not UP: ta-runner could not be started through the socket proxy"

step "An analysis in its own container"
id=$(curl -fsS -X POST -H 'Content-Type: application/json' "$API/analyses" \
    -d '{"ticker":"NVDA","tradeDate":"2026-09-25","analysts":["MARKET"],"llmProvider":"deepseek","deepThinkLlm":"deepseek-v4-pro","quickThinkLlm":"deepseek-v4-flash"}' \
    | jq -r .id)
echo "started $id"
status=""
for _ in $(seq 1 90); do
    analysis=$(curl -fsS "$API/analyses/$id")
    status=$(echo "$analysis" | jq -r .status)
    case "$status" in QUEUED|RUNNING) sleep 2 ;; *) break ;; esac
done
echo "$analysis" | jq -c '{status, errorCode, errorMessage}'
# No provider key is configured: the runner starts, then fails at the first LLM call.
if [ "$status" != FAILED ] || [ "$(echo "$analysis" | jq -r .errorCode)" != runner_error ]; then
    fail "expected the analysis to end FAILED/runner_error, got $status"
fi
events=$(curl -fsS --max-time 5 "$API/analyses/$id/events" | grep -c '^data:' || true)
[ "$events" -ge 2 ] || fail "expected the run's events to replay, got $events"
sleep 3
left=$(docker ps -aq --filter "label=ta.platform.run_id=$id" | wc -l | tr -d ' ')
[ "$left" = 0 ] || fail "the analysis container was not removed"

step "Data survives a database restart"
before=$(analysis_count)
compose restart postgres
sleep 5
wait_for_platform
[ "$(analysis_count)" = "$before" ] || fail "analyses lost after restarting postgres"

step "Data survives down and up"
compose down
compose up -d
wait_for_platform
[ "$(analysis_count)" = "$before" ] || fail "analyses lost after down/up"

step "OK"
