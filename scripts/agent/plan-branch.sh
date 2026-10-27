#!/usr/bin/env bash
# Step 2 of the agentic development flow (docs/agentic-development.md):
# pushes a plan on its own branch.
#
#   scripts/agent/plan-branch.sh .plans/<issue>-<slug>.md
#
# Creates plan/<issue>-<slug> from origin/$AGENT_BASE_BRANCH (main) with one commit that adds only the plan file, pushes
# it, opens a draft pull request labelled "plan" for the plan's review (plan-comments.sh reads the
# comments; the implementation later takes the pull request over) and comments its link on the
# issue. Prints the pull request's URL. Works in a temporary worktree, so the current
# checkout is left alone and the plan file may be untracked. Fails if the branch exists already.
# An untracked plan file is removed once pushed: the branch holds it, and a leftover copy would
# stop agent:run (it needs a clean working tree).
file=${1:?usage: $0 .plans/<issue>-<slug>.md}
[ -f "$file" ] || {
  echo "agent: no such file: $file" >&2
  exit 1
}
# lib.sh changes to the repository root.
file=$(cd "$(dirname "$file")" && pwd)/$(basename "$file")
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

name=$(basename "$file" .md)
[[ $name =~ ^[0-9]+-[a-z0-9-]+$ ]] || die "plan file name must be <issue>-<slug>.md: $file"
# A warning only: the planning session shows it in the review.
py "$AGENT_DIR/plan.py" oversized "$file" "$AGENT_MAX_WP_FILES" | while IFS= read -r line; do
  echo "agent: ${line#- }: consider splitting it" >&2
done
branch=plan/$name
issue=$(issue_of_branch "$branch")

git fetch --quiet origin "$AGENT_BASE_BRANCH"
if git ls-remote --exit-code --heads origin "$branch" >/dev/null; then
  die "$branch exists already; edit the plan on that branch instead"
fi

worktree=$AGENT_TMP/worktree
git worktree add --quiet -b "$branch" "$worktree" "origin/$AGENT_BASE_BRANCH"
trap 'git worktree remove --force "$worktree"' EXIT
mkdir -p "$worktree/.plans"
cp "$file" "$worktree/.plans/$name.md"
title=$(sed -n 's/^# Plan: //p' "$file" | head -n 1)
git -C "$worktree" add ".plans/$name.md"
git -C "$worktree" commit --quiet -m "Plan for #$issue: ${title:-$name}"
git -C "$worktree" push --quiet --set-upstream origin "$branch"
# The worktree's branch is not needed locally; the remote one is the plan.
git -C "$worktree" switch --quiet --detach

blob="https://github.com/$(repo_slug)/blob/$branch/.plans/$name.md"
cat >"$AGENT_TMP/pr.md" <<EOF
Plan for #$issue: [\`.plans/$name.md\`]($blob).

Review the plan here: comment inline on the plan's lines (*Files changed*) or on this pull request, or
tell the planning session in Claude Code. It changes the plan on this branch and answers each comment.

Approve it in the planning session, or start the agents:
\`mise run agent:run $branch\`

The agents push the implementation to this pull request and change its label from \`$AGENT_PLAN_LABEL\` to \`$AGENT_LABEL\`.
EOF
gh label create "$AGENT_PLAN_LABEL" --color FBCA04 --description "A plan under review" 2>/dev/null || true
url=$(gh pr create --draft --base "$AGENT_BASE_BRANCH" --head "$branch" --title "Plan: ${title:-$name}" \
  --body-file "$AGENT_TMP/pr.md" --label "$AGENT_PLAN_LABEL")
gh issue comment "$issue" --body "Plan for review: [\`.plans/$name.md\`]($blob) on branch \`$branch\`, draft pull request $url.

Comment on the pull request, or start the implementation:
\`mise run agent:run $branch\`" >/dev/null
git branch --quiet -D "$branch"
# The plan now lives on its branch; drop the untracked local copy. A tracked file is left alone.
if ! git ls-files --error-unmatch "$file" >/dev/null 2>&1; then
  rm "$file"
fi
"$AGENT_DIR/dashboard.sh" update --wait "$branch" >/dev/null || true
echo "$url"
