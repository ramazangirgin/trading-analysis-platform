#!/usr/bin/env bash
# The progress of a plan branch on its issue (docs/agentic-development.md, "Progress on the issue"):
#
#   scripts/agent/progress.sh show <branch | pr>       print the comment          (mise run agent:progress)
#   scripts/agent/progress.sh post <branch | pr>       post it on the issue, or update it there
#   scripts/agent/progress.sh begin <branch | pr> <step> [--at <epoch> [--limits "<probe line>"]]
#   scripts/agent/progress.sh probe                    print the limits now (the plan's start)
#   scripts/agent/progress.sh probe <branch | pr> <step>   save the limits at the start of a window
#   scripts/agent/progress.sh end <branch | pr> <step> [result]
#   scripts/agent/progress.sh note <branch | pr> <text>
#   scripts/agent/progress.sh orchestration <branch | pr> [result]
#   scripts/agent/progress.sh usage <branch | pr>      print the Usage section alone
#
# The step scripts record their steps, milestones and agent usage in
# .git/agent/<issue>-<slug>/progress.tsv themselves (lib.sh) and post as they go. begin, end and
# note are for the steps a Claude Code session runs on its own (develop-issue: the plan and its
# approval, the CI waits, the update with main); each posts the comment too. The session's own
# usage (end of plan and update with main, orchestration) is read from its transcript, its limits
# from a probe at the start and end of the window. The comment is one per
# plan branch, found by its marker and edited in place, so the issue does not fill up with them.
# post --async (the step scripts' milestones) skips the post while another one is running and lets
# that one post again when it is done; post waits for it.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

# "limit5h=<n> limit7d=<n>": the Claude limits now, from one headless Haiku call (a few tokens, in
# no row) whose rate_limit_event is read. Empty when the call fails: a limit that cannot be read is
# n/a. --no-session-persistence keeps the call out of the session transcripts read below. It uses
# the developer's login like the session does, never the DeepSeek endpoint.
probe() {
  local stream
  command -v claude >/dev/null || return 0
  stream=$(mktemp)
  (
    unset ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN ANTHROPIC_BASE_URL ANTHROPIC_MODEL
    claude -p --model haiku --output-format stream-json --verbose --no-session-persistence --tools "" \
      "Reply with ok." >"$stream" 2>/dev/null
  ) || true
  py "$AGENT_DIR/agent_json.py" limits "$stream" 2>/dev/null || true
  rm -f "$stream"
}

command=${1:?usage: $0 show|post|usage|begin|end|orchestration|note <branch | pr> [...]}
shift
# Without a branch: the plan window starts before its branch (and the number of its plans) is known.
if [ "$command" = probe ] && [ $# -eq 0 ]; then
  probe
  exit 0
fi
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
    --max-rounds "$AGENT_MAX_ROUNDS" --max-ci-fixes "$AGENT_MAX_CI_FIXES" "$@"
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

# The lock this script holds, released when it exits. Global, not local to post(): the EXIT trap
# runs after post() has returned, and with set -u a local would be unbound there.
lock=""

# Releases the lock and hands the script's exit status on to agent_exit, which reads $? to record
# the step as failed (an rm in the trap before it would turn every status into 0).
release_lock() {
  local status=$?
  [ -z "$lock" ] || rm -f "$lock"
  return "$status"
}

# One post at a time (progress.lock holds the poster's pid); a post asked for meanwhile is pending.
# Nothing is posted with AGENT_ISSUE_PROGRESS=0.
post() {
  [ "$AGENT_ISSUE_PROGRESS" = 1 ] || return 0
  local pending=$AGENT_STATE/progress.pending tries=0 holder
  lock=$AGENT_STATE/progress.lock
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
  trap 'release_lock; agent_exit' EXIT
  rm -f "$pending"
  post_once
  while [ -e "$pending" ]; do
    rm -f "$pending"
    post_once >/dev/null
  done
}

limit_value() { printf '%s\n' "$2" | tr ' ' '\n' | sed -n "s/^$1=//p"; }

# limit_keys <probe before> <probe after>: the marker keys of the windows both probes read.
limit_keys() {
  local key before after
  for key in 5h 7d; do
    before=$(limit_value "limit$key" "$1")
    after=$(limit_value "limit$key" "$2")
    if [ -n "$before" ] && [ -n "$after" ]; then
      printf ' limit%s_before=%s limit%s_after=%s' "$key" "$before" "$key" "$after"
    fi
  done
}

# The newest transcript of the session that runs develop-issue: in the checkout it was started in,
# the main one unless AGENT_SESSION_DIR says otherwise (the folder of the issue is a worktree of it).
session_transcript() {
  local dir=${AGENT_SESSION_DIR:-}
  [ -n "$dir" ] || dir=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)
  py "$AGENT_DIR/agent_json.py" transcript "$dir" 2>/dev/null || true
}

