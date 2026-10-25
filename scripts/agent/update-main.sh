#!/usr/bin/env bash
# A step of the agentic development flow (docs/agentic-development.md): merges the base branch (main)
# into a pull request's branch.
#
#   scripts/agent/update-main.sh <pr | plan/<issue>-<slug>> [--bump major|minor|patch]
#
# Works on any open pull request of a plan branch, and on a plan branch without one. Up to date: does
# nothing. Conflicts that are only the version lines are resolved by merge_versions.py, any other
# conflict (or a failed `mise run check` after a clean merge) by a headless developer agent in a
# fresh context. The version is raised again when the base caught up (--bump, default the plan's
# "Version bump", else minor). The merge, the resolution and the bump go out in one push, then the
# step's comment (marker update-main, with from=<head before> head=<head after>) on the pull request.
# An unresolved conflict stops the script with the merge left local: finish it by hand
# (/merge-main-into-branch) or drop it with `git reset --keep @{u}`. Milestone lines (agent: >> …,
# see lib.sh): up to date, or the number of conflicts resolved and the version. Its files are in
# .git/agent/<issue>-<slug>/ (update-main-<n>.json is the agent's result).
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

usage="usage: $0 <pr | plan branch> [--bump major|minor|patch]"
target=""
bump=""
while [ $# -gt 0 ]; do
  case $1 in
    --bump)
      bump=${2:?$usage}
      shift 2
      ;;
    -*) die "$usage" ;;
    *)
      target=$1
      shift
      ;;
  esac
done
[ -n "$target" ] || die "$usage"
case $bump in "" | major | minor | patch) ;; *) die "$usage" ;; esac

if [[ $target =~ ^[0-9]+$ ]]; then
  pr=$target
  branch=$(gh pr view "$pr" --json headRefName --jq .headRefName)
else
  branch=$target
  pr=$(pr_of_branch "$branch")
fi
use_state "$branch"
lock_run
step_begin "update with main"
[ -z "$pr" ] || use_pr_base "$pr"
base=$AGENT_BASE_BRANCH
plan=$(plan_file_of_branch "$branch")

checkout_branch "$branch"
if [ -n "$(git rev-list --merges "origin/$branch..HEAD")" ]; then
  die "update with main: $branch has a merge that was never pushed (an earlier unresolved run)
agent: finish it by hand (/merge-main-into-branch) or drop it with: git reset --keep @{u}"
fi
if git merge-base --is-ancestor "origin/$base" HEAD; then
  milestone "$branch: up to date with $base"
  step_end "up to date"
  exit 0
fi

if [ -z "$bump" ]; then
  bump=$(sed -n 's/^- \*\*Version bump\*\*: *\([a-z]*\).*/\1/p' "$plan" 2>/dev/null | head -n 1) || true
  case $bump in major | minor | patch) ;; *) bump=minor ;; esac
fi

milestone "update with $base started"
head_before=$(git rev-parse HEAD)
git merge --no-ff --no-commit "origin/$base" >/dev/null || true
[ -e "$(git rev-parse --git-path MERGE_HEAD)" ] || die "update with main: git merge origin/$base failed"
conflicted=()
while IFS= read -r file; do
  [ -z "$file" ] || conflicted+=("$file")
done < <(git diff --name-only --diff-filter=U)

merged_commits=$(git rev-list --count "$head_before..origin/$base")
resolution_lines=()
script_resolved=0
run_agent_task=""
agent_files=()
check_excerpt=""

# --no-verify: a hook that rejects the merge would leave MERGE_HEAD set and skip the agent; the
# clean merge is checked by `mise run check` below, and the agents' own --no-verify stays denied.
commit_quiet() { AGENT_QUIET=1 git commit --quiet --no-verify "$@"; }

