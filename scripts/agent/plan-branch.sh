#!/usr/bin/env bash
# Step 2 of the agentic development flow (docs/agentic-development.md):
# pushes a plan on its own branch.
#
#   scripts/agent/plan-branch.sh .plans/<issue>-<slug>.md
#
# Creates plan/<issue>-<slug> from origin/$AGENT_BASE_BRANCH (main) with one commit that adds only the plan file, pushes
# it and comments the branch link on the issue. Works in a temporary worktree, so the current
# checkout is left alone and the plan file may be untracked. Fails if the branch exists already.
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

url="https://github.com/$(repo_slug)/blob/$branch/.plans/$name.md"
gh issue comment "$issue" --body "Plan for review: [\`.plans/$name.md\`]($url) on branch \`$branch\`.

Edit it on the branch, or start the implementation:
\`mise run agent:run $branch\`" >/dev/null
git branch --quiet -D "$branch"
echo "$url"
