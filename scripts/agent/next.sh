#!/usr/bin/env bash
# The review → fix loop of the agentic development flow
# (doc/coding-convention/repository-agentic-development.md), steps 5 to 9.
#
#   scripts/agent/next.sh plan/<issue>-<slug>     (or a pull request number)
#   scripts/agent/next.sh --dry-run <branch|pr>   print the next step only
#   scripts/agent/next.sh --check <branch|pr>     "run" if there may be a step to run, else "skip"
#
# Runs whatever comes next on the branch's pull request, read from its state, until a step pushes
# (CI then runs, and the agent-loop workflow calls this again when CI passed) or nothing is left:
#
#   CI pending on the head                         wait (exit, nothing done)
#   CI red                                         fix-ci.sh, up to AGENT_MAX_CI_FIXES times; stop
#                                                  if it pushes nothing
#   CI green, reviews = fixes < AGENT_MAX_ROUNDS   review.sh
#   CI green, reviews > fixes                      fix.sh
#   CI green, reviews = fixes = AGENT_MAX_ROUNDS   finalise.sh
#
# Only open draft pull requests labelled "agent" are touched: removing the label stops the loop.
# Over AGENT_MAX_CI_FIXES or AGENT_MAX_TOKENS, it comments, removes the label and stops.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

dry_run=false check=false
case ${1:-} in
  --dry-run) dry_run=true && shift ;;
  --check) check=true && shift ;;
esac
target=${1:?usage: $0 [--dry-run | --check] <plan branch | pr>}
if [[ $target =~ ^[0-9]+$ ]]; then
  pr=$target
else
  pr=$(pr_of_branch "$target")
  if [ -z "$pr" ]; then
    log "$target has no open pull request; nothing to do"
    ! $check || echo skip
    exit 0
  fi
fi

# --check: "run" when the pull request is the agents' and its CI finished, else "skip". Cheap, and
# needs only a read token: the agent-loop workflow skips its setup on "skip".
if $check; then
  gh pr view "$pr" --json state,isDraft,labels,headRefOid \
    --jq "[.state, .isDraft, ([.labels[].name] | index(\"$AGENT_LABEL\") != null), .headRefOid] | @tsv" |
    {
      read -r state draft labelled head
      if [ "$state" = OPEN ] && [ "$draft" = true ] && [ "$labelled" = true ] && [ "$(ci_status "$head")" != pending ]; then
        echo run
      else
        echo skip
      fi
    }
  exit 0
fi

# stop <reason>: hand the pull request back to the developer.
stop() {
  log "stopping: $1"
  if ! $dry_run; then
    printf '### Agent loop stopped\n\n%s\n\nThe pull request stays a draft. Continue by hand, or add the `%s` label and run the agent loop again.\n' \
      "$1" "$AGENT_LABEL" >"$AGENT_TMP/stop.md"
    post_step "$pr" stop 0 "$AGENT_TMP/stop.md"
    gh pr edit "$pr" --remove-label "$AGENT_LABEL" >/dev/null
  fi
  exit 0
}

next_step() {
  local info head ci reviews fixes
  info=$(gh pr view "$pr" --json state,isDraft,labels,headRefOid \
    --jq "[.state, .isDraft, ([.labels[].name] | index(\"$AGENT_LABEL\") != null), .headRefOid] | @tsv")
  read -r state draft labelled head <<<"$info"
  if [ "$state" != OPEN ] || [ "$draft" != true ] || [ "$labelled" != true ]; then
    echo none
    return
  fi
  if [ "$(tokens_used "$pr")" -gt "$AGENT_MAX_TOKENS" ]; then
    echo "stop over the token limit ($(tokens_used "$pr") > $AGENT_MAX_TOKENS)"
    return
  fi
  reviews=$(count_steps "$pr" review)
  fixes=$(count_steps "$pr" fix)
  # A review was posted for this head and its fix round is due: CI needs no new run for that.
  if [ "$reviews" -gt "$fixes" ]; then
    echo fix
    return
  fi
  ci=$(ci_status "$head")
  case $ci in
    pending) echo wait ;;
    failure)
      if [ "$(count_steps "$pr" ci-fix)" -ge "$AGENT_MAX_CI_FIXES" ]; then
        echo "stop CI still fails after $AGENT_MAX_CI_FIXES fix attempts"
      else
        echo fix-ci
      fi
      ;;
    success)
      if [ "$reviews" -lt "$AGENT_MAX_ROUNDS" ]; then echo review; else echo finalise; fi
      ;;
  esac
}

while true; do
  step=$(next_step)
  log "#$pr: next step: $step"
  $dry_run && exit 0
  before=$(gh pr view "$pr" --json headRefOid --jq .headRefOid)
  case $step in
    none | wait) exit 0 ;;
    stop*) stop "${step#stop }" ;;
    fix-ci) "$AGENT_DIR/fix-ci.sh" "$pr" ;;
    review) "$AGENT_DIR/review.sh" "$pr" ;;
    fix) "$AGENT_DIR/fix.sh" "$pr" ;;
    finalise)
      "$AGENT_DIR/finalise.sh" "$pr"
      exit 0
      ;;
  esac
  # A push starts CI, which starts this script again.
  [ "$(gh pr view "$pr" --json headRefOid --jq .headRefOid)" = "$before" ] || exit 0
  # Without a push CI does not run again: a red CI would stay red.
  [ "$step" != fix-ci ] || stop "The developer agent found no fix for the failing CI (see its comment above)."
done
