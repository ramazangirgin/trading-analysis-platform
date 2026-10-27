#!/usr/bin/env bash
# The Agent Dashboard issue (docs/agentic-development.md, "Dashboard"): one open issue that shows
# the step graph of every active agent process.
#
#   scripts/agent/dashboard.sh                         print the rebuilt body, post nothing (mise run agent:dashboard)
#   scripts/agent/dashboard.sh --post                  rebuild the body and post it
#   scripts/agent/dashboard.sh update [--async | --wait] <branch>   the hook of progress.sh and agent_exit:
#                                                      this branch's section, and the sections of the
#                                                      pull requests that are no longer active dropped
#
# A rebuild covers every open pull request of a plan/* branch and every state directory under
# .git/agent/ with a live run. The graph, the section and the merge of the body are dashboard.py's;
# this script reads GitHub and the state directory into one record per branch and writes the body.
# The issue (open, labelled agent-dashboard, created by the agent account) is found by the number
# cached in .git/agent/dashboard.issue, or by its label, or created and pinned.
# One writer per machine: dashboard.lock holds the pid; update --async leaves the branch in
# dashboard.pending for the writer to serve again before it releases the lock. Another machine may
# write in between, so a body that does not hold this machine's sections after the write is
# rebuilt from the new body, up to 3 times.
# AGENT_DASHBOARD=0 turns every command but the print-only one off. update never fails: a GitHub
# call that fails is logged.
# shellcheck source=scripts/agent/lib.sh
source "$(dirname "$0")/lib.sh"

DASHBOARD_LABEL=agent-dashboard
BODY_MARKER="<!-- agent-dashboard -->"

mode=print
async=false
case ${1:-} in
  "") ;;
  --post) mode=post ;;
  update)
    mode=update
    shift
    case ${1:-} in
      --async)
        async=true
        shift
        ;;
      --wait) shift ;;
    esac
    update_branch=${1:?usage: $0 update [--async | --wait] <branch>}
    ;;
  *) die "usage: $0 [--post | update [--async | --wait] <branch>]" ;;