step_slug() { printf '%s' "${1// /-}"; }

# session_usage <step>: the usage event of a step the session ran itself (plan, update with main),
# from its begin until now, with the limits read by the probes at both ends. No transcript, no
# usage: the row is n/a.
session_usage() {
  local step=$1 from transcript usage before=""
  from=$(awk -F'\t' -v s="$step" '$2 == "begin" && $3 == "" && $4 == s { at = $1 } END { print at }' "$file")
  transcript=$(session_transcript)
  if [ -z "$from" ] || [ -z "$transcript" ]; then
    log "no session transcript found: the usage of '$step' is n/a"
    return 0
  fi
  usage=$(py "$AGENT_DIR/agent_json.py" session-usage "$transcript" "$from" "$(($(date +%s) + 1))" 2>/dev/null || true)
  if [ -z "$usage" ]; then
    log "no session usage found for '$step': n/a"
    return 0
  fi
  before=$(cat "$AGENT_STATE/probe-$(step_slug "$step")" 2>/dev/null || true)
  usage+=$(limit_keys "$before" "$(probe)")
  AGENT_PROGRESS=$file progress_event usage "$step" "" "$usage"
}

case $command in
  show) render ;;
  post) post ;;
  usage) render --only usage ;;
  begin)
    step=${1:?usage: $0 begin <branch | pr> <step> [--at <epoch> [--limits "<probe line>"]]}
    at=$(date +%s)
    if [ "${2:-}" = --at ]; then
      # The probe line taken when the window started; without one, the limits before are n/a.
      at=${3:?--at needs an epoch}
      if [ "${4:-}" = --limits ]; then
        printf '%s\n' "${5:-}" >"$AGENT_STATE/probe-$(step_slug "$step")"
      fi
    else
      probe >"$AGENT_STATE/probe-$(step_slug "$step")" || true
    fi
    [[ $at =~ ^[0-9]+$ ]] || die "not an epoch: $at"
    printf '%s\tbegin\t\t%s\t\n' "$at" "$(printf '%s' "$step" | tr '\t\n' '  ')" >>"$file"
    post
    ;;
  probe)
    step=${1:?usage: $0 probe <branch | pr> <step>}
    probe >"$AGENT_STATE/probe-$(step_slug "$step")" || true
    ;;
  end)
    step=${1:?usage: $0 end <branch | pr> <step> [result]}
    case $step in plan | "update with main") session_usage "$step" ;; esac
    AGENT_PROGRESS=$file progress_event end "" "$step" "${2:-}"
    post
    ;;
  orchestration)
    result=${1:-done}
    if grep -q $'\tend\t\torchestration\t' "$file" 2>/dev/null; then
      log "the orchestration is recorded already"
      exit 0
    fi
    transcript=$(session_transcript)
    usage=""
    if [ -n "$transcript" ]; then
      usage=$(py "$AGENT_DIR/agent_json.py" orchestration-usage "$transcript" "$file" "$(($(date +%s) + 1))" 2>/dev/null || true)
    fi
    if [ -z "$usage" ]; then
      log "no session usage found: the orchestration is n/a"
      exit 0
    fi
    before=$(cat "$AGENT_STATE/probe-orchestration" 2>/dev/null || true)
    usage+=$(limit_keys "$before" "$(probe)")
    from=${usage%% *}
    printf '%s\tbegin\t\torchestration\t\n' "${from#from=}" >>"$file"
    AGENT_PROGRESS=$file progress_event usage orchestration "" "${usage#* }"
    AGENT_PROGRESS=$file progress_event end "" orchestration "$result"
    post
    ;;
  note)
    AGENT_PROGRESS=$file progress_event note "" "" "${1:?usage: $0 note <branch | pr> <text>}"
    post
    ;;
  *) die "unknown command: $command (show, post, usage, begin, probe, end, orchestration or note)" ;;
esac
