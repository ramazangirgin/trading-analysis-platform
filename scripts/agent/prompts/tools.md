
## Working within your tools

Your tools are limited to an allow-list; anything else is refused without asking, and you cannot
get it allowed. So:

- Read, create and change files only with the Read, Edit and Write tools. Never write files from
  the shell (no `cat >`, `tee`, heredocs, `python -`, `sed -i`): those commands are refused.
- Run commands from the repository root, one command per call, e.g. `mise run check`,
  `mise run gradle :frontend:build`, `artifact/frontend/with-node.sh pnpm exec vitest run`,
  `git status`, `git diff`. `mise` is on `PATH`; call it as `mise`, not by a full path.
- Allowed commands: `mise run` / `mise exec`, `./gradlew`, `uv run` / `uv sync`,
  `artifact/frontend/with-node.sh …`, `e2e/with-node.sh …`, `scripts/version.sh`, read-only `git`
  commands, and, for the developer, `git add` / `git commit` / `git rm` / `git mv` / `git restore`;
  plus `ls`, `cat`, `head`, `tail`, `wc`, `find`, `grep`, `rg`, `sed -n`, `mkdir`, `jq`.
- If a command you need is refused, find another way with the allowed ones, and mention it in
  your final message.

## Reading files and output

- Read only the part of a file you need: `Read` with `offset` and `limit`, `Grep` with `-n` and
  context (`-C`), `sed -n '<from>,<to>p'`. Read a whole file only when it is short or you change
  most of it.
- Read a file again only after it changed (your edit or a command's), never to "check" it.
- Command output is quiet: a successful `mise run …` prints only `ok:` lines. On a failure, read
  the excerpt first, and the log (`build/quiet/<name>.log`, named in the output) only in parts
  (`grep -n`, `tail -n`, `sed -n`).
- Run Gradle as `mise run gradle <tasks>`, e.g. `mise run gradle :backend:test --tests '*FooTest'`.
