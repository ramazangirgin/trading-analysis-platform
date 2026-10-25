#!/usr/bin/env bash
# Runs one command; inside an agent it prints one line instead of the command's output.
#
#   scripts/quiet.sh <name> <command> [args...]
#
# Without AGENT_QUIET=1 the command runs unchanged (developers, CI). With it the output goes to
# build/quiet/<name>.log (QUIET_LOG_DIR overrides the folder): success prints "ok: <name> ...", a
# failure prints "failed: <name> ..." and an excerpt (the lines that look like errors with the line
# after each, at most 60, and the last 30 lines of the log), and the command's exit status is kept.
# AGENT_QUIET is set by scripts/agent/lib.sh (run_agent) for every agent.
set -euo pipefail

[ $# -ge 2 ] || {
  echo "usage: scripts/quiet.sh <name> <command> [args...]" >&2
  exit 2
}
name=$1
shift

if [ "${AGENT_QUIET:-}" != 1 ]; then
  exec "$@"
fi

root=$(cd "$(dirname "$0")/.." && pwd)
dir=${QUIET_LOG_DIR:-$root/build/quiet}
log=$dir/$name.log
mkdir -p "$dir"

shown=$log
case $log in
  "$root"/*) shown=${log#"$root"/} ;;
esac

SECONDS=0
status=0
"$@" >"$log" 2>&1 || status=$?

if [ "$status" -eq 0 ]; then
  echo "ok: $name (${SECONDS}s; full output: $shown)"
  exit 0
fi

echo "failed: $name (exit $status; full output: $shown)"
awk -v max=60 -v tail=30 -v re='error|fail|exception|✖|warning:|what went wrong' '
  { line[NR] = $0 }
  END {
    picked = 0
    for (i = 1; i <= NR && picked < max; i++) {
      if (tolower(line[i]) ~ re) {
        if (!sel[i]) { sel[i] = 1; picked++ }
        if (i < NR && picked < max && !sel[i + 1]) { sel[i + 1] = 1; picked++ }
      }
    }
    for (i = NR - tail + 1; i <= NR; i++) if (i >= 1) sel[i] = 1
    for (i = 1; i <= NR; i++) if (sel[i]) print line[i]
  }
' "$log"
exit "$status"
