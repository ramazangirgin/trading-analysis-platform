# Plan: Agent flow: progress of long-running steps visible from the Claude Code session

- **Issue**: #137 (Agent flow: make the progress of long-running steps visible from the Claude Code session)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

While `/develop-issue` runs a long step (implement, fix-ci, review, fix, finalise), the session
reports the step's progress as it happens, one line per milestone (a work package started or done,
tests, `mise run check` and its result, a commit, a push, the draft pull request, CI, a review
round and its findings, a fix round and each finding's outcome, finalise, a stop and its reason),
and nothing for the agents' individual tool calls. The step scripts write every milestone as a
distinct, greppable line in `run.log`, print the log and stream paths as the first lines of every
step, and `mise run agent:status <branch|pr>` answers "where is the run?" in one command, for the
session and for the developer. The skill starts the step scripts as they are (never piped through
`tail`, `head` or `grep`), names the right paths, and prints the `tail -f` command for following a
step in a terminal.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`) | No coding-convention document of their own; follow the existing scripts: Bash 3.2 compatible (macOS), `set -euo pipefail` from `lib.sh`, shared helpers in `lib.sh`, standard-library-only Python helpers run through `py`, a header comment per script naming its usage and the files it leaves in `.git/agent/<issue>-<slug>/`. Python tests in `scripts/agent/test_*.py`, run by `mise run agent:test` (part of `mise run check` and CI's ta-runner job). |
| Claude Code skill (`.claude/skills/develop-issue/`) | The skill's own style: numbered steps, commands run in the issue's folder, one approval (the plan). |
| `mise.toml` | One task per agent script, `agent:<name>`, with a description that shows its usage. |
| Docs | `docs/agentic-development.md` (the flow and its walkthrough), root `README.md` ("Agentic development"). English, plain style. `docs/coding-convention/repository-versioning-and-releases.md`: one `minor` bump. |

## Design

### Milestone lines

Today every script line starts with `agent: ` and the agents' tool lines with `agent:   [mm:ss] `
(`agent_json.py stream`), so a reader cannot tell a milestone from "checking out …" or "running …".
A new helper in `scripts/agent/lib.sh`:

```sh
milestone() { echo "agent: >> $*" >&2; }
```

A milestone line is `agent: >> <text>`; everything else stays as it is (`agent: <text>` for the
scripts' other lines, `agent:   [mm:ss] <text>` for tool calls). Milestones go to stderr like every
other line, so the outermost script's `tee` copies them into `run.log` (`use_state`). `grep
'^agent: >> ' run.log` lists them. The pattern is written down once, in `status.sh` (below), which
the skill and the docs use; the docs name it too.

Texts that the skill or `next.sh --dry-run` readers match today keep their wording: `next step:
<step>` and `stopping: <reason>` only gain the `>> ` prefix (the skill matches the substring).

`die` writes its first line as a milestone, `agent: >> failed: <message>`, followed by any further
lines of the message as plain `agent:` lines, so a stop always reaches the session.

The milestones, by script (the label of a package is today's `WP2 (2 of 4)`):

| Where | Milestone lines |
|---|---|
| `implement.sh` | `<label>: started` (with `(on uncommitted work)` as today); `<label>: not finished, resuming the agent`; `<label>: done (<k> commits)` / `<label>: not done: <reason>`; `checking the implementation (attempt <n>)`; `checks passed` / `checks failed (attempt <n>): <first problem line>`; `draft pull request #<pr> opened: <url>` |
| `agent_json.py stream` (inside an agent run, see below) | `<label>: tests`, `<label>: mise run check`, `<label>: mise run check passed` / `failed`, `<label>: commit` |
| `lib.sh` `push_branch` | `pushed <branch> (<short sha>)` (after the push; the "pushing" line stays a plain line) |
| `next.sh` | `#<pr>: next step: <step>`; `#<pr>: waiting for <AGENT_CI_CHECK> on <short sha>` (once per head, the 2-minute progress lines stay plain); `#<pr>: CI passed on <short sha>` / `#<pr>: CI failed on <short sha>: <failed job names>`; `stopping: <reason>` |
| `review.sh` | `review R<round> started (<model>)`; `review R<round>: approved <short sha>` / `review R<round>: <n> findings (<c> CRITICAL, <m> MAJOR, <i> MINOR)` |
| `fix.sh` | `fix round <round> started (<n> findings)`; `R<round>-<i>: fixed (<short sha>)` / `declined` / `open: <reason>` / `skipped (thread resolved)`; `fix round <round> done (<k> commits)` |
| `fix-ci.sh` | `CI fix <attempt> started (run <id>)`; `CI fix <attempt>: <k> commit(s) pushed` / `CI fix <attempt>: nothing pushed` |
| `finalise.sh` | `finalise #<pr> started`; `#<pr> is ready for review (<approved by the review agent | N open findings>)` |

