#!/usr/bin/env bash
# Step 2 of the agentic development flow (docs/agentic-development.md):
# the developer's comments on a plan pull request, and the session's answers.
#
#   scripts/agent/plan-comments.sh <pr | branch>                     (mise run agent:plan-comments …)
#   scripts/agent/plan-comments.sh --reply <comment id> <file>       reply on a review thread
#   scripts/agent/plan-comments.sh --comment <pr | branch> <file>    comment on the pull request
#
# Without an option it prints what has no answer yet (plan_comments.py): the review threads that are
# not resolved and do not end in a reply of the session, and the pull request comments and review
# bodies that came after its latest reply. The replies carry a hidden marker, which is how the next
# call knows they are answers; so the session answers through --reply / --comment only. It never
# resolves a thread: the developer does.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

REPLY_MARKER='<!-- plan-reply -->'

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
  printf '%s\n\n%s\n' "$(cat "$1")" "$REPLY_MARKER"
}

case ${1:-} in
  --reply)
    id=${2:?usage: $0 --reply <comment id> <file>}
    file=${3:?usage: $0 --reply <comment id> <file>}
    [[ $id =~ ^[0-9]+$ ]] || die "not a comment ID: $id"
    pr_url=$(gh api "repos/{owner}/{repo}/pulls/comments/$id" --jq .pull_request_url)
    reply_body "$file" >"$AGENT_TMP/reply.md"
    gh api -X POST "$pr_url/comments/$id/replies" -F body=@"$AGENT_TMP/reply.md" --jq .html_url
    ;;
  --comment)
    pr=$(pr_number "${2:?usage: $0 --comment <pr | branch> <file>}")
    file=${3:?usage: $0 --comment <pr | branch> <file>}
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