if [ ${#conflicted[@]} -eq 0 ]; then
  commit_quiet -m "Merge $base into $branch"
  merge_commit=$(git rev-parse HEAD)
  if check_out=$(AGENT_QUIET=1 mise run check 2>&1); then
    resolution_lines+=("No conflicts; \`mise run check\` passes.")
  else
    run_agent_task=adapt
    check_excerpt=$(printf '%s\n' "$check_out" | tail -n 80)
    commit_subject="Adapt to $base after the merge"
  fi
else
  version_files=()
  for file in "${conflicted[@]}"; do
    if py "$AGENT_DIR/merge_versions.py" is-version-file "$file"; then version_files+=("$file"); fi
  done
  left=""
  if [ ${#version_files[@]} -gt 0 ]; then
    left=$(py "$AGENT_DIR/merge_versions.py" resolve "${version_files[@]}") || true
  fi
  resolved_versions=()
  for file in ${version_files[@]+"${version_files[@]}"}; do
    case $'\n'"$left"$'\n' in
      *$'\n'"$file"$'\n'*) ;;
      *) resolved_versions+=("$file") ;;
    esac
  done
  for file in "${conflicted[@]}"; do
    case " ${resolved_versions[*]-} " in
      *" $file "*) ;;
      *) agent_files+=("$file") ;;
    esac
  done
  script_resolved=${#resolved_versions[@]}
  for file in ${resolved_versions[@]+"${resolved_versions[@]}"}; do
    resolution_lines+=("\`$file\`: version files taken from \`$base\`.")
  done
  git add -- "${conflicted[@]}"
  if [ ${#agent_files[@]} -eq 0 ]; then
    versions=$(printf '%s, ' "${resolved_versions[@]}")
    commit_quiet -m "Merge $base into $branch" -m "Version files taken from $base: ${versions%, }"
  else
    run_agent_task=resolve
    commit_subject="Resolve merge conflicts with $base"
    # The markers are committed on purpose (as /merge-main-into-branch does), so the resolution is a
    # commit of its own.
    commit_quiet -m "Merge $base into $branch" \
      -m "Conflicts, resolved in the next commit:
$(printf '%s\n' "${agent_files[@]}")"
  fi
  merge_commit=$(git rev-parse HEAD)
fi

results=()
if [ -n "$run_agent_task" ]; then
  n=1
  while [ -e "$AGENT_TMP/update-main-$n.json" ]; do n=$((n + 1)); done
  {
    prompt update-main.md
    echo
    if [ "$run_agent_task" = resolve ]; then
      echo "## Task: resolve the conflicts with $base"
      echo
      echo "Conflicted files (the markers are committed in the merge commit):"
      echo
      printf -- '- `%s`\n' "${agent_files[@]}"
      if [ ${#resolved_versions[@]} -gt 0 ]; then
        echo
        echo "Version files already resolved by the script: ${resolved_versions[*]}"
      fi
    else
      echo "## Task: adapt to $base"
      echo
      echo "The merge has no conflict, but \`mise run check\` fails on it (last 80 lines):"
      echo
      echo '```'
      printf '%s\n' "$check_excerpt"
      echo '```'
    fi
    if [ -f "$plan" ]; then
      echo
      echo "## The plan ($plan)"
      echo
      cat "$plan"
    fi
  } >"$AGENT_TMP/update-main-$n.md"
  schema='{"type":"object","required":["resolved","summary"],"properties":{"resolved":{"type":"boolean"},"summary":{"type":"string"},"reason":{"type":"string"}}}'
  result=$AGENT_TMP/update-main-$n.json
  milestone "update with $base: agent started (${#agent_files[@]} conflicted files)"
  export AGENT_STEP_LABEL="update with main"
  run_agent "$AGENT_MODEL" "$AGENT_TMP/update-main-$n.md" "$result" "${AGENT_DEV_TOOLS[@]}" -- --json-schema "$schema"
  results+=("$result")
  agent_out=$(py "$AGENT_DIR/agent_json.py" get "$result" structured_output)
  agent_resolved=$(py -c 'import json, sys; print(json.loads(sys.argv[1]).get("resolved"))' "$agent_out")
  agent_summary=$(py -c 'import json, sys; print(json.loads(sys.argv[1]).get("summary") or "")' "$agent_out")
  agent_reason=$(py -c 'import json, sys; print(json.loads(sys.argv[1]).get("reason") or "")' "$agent_out")

  unresolved=""
  [ "$agent_resolved" = True ] || unresolved="the agent did not resolve it"
  [ -z "$(git status --porcelain)" ] || unresolved="${unresolved:-the agent left uncommitted changes}"
  [ "$(git rev-parse HEAD)" != "$merge_commit" ] || unresolved="${unresolved:-the agent committed nothing}"
  if [ ${#agent_files[@]} -gt 0 ] && git grep -qE '^(<<<<<<<|=======|>>>>>>>)( |$)' -- "${agent_files[@]}"; then
    unresolved="${unresolved:-conflict markers remain}"
  fi
  if [ -n "$unresolved" ]; then
    files=${agent_files[*]-the check}
    die "update with main: unresolved conflict in ${files// /, }: ${agent_reason:-$unresolved}
agent: the merge stays local ($unresolved); finish it by hand (/merge-main-into-branch) or drop it with: git reset --keep @{u}"
  fi
  while IFS= read -r line; do
    [ -z "$line" ] || resolution_lines+=("$line")
  done <<<"$agent_summary"
fi

version_note=""
if ! scripts/version.sh check-bump "origin/$base" >/dev/null 2>&1; then
  new_version=$(scripts/version.sh bump "$bump")
  AGENT_QUIET=1 git commit --quiet -am "Bump version to $new_version"
  version_note="bumped ($bump) to $new_version"
else
  new_version=$(scripts/version.sh get)
  version_note="$new_version, no bump needed"
fi

head_after=$(git rev-parse HEAD)
push_branch
conflict_count=${#conflicted[@]}
if [ -n "$pr" ]; then
  {
    echo "### Update with $base"
    echo
    echo "Merged \`$base\` ($merged_commits commit(s)) into \`$branch\`: $conflict_count conflicted file(s), $script_resolved resolved by script."
    if [ ${#resolution_lines[@]} -gt 0 ]; then
      echo
      printf -- '- %s\n' "${resolution_lines[@]}"
    fi
    echo
    echo "Version: $version_note."
  } >"$AGENT_TMP/step.md"
  AGENT_STEP_MARKER="from=$head_before head=$head_after" \
    post_step "$pr" update-main "$(count_steps "$pr" review)" "$AGENT_TMP/step.md" ${results[@]+"${results[@]}"}
fi
milestone "updated with $base: $conflict_count conflicts resolved ($script_resolved by script), version $new_version"
