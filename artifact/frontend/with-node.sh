#!/bin/sh
# Runs a command with the Node.js and pnpm the Gradle build downloaded (`mise run setup`) on PATH.
# Used by the Git hooks (lefthook.yml), which run outside Gradle.
cd "$(dirname "$0")" || exit 1
node_bin=$(ls -d .gradle/nodejs/*/bin 2>/dev/null | tail -n 1)
pnpm_bin=$(ls -d .gradle/pnpm/*/bin 2>/dev/null | tail -n 1)
if [ -z "$node_bin" ] || [ -z "$pnpm_bin" ] || [ ! -d node_modules ]; then
  echo "Node.js, pnpm or the frontend's packages are missing: run 'mise run setup' first." >&2
  exit 1
fi
PATH="$PWD/$node_bin:$PWD/$pnpm_bin:$PATH" exec "$@"
