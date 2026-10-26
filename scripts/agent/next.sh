#!/usr/bin/env bash
# The review → fix loop of the agentic development flow
# (docs/agentic-development.md), steps 5 to 9.
#
#   scripts/agent/next.sh plan/<issue>-<slug>     (or a pull request number)
#   scripts/agent/next.sh --dry-run <branch|pr>   print the next step only
#
# Runs the agents' pull request to its end, one step after the other, each read from the pull
# request's state; after every push it waits for CI (up to AGENT_CI_TIMEOUT minutes):
#
#   CI pending on the head                         wait, checking every 30 seconds
#   CI red                                         fix-ci.sh, up to AGENT_MAX_CI_FIXES times; stop
#                                                  if it pushes nothing
#   CI green, reviews = fixes < AGENT_MAX_ROUNDS   review.sh
#   CI green, reviews > fixes                      fix.sh
#   CI green, reviews = fixes = AGENT_MAX_ROUNDS   finalise.sh
#   CI green, the latest review approved the head  finalise.sh (no findings: no fix round, and no
#                                                  further review of the same commit; an update
#                                                  with main keeps the approval of its old head)
#   before a review or the finalise, branch behind the base, not updated in this round yet
#                                                  update-main.sh (its push starts CI again)
#   CI pending, the pull request conflicts with the base
#                                                  update-main.sh (GitHub runs no CI on a conflict);
#                                                  stop if it cannot resolve it. Only behind: wait.
#                                                  A head counts only after one 30-second wait
#
# A pull request still in the plan stage (labelled "plan") prints "waiting for: plan approval" and
# nothing runs. Only open draft pull requests labelled "agent" are touched: removing the label stops the loop (at
# the next step). Over AGENT_MAX_CI_FIXES or AGENT_MAX_TOKENS, it comments, removes the label and
# stops. Milestone lines (agent: >> …, see lib.sh) say how far it is: the next step, CI waited
# for / passed / failed (with the failed jobs) per head, and why it stopped. Interrupted (Ctrl+C), it continues where it was when started again. Its log, the agents'
# streams and its lock are in .git/agent/<issue>-<slug>/ (run.log, run.pid); a second run on the
# same branch is refused.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

dry_run=false
if [ "${1:-}" = --dry-run ]; then
  dry_run=true
  shift
fi
target=${1:?usage: $0 [--dry-run] <plan branch | pr>}
if [[ $target =~ ^[0-9]+$ ]]; then
  pr=$target
  branch=$(gh pr view "$pr" --json headRefName --jq .headRefName)
else
  branch=$target
  pr=$(pr_of_branch "$branch")
fi
! $dry_run || AGENT_NO_PROGRESS=1
use_state "$branch"
if [ -z "$pr" ]; then
  log "$target has no open pull request; nothing to do"
  exit 0
fi
if [ "$(pr_stage "$pr")" = plan ]; then
  echo "waiting for: plan approval (mise run agent:run $branch)"
  exit 0
fi
$dry_run || lock_run

# stop <reason>: hand the pull request back to the developer.
stop() {
  milestone "stopping: $1"
  if ! $dry_run; then
    printf '### Agent loop stopped\n\n%s\n\nThe pull request stays a draft. Continue by hand, or add the `%s` label and run the agent loop again.\n' \
      "$1" "$AGENT_LABEL" >"$AGENT_TMP/stop.md"
    post_step "$pr" stop 0 "$AGENT_TMP/stop.md"
    gh pr edit "$pr" --remove-label "$AGENT_LABEL" >/dev/null
  fi
  exit 0
}

use_pr_base "$pr"

# behind <head>: the pull request's head lacks commits of the base branch.
behind() {
  git fetch --quiet origin "$AGENT_BASE_BRANCH" "$branch" || return 1
  ! git merge-base --is-ancestor "origin/$AGENT_BASE_BRANCH" "$1"
}

# A review or the finalise, when the branch is behind its base and was not updated in this round
# yet, becomes an update with main first (at most one per round, so a busy base cannot keep the
# loop merging).
next_step() {
  local step head reviews
  step=$(planned_step)
  case $step in review | finalise)
    head=$(gh pr view "$pr" --json headRefOid --jq .headRefOid)
    reviews=$(count_steps "$pr" review)
    if ! pr_steps "$pr" | awk -v r="$reviews" '$1 == "update-main" && $2 == r { found = 1 } END { exit !found }' &&
      behind "$head"; then
      echo update-main
      return
    fi
    ;;
  wait)
    # A pull request that conflicts with its base gets no CI run: waiting could only time out.
    # GitHub computes the merge state in the background, so a head's state counts only once the
    # loop has waited for that head: right after our own push it may still show the old conflict.
    head=$(gh pr view "$pr" --json headRefOid --jq .headRefOid)
    if { $dry_run || [ "$head" = "$waiting_head" ]; } &&
      [ "$(gh pr view "$pr" --json mergeable,mergeStateStatus 2>/dev/null | py "$AGENT_DIR/merge_state.py" wait-step)" = update ]; then
      echo update-main-conflict
      return
    fi
    ;;
  esac
  echo "$step"
}

