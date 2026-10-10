#!/usr/bin/env bash
# Step 5 of the agentic development flow (docs/agentic-development.md):
# the developer agent implements a plan, work package by work package, and opens a draft pull request.
#
#   scripts/agent/implement.sh plan/<issue>-<slug>
#   scripts/agent/implement.sh --dry-run plan/<issue>-<slug>   the packages, their status and what a
#                                                              run would do; changes nothing
#
# Checks out the plan branch and runs the developer agent once per work package, in the order of
# their dependencies (plan.py). The agent commits the package with a "Work-package: <id>" trailer
# and records it in the plan (steps ticked, "Status: done" or "not done: <reason>"); the script
# checks that, pushes the branch and goes on with the next package. A plan without work packages is
# one package, "all". Then the final checks (clean tree, commits, version bump, mise run check) and
# the draft pull request labelled "agent". The review → fix loop (next.sh) follows; run.sh runs
# both. The plan file stays in the pull request. Its body closes the issue only when this is the
# issue's last plan (issue_reference in lib.sh).
#
# Milestone lines (agent: >> …, see lib.sh) say how far it is: a package started / done, the final
# checks, the pushes, the draft pull request; the agent's tests, mise run check and commits too.
#
# Interrupted (usage limit, Ctrl+C, a crash), started again, it skips the packages the plan says
# are done, keeps the uncommitted work on the branch and lets the agent continue it. Its files are
# in .git/agent/<issue>-<slug>/: run.log, the agents' results and streams (wp-<id>.json), `current`
# (the package it is on) and the lock run.pid.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

dry_run=false
if [ "${1:-}" = --dry-run ]; then
  dry_run=true
  shift
fi
branch=${1:?usage: $0 [--dry-run] plan/<issue>-<slug>}
[[ $branch == plan/* ]] || die "not a plan branch: $branch"
issue_of_branch "$branch" >/dev/null # fails for a branch without an issue number
plan=$(plan_file_of_branch "$branch")
use_state "$branch"

plan_py() { py "$AGENT_DIR/plan.py" "$@"; }

if $dry_run; then
  # Reads the plan of the branch as it is: the working tree's on the current branch, else the pushed one.
  git fetch --quiet origin "$branch"
  file=$plan
  if [ "$(git branch --show-current)" != "$branch" ]; then
    file=$AGENT_TMP/dry-run-plan.md
    git show "origin/$branch:$plan" >"$file"
  fi
  echo "Plan $plan on $branch"
  echo
  echo "Work packages:"
  plan_py packages "$file" | awk -F'\t' '{ printf "  %-6s %-9s %s\n", $1, $2, $3 }'
  next=$(plan_py next "$file")
  total=$(plan_py packages "$file" | wc -l | tr -d ' ')
  echo
  dirty=$(git status --short)
  current=$(sed -n 's/^package=//p' "$AGENT_STATE/current" 2>/dev/null || true)
  if [ -n "$dirty" ] && [ "$(git branch --show-current)" = "$branch" ]; then
    echo "Uncommitted work on $branch, from an interrupted run (package: ${current:-unknown}):"
    echo "$dirty" | sed 's/^/  /'
  elif [ -n "$dirty" ]; then
    echo "The working tree is not clean, on $(git branch --show-current): a run would stop."
  else
    echo "The working tree is clean."
  fi
  pid=$(active_run)
  if [ -n "$pid" ]; then
    echo "A run is active: pid $pid, log $AGENT_STATE/run.log"
  else
    echo "No run is active (log: $AGENT_STATE/run.log)."
  fi
  pr=$(pr_of_branch "$branch")
  echo
  if [ -n "$pr" ]; then
    echo "The branch has an open pull request (#$pr): continue it with next.sh."
  elif [ -n "$next" ]; then
    position=$(plan_py packages "$file" | awk -F'\t' -v id="$next" '$1 == id { print NR }')
    done_count=$(plan_py packages "$file" | awk -F'\t' '$2 != "open" && $2 != "invalid"' | wc -l | tr -d ' ')
    if [ -n "$dirty" ] && [ "$(git branch --show-current)" = "$branch" ]; then
      echo "A run would continue $next ($position of $total, $done_count done) on the uncommitted work."
    else
      echo "A run would start with $next ($position of $total, $done_count done)."
    fi
  else
    echo "Every package is finished: a run would do the final checks and open the draft pull request."
  fi
  exit 0
fi

[ -z "$(pr_of_branch "$branch")" ] || die "$branch has an open pull request already; continue it with next.sh"
lock_run
checkout_branch --keep-changes "$branch"
[ -f "$plan" ] || die "$branch has no $plan"
# The commit that added the plan: what the run committed comes after it.
plan_commit=$(git log --diff-filter=A --format=%H --reverse -- "$plan" | head -n 1)
[ -n "$plan_commit" ] || die "$plan was never committed on $branch"

schema='{"type":"object","properties":{"summary":{"type":"string"},"not_done":{"type":"string"}},"required":["summary","not_done"]}'
# A field of an agent result's structured output, empty when the result or the field is missing.
result_field() {
  py -c 'import json, sys
try:
    with open(sys.argv[1], encoding="utf-8") as f:
        print((json.load(f).get("structured_output") or {}).get(sys.argv[2], ""))
except OSError:
    pass' "$1" "$2"
}
package_commits() { git rev-list --count --grep "^Work-package: $1\$" "$plan_commit..HEAD"; }
# The Status of package $1 in the plan: open, done, not done or invalid.
package_status() { plan_py packages "$plan" | awk -F'\t' -v id="$1" '$1 == id { print $2 }'; }

# What is wrong with package $1 after its run: its Status in the plan, and the working tree.
package_problems() {
  local out
  out=$(plan_py check "$plan" "$1")
  if [ -n "$out" ]; then printf '%s\n\n' "$out"; fi
  if [ -n "$(git status --porcelain)" ]; then
    printf 'Uncommitted changes are left (commit them, with the Work-package trailer):\n%s\n\n' "$(git status --short)"
  fi
}

session=""
total=$(plan_py packages "$plan" | wc -l | tr -d ' ')
while true; do
  package=$(plan_py next "$plan")
  [ -n "$package" ] || break
  position=$(plan_py packages "$plan" | awk -F'\t' -v id="$package" '$1 == id { print NR }')
  name=$(plan_py packages "$plan" | awk -F'\t' -v id="$package" '$1 == id { print $3 }')
  open_count=$(plan_py remaining "$plan" | wc -l | tr -d ' ')
  label="$package ($position of $total)"
  previous=$(sed -n 's/^package=//p' "$AGENT_STATE/current" 2>/dev/null || true)
  dirty=$(git status --short)
  echo "package=$package" >"$AGENT_STATE/current"
  milestone "$label: started${dirty:+ (on uncommitted work)}"

  {
    prompt implement.md
    echo
    echo "## Your package: $label, $name"
    echo
    echo "Your package ID, for the commit trailer and the plan: \`$package\`."
    if [ "$open_count" -le 1 ]; then
      echo
      echo "This is the last open package. With it, raise the version as the plan says (\`scripts/version.sh"
      echo "bump <major|minor|patch>\`, once) unless \`scripts/version.sh check-bump {{BASE}}\` already passes,"
      echo "and do the docs and screenshots that no earlier package covers."
    else
      echo
      echo "Other packages are open after this one: do not bump the version yet."
    fi
    if [ -n "$dirty" ]; then
      echo
      echo "### Resume: uncommitted work from an interrupted run"
      echo
      echo "The working tree holds uncommitted work from an interrupted run, which was on package"
      echo "\`${previous:-unknown}\`$([ -n "$previous" ] || echo " (not recorded)"). Read \`git status\` and \`git diff\` first, and keep what is right and finish it."
      echo "Commit it with the trailer of the package it belongs to. If it covers several open packages"
      echo "(an interrupted run of the old single-session flow), finish and commit them one after the other"
      echo "in dependency order, each with its status in the plan and its own trailer. Never discard work"
      echo "that you did not check: use \`git restore\` only on files you have read."
      echo
      echo "Uncommitted files:"
      echo
      echo '```'
      echo "$dirty"
      echo '```'
    fi
    echo
    echo "## The plan ($plan, approved; the status lines and ticks are the progress so far)"
    echo
    cat "$plan"
  } | sed "s|{{BASE}}|origin/$AGENT_BASE_BRANCH|g" >"$AGENT_TMP/implement-$package.md"

  result=$AGENT_TMP/wp-$package.json
  export AGENT_STEP_LABEL=$label
  run_agent "$AGENT_MODEL" "$AGENT_TMP/implement-$package.md" "$result" "${AGENT_DEV_TOOLS[@]}" -- --json-schema "$schema"
  session=$(py "$AGENT_DIR/agent_json.py" get "$result" session_id)

  # The package must be recorded in the plan and committed. One more try, resuming the session.
  problems=$(package_problems "$package")
  if [ -n "$problems" ]; then
    milestone "$label: not finished, resuming the agent"
    printf 'Package %s is not finished. Fix the problems and commit:\n\n%s\n' "$package" "$problems" >"$AGENT_TMP/retry-$package.md"
    run_agent "$AGENT_MODEL" "$AGENT_TMP/retry-$package.md" "$AGENT_TMP/wp-$package-retry.json" "${AGENT_DEV_TOOLS[@]}" -- --resume "$session" --json-schema "$schema"
    session=$(py "$AGENT_DIR/agent_json.py" get "$AGENT_TMP/wp-$package-retry.json" session_id)
    problems=$(package_problems "$package")
    [ -z "$problems" ] || die "$label: still not finished (the work stays on $branch; run this again to resume):"$'\n'"$problems"
  fi

  push_branch
  if [ "$(package_status "$package")" = done ]; then
    milestone "$label: done ($(package_commits "$package") commits)"
  else
    milestone "$label: not done:$(plan_py not-done "$plan" | sed -n "s/^- $package: //p")"
  fi
done
rm -f "$AGENT_STATE/current"

# Final checks: what CI would reject fast. Two more tries, each resuming the agent with the failure.
results=()
export AGENT_STEP_LABEL="final checks"
for attempt in 1 2 3; do
  milestone "checking the implementation (attempt $attempt)"
  log "checking packages, commits, version bump, mise run check"
  problems=""
  [ -z "$(git status --porcelain)" ] || problems+="Uncommitted changes are left:"$'\n'"$(git status --short)"$'\n\n'
  [ "$(git rev-list --count "$plan_commit..HEAD")" -gt 0 ] || problems+="Nothing was committed."$'\n\n'
  if [ -n "$(plan_py remaining "$plan")" ]; then
    problems+="Work packages are neither done nor marked not done:"$'\n'"$(plan_py remaining "$plan")"$'\n\n'
  fi
  if ! out=$(scripts/version.sh check-bump "origin/$AGENT_BASE_BRANCH" 2>&1); then
    problems+="Version check failed: $out"$'\n\n'
  fi
  if ! out=$(mise run check 2>&1); then
    problems+="mise run check failed:"$'\n'"$(tail -n 80 <<<"$out")"$'\n\n'
  fi
  if [ -z "$problems" ]; then
    milestone "checks passed"
    break
  fi
  milestone "checks failed (attempt $attempt): $(head -n 1 <<<"$problems")"
  [ "$attempt" -lt 3 ] || die "the implementation still fails its checks:"$'\n'"$problems"
  log "resuming the agent with the failed checks"
  {
    # Without a session of this run (every package was done before it), the agent starts fresh.
    if [ -z "$session" ]; then
      prompt implement.md
      echo
      echo "## Your task: fix the problems below. All packages of the plan are done; there is no package to implement."
      echo
      echo "## The plan ($plan)"
      echo
      cat "$plan"
      echo
    fi
    printf 'The checks failed. Fix the problems, run the checks again and commit:\n\n%s\n' "$problems"
  } | sed "s|{{BASE}}|origin/$AGENT_BASE_BRANCH|g" >"$AGENT_TMP/retry.md"
  retry=$AGENT_TMP/implement-retry-$attempt.json
  run_agent "$AGENT_MODEL" "$AGENT_TMP/retry.md" "$retry" "${AGENT_DEV_TOOLS[@]}" -- ${session:+--resume "$session"} --json-schema "$schema"
  session=$(py "$AGENT_DIR/agent_json.py" get "$retry" session_id)
  results+=("$retry")
done

push_branch

# One sub-section per package, from that package's result.
summary=""
missing=""
while IFS=$'\t' read -r id _ pkg_name; do
  text=$(result_field "$AGENT_TMP/wp-$id.json" summary)
  if [ -z "$text" ]; then
    text="Implemented in an earlier run, see its commits (\`git log --grep '^Work-package: $id\$'\`)."
    [ -e "$AGENT_TMP/wp-$id.jsonl" ] || missing+="${missing:+, }$id"
  fi
  if [ "$total" -eq 1 ] && [ "$id" = all ]; then
    summary+="$text"$'\n\n'
  else
    summary+="### $id: $pkg_name"$'\n\n'"$text"$'\n\n'
  fi
done < <(plan_py packages "$plan")
not_done=$(plan_py not-done "$plan")
while IFS=$'\t' read -r id _ pkg_name; do
  text=$(result_field "$AGENT_TMP/wp-$id.json" not_done)
  case $(tr '[:upper:]' '[:lower:]' <<<"$text") in "" | nothing | nothing. | none | none.) continue ;; esac
  not_done+="${not_done:+$'\n\n'}**$id**: $text"
done < <(plan_py packages "$plan")
for retry in "${results[@]+"${results[@]}"}"; do
  text=$(result_field "$retry" not_done)
  case $(tr '[:upper:]' '[:lower:]' <<<"$text") in "" | nothing | nothing. | none | none.) continue ;; esac
  not_done+="${not_done:+$'\n\n'}**Final checks**: $text"
done

title=$(sed -n 's/^# Plan: //p' "$plan" | head -n 1)
blob="https://github.com/$(repo_slug)/blob"
cat >"$AGENT_TMP/pr.md" <<EOF
$(issue_reference "$branch")

Implemented by the developer agent from the plan [\`$plan\`]($blob/$branch/$plan) (model: $(model_name "$AGENT_MODEL")),
one work package at a time.
Draft until the agents' review → fix rounds are done; see
[the agentic development flow]($blob/$AGENT_BASE_BRANCH/docs/agentic-development.md).

## Summary

$summary## Not done or done differently

${not_done:-Nothing.}

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
log "opening the draft pull request"
gh label create "$AGENT_LABEL" --color 5319E7 --description "Driven by the agent loop; remove to stop it" 2>/dev/null || true
url=$(gh pr create --draft --base "$AGENT_BASE_BRANCH" --head "$branch" --title "${title:-$branch}" \
  --body-file "$AGENT_TMP/pr.md" --label "$AGENT_LABEL")
pr=${url##*/}
milestone "draft pull request #$pr opened: $url"

# Every run of the implementation counts: the packages' results and the streams of interrupted runs.
runs=()
for file in "$AGENT_TMP"/wp-*.json "$AGENT_TMP"/wp-*.jsonl "$AGENT_TMP"/implement-retry-*.json "$AGENT_TMP"/implement-retry-*.jsonl; do
  if [ -e "$file" ]; then runs+=("$file"); fi
done
{
  echo "Implementation pushed ($(git rev-list --count "$plan_commit..HEAD") commits, $total work package(s)). The review starts after **$AGENT_CI_CHECK**."
  if [ -n "$missing" ]; then
    echo
    echo "Usage of earlier runs not available: $missing."
  fi
} >"$AGENT_TMP/step.md"
post_step "$pr" implement 0 "$AGENT_TMP/step.md" ${runs[@]+"${runs[@]}"}
echo "$url"
