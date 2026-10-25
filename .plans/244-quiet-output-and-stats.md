# Plan: Agentic flow: quiet tool output, no screenshot reads, transcript stats

- **Issue**: #244 (Agentic flow: cut tokens per issue (orchestration, work package size, update with main, quiet tool output, smaller docs))
- **Plan**: 1 of 3 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The agents' tool output gets small: `mise run check`, `mise run format`, `mise run format-check`,
`mise run runner-test`, the Gradle calls the agents make and the pre-commit hook print one line per
command on success and an excerpt plus a log path on failure, when they run inside an agent
(`AGENT_QUIET=1`). The agents are told to read only the part of a file they need and never to open a
screenshot as an image; `mise run screenshots` prints a report (dimensions, size, new / changed /
unchanged, likely blank) instead, and reading PNGs is denied to the agents. A new script,
`scripts/agent/transcript_stats.py` (`mise run agent:stats <issue>`), summarises an issue's agent
transcripts (turns, context size, tokens, tool calls and tool output by tool, files read more than
twice), so the baseline in #244 and the effect of plans 2 and 3 can be measured.

This is the first of three plans for #244. Plan 2 (`244-update-main-and-wp-size`) moves the update
with `main` into a headless step and caps work package size; plan 3 (`244-readme-topic-files`)
splits `README.md` into topic files.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`, `scripts/`) | `docs/agentic-development.md`; Bash 3.2 compatible (no negative indices, guarded empty arrays, as `lib.sh` says); Python helpers standard library only, run with `uv run --no-project python`, tested with `unittest` in `scripts/agent/test_*.py` (picked up by `mise run agent:test`, part of `mise run check` and CI); usage line at the top of each script, as the existing scripts have |
| mise tasks, Git hooks (`mise.toml`, `lefthook.yml`) | `docs/coding-convention/repository-git-hooks.md` (fast, static checks on staged files; CI is the authority); every task keeps its `description` |
| Agent prompts (`scripts/agent/prompts/`) | the plain, imperative style of the existing prompts; `tools.md` is appended to every prompt (`prompt` in `lib.sh`) |
| Docs | `docs/agentic-development.md`, `docs/coding-convention/repository-git-hooks.md`, `README.md` "Tasks", `CLAUDE.md` "Commands" |

No backend, frontend or ta-runner code changes.

## Design

### Quiet output: `scripts/quiet.sh`

`scripts/quiet.sh <name> <command> [args...]` runs one command.

- Without `AGENT_QUIET=1` it `exec`s the command unchanged: developers and CI see what they see today.
- With `AGENT_QUIET=1` it writes the command's stdout and stderr to `build/quiet/<name>.log` (repository
  root; `build/` is gitignored), and then:
  - on success prints one line: `ok: <name> (<seconds>s; full output: build/quiet/<name>.log)`;
  - on failure prints `failed: <name> (exit <n>; full output: build/quiet/<name>.log)`, then an
    excerpt: the lines matching `error|fail|exception|✖|warning:|What went wrong` (case-insensitive)
    with the line after each, at most 60 lines, then the last 30 lines of the log (a line already in
    the excerpt is not repeated), and exits with the command's status.
- The repository root is found from the script's own location (as `scripts/version.sh` does), so it
  works from a sub-project folder (lefthook jobs with `root:`).

`AGENT_QUIET` is set only for the agents: `run_agent` in `scripts/agent/lib.sh` exports
`AGENT_QUIET=1` into the environment of `claude -p`, so every Bash call of an agent, and the
lefthook hook of its `git commit`, inherits it. The final checks in `implement.sh`
(`out=$(mise run check 2>&1)`) are not inside an agent and stay as they are.

The commands that go through it:

- `mise.toml`: every command of `check`, `format`, `format-check`, `runner-test`, `agent:test`,
  `skill:test` becomes `scripts/quiet.sh <task>-<part> <command>` (e.g. `check-gradle`,
  `check-frontend-types`, `check-e2e-types`, `check-runner`, `format-gradle`, `format-frontend`,
  `format-e2e`, `runner-test-ruff`, ...). A command with `cd ... &&` or `sh -c` is wrapped as
  `scripts/quiet.sh <name> sh -c '<command>'`. Task references (`{ task = "agent:test" }`) stay.
- A new task `gradle`: `run = "scripts/quiet.sh gradle ./gradlew --console=plain"`, so
  `mise run gradle :backend:test` (the arguments after the task name are appended by mise; the
  developer agent checks this) runs Gradle quietly inside an agent. The prompts switch their Gradle
  calls to it (WP2). `./gradlew` stays allowed.
- `lefthook.yml`: every job's `run:` is prefixed with the quiet wrapper (a job with `root:` calls it
  as `../../scripts/quiet.sh` or by an absolute path from `{root}`; the developer agent picks what
  lefthook supports), named `hook-<job>`. lefthook's own job summary stays.

### Prompts: reading files

`scripts/agent/prompts/tools.md` gets a section "Reading files and output":

- read only the part of a file you need: `Read` with `offset`/`limit`, `Grep` with `-n` and context
  (`-C`), `sed -n '<from>,<to>p'`; read a whole file only when it is short or you change most of it;
- read a file again only after it changed (yours or a command's change), never to "check" it;
- the quiet output: a successful `mise run …` prints only `ok:` lines; on a failure read the excerpt
  first, and the log (`build/quiet/<name>.log`) only in parts (`grep -n`, `tail -n`, `sed -n`);
- run Gradle as `mise run gradle <tasks>` (e.g. `mise run gradle :backend:test --tests '*FooTest'`).

`implement.md`, `fix.md` and `fix-ci.md` replace `./gradlew --console=plain …` with
`mise run gradle …` in their check lines.

### Screenshots without reading them

- `lib.sh`: `AGENT_DENIED_TOOLS` gets `"Read(**/*.png)"`, so no agent (developer or review) can open
  a PNG. The developer verifies on the first run after the merge that the rule matches (the stats
  script lists no `Read` of a `.png`); the agents cannot run `claude` themselves.
- `scripts/agent/screenshot_report.py <stamp file> [<dir>]` (standard library): for every
  `docs/screenshots/*.png` (or `<dir>`, for `SCREENSHOT_DIR`) written after the stamp file's mtime,
  one line `<name>.png  <width>×<height>  <KB> KB  new|changed|unchanged` (from the PNG's IHDR chunk;
  `new` / `changed` / `unchanged` from `git status --porcelain` and `git diff --quiet` on the file),
  then a summary line. It exits 1 when a written image is suspicious: width or height under 100
  pixels, or under 0.01 bytes per pixel (a near-uniform, likely blank page), naming the file and why.
  A shot that matched the name filter but wrote no file is not its business (Playwright fails that).
- `mise.toml` `screenshots`: a first command writes the stamp (`mkdir -p build && touch
  build/screenshots.stamp`), the last one runs the report.
- `implement.md` and `fix.md`: instead of "look at the PNG with the Read tool and retake it until …",
  "never open a PNG; check the report line `mise run screenshots <name>` prints (dimensions, new or
  changed, not blank) and that your `.shot.ts` brings the page into the state the plan describes
  (the selectors you wait for are visible before `shot()`)". `review.md`: instead of "open the PNGs
  with the Read tool", check that every screenshot the plan lists is in the diff
  (`git diff --stat {{BASE}}...HEAD -- docs/screenshots`) with a `.shot.ts` that sets up the state the
  plan describes, and shown in the docs; whether it looks right is the developer's review.

### Transcript stats: `scripts/agent/transcript_stats.py`

```
transcript_stats.py <issue> [--projects DIR] [--folder NAME] [--state DIR]
                    [--transcript FILE]... [--since EPOCH] [--until EPOCH] [--json]
```

- **Transcripts**: Claude Code keeps them in `~/.claude/projects/<folder>/<session>.jsonl`, with
  subagents in `<session>/subagents/*.jsonl`; `<folder>` is the directory with every character but
  letters and digits as `-` (as `latest_transcript` in `agent_json.py` computes it). The default
  folder is that of the issue's worktree, `<main checkout>-<issue>` (from
  `git rev-parse --git-common-dir`), which holds the headless agents' sessions and the
  `develop-issue` session run there; `--folder` overrides it. `--transcript` adds files, for the
  orchestration session started in the main checkout, which is shared by every issue and is
  therefore cut by `--since` / `--until` (epochs; the progress comment's start and end).
- **Steps**: each transcript's session ID is matched against the results in the state directory
  (`.git/agent/<issue>-*/*.json`, `session_id`; `--state` overrides): `wp-*` and `implement-retry-*`
  → implement, `review-*` → review, `fix-ci-*` → CI fix, `fix-*` → fix, `update-main*` → update with
  main (plan 2); anything else → session. A subagent transcript counts under its parent's step.
- **Per transcript** (assistant messages counted once per message ID, as `session_usage` does):
  turns; context first / last / max (`input_tokens + cache_creation_input_tokens +
  cache_read_input_tokens` of a message); uncached input (input plus cache writes), cached input,
  output, total; cost from `agent_json.price_of` (`n/a` for an unknown model); tool calls per tool
  name; tool output in characters per tool (the `tool_result` blocks, matched to their `tool_use`
  by ID; text length, an image block counted as an image, with its count); `Read` calls per file
  path, listing the files read more than twice.
- **Output**: Markdown, ready to paste into an issue comment: a table per step (transcripts, turns,
  max context, cached / uncached / output tokens, cost), a table per transcript, the tools table
  (calls, output characters, share of output, images), and the files read more than twice (top 20).
  `--json` prints the same as JSON.
- `mise.toml`: task `agent:stats` (`uv run --no-project python scripts/agent/transcript_stats.py`).

It reuses `price_of` and `epoch_of` from `agent_json.py` by importing it (same folder) rather than
copying them.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Quiet wrapper, mise tasks and hooks

- **Status**: done
- **Depends on**: none
- **Files**: `scripts/quiet.sh` (new), `scripts/agent/test_quiet.py` (new), `mise.toml`,
  `scripts/agent/lib.sh`
- **Steps**:
  - [x] `scripts/quiet.sh` as in the design (usage line at the top, `set -euo pipefail`, Bash 3.2)
  - [x] wrap the commands of `check`, `format`, `format-check`, `runner-test`, `agent:test`,
    `skill:test`; add the `gradle` task; keep every task's behaviour without `AGENT_QUIET`
  - [x] `run_agent` exports `AGENT_QUIET=1` into the agent's environment; document it in the
    variables list at the top of `lib.sh`
- **Tests**: `test_quiet.py` runs `scripts/quiet.sh` with throwaway commands (`sh -c 'echo a; exit
  0'`, a failing one with error lines and many lines of noise): without `AGENT_QUIET` the output is
  passed through and the status kept; with it, success prints one `ok:` line and writes the log,
  failure prints the excerpt (matching lines, the tail, no duplicates, at most the limits) and the
  log path, and keeps the exit status. The log goes to a temporary root (an environment variable for
  the log directory, e.g. `QUIET_LOG_DIR`, defaulting to `build/quiet`, so the test does not write
  into the repository).

### WP2: Prompts: read less, quiet output, Gradle through mise

- **Status**: done
- **Depends on**: WP1
- **Files**: `scripts/agent/prompts/tools.md`, `scripts/agent/prompts/implement.md`,
  `scripts/agent/prompts/fix.md`, `scripts/agent/prompts/fix-ci.md`
- **Steps**:
  - [x] `tools.md`: the section "Reading files and output" from the design; list `mise run gradle`
    among the example commands
  - [x] `implement.md`, `fix.md`, `fix-ci.md`: Gradle checks as `mise run gradle …`
- **Tests**: `mise run agent:test` (the prompts are read by no test; `lib.sh`'s `prompt` still
  concatenates them). The effect is measured with WP4's script on the first run after the merge.

### WP3: Screenshot report instead of reading PNGs

- **Status**: open
- **Depends on**: WP2
- **Files**: `scripts/agent/screenshot_report.py` (new), `scripts/agent/test_screenshot_report.py`
  (new), `mise.toml`, `scripts/agent/lib.sh`, `scripts/agent/prompts/implement.md`,
  `scripts/agent/prompts/fix.md`, `scripts/agent/prompts/review.md`
- **Steps**:
  - [ ] `screenshot_report.py` as in the design
  - [ ] the `screenshots` task writes the stamp first and runs the report last
  - [ ] `"Read(**/*.png)"` in `AGENT_DENIED_TOOLS`
  - [ ] the screenshot lines of `implement.md`, `fix.md` and `review.md` as in the design
- **Tests**: `test_screenshot_report.py`: PNGs written by the test (a minimal valid PNG built with
  `zlib` and `struct`: a large noisy one, a uniform 1440×900 one, a 50×50 one) in a temporary Git
  repository: dimensions and size are read from IHDR; new / changed / unchanged come out right; a
  file older than the stamp is not listed; the uniform and the tiny image make it exit 1 with the
  reason; a normal one exits 0.

### WP4: Transcript stats

- **Status**: open
- **Depends on**: WP1
- **Files**: `scripts/agent/transcript_stats.py` (new), `scripts/agent/test_transcript_stats.py`
  (new), `mise.toml`
- **Steps**:
  - [ ] `transcript_stats.py` as in the design
  - [ ] task `agent:stats`
- **Tests**: `test_transcript_stats.py` with transcripts written by the test into a temporary
  projects folder and a temporary state directory: two sessions (one matched to `wp-WP1.json`, one
  to `review-1.json`) and a subagent transcript; a message repeated with the same ID counts once;
  context first / last / max; cached / uncached / output sums; tool calls and output characters per
  tool, an image result counted as an image; a file read three times is listed, one read twice is
  not; `--since` / `--until` cut a `--transcript` file; an unknown model has no cost; the Markdown
  and the `--json` output carry the same numbers.

