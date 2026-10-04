#!/usr/bin/env bash
# Step 9 of the agentic development flow (doc/agentic-development.md):
# sums up an agent's pull request and hands it to the developer.
#
#   scripts/agent/finalise.sh <pr>
#
# Posts the final comment (what the plan asked for, the commits, the review findings fixed /
# declined / still open, the tokens used), marks the pull request ready for review and removes the
# "agent" label. Never merges.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

pr=${1:?usage: $0 <pr>}
branch=$(gh pr view "$pr" --json headRefName --jq .headRefName)
use_pr_base "$pr"
plan=$(plan_file_of_branch "$branch")
git fetch --quiet origin "$AGENT_BASE_BRANCH" "$branch"
comments=$(pr_step_comments "$pr")
rounds=$(count_steps "$pr" review)

{
  echo "### Done: ready for review"
  echo
  echo "**Plan** ([\`$plan\`](https://github.com/$(repo_slug)/blob/$branch/$plan)), its work packages:"
  echo
  git show "origin/$branch:$plan" | sed -n 's/^### \(WP[0-9].*\)/- \1/p'
  echo
  echo "**Commits**:"
  echo
  git log --reverse --format='- %h %s' "origin/$AGENT_BASE_BRANCH..origin/$branch"
  echo
  echo "**Review findings** ($rounds round(s)):"
  echo
  py "$AGENT_DIR/review.py" findings-table <<<"$comments"
  echo
  echo "Open and declined findings are for the developer to decide."
  echo
  echo "**Usage** (developer: $(model_name "$AGENT_MODEL"), review: $(model_name "$AGENT_REVIEW_MODEL")):"
  echo
  py "$AGENT_DIR/agent_json.py" usage-table <<<"$comments"
} >"$AGENT_TMP/final.md"

post_step "$pr" finalise "$rounds" "$AGENT_TMP/final.md"
gh pr ready "$pr"
gh pr edit "$pr" --remove-label "$AGENT_LABEL" >/dev/null
log "#$pr is ready for review"