esac
if [ "$mode" != print ] && [ "$AGENT_DASHBOARD" != 1 ]; then exit 0; fi
# update is a hook: it goes on and logs when a step fails, so the functions below return their
# status explicitly instead of relying on set -e.
if [ "$mode" = update ]; then
  [[ $update_branch == plan/* ]] || exit 0
  set +e
fi

agent_home=$(cd "$(git rev-parse --git-common-dir)" && pwd)/agent
mkdir -p "$agent_home"
rmdir "$AGENT_TMP" 2>/dev/null || true # the scratch directory lib.sh made, if still empty
work=$(mktemp -d "${TMPDIR:-/tmp}/agent-dashboard.XXXXXX")
lock=""
login=""
repo_url=""

# Releases the lock and the scratch files, and hands the exit status on to agent_exit.
cleanup() {
  local status=$?
  [ -z "$lock" ] || rm -f "$lock"
  rm -rf "$work"
  return "$status"
}
trap 'cleanup; agent_exit' EXIT

json_field() {
  py -c 'import json, sys
d = json.load(open(sys.argv[1]))
for key in sys.argv[2:]:
    d = (d or {}).get(key)
print("" if d is None else d)' "$@"
}

# gather [--light] <branch> <record file>: the record dashboard.py reads (its docstring). Light is
# the pull request and its comments, enough to tell whether a process is still active.
gather() {
  local light=false
  if [ "$1" = --light ]; then
    light=true
    shift
  fi
  local branch=$1 out=$2 name dir d number title="" prnum comment_url="" id
  name=$(plan_name_of_branch "$branch")
  dir=$agent_home/$name
  d=$work/gather-$name
  rm -rf "$d"
  mkdir -p "$d"
  if [ -z "$login" ]; then
    login=$(agent_login) || return 1
    AGENT_GITHUB_LOGIN=$login
  fi
  [ -n "$repo_url" ] || repo_url=https://github.com/$(repo_slug) || return 1
  number=${name%%-*}
  gh pr list --head "$branch" --state all --limit 1 \
    --json number,url,state,isDraft,labels,headRefOid,title --jq '.[0] // null' >"$d/pr.json" || return 1
  prnum=$(json_field "$d/pr.json" number) || return 1
  : >"$d/comments.jsonl"
  if [ -n "$prnum" ]; then
    gh api --paginate "repos/{owner}/{repo}/issues/$prnum/comments" \
      --jq ".[] | select(.user.login == \"$login\") | {url: .html_url, body: .body}" >"$d/comments.jsonl" || return 1
  fi
  : >"$d/plan.md"
  echo '[]' >"$d/ci.json"
  if ! $light; then
    title=$(gh issue view "$number" --json title --jq .title 2>/dev/null || true)
    gh api -H "Accept: application/vnd.github.raw" "repos/{owner}/{repo}/contents/.plans/$name.md?ref=$branch" \
      >"$d/plan.md" 2>/dev/null || : >"$d/plan.md"
    gh run list --branch "$branch" --workflow ci.yml --json headSha,url,status,conclusion --limit 20 \
      >"$d/ci.json" 2>/dev/null || echo '[]' >"$d/ci.json"
    id=$(cat "$dir/progress.comment" 2>/dev/null || true)
    [ -z "$id" ] || comment_url=$repo_url/issues/$number#issuecomment-$id
  fi
  local progress_file="" alive=0
  [ ! -f "$dir/progress.tsv" ] || progress_file=$dir/progress.tsv
  AGENT_STATE=$dir
  [ -z "$(active_run)" ] || alive=1
  py -c 'import json, os, sys
d, branch, repo_url, title, progress_file, alive, comment_url = sys.argv[1:]

def read(name):
    try:
        with open(os.path.join(d, name), encoding="utf-8") as f:
            return f.read()
    except OSError:
        return ""

def loads(name, default):
    try:
        return json.loads(read(name))
    except ValueError:
        return default

print(json.dumps({
    "branch": branch,
    "repo_url": repo_url,
    "issue_title": title,
    "pr": loads("pr.json", None),
    "plan_text": read("plan.md"),
    "comments": [json.loads(line) for line in read("comments.jsonl").splitlines() if line.strip()],
    "ci_runs": loads("ci.json", []),
    "progress_file": progress_file,
    "run_alive": alive == "1",
    "progress_comment_url": comment_url,
}))' "$d" "$branch" "$repo_url" "$title" "$progress_file" "$alive" "$comment_url" >"$out"
}

dashboard_py() { py "$AGENT_DIR/dashboard.py" "$@"; }

# The issue number, from the cache or the label; empty when there is none.
find_issue() {
  local file=$agent_home/dashboard.issue number state
  number=$(cat "$file" 2>/dev/null || true)
  if [ -n "$number" ]; then
    state=$(gh api "repos/{owner}/{repo}/issues/$number" --jq .state 2>/dev/null || true)
    [ "$state" = open ] || number=""
  fi
  if [ -z "$number" ]; then
    [ -n "$login" ] || login=$(agent_login) || return 1
    AGENT_GITHUB_LOGIN=$login
    number=$(gh issue list --label "$DASHBOARD_LABEL" --state open --author "$login" --limit 1 \
      --json number --jq '.[0].number // empty') || return 1
  fi
  [ -z "$number" ] || echo "$number" >"$file"
  echo "$number"
}

create_issue() {
  local url number
  gh label create "$DASHBOARD_LABEL" --color 1D76DB --description "The issue that shows the active agent processes" \
    --force >/dev/null || log "dashboard: the label $DASHBOARD_LABEL was not created"
  url=$(gh issue create --title "Agent Dashboard" --label "$DASHBOARD_LABEL" --body "$BODY_MARKER") || return 1
  number=${url##*/}
  gh issue pin "$number" >/dev/null 2>&1 || log "dashboard: issue #$number was not pinned"
  echo "$number" >"$agent_home/dashboard.issue"
  echo "$number"
}

read_body() { gh api "repos/{owner}/{repo}/issues/$1" --jq '.body // ""' >"$2"; }

