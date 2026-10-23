# Plan: Model and limit usage per step, with planning and orchestration

- **Issue**: #219 (Develop-issue progress: model and limit usage per step, with planning and orchestration)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The usage table of a `/develop-issue` run shows, for every row, the model that ran it and the
Claude 5-hour and weekly limit before the step, after it and the difference. The plan and the
orchestration (the `develop-issue` session itself) get rows of their own, and the **Total** row
covers everything the issue consumed, with the total limit consumption next to it. A limit that
cannot be read renders as `n/a` and never fails a step.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`) | Bash and standard-library Python, as the neighbouring scripts. Tests in `test_*.py`, run by `mise run agent:test`. No comment that restates the code ([repository-code-comments.md](../docs/coding-convention/repository-code-comments.md)). |
| Docs | `docs/agentic-development.md` documents the flow and its settings. |
| Skills (`.claude/skills/`) | The headless agents may not edit them: the skill changes are listed as done by hand. |

No backend, frontend, ta-runner, e2e or deploy change.

## Design

### Where the numbers come from

- **Agent runs** (`implement`, `fix-ci`, `review`, `fix`): the stream `claude -p --output-format
  stream-json` already writes (`<step>.jsonl`) holds `rate_limit_event` lines with
  `rate_limit_info.unifiedWindows.five_hour.utilization` and `.seven_day.utilization` (a fraction,
  `0.08` = 8%). So headless runs do report the plan limits, with no extra call. The model of a run
  is the `model` of its `system` `init` event, or the keys of the result's `modelUsage`; the
  scripts' `--model` value is only a fallback.
  - *Before* is the first limit event of the run, *after* the last one, as whole percents. The
    first event comes after the first model turn, so a run's own first turn is missing from the
    diff; the comment says so.
  - A step made of several runs (the work packages of `implement`, a run and its interrupted
    streams) takes *before* from its first run and *after* from its last one, and lists every
    distinct model, comma separated.
  - A run without such an event (an API key, another endpoint such as DeepSeek, an older Claude
    Code) has no limit values: `n/a`.
- **Planning and orchestration** run in the developer's own session, not through `lib.sh`:
  - *Tokens, cost, model*: read from the session's transcript
    (`~/.claude/projects/<encoded session directory>/<session id>.jsonl`), summing the `usage` of
    the assistant messages whose timestamp lies in the step's window, each message id once. The
    transcript has no cost: it is computed from a small price table of the Claude models in
    `agent_json.py` (per million tokens: input, cache write, cache read, output); a model not in
    the table has no cost (`n/a`, never `$0`). The session id is the newest transcript of the
    session's directory, so a transcript that cannot be found gives `n/a` for the whole row.
  - *Limits*: the session has no stream. `progress.sh` takes a **limit probe** at the start and the
    end of each window: one headless call (`claude -p --model haiku`, a one-word prompt, stream-json,
    no tools) whose `rate_limit_event` is read by `agent_json.py limits`. It costs a few tokens, which
    are not counted in any row. A failed probe is `n/a`.
- Limits are shared with everything else the developer runs at the same time, so the diff is an
  approximation. The comment says so under the table.

### Rows

- **plan**: the window `plan_start` until the approval (the plan-from-issue run and its review
  rounds), recorded by `progress.sh begin|end … plan` as today; `end` now also records the session
  usage and the probes of that window.
- **update with main**: the same, for the step the session already records.
- **orchestration**: decided: **one summed row**, not one per step. CI waits, fix-ci, review, fix
  and finalise run in the scripts, whose agents already have their rows; the session only sleeps on
  the Monitor between them, so a row per step would be almost empty. The row is the session's usage
  outside the plan and the update windows, from the start of `run.sh` to the ready-for-review
  notification, recorded by `progress.sh orchestration <pr>` (new subcommand, run by the skill at
  step 7 before the run summary). Its steps and result: `progress.tsv` gets a `begin`/`end` pair
  named `orchestration` like any other step, with its start, end, duration, tokens and result.

### Marker and progress.tsv

The `agent-step` marker gains `model=<name>[,<name>]` and
`limit5h_before=<n> limit5h_after=<n> limit7d_before=<n> limit7d_after=<n>` (whole percents;
a value that is not known is left out of the marker, and the table renders it `n/a`). Old markers
without them render `n/a`, so the loop's state (`steps`, `approved-head`) stays compatible.
The `usage` event's text in `progress.tsv` carries the same keys; `progress.py` reads them as it
reads `tokens` and `cost_usd`.

### The tables

- `agent_json.py usage-table` (the finalise comment): columns `Step | Round | Model | Input |
  Cached input | Output | Cost | 5h before → after (Δ) | Weekly before → after (Δ)`, as in the
  issue. The **Total** row keeps the sums and adds the limit consumption: the first *before* to the
  last *after*, both windows. A row without values shows `n/a`; a total with an `n/a` row says
  `at least` as the cost already does.
- `progress.py render` (the issue comment) gets a **Usage** section with the same columns for every
  step that has usage, plan and orchestration included, and its own **Total** row, so it is
  everything consumed to implement the issue. The existing header line (tokens, cost) uses the
  same totals. The finalise table cannot hold the rows recorded after it (update with main,
  orchestration); the issue comment and the run summary do.
- `progress.sh usage <branch | pr>` (new) prints that Usage section alone, for the skill's run
  summary on the pull request.

### Where the code goes

- `scripts/agent/agent_json.py`: `limits` (the first and last limit event of a run or stream),
  `model` of a run, the session transcript reader (`session-usage <transcript> <from> <to>`), the
  price table, `usage_marker` with the new fields, `usage_table` with the new columns,
  `progress-usage` with the new fields.
- `scripts/agent/lib.sh`: `usage_marker` unchanged in shape (the new fields come from
  `agent_json.py`); `run_agent` needs no new code, the stream is already kept. `finalise.sh`: the
  header line "developer: …, review: …" goes (the model is in every row).
- `scripts/agent/progress.py`: parse and sum the new keys, the Usage section.
- `scripts/agent/progress.sh`: the probe, session usage in `end`, `orchestration`, `usage`.

## Work packages

### WP1: Model and limits in agent runs and their markers

- **Depends on**: none
- **Files**: `scripts/agent/agent_json.py`, `scripts/agent/test_agent_json.py`
- **Steps**:
  - [ ] Read the model of a run and the first and last `unifiedWindows` utilization of a run or
    stream (percents, `None` when there is none); a cut-off stream and its interrupted siblings
    combine as `usage()` does.
  - [ ] Put `model=` and the four `limit*` keys into `usage_marker` and `progress-usage`, leaving a
    key out when unknown.
  - [ ] Rebuild `usage_table` with the new columns, the `n/a` cells and the total limit
    consumption.
- **Tests**: a stream with several limit events gives first and last; a run without any gives no
  keys and `n/a` cells; two runs of one step give the first run's before and the last run's after;
  two models are listed once each; an old marker without the new keys renders; the total row sums
  and says `at least` with a partial or `n/a` row.

### WP2: Session usage, probes and the new rows

- **Depends on**: WP1
- **Files**: `scripts/agent/agent_json.py`, `scripts/agent/progress.sh`,
  `scripts/agent/test_agent_json.py`
- **Steps**:
  - [ ] `session-usage`: sum a transcript's assistant usage in a time window, each message id once,
    with the model and the cost from the price table (no cost for an unknown model).
  - [ ] `limits` on a probe's stream; `progress.sh` runs the probe at `begin` and `end` and writes
    the `usage` event of the window (`plan`, `update with main`).
  - [ ] `progress.sh orchestration <pr>` and `progress.sh usage <branch | pr>`.
  - [ ] A missing transcript, a failed probe or an unknown model gives `n/a` and a one-line
    message, never a failing exit.
- **Tests**: a transcript fixture with messages inside and outside the window and a repeated
  message id; an unknown model has tokens and no cost; no transcript gives an empty result.

### WP3: The progress comment

- **Depends on**: WP1
- **Files**: `scripts/agent/progress.py`, `scripts/agent/test_progress.py`
- **Steps**:
  - [ ] Parse the new `usage` keys per step (sub-steps summed into their step, as tokens are).
  - [ ] Render the Usage section and the Total with the limit consumption; take the header's
    tokens and cost from the same sums.
  - [ ] The note under the table: limits are shared with whatever else runs, the diff is
    approximate, the first turn of a run is not in it.
- **Tests**: `A_RUN` extended with plan, orchestration and limit values: the table has a row per
  step with usage, plan and orchestration included, the total covers them, rows without limits show
  `n/a`, and the existing assertions still hold.

### WP4: Finalise, docs

- **Depends on**: WP1, WP3
- **Files**: `scripts/agent/finalise.sh`, `docs/agentic-development.md`
- **Steps**:
  - [ ] Drop the model header line from the finalise comment.
  - [ ] Document the usage table, where the limits come from, the probe, the orchestration row and
    the approximation in "Progress on the issue" and under the run summary of `/develop-issue`.
- **Tests**: covered by WP1 and WP3; `mise run check` and `mise run agent:test`.

## Tests

`mise run agent:test` (all of the above), and `mise run check`. The "done when" of the issue: every
Usage row has the model and the three limit values or `n/a` (WP1), plan and orchestration have rows
with tokens, cost and progress details (WP2, WP3), the total includes them with the limit
consumption (WP3), and a missing value is `n/a` without a failure (WP1, WP2).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md`, "Progress on the issue" | The Usage section: columns, the `n/a` rule, the total with the limit consumption, the approximation. |
| Text | `docs/agentic-development.md`, the `/develop-issue` section | The plan, orchestration and update rows, the probe, the session transcript as the source, and `progress.sh orchestration` / `usage`. |
| Text | `.claude/skills/develop-issue/SKILL.md` (by hand, the agents may not edit it) | Step 7 runs `progress.sh orchestration <pr>` and puts `progress.sh usage <pr>` in the run summary; the progress table lists the new step. |
| Screenshot | none | No UI page changes. |

## Out of scope

- Limits for a run on the DeepSeek endpoint or with an API key: `n/a`.
- A bill: the cost stays at API prices.
- Per-step rows for the session's CI waits: they use no session tokens (see the decision above).

## Open questions

- The session's cost uses a price table kept in `agent_json.py`, because the transcript has none:
  it has to be updated when models or prices change. Is that acceptable, or should the cost of the
  plan and orchestration rows be `n/a` (tokens only)?
- The probe is one cheap headless Haiku call per window boundary (about six per run). Acceptable, or
  should the limits of the session's rows be read from a status line snapshot you set up instead?
