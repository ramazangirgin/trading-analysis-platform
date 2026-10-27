# Plan: Agent loop retries, step timeouts and a failed line for every exit

- **Issue**: #242 (Agent loop: retries with backoff, timeouts for every step, and a failed line for every exit)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A network error on a `git push`, `git fetch` or `gh` call no longer ends an agent run: it is
retried with a growing delay, and each retry is a line in `run.log`. No step can hang: every agent
run, CI wait and network call has a time limit, and running over it stops the run with a
`stopping:` line. Every run that ends on an error, whatever the cause, writes
`>> failed: <step>: <reason>`, comments it on the pull request and sends the notification, so the
Monitor and ntfy always fire. A fix round resumed after a restart records the findings it already
committed as `fixed`, and `plan-branch.sh` refuses a plan the scripts cannot read before the
developer approves it. (#241, the stop on a conflict with `main`, is merged already: `merge_state.py`
and the `update-main-conflict` step of `next.sh`.)

## Affected parts and their conventions

- Agent scripts (`scripts/agent/`): [agentic-development.md](../docs/agentic-development.md), the
  existing scripts' style (Bash 3.2 compatible, standard-library Python run through `py`, unittest
  tests `test_*.py` run by `mise run agent:test`);
  [code comments](../docs/coding-convention/repository-code-comments.md).

## Design

- **One place for every network call.** `lib.sh` defines a `gh` function and a `git` function that
  shadow the commands for every script sourcing it (`command gh` / `command git` inside). `gh` always
  goes through `retry`; `git` only for the subcommands that reach the remote (`fetch`, `push`,
  `ls-remote`, `pull`, found after leading `-C <dir>` / `-c <k=v>` options), every other subcommand
  is passed straight through. So `run.sh`, `implement.sh`, `review.sh`, `finalise.sh`, `fix-ci.sh`,
  `update-main.sh` and the CI watch of `next.sh` (whose polls are `gh` calls) get the retries without
  changing their call sites. No call site reads `gh`'s stdin today; `retry` passes stdin through and
  does not replay it.
- **`retry <what> <command…>`** runs each attempt under the call timeout, with stderr into a file
  that is printed to stderr after the attempt. A failure is transient when the attempt timed out or
  its stderr matches one pattern list in `lib.sh` (connection reset or refused, timed out, could not
  resolve host, `RPC failed`, early EOF, the remote end hung up, HTTP 5xx / `502`-`504`, rate limit,
  TLS handshake). Anything else (a rejected push, auth, a missing ref, `ls-remote --exit-code` not
  finding a branch, `gh label create` on an existing label) returns at once with its status.
  Settings: `AGENT_RETRIES` (retries after the first attempt, default 3), `AGENT_RETRY_DELAY`
  (seconds before the first retry, default 10, doubled for each next one: 10, 20, 40 s),
  `AGENT_CALL_TIMEOUT` (seconds per attempt, default 120). These defaults are decided. Each retry writes `agent: >> retrying <what> (<k> of <n>): <first
  error line>` into the log, but not through `milestone`: a retry line is not published or sent to
  ntfy, otherwise an outage would fan out into more `gh` calls from `progress_publish`.
- **Timeouts: `scripts/agent/timeout.py <seconds> <command…>`** (new, standard library): runs the
  command in its own process group, sends `TERM` to the group at the deadline and `KILL` 10 seconds
  later, exits 124; forwards `INT`, `TERM` and `HUP` to the group (Ctrl+C and `kill -- -<pid>` of a
  detached run reach the child, which is in another group) and exits 130 / 143; otherwise exits
  with the command's status. macOS has no `timeout` command, and killing the group also ends
  `git-remote-https` and the agent's own subprocesses.
