#!/usr/bin/env bash
# Starts an agent script detached from this terminal, so that closing the terminal or the Claude
# Code session that started it does not stop it (docs/agentic-development.md, step 4):
#
#   scripts/agent/detach.sh <script> <args…>     (mise run agent:detach <script> <args…>)
#   scripts/agent/detach.sh implement.sh plan/<issue>-<slug>
#   scripts/agent/detach.sh next.sh <pr>
#
# <script> is a script of scripts/agent/ (run.sh, implement.sh, next.sh, review.sh, ...). The plan
# branch is the argument that is a plan/… branch, or a pull request number. The script runs in a new
# session, with its output appended to .git/agent/<issue>-<slug>/run.log; this prints the process ID
# and the log and returns. Follow it with `tail -f <log>`. Stop it with `kill -- -<pid>` (the whole
# process group): the lock run.pid is released, and started again it resumes. A branch whose lock is
# held (a run is active) is refused.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

script=${1:?usage: $0 <script> <args…>}
shift
case $script in */* | .*) die "not a script of scripts/agent/: $script" ;; esac
[ -f "$AGENT_DIR/$script" ] || die "no such script in scripts/agent/: $script"
case $script in lib.sh | detach.sh) die "$script cannot be run detached" ;; esac

branch=""
for arg in "$@"; do
  if [[ $arg == plan/* ]]; then
    branch=$arg
  elif [[ $arg =~ ^[0-9]+$ ]]; then
    branch=$(gh pr view "$arg" --json headRefName --jq .headRefName)
  else
    continue
  fi
  break
done
[ -n "$branch" ] || die "no plan branch (plan/<issue>-<slug>) or pull request number in the arguments"

use_state "$branch"
pid=$(active_run)
[ -z "$pid" ] || die "a run is already active: pid $pid, log $AGENT_STATE/run.log"

# A new session (setsid), stdin from /dev/null, stdout and stderr into run.log. The script copy is
# the snapshot of scripts/agent/ this run started with, as for any script.
pid=$(py -c '
import subprocess, sys
with open(sys.argv[1], "ab") as log:
    process = subprocess.Popen(
        sys.argv[2:], stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, start_new_session=True
    )
print(process.pid)
' "$AGENT_STATE/run.log" bash "$AGENT_DIR/$script" "$@")

# The script refuses to start in the first moments if something is wrong (the lock, a dirty tree).
sleep 2
if ! kill -0 "$pid" 2>/dev/null; then
  tail -n 10 "$AGENT_STATE/run.log" >&2
  die "$script stopped right after it started (log: $AGENT_STATE/run.log)"
fi
echo "Started $script detached: pid $pid"
echo "Log:  $AGENT_STATE/run.log   (follow it: tail -f $AGENT_STATE/run.log)"
echo "Stop: kill -- -$pid   (the lock is released; start it again to resume)"
