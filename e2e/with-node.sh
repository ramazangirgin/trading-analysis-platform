#!/bin/sh
# Runs a command in e2e/ with the Node.js and pnpm the frontend's Gradle build downloaded
# (./gradlew :frontend:pnpmInstall, or `mise run setup`), so there is one Node version to keep.
cd "$(dirname "$0")" || exit 1
frontend=../artifact/frontend
node_bin=$(ls -d "$frontend"/.gradle/nodejs/*/bin 2>/dev/null | tail -n 1)
pnpm_bin=$(ls -d "$frontend"/.gradle/pnpm/*/bin 2>/dev/null | tail -n 1)
if [ -z "$node_bin" ] || [ -z "$pnpm_bin" ]; then
  echo "Node.js or pnpm is missing: run './gradlew :frontend:pnpmInstall' first." >&2
  exit 1
fi
PATH="$(cd "$node_bin" && pwd):$(cd "$pnpm_bin" && pwd):$PATH" exec "$@"