- **Agent step timeout**: `run_agent` runs `claude` under `timeout.py` with `AGENT_STEP_TIMEOUT`
  minutes (default 60) and reads the pipeline's statuses itself instead of `|| true`. The limit is
  per agent run (one work package run, one finding, one review), not per script, as decided: an
  `implement` of several packages legitimately runs for hours and has no overall limit. On 124 it calls `stop_run "<AGENT_STEP_LABEL or
  AGENT_STEP> ran longer than <n> min"` and exits with `AGENT_EXIT_STOPPED`. The state needed to
  resume is already where it is today (uncommitted work and the plan for `implement`, the step
  comments for the others) and the partial stream counts as an interrupted run's usage.
- **`stop_run <reason>`** moves into `lib.sh` from `next.sh`'s `stop`: the `stopping:` milestone,
  the "Agent loop stopped" step comment on the branch's open pull request, and the `agent` label
  removed when the pull request is in the agent stage. A step timeout is such a stop, as decided:
  it removes the label like the CI timeout, and the developer adds it again to resume (a `failed:`
  exit keeps it). `next.sh`'s `stop` calls it and exits 0 as
  today. The CI timeout of `next.sh` keeps `AGENT_CI_TIMEOUT` and says `CI <sha> ran longer than
  <n> min`.
