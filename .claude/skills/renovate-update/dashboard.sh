#!/usr/bin/env bash
# Helper of the renovate-update skill (SKILL.md next to it): drives the hosted Renovate app through
# its dependency dashboard issue and waits for the results, with the user's own gh login.
#
#   dashboard.sh show                 the dashboard's checkboxes, by section
#   dashboard.sh request [--dry-run]  tick every checkbox that brings mature updates in, then start
#                                     the Renovate run workflow (renovate-run.yml): it ticks "run again"
#   dashboard.sh wait [minutes]       wait until Renovate has processed every ticked checkbox (default 25),
#                                     with a status line every 30 seconds
#   dashboard.sh prs                  Renovate's open pull requests
#   dashboard.sh wait-ci <pr>         wait for the CI run on the pull request's head, with a status line
#                                     every 30 seconds; exit 1 when it failed
#
# Releases younger than minimumReleaseAge (.github/renovate.json5) are listed under "Pending Status
# Checks"; their checkbox would create them anyway, so `request` never touches it. Neither does it tick
# a closed or ignored update ("recreate"), nor "rebase" a branch someone else has pushed to: a
# rebase would recreate it from scratch and drop those commits.
#
# Mend may take a request up within a minute, leave its job pending, or create no job for it at all
# (all seen on 2026-10-07); only the job list on developer.mend.io shows which. Measured that day:
# request 10:30:20 UTC, job pending 11m42s, job 1m54s, dashboard edited 10:43:53, 13m36s in all.
# `wait`'s default leaves room above that: 25 minutes.
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

# Minutes and seconds since $1 (epoch seconds), as m:ss.
since() {
  local d=$(($(date +%s) - $1))
  printf '%d:%02d' $((d / 60)) $((d % 60))
}

cmd_wait() {
  local minutes=${1:-25} n start deadline left edits req ren seen=''
  n=$(issue)
  start=$(date +%s)
  deadline=$((start + minutes * 60))
  echo "Waiting up to $minutes:00 for Renovate to process https://github.com/$repo/issues/$n."
  echo "Mend's job usually stays pending about 12 minutes and runs about 2; its job list: https://developer.mend.io/github/$repo"
  while :; do
    left=$(body "$n" | grep -cE '^ *- \[x\] <!--' || true)
    if [ "$left" -eq 0 ]; then
      echo "[$(since "$start")] Renovate processed the dashboard. Its pull requests:"
      cmd_prs
      return 0
    fi
    edits=$(dashboard_edits "$n" || true)
    req=$(awk '$2 != "renovate" { print $1; exit }' <<<"$edits")
    ren=$(awk '$2 == "renovate" { print $1; exit }' <<<"$edits")
    if [ -n "$seen" ] && [ "$ren" != "$seen" ]; then
      echo "[$(since "$start")] Renovate edited the dashboard at ${ren:-?}: its job is running; $left checkbox(es) still ticked."
    else
      echo "[$(since "$start") of $minutes:00] Waiting for Mend's job: $left checkbox(es) ticked, requested at ${req:-?}; Renovate's last edit ${ren:-never}."
    fi
    seen=${ren:-none}
    if [ "$(date +%s)" -ge "$deadline" ]; then
      cat <<EOT
Renovate has not processed the dashboard after $minutes minutes ($left checkbox(es) still ticked).
The request is on the dashboard; whether Mend made a job of it shows only in its job list:
https://developer.mend.io/github/$repo
  - no new job: Mend dropped the request. Tick the updates there (never one under "Edited/Blocked")
    and press "Create/Rebase", then wait again (dashboard.sh wait);
  - a job pending or running (its reason "requested"): wait again. If it stays pending, look for an
    older job still in progress ahead of it, and open its log;
  - the latest job's log shows "mode":"silent": turn silent mode off on that page, then request again.
EOT
      exit 1
    fi
    sleep 30
  done
}

# The dashboard's last 20 edits, newest first: "<time> <login>" per line (renovate[bot] is "renovate").
dashboard_edits() {
  # $owner, $name and $n are GraphQL variables, not shell ones.
  # shellcheck disable=SC2016
  gh api graphql -F n="$1" -F owner="${repo%/*}" -F name="${repo#*/}" -f query='
    query($owner: String!, $name: String!, $n: Int!) {
      repository(owner: $owner, name: $name) { issue(number: $n) {
        userContentEdits(first: 20) { nodes { editedAt editor { login } } } } } }' \
    --jq '.data.repository.issue.userContentEdits.nodes[] | "\(.editedAt) \(.editor.login // "?")"'
}

cmd_prs() {
  gh pr list --author app/renovate --state open \
    --json number,title,headRefName,mergeStateStatus,url \
    --jq '.[] | "#\(.number)\t\(.headRefName)\t\(.mergeStateStatus)\t\(.title)\t\(.url)"'
}

cmd_wait_ci() {
  local pr=${1:?usage: dashboard.sh wait-ci <pr>} sha run start deadline status jobs prev='' cur conclusion
  sha=$(gh pr view "$pr" --json headRefOid --jq .headRefOid)
  start=$(date +%s)
  deadline=$((start + 600))
  echo "Waiting for the CI run on #$pr (${sha:0:12})."
  while :; do
    run=$(gh run list --workflow ci.yml --commit "$sha" --event pull_request --limit 1 \
      --json databaseId --jq '.[0].databaseId // empty' || true)
    [ -z "$run" ] || break
    [ "$(date +%s)" -lt "$deadline" ] || die "no CI run for $sha after 10 minutes"
    echo "[$(since "$start")] No CI run yet for ${sha:0:12}."
    sleep 30
  done
  echo "[$(since "$start")] CI run: https://github.com/$repo/actions/runs/$run"
  while :; do
    if jobs=$(gh run view "$run" --json status,conclusion,jobs 2>/dev/null); then
      # One line per job whose state changed since the last poll.
      cur=$(jq -r '.jobs[] | "\(.name): \(if .status == "completed" then .conclusion else .status end)"' <<<"$jobs")
      comm -13 <(sort <<<"$prev") <(sort <<<"$cur") | sed "s/^/[$(since "$start")] /"
      prev=$cur
      jq -r --arg t "$(since "$start")" \
        '"[\($t)] \([.jobs[] | select(.status == "completed")] | length) of \(.jobs | length) jobs done; running: \([.jobs[] | select(.status != "completed") | .name] | join(", ") | if . == "" then "none" else . end)"' <<<"$jobs"
      status=$(jq -r .status <<<"$jobs")
      [ "$status" != completed ] || break
    fi
    sleep 30
  done
  conclusion=$(jq -r .conclusion <<<"$jobs")
  echo "[$(since "$start")] CI $conclusion: https://github.com/$repo/actions/runs/$run"
  if [ "$conclusion" != success ]; then
    jq -r '.jobs[] | select(.conclusion == "failure") | "failed: \(.name)"' <<<"$jobs"
    return 1
  fi
}

case ${1:-} in
  show) cmd_show ;;
  request) cmd_request "${2:-}" ;;
  wait) cmd_wait "${2:-}" ;;
  prs) cmd_prs ;;
  wait-ci) cmd_wait_ci "${2:-}" ;;
  *) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2 ;;
esac
