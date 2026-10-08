#!/usr/bin/env bash
# The agentic development flow from an approved plan to a pull request ready for review
# (docs/agentic-development.md), steps 5 to 9, on this machine:
#
#   scripts/agent/run.sh plan/<issue>-<slug>      (mise run agent:run plan/<issue>-<slug>)
#
# Implements the plan, work package by work package, and opens a draft pull request (implement.sh)
# unless the branch has one already, then runs the review → fix loop to its end (next.sh). Started
# again after an interruption, it continues where it stopped. The log, the agents' streams and the
# lock of the run are in .git/agent/<issue>-<slug>/ (run.log, run.pid); a second run on the same
# branch is refused.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

branch=${1:?usage: $0 plan/<issue>-<slug>}
[[ $branch == plan/* ]] || die "not a plan branch: $branch"
use_state "$branch"
lock_run
[ -n "$(pr_of_branch "$branch")" ] || "$AGENT_DIR/implement.sh" "$branch"
"$AGENT_DIR/next.sh" "$branch"