- **Every exit is announced.** `lib.sh` sets `set -E` and an `ERR` trap that records, once per
  process, the failing command, its status and `<script>:<line>`. `agent_exit` (the `EXIT` trap),
  for a status other than 0, 130, 143 and the two codes below, writes the milestone
  `failed: <step>: <command> exited <status> (<script>:<line>)`, `<step>` being `AGENT_STEP` or the
  script's name; the command's own error is the log line above it. `die` writes the same form
  (`failed: <step>: <message>`). A process that wrote its failed line exits with
  `AGENT_EXIT_FAILED` (86), a stop with `AGENT_EXIT_STOPPED` (87): the parent script (`run.sh` →
  `next.sh` → `fix.sh`) fails on that status and does not write a second line. The failed line is
  also written to `$AGENT_STATE/failed.txt`; the outermost script (the one `use_state` started
  `run.log` for) posts it on the branch's open pull request when it exits failed, with
  `post_step <pr> stop 0` ("Agent loop failed", the line, "the `agent` label stays: run
  `mise run agent:run <branch>` again to resume"), so the dashboard shows it stopped and a nested
  failure is commented once. The label is kept because a transient cause needs only a rerun.
  Nothing is posted in a dry run (`AGENT_NO_PROGRESS`), and a failure of the comment itself is a
  log line. ntfy fires through the failed milestone, as for `die` today.
- **Helpers are not steps.** `progress.sh` and `dashboard.sh`, when started by `progress_publish`
  or `agent_exit`, run with `AGENT_NO_ANNOUNCE=1`: their failures stay the existing log line ("the
  progress comment on the issue was not updated"), never a `failed:` line the Monitor would take
  for the end of the run. `plan-branch.sh` replaces lib's `EXIT` trap with its worktree removal
  today; it calls `agent_exit` from its own trap instead.
- **Fix round resumed** (`fix.sh`): before running the agent for a finding, it looks for a commit
  `Address <id>:` in `origin/<base>..HEAD`. Found, the finding is `fixed` with that commit, no agent
  runs and no reply is posted again (the interrupted run replied right after committing). The
  round's commit count and the push use the round's `Address R<round>-` commits and whether `HEAD`
  is ahead of `origin/<branch>`, not `start..HEAD`, which is empty after a restart.
- **Plan check before approval**: `plan-branch.sh` runs `plan.py packages` on the plan before the
  oversized warning and before anything is pushed, and dies with its error on failure. `plan.py`'s
  "cannot read" error names the accepted forms (`none`, `WP1, WP2`, `WP2-WP4`), so the message has
  the line and the format.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Process-group timeout

- **Status**: done
- **Depends on**: none
- **Files**: `scripts/agent/timeout.py`, `scripts/agent/test_timeout.py`
- **Steps**:
  - [x] `timeout.py` as in Design
- **Tests**: `test_timeout.py`: the command's exit status is passed through; a command over the
  limit ends with 124 and a child it started (a `sleep` in the background) is gone too; `TERM` sent
  to `timeout.py` ends the command and exits 143.

### WP2: Retries, call timeout, announced exits and stops in lib.sh

- **Status**: done
- **Depends on**: WP1
- **Files**: `scripts/agent/lib.sh`, `scripts/agent/test_lib.py`
- **Steps**:
  - [x] `retry`, the `gh` and `git` functions, the transient pattern list and the new settings
        (with the header comment of `lib.sh`)
  - [x] `AGENT_STEP_TIMEOUT` in `run_agent`, `stop_run`, the exit codes
  - [x] `set -E`, the `ERR` trap, `die`'s new form, `agent_exit` announcing and the outermost
        script's failure comment, `AGENT_NO_ANNOUNCE` for `progress_publish` and the dashboard update
- **Tests**: `test_lib.py` runs Bash snippets that source `lib.sh` with `AGENT_SCRIPTS` and
  `AGENT_REPO_ROOT` set (no re-exec), `AGENT_RETRY_DELAY=0` and a fake `gh` / `git` on `PATH` that
  fails a given number of times with a given stderr and counts its calls. They prove: a transient
  error is retried, logs `>> retrying`, and then succeeds; a permanent one (`! [rejected]`, HTTP 404)
  is called once and keeps its status; `AGENT_RETRIES` limits the calls; an attempt over
  `AGENT_CALL_TIMEOUT` counts as transient; `git status` is not wrapped; a failing command ends the
  script with one `>> failed: <script>: <command> exited …` line and status 86; a child exiting 86
  makes the parent exit 86 without a second line; with `AGENT_NO_ANNOUNCE=1` no `failed:` line is
  written.

### WP3: Plan check in plan-branch.sh

- **Status**: open
- **Depends on**: WP2
- **Files**: `scripts/agent/plan-branch.sh`, `scripts/agent/plan.py`, `scripts/agent/test_plan.py`
- **Steps**:
  - [ ] The check and the `EXIT` trap that calls `agent_exit`
- **Tests**: `test_plan.py`: `test_unreadable_depends_names_the_line` also asserts the accepted
  forms in the message.

### WP4: Stops in next.sh and the resumed fix round

- **Status**: open
- **Depends on**: WP2
- **Files**: `scripts/agent/next.sh`, `scripts/agent/fix.sh`
- **Steps**:
  - [ ] `next.sh`'s `stop` on `stop_run`, the CI timeout message
  - [ ] `fix.sh`'s lookup of an existing `Address <id>:` commit, the round's commit count and push
- **Tests**: none new: `stop_run` and the exit codes are tested in WP2; the resumed fix round, see
  "Tests".

## Tests

Each work package's tests, and `mise run check`. The resumed fix round has no unit test (it needs a
pull request): the review agent checks `fix.sh` against the Design, and the next real fix round
that is restarted proves it.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md` "5. When a run stops early" | Transient errors are retried (the `retrying` line); a step over its limit stops (`stopping: <step> ran longer than …`), resumed like the other stops; every other error is a `failed: <step>: <reason>` line and a pull request comment with the label kept, resumed with `mise run agent:run` or `/develop-issue <n>` |
| Text | `docs/agentic-development.md` "Settings" | `AGENT_RETRIES`, `AGENT_RETRY_DELAY`, `AGENT_CALL_TIMEOUT`, `AGENT_STEP_TIMEOUT`; `AGENT_CI_TIMEOUT` stays |
| Text | `docs/agentic-development.md` "3. Review the plan" | `plan-branch.sh` refuses a plan whose work packages it cannot read |
| Text (by hand) | `.claude/skills/develop-issue/SKILL.md` | One line: a `failed:` line from a transient cause is resumed with `/develop-issue <n>`. Headless agents cannot edit `.claude/`: the developer adds it on this branch before the merge |

No screenshot: nothing in the web UI changes.

## Out of scope

- Retrying the agent runs themselves (a usage limit, an API error inside Claude Code): they stay a
  `failed:` line and are resumed by running the loop again.
- Retries in `plan-comments.sh` and `status.sh` beyond what the `gh` function gives them for free.

## Open questions

None.