### WP5: Docs

- **Status**: open
- **Depends on**: WP1, WP2, WP3, WP4
- **Files**: `docs/agentic-development.md`, `docs/coding-convention/repository-git-hooks.md`,
  `README.md`, `CLAUDE.md`
- **Steps**:
  - [ ] the docs listed under "Docs to update"
  - [ ] bump the version (`scripts/version.sh bump minor`), as the last package
- **Tests**: `mise run check` (Prettier does not cover Markdown here; the links are checked by
  reading them).

### WP6: Hooks and the by-hand check (by hand, not by the agents)

- **Status**: not done: manual: the agents may not edit `lefthook.yml` and may not run commands with an environment variable in front (`AGENT_QUIET=1 …`), so the developer does this by hand
- **Depends on**: WP1
- **Files**: `lefthook.yml`
- **Steps**:
  - [ ] wrap the lefthook jobs' `run:` commands as in the design
  - [ ] check by hand: `AGENT_QUIET=1 mise run check` prints only `ok:` lines (and mise's own task
    lines); a commit of a staged Java file with `AGENT_QUIET=1` prints the hook summary only
- **Tests**: the by-hand check above.

## Tests

- Unit: `test_quiet.py`, `test_screenshot_report.py`, `test_transcript_stats.py` in
  `scripts/agent/`, run by `mise run agent:test` (part of `mise run check` and CI's agent-scripts
  job).
- By hand (WP1): `AGENT_QUIET=1 mise run check` and a commit through the hook print the short form;
  without the variable both print as before.
- The issue's "done when" for this part: the `mise` tasks and commit hooks the agents use print a
  short summary on success (WP1), agents do not read screenshots as images (WP3), and the stats
  script exists with a unit test (WP4). The baseline numbers and the comparison after the change
  are the developer's (see "Out of scope").

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md`, "Documentation and screenshots" | the agents never open PNGs (`Read(**/*.png)` is denied); `mise run screenshots` prints the report and fails on a likely blank or tiny image; the developer checks the look in the pull request |
| Text | `docs/agentic-development.md`, "Settings" and "Safety" | `AGENT_QUIET` (set by `run_agent` for every agent; a developer may set it too) and the logs in `build/quiet/`; the PNG rule in the denied tools |
| Text | `docs/agentic-development.md`, new section "Measuring the usage" (before "Settings") | `mise run agent:stats <issue>`: where it reads, what it prints, `--transcript` / `--since` / `--until` for the orchestration session, and that #244 holds the baseline |
| Text | `docs/coding-convention/repository-git-hooks.md` | the jobs run through `scripts/quiet.sh`: quiet with `AGENT_QUIET=1`, unchanged otherwise |
| Text | `README.md`, "Tasks" | `mise run gradle <tasks>` and `mise run agent:stats <issue>`; one sentence on `AGENT_QUIET` |
| Text | `CLAUDE.md`, "Commands" | `mise run gradle :backend:domain:analysis:adapter:test --tests '*SomeTest'` as the one-module form, and that `AGENT_QUIET=1` makes the tasks print `ok:` lines |
| Screenshot | none | no page of the platform changes |

## Out of scope

- Running `agent:stats` on the baseline issues (#194, #192, #198, #224, #195) and on the first issue
  after plan 3, and recording the comparison in a comment on #244: the developer's, after the merge
  (the agents have no `gh` and cannot read `~/.claude/projects`).
- The update with `main` as a headless step, merging `main` before the review rounds, work package
  size, the orchestration model: plan 2 (`244-update-main-and-wp-size`).
- Splitting `README.md`: plan 3 (`244-readme-topic-files`).
- An LSP symbol tool for the agents (issue item 6): to be evaluated in a follow-up issue once
  `agent:stats` has measured a run after plans 1 to 3, as the issue asks (keep it only if a re-run
  shows the saving).
- Quiet output for `mise run build`, `mise run e2e` and `mise run screenshots`' Playwright output:
  the agents do not run `build`; `e2e` and `screenshots` output is short next to Gradle's. They can
  be wrapped the same way later if the stats show them.

## Open questions

None open. Decided in review:

- Quiet output only inside the agents: `run_agent` sets `AGENT_QUIET=1`; developers and CI keep the
  full output.
- The logs go to `build/quiet/<name>.log`.
