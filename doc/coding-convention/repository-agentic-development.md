# Repository: agentic development

Agents take a GitHub issue to a reviewed pull request with a green CI. A developer decides at two
points only: approving the plan and merging the pull request (#65). The agents follow the
documented architecture and conventions; the plan they work from is a file in the repository that
the developer reviewed, so their work can be checked against it.

## The flow

| # | Step | Who | Output |
|---|---|---|---|
| 1 | Plan: read the issue and the documents of every part it touches; one plan per reviewable pull request | Claude Code skill `plan-from-issue` | `.plans/<issue>-<slug>.md` |
| 2 | One branch per plan, holding only that plan file | `scripts/agent/plan-branch.sh` (the skill runs it) | `plan/<issue>-<slug>` pushed, linked on the issue |
| 3 | Plan review: edit the plan on its branch, or approve it | Developer | — |
| 4 | Start the agents | Developer: `mise run agent:run plan/<issue>-<slug>` | — |
| 5 | Implement with tests, `mise run check`, version bump; push; open a **draft** pull request; fix a red CI | Developer agent: `implement.sh`, `fix-ci.sh` | Draft PR labelled `agent`, **CI passed** green |
| 6 | Review against the plan and the conventions: one inline comment per finding, with priority and possible solutions | Review agent: `review.sh` | PR review + summary comment |
| 7 | One commit per finding (`Address R1-3: …`), or a reply declining it; reply on each thread; push | Developer agent: `fix.sh` | Commits, thread replies |
| 8 | Second review → fix round (6–7 again) | Agents | — |
| 9 | Finalise: what the plan asked for, what was done, findings fixed / declined / open, usage; mark ready for review | `finalise.sh` | PR ready for review |
| 10 | Review and merge | Developer | — |

Agents never merge and never push to `main`: the scripts push only to the plan branch, the agents
cannot run `git push` or `gh`, and `main`'s ruleset requires a code owner's approval and
**CI passed** for every merge anyway.

## Decision

- **Everything runs on the developer's machine, with Claude Code.** Planning, implementation,
  review and fixes all run in Claude Code with the developer's own Claude Code login: the same
  skills, `CLAUDE.md`, subagents and hooks for every step, no API key or other model endpoint to
  manage, and no secrets in GitHub. The agents run headless (`claude -p`); the scripts remove any
  `ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN` or `ANTHROPIC_BASE_URL` from their environment, so the
  login is always what they use. GitHub is reached through the developer's `gh` login and Git
  credentials, so the developer's pushes start CI as usual.
- **Scripts, one per step.** [`scripts/agent/`](../../scripts/agent) holds one script per step and
  `run.sh`, which runs them all; mise tasks start them. Each step can also be run, and debugged, on
  its own.
- **The loop follows CI.** After every push the loop waits for **CI passed** on the new head, so a
  review always sees a green head, and a red CI goes to the developer agent first.
- **The pull request is the state.** Every step leaves one comment with a hidden marker
  (`<!-- agent-step step=review round=1 tokens=… -->`). `next.sh` reads the next step from these
  markers and CI's result; nothing else is stored, so an interrupted run continues where it was.
  Comments by other accounts are ignored.
- **The plan file is merged with the code.** It stays in `.plans/` as the record of why the change
  looks the way it does.
- **Sonnet develops, Opus reviews.** The developer agent does most of the work (implementing,
  running the checks, fixing), where Sonnet is fast and good enough; the review is a single,
  judgement-heavy pass per round, where the stronger model finds more. Both are settings.
- **A fixed number of rounds**: two review → fix rounds, then the pull request goes to the
  developer, even when `MINOR` findings remain.

## Review findings

Each finding is one inline comment on the line concerned, or on the file when the line is not part
of the diff (a finding outside the diff goes into the review's body):

- an ID, `R<round>-<n>`, that the fix commit (`Address R1-3: <summary>`) and its reply refer to;
- a priority: `CRITICAL` (a bug, data loss, a security problem, a missing plan item: must be fixed),
  `MAJOR` (an edge case, a broken convention, a missing test: should be fixed), `MINOR` (optional);
- the problem, and one or more possible solutions;
- the other places with the same problem ("Same finding also at"), instead of a comment each.

The review checks the diff against the plan (missing items, out-of-plan changes), correctness,
the conventions of the parts it touches, tests and docs. The summary comment counts the findings
per priority and says whether another round is needed.

The developer agent handles one finding per run, most severe first. A fix becomes one commit
through the pre-commit hook; a finding it disagrees with gets a reply with the reason instead of a
change. A thread a developer resolves before the fix round is skipped.

## Running it

Once: `mise run setup` (the Git hooks the agents commit through), Claude Code logged in
(`claude`, then `/login`), `gh auth login`.

### Planning (steps 1–3)

In Claude Code, in this repository:

```text
/plan-from-issue 32
```

The skill ([`.claude/skills/plan-from-issue/SKILL.md`](../../.claude/skills/plan-from-issue/SKILL.md))
reads the issue and the documents of the parts it touches, writes the plan(s) from
[`plan-template.md`](../../.claude/skills/plan-from-issue/plan-template.md), and, once you agree,
pushes one branch per plan (`mise run agent:plan-branch .plans/<issue>-<slug>.md`). Review the plan
on its branch; edit and push there.

### The agents (steps 4–9)

```sh
mise run agent:run plan/32-compare-runs
```

It implements the plan, opens the draft pull request and runs the review → fix loop until the pull
request is ready for review, waiting for CI after each push; it takes a while, so leave the
terminal open. The scripts need a clean working tree, since they switch to the plan branch. They run
from a copy of `scripts/agent/` taken at start, so switching branches does not change the running
scripts or prompts.

Each step on its own:

```sh
mise run agent:implement plan/32-compare-runs        # implement, push, draft pull request
scripts/agent/next.sh --dry-run plan/32-compare-runs # which step is next
mise run agent:next plan/32-compare-runs             # continue the loop to its end
scripts/agent/review.sh <pr>                         # one step: review, fix, fix-ci, finalise
```

`AGENT_BASE_BRANCH=<branch>` starts the plan branch from, and opens the pull request into, another
branch than `main` (a stacked change, or trying out a change to the agents themselves); later steps
use the pull request's base.

### Stopping a run

- Ctrl+C. Started again, `mise run agent:run` (or `agent:next`) continues where it was.
- Remove the `agent` label from the pull request: the loop does nothing on it any more.
- The loop stops by itself (comment, label removed, pull request left as a draft) after
  `AGENT_MAX_CI_FIXES` attempts at a red CI, when the developer agent finds no fix for it, when CI
  takes longer than `AGENT_CI_TIMEOUT`, or over `AGENT_MAX_TOKENS`.

## Settings

Environment variables of the scripts ([`lib.sh`](../../scripts/agent/lib.sh)):

| Variable | Default | What |
|---|---|---|
| `AGENT_MODEL` | `claude-sonnet-5-5` | The developer agent's model (implement, fix-ci, fix) |
| `AGENT_REVIEW_MODEL` | `claude-opus-5-5` | The review agent's model |
| `AGENT_MAX_ROUNDS` | 2 | Review → fix rounds |
| `AGENT_MAX_CI_FIXES` | 3 | Attempts at a red CI, per pull request |
| `AGENT_MAX_TOKENS` | 50 000 000 | Tokens per pull request, cached input included |
| `AGENT_MAX_BUDGET_USD` | none | Per agent call (`claude --max-budget-usd`) |
| `AGENT_CI_TIMEOUT` | 60 | Minutes to wait for CI after a push |
| `AGENT_BASE_BRANCH` | `main` | Where plan branches start and pull requests go |

Costs in the final comment are Claude Code's own figures at API prices; with a subscription login
they show the usage, not a bill.

## Safety

- The agents run with an allow-list of tools (`--permission-mode dontAsk`): no `git push`, no
  `gh`, no skipping the Git hooks, no web search; the review agent only reads. The scripts do every
  push and every GitHub call.
- What the agents read is the reviewed plan, the code, CI's logs and their own review comments,
  not comments by other accounts.
- The agents run real commands (Gradle, pnpm, uv) on the developer's machine, like a developer
  would; run them only on plans you reviewed.

## Not done yet

- **Parallel work packages** each in their own `git worktree`: for now one developer agent works
  through the packages and may hand independent ones to subagents.
- **Sub-issues** for the plans of a split issue.
