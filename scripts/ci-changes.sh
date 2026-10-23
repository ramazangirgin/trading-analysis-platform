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
#   runner      app, or the agent scripts and skills (their tests run in the runner job)
#   deploy      deploy/, mise.toml, .github/
#   server_e2e  deploy/, the ta-runner Dockerfile, the CI workflow, this script
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
  files=$(git diff --name-only "$base" HEAD)
  echo "changed since $base:" >&2
  sed 's/^/  /' <<<"$files" >&2

  while IFS= read -r file; do
    [ -n "$file" ] || continue
    case $file in
      docs/* | .plans/* | README.md | CLAUDE.md | .claude/* | scripts/agent/*) ;;
      *) app=true ;;
    esac
    case $file in
      scripts/agent/* | .claude/skills/*) runner=true ;;
    esac
    case $file in
      deploy/* | mise.toml | .github/*) deploy=true ;;
    esac
    case $file in
      deploy/* | artifact/ta-runner/Dockerfile | .github/workflows/ci.yml | scripts/ci-changes.sh) server_e2e=true ;;
    esac
  done <<<"$files"
  [ "$app" = true ] && runner=true
fi

echo "app=$app runner=$runner deploy=$deploy server_e2e=$server_e2e" >&2
echo "app=$app"
echo "runner=$runner"
echo "deploy=$deploy"
echo "server_e2e=$server_e2e"
