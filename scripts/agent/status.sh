#!/usr/bin/env bash
# Where is an agent run? Read only (docs/agentic-development.md, step 4):
#
#   scripts/agent/status.sh <branch | pr> [lines]     (mise run agent:status <branch | pr> [lines])
#   scripts/agent/status.sh --follow <branch | pr>
#
# <branch> is a plan branch (plan/<issue>-<slug>), <pr> the number of its pull request. Prints the
# active run (the pid in run.pid, or that none is active), the work package the implementation is on
# (`current`), the latest 10 milestone lines of .git/agent/<issue>-<slug>/run.log and its last
# [lines] lines (default 20). With --follow it prints only the milestone lines written from now on,
# one per line as they happen, until it is stopped: that is what a Claude Code session watches.
# This script writes nothing to run.log and does not take the lock.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

# The milestone lines of run.log: the one place where their pattern is written.
MILESTONE_PATTERN='^agent: >> '

follow=false
if [ "${1:-}" = --follow ]; then
  follow=true
  shift
fi
target=${1:?usage: $0 [--follow] <branch | pr> [lines]}
lines=${2:-20}
[[ $lines =~ ^[0-9]+$ ]] || die "not a number of lines: $lines"

if [[ $target =~ ^[0-9]+$ ]]; then
  branch=$(gh pr view "$target" --json headRefName --jq .headRefName)
else
  branch=$target
fi

use_state --no-log "$branch"
log_file=$AGENT_STATE/run.log

if $follow; then
  # tail -F waits for a log that does not exist yet; grep passes each line on as it arrives.
  tail -n 0 -F "$log_file" 2>/dev/null | grep --line-buffered "$MILESTONE_PATTERN"
  exit 0
fi

pid=$(active_run)
if [ -n "$pid" ]; then
  echo "Active run: pid $pid"
else
  echo "No run is active"
fi
package=$(sed -n 's/^package=//p' "$AGENT_STATE/current" 2>/dev/null || true)
[ -z "$package" ] || echo "Work package: $package"
echo "Log: $log_file"

if [ ! -s "$log_file" ]; then
  echo "No run log yet"
  exit 0
fi
echo
echo "Latest milestones:"
grep "$MILESTONE_PATTERN" "$log_file" | tail -n 10 || true
echo
echo "Last $lines lines of the log:"
tail -n "$lines" "$log_file"
