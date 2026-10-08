# Plan: Implement work package by work package, keep progress in the plan, resume after an interruption

- **Issue**: #118 (Agentic development: implement work package by work package, keep progress in the plan, resume after an interruption)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md): a change to
  the development tooling, nothing breaking for users

## Goal

The developer agent implements a plan one work package at a time. After each package the plan on
the branch says it is done (its steps ticked, a status line), the commits name the package, and the
script pushes the branch. Everything an agent run leaves behind (its log, its streams, its session
IDs, which package it is on) goes to a state directory per plan branch inside `.git/`, where a later
run finds it. An interrupted implementation, whether the interruption is a usage limit, a closed
terminal or a crash, is started again with the same command. It skips the packages that are done,
continues the one in progress on top of the uncommitted changes it left, and ends in the same draft
pull request as an uninterrupted run. A run can be started detached from the Claude Code session, so
closing the session does not kill it. The `develop-issue` skill recognises a half-done
implementation and offers to resume it, instead of stopping on the dirty working tree.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`) | The style of the existing scripts: Bash with `lib.sh` for shared functions, a header comment per script (usage, what it does, which step of `docs/agentic-development.md`), Python helpers standard library only and run through `py` (`uv run --no-project python`), shellcheck directives as in the existing files. The agents' tool allow-list and denied tools (`lib.sh`) stay as they are: no `git push`, no `--no-verify`. |
| Agent prompts (`scripts/agent/prompts/`) | The style of the existing prompts; `tools.md` is appended to every prompt. |
| Claude Code skills (`.claude/skills/`) | `develop-issue`: gates asked with `AskUserQuestion`, one approval per step, never retry on its own. `plan-from-issue`: the plan template is the plan's structure. |
| Tooling (`mise.toml`) | One task per script, with a description that shows its usage. |
| docs | `docs/agentic-development.md` describes the flow, the scripts and the settings, in English, in the plain style of the existing text. |
| Versioning | `docs/coding-convention/repository-versioning-and-releases.md`: one bump per pull request (`mise run version:bump minor`). |

## Design

### Work packages in the plan: what the scripts read and write

The plan template already gives every package the same shape (`### WP<id>: <name>`,
`- **Depends on**:`, `- **Steps**:` with `- [ ]` items). This plan adds one line under each heading:

```markdown
### WP2: settings (the first slice)

- **Status**: open
- **Depends on**: WP1, WP1c
```

- `open`: not started, or started and not finished.
- `done`: implemented and committed. Every step of the package is ticked (`- [x]`).
- `not done: <reason>`: the agent could not do it. Unticked steps stay unticked, and the reason goes
  into the pull request's "Not done or done differently" section too.

A plan without `Status` lines, for example one pushed before this change, counts every package as
`open`. A plan without any `### WP` heading is one package, `all`, which is the whole plan, so
implementing it works as it does today.

The issue suggested a `Done in <sha>` line. A commit cannot name its own SHA, and the agent commits
the plan together with the package's code, so the link goes the other way: every commit of a
package carries a trailer, `Work-package: WP2`. `git log --grep '^Work-package: WP2$'` lists the
commits of a package. #119's `agent:status` uses it.

**`scripts/agent/plan.py`** (new, standard library only, run through `py` like `agent_json.py`)
parses the plan:

```text
plan.py packages <plan>   one line per package, in implementation order: "<id>\t<status>\t<name>"
plan.py next <plan>       the first open package whose dependencies are all done, or nothing
plan.py check <plan> <id> problems with package <id> after its run, or nothing (see below)
plan.py remaining <plan>  the packages that are neither done nor "not done", or nothing
plan.py not-done <plan>   "- WP3: <reason>" per package marked "not done", for the PR body
```

- **Implementation order**: a topological order of the `Depends on` lines, keeping the order of the
  document among packages that are free at the same time. `Depends on` accepts `none`, a list
  (`WP1, WP1c`), a range in document order (`WP2–WP4` or `WP2-WP4`) and text in parentheses after
  the IDs, which is ignored. That is everything the existing plans use, for example #112's
  `WP1b, WP2–WP4 (the rules must pass on the migrated code)`. An unknown ID or a cycle is an error
  that names the line.
- **`next`**: a package that depends on a package marked `not done` is still attempted. The agent
  then knows from the plan what is missing and says what it did instead. The flow does not stop on
  a single package.
