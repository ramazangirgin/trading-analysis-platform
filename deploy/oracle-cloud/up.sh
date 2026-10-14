#!/usr/bin/env bash
# Creates the Oracle Cloud VM (mise run oracle:up): terraform init, then terraform apply in a loop.
# Always Free Ampere A1 capacity is often sold out; OCI then fails with "Out of host capacity", and
# the apply is tried again until it succeeds. Any other failure stops at once.
#
#   deploy/oracle-cloud/up.sh [extra terraform apply arguments]     e.g. -var ocpus=2
#
# ORACLE_RETRY_INTERVAL  seconds between attempts (default 60)
# ORACLE_RETRY_ATTEMPTS  attempts before giving up (default 60, 0 for no limit)
# TERRAFORM              the terraform binary (default terraform)
#
# Applies without asking, on purpose: the loop cannot answer a prompt on every attempt. Look at
# what it will do first with mise run oracle:plan.
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
TF=${TERRAFORM:-terraform}
INTERVAL=${ORACLE_RETRY_INTERVAL:-60}
ATTEMPTS=${ORACLE_RETRY_ATTEMPTS:-60}
LOG=$(mktemp)
trap 'rm -f "$LOG"' EXIT

"$TF" -chdir="$DIR/terraform" init -input=false

attempt=1
while true; do
    echo "== terraform apply: attempt $attempt of $([ "$ATTEMPTS" -eq 0 ] && echo unlimited || echo "$ATTEMPTS")"
    status=0
    "$TF" -chdir="$DIR/terraform" apply -input=false -auto-approve "$@" 2>&1 | tee "$LOG" || status=$?
    # With pipefail the pipeline's status is the last failing one: terraform's, tee does not fail.
    if [ "$status" -eq 0 ]; then
        exit 0
    fi
    if ! grep -q 'Out of host capacity' "$LOG"; then
        echo "terraform apply failed (exit code $status)" >&2
        exit "$status"
    fi
    if [ "$ATTEMPTS" -ne 0 ] && [ "$attempt" -ge "$ATTEMPTS" ]; then
        echo "Out of host capacity after $attempt attempts, giving up" >&2
        exit "$status"
    fi
    echo "Out of host capacity, trying again in ${INTERVAL}s"
    sleep "$INTERVAL"
    attempt=$((attempt + 1))
done
