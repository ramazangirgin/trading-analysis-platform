#!/usr/bin/env bash
# Smoke test of the Docker Compose setup, as CI runs it: the stack comes up, the platform reports its
# runner healthy, the first admin (configured through the env file) logs in, an analysis runs in its
# own container (without a provider key, so it fails fast at the first LLM call, which is the
# expected outcome here), and the data and the admin survive a database restart and a full down/up.
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
JAR=$(mktemp)
API="http://127.0.0.1:$PORT/api"

cat > "$ENV_FILE" <<EOF
PLATFORM_DATA=$DATA
POSTGRES_PASSWORD=smoke-test-only
PLATFORM_PORT=$PORT
PLATFORM_ADMIN_PASSWORD=smoke-test-only-password
DOCKER_SOCKET=${DOCKER_SOCKET:-/var/run/docker.sock}
PLATFORM_IMAGE=${PLATFORM_IMAGE:-trading-analysis-platform:latest}
TA_RUNNER_IMAGE=${TA_RUNNER_IMAGE:-ta-runner:latest}
EOF

compose() { docker compose -f "$DIR/docker-compose.yml" --env-file "$ENV_FILE" "$@"; }
step() { printf '\n== %s\n' "$*"; }
fail() { printf '\nFAILED: %s\n' "$*"; compose ps -a || true; compose logs --tail 80 || true; exit 1; }

wait_for_platform() {
    for _ in $(seq 1 90); do
        curl -fsS "http://127.0.0.1:$PORT/actuator/health" > /dev/null 2>&1 && return 0
        sleep 2
    done
    fail "the platform did not answer on $API within 3 minutes"
}

# Logs in as the first admin; the session lives in memory, so every start needs a new one. The
# cookie jar holds the session and the CSRF cookie that the POST sends back as a header. The health
# check can answer a moment before the first admin is created, so the login is retried.
login() {
    for _ in $(seq 1 10); do
        : > "$JAR"
        curl -fsS -c "$JAR" -b "$JAR" "$API/auth/me" > /dev/null 2>&1 || true
        xsrf=$(awk '$6 == "XSRF-TOKEN" {print $7}' "$JAR")
        if [ -n "$xsrf" ] && curl -fsS -c "$JAR" -b "$JAR" -X POST -H 'Content-Type: application/json' \
            -H "X-XSRF-TOKEN: $xsrf" "$API/auth/login" \
            -d '{"username":"admin","password":"smoke-test-only-password"}' > /dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    fail "the first admin could not log in"
}

# Every API call goes through the session; a POST also sends the CSRF header.
api() { curl -fsS -b "$JAR" -c "$JAR" "$@"; }
api_post() { api -X POST -H "X-XSRF-TOKEN: $(awk '$6 == "XSRF-TOKEN" {print $7}' "$JAR")" "$@"; }

analysis_count() { api "$API/analyses" | jq length; }

trap 'compose down > /dev/null 2>&1 || true; rm -f "$ENV_FILE" "$JAR"' EXIT

step "Starting the stack (data in $DATA)"
# Compose creates the folder if needed (it may live inside a VM, e.g. with Podman on macOS).
compose up -d
wait_for_platform

step "First admin"
login
me=$(api "$API/auth/me")
echo "$me" | jq -c .
[ "$(echo "$me" | jq -r .username)" = admin ] || fail "/api/auth/me does not show the user admin"
echo "$me" | jq -e '.roles | index("ADMIN")' > /dev/null || fail "the first admin does not have the ADMIN role"

step "Health"
health=$(api "$API/health")
echo "$health" | jq -c '.checks[] | {name, status, code}'
[ "$(echo "$health" | jq -r '.checks[] | select(.name == "runner") | .status')" = UP ] \
    || fail "the runner check is not UP: ta-runner could not be started through the socket proxy"

step "An analysis in its own container"
id=$(api_post -H 'Content-Type: application/json' "$API/analyses" \
    -d '{"ticker":"NVDA","tradeDate":"2026-09-25","analysts":["MARKET"],"llmProvider":"deepseek","deepThinkLlm":"deepseek-v4-pro","quickThinkLlm":"deepseek-v4-flash"}' \
    | jq -r .id)
echo "started $id"
status=""
for _ in $(seq 1 90); do
    analysis=$(api "$API/analyses/$id")
    status=$(echo "$analysis" | jq -r .status)
    case "$status" in QUEUED|RUNNING) sleep 2 ;; *) break ;; esac
done
echo "$analysis" | jq -c '{status, errorCode, errorMessage}'
# No provider key is configured: the runner starts, then fails at the first LLM call.
if [ "$status" != FAILED ] || [ "$(echo "$analysis" | jq -r .errorCode)" != runner_error ]; then
    fail "expected the analysis to end FAILED/runner_error, got $status"
fi
events=$(api --max-time 5 "$API/analyses/$id/events" | grep -c '^data:' || true)
[ "$events" -ge 2 ] || fail "expected the run's events to replay, got $events"
sleep 3
left=$(docker ps -aq --filter "label=ta.platform.run_id=$id" | wc -l | tr -d ' ')
[ "$left" = 0 ] || fail "the analysis container was not removed"

step "Data survives a database restart"
before=$(analysis_count)
compose restart postgres
sleep 5
wait_for_platform
login
[ "$(analysis_count)" = "$before" ] || fail "analyses lost after restarting postgres"

step "Data survives down and up"
compose down
compose up -d
wait_for_platform
# Sessions live in memory: a new login also proves the admin and its password survived.
login
[ "$(analysis_count)" = "$before" ] || fail "analyses lost after down/up"

step "OK"