# render <branch>...: the section of each branch into $work, in the arrays sections (--section
# arguments), rendered and active.
sections=()
rendered=()
active=()
render() {
  local branch record section
  for branch in "$@"; do
    record=$work/record-${branch//\//-}.json
    section=$work/section-${branch//\//-}.md
    if ! gather "$branch" "$record"; then
      log "dashboard: $branch was not read from GitHub, its section stays as it is"
      continue
    fi
    if ! dashboard_py section "$record" --max-rounds "$AGENT_MAX_ROUNDS" --max-ci-fixes "$AGENT_MAX_CI_FIXES" >"$section"; then
      log "dashboard: the section of $branch was not rendered"
      continue
    fi
    sections+=(--section "$section")
    rendered+=("$branch")
    if dashboard_py active "$record"; then active+=("$branch"); fi
  done
}

# merge_body <old body> <new body>: the old body with the rendered sections in, and the sections of
# processes that are no longer active out. A section of a branch that was not rendered here (another
# machine's) stays while its pull request is active; one that cannot be read stays too.
merge_body() {
  local branch keep=("${active[@]+"${active[@]}"}") record
  for branch in $(sed -n 's/^<!-- agent-process \([^ ]*\) -->$/\1/p' "$1"); do
    case " ${rendered[*]-} " in *" $branch "*) continue ;; esac
    record=$work/old-${branch//\//-}.json
    if ! gather --light "$branch" "$record" || dashboard_py active "$record"; then keep+=("$branch"); fi
  done
  dashboard_py merge "$1" "${sections[@]+"${sections[@]}"}" --active "${keep[@]+"${keep[@]}"}" >"$2"
}

# present <body file>: whether the body holds every rendered section as written.
present() {
  local files=() arg
  for arg in "${sections[@]+"${sections[@]}"}"; do
    [ "$arg" = --section ] || files+=("$arg")
  done
  [ "${#files[@]}" -gt 0 ] || return 0
  py -c 'import sys
body = open(sys.argv[1], encoding="utf-8").read()
sys.exit(0 if all(open(p, encoding="utf-8").read().strip() in body for p in sys.argv[2:]) else 1)' "$1" "${files[@]}"
}

# publish <branch>...: renders these branches and writes the body, again from the new body when
# another writer got in between.
publish() {
  local issue tries=0
  issue=$(find_issue) || return 1
  [ -n "$issue" ] || issue=$(create_issue) || return 1
  render "$@"
  while [ "$tries" -lt 3 ]; do
    tries=$((tries + 1))
    read_body "$issue" "$work/old-body.md" || return 1
    merge_body "$work/old-body.md" "$work/new-body.md" || return 1
    gh api -X PATCH "repos/{owner}/{repo}/issues/$issue" -F "body=@$work/new-body.md" >/dev/null || return 1
    read_body "$issue" "$work/check-body.md" || return 1
    if present "$work/check-body.md"; then return 0; fi
    log "dashboard: the issue changed meanwhile, writing again ($tries of 3)"
  done
  log "dashboard: the issue #$issue does not hold the sections as written after 3 tries"
  return 1
}

# The branches a rebuild renders: open plan pull requests and state directories with a live run.
# A branch with no local state keeps the section it has (another machine's run).
rebuild_branches() {
  local old=$1 branch d name pid local_state
  {
    gh pr list --state open --limit 100 --json headRefName --jq '.[].headRefName | select(startswith("plan/"))' || return 1
    for d in "$agent_home"/*/; do
      [ -d "$d" ] || continue
      pid=$(cat "$d/run.pid" 2>/dev/null || true)
      if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then echo "plan/$(basename "$d")"; fi
    done
  } | sort -u | while read -r branch; do
    name=$(plan_name_of_branch "$branch")
    local_state=false
    if [ -f "$agent_home/$name/progress.tsv" ] || [ -f "$agent_home/$name/run.pid" ]; then local_state=true; fi
    if $local_state || ! grep -qF "<!-- agent-process $branch -->" "$old"; then echo "$branch"; fi
  done
}

case $mode in
  print | post)
    issue=$(find_issue) || die "the dashboard issue was not looked up"
    : >"$work/old-body.md"
    [ -z "$issue" ] || read_body "$issue" "$work/old-body.md" || die "the dashboard issue #$issue was not read"
    branches=$(rebuild_branches "$work/old-body.md") || die "the pull requests were not listed"
    if [ "$mode" = print ]; then
      # shellcheck disable=SC2086
      render $branches
      merge_body "$work/old-body.md" "$work/new-body.md" || die "the dashboard body was not built"
      cat "$work/new-body.md"
    else
      # shellcheck disable=SC2086
      publish $branches || die "the dashboard was not posted"
      echo "https://github.com/$(repo_slug)/issues/$(cat "$agent_home/dashboard.issue")"
    fi
    ;;
  update)
    pending=$agent_home/dashboard.pending
    holder_lock=$agent_home/dashboard.lock
    tries=0
    while ! (
      set -o noclobber
      echo $$ >"$holder_lock"
    ) 2>/dev/null; do
      holder=$(cat "$holder_lock" 2>/dev/null || true)
      if [ -n "$holder" ] && ! kill -0 "$holder" 2>/dev/null; then
        rm -f "$holder_lock" # a writer that died
        continue
      fi
      if $async; then
        echo "$update_branch" >>"$pending"
        exit 0
      fi
      tries=$((tries + 1))
      if [ "$tries" -ge 60 ]; then
        log "dashboard: another writer holds $holder_lock, the dashboard was not updated"
        exit 0
      fi
      sleep 1
    done
    lock=$holder_lock
    batch=$update_branch
    while :; do
      if [ -e "$pending" ] && mv "$pending" "$work/pending" 2>/dev/null; then
        batch=$(printf '%s\n' "$batch" "$(cat "$work/pending")" | sed '/^$/d' | sort -u)
      fi
      [ -n "$batch" ] || break
      # shellcheck disable=SC2086
      publish $batch || log "dashboard: not updated"
      [ -e "$pending" ] || break
      batch=""
    done
    exit 0
    ;;
esac
