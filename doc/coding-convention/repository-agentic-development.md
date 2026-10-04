# Repository: agentic development

Agents take a GitHub issue to a reviewed pull request with a green CI. A developer decides at two
points only: approving the plan and merging the pull request (#65). The agents follow the
documented architecture and conventions; the plan they work from is a file in the repository that
the developer reviewed, so their work can be checked against it.

## The flow

| # | Step | Who | Where | Output |
|---|---|---|---|---|
| 1 | Plan: read the issue and the documents of every part it touches; one plan per reviewable pull request | Claude Code skill `plan-from-issue` | Developer's machine | `.plans/<issue>-<slug>.md` |
| 2 | One branch per plan, holding only that plan file | `scripts/agent/plan-branch.sh` (the skill runs it) | Developer's machine | `plan/<issue>-<slug>` pushed, linked on the issue |
| 3 | Plan review: edit the plan on its branch, or approve it | Developer | — | — |
| 4 | Start the implementation | Developer, one command | — | — |
| 5 | Implement with tests, `mise run check`, version bump; push; open a **draft** pull request; fix a red CI | Developer agent: `implement.sh`, `fix-ci.sh` | GitHub Actions | Draft PR labelled `agent`, **CI passed** green |
| 6 | Review against the plan and the conventions: one inline comment per finding, with priority and possible solutions | Review agent: `review.sh` | GitHub Actions | PR review + summary comment |
| 7 | One commit per finding (`Address R1-3: …`), or a reply declining it; reply on each thread; push | Developer agent: `fix.sh` | GitHub Actions | Commits, thread replies |
| 8 | Second review → fix round (6–7 again) | Agents | GitHub Actions | — |
| 9 | Finalise: what the plan asked for, what was done, findings fixed / declined / open, tokens used; mark ready for review | `finalise.sh` | GitHub Actions | PR ready for review |
| 10 | Review and merge | Developer | — | — |

Agents never merge and never push to `main`: the scripts push only to the plan branch, the agents
get no GitHub token, and `main`'s ruleset requires a code owner's approval and **CI passed** for
every merge anyway.

## Decision

- **Hybrid: planning locally, the loop in GitHub Actions.** Planning (steps 1–3) is interactive and
  runs where the developer is, in Claude Code. The implement → review → fix loop (steps 5–9) is long
  and needs nobody, so it runs unattended in Actions, in the same environment as CI, visible in the
  Actions tab.
- **One agent runtime: Claude Code.** The same skills, `CLAUDE.md`, subagents and hooks for
  planning and for the loop. In the loop it runs headless (`claude -p`) against DeepSeek's
  Anthropic-compatible endpoint (`https://api.deepseek.com/anthropic`), with the models named
  explicitly (`deepseek-v4-pro` for the developer and review agents, `deepseek-flash` for Claude
  Code's background calls). Endpoint and models are environment variables, so switching is
  configuration. Planning runs on the developer's own Claude Code.
- **Workflows thin, logic in scripts.** A workflow only sets up the toolchain and secrets and
  calls one script in [`scripts/agent/`](../../scripts/agent). The scripts read everything from
  environment variables and arguments, so each runs the same on a developer's machine; that is how
  they are developed and debugged.
- **Started by `workflow_dispatch`** with the plan branch as input: one explicit command, only for
  people with write access.
- **The loop follows CI.** [`agent-loop.yml`](../../.github/workflows/agent-loop.yml) runs after
  every CI run on a `plan/**` branch (`workflow_run`), so a review always sees a green head, never
  runs in parallel with CI, and a red CI goes to the developer agent first.
- **The pull request is the state.** Every step leaves one comment, by the agent account, with a
  hidden marker (`<!-- agent-step step=review round=1 tokens=… -->`). `next.sh` reads the next step
  from these markers and CI's result; nothing else is stored. Comments by other accounts are
  ignored.
- **Pushes with a GitHub App token.** Pushes made with a workflow's `GITHUB_TOKEN` start no
  workflow, so CI would not run on the agents' commits. A GitHub App's installation token is
  short-lived and scoped to this repository; a fine-grained personal access token is the fallback.
- **The plan file is merged with the code.** It stays in `.plans/` as the record of why the change
  looks the way it does.
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

### Planning (step 1–3)

In Claude Code, in this repository:

```text
/plan-from-issue 32
```

The skill ([`.claude/skills/plan-from-issue/SKILL.md`](../../.claude/skills/plan-from-issue/SKILL.md))
reads the issue and the documents of the parts it touches, writes the plan(s) from
[`plan-template.md`](../../.claude/skills/plan-from-issue/plan-template.md), and, once you agree,
pushes one branch per plan (`mise run agent:plan-branch .plans/<issue>-<slug>.md`). Review the plan
on its branch; edit and push there.

### The loop (step 4–9), in GitHub Actions

```sh
gh workflow run agent-implement.yml -f plan_branch=plan/32-compare-runs
```

That is all: the implementation opens a draft pull request, CI runs, and `agent-loop.yml` takes it
from there until the pull request is ready for review. To continue a stopped loop by hand:
`gh workflow run agent-loop.yml -f branch=plan/<issue>-<slug>`. Both workflows must be on `main`
(`gh workflow run` and `workflow_run` only see workflows on the default branch).

### The same steps locally

Every step is a script; with `DEEPSEEK_API_KEY` in the environment:

```sh
mise run agent:implement plan/32-compare-runs        # step 5: implement, push, draft pull request
scripts/agent/next.sh --dry-run plan/32-compare-runs # which step is next
mise run agent:next plan/32-compare-runs             # run it, and the next ones until a push
scripts/agent/review.sh <pr>                         # or one step: review, fix, fix-ci, finalise
```

`AGENT_BASE_BRANCH=<branch>` starts the plan branch from, and opens the pull request into, another
branch than `main` (a stacked change, or trying out a change to the agents themselves); later steps
use the pull request's base.
`AGENT_BASE_URL= AGENT_MODEL=claude-sonnet-5-5 AGENT_SMALL_MODEL=claude-haiku-4-5-20251001` runs
them on Claude Code's own login and Claude models instead of DeepSeek. The scripts need a clean
working tree: they switch to the plan branch. They run from a copy of `scripts/agent/` taken at
start, so switching branches does not change the running scripts or prompts.

### Stopping a run

- Remove the `agent` label from the pull request: the loop does nothing on it any more. Add it back
  and run `agent-loop.yml` by hand to continue.
- Cancel the workflow run in the Actions tab.
- The loop stops by itself (comment, label removed, pull request left as a draft) after
  `AGENT_MAX_CI_FIXES` attempts at a red CI, when the developer agent finds no fix for it, or over
  `AGENT_MAX_TOKENS`.

## Configuration

Repository **secrets** and **variables** (Settings → Secrets and variables → Actions):

| Name | Kind | What |
|---|---|---|
| `DEEPSEEK_API_KEY` | secret | The model endpoint's key |
| `AGENT_APP_ID` | variable | The GitHub App the agents push and comment as |
| `AGENT_APP_PRIVATE_KEY` | secret | Its private key |
| `AGENT_GITHUB_TOKEN` | secret | Without an App: a fine-grained personal access token for this repository |
| `AGENT_MODEL`, `AGENT_REVIEW_MODEL`, `AGENT_SMALL_MODEL` | variables | Override the models (defaults above) |
| `AGENT_MAX_TOKENS` | variable | Token limit per pull request (default 50 000 000, cached input included) |

The App (or token) needs, on this repository only: **Contents** read and write, **Pull requests**
read and write, **Issues** read and write (labels, comments), **Checks** and **Actions** read. Not
**Workflows**: a plan that changes `.github/workflows/` cannot be pushed by the agents and is
implemented by hand.

Limits, as environment variables of the scripts ([`lib.sh`](../../scripts/agent/lib.sh)):
`AGENT_MAX_ROUNDS` (2), `AGENT_MAX_CI_FIXES` (3), `AGENT_MAX_TOKENS`, and `AGENT_MAX_BUDGET_USD`
per agent call (Claude prices; not meaningful for DeepSeek). The implement job may take 150
minutes, a loop job 180.

## Security

- The DeepSeek key lives in an Actions secret or the developer's environment, never in the
  repository or a log.
- `agent-loop.yml` runs with secrets after CI, so it acts only on branches of this repository,
  never a fork's, and runs the scripts and prompts of `main`, not those of the pull request.
- The agents run with an allow-list of tools (`--permission-mode dontAsk`): no `git push`, no
  `gh`, no skipping the Git hooks, no web search; the review agent only reads. They get no GitHub
  token. What they read is the reviewed plan, the code, CI's logs and their own review comments,
  not comments by other accounts.

## Not done yet

- **The DeepSeek spike** (#65): an end-to-end run with DeepSeek's endpoint, in Actions, on a small
  issue; the comparison of `deepseek-v4-pro` and `deepseek-flash` for the developer and review
  agents; the result goes into this document.
- **Parallel work packages** each in their own `git worktree`: for now one developer agent works
  through the packages and may hand independent ones to subagents.
- **Sub-issues** for the plans of a split issue.