planned_step() {
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
  # The review agent approved this very commit: nothing to fix, nothing new to review.
  if [ "$(approved_head "$pr")" = "$head" ]; then
    if [ "$(ci_status "$head")" = success ]; then echo finalise; else echo wait; fi
    return
  fi
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

waited=0
waiting_head="" # the head a "waiting for CI" milestone was written for
reported_head="" # the head CI's result was written for
while true; do
  step=$(next_step)
  if [ "$step" = wait ]; then
    [ "$waited" -lt $((AGENT_CI_TIMEOUT * 60)) ] || stop "CI did not finish within $AGENT_CI_TIMEOUT minutes."
    head=$(gh pr view "$pr" --json headRefOid --jq .headRefOid)
    if [ "$head" != "$waiting_head" ]; then
      # Each head's CI wait is a step of its own; a newer head replaces the one waited for.
      $dry_run || step_end "superseded by ${head:0:7}"
      $dry_run || step_begin "CI ${head:0:7}"
      milestone "#$pr: waiting for $AGENT_CI_CHECK on ${head:0:7}"
      waiting_head=$head
    elif [ $((waited % 120)) -eq 0 ]; then
      log "#$pr: waiting for $AGENT_CI_CHECK ($((waited / 60)) of $AGENT_CI_TIMEOUT min)"
    fi
    $dry_run && exit 0
    sleep 30
    waited=$((waited + 30))
    continue
  fi
  waited=0
  # CI's result on the head, once per head, where the next step depends on it.
  case $step in review | finalise | fix-ci | update-main)
    head=$(gh pr view "$pr" --json headRefOid --jq .headRefOid)
    if ! $dry_run && [ "$head" != "$reported_head" ]; then
      reported_head=$head
      if [ "$step" = fix-ci ]; then
        failed_jobs=$(ci_failed_jobs "$head")
        milestone "#$pr: CI failed on ${head:0:7}: $failed_jobs"
        step_end "failed: $failed_jobs"
      else
        milestone "#$pr: CI passed on ${head:0:7}"
        step_end passed
      fi
    fi
    ;;
  esac
  if [ "$step" = update-main-conflict ]; then
    if $dry_run; then
      log "#$pr: $branch conflicts with $AGENT_BASE_BRANCH"
    else
      milestone "#$pr: $branch conflicts with $AGENT_BASE_BRANCH"
      step_end "conflicts with $AGENT_BASE_BRANCH"
    fi
  fi
  $dry_run || step_end # a CI wait the next step does not depend on (fix, stop, none)
  case $step in
    update-main | update-main-conflict) milestone "#$pr: next step: update with main" ;;
    *) milestone "#$pr: next step: $step" ;;
  esac
  $dry_run && exit 0
  before=$(gh pr view "$pr" --json headRefOid --jq .headRefOid)
  case $step in
    none) exit 0 ;;
    stop*) stop "${step#stop }" ;;
    fix-ci) "$AGENT_DIR/fix-ci.sh" "$pr" ;;
    update-main) "$AGENT_DIR/update-main.sh" "$pr" ;;
    update-main-conflict)
      "$AGENT_DIR/update-main.sh" "$pr" ||
        stop "$branch conflicts with $AGENT_BASE_BRANCH and the update with $AGENT_BASE_BRANCH could not resolve it (see run.log). The merge stays local: finish it with /merge-main-into-branch or drop it with git reset --keep @{u}, then add the agent label and run the agent loop again."
      ;;
    review) "$AGENT_DIR/review.sh" "$pr" ;;
    fix) "$AGENT_DIR/fix.sh" "$pr" ;;
    finalise)
      "$AGENT_DIR/finalise.sh" "$pr"
      exit 0
      ;;
  esac
  # Without a push CI does not run again: a red CI would stay red.
  if [ "$step" = fix-ci ] && [ "$(gh pr view "$pr" --json headRefOid --jq .headRefOid)" = "$before" ]; then
    stop "The developer agent found no fix for the failing CI (see its comment above)."
  fi
  # An update that pushed nothing cannot clear the conflict, and asking again would loop at once.
  if [ "$step" = update-main-conflict ] && [ "$(gh pr view "$pr" --json headRefOid --jq .headRefOid)" = "$before" ]; then
    stop "GitHub reports a conflict with $AGENT_BASE_BRANCH but $branch already contains it and merges cleanly locally; check the pull request on GitHub, then run the agent loop again."
  fi
done
