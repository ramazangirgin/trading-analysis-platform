#!/usr/bin/env bash
# Helper of the renovate-update skill (SKILL.md next to it): drives the hosted Renovate app through
# its dependency dashboard issue and waits for the results, with the user's own gh login.
#
#   dashboard.sh show              the dashboard's checkboxes, by section
#   dashboard.sh tick [--dry-run]  tick every checkbox that brings mature updates in, and "run again"
#   dashboard.sh wait [minutes]    wait until Renovate has processed every ticked checkbox (default 20)
#   dashboard.sh prs               Renovate's open pull requests
#   dashboard.sh wait-ci <pr>      wait for the CI run on the pull request's head; exit 1 when it failed
#
# Releases younger than minimumReleaseAge (.github/renovate.json5) are listed under "Pending Status
# Checks"; their checkbox would create them anyway, so `tick` never touches it. Neither does it tick
# a closed or ignored update ("recreate"), nor "rebase" a branch someone else has pushed to: a
# rebase would recreate it from scratch and drop those commits.
set -euo pipefail

die() {
  echo "dashboard.sh: $*" >&2
  exit 1
}

repo=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
base=$(gh repo view --json defaultBranchRef --jq .defaultBranchRef.name)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

issue() {
  local n
  n=$(gh issue list --author app/renovate --state open --search 'in:title "Dependency Dashboard"' \
    --json number --jq '.[0].number // empty')
  [ -n "$n" ] || die "no open Dependency Dashboard issue by renovate[bot]: is the Renovate app installed, and not in silent mode?"
  echo "$n"
}

body() {
  gh issue view "$1" --json body --jq .body
}

# The marker of a checkbox line ("unschedule-branch=renovate/all", "manual job"), or nothing.
marker() {
  sed -nE 's/^ *- \[[ x]\] <!-- ([^>]*[^ ]) -->.*/\1/p' <<<"$1"
}

# True when every commit on the branch that is not on the base branch is Renovate's own.
renovate_only() {
  gh api "repos/$repo/compare/$base...$1" \
    --jq '[.commits[] | .author.login // "?"] | all(. == "renovate[bot]")' 2>/dev/null | grep -qx true
}

cmd_show() {
  local n
  n=$(issue)
  echo "Dashboard: https://github.com/$repo/issues/$n"
  body "$n" | grep -E '^## |^ *- \[[ x]\] <!--' | sed -E 's/<!-- [^>]* -->//; s/^ *//'
}

cmd_tick() {
  local dry=${1:-} n line m ticked=0
  n=$(issue)
  body "$n" >"$tmp/body.md"
  : >"$tmp/new.md"
  while IFS= read -r line || [ -n "$line" ]; do
    m=$(marker "$line")
    if [[ $line =~ ^\ *-\ \[\ \] ]] && [ -n "$m" ]; then
      case $m in
        unschedule-branch=* | unlimit-branch=* | approve-branch=* | retry-branch=* | \
          create-all-awaiting-schedule-prs | create-all-rate-limited-prs | approve-all-pending-prs | \
          "manual job")
          line=${line/- \[ \]/- [x]}
          ticked=$((ticked + 1))
          echo "tick:  $m" ;;
        rebase-branch=*)
          if renovate_only "${m#rebase-branch=}"; then
            line=${line/- \[ \]/- [x]}
            ticked=$((ticked + 1))
            echo "tick:  $m"
          else
            echo "leave: $m (has commits by others; a rebase would drop them)"
          fi ;;
        *) echo "leave: $m" ;;
      esac
    fi
    printf '%s\n' "$line" >>"$tmp/new.md"
  done <"$tmp/body.md"
  if [ "$ticked" -eq 0 ]; then
    echo "Nothing to tick: a requested run may still be pending (see: dashboard.sh show)."
  elif [ "$dry" = --dry-run ]; then
    echo "Dry run: the dashboard was not changed."
  else
    gh issue edit "$n" --body-file "$tmp/new.md" >/dev/null
    echo "Ticked $ticked checkbox(es) on https://github.com/$repo/issues/$n"
  fi
}

cmd_wait() {
  local minutes=${1:-20} n deadline left
  n=$(issue)
  deadline=$(($(date +%s) + minutes * 60))
  while :; do
    left=$(body "$n" | grep -cE '^ *- \[x\] <!--' || true)
    if [ "$left" -eq 0 ]; then
      echo "Renovate processed the dashboard."
      cmd_prs
      return 0
    fi
    [ "$(date +%s)" -lt "$deadline" ] || die "still $left ticked checkbox(es) after $minutes minutes: is the app running (developer.mend.io job log)?"
    sleep 30
  done
}

cmd_prs() {
  gh pr list --author app/renovate --state open \
    --json number,title,headRefName,mergeStateStatus,url \
    --jq '.[] | "#\(.number)\t\(.headRefName)\t\(.mergeStateStatus)\t\(.title)\t\(.url)"'
}

cmd_wait_ci() {
  local pr=${1:?usage: dashboard.sh wait-ci <pr>} sha run deadline conclusion
  sha=$(gh pr view "$pr" --json headRefOid --jq .headRefOid)
  deadline=$(($(date +%s) + 600))
  while :; do
    run=$(gh run list --workflow ci.yml --commit "$sha" --event pull_request --limit 1 \
      --json databaseId --jq '.[0].databaseId // empty')
    [ -z "$run" ] || break
    [ "$(date +%s)" -lt "$deadline" ] || die "no CI run for $sha after 10 minutes"
    sleep 20
  done
  echo "CI run $run on ${sha:0:12}: https://github.com/$repo/actions/runs/$run"
  gh run watch "$run" --interval 30 >/dev/null 2>&1 || true
  conclusion=$(gh run view "$run" --json conclusion --jq .conclusion)
  echo "Conclusion: $conclusion"
  if [ "$conclusion" != success ]; then
    gh run view "$run" --json jobs --jq '.jobs[] | select(.conclusion == "failure") | "failed: \(.name)"'
    return 1
  fi
}

case ${1:-} in
  show) cmd_show ;;
  tick) cmd_tick "${2:-}" ;;
  wait) cmd_wait "${2:-}" ;;
  prs) cmd_prs ;;
  wait-ci) cmd_wait_ci "${2:-}" ;;
  *) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac
