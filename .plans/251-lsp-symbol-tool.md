# Plan: LSP symbol tool for the agents, behind a switch

- **Issue**: #251 (Agentic flow: evaluate an LSP symbol tool for the agents)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A developer can run the agent flow with Claude Code's LSP tool switched on (`AGENT_LSP=1`) or off
(the default, unchanged), so the baseline and the trial of #251 run under the same scripts and
prompts and differ only by that one setting. With it on, the developer and review agents get a
repository-owned LSP plugin (jdtls for Java, the TypeScript server for `.ts` and `.vue`, pyright for
Python), the `LSP` tool in their allow-list, and a short "find symbols before reading files" rule.
`mise run agent:lsp-check` answers the issue's questions on the developer's machine in one run:
whether a headless `claude -p` gets the tool, under which name, and how long each server takes until
its first answer. The measuring (steps 1, 3 and 4 of the issue) stays with the developer and happens
on later application issues; this pull request gives them the switch, the check and the instructions.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`) | `docs/agentic-development.md` (settings are `AGENT_*` environment variables declared and defaulted at the top of `lib.sh` and listed in its Settings table; prompts in `scripts/agent/prompts/`; Python helpers standard library only, tested by `scripts/agent/test_*.py` through `mise run agent:test`); Bash 3.2 compatible (comment at the top of `lib.sh`); quiet output (`scripts/quiet.sh`) for anything an agent runs |
| mise tasks (`mise.toml`) | `docs/development.md` task list; an `agent:*` task per script, like `agent:stats` |
| Docs | `docs/agentic-development.md` (Settings, Measuring the usage, Safety), `docs/development.md` (task list); `docs/coding-convention/repository-code-comments.md` |
| Whole repository | `docs/coding-convention/repository-versioning-and-releases.md` (minor bump) |

Headless agents cannot change `.claude/`, set the executable bit or run env-prefixed commands, so
nothing in this plan goes under `.claude/`, and every new shell script is started as
`bash <script>` (no `chmod`).

## Design

### What the investigation found (answers to the issue's questions, to be confirmed by WP2's check)

- **The marketplace plugins cannot be loaded as they are.** In `claude-plugins-official`, the
  `jdtls-lsp`, `typescript-lsp` and `pyright-lsp` plugin folders hold only a README; their
  `lspServers` live in the marketplace entry (`strict: false`). `--plugin-dir` or
  `CLAUDE_CODE_PLUGIN_DIRS` pointing at those folders would load a plugin with no server. Installing
  them with `/plugin install` writes into the developer's user settings, which the agents share with
  every other session. So the repository gets its own plugin, `scripts/agent/lsp/`, with the three
  servers in its `.lsp.json`, loaded only for agent runs with `--plugin-dir`.
- **Tool name**: Claude Code (2.1.310 here) exposes one tool, `LSP`, with operations such as
  `goToDefinition`, `findReferences`, `hover`, `documentSymbol`, `workspaceSymbol`,
  `goToImplementation` and the call hierarchy. The binary still knows `ENABLE_LSP_TOOL`; `run_agent`
  sets it to `1` with the switch, which costs nothing if it is no longer needed. `--allowedTools LSP`
  is the entry for the allow-lists. WP2's check confirms the name: if it differs, WP2 changes the one
  constant `AGENT_LSP_TOOL` in `lib.sh`.
- **Start-up cost**: a language server is started by the `claude` process and ends with it, so it is
  paid once per agent run (each work package, review, fix), not per call. jdtls is the expensive one:
  it imports the Gradle build. Its workspace data (`-data`) goes to a fixed folder per worktree
  (`~/.cache/tap-agent-jdtls/<hash of the worktree path>`), so only the first run in a worktree
  imports from scratch and later runs of the same issue reuse the index. `agent:lsp-check` prints the
  time to the first answer per language, cold and warm.
- **Switch**: `AGENT_LSP=1` (default `0`). Off, the agents run exactly as today (same tool list, same
  prompts, no plugin), so a baseline taken with this pull request merged is comparable.
- **Diagnostics**: LSP servers also push diagnostics after an edit, which Claude Code may put into the
  context; that is tokens too. pyright runs with `typeCheckingMode: "off"` (ruff and the build check
  the code already); the jdtls and TypeScript diagnostics are part of what the trial measures.

### The plugin: `scripts/agent/lsp/`

- `.claude-plugin/plugin.json`: name `tap-agent-lsp`, version, one-line description (the shape of
  `.claude/mods/agent-progress/.claude-plugin/plugin.json`).
- `.lsp.json`: three servers, each started as `bash ${CLAUDE_PLUGIN_ROOT}/<wrapper>.sh` (no
  executable bit needed):
  - `jdtls`: `.java` → `java`; `startupTimeout` 120000 like the marketplace entry.
  - `typescript`: `.ts`, `.mts`, `.cts`, `.js`, `.mjs` and `.vue` → their language ids;
    `initializationOptions` name the frontend's TypeScript (`artifact/frontend/node_modules/typescript/lib`)
    and the `@vue/typescript-plugin` for `.vue`, so `.vue` script blocks resolve like `.ts`. If
    `.vue` does not work in WP2's check, `.vue` is left out of the mapping and the check's output says
    so (see Decisions).
  - `pyright`: `.py`, `.pyi` → `python`.
- Wrappers (Bash 3.2, `set -eu`, repository root from `git rev-parse --show-toplevel`):
  - `jdtls.sh`: fails with "jdtls not found: brew install jdtls" when `jdtls` is not on `PATH`;
    otherwise `exec jdtls -data <per-worktree folder> "$@"`, with the Java mise provides (`mise.toml`
    pins Temurin 25; jdtls needs 21 or later).
  - `typescript.sh` and `pyright.sh`: the Node.js and pnpm the Gradle build downloaded
    (`artifact/frontend/with-node.sh` finds them; `mise run setup` must have run), then
    `pnpm dlx` of a pinned `typescript-language-server` (plus `@vue/typescript-plugin` at the
    version matching the frontend's `vue-tsc` 3.3.x) and a pinned `pyright` (`pyright-langserver
    --stdio`). Pinned versions sit in variables at the top of each wrapper. Nothing is added to the
    frontend's `package.json` (Knip would flag an unused package) or to `mise.toml`'s `[tools]`
    (every developer and CI would install them).
- `pyrightconfig.json` at the repository root (pyright reads its config from the workspace root, the
  agents' working directory): `venvPath` `artifact/ta-runner`, `venv` `.venv`, `include`
  `artifact/ta-runner` and `scripts`, `typeCheckingMode` `off`.

### The switch: `scripts/agent/lib.sh`

- Declare `AGENT_LSP` (default `0`) with the other settings, and in the header comment.
- `AGENT_LSP_TOOL=LSP`.
- `lsp_enabled()`: true when `AGENT_LSP=1`.
- `run_agent`: when `lsp_enabled`, add `"$AGENT_LSP_TOOL"` to the tool list it was given, add
  `--plugin-dir "$AGENT_DIR/lsp"` to the extra arguments, pass `ENABLE_LSP_TOOL=1` in the `env` that
  starts `claude`, and log `LSP tools on` in the "running …" line. This covers every caller
  (implement, fix-ci, fix, review, update-main) without touching `AGENT_DEV_TOOLS` /
  `AGENT_REVIEW_TOOLS` themselves, so the off state is byte for byte today's command line.
- `prompt()`: when `lsp_enabled`, append `prompts/tools-lsp.md` after `tools.md`.
- `prompts/tools-lsp.md` (new, a few lines under `## Finding code`): look a symbol up with the `LSP`
  tool (`workspaceSymbol` for a class or function by name, `findReferences` for its callers,
  `goToDefinition` from a use, `documentSymbol` for a file's outline with line numbers) before
  reading files, then `Read` only those lines with `offset` / `limit`; fall back to `Grep` for text
  that is not a symbol (config keys, SQL, Markdown) or when the tool answers nothing.

### The check: `agent:lsp-check`

`scripts/agent/lsp-check.sh` (run as `bash scripts/agent/lsp-check.sh`) sources `lib.sh`, forces the
switch on, and for each language runs one short headless agent through `run_agent` with the review
tool list (read only), a scratch `AGENT_TMP` and a prompt that asks for one `LSP` call on a known
symbol and its answer as structured output (`--json-schema`: `tool_seen`, `answer`):

- Java: `findReferences` of `DomainExceptionHandler`
  (`artifact/backend/bff/impl/.../error/DomainExceptionHandler.java`);
- TypeScript: `findReferences` of an export of `artifact/frontend/src/shared/api/client.ts`, and one
  `.vue` file's `documentSymbol`;
- Python: `findReferences` of `build_config` in `artifact/ta-runner/ta_runner/engine/compat.py`.

It prints one line per language: whether the tool was there (the stream's `init` message lists the
tools; `agent_json.py` gets a small `tools` subcommand that prints them from a stream file), the
wall time, the agent's turns and tokens (`usage-line`), and the answer's first line. With `--twice`
it runs the Java check a second time to show the warm jdtls start. A missing server binary or a
missing `mise run setup` fails with the wrapper's message. `mise.toml` gets the task
`agent:lsp-check` (`run = "bash scripts/agent/lsp-check.sh"`). It uses the developer's login like
every agent run and costs a few thousand tokens per language.

### How the evaluation is run (documented, not automated)

1. Baseline: the next application issue runs as today (`AGENT_LSP` unset); afterwards
   `mise run agent:stats <issue>` and the numbers go into #251.
2. Trial: `mise run agent:lsp-check` once; then `AGENT_LSP=1 mise run agent:run plan/<issue>-<slug>`
   (or `export AGENT_LSP=1` before `/develop-issue`) on the next comparable issue, and its
   `agent:stats` into #251: tokens per step, `Read` characters, the `LSP` row, turns, cost, step
   durations from the progress comment.
3. Decision in #251: keep (a follow-up flips the default to `1`) or drop (a follow-up removes the
   plugin, the switch and this section).

`transcript_stats.py` already counts every tool by name, so the `LSP` tool gets its own row with no
change.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Repository LSP plugin

- **Depends on**: none
- **Status**: not done: Write was denied for `pyrightconfig.json` at the repository root; the rest is done
- **Files**:
  - `scripts/agent/lsp/.claude-plugin/plugin.json` (new)
  - `scripts/agent/lsp/.lsp.json` (new)
  - `scripts/agent/lsp/jdtls.sh` (new)
  - `scripts/agent/lsp/typescript.sh` (new)
  - `scripts/agent/lsp/pyright.sh` (new)
  - `pyrightconfig.json` (new)
  - `scripts/agent/test_lsp_plugin.py` (new)
- **Steps**:
  - [x] Write `plugin.json` and `.lsp.json` as in the design; every command is `bash` with
        `${CLAUDE_PLUGIN_ROOT}/<wrapper>.sh` as its first argument.
  - [x] Write the three wrappers (Bash 3.2, pinned versions in variables at the top, clear message
        and non-zero exit when a prerequisite is missing).
  - [ ] Write `pyrightconfig.json` as in the design.
- **Tests**: `test_lsp_plugin.py` (unittest, standard library, picked up by `mise run agent:test`):
  `.lsp.json` is valid JSON; every server's command is `bash` and its wrapper file exists in
  `scripts/agent/lsp/`; the mapped extensions cover `.java`, `.ts`, `.vue`, `.py`; `plugin.json` has a
  name and version; each wrapper passes `bash -n` (run through `subprocess`). `mise run check`.

### WP2: The switch, the prompt rule and the check

- **Depends on**: WP1
- **Status**: done
- **Files**:
  - `scripts/agent/lib.sh`
  - `scripts/agent/prompts/tools-lsp.md` (new)
  - `scripts/agent/lsp-check.sh` (new)
  - `scripts/agent/agent_json.py`
  - `scripts/agent/test_agent_json.py`
  - `mise.toml`
- **Steps**:
  - [x] `lib.sh`: `AGENT_LSP`, `AGENT_LSP_TOOL`, `lsp_enabled`, the additions to `run_agent` and
        `prompt` as in the design; header comment lists `AGENT_LSP`.
  - [x] `prompts/tools-lsp.md` as in the design.
  - [x] `agent_json.py`: `tools <stream.jsonl>` prints the tool names of the stream's `init`
        message, one per line.
  - [x] `lsp-check.sh` and the `agent:lsp-check` task in `mise.toml` (its description names
        `--twice`).
  - [x] Run `mise run agent:lsp-check` once if the machine has the servers and the login, and put
        its output (tool name, times) into the pull request's final comment; if the tool name is not
        `LSP`, change `AGENT_LSP_TOOL` and the prompt accordingly. (Skipped: jdtls is not installed
        on the agent's machine; the developer runs it once, `AGENT_LSP_TOOL` stays `LSP` until then.)
- **Tests**: `test_agent_json.py`: `tools` on a stream with an `init` message listing `LSP`, and on
  one without an `init` message (prints nothing). `bash -n` on `lib.sh` and `lsp-check.sh`.
  `mise run check` (includes `agent:test`). The off state: with `AGENT_LSP` unset, the prompt built
  by `prompt implement.md` is identical to today's (no `tools-lsp.md`), checked by reading the
  function, as `lib.sh` has no shell tests.

### WP3: Documentation and version

- **Depends on**: WP2
- **Status**: done
- **Files**:
  - `docs/agentic-development.md`
  - `docs/development.md`
  - `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
    (through `mise run version:bump minor`)
- **Steps**:
  - [x] `docs/agentic-development.md`, Settings table: `AGENT_LSP` | `0` | 1: the agents get the LSP
        tool (`scripts/agent/lsp/`: jdtls, TypeScript with Vue, pyright) and the "find symbols
        first" rule; under evaluation in #251.
  - [x] `docs/agentic-development.md`, a subsection "Trying the LSP tool" under "Measuring the
        usage": the prerequisites (`brew install jdtls`, `mise run setup` for Node.js and pnpm; the
        TypeScript and pyright servers are fetched by `pnpm dlx` on first use), `mise run
        agent:lsp-check` and what it prints, the cold and warm jdtls start and where its index
        lives, and the three evaluation steps from the design.
  - [x] `docs/agentic-development.md`, Safety: the `LSP` tool only reads (definitions, references,
        symbols); the servers run locally on the developer's machine.
  - [x] `docs/development.md`: `mise run agent:lsp-check` in the task list next to `agent:stats`.
  - [x] `mise run version:bump minor`.
- **Tests**: `mise run check` (the doc link check of the agent scripts, `doc_links.py`, runs with
  `agent:test`).

## Tests

- `scripts/agent/test_lsp_plugin.py` and the new cases in `test_agent_json.py`, through
  `mise run agent:test` (part of `mise run check` and CI).
- `mise run agent:lsp-check` is the end-to-end proof that a headless agent gets the `LSP` tool from
  the repository plugin and gets an answer in each language; it needs the developer's login and the
  servers, so it is not part of CI. The issue's "done when" (a measured keep or drop) is decided by
  the developer after two application issues, outside this pull request.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md`, "Settings" | Row for `AGENT_LSP` |
| Text | `docs/agentic-development.md`, "Measuring the usage" | New subsection "Trying the LSP tool": prerequisites, `agent:lsp-check`, start-up cost, the evaluation steps |
| Text | `docs/agentic-development.md`, "Safety" | The `LSP` tool is read only; servers run locally |
| Text | `docs/development.md`, task list | `mise run agent:lsp-check` |
| Screenshot | none | No page of the application changes |

## Out of scope

- Running the baseline and the trial and recording the numbers (issue steps 1, 3, 4): done by the
  developer on the next application issues, in #251, which therefore stays open after this pull
  request is merged (`Part of #251`, see Decisions).
- Making the LSP tool the default, or removing it: a follow-up after the decision.
- The developer's own interactive sessions and the `develop-issue` orchestration session: the switch
  covers the headless agents only (`run_agent`).
- Renovate updates of the pinned server versions in the wrappers: only if the tool is kept.
- An LLM-generated wiki of the code (not planned in the issue).

## Decisions

Answers of the developer to the plan's open questions:

- **Closing the issue**: #251 stays open for the measurement. The pull request says
  `Part of #251`, not `Closes #251`: the orchestration session edits the pull request body by hand
  after `implement.sh` (the scripts write `Closes #251`, as this is the issue's only plan).
- **Server installation**: as planned. jdtls from Homebrew (`brew install jdtls`, manual, once per
  machine); the TypeScript server and pyright through `pnpm dlx` at versions pinned in the wrappers.
- **`.vue` support**: if the TypeScript server with `@vue/typescript-plugin` does not answer for
  `.vue` files in WP2's check, `.vue` is left out of `.lsp.json` (and out of `test_lsp_plugin.py`'s
  extension list), the trial runs `.ts`-only, and the pull request's final comment says so.
- **`pyrightconfig.json` at the repository root**: accepted.
- **Headless plugin loading**: if `--plugin-dir` drops the agent-progress mod from the headless
  agents, that is accepted.
