#!/usr/bin/env bash
# The agentic development flow from an approved plan to a pull request ready for review
# (doc/coding-convention/repository-agentic-development.md), steps 5 to 9, on this machine:
#
#   scripts/agent/run.sh plan/<issue>-<slug>      (mise run agent:run plan/<issue>-<slug>)
#
# Implements the plan and opens a draft pull request (implement.sh) unless the branch has one
# already, then runs the review → fix loop to its end (next.sh). Started again after an
# interruption, it continues where it stopped.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

branch=${1:?usage: $0 plan/<issue>-<slug>}
[ -n "$(pr_of_branch "$branch")" ] || "$AGENT_DIR/implement.sh" "$branch"
"$AGENT_DIR/next.sh" "$branch"
