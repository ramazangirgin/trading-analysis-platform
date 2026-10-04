#!/usr/bin/env bash
# Part of step 5 of the agentic development flow
# (doc/coding-convention/repository-agentic-development.md): the developer agent fixes a red CI.
#
#   scripts/agent/fix-ci.sh <pr>
#
# Gives the agent the failed jobs' log of the latest CI run on the pull request's head, lets it
# commit a fix and pushes it. next.sh limits the attempts (AGENT_MAX_CI_FIXES).
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

pr=${1:?usage: $0 <pr>}
branch=$(gh pr view "$pr" --json headRefName --jq .headRefName)
use_pr_base "$pr"
plan=$(plan_file_of_branch "$branch")
attempt=$(($(count_steps "$pr" ci-fix) + 1))

checkout_branch "$branch"
head=$(git rev-parse HEAD)
run=$(gh run list --workflow ci.yml --commit "$head" --status failure --limit 1 --json databaseId --jq '.[0].databaseId // empty')
[ -n "$run" ] || die "no failed CI run on $head"

{
  prompt fix-ci.md
  echo
  echo "## The plan: $plan"
  echo
  echo "## Failed jobs of CI run $run (last 400 lines)"
  echo
  echo '```'
  gh run view "$run" --log-failed | tail -n 400
  echo '```'
} >"$AGENT_TMP/fix-ci.md"

schema='{"type":"object","required":["summary"],"properties":{"summary":{"type":"string"}}}'
run_agent "$AGENT_MODEL" "$AGENT_TMP/fix-ci.md" "$AGENT_TMP/fix-ci.json" "${AGENT_DEV_TOOLS[@]}" -- --json-schema "$schema"
summary=$(py "$AGENT_DIR/agent_json.py" get "$AGENT_TMP/fix-ci.json" structured_output | py -c 'import json, sys; print(json.load(sys.stdin)["summary"])')
git reset --quiet --hard HEAD
git clean -fdq

commits=$(git rev-list --count "$head..HEAD")
[ "$commits" -eq 0 ] || push_branch
printf '### CI fix %s\n\nCI run %s failed. %s\n\n%s commit(s) pushed.\n' "$attempt" "$run" "$summary" "$commits" >"$AGENT_TMP/step.md"
post_step "$pr" ci-fix "$attempt" "$AGENT_TMP/step.md" "$AGENT_TMP/fix-ci.json"
