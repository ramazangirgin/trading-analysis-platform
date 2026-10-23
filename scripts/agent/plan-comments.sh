#!/usr/bin/env bash
# Step 2 of the agentic development flow (docs/agentic-development.md):
# the developer's comments on a plan pull request, and the session's answers.
#
#   scripts/agent/plan-comments.sh <pr | branch>                     (mise run agent:plan-comments …)
#   scripts/agent/plan-comments.sh --reply <comment id> <file>       reply on a review thread
#   scripts/agent/plan-comments.sh --comment <pr | branch> <file> <id>...
#                                      comment on the pull request, answering the comments or reviews <id>...
#
#   scripts/agent/plan-comments.sh --approve <pr | branch>
#                                      comment that the plan is approved at the branch's pushed head
#   scripts/agent/plan-comments.sh --approved <pr | branch>
#                                      print the head and exit 0 when the newest approval of the agent
#                                      account names the current head, exit 1 otherwise
#
# Without an option it prints what has no answer yet (plan_comments.py): the review threads that are
# not resolved and do not end in a reply of the session, and the pull request comments and review
# bodies (with their IDs) that no --comment names. The replies carry a hidden marker, which is how
# the next call knows they are answers; so the session answers through --reply / --comment only. It
# never resolves a thread: the developer does.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

REPLY_MARKER='<!-- plan-reply -->'
# Ids of the pull request comments and reviews a --comment answers, comma separated; empty for none.
answered_ids=

# The pull request number of a number or a plan branch.
pr_number() {
  local pr=$1
  if [[ $pr =~ ^[0-9]+$ ]]; then
    echo "$pr"
    return
  fi
  pr=$(pr_of_branch "$1")
  [ -n "$pr" ] || die "$1 has no open pull request"
  echo "$pr"
}

# The text of file $1 followed by the marker.
reply_body() {
  [ -f "$1" ] || die "no such file: $1"
  local marker=$REPLY_MARKER
  [ -z "$answered_ids" ] || marker="<!-- plan-reply to=$answered_ids -->"
  printf '%s\n\n%s\n' "$(cat "$1")" "$marker"
}

case ${1:-} in
  --approve)
    pr=$(pr_number "${2:?usage: $0 --approve <pr | branch>}")
    read -r branch head < <(gh pr view "$pr" --json headRefName,headRefOid --jq '"\(.headRefName) \(.headRefOid)"')
    git fetch --quiet origin "$branch"
    local_head=$(git rev-parse --verify --quiet "refs/heads/$branch" || true)
    if [ -n "$local_head" ] && [ "$local_head" != "$head" ]; then
      die "$branch is not at the pull request's head $head (unpushed or newer commits); bring them in line, show the plan again, then approve"
    fi
    py "$AGENT_DIR/plan_comments.py" approval "$head" >"$AGENT_TMP/approval.md"
    gh pr comment "$pr" --body-file "$AGENT_TMP/approval.md"
    ;;
  --approved)
    pr=$(pr_number "${2:?usage: $0 --approved <pr | branch>}")
    head=$(gh pr view "$pr" --json headRefOid --jq .headRefOid)
    gh pr view "$pr" --json comments >"$AGENT_TMP/pr.json"
    py "$AGENT_DIR/plan_comments.py" approved "$AGENT_TMP/pr.json" "$(agent_login)" "$head"
    ;;
  --reply)
    id=${2:?usage: $0 --reply <comment id> <file>}
    file=${3:?usage: $0 --reply <comment id> <file>}
    [[ $id =~ ^[0-9]+$ ]] || die "not a comment ID: $id"
    pr_url=$(gh api "repos/{owner}/{repo}/pulls/comments/$id" --jq .pull_request_url)
    reply_body "$file" >"$AGENT_TMP/reply.md"
    gh api -X POST "$pr_url/comments/$id/replies" -F body=@"$AGENT_TMP/reply.md" --jq .html_url
    ;;
  --comment)
    pr=$(pr_number "${2:?usage: $0 --comment <pr | branch> <file> <id>...}")
    file=${3:?usage: $0 --comment <pr | branch> <file> <id>...}
    (($# > 3)) || die "name the comments or reviews it answers: --comment <pr | branch> <file> <id>..."
    shift 3
    answered_ids=$(
      IFS=,
      echo "$*"
    )
    reply_body "$file" >"$AGENT_TMP/reply.md"
    gh pr comment "$pr" --body-file "$AGENT_TMP/reply.md"
    ;;
  *)
    pr=$(pr_number "${1:?usage: $0 <pr | branch>}")
    # shellcheck disable=SC2016 # GraphQL variables, not shell
    gh api graphql -F owner='{owner}' -F name='{repo}' -F number="$pr" -f query='
      query($owner: String!, $name: String!, $number: Int!) {
        repository(owner: $owner, name: $name) {
          pullRequest(number: $number) {
            reviewThreads(first: 100) {
              nodes {
                isResolved
                comments(first: 100) {
                  nodes { databaseId path line originalLine author { login } body createdAt }
                }
              }
            }
          }
        }
      }' >"$AGENT_TMP/threads.json"
    gh pr view "$pr" --json comments,reviews >"$AGENT_TMP/pr.json"
    out=$(py "$AGENT_DIR/plan_comments.py" "$AGENT_TMP/threads.json" "$AGENT_TMP/pr.json")
    if [ -n "$out" ]; then
      echo "$out"
    else
      echo "No unanswered comments on #$pr."
    fi
    ;;
esac
