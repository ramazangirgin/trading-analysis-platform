# Plan: Agent Dashboard issue with a step graph per active process

- **Issue**: #258 (Agent Dashboard: one issue showing the active agent processes as step graphs)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

One open GitHub issue, "Agent Dashboard", shows every active agent process of the repository at a
glance: for each plan branch whose pull request is open and labelled `plan` or `agent`, or that has
a live run, a Mermaid step graph of the flow (plan → approval → work packages → checks → CI →
review / fix rounds → update with `main` → finalise → ready for review) coloured by state, a line
of facts (stage, current step, work package, round, times, tokens, cost, who is waited for) and a
table of the links to open. The agent scripts keep it up to date at every milestone, from the same
place that updates the progress comment on the issue, and once more when a run ends, so several runs
side by side (`/develop-issue`, worktrees) share one place to look. `mise run agent:dashboard`
prints it, `mise run agent:dashboard --post` rebuilds it, `AGENT_DASHBOARD=0` turns it off.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`) | The scripts' own pattern (docs/agentic-development.md, "Decision"): Bash for the GitHub calls and the state directory, sourcing `lib.sh` (Bash 3.2 compatible, `set -euo pipefail`, the script copy in `AGENT_SCRIPTS`); the logic in a standard-library-only Python module run with `py` (`uv run --no-project python`), with a `test_<module>.py` beside it (`unittest`, run by `mise run agent:test`, part of `mise run check` and CI's ta-runner job). Settings are `AGENT_*` variables with a default in `lib.sh` and a line in its header and in the docs' settings table. A GitHub call made from a milestone never fails a step (`progress_publish`). |
| mise tasks (`mise.toml`) | One `agent:<name>` task per script entry, with a description that shows its usage, like `agent:progress`; a new task gets a line in `docs/development.md` |
| GitHub setup (`scripts/github-setup.sh`) | The labels the scripts use are created there (`label <name> <colour> <description>`) |
| Docs | `docs/agentic-development.md` (the flow's topic file), `docs/development.md` (task list); [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md): no comment that restates the code; plain style of the existing documents; every relative link resolves (`doc_links.py`, part of `agent:test`) |

## Design

### What is read, and nothing more

The dashboard only reads what the scripts already record (the issue's "Out of scope"): per plan
branch `.git/agent/<issue>-<slug>/progress.tsv` (parsed with `progress.parse` / `progress.build`),
`run.pid` (alive or not, `active_run`), `current` and `progress.comment` (the progress comment's ID);
the plan's work packages (`plan.parse` and the implementation order of `plan.py`, from the plan file
on the pushed branch); the pull request's state, draft flag, labels and head; the `agent-step`
markers of the agent account's comments (`agent_json.MARKER`, with the comment URLs); and the CI
runs of the branch. No script records anything new for it.

### Which processes are listed

`dashboard.py` decides, from one JSON record per plan branch (built by `dashboard.sh`, below):

- **active**: the pull request is open and labelled `plan` or `agent`, or `run.pid` names a live
  process. Also active: an open **draft** pull request whose newest `agent-step` marker is `stop`
  (the loop gave up and removed the `agent` label): it waits for the developer, and is the
  "stopped" case the issue's "done when" asks to show (see Open questions).
- **not active**: merged, closed, or ready for review (not a draft) with no live run. Its section is
  removed on the next update. With no active process the body says "No active agent processes".

### Stage, state of a step, who is waited for

- **Stage** (the facts line): `plan review` (label `plan`), `agent loop` (label `agent`, or a live
  run), `stopped` (no live run while a step is still open in `progress.tsv`, or the newest marker is
  `stop`), `ready for review` (only while a run is still finishing).
- **State of a node**, one Mermaid `classDef` each: `done` (green), `running` (blue: an open step of
  a live run; its label shows the minutes, `CI fix 1<br/>12 min`), `waiting` (yellow: an open `CI …`
  step of a live run, or the plan under review / waiting for approval), `failed` (red: outcome
  `failed`, or a `CI …` step whose result starts with `failed`), `stopped` (orange, dashed: outcome
  `interrupted`, an open step without a live run, or the step a `stop` marker ended), `todo` (grey:
  the next expected step and the work packages not begun).
- **Waited for**: `CI` while a `CI …` step is open, `developer` in plan review or when stopped,
  `nobody` while an agent step runs.

### The graph

`dashboard.py` turns the steps into a list of nodes, in the order they ran, `flowchart LR`, one
edge from each node to the next:

- `plan` (from the `plan` step, or `todo`/`waiting` while the plan is under review) → `plan approved`
  (done when the `plan` step ended, or the pull request left the `plan` label).
- `implement`: one node per work package of the plan, in the implementation order (`plan.py`), each
  from its sub-step (`WP2 (2 of 4)`) or `todo` when not begun yet; a package marked `not done` in the
  plan is `done` with the text "not done"; then `checks` (the `final checks` sub-step).
- Every further top-level step of `progress.tsv` in order, one node each: `CI <sha7>`, `CI fix <n>`,
  `review R<n>`, `fix R<n>`, `update with main`, `finalise`; then `ready for review` when `finalise`
  ended well. A retried step (a second CI fix, a second update with `main`) is a node of its own, so
  a loop shows as repeated nodes. `orchestration` is not drawn.
- **The next expected step**, one `todo` node after the last one: after `implement`, a `CI fix` or a
  `fix` → `CI`; a red `CI` → `CI fix <n+1>` (until `AGENT_MAX_CI_FIXES`); a green `CI` → `fix R<n>`
  when review R<n> had findings and no fix yet, `review R<n+1>` while rounds are left
  (`AGENT_MAX_ROUNDS`) and the last review did not approve, else `finalise`; `update with main` →
  `CI`; `finalise` → `ready for review`. No next node when stopped or ready.
- **Without a local `progress.tsv`** (a rebuild for a run of another machine, a plan only pushed):
  the nodes come from the plan's packages and the `agent-step` markers (`implement`, `ci-fix`,
  `review`, `fix`, `update-main`, `finalise`, `stop`), plus one `CI <sha7>` node for the head from
  the CI run's status.
- Node IDs are generated (`n1`, `n2`, …); labels are escaped for Mermaid (quotes, `#`, `<`, `>` as
  `#quot;`, `#35;`, `#lt;`, `#gt;`, as in docs/agentic-development.md's flow).
