#!/usr/bin/env bash
# Starts jdtls for the agents. Its workspace data is kept per worktree, so only the first run in a
# worktree imports the Gradle build from scratch.
set -eu

if ! command -v jdtls >/dev/null 2>&1; then
  echo "jdtls not found: brew install jdtls" >&2
  exit 1
fi

root=$(git rev-parse --show-toplevel)
hash=$(printf '%s' "$root" | shasum | cut -c1-16)
data="$HOME/.cache/tap-agent-jdtls/$hash"
mkdir -p "$data"

if java_home=$(cd "$root" && mise where java 2>/dev/null) && [ -n "$java_home" ]; then
  export JAVA_HOME="$java_home"
  export PATH="$java_home/bin:$PATH"
fi

exec jdtls -data "$data" "$@"
