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
#
#   AGENT_STEP_LABEL     set by a step before run_agent: the label of the milestone lines for the
#                        agent's phases (tests, mise run check, commit); none without it
#
# Everything a run leaves behind (prompts, agent results and streams, run.log, the lock run.pid, the
# package the implementation is on) goes to .git/agent/<issue>-<slug>/ of the plan branch
# (use_state), where a later run finds it.

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
AGENT_CMDLINE="$(basename "$0") $*"

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

# Scratch files (prompts, agent output), kept for a look after a failure. A script that knows its
# plan branch moves them to the branch's state directory (use_state).
AGENT_TMP=${AGENT_TMP:-${TMPDIR:-/tmp}/agent-$$}
mkdir -p "$AGENT_TMP"

log() { echo "agent: $*" >&2; }
# A milestone: a line of its own in run.log (agent: >> <text>) that says how far a run is. Every
# line is written as it happens, nothing between the script's stderr and the terminal buffers it.
milestone() { echo "agent: >> $*" >&2; }
# The first line of the message is a milestone (failed: …), the rest plain lines.
die() {
  local message=$* first rest
  first=${message%%$'\n'*}
  rest=${message#"$first"}
  milestone "failed: $first"
  if [ -n "$rest" ]; then
    printf '%s\n' "${rest#$'\n'}" | sed 's/^/agent: /' >&2
  fi
  exit 1
}

# --- State per plan branch -----------------------------------------------------------------------

# use_state [--no-log] <plan branch>: AGENT_STATE is .git/agent/<issue>-<slug>/ (the main
# repository's .git in a linked worktree, so every worktree finds the same state), never committed
# and never touched by git clean. AGENT_TMP points at it: prompts, results (*.json) and streams
# (*.jsonl) go there. The outermost script copies its stderr (the "agent:" lines and the agents'
# progress) into run.log; nested scripts see AGENT_LOGGING and do not copy it again. The first lines
# of a step name the log and the streams. The lines that say how far a run is start with
# "agent: >> " (milestone): status.sh lists them. With --no-log (a read-only script, status.sh)
# nothing is copied into run.log, no header and no path lines are written.
use_state() {
  local common name copy=true
  if [ "${1:-}" = --no-log ]; then
    copy=false
    shift
  fi
  common=$(cd "$(git rev-parse --git-common-dir)" && pwd)
  name=$(plan_name_of_branch "$1")
  AGENT_STATE=$common/agent/${name//\//-}
  mkdir -p "$AGENT_STATE"
  rmdir "$AGENT_TMP" 2>/dev/null || true # the scratch directory made above, if still empty
  AGENT_TMP=$AGENT_STATE
  $copy || return 0
  if [ -z "${AGENT_LOGGING:-}" ]; then
    export AGENT_LOGGING=1
    log_header
    exec 2> >(tee -a "$AGENT_STATE/run.log" >&2)
  fi
  log "log: $AGENT_STATE/run.log   (follow it: tail -f $AGENT_STATE/run.log)"
  log "streams: $AGENT_STATE/*.jsonl"
  log "state: $AGENT_STATE"
}

# A header line in run.log for each start: the date, the script and its arguments.
log_header() { echo "=== $(date '+%Y-%m-%d %H:%M:%S') $AGENT_CMDLINE ===" >>"$AGENT_STATE/run.log"; }

# The process of a run that holds the lock of this state directory, or nothing.
active_run() {
  local pid
  pid=$(cat "$AGENT_STATE/run.pid" 2>/dev/null || true)
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then echo "$pid"; fi
}

# Takes the lock (run.pid) of this state directory, unless an outer script holds it already
# (AGENT_LOCKED). A run.pid naming a live process stops the script; a stale one is replaced. The
# lock is released when the script ends, also on Ctrl+C or kill.
lock_run() {
  [ -z "${AGENT_LOCKED:-}" ] || return 0
  local pid
  pid=$(active_run)
  [ -z "$pid" ] || die "a run is already active: pid $pid, log $AGENT_STATE/run.log"
  if [ -e "$AGENT_STATE/run.pid" ]; then
    log "replacing the stale lock of pid $(cat "$AGENT_STATE/run.pid")"
    rm -f "$AGENT_STATE/run.pid"
  fi
  (
    set -o noclobber
    echo $$ >"$AGENT_STATE/run.pid"
  ) 2>/dev/null || die "a run is already active (log $AGENT_STATE/run.log)"
  export AGENT_LOCKED=1
  trap 'rm -f "$AGENT_STATE/run.pid"' EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM HUP
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

# How the pull request of plan branch $1 references its issue: "Closes #<issue>" only when every
# other plan branch of the issue (plan/<issue>-*) has a merged pull request, so a split issue stays
# open until its last plan is merged; "Part of #<issue>" otherwise.
issue_reference() {
  local issue other
  issue=$(issue_of_branch "$1")
  for other in $(git ls-remote --heads origin "plan/$issue-*" | sed 's|.*refs/heads/||'); do
    [ "$other" != "$1" ] || continue
    if [ -z "$(gh pr list --head "$other" --state merged --json number --jq '.[0].number // empty')" ]; then
      echo "Part of #$issue"
      return
    fi
  done
  echo "Closes #$issue"
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
# session_id) to <output json>. Fails if the agent reports an error. While it runs, each step of
# the agent (tool calls, messages, to-dos) is logged; the full stream goes to <output json>l. The
# files of an earlier run of the same name (an interrupted one has a stream and no result) are kept
# under a name with the time, so that its usage still counts.
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
  log "running $(model_name "$model") ($(basename "$prompt")); stream: ${out}l"
  if [ -e "$out" ] || [ -e "${out}l" ]; then
    local old
    old="${out%.json}.$(date +%Y%m%d%H%M%S)-$$"
    if [ -e "${out}l" ]; then mv "${out}l" "$old.jsonl"; fi
    if [ -e "$out" ]; then mv "$out" "$old.json"; fi
  fi
  # dontAsk: a tool outside the allowed list is refused instead of waiting for an answer.
  # The agent gets no GitHub token: it talks to GitHub only through the scripts.
  env -u GH_TOKEN -u GITHUB_TOKEN claude -p --output-format stream-json --verbose --permission-mode dontAsk \
    --allowedTools "${tools[@]}" --disallowedTools "${AGENT_DENIED_TOOLS[@]}" \
    ${extra[@]+"${extra[@]}"} <"$prompt" | py "$AGENT_DIR/agent_json.py" stream "$out" "${out}l" "${AGENT_STEP_LABEL:-}" || true
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
  local pr=$1 step=$2 round=$3 body=$4 result stream
  shift 4
  # Each result with the streams of its interrupted runs (run_agent keeps them next to it).
  local -a runs=()
  for result in "$@"; do
    runs+=("$result")
    for stream in "${result%.json}".*.jsonl; do
      if [ -e "$stream" ]; then runs+=("$stream"); fi
    done
  done
  {
    echo "<!-- agent-step step=$step round=$round ${AGENT_STEP_MARKER:+$AGENT_STEP_MARKER }$(usage_marker ${runs[@]+"${runs[@]}"}) -->"
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

# The names of the jobs that failed on commit $1 (other than the required check itself), comma
# separated, or nothing.
ci_failed_jobs() {
  gh api -X GET "repos/{owner}/{repo}/commits/$1/check-runs" -f per_page=100 \
    --jq "[.check_runs[] | select(.conclusion == \"failure\" and .name != \"$AGENT_CI_CHECK\") | .name] | unique | join(\", \")"
}

# --- Git -----------------------------------------------------------------------------------------

# Checks out branch $1 at its remote head, with a clean working tree. With --keep-changes, a dirty
# working tree is accepted when $1 is the current branch already (the uncommitted work of an
# interrupted run): the fast-forward then fails, leaving everything as it is, if it would
# overwrite those changes.
checkout_branch() {
  local keep=false
  if [ "${1:-}" = --keep-changes ]; then
    keep=true
    shift
  fi
  if [ -n "$(git status --porcelain)" ]; then
    if $keep && [ "$(git branch --show-current)" = "$1" ]; then
      log "keeping the uncommitted changes on $1"
    else
      die "the working tree is not clean"
    fi
  fi
  log "checking out $1"
  git fetch --quiet origin "$AGENT_BASE_BRANCH" "$1"
  git switch --quiet "$1" 2>/dev/null || git switch --quiet -c "$1" --track "origin/$1"
  git merge --quiet --ff-only "origin/$1"
}

push_branch() {
  local branch
  branch=$(git branch --show-current)
  case $branch in main | "$AGENT_BASE_BRANCH") die "refusing to push $branch" ;; esac
  log "pushing $branch"
  git push --quiet origin "HEAD:refs/heads/$branch"
  milestone "pushed $branch ($(git rev-parse --short HEAD))"
}
