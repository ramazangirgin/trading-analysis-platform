#!/usr/bin/env bash
# Starts the TypeScript language server (with the Vue plugin for .vue files) for the agents, using
# the Node.js and pnpm of `mise run setup`. The packages are installed once into a cache folder;
# .lsp.json names the same folder for the Vue plugin.
set -eu

SERVER_VERSION=5.1.3
TYPESCRIPT_VERSION=5.9.3
VUE_PLUGIN_VERSION=3.3.12

root=$(git rev-parse --show-toplevel)
cache="$HOME/.cache/tap-agent-lsp/typescript"
stamp="$cache/.installed-$SERVER_VERSION-$TYPESCRIPT_VERSION-$VUE_PLUGIN_VERSION"
with_node="$root/artifact/frontend/with-node.sh"

if [ ! -f "$stamp" ]; then
  mkdir -p "$cache"
  "$with_node" pnpm --dir "$cache" add \
    "typescript-language-server@$SERVER_VERSION" \
    "typescript@$TYPESCRIPT_VERSION" \
    "@vue/typescript-plugin@$VUE_PLUGIN_VERSION" >&2
  : >"$stamp"
fi

exec "$with_node" "$cache/node_modules/.bin/typescript-language-server" --stdio \
  --tsserver-path "$cache/node_modules/typescript/lib/tsserver.js"