- **Links**: a `click n<k> href "<url>" _blank` line per node that has one: `plan` → the plan file
  on the plan branch; `plan approved` → the pull request; a work package → its heading in the plan
  file (`#wp2-…`, the GitHub slug of `WP2: <name>`); `checks` → the pull request's files tab; `CI
  <sha7>` → the CI run of that commit; `CI fix <n>`, `review R<n>`, `fix R<n>`, `update with main`,
  `finalise` → the agent account's comment with that marker (`step=ci-fix round=<n>`, …);
  `ready for review` → the pull request.

### The section of a process

~~~text
<!-- agent-process plan/<issue>-<slug> -->
### #<issue> <issue title>: `plan/<issue>-<slug>`
**Stage**: agent loop · **Step**: review R2 · **Work package**: 4 of 4 · **Round**: 2 of 2 ·
**Started**: … · **Last update**: … · **Elapsed**: … · **Tokens**: … · **Cost**: … · **Waiting for**: nobody
```mermaid
flowchart LR
  …
```
| Link | |
|---|---|
| Issue · Plan · Pull request · Files · Progress comment · Review R1, R2 · Final comment · CI (head) · Branch | the URLs (a row without a URL is left out) |
<!-- /agent-process -->
~~~

The times, tokens and cost use `progress.py`'s helpers (`duration`, `tokens`, `money`, `clock`) and
the same totals as the progress comment: the totals computed inside `progress.render` move into a
function `progress.totals(steps, events, now)` that both use (no change to the comment's text). The
progress comment's link comes from `progress.comment`; without it (rebuild on another machine), the
link is left out.

### The body and the merge of sections

The body starts with `<!-- agent-dashboard -->`, a title line and "Updated <time>", then the
sections, ordered by issue number. `dashboard.py merge` takes the old body, the new sections of the
branches this machine rendered, and the set of active branches, and returns the new body: a section
of a rendered branch is replaced (or added), a section of a branch that is not active is removed
(merged or closed, ready for review, no run), every other section (another machine's run) is kept
as it is. A body that is not a dashboard yet (no marker) becomes one. A body over 60 000 characters
drops the link tables first, then reports it in one line (an issue body holds at most 65 536).

### `dashboard.sh`: the GitHub side

`scripts/agent/dashboard.sh`, sourcing `lib.sh`:

- `dashboard.sh` (no argument, `mise run agent:dashboard`): rebuild and print the body, post nothing.
- `dashboard.sh --post` (`mise run agent:dashboard --post`): rebuild and post. Rebuild = every open
  pull request with a `plan/*` head (one `gh pr list --state open --json …` call) plus every local
  state directory under `.git/agent/` with a live `run.pid`; each gets a section, from the local
  state when there is one, else from the pull request alone (only when the body has no section of it
  yet: another machine's section is kept).
- `dashboard.sh update [--async | --wait] <branch>` (the hook): render and post only this branch's
  section, and drop the sections of branches whose pull request is no longer active.
- **Gathering** a record (JSON, one file in `$AGENT_TMP`): the pull request (`gh pr list --head
  <branch> --state all --limit 1 --json number,url,state,isDraft,labels,headRefOid,title`), the
  issue title, the plan file of the branch (`gh api repos/{owner}/{repo}/contents/<plan>?ref=<branch>`
  raw, so no fetch is needed in a running worktree), the agent account's comments with their markers
  and URLs (`pr_step_comments`-like, keeping `html_url`), the CI runs of the branch (`gh run list
  --branch <branch> --workflow ci.yml --json headSha,url,status,conclusion --limit 20`), the state
  directory's `progress.tsv` path, `active_run`, `progress.comment`. `dashboard.py section
  <record.json> --max-rounds … --max-ci-fixes …` renders it.
- **The issue**: open, labelled `agent-dashboard`, created by the agent account; its number is
  cached in `.git/agent/dashboard.issue` (shared by the worktrees). Missing: the label is created
  (`gh label create --force`, as `github-setup.sh` does), then `gh issue create --title "Agent
  Dashboard" --label agent-dashboard`, then `gh issue pin` (a failed pin is logged).
- **One writer per machine**: `.git/agent/dashboard.lock` (noclobber, the pid, a dead holder's lock
  replaced), with `--async` leaving a `dashboard.pending` flag that the holder serves again before it
  releases the lock, as `progress.sh post --async` does.
- **A write that loses a race** (another machine wrote in between): after the `PATCH` of the body,
  the body is read again; when it does not contain this machine's section as written, the update is
  done again from the new body, up to 3 times, then logged.
- `AGENT_DASHBOARD=0`: every command but the print-only one returns at once. Any failed `gh` call
  is logged (`log`) and the script exits 0 when run as the hook (`update`).

### The hooks

- `lib.sh`: `AGENT_DASHBOARD=${AGENT_DASHBOARD:-1}` with its header line; `progress_publish` runs
  when `AGENT_ISSUE_PROGRESS=1` or `AGENT_DASHBOARD=1`.
- `progress.sh post`: the progress comment as today (only with `AGENT_ISSUE_PROGRESS=1`), then
  `dashboard.sh update --async <branch>` (with `--wait` when `post` itself was not `--async`), when
  `AGENT_DASHBOARD=1`; its failure is logged and ignored. That one place covers the step scripts'
  milestones and the session's own `progress.sh begin|end|note|orchestration` (develop-issue).
- `agent_exit` in `lib.sh`: after the lock (`run.pid`) is released by the run that held it, one more
  `dashboard.sh update --wait <branch>` (when `AGENT_BRANCH` is set and `AGENT_DASHBOARD=1`), so an
  ended, failed or interrupted run does not stay "running". A run killed with `kill -9` leaves a
  dead `run.pid`: the next update by any script (or `--post`) shows it stopped.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: The step graph of a process

- **Status**: done
- **Depends on**: none
- **Files**: `scripts/agent/dashboard.py`, `scripts/agent/test_dashboard.py`, `scripts/agent/progress.py`, `scripts/agent/test_progress.py`
- **Steps**:
  - [x] Move the totals of `progress.render` (elapsed, agent runs and time, tokens, cost text) into `progress.totals(steps, events, now)` and use it in `render`; the comment's text stays the same
  - [x] `dashboard.py`: the node model (name, state, minutes when running, link key) built from a record (`progress.tsv` events, run alive, plan packages, markers, CI runs, pull request labels) as described in "The graph", with the next expected step
  - [x] The states `done`, `running`, `waiting`, `failed`, `stopped`, `todo`, and the stage and "waiting for" of the process
  - [x] The fallback from the plan and the markers when there is no `progress.tsv`
  - [x] `dashboard.py graph <record.json> [--now <epoch>] [--max-rounds <n>] [--max-ci-fixes <n>]`: the Mermaid `flowchart LR` block with escaped labels, the `classDef`s and `click` lines
- **Tests**: `test_dashboard.py` with records for each stage: plan review (no `progress.tsv`, label `plan`: `plan` waiting, every WP todo), mid-implementation (WP1 done, WP2 running with minutes, WP3 todo), review round 2 (two CI nodes, a CI fix after a red CI, R1 and fix R1 done, R2 running), ready for review, stopped (an open step without a live run; a `stop` marker), a failed CI (red node, next `CI fix`), the markers-only fallback, label escaping, the next expected step after each kind of step; `test_progress.py` still passes unchanged plus one test of `totals`

### WP2: The section, the link table and the merge of the body

- **Status**: open
- **Depends on**: WP1
- **Files**: `scripts/agent/dashboard.py`, `scripts/agent/test_dashboard.py`
- **Steps**:
  - [ ] The active-process rule (open and labelled `plan`/`agent`, a live run, or an open draft whose newest marker is `stop`) as `dashboard.py active <record.json>` (exit 0 / 1) and a function the merge uses
  - [ ] The section: marker, heading, facts line, graph, link table (issue, plan file, pull request, files tab, progress comment, review comments, final comment, CI run of the head, branch), end marker; `dashboard.py section <record.json> …`
  - [ ] The body: `<!-- agent-dashboard -->` header, "Updated …", sections by issue number, "No active agent processes" when empty
  - [ ] `dashboard.py merge <old body> --section <file>... --active <branch>...`: replace or add own sections, remove inactive ones, keep the others, turn a non-dashboard body into one, the size limit
- **Tests**: the link table (every link with its URL, rows without a URL left out, the WP heading anchors), the facts line per stage, the merge (replace own section and keep another machine's, add a new one, remove a merged / closed / ready one, empty-state text, a body without the marker, two processes ordered by issue), the size limit

### WP3: Posting it from the scripts

- **Status**: open
- **Depends on**: WP2
- **Files**: `scripts/agent/dashboard.sh`, `scripts/agent/progress.sh`, `scripts/agent/lib.sh`, `mise.toml`, `scripts/github-setup.sh`
- **Steps**:
  - [ ] `dashboard.sh` with the commands, the gathering of a record, finding or creating (and pinning) the issue, the machine-wide lock with the pending flag, the read-back retry, `AGENT_DASHBOARD=0`, failures logged and ignored in `update`
  - [ ] `lib.sh`: `AGENT_DASHBOARD` default and header line, `progress_publish`'s guard, the final `dashboard.sh update --wait` in `agent_exit` after the lock is released
  - [ ] `progress.sh post`: the progress comment only with `AGENT_ISSUE_PROGRESS=1`, then the dashboard update with `AGENT_DASHBOARD=1`; its header comment names it
  - [ ] `mise.toml`: task `agent:dashboard` (`scripts/agent/dashboard.sh`, description with `[--post]`); the `agent:test` description mentions the dashboard
  - [ ] `scripts/github-setup.sh`: label `agent-dashboard` ("The issue that shows the active agent processes")
- **Tests**: the logic is covered by `test_dashboard.py` (WP1, WP2); here `mise run agent:test` and `mise run check` pass, `bash -n` on the changed scripts, and `AGENT_DASHBOARD=0 scripts/agent/dashboard.sh update plan/258-x` exits 0 without a GitHub call. A run against GitHub is the developer's (see Tests)

### WP4: Documentation

- **Status**: open
- **Depends on**: WP3
- **Files**: `docs/agentic-development.md`, `docs/development.md`
- **Steps**:
  - [ ] `docs/agentic-development.md`: a "Dashboard" section after "Progress on the issue" (what it lists, the graph and its colours, the facts and links, when it is updated, several runs and machines, `mise run agent:dashboard [--post]`, how it differs from the progress comment); the `AGENT_DASHBOARD` row in "Settings"; the state directory bullet in "Decision" names `dashboard.issue` and `dashboard.lock` under `.git/agent/`; "Progress while it runs" points to the dashboard
  - [ ] `docs/development.md`: `mise run agent:dashboard` in the task list
- **Tests**: `mise run agent:test` (the link check `doc_links.py` resolves every new link and anchor)

## Tests

- **Unit**: `scripts/agent/test_dashboard.py` (WP1, WP2) proves the graph for each stage the issue
  names (plan review, mid-implementation, review round 2, ready for review, stopped), the failed,
  waiting and running states, the link table and the replace-own-section merge; `test_progress.py`
  proves the progress comment is unchanged by the `totals` refactoring. Both run in `mise run
  agent:test`, part of `mise run check` and CI's ta-runner job.
- **By the developer, against GitHub** (the agents cannot call `gh`): start `/develop-issue` on two
  issues and watch one dashboard issue with two graphs and working links while the running node
  moves; a red CI, a stopped run and a run waiting for the developer look different from a running
  one; merging or closing a pull request removes its section on the next update; with nothing active
  the body says "No active agent processes". This is the issue's "done when".

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md`, new section "Dashboard" after "Progress on the issue"; "Settings" table; "Decision" (state directory); "Progress while it runs" | What the dashboard shows and when, `mise run agent:dashboard [--post]`, `AGENT_DASHBOARD` |
| Text | `docs/development.md`, the task list | `mise run agent:dashboard` |
| Text | `scripts/agent/lib.sh` header, `scripts/agent/progress.sh` header (WP3) | `AGENT_DASHBOARD`, the hook |
| Screenshot | `docs/screenshots/agent-dashboard.png`, new | The dashboard issue on GitHub with two active processes (one in the review → fix loop, one in plan review), shown in the "Dashboard" section. It is a GitHub page, which `mise run screenshots` (the app's pages, against the built jar) cannot take, and the agents cannot reach GitHub: the developer takes it by hand from the first live dashboard and adds it with its image line in the same pull request (an image line without the file would fail `doc_links.py`, so the developer agent does not add it) |

## Out of scope

- Changing the flow or what the scripts record (the issue's "Out of scope"): the dashboard only
  reads `progress.tsv`, `run.pid`, `current`, `progress.comment`, the plan, the markers, the labels
  and CI's runs.
- Updating another machine's section: only the machine that runs a process updates it; its section
  is kept until the pull request closes.
- A workflow that updates the dashboard from GitHub events (a follow-up issue if wanted).
- Changes to the skills under `.claude/` (`develop-issue` gets the dashboard through `progress.sh`
  without a change) and to the `agent-progress` mod.

## Open questions

None. Decided with the approval:

- A loop that gave up stays listed as `stopped` while its open draft pull request's newest marker is `stop`.
- The Mermaid `click … href` lines stay; the table under the graph carries the same links anyway.
- The developer takes `docs/screenshots/agent-dashboard.png` by hand, as "Docs to update" says.
- The dashboard issue is pinned once at creation; a failed pin is logged.
