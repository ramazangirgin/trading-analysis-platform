#!/usr/bin/env bash
# Step 5 of the agentic development flow (docs/agentic-development.md):
# the developer agent implements a plan and opens a draft pull request.
#
#   scripts/agent/implement.sh plan/<issue>-<slug>
#
# Checks out the plan branch, runs the developer agent with the plan, checks its work
# (mise run check, the version bump), pushes and opens a draft pull request labelled "agent".
# The review → fix loop (next.sh) follows; run.sh runs both. The plan file stays in the pull request.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

branch=${1:?usage: $0 plan/<issue>-<slug>}
[[ $branch == plan/* ]] || die "not a plan branch: $branch"
issue=$(issue_of_branch "$branch")
plan=$(plan_file_of_branch "$branch")

checkout_branch "$branch"
[ -f "$plan" ] || die "$branch has no $plan"
[ -z "$(pr_of_branch "$branch")" ] || die "$branch has an open pull request already; continue it with next.sh"
start=$(git rev-parse HEAD)

{
  prompt implement.md
  echo
  echo "## The plan ($plan, approved)"
  echo
  cat "$plan"
} >"$AGENT_TMP/implement.md"

schema='{"type":"object","properties":{"summary":{"type":"string"},"not_done":{"type":"string"}},"required":["summary","not_done"]}'
results=("$AGENT_TMP/implement.json")
run_agent "$AGENT_MODEL" "$AGENT_TMP/implement.md" "$AGENT_TMP/implement.json" "${AGENT_DEV_TOOLS[@]}" -- --json-schema "$schema"
session=$(py "$AGENT_DIR/agent_json.py" get "$AGENT_TMP/implement.json" session_id)

# Gate: what CI would reject fast. Two more tries, each resuming the agent with the failure.
for attempt in 1 2 3; do
  problems=""
  [ -z "$(git status --porcelain)" ] || problems+="Uncommitted changes are left:"$'\n'"$(git status --short)"$'\n\n'
  [ "$(git rev-list --count "$start..HEAD")" -gt 0 ] || problems+="Nothing was committed."$'\n\n'
  if ! out=$(scripts/version.sh check-bump "origin/$AGENT_BASE_BRANCH" 2>&1); then
    problems+="Version check failed: $out"$'\n\n'
  fi
  if ! out=$(mise run check 2>&1); then
    problems+="mise run check failed:"$'\n'"$(tail -n 80 <<<"$out")"$'\n\n'
  fi
  [ -n "$problems" ] || break
  [ "$attempt" -lt 3 ] || die "the implementation still fails its checks:"$'\n'"$problems"
  log "checks failed (attempt $attempt), resuming the agent"
  printf 'The checks failed. Fix the problems, run the checks again and commit:\n\n%s\n' "$problems" >"$AGENT_TMP/retry.md"
  results+=("$AGENT_TMP/implement-retry-$attempt.json")
  run_agent "$AGENT_MODEL" "$AGENT_TMP/retry.md" "${results[${#results[@]}-1]}" "${AGENT_DEV_TOOLS[@]}" -- --resume "$session" --json-schema "$schema"
done

push_branch

summary=$(py "$AGENT_DIR/agent_json.py" get "${results[0]}" structured_output | py -c 'import json,sys; print(json.load(sys.stdin)["summary"])')
not_done=$(py "$AGENT_DIR/agent_json.py" get "${results[${#results[@]}-1]}" structured_output | py -c 'import json,sys; print(json.load(sys.stdin)["not_done"])')
title=$(sed -n 's/^# Plan: //p' "$plan" | head -n 1)
blob="https://github.com/$(repo_slug)/blob"
cat >"$AGENT_TMP/pr.md" <<EOF
Closes #$issue

Implemented by the developer agent from the plan [\`$plan\`]($blob/$branch/$plan) (model: $(model_name "$AGENT_MODEL")).
Draft until the agents' review → fix rounds are done; see
[the agentic development flow]($blob/$AGENT_BASE_BRANCH/docs/agentic-development.md).

## Summary

$summary

## Not done or done differently

${not_done:-Nothing.}

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
gh label create "$AGENT_LABEL" --color 5319E7 --description "Driven by the agent loop; remove to stop it" 2>/dev/null || true
url=$(gh pr create --draft --base "$AGENT_BASE_BRANCH" --head "$branch" --title "${title:-$branch}" \
  --body-file "$AGENT_TMP/pr.md" --label "$AGENT_LABEL")
pr=${url##*/}

echo "Implementation pushed ($(git rev-list --count "$start..HEAD") commits). The review starts after **$AGENT_CI_CHECK**." >"$AGENT_TMP/step.md"
post_step "$pr" implement 0 "$AGENT_TMP/step.md" "${results[@]}"
echo "$url"
