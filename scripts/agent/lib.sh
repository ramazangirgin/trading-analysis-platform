# shellcheck shell=bash disable=SC2034
# Shared by the agent scripts (docs/agentic-development.md). Sourced,
# not run. The agents are Claude Code, run headless on the developer's machine with the developer's
# own Claude Code login; GitHub is reached through the developer's gh login and Git credentials.
# Configured by environment variables:
#
#   AGENT_MODEL          developer agent (implement, fix-ci, fix); default Sonnet
#   AGENT_REVIEW_MODEL   review agent; default Opus
#   AGENT_MAX_ROUNDS     review → fix rounds; default 2
#   AGENT_MAX_CI_FIXES   attempts to fix a red CI over the whole pull request; default 3
#   AGENT_MAX_TOKENS     tokens (input, cached and output) over the whole pull request; default 50000000
#   AGENT_MAX_BUDGET_USD per agent call, passed to claude --max-budget-usd when set
#   AGENT_GITHUB_LOGIN   the account the agents comment as; default the gh login
#   AGENT_CI_CHECK       the required check; default "CI passed"
#   AGENT_CI_TIMEOUT     minutes to wait for CI on a push; default 60
#   AGENT_BASE_BRANCH    the branch plans start from and pull requests go into; default main.
#                        Steps on an existing pull request use its base instead.

set -euo pipefail

# Bash 3.2 (macOS) compatible: no negative array indices, guarded empty-array expansions.

# The scripts switch branches, which would change their own files while Bash still reads them, and
# an agent must not change the prompts it runs with. So every script restarts from a copy of
# scripts/agent taken before anything else happens: the version the run was started with.
if [ -z "${AGENT_SCRIPTS:-}" ]; then
  AGENT_REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
  AGENT_SCRIPTS=$(mktemp -d "${TMPDIR:-/tmp}/agent-scripts.XXXXXX")
  cp -R "$AGENT_REPO_ROOT/scripts/agent/." "$AGENT_SCRIPTS"
  export AGENT_SCRIPTS AGENT_REPO_ROOT
  exec bash "$AGENT_SCRIPTS/$(basename "$0")" "$@"
fi
AGENT_DIR=$AGENT_SCRIPTS
REPO_ROOT=$AGENT_REPO_ROOT
cd "$REPO_ROOT"

# The agents call mise by name; mise's installer puts it into ~/.local/bin, which is not always on
# PATH (a shell without mise activated).
if ! command -v mise >/dev/null && [ -x "$HOME/.local/bin/mise" ]; then
  export PATH="$HOME/.local/bin:$PATH"
fi

AGENT_MODEL=${AGENT_MODEL:-claude-sonnet-5-5}
AGENT_REVIEW_MODEL=${AGENT_REVIEW_MODEL:-claude-opus-5-5}
AGENT_MAX_ROUNDS=${AGENT_MAX_ROUNDS:-2}
AGENT_MAX_CI_FIXES=${AGENT_MAX_CI_FIXES:-3}
AGENT_MAX_TOKENS=${AGENT_MAX_TOKENS:-50000000}
AGENT_CI_CHECK=${AGENT_CI_CHECK:-CI passed}
AGENT_CI_TIMEOUT=${AGENT_CI_TIMEOUT:-60}
AGENT_BASE_BRANCH=${AGENT_BASE_BRANCH:-main}
# Label on an agent's pull request; removing it stops the loop.
AGENT_LABEL=agent

# Per-run scratch files (prompts, agent output), kept for a look after a failure.
AGENT_TMP=${AGENT_TMP:-${TMPDIR:-/tmp}/agent-$$}
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

# Steps on an existing pull request work against its base branch.
use_pr_base() { AGENT_BASE_BRANCH=$(gh pr view "$1" --json baseRefName --jq .baseRefName); }

# A prompt file with {{BASE}} replaced by the base branch, followed by how to work within the
# agents' tool allow-list (prompts/tools.md).
prompt() {
  cat "$AGENT_DIR/prompts/$1" "$AGENT_DIR/prompts/tools.md" | sed "s|{{BASE}}|origin/$AGENT_BASE_BRANCH|g"
  echo
}

# The open pull request whose head is $1, or nothing.
pr_of_branch() {
  gh pr list --head "$1" --state open --json number --jq '.[0].number // empty'
}

# --- Claude Code ---------------------------------------------------------------------------------

# Claude Code with the developer's own login: an API key or another endpoint in the environment
# would take precedence over it, so they are removed for the agents.
use_claude_login() {
  unset ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN ANTHROPIC_BASE_URL ANTHROPIC_MODEL
  export CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1
}

DEFAULT_MODEL_NAME="Claude Code's default model"
model_name() { echo "${1:-$DEFAULT_MODEL_NAME}"; }

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
  Read Edit Write Glob Grep TodoWrite Task "Bash(cd:*)" "Bash(pwd)"
  "Bash(mise run:*)" "Bash(mise exec:*)" "Bash(./gradlew:*)" "Bash(uv run:*)" "Bash(uv sync:*)"
  "Bash(artifact/frontend/with-node.sh:*)" "Bash(e2e/with-node.sh:*)" "Bash(scripts/version.sh:*)"
  "Bash(git status:*)" "Bash(git diff:*)" "Bash(git log:*)" "Bash(git show:*)" "Bash(git add:*)"
  "Bash(git commit:*)" "Bash(git rm:*)" "Bash(git mv:*)" "Bash(git restore:*)"
  "Bash(ls:*)" "Bash(cat:*)" "Bash(head:*)" "Bash(tail:*)" "Bash(wc:*)" "Bash(find:*)"
  "Bash(grep:*)" "Bash(rg:*)" "Bash(sed -n:*)" "Bash(mkdir:*)" "Bash(jq:*)"
)

# Tools the review agent may use: read only.
AGENT_REVIEW_TOOLS=(
  Read Glob Grep TodoWrite Task "Bash(cd:*)" "Bash(pwd)"
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
  [ -z "$model" ] || extra+=(--model "$model")

  use_claude_login
  log "running $(model_name "$model") ($(basename "$prompt"))"
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

# The commit the latest review approved (no findings), or nothing.
approved_head() { pr_step_comments "$1" | py "$AGENT_DIR/agent_json.py" approved-head; }

count_steps() { pr_steps "$1" | awk -v s="$2" '$1 == s' | wc -l | tr -d ' '; }
tokens_used() { pr_steps "$1" | awk '{ t += $3 } END { print t + 0 }'; }

# post_step <pr> <step> <round> <body file> [agent result json...]
# AGENT_STEP_MARKER adds "key=value" pairs to the marker (review: approved=0|1 head=<sha>).
post_step() {
  local pr=$1 step=$2 round=$3 body=$4
  shift 4
  {
    echo "<!-- agent-step step=$step round=$round ${AGENT_STEP_MARKER:+$AGENT_STEP_MARKER }$(usage_marker "$@") -->"
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
  case $branch in main | "$AGENT_BASE_BRANCH") die "refusing to push $branch" ;; esac
  git push --quiet origin "HEAD:refs/heads/$branch"
}
