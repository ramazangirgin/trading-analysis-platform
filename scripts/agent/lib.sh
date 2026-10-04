# shellcheck shell=bash disable=SC2034
# Shared by the agent scripts (doc/coding-convention/repository-agentic-development.md). Sourced,
# not run. Everything is configured by environment variables, so a script runs the same on a
# developer's machine and in GitHub Actions:
#
#   DEEPSEEK_API_KEY / AGENT_API_KEY   the model endpoint's key (AGENT_API_KEY wins)
#   AGENT_BASE_URL       Anthropic-compatible endpoint; default DeepSeek's. Set it to "" to use
#                        Claude Code's own login and Claude models instead.
#   AGENT_MODEL          developer agent (implement, fix); default deepseek-v4-pro
#   AGENT_REVIEW_MODEL   review agent; default AGENT_MODEL
#   AGENT_SMALL_MODEL    Claude Code's background model (summaries, WebFetch); default deepseek-flash
#   AGENT_MAX_ROUNDS     review → fix rounds; default 2
#   AGENT_MAX_CI_FIXES   attempts to fix a red CI over the whole pull request; default 3
#   AGENT_MAX_TOKENS     tokens (input, cached and output) over the whole pull request; default 50000000
#   AGENT_MAX_BUDGET_USD per agent call, passed to claude --max-budget-usd when set (Claude prices)
#   AGENT_GITHUB_LOGIN   the account the agents comment as; default the gh login
#   AGENT_CI_CHECK       the required check; default "CI passed"
#   AGENT_BASE_BRANCH    default main

set -euo pipefail

# Bash 3.2 (macOS) compatible: no negative array indices, guarded empty-array expansions.

# The scripts switch branches, which would change their own files while Bash still reads them, and
# an agent must not change the prompts it runs with. So every script restarts from a copy of
# scripts/agent taken before anything else happens: the version the run was started with.
if [ -z "${AGENT_SCRIPTS:-}" ]; then
  AGENT_REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
  AGENT_SCRIPTS=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/agent-scripts.XXXXXX")
  cp -R "$AGENT_REPO_ROOT/scripts/agent/." "$AGENT_SCRIPTS"
  export AGENT_SCRIPTS AGENT_REPO_ROOT
  exec bash "$AGENT_SCRIPTS/$(basename "$0")" "$@"
fi
AGENT_DIR=$AGENT_SCRIPTS
REPO_ROOT=$AGENT_REPO_ROOT
cd "$REPO_ROOT"