`next.sh`'s failed job names: the `check-runs` of the head with `conclusion == "failure"`, other
than `AGENT_CI_CHECK` itself, from the same `gh api …/commits/<sha>/check-runs` call as
`ci_status` (a new helper `ci_failed_jobs <sha>` in `lib.sh`).

### Phases inside an agent run

`agent_json.py stream` already sees every tool call of the agent. It gets an optional third
argument, a label (`stream <result.json> <log.jsonl> [label]`), and writes a milestone line
`agent: >> <label>: <phase>` when a Bash tool call starts a new phase:

| Phase | Bash command matches |
|---|---|
| `mise run check` | `mise run check` |
| `tests` | `mise run test`, `mise run runner-test`, `mise run agent:test`, `mise run skill:test`, `mise run e2e`, `./gradlew … test` (a task named `test` or ending in `Test`/`:test`), `uv run pytest`, `pnpm … test`, `vitest` |
| `commit` | `git commit` |

A phase is written once while it lasts: a run of test calls in a row is one `tests` line; the
next phase change writes the next line. For `mise run check` the matching tool result (same
`tool_use_id`) adds `mise run check passed` or `mise run check failed` (the result's `is_error`).
Without a label (callers that pass none) no phase lines are written. The matching rules are a table
in `agent_json.py` (like `TOOL_DETAIL`), so they are easy to extend.

`run_agent` in `lib.sh` passes `${AGENT_STEP_LABEL:-}` as the label. `implement.sh` sets
`AGENT_STEP_LABEL=$label` for a package's runs and `final checks` for the retries of the final
checks; `fix-ci.sh` sets `CI fix <attempt>`; `fix.sh` sets the finding ID. `review.sh` sets none (the
review agent runs no tests and commits nothing).

### Paths first

`use_state` prints, as the first lines of every step (before `state:` and anything else):

```text
agent: log: <state>/run.log   (follow it: tail -f <state>/run.log)
agent: streams: <state>/*.jsonl
```

`run_agent`'s line becomes `running <model> (<prompt>); stream: <state>/<name>.jsonl`. The
`state: …` line stays. Nested scripts (run.sh → implement.sh) print the lines again, which is fine:
each step's output starts with them.

### Line-buffered output

Every line is written as it happens: `log`, `milestone` and `die` are `echo`s to stderr, the Python
progress uses `flush=True`, and `tee` writes what it reads without buffering. The plan keeps it so
and adds nothing that buffers (no pipes through `sed`, `awk` or `grep` between a script's stderr and
the terminal). The `| tail` on the #116 run came from the skill, which this plan fixes.

### `status.sh`

`scripts/agent/status.sh <branch | pr> [lines]` (`mise run agent:status`), read only:

- the active run: the pid from `run.pid` when that process is alive (`active_run`), or "no run is
  active";
- the package the implementation is on, from `current`;
- the latest milestones: the last 10 `agent: >> ` lines of `run.log`;
- the last `lines` (default 20) lines of `run.log`.

`status.sh --follow <branch | pr>` prints nothing of the above; it follows `run.log` and prints only
the milestone lines written from now on (`tail -n 0 -F <run.log> | grep --line-buffered '^agent: >> '`),
until it is stopped. That is the command the skill watches with the `Monitor` tool, and the one
place where the milestone pattern is used.

It must not write to `run.log` or take the lock. `use_state` gets an option for that:
`use_state --no-log <branch>` sets `AGENT_STATE` and `AGENT_TMP` without the `tee`, the header line
and the path lines. A pull request number resolves to its branch with `gh pr view` as in
`next.sh`.

### The skill

`.claude/skills/develop-issue/SKILL.md`, the section "Long-running scripts" (and steps 4 and 5 where
they start a script):

- Start every step script as it is, with `run_in_background`, never piped through `tail`, `head` or
  `grep`: the background output must fill while the step runs.
- Progress goes to `.git/agent/<issue>-<slug>/run.log` (in the main repository's `.git`, shared by
  the worktrees; the script prints the path first). Before each long step, print
  `tail -f <run.log>`, so the developer can follow it in a terminal.
- Right after starting a step, start `scripts/agent/status.sh --follow <branch>` with the `Monitor`
  tool (`timeout_ms` 1800000, re-armed if it expires before the step ends) and pass each event on
  as one line, as it arrives (`WP2 (2 of 4): tests`, `review R1: 3 findings (0 CRITICAL, 2 MAJOR,
  1 MINOR)`, …). Nothing for individual tool calls. Stop the monitor (`TaskStop`) when the step's
  completion notification arrives. CI waits (step 5.2) and the update with `main` (step 6) are
  reported by the session itself as milestones too: CI started, passed, failed (which job); updated
  with `main`.
- When the developer asks for the status, run `scripts/agent/status.sh <branch>` and summarise it
  (active step, package, latest milestones). On a failed step, show the end of the step's output
  and of `run.log`, tool lines included.
- Fix the stream path: `.git/agent/<issue>-<slug>/<step>.jsonl` (`wp-WP1.jsonl`, `review-1.jsonl`,
  `fix-R1-2.jsonl`, `fix-ci-1.jsonl`), not `$TMPDIR/agent-<pid>/`.

The other skills need no change: `plan-from-issue` runs only `plan-branch.sh` (seconds), and
`renovate-update` and `dependabot-fix` start no agent script (`renovate-update` already watches its
waits with `Monitor`).

## Work packages

### WP1: Milestone lines, paths and phases in the shared helpers

- **Depends on**: none
- **Status**: done
- **Files**: `scripts/agent/lib.sh`, `scripts/agent/agent_json.py`, `scripts/agent/test_agent_json.py`
- **Steps**:
  - [x] `lib.sh`: add `milestone`; `die` writes its first line as `agent: >> failed: …`; `push_branch` writes `pushed <branch> (<short sha>)` after the push; add `ci_failed_jobs <sha>`.
  - [x] `lib.sh`: `use_state` prints the `log:` and `streams:` lines first; `use_state --no-log <branch>` (no `tee`, header or path lines); update the header comment (milestone lines, the option).
  - [x] `lib.sh`: `run_agent` prints `stream: <path>` and passes `${AGENT_STEP_LABEL:-}` to `agent_json.py stream`.
  - [x] `agent_json.py`: optional label argument of `stream`; the phase table and the phase milestones (one line per phase change; `mise run check passed|failed` from the tool result's `is_error`); update the module docstring.
- **Tests**: `test_agent_json.py`: a stream of tool calls with a label writes `agent: >> WP1: tests` once for consecutive test calls, then `… mise run check`, `… mise run check failed` for an `is_error` result (and `passed` for a successful one), then `… commit`; without a label no `>>` line is written; the per-tool lines and the result file stay as before. Each phase pattern of the table has a matching and a non-matching command (`./gradlew :backend:test` vs `./gradlew build`).

### WP2: Milestones in the step scripts

- **Depends on**: WP1
- **Status**: done
- **Files**: `scripts/agent/implement.sh`, `scripts/agent/next.sh`, `scripts/agent/review.sh`, `scripts/agent/fix.sh`, `scripts/agent/fix-ci.sh`, `scripts/agent/finalise.sh`
- **Steps**:
  - [x] `implement.sh`: the milestones of the table (packages, final checks, draft pull request with its number and link); `AGENT_STEP_LABEL` per package and for the final checks' retries.
  - [x] `next.sh`: `next step`, CI waiting (once per head), CI passed / failed with the failed jobs (`ci_failed_jobs`), `stopping` as milestones.
  - [x] `review.sh`: started (with the model) and the outcome: approved, or the number of findings per priority (from the agent's `structured_output.findings`).
  - [x] `fix.sh`: round started (number of findings), one line per finding with its outcome, round done; `AGENT_STEP_LABEL` per finding.
  - [x] `fix-ci.sh`: started (attempt, run), commits pushed or nothing pushed; `AGENT_STEP_LABEL`.
  - [x] `finalise.sh`: started, ready for review with the review agent's approval or the number of open findings.
  - [x] Keep the texts `next step: <step>` and `stopping: <reason>` (the skill matches them); update each script's header comment where it lists its output.
- **Tests**: no shell test harness exists for the step scripts: `bash -n` on every changed script and `mise run check`. The milestone texts are checked against the table in the design.

### WP3: `status.sh` and its mise task

- **Depends on**: WP1
- **Status**: done
- **Files**: `scripts/agent/status.sh` (new, executable), `mise.toml`
- **Steps**:
  - [x] `status.sh <branch | pr> [lines]`: active run (pid), current package, the last 10 milestones, the last `lines` (default 20) lines of `run.log`; "no run log yet" when there is none. Uses `use_state --no-log`, never `lock_run`.
  - [x] `status.sh --follow <branch | pr>`: `tail -n 0 -F <run.log> | grep --line-buffered '^agent: >> '`.
  - [x] Header comment in the style of the other scripts.
  - [x] `mise.toml`: `[tasks."agent:status"]`, description "Where an agent run is: active pid, work package, latest milestones and log lines: mise run agent:status plan/<issue>-<slug> (or a PR number)".
- **Tests**: `bash -n`; run `status.sh` against a plan branch with and without a `run.log` (a state directory made in the test run: `.git/agent/<name>/run.log` with a few lines) and check its output by hand; `mise run check`.

### WP4: The skill and the docs

- **Depends on**: WP2, WP3
- **Status**: not done: edits to .claude/skills/develop-issue/SKILL.md are denied in this session; docs, README and version bump done
- **Files**: `.claude/skills/develop-issue/SKILL.md`, `docs/agentic-development.md`, `README.md`, `scripts/agent/test_agent_json.py` (docstring only, if it names the stream helper's usage), version files (`scripts/version.sh bump minor`)
- **Steps**:
  - [ ] `SKILL.md`: rewrite "Long-running scripts" as in the design (start as is, no pipes; `run.log` and stream paths; `tail -f` before each long step; `Monitor` on `status.sh --follow`, one line per milestone, stop it at the end; `status.sh` on request; tool lines only on a failure); steps 4 and 5.3 refer to it; steps 5.2 and 6 report CI and the update with `main` as milestones.
  - [x] `docs/agentic-development.md`: step 4: the milestone lines (`agent: >> `, what they cover, `grep '^agent: >> ' run.log`), the paths printed first, `mise run agent:status` and `status.sh --follow`; the "state directory" bullet names the milestones in `run.log`; "From Claude Code, with one approval: the plan": how the session reports progress (one line per milestone, from `run.log`, the status on request) and the `tail -f` command.
  - [x] `README.md`, "Agentic development": one sentence that `mise run agent:status <branch>` shows where a run is and that `/develop-issue` reports each milestone as it happens.
  - [x] Bump the version: `scripts/version.sh bump minor`.
- **Tests**: `mise run check`; the docs' commands match the scripts (`status.sh` usage, the milestone prefix).

## Tests

- Unit: `test_agent_json.py` covers the phase milestones of `agent_json.py stream` (run by `mise run
  agent:test`, part of `mise run check` and CI).
- Shell: `bash -n` and `mise run check`; no test harness exists for the step scripts, and adding
  one is out of scope.
- The issue's acceptance criteria are proved on the first `/develop-issue` run after the merge:
  the session prints one line per milestone and none per tool call, the background output of every
  step fills while it runs, and "what is the progress?" is answered from `status.sh`. This plan's
  own run cannot show them: the scripts run from a copy of `scripts/agent/` taken when each step
  starts (from `main`), and the session follows the skill as it is on `main`. The review agent
  checks the milestone lines in the scripts against the table above.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md`, walkthrough step 4 | Milestone lines and their prefix, paths printed first, `mise run agent:status`, `status.sh --follow` |
| Text | `docs/agentic-development.md`, "Decision", the state directory bullet | `run.log` holds the milestone lines next to the tool lines |
| Text | `docs/agentic-development.md`, "From Claude Code, with one approval: the plan" | How the session reports progress (one line per milestone, status on request) and the `tail -f` command |
| Text | `README.md`, "Agentic development" | `mise run agent:status`, and that `/develop-issue` reports milestones as they happen |
| Text | `.claude/skills/develop-issue/SKILL.md` | "Long-running scripts" rewritten; correct stream path |
| Screenshot | none | No page of the application changes |

## Out of scope

- A test harness for the Bash step scripts (bats or similar): not in the repository today; a
  separate issue if wanted.
- Changing what `next.sh`, `run.sh` or `detach.sh` do beyond their log lines.
- Push notifications per milestone: the skill still notifies only when a run stops or the pull
  request is ready.
