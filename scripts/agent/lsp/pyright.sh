#!/usr/bin/env bash
# Starts pyright's language server for the agents, using the Node.js and pnpm of `mise run setup`.
set -eu

PYRIGHT_VERSION=1.1.408

root=$(git rev-parse --show-toplevel)

exec "$root/artifact/frontend/with-node.sh" pnpm dlx --package "pyright@$PYRIGHT_VERSION" \
  pyright-langserver --stdio