- **`check <id>`** reports a problem when the status is still `open`, when the status is `done` and
  a step is unticked, or when the status line is malformed.

### `implement.sh`: one agent run per package

```text
implement.sh [--dry-run] plan/<issue>-<slug>
```

1. Check out the branch. `checkout_branch` gets a `--keep-changes` mode: when the current branch is
   already the plan branch and the working tree is dirty, it keeps the changes instead of stopping
   (fetch and `merge --ff-only` as today, which fails safely if they would be overwritten). Every
   other script keeps the strict check.
2. Take the lock and set up the state directory (see below).
3. Loop: `package=$(plan.py next "$plan")`. When there is none left, go to step 6.
   - Write `package=<id>` to `$AGENT_STATE/current` and log `WP2 (2 of 6): started`.
   - Build the prompt: `prompts/implement.md`, then the package section (see Prompts), then the
     plan. The resume note goes in front when the working tree was dirty at the start of this
     package.
   - `run_agent` with a fresh session per package, results in `$AGENT_STATE/wp-<id>.json`. A fresh
     session keeps each run's context to one package and works the same whether or not the earlier
     packages ran on this machine. The session ID is in the result, for the retry in step 4 and for
     debugging.
4. After the run: `plan.py check <plan> <id>`, and the working tree must be clean. A package marked
   `done` or `not done` with a clean tree is finished. Otherwise resume the same session once with
   the problems (as the final checks do today). If they remain, stop with the problems. The work
   stays on the branch for the next run.
5. Push the branch (`push_branch`), log `WP2 (2 of 6): done (3 commits)` or
   `WP2 (2 of 6): not done: <reason>`, and continue the loop.
   - Pushing after every package means the work survives the machine. It starts CI on the plan
     branch. CI's concurrency group (`cancel-in-progress` on non-`main` refs) cancels the run of
     the previous push, so at most one run is going at a time.
6. The final checks, as today: clean tree, at least one commit since the plan commit,
   `scripts/version.sh check-bump`, `mise run check`, and new: `plan.py remaining` is empty. Up to
   two retries, each resuming the session of the last package run with the problems.
7. Push, open the draft pull request, post the `implement` step comment as today:
   - **Summary**: one `### WP<id>: <name>` sub-section per package, from that package's structured
     output (`wp-<id>.json`). A package implemented in an earlier run whose result file is missing
     (another machine, a deleted state directory) says "Implemented in an earlier run, see its
     commits".
   - **Not done or done differently**: `plan.py not-done`, followed by each package's `not_done`
     text.
   - **Usage** in the step marker: every `wp-*.json` and retry result in the state directory, so an
     interrupted and resumed run counts what it used in total.
8. Clear `current`. The state directory stays for the review → fix loop.

The version bump moves into the last package's run: the package prompt says when it is the last
open package, and that run bumps the version unless `check-bump` already passes. The final checks
catch a missing bump as today.

`--dry-run` prints the packages with their status (`plan.py packages`), whether the working tree
holds uncommitted work and for which package (`current`), whether a run is active (the lock), and
what the next run would do. It changes nothing. The `develop-issue` skill and the developer use
it.

### Resume after an interruption

`implement.sh` (and `run.sh`, which calls it while the branch has no pull request) started again:

- **Packages already `done` or `not done` in the plan on the branch**: skipped, because `next`
  starts after them. This works from the pushed branch alone, without the state directory.
- **Uncommitted changes on the plan branch**: kept (`--keep-changes`). The prompt of the first
  package run starts with a resume note: the working tree holds uncommitted work from an
  interrupted run, which was on package `<current>`, or on an unknown package when `current` is
  missing. The agent is told to:
  - read `git status` and `git diff`;
  - keep what is right and finish it;
  - commit it with the trailer of the package it belongs to. When the uncommitted work covers
    several open packages (an interrupted run of the old single-session flow), finish and commit
    them one after the other in dependency order, each with its status and trailer;
  - never discard work it did not check (`git restore` only on files it has read).

  After that run the loop continues with `next`, which skips whatever the resumed run finished.
- **Committed but not pushed** (interrupted between commit and push): nothing special. The next
  push takes the commits along.

The issue proposed committing the uncommitted work as `WIP: …` first. This plan resumes on the
dirty tree instead. A WIP commit has to pass the pre-commit hook, and half-done code often does not
(Spotless, Checkstyle, ESLint). The scripts and agents never skip the hook (`--no-verify` is denied).
Continuing on the dirty tree loses nothing, and the work still ends in reviewed commits, each named
by its package. See Open questions.