AGENT_BASE_URL=${AGENT_BASE_URL-https://api.deepseek.com/anthropic}
AGENT_MODEL=${AGENT_MODEL:-deepseek-v4-pro}
AGENT_REVIEW_MODEL=${AGENT_REVIEW_MODEL:-$AGENT_MODEL}
AGENT_SMALL_MODEL=${AGENT_SMALL_MODEL:-deepseek-flash}
AGENT_MAX_ROUNDS=${AGENT_MAX_ROUNDS:-2}
AGENT_MAX_CI_FIXES=${AGENT_MAX_CI_FIXES:-3}
AGENT_MAX_TOKENS=${AGENT_MAX_TOKENS:-50000000}
AGENT_CI_CHECK=${AGENT_CI_CHECK:-CI passed}
AGENT_BASE_BRANCH=${AGENT_BASE_BRANCH:-main}
# Label on an agent's pull request; removing it stops the loop.
AGENT_LABEL=agent

# Per-run scratch files (prompts, agent output); kept for the Actions log on failure.
AGENT_TMP=${AGENT_TMP:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/agent-$$}
mkdir -p "$AGENT_TMP"

log() { echo "agent: $*" >&2; }
die() {
  echo "agent: $*" >&2
  exit 1
}

# Python for the JSON / diff helpers (standard library only): uv's, as elsewhere in the repository
# (mise run api-types), or the system's when the script runs outside mise.
py() {
  if command -v uv >/dev/null; then
    uv run --quiet --no-project python "$@"
  else
    python3 "$@"
  fi
}

# All pages of a GitHub list endpoint, as one JSON array.
gh_list() {
  gh api --paginate --slurp "$@" | py -c 'import json, sys; print(json.dumps([x for page in json.load(sys.stdin) for x in page]))'
}

repo_slug() { gh repo view --json nameWithOwner --jq .nameWithOwner; }

agent_login() {
  if [ -z "${AGENT_GITHUB_LOGIN:-}" ]; then
    AGENT_GITHUB_LOGIN=$(gh api user --jq .login)
  fi
  echo "$AGENT_GITHUB_LOGIN"
}

# .plans/<issue>-<slug>.md ↔ plan/<issue>-<slug>
plan_name_of_branch() { echo "${1#plan/}"; }
plan_file_of_branch() { echo ".plans/$(plan_name_of_branch "$1").md"; }
issue_of_branch() {
  local name
  name=$(plan_name_of_branch "$1")
  [[ $name =~ ^([0-9]+)- ]] || die "not a plan branch (plan/<issue>-<slug>): $1"
  echo "${BASH_REMATCH[1]}"
}

# The open pull request whose head is $1, or nothing.
pr_of_branch() {
  gh pr list --head "$1" --state open --json number --jq '.[0].number // empty'
}

# --- Model endpoint ------------------------------------------------------------------------------

# Exports the variables Claude Code reads, for the given model.
use_model() {
  local model=$1
  if [ -n "$AGENT_BASE_URL" ]; then
    local key=${AGENT_API_KEY:-${DEEPSEEK_API_KEY:-}}
    [ -n "$key" ] || die "no API key: set DEEPSEEK_API_KEY (or AGENT_API_KEY) for $AGENT_BASE_URL"
    export ANTHROPIC_BASE_URL=$AGENT_BASE_URL
    export ANTHROPIC_API_KEY=$key
    unset ANTHROPIC_AUTH_TOKEN
    # The endpoint maps unknown Claude names to its own models; name them explicitly instead.
    export ANTHROPIC_DEFAULT_OPUS_MODEL=$model
    export ANTHROPIC_DEFAULT_SONNET_MODEL=$model
    export ANTHROPIC_DEFAULT_HAIKU_MODEL=$AGENT_SMALL_MODEL
    export ANTHROPIC_SMALL_FAST_MODEL=$AGENT_SMALL_MODEL
    export CLAUDE_CODE_SUBAGENT_MODEL=$model
  fi
  export ANTHROPIC_MODEL=$model
  export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1
  export DISABLE_AUTOUPDATER=1
}

# Tools no agent may use, whatever else it is allowed: agents never merge, never push (the scripts
# push, to the agent's own branch only) and never skip the Git hooks.
AGENT_DENIED_TOOLS=(
  "Bash(git push:*)" "Bash(gh pr merge:*)" "Bash(gh api:*)" "Bash(gh repo:*)"
  "Bash(git commit --no-verify:*)" "Bash(git commit -n:*)" "Bash(LEFTHOOK=0:*)"
  "Bash(git checkout main:*)" "Bash(git switch main:*)" "Bash(git reset --hard:*)"
  "Bash(git rebase:*)" "WebSearch"
)

# Tools the developer agents (implement, fix) may use.
AGENT_DEV_TOOLS=(
  Read Edit Write Glob Grep TodoWrite Task
  "Bash(mise run:*)" "Bash(mise exec:*)" "Bash(./gradlew:*)" "Bash(uv run:*)" "Bash(uv sync:*)"
  "Bash(artifact/frontend/with-node.sh:*)" "Bash(e2e/with-node.sh:*)" "Bash(scripts/version.sh:*)"
  "Bash(git status:*)" "Bash(git diff:*)" "Bash(git log:*)" "Bash(git show:*)" "Bash(git add:*)"
  "Bash(git commit:*)" "Bash(git rm:*)" "Bash(git mv:*)" "Bash(git restore:*)"
  "Bash(ls:*)" "Bash(cat:*)" "Bash(head:*)" "Bash(tail:*)" "Bash(wc:*)" "Bash(find:*)"
  "Bash(grep:*)" "Bash(rg:*)" "Bash(sed -n:*)" "Bash(mkdir:*)" "Bash(jq:*)"
)

# Tools the review agent may use: read only.
AGENT_REVIEW_TOOLS=(
  Read Glob Grep TodoWrite Task
  "Bash(git diff:*)" "Bash(git log:*)" "Bash(git show:*)" "Bash(git status:*)"
  "Bash(ls:*)" "Bash(cat:*)" "Bash(head:*)" "Bash(wc:*)" "Bash(find:*)" "Bash(grep:*)" "Bash(rg:*)"
)

# run_agent <model> <prompt file> <output json> [tool...] [-- extra claude args...]
# Runs Claude Code headless and writes its JSON result (result, structured_output, usage,
# session_id) to <output json>. Fails if the agent reports an error.
run_agent() {
  local model=$1 prompt=$2 out=$3
  shift 3
  local -a tools=() extra=()
  while [ $# -gt 0 ] && [ "$1" != -- ]; do
    tools+=("$1")
    shift
  done
  [ "${1:-}" = -- ] && shift
  extra=("$@")
  [ -n "${AGENT_MAX_BUDGET_USD:-}" ] && extra+=(--max-budget-usd "$AGENT_MAX_BUDGET_USD")

  use_model "$model"
  log "running $model ($(basename "$prompt"))"
  # dontAsk: a tool outside the allowed list is refused instead of waiting for an answer.
  # The agent gets no GitHub token: it talks to GitHub only through the scripts.
  env -u GH_TOKEN -u GITHUB_TOKEN claude -p --output-format json --permission-mode dontAsk \
    --allowedTools "${tools[@]}" --disallowedTools "${AGENT_DENIED_TOOLS[@]}" \
    ${extra[@]+"${extra[@]}"} <"$prompt" >"$out" || true
  [ -s "$out" ] || die "the agent wrote no result ($out)"
  if [ "$(py "$AGENT_DIR/agent_json.py" get "$out" is_error)" = True ]; then
    die "the agent failed: $(py "$AGENT_DIR/agent_json.py" get "$out" result)"
  fi
  log "done: $(py "$AGENT_DIR/agent_json.py" usage-line "$out")"
}

# The usage marker of one or more agent results, for the step's PR comment.
usage_marker() { py "$AGENT_DIR/agent_json.py" usage-marker "$@"; }

# --- Pull request state --------------------------------------------------------------------------
#
# Every step leaves one comment on the pull request, written by the agent account, that starts with
# a marker: <!-- agent-step step=<implement|ci-fix|review|fix|finalise> round=<n> ... -->. The loop's
# state is read back from these markers only (comments of other accounts are ignored).

# The bodies of the agent account's comments on pull request $1.
pr_step_comments() {
  gh api --paginate "repos/{owner}/{repo}/issues/$1/comments" \
    --jq ".[] | select(.user.login == \"$(agent_login)\") | .body"
}

# Step markers of pull request $1, one per line: "<step> <round> <tokens>".
pr_steps() { pr_step_comments "$1" | py "$AGENT_DIR/agent_json.py" steps; }

count_steps() { pr_steps "$1" | awk -v s="$2" '$1 == s' | wc -l | tr -d ' '; }
tokens_used() { pr_steps "$1" | awk '{ t += $3 } END { print t + 0 }'; }

# post_step <pr> <step> <round> <body file> [agent result json...]
post_step() {
  local pr=$1 step=$2 round=$3 body=$4
  shift 4
  {
    echo "<!-- agent-step step=$step round=$round $(usage_marker "$@") -->"
    cat "$body"
  } >"$AGENT_TMP/comment.md"
  gh pr comment "$pr" --body-file "$AGENT_TMP/comment.md" >/dev/null
}

# Conclusion of the required check on commit $1: success, failure or pending. CI runs twice on an
# agent's push (push and pull_request events); both must pass.
ci_status() {
  gh api -X GET "repos/{owner}/{repo}/commits/$1/check-runs" -f check_name="$AGENT_CI_CHECK" -f per_page=100 \
    --jq '[.check_runs[] | {status, conclusion}]' |
    py -c '
import json, sys
# A cancelled run was replaced by a newer one (CI cancels superseded runs).
runs = [r for r in json.load(sys.stdin) if r["conclusion"] != "cancelled"]
if not runs or any(r["status"] != "completed" for r in runs):
    print("pending")
elif all(r["conclusion"] == "success" for r in runs):
    print("success")
else:
    print("failure")
'
}

# --- Git -----------------------------------------------------------------------------------------

# Checks out branch $1 at its remote head, with a clean working tree.
checkout_branch() {
  [ -z "$(git status --porcelain)" ] || die "the working tree is not clean"
  git fetch --quiet origin "$AGENT_BASE_BRANCH" "$1"
  git switch --quiet "$1" 2>/dev/null || git switch --quiet -c "$1" --track "origin/$1"
  git merge --quiet --ff-only "origin/$1"
}

push_branch() {
  local branch
  branch=$(git branch --show-current)
  [ "$branch" != "$AGENT_BASE_BRANCH" ] || die "refusing to push $AGENT_BASE_BRANCH"
  git push --quiet origin "HEAD:refs/heads/$branch"
}
