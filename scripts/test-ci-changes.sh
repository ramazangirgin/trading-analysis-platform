#!/usr/bin/env bash
# Tests scripts/ci-changes.sh against a throwaway Git repository (mise run ci:test-changes).
set -euo pipefail

script=$(cd "$(dirname "$0")" && pwd)/ci-changes.sh
repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT

cd "$repo"
git init -q
git config user.email test@example.com
git config user.name test
git commit -q --allow-empty -m base
base=$(git rev-parse HEAD)

failures=0
n=0

# check <expected "app runner deploy server_e2e"> <changed file>...
check() {
  local expected=$1 actual file
  shift
  n=$((n + 1))
  git reset -q --hard "$base"
  for file in "$@"; do
    mkdir -p "$(dirname "$file")"
    echo "$n" >"$file"
  done
  git add -A
  git commit -q -m "case $n"
  actual=$(bash "$script" "$base" 2>/dev/null | cut -d= -f2 | paste -sd' ' -)
  if [ "$actual" = "$expected" ]; then
    echo "ok   $*"
  else
    echo "FAIL $*: expected '$expected', got '$actual'" >&2
    failures=$((failures + 1))
  fi
}

#     app   runner deploy server_e2e
check "false true false false" docs/x.md
check "false false false false" .plans/1-x.md
check "false true false false" README.md
check "false true false false" CLAUDE.md
check "false true false false" scripts/agent/x.sh
check "false true false false" .claude/skills/x/SKILL.md
check "false false false false" .claude/settings.json
check "true true true true" artifact/backend/src/Main.java
check "true true true true" artifact/frontend/src/main.ts
check "true true true true" artifact/ta-runner/Dockerfile
check "true true true true" build-logic/src/main/kotlin/x.gradle.kts
check "true true true true" config/checkstyle/checkstyle.xml
check "true true true true" gradle.properties
check "true true true true" mise.toml
check "true true true true" deploy/Dockerfile
check "true true true true" .github/workflows/ci.yml
check "true true true true" new-top-level-file.txt
check "true true true true" docs/x.md artifact/backend/src/Main.java

n=$((n + 1))
git reset -q --hard "$base"
mkdir -p artifact/backend/src
echo "content that git sees as the same file after the move" >artifact/backend/src/Foo.java
git add -A
git commit -q -m "add Foo"
moved_base=$(git rev-parse HEAD)
mkdir -p docs
git mv artifact/backend/src/Foo.java docs/Foo.java
git commit -q -m "move Foo"
actual=$(bash "$script" "$moved_base" 2>/dev/null | cut -d= -f2 | paste -sd' ' -)
if [ "$actual" = "true true true true" ]; then
  echo "ok   file moved out of artifact/ into docs/"
else
  echo "FAIL file moved out of artifact/ into docs/: got '$actual'" >&2
  failures=$((failures + 1))
fi

n=$((n + 1))
actual=$(bash "$script" "" 2>/dev/null | cut -d= -f2 | paste -sd' ' -)
if [ "$actual" = "true true true true" ]; then
  echo "ok   empty base"
else
  echo "FAIL empty base: got '$actual'" >&2
  failures=$((failures + 1))
fi

[ "$failures" = 0 ] || {
  echo "$failures of $n cases failed" >&2
  exit 1
}
echo "$n cases passed"
