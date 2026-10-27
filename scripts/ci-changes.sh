#!/usr/bin/env bash
# Which CI jobs a change needs (.github/workflows/ci.yml, job "changes").
#
#   scripts/ci-changes.sh <base sha>   areas of the files changed from <base> to HEAD
#   scripts/ci-changes.sh ''           no base known: every area is true
#
# Prints app=, runner=, deploy= and server_e2e= (true or false) on stdout, for $GITHUB_OUTPUT, and
# the changed files and the reasons on stderr. Runs in the current repository.
#
#   app         anything but the non-application set (docs, plans, agent pipeline): the jar, the
#               images and the end-to-end tests
#   runner      app, or the agent scripts, skills and Markdown documents (their tests and the link
#               check run in the runner job)
#   deploy      app (the deploy builds and starts the application, so any application change can
#               break it)
#   server_e2e  app
#
# The non-application set is an allow-list: a file nobody listed is an application change.
set -euo pipefail

base=${1:-}

app=false
runner=false
deploy=false
server_e2e=false

if [ -z "$base" ]; then
  echo "no base to compare with: every area runs" >&2
  app=true runner=true deploy=true server_e2e=true
else
  # --no-renames lists the old path of a moved file too, so moving a file out of an area counts
  files=$(git diff --name-only --no-renames "$base" HEAD)
  echo "changed since $base:" >&2
  sed 's/^/  /' <<<"$files" >&2

  while IFS= read -r file; do
    [ -n "$file" ] || continue
    case $file in
      docs/* | .plans/* | README.md | CLAUDE.md | .claude/* | scripts/agent/*) ;;
      *) app=true ;;
    esac
    case $file in
      scripts/agent/* | .claude/skills/* | docs/* | README.md | CLAUDE.md) runner=true ;;
    esac
  done <<<"$files"
  [ "$app" = true ] && runner=true deploy=true server_e2e=true
fi

echo "app=$app runner=$runner deploy=$deploy server_e2e=$server_e2e" >&2
echo "app=$app"
echo "runner=$runner"
echo "deploy=$deploy"
echo "server_e2e=$server_e2e"
