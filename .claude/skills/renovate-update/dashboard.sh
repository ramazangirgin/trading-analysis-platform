#!/usr/bin/env bash
# Helper of the renovate-update skill (SKILL.md next to it): drives the hosted Renovate app through
# its dependency dashboard issue and waits for the results, with the user's own gh login.
#
#   dashboard.sh show                 the dashboard's checkboxes, by section
#   dashboard.sh request [--dry-run]  tick every checkbox that brings mature updates in, then start
#                                     the Renovate run workflow (renovate-run.yml): it ticks "run again"
#   dashboard.sh wait [minutes]       wait until Renovate has processed every ticked checkbox (default 60)
#   dashboard.sh prs                  Renovate's open pull requests
#   dashboard.sh wait-ci <pr>         wait for the CI run on the pull request's head; exit 1 when it failed
#
# Releases younger than minimumReleaseAge (.github/renovate.json5) are listed under "Pending Status
# Checks"; their checkbox would create them anyway, so `request` never touches it. Neither does it tick
# a closed or ignored update ("recreate"), nor "rebase" a branch someone else has pushed to: a
# rebase would recreate it from scratch and drop those commits.
#
# Mend takes a requested run up from its own queue: within a minute at times, after an hour at
# others (2026-10-07), and nothing on GitHub's side shows which. Hence `wait`'s long default, its
# progress lines, and the steps it prints when it gives up.
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

cmd_request() {
  local dry=${1:-} n line m ticked=0 stale=0 since run
  n=$(issue)
  body "$n" >"$tmp/body.md"
  : >"$tmp/new.md"
  while IFS= read -r line || [ -n "$line" ]; do
    m=$(marker "$line")
    if [ "$m" = "manual job" ]; then
      # The workflow refuses a ticked box: untick one left from a request the app never took up.
      if [[ $line =~ ^\ *-\ \[x\] ]]; then
        line=${line/- \[x\]/- [ ]}
        stale=1
        echo "untick: $m (left from an earlier request; the workflow ticks it again)"
      fi
    elif [[ $line =~ ^\ *-\ \[\ \] ]] && [ -n "$m" ]; then
      case $m in
        unschedule-branch=* | unlimit-branch=* | approve-branch=* | retry-branch=* | \
          create-all-awaiting-schedule-prs | create-all-rate-limited-prs | approve-all-pending-prs)
          line=${line/- \[ \]/- [x]}
          ticked=$((ticked + 1))
          echo "tick:   $m" ;;
        rebase-branch=*)
          if renovate_only "${m#rebase-branch=}"; then
            line=${line/- \[ \]/- [x]}
            ticked=$((ticked + 1))
            echo "tick:   $m"
          else
            echo "leave:  $m (has commits by others; a rebase would drop them)"
          fi ;;
        *) echo "leave:  $m" ;;
      esac
    fi
    printf '%s\n' "$line" >>"$tmp/new.md"
  done <"$tmp/body.md"
  if [ "$dry" = --dry-run ]; then
    echo "Dry run: the dashboard was not changed and the workflow was not started."
    return 0
  fi
  if [ $((ticked + stale)) -gt 0 ]; then
    gh issue edit "$n" --body-file "$tmp/new.md" >/dev/null
    echo "Edited https://github.com/$repo/issues/$n: $ticked ticked, $stale unticked"
  fi
  since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  gh workflow run renovate-run.yml --ref "$base" >/dev/null
  for _ in $(seq 30); do
    run=$(gh run list --workflow renovate-run.yml --event workflow_dispatch --limit 5 \
      --json databaseId,createdAt --jq "[.[] | select(.createdAt >= \"$since\")][0].databaseId // empty")
    [ -z "$run" ] || break
    sleep 5
  done
  [ -n "$run" ] || die "the Renovate run workflow did not start: see https://github.com/$repo/actions/workflows/renovate-run.yml"
  echo "Renovate run workflow: https://github.com/$repo/actions/runs/$run"
  gh run watch "$run" --exit-status --interval 5 >/dev/null 2>&1 ||
    die "the Renovate run workflow failed: gh run view $run --log-failed"
  echo "Requested a Renovate run on https://github.com/$repo/issues/$n"
}

cmd_wait() {
  local minutes=${1:-60} n start deadline left elapsed next=5
  n=$(issue)
  start=$(date +%s)
  deadline=$((start + minutes * 60))
  echo "Waiting up to $minutes minutes for Renovate to process https://github.com/$repo/issues/$n"
  while :; do
    left=$(body "$n" | grep -cE '^ *- \[x\] <!--' || true)
    if [ "$left" -eq 0 ]; then
      echo "Renovate processed the dashboard after $((($(date +%s) - start) / 60)) minutes."
      cmd_prs
      return 0
    fi
    elapsed=$((($(date +%s) - start) / 60))
    if [ "$elapsed" -ge "$next" ]; then
      echo "$elapsed min: $left checkbox(es) still ticked; Renovate last edited the dashboard $(last_renovate_edit "$n")"
      next=$((next + 5))
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
      cat >&2 <<EOT
dashboard.sh: Renovate has not processed the dashboard after $minutes minutes ($left checkbox(es) still ticked).
The request reached GitHub; Mend runs it from its own queue, which took over an hour at times.
Check the jobs on https://developer.mend.io/github/$repo:
  - a job queued or running: wait again (dashboard.sh wait);
  - no job since the request: tick the updates there (never one under "Edited/Blocked") and press
    "Create/Rebase", which starts a job on Mend's side; then wait again;
  - the latest job's log shows "mode":"silent": turn silent mode off on that page, then request again.
EOT
      exit 1
    fi
    sleep 30
  done
}

# When renovate[bot] last edited the issue (UTC).
last_renovate_edit() {
  # $owner, $name and $n are GraphQL variables, not shell ones.
  # shellcheck disable=SC2016
  gh api graphql -F n="$1" -F owner="${repo%/*}" -F name="${repo#*/}" -f query='
    query($owner: String!, $name: String!, $n: Int!) {
      repository(owner: $owner, name: $name) { issue(number: $n) {
        userContentEdits(first: 20) { nodes { editedAt editor { login } } } } } }' \
    --jq '[.data.repository.issue.userContentEdits.nodes[] | select(.editor.login == "renovate")][0].editedAt // "never (in the last 20 edits)"'
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
  request) cmd_request "${2:-}" ;;
  wait) cmd_wait "${2:-}" ;;
  prs) cmd_prs ;;
  wait-ci) cmd_wait_ci "${2:-}" ;;
  *) sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac
