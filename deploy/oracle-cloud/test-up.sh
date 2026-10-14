#!/usr/bin/env bash
# Tests up.sh against a fake terraform (mise run oracle:check, CI). The fake logs every call and
# answers `apply` from a script of outcomes: "ok", "capacity" (OCI's sold-out error) or "other".
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

cat > "$WORK/terraform" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_CALLS"
case " $* " in
    *" apply "*)
        n=$(( $(cat "$FAKE_COUNT" 2>/dev/null || echo 0) + 1 ))
        echo "$n" > "$FAKE_COUNT"
        outcome=$(echo "$FAKE_OUTCOMES" | cut -d' ' -f"$n")
        outcome=${outcome:-ok}
        case "$outcome" in
            ok) echo "Apply complete!" ;;
            capacity) echo "Error: 500-InternalError, Out of host capacity for shape VM.Standard.A1.Flex" >&2; exit 1 ;;
            other) echo "Error: 401-NotAuthenticated" >&2; exit 7 ;;
        esac
        ;;
esac
EOF
chmod +x "$WORK/terraform"

export TERRAFORM="$WORK/terraform"
export ORACLE_RETRY_INTERVAL=0
export FAKE_CALLS="$WORK/calls"
export FAKE_COUNT="$WORK/count"

FAILED=0
# run <name> <outcomes> <expected exit code> <expected apply calls> [up.sh arguments]
run() {
    local name=$1 outcomes=$2 want_code=$3 want_applies=$4
    shift 4
    : > "$FAKE_CALLS"
    rm -f "$FAKE_COUNT"
    local code=0
    FAKE_OUTCOMES=$outcomes bash "$DIR/up.sh" "$@" > "$WORK/out" 2>&1 || code=$?
    local applies
    applies=$(grep -c ' apply ' "$FAKE_CALLS" || true)
    if [ "$code" -ne "$want_code" ] || [ "$applies" -ne "$want_applies" ]; then
        echo "FAIL $name: exit $code (want $want_code), $applies applies (want $want_applies)"
        cat "$WORK/out"
        FAILED=1
    else
        echo "ok   $name"
    fi
}

run "first-time success applies once" "ok" 0 1
run "two capacity failures, then success" "capacity capacity ok" 0 3
run "another error stops at once with its code" "other ok" 7 1
ORACLE_RETRY_ATTEMPTS=2 run "gives up after the attempt limit" "capacity capacity ok" 1 2

run "extra arguments reach apply" "ok" 0 1 -var ocpus=2
if ! grep -q 'apply .*-var ocpus=2' "$FAKE_CALLS"; then
    echo "FAIL extra arguments reach apply: calls were:"
    cat "$FAKE_CALLS"
    FAILED=1
fi

exit "$FAILED"
