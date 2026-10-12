#!/usr/bin/env bash
# The progress of a plan branch on its issue (docs/agentic-development.md, "Progress on the issue"):
#
#   scripts/agent/progress.sh show <branch | pr>       print the comment          (mise run agent:progress)
#   scripts/agent/progress.sh post <branch | pr>       post it on the issue, or update it there
#   scripts/agent/progress.sh begin <branch | pr> <step> [--at <epoch>]
#   scripts/agent/progress.sh end <branch | pr> <step> [result]
#   scripts/agent/progress.sh note <branch | pr> <text>
#
# The step scripts record their steps, milestones and agent usage in
# .git/agent/<issue>-<slug>/progress.tsv themselves (lib.sh) and post as they go. begin, end and
# note are for the steps a Claude Code session runs on its own (develop-issue: the plan and its
# approval, the CI waits, the update with main); each posts the comment too. The comment is one per
# plan branch, found by its marker and edited in place, so the issue does not fill up with them.
# post --async (the step scripts' milestones) skips the post while another one is running and lets
# that one post again when it is done; post waits for it.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

command=${1:?usage: $0 show|post|begin|end|note <branch | pr> [...]}
shift
async=false
if [ "$command" = post ] && [ "${1:-}" = --async ]; then
  async=true
  shift
fi
target=${1:?usage: $0 $command <branch | pr> [...]}
shift
if [[ $target =~ ^[0-9]+$ ]]; then
  branch=$(gh pr view "$target" --json headRefName --jq .headRefName)
else
  branch=$target
fi
issue=$(issue_of_branch "$branch")
use_state --no-log "$branch"
# Not AGENT_PROGRESS: this script's own failures are no milestones of the run.
file=$AGENT_STATE/progress.tsv
name=$(plan_name_of_branch "$branch")
marker="<!-- agent-progress plan=$name -->"

render() {
  local pr
  pr=$(gh pr list --head "$branch" --state all --limit 1 --json number --jq '.[0].number // empty' 2>/dev/null || true)
  py "$AGENT_DIR/progress.py" render "$file" "$marker" "$branch" ${pr:+--pr "$pr"} \
    --max-rounds "$AGENT_MAX_ROUNDS" --max-ci-fixes "$AGENT_MAX_CI_FIXES"
}

# The issue comment with the marker, created or edited; its ID is kept in progress.comment.
post_once() {
  local body=$AGENT_STATE/progress.md id
  render >"$body"
  id=$(cat "$AGENT_STATE/progress.comment" 2>/dev/null || true)
  if [ -n "$id" ] && gh api -X PATCH "repos/{owner}/{repo}/issues/comments/$id" -F "body=@$body" >/dev/null 2>&1; then
    return 0
  fi
  id=$(gh api --paginate "repos/{owner}/{repo}/issues/$issue/comments" \
    --jq ".[] | select(.user.login == \"$(agent_login)\" and (.body | startswith(\"$marker\"))) | .id" | tail -n 1)
  if [ -n "$id" ]; then
    gh api -X PATCH "repos/{owner}/{repo}/issues/comments/$id" -F "body=@$body" >/dev/null
  else
    id=$(gh api "repos/{owner}/{repo}/issues/$issue/comments" -F "body=@$body" --jq .id)
  fi
  echo "$id" >"$AGENT_STATE/progress.comment"
  echo "https://github.com/$(repo_slug)/issues/$issue#issuecomment-$id"
}

# One post at a time (progress.lock holds the poster's pid); a post asked for meanwhile is pending.
# Nothing is posted with AGENT_ISSUE_PROGRESS=0.
post() {
  [ "$AGENT_ISSUE_PROGRESS" = 1 ] || return 0
  local lock=$AGENT_STATE/progress.lock pending=$AGENT_STATE/progress.pending tries=0 holder
  while ! (
    set -o noclobber
    echo $$ >"$lock"
  ) 2>/dev/null; do
    holder=$(cat "$lock" 2>/dev/null || true)
    if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
      rm -f "$lock" # a poster that died
      continue
    fi
    if $async; then
      touch "$pending"
      exit 0
    fi
    tries=$((tries + 1))
    [ "$tries" -lt 60 ] || die "another post holds $lock"
    sleep 1
  done
  trap 'rm -f "$lock"; agent_exit' EXIT
  rm -f "$pending"
  post_once
  while [ -e "$pending" ]; do
    rm -f "$pending"
    post_once >/dev/null
  done
}

case $command in
  show) render ;;
  post) post ;;
  begin)
    step=${1:?usage: $0 begin <branch | pr> <step> [--at <epoch>]}
    at=$(date +%s)
    if [ "${2:-}" = --at ]; then at=${3:?--at needs an epoch}; fi
    [[ $at =~ ^[0-9]+$ ]] || die "not an epoch: $at"
    printf '%s\tbegin\t\t%s\t\n' "$at" "$(printf '%s' "$step" | tr '\t\n' '  ')" >>"$file"
    post
    ;;
  end)
    step=${1:?usage: $0 end <branch | pr> <step> [result]}
    AGENT_PROGRESS=$file progress_event end "" "$step" "${2:-}"
    post
    ;;
  note)
    AGENT_PROGRESS=$file progress_event note "" "" "${1:?usage: $0 note <branch | pr> <text>}"
    post
    ;;
  *) die "unknown command: $command (show, post, begin, end or note)" ;;
esac
