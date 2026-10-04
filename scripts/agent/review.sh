#!/usr/bin/env bash
# Step 6 of the agentic development flow (doc/coding-convention/repository-agentic-development.md):
# the review agent reviews a pull request against its plan.
#
#   scripts/agent/review.sh <pr>
#
# Posts one GitHub review with an inline comment per finding (priority, problem, possible
# solutions; ID R<round>-<n>), and a summary comment with the counts per priority, which also holds
# the findings for fix.sh. Changes no code.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

pr=${1:?usage: $0 <pr>}
branch=$(gh pr view "$pr" --json headRefName --jq .headRefName)
use_pr_base "$pr"
plan=$(plan_file_of_branch "$branch")
round=$(($(count_steps "$pr" review) + 1))

checkout_branch "$branch"
head=$(git rev-parse HEAD)

{
  prompt review.md
  echo
  echo "## The plan ($plan, approved)"
  echo
  cat "$plan"
  if [ "$round" -gt 1 ]; then
    echo
    echo "## Earlier rounds"
    echo
    echo "This is round $round. The findings of the earlier rounds and what the developer agent did"
    echo "with them follow. Do not report a finding again that was fixed, or declined with a reason"
    echo "you accept; report it again only when the fix is wrong or incomplete."
    echo
    comments=$(pr_step_comments "$pr")
    for ((r = 1; r < round; r++)); do
      echo "### Round $r findings"
      py "$AGENT_DIR/review.py" findings "$r" <<<"$comments"
    done
    echo "### Outcomes"
    py "$AGENT_DIR/review.py" outcomes <<<"$comments"
  fi
} >"$AGENT_TMP/review.md"

schema=$(
  cat <<'EOF'
{"type": "object", "required": ["summary", "another_round_needed", "findings"], "properties": {
  "summary": {"type": "string"},
  "another_round_needed": {"type": "boolean"},
  "findings": {"type": "array", "items": {"type": "object",
    "required": ["priority", "path", "line", "title", "problem", "solutions"], "properties": {
      "priority": {"enum": ["CRITICAL", "MAJOR", "MINOR"]},
      "path": {"type": "string"}, "line": {"type": "integer"},
      "title": {"type": "string"}, "problem": {"type": "string"},
      "solutions": {"type": "array", "items": {"type": "string"}, "minItems": 1},
      "also_at": {"type": "array", "items": {"type": "object", "required": ["path", "line"],
        "properties": {"path": {"type": "string"}, "line": {"type": "integer"}}}}}}}}}
EOF
)
run_agent "$AGENT_REVIEW_MODEL" "$AGENT_TMP/review.md" "$AGENT_TMP/review-agent.json" "${AGENT_REVIEW_TOOLS[@]}" -- --json-schema "$schema"

gh_list "repos/{owner}/{repo}/pulls/$pr/files" >"$AGENT_TMP/files.json"
py "$AGENT_DIR/review.py" build "$round" "$AGENT_TMP/review-agent.json" "$AGENT_TMP/files.json" "$AGENT_TMP"
py -c 'import json,sys; r=json.load(open(sys.argv[1])); r["commit_id"]=sys.argv[2]; json.dump(r, open(sys.argv[1], "w"))' \
  "$AGENT_TMP/review.json" "$head"
gh api "repos/{owner}/{repo}/pulls/$pr/reviews" --input "$AGENT_TMP/review.json" >/dev/null
post_step "$pr" review "$round" "$AGENT_TMP/summary.md" "$AGENT_TMP/review-agent.json"
log "review round $round posted on #$pr"