### State directory per plan branch

`lib.sh` gets `use_state <branch>`. Every script calls it once it knows its branch: `implement.sh`
from its argument, and `next.sh`, `review.sh`, `fix.sh`, `fix-ci.sh` and `finalise.sh` from the pull
request's head.

- `AGENT_STATE=$(git rev-parse --git-common-dir)/agent/<issue>-<slug>`, for example
  `.git/agent/112-spring-data-jpa/`. It is inside `.git`, so it is never committed and never
  touched by `git clean`. In a linked worktree it is the main repository's `.git`, so every worktree
  finds the same state.
- `AGENT_TMP` is set to it, so every prompt, result (`*.json`) and stream (`*.jsonl`) that the
  scripts write today goes there instead of `$TMPDIR/agent-<pid>/`, without changing their names.
  Results that would overwrite each other across rounds get the round in their name
  (`review-<round>.json`, `fix-<round>-<finding>.json`, `fix-ci-<attempt>.json`). The implement
  results are `wp-<id>.json` and `implement-retry-<n>.json`.
- **`run.log`**: the outermost script copies its own stderr into `$AGENT_STATE/run.log`
  (`exec 2> >(tee -a …)`, appended, one header line per start with the date, the script and its
  arguments). It holds the `agent:` log lines and the agents' progress lines from
  `agent_json.py stream`. Nested scripts see `AGENT_LOGGING=1` (exported) and do not copy it again.
- **`run.pid`, the lock**: `run.sh`, `implement.sh` and `next.sh` take it when they start, unless
  `AGENT_LOCKED` (exported) says an outer script holds it. A `run.pid` naming a live process stops
  the script ("a run is already active: pid <n>, log <path>"). A stale one is replaced. It is
  removed on exit (`trap`).
- **`current`**: the package the implementation is on (see above).
- The scripts print the state directory and `run.log` when they start. "Full log:" lines point
  there.

### Usage of interrupted runs

Today an interrupted agent leaves no usage behind. `agent_json.py stream` writes the result file,
which holds the usage and the cost, only when the final `result` event arrives. A run killed before
that has a stream (`.jsonl`) but no result, so its tokens are missing from the step comment.

- `agent_json.py usage`, behind `usage-line` and `usage-marker`, takes stream files as well as
  result files. For a stream without a `result` event it sums the `message.usage` of the
  assistant messages, the subagents' messages included (`parent_tool_use_id`). A message can arrive
  as several events, one per content block, each repeating the same usage, so it counts each
  message ID once. There is no cost in the stream, so such a stream adds tokens only. Its count is
  marked partial.
- The marker gets `partial=1` when any stream was counted without its result. The usage table
  (`usage-table`) then shows the cost as "at least $x.xx (an interrupted run's cost is unknown)".
  `tokens_used`, and so `AGENT_MAX_TOKENS`, counts the partial tokens too.
- `implement.sh` passes every `wp-*.json` and its stream to the usage. A stream counts only when its
  result is missing, so nothing is counted twice. Interrupted runs of the earlier packages, and of
  the package that was resumed, are therefore included. The other steps pass their own streams the
  same way.
- Streams of runs from before this change, in `$TMPDIR/agent-<pid>/`, are not searched. A step
  whose state directory is gone (another machine) reports what it has and says "usage of earlier
  runs not available" instead of nothing.
- The `develop-issue` skill removes the directory after the merge (step 7), together with the local
  branch.

### Detached runs

```text
scripts/agent/detach.sh <script> <args…>      (mise run agent:detach <script> <args…>)
```

`detach.sh` resolves the plan branch from the arguments (a `plan/…` branch, or a pull request
number through `gh pr view`). It starts `scripts/agent/<script>` in a new session (`os.setsid()`
through `py`, since macOS has no `setsid` command) with stdin from `/dev/null` and stdout and stderr
appended to `$AGENT_STATE/run.log`. It then prints the process ID and the log path and returns. The
run keeps going when the terminal or the Claude Code session that started it closes. The started
script takes the lock as usual, so a second start is refused.

Follow it with `tail -f <log>` or, in the skill, the Monitor tool (see below). Stop it with
`kill <pid>`. The script's `trap` removes the lock, and the next start resumes.

### The `develop-issue` skill

- **Step 0** (where the issue stands): for a plan branch without a pull request, run
  `scripts/agent/implement.sh --dry-run <branch>` and print its package list.
  - When the working tree is dirty **on that plan branch**: say which files and which package
    (`current`). Start at step 3 with the options **Resume (keep the uncommitted work)**, **Stop
    here**. The skill never discards or stashes the changes. Any other dirty tree still stops, as
    today.
  - When a run is active (lock held): say so, with the log path, and offer to follow it (Monitor)
    instead of starting another one.
  - Start at step 3 when the branch has packages done but no pull request, and say "resuming at WP3
    (2 of 6 done)".
- **Step 3** (implement): the gate shows the package list and which package the run starts with.
  The command is `scripts/agent/detach.sh implement.sh <branch>`. The skill then follows `run.log`
  with the Monitor tool until the process in `run.pid` has exited, reporting each
  `WP<id> (<n> of <total>): …` line as it appears. That replaces "start it in the background, do not
  poll". (#119 adds the remaining milestone lines and the filtering.) At the end it reports as
  today, with the package list.
- **Steps 4 and 6** use the same pattern for `next.sh` (**Run the rest without asking**) and for the
  per-step scripts. Short steps may keep `run_in_background`.
- **Step 7**: after the merge, also `rm -rf "$(git rev-parse --git-common-dir)/agent/<issue>-<slug>"`.
- **Step 8** (summary): the package list for each plan that is not merged.

### The planning skill and the template

- `plan-template.md`: `- **Status**: open` under every work package heading. The work package
  section says that the developer agent ticks the steps and sets the status, and that the scripts
  read the headings, `Status` and `Depends on`, so their format is fixed.
- `plan-from-issue/SKILL.md`, step 3 "Work packages": the same, and that `Depends on` uses package
  IDs (`none`, `WP1, WP2`, `WP2–WP4`) followed by an optional reason in parentheses.

### Prompts

- `prompts/implement.md` becomes the prompt for one package:
  - implement **package `<id>` only**. The plan is given whole for context. Earlier packages are
    done, and later ones are not yours;
  - commit in a few logical commits, each with the trailer `Work-package: <id>` (a last line of its
    own, after a blank line);
  - with the last commit of the package: tick its steps (`- [x]`) and set `- **Status**: done`, or
    `not done: <one-line reason>` when it cannot be done, in the plan file, committed with the code
    (the plan file is the one exception to "do not commit the plan file again");
  - the version bump only when the prompt says this is the last open package;
  - the final message: the structured output as today (`summary` and `not_done`), for this package.
- The package section that `implement.sh` puts in front of the plan:
  `## Your package: WP2 (2 of 6), settings (the first slice)`. It says whether this is the last open
  package, and it carries the resume note when there is one.
- `fix.md`, `fix-ci.md`, `review.md`: no change. The review agent sees ticked steps and status
  lines in the plan, which is the plan the developer approved plus progress. `review.md` gets one
  sentence saying so, so that it does not report the ticks as changes to the plan.

## Work packages

### WP1: Plan parsing (`plan.py`)

- **Status**: open
- **Depends on**: none
- **Files**: `scripts/agent/plan.py` (new), `scripts/agent/test_plan.py` (new), `mise.toml` (task
  `agent:test`, and the test in `check`), `.github/workflows/ci.yml` (the test in an existing job)
- **Steps**:
  - [ ] `plan.py` with the five commands as in Design, standard library only, a module docstring
        listing the commands like `agent_json.py`'s
  - [ ] `test_plan.py` (`unittest`, standard library) run with
        `uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'`
  - [ ] mise task `agent:test` running it; `check` runs it too (it takes about a second)
  - [ ] CI runs `mise run agent:test` as a step of the *ta-runner* job, which already has uv
- **Tests**: `test_plan.py`:
  - implementation order with dependencies, ranges with `–` and `-`, and parenthesised reasons. It
    includes the shape of #112's work packages (`WP1`, `WP1b`, `WP1c`, `WP2–WP4`);
  - `next` skips `done` and `not done`, waits for open dependencies, and attempts a package whose
    dependency is `not done`;
  - a plan without `Status` lines (all open), and a plan without work packages (`all`);
  - `check`: an open status, a `done` package with an unticked step, a malformed status;
  - `remaining` and `not-done`;
  - an unknown ID and a cycle fail with the line named.

### WP2: State directory, log and lock (`lib.sh` and the loop scripts)

