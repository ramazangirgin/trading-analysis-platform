#!/usr/bin/env bash
# Step 7 of the agentic development flow (doc/agentic-development.md):
# the developer agent addresses the findings of the latest review round.
#
#   scripts/agent/fix.sh <pr>
#
# One agent run per finding, most severe first. A fixed finding becomes one commit,
# "Address R<round>-<n>: <summary>", through the pre-commit hook; a declined one changes nothing.
# Either way the review thread gets a reply (commit SHA and what changed, or the reason). Threads a
# developer resolved are skipped. Pushes once at the end, and leaves a step comment with the outcomes.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

pr=${1:?usage: $0 <pr>}
branch=$(gh pr view "$pr" --json headRefName --jq .headRefName)
use_pr_base "$pr"
plan=$(plan_file_of_branch "$branch")
round=$(($(count_steps "$pr" fix) + 1))
[ "$(count_steps "$pr" review)" -ge "$round" ] || die "review round $round has not run yet"

checkout_branch "$branch"
start=$(git rev-parse HEAD)
review_py() { py "$AGENT_DIR/review.py" "$@"; }

pr_step_comments "$pr" | review_py findings "$round" >"$AGENT_TMP/findings.json"
gh_list "repos/{owner}/{repo}/pulls/$pr/comments" | review_py comment-ids >"$AGENT_TMP/comment-ids.json"
# Review comments (database IDs) whose thread is resolved.
gh api graphql -F pr="$pr" -f query='
  query($owner: String!, $repo: String!, $pr: Int!) {
    repository(owner: $owner, name: $repo) { pullRequest(number: $pr) {
      reviewThreads(first: 100) { nodes { isResolved comments(first: 1) { nodes { databaseId } } } }
    } }
  }' -F owner="{owner}" -F repo="{repo}" \
  --jq '[.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved) | .comments.nodes[0].databaseId]' \
  >"$AGENT_TMP/resolved.json"

# The developer tools without committing: the script commits.
tools=()
for tool in "${AGENT_DEV_TOOLS[@]}"; do
  case $tool in "Bash(git commit:"* | "Bash(git add:"*) ;; *) tools+=("$tool") ;; esac
done
schema='{"type":"object","required":["action","summary","reply"],"properties":{"action":{"enum":["fixed","declined"]},"summary":{"type":"string"},"reply":{"type":"string"}}}'

finding_field() { review_py field "$AGENT_TMP/findings.json" "$1" "$2"; }
result_field() { py "$AGENT_DIR/agent_json.py" get "$1" structured_output | py -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$2"; }

# reply <finding id> <text>: on the finding's review thread, or as a PR comment if it has none.
reply() {
  local comment
  comment=$(review_py lookup "$AGENT_TMP/comment-ids.json" "$1")
  if [ -n "$comment" ]; then
    gh api "repos/{owner}/{repo}/pulls/$pr/comments/$comment/replies" -f body="$2" >/dev/null
  else
    gh pr comment "$pr" --body "**$1**: $2" >/dev/null
  fi
}

count=$(py -c 'import json, sys; print(len(json.load(sys.stdin)))' <"$AGENT_TMP/findings.json")
results=()
echo "{}" >"$AGENT_TMP/outcomes.json"
for ((i = 0; i < count; i++)); do
  id=$(finding_field "$i" id)
  comment=$(review_py lookup "$AGENT_TMP/comment-ids.json" "$id")
  if [ -n "$comment" ] && review_py contains "$AGENT_TMP/resolved.json" "$comment"; then
    log "$id: thread resolved, skipped"
    outcome=skipped reason="The thread was resolved by a developer."
  else
    {
      prompt fix.md
      echo
      echo "## The finding ($id; the plan is $plan)"
      echo
      echo "Where: $(finding_field "$i" path), line $(finding_field "$i" line); also at: $(finding_field "$i" also_at)"
      echo
      echo "**$(finding_field "$i" priority)**: $(finding_field "$i" title)"
      echo
      finding_field "$i" problem
      echo
      echo "Possible solutions: $(finding_field "$i" solutions)"
    } >"$AGENT_TMP/fix-$id.md"
    out=$AGENT_TMP/fix-$id.json
    results+=("$out")
    run_agent "$AGENT_MODEL" "$AGENT_TMP/fix-$id.md" "$out" "${tools[@]}" -- --json-schema "$schema"
    action=$(result_field "$out" action)
    reason=$(result_field "$out" reply)
    outcome=declined
    if [ "$action" = fixed ] && [ -n "$(git status --porcelain)" ]; then
      git add -A
      if git commit --quiet -m "Address $id: $(result_field "$out" summary)" 2>"$AGENT_TMP/hook.log"; then
        outcome=fixed
        reason="Fixed in $(git rev-parse --short HEAD): $reason"
      else
        log "$id: the pre-commit hook rejected the change; dropped"
        git reset --quiet --hard HEAD
        git clean -fdq
        outcome=open reason="The fix did not pass the pre-commit hook and was dropped: $(tail -n 5 "$AGENT_TMP/hook.log")"
      fi
    elif [ "$action" = fixed ]; then
      outcome=open reason="The developer agent reported a fix but changed nothing."
    else
      git reset --quiet --hard HEAD
      git clean -fdq
      reason="Declined: $reason"
    fi
    reply "$id" "$reason"
  fi
  review_py record "$AGENT_TMP/outcomes.json" "$id" "$outcome" "$reason"
  log "$id: $outcome"
done

commits=$(git rev-list --count "$start..HEAD")
[ "$commits" -eq 0 ] || push_branch
{
  echo "### Fix round $round"
  echo
  echo "$commits commit(s) for $count finding(s)."
  echo
  review_py outcomes-section "$AGENT_TMP/outcomes.json"
} >"$AGENT_TMP/step.md"
post_step "$pr" fix "$round" "$AGENT_TMP/step.md" ${results[@]+"${results[@]}"}
log "fix round $round done on #$pr ($commits commits)"