- **Status**: open
- **Depends on**: none
- **Files**: `scripts/agent/lib.sh`, `scripts/agent/agent_json.py`,
  `scripts/agent/test_agent_json.py` (new), `scripts/agent/run.sh`, `scripts/agent/next.sh`,
  `scripts/agent/review.sh`, `scripts/agent/fix.sh`, `scripts/agent/fix-ci.sh`,
  `scripts/agent/finalise.sh`
- **Steps**:
  - [ ] `use_state <branch>`: `AGENT_STATE`, `AGENT_TMP` pointing at it, `run.log` copy for the
        outermost script, the start header line
  - [ ] `lock_run` / the `trap` that releases it, `AGENT_LOCKED` for nested scripts, a stale lock
        replaced
  - [ ] `checkout_branch --keep-changes`
  - [ ] Every script calls `use_state` once it knows its branch. `run.sh` and `next.sh` take the
        lock. Result names that would collide across rounds get the round or attempt in them
  - [ ] The header comments of the scripts and of `lib.sh` mention the state directory
  - [ ] `agent_json.py`: usage from streams without a result, counted once per message ID, `partial=1`
        in the marker, "at least" cost in the usage table; every script passes its streams
- **Tests**: `scripts/agent/test_agent_json.py` (new, run by `agent:test`, see WP1): usage of a
  complete run equals its result's; a stream cut off before `result` gives the summed tokens of its
  messages, each message ID once, subagents included, and `partial=1`; a result and its stream
  together are not counted twice; the usage table with a partial row says "at least".
  No test harness exists for the Bash scripts. These are checked by hand and described in the pull
  request: `next.sh --dry-run` on an open agent pull request writes to
  `.git/agent/<name>/run.log`; a second `next.sh` on the same branch while one runs is refused;
  killing it leaves no `run.pid`.

### WP3: `implement.sh` package by package, with resume

- **Status**: open
- **Depends on**: WP1, WP2
- **Files**: `scripts/agent/implement.sh`, `scripts/agent/prompts/implement.md`,
  `scripts/agent/prompts/review.md` (one sentence)
- **Steps**:
  - [ ] The loop as in Design (steps 1–8), with the log lines `WP<id> (<n> of <total>): …`
  - [ ] The package section and the resume note in the prompt
  - [ ] `prompts/implement.md` for one package, the trailer, the plan update and the version bump
        rule
  - [ ] The pull request body from the per-package results, and the usage over every result in the
        state directory
  - [ ] `--dry-run`
- **Tests**: by hand on a throwaway plan with two small packages (for example two documentation
  changes) on a branch from `AGENT_BASE_BRANCH=<a test branch>`, described in the pull request:
  1. a run interrupted (Ctrl+C) during WP2 after WP1 was pushed, started again: it skips WP1,
     resumes WP2 with the resume note, and opens one draft pull request;
  2. the plan on the branch shows both packages `done` with every step ticked;
  3. `git log --grep '^Work-package: WP1$'` lists WP1's commits;
  4. `--dry-run` before and after;
  5. the `implement` step comment's usage includes the interrupted WP2 run (marked partial).

  `plan.py`'s tests (WP1) cover the order and the checks.

### WP4: Detached runs (`detach.sh`)

- **Status**: open
- **Depends on**: WP2
- **Files**: `scripts/agent/detach.sh` (new), `mise.toml` (task `agent:detach`)
- **Steps**:
  - [ ] `detach.sh <script> <args…>`: resolve the branch, `use_state`, start in a new session with
        the output appended to `run.log`, print the process ID and the log
  - [ ] Refuse a script name outside `scripts/agent/` and a branch whose lock is held
- **Tests**: by hand, described in the pull request: `mise run agent:detach next.sh <pr>` keeps
  running after the terminal that started it is closed, and `kill <pid>` releases the lock.

### WP5: Skills, template and docs

- **Status**: open
- **Depends on**: WP3, WP4
- **Files**: `.claude/skills/develop-issue/SKILL.md`, `.claude/skills/plan-from-issue/SKILL.md`,
  `.claude/skills/plan-from-issue/plan-template.md`, `docs/agentic-development.md`, the three version
  files
- **Steps**:
  - [ ] `develop-issue`: steps 0, 3, 4, 6, 7 and 8 as in Design
  - [ ] Template and planning skill: the `Status` line and the fixed format of the package headings
        and `Depends on`
  - [ ] `docs/agentic-development.md` (see Docs to update)
  - [ ] `mise run version:bump minor`
- **Tests**: `/develop-issue` on the throwaway plan of WP3's test, interrupted after its first
  package: step 0 shows the package list and offers **Resume**. Step 3 follows the detached run
  with Monitor and reports each package. Described in the pull request.

## Tests

- **Automated**: `test_plan.py` (WP1) in `mise run check`, `mise run agent:test` and CI. It covers
  everything that decides which package runs next and whether a package is finished.
- **By hand** (the scripts drive Claude Code and GitHub, and there is no harness for them, like
  today): the interrupted-and-resumed run of WP3, the lock of WP2, the detached run of WP4 and the
  skill of WP5, each on a throwaway plan against a test base branch (`AGENT_BASE_BRANCH`). The pull
  request description lists what was run and what it showed.
- **Done when** (issue): interrupting `implement.sh` after the first package and running it again
  does not redo that package and ends in the same draft pull request (WP3 test 1). The plan on the
  branch shows every package done, or not done with its reason (WP3 test 2). The tokens of the
  interrupted run are in the usage, marked partial, instead of missing (WP3 test 5,
  `test_agent_json.py`).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md`, flow diagram and step table | Step 5: the developer agent implements one work package per run and pushes after each one. The diagram's implement node says so. |
| Text | `docs/agentic-development.md`, "Decision" | New bullets. **Progress is in the plan**: status and ticks per package, the `Work-package` trailer, and why there is no SHA in the plan. **One run per package**: smaller context, survives an interruption. **The state directory**: what is in it, why inside `.git`. "The pull request is the state" now covers the review → fix loop, and the plan branch covers the implementation. |
| Text | `docs/agentic-development.md`, walkthrough step 4 | The log and streams are in `.git/agent/<issue>-<slug>/` (`run.log`), not `$TMPDIR/agent-<pid>/`. `implement.sh --dry-run`. Starting detached with `mise run agent:detach run.sh plan/…`, following it with `tail -f`, and stopping it. |
| Text | `docs/agentic-development.md`, walkthrough step 5 "When a run stops early" | An interrupted implementation: start it again; done packages are skipped and the uncommitted work of the interrupted package is continued, never discarded. A run already active is refused. |
| Text | `docs/agentic-development.md`, walkthrough step 2 and 3 | The plan's `Status` lines and their fixed format. Reviewing a plan: leave every status `open`. |
| Text | `docs/agentic-development.md`, "Step by step" | The skill offers to resume a half-done implementation and follows detached runs. |
| Text | `docs/agentic-development.md`, "Not done yet" | Parallel work packages (#70) now build on the per-package loop. |
| Text | `.claude/skills/develop-issue/SKILL.md`, `.claude/skills/plan-from-issue/SKILL.md`, `plan-template.md` | As in Design. |
| Text | Root `README.md` | None: it does not describe the agent flow. It links to nothing that changes. |
| Screenshot | none | No page changes. |

## Out of scope

- Live progress on the pull request (early draft, checklist body, one status comment),
  `agent:status`, milestone filtering in the skill, notifications, hooks that enforce the ticks, a
  diff-only second review, and "run without asking" at the first gate: #119.
- Parallel work packages in their own worktrees: #70. The per-package loop and `plan.py next` are
  what it will build on.
- Running the loop in GitHub Actions: #67.
- Changing plans already on branches, for example adding `Status` lines to #112's plan. They work
  without them.

## Open questions

- Settled in this plan, confirm before approving:
  1. **Resume on the dirty tree instead of a `WIP:` commit** (the issue's suggestion). The hook
     would often reject half-done code, and the flow never skips it. Continuing on the dirty tree
     loses nothing and still ends in commits per package.
  2. **A `Work-package:` trailer instead of `Done in <sha>` in the plan**: a commit cannot carry its
     own SHA.
  3. **A fresh agent session per package**, not one session resumed across packages. The context
     per run stays small and every run works the same with or without earlier runs on this
     machine. The cost is that each package's agent reads the conventions again.
  4. **A Python test for `plan.py` in `mise run check` and in CI's ta-runner job**: the first test of
     the agent scripts. Kept in `scripts/agent/` next to the module, without a new tool. The
     alternative is no automated test, as for the rest of the scripts.
  5. **The state directory inside `.git/`** (`.git/agent/<issue>-<slug>/`) rather than a folder in
     the working tree with a `.gitignore` entry. It survives `git clean`, is shared by worktrees,
     and cannot be committed by accident.
