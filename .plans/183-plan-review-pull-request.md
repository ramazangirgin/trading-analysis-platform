# Plan: Review the plan as a draft pull request

- **Issue**: #183 (Agentic flow: review the plan as a draft pull request)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A pushed plan gets a draft pull request right away, labelled `plan`, so the developer reviews the
plan where they prefer: inline on the plan's lines or as comments on the pull request, or in the
Claude Code session. The planning session reads both, changes the plan on its branch and answers
each comment. Once the plan is approved, the same pull request carries on: the agents push the
implementation to it, and its label changes from `plan` to `agent`. The plan discussion stays next to
the code review, and there is still one pull request per plan.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`) | The existing style: one script per step, `lib.sh` for shared helpers and settings (`AGENT_*`), milestone lines (`milestone`), state on the pull request and the plan branch, `die` on errors. Pure logic in Python next to the scripts (`plan.py`, `review.py`) with `test_*.py` (`mise run agent:test`, part of CI's *ta-runner* job). |
| mise tasks (`mise.toml`) | One `agent:*` task per script, with a usage line in the description. |
| CI (`.github/workflows/ci.yml`), `scripts/version.sh` | `docs/coding-convention/repository-versioning-and-releases.md`: every pull request into `main` raises the version; exceptions are written in that document and in the *Version* job. |
| Skills (`.claude/skills/`) | `plan-from-issue` and `develop-issue`. The agents run headless and may not edit `.claude/`, so this session changes them by hand (WP3). |
| Docs | `docs/agentic-development.md` (the flow, the steps, the walkthrough), the root `README.md` (*Agentic development*), the versioning document. |

## Design

### The label and the pull request's stages

- `lib.sh` gets `AGENT_PLAN_LABEL=plan` next to `AGENT_LABEL=agent`, and `pr_stage <pr>`, which
  prints `plan` (open draft labelled `plan`), `agent` (open draft labelled `agent`) or `other`. The
  scripts decide with it rather than with "the branch has an open pull request".
- A plan pull request has the plan file as its only change, the title `Plan: <plan title>` and a body
  that links the plan and the issue without a closing keyword (`Plan for #<issue>`). The body
  says how to comment (inline on the plan's lines, or a comment) and how to approve: in the
  session, or `mise run agent:run <branch>`.

### Pushing the plan (`plan-branch.sh`)

After pushing the branch, it creates the `plan` label if missing and opens the draft pull request
(`gh pr create --draft --label plan`). The issue comment links the pull request instead of the
plan file alone. It prints the pull request's URL. The rest (one commit from `origin/main`, the
local copy removed) is unchanged.

### Comments on the plan (`plan-comments.sh`, `plan_comments.py`)

- `scripts/agent/plan-comments.sh <pr | branch>` (task `agent:plan-comments`) prints what the
  developer said on the plan pull request that has no answer yet:
  - each review thread that is not resolved and whose last comment is not a reply of the session,
    with its comment ID, `path:line`, author and text;
  - each pull request comment and each review body that is not empty and came after the session's
    latest reply.
  It reads the review threads with `gh api graphql` (`reviewThreads`: `isResolved`, comments with
  `databaseId`, `path`, `line`, `author`, `body`, `createdAt`) and the comments and reviews with
  `gh pr view --json comments,reviews`.
- The session's replies carry a hidden marker, `<!-- plan-reply -->`, and the agent steps' comments
  (`<!-- agent-step … -->`) and the progress comment are never listed. The filtering is pure Python,
  `scripts/agent/plan_comments.py` (JSON in, lines out), so it is tested without GitHub.
- `plan-comments.sh --reply <comment id> <file>` replies on a review thread, and
  `--comment <file>` adds a pull request comment. Both add the marker. The session uses them, so
  the marker is written in one place.

### Implementation on the plan pull request

- `run.sh` runs `implement.sh` unless the branch's pull request is past the plan stage
  (`pr_stage` is `agent` or `other`). This keeps an interrupted implementation resumable, because
  the `plan` label stays until the implementation is done.
- `implement.sh`:
  - it refuses a branch whose pull request is past the plan stage ("continue it with next.sh"),
    not one in the plan stage;
  - it runs the packages as today;
  - at the end, with a plan pull request, it edits it (`gh pr edit`: the title, the body as today,
    `--add-label agent --remove-label plan`) instead of creating one, with the milestone
    `pull request #<pr> taken from plan review to implementation: <url>`. Without one (a branch
    pushed before this change, or a deleted pull request) it opens the draft pull request as today;
  - `--dry-run` says which of the two it would do.
- `next.sh` on a pull request in the plan stage prints
  `waiting for: plan approval (mise run agent:run <branch>)` and runs nothing, in `--dry-run` too.
  The `develop-issue` skill reads that line.
- `status.sh` prints the stage (`Pull request: #<n>, plan review` / `agent loop` / `ready for review`).

### CI: no version bump on a plan pull request

`scripts/version.sh check-bump <base>` passes, with the message
`only .plans/ changed: no version bump needed`, when every file the branch changes against the
merge base is under `.plans/`. A plan pull request's CI is then green. The exception is written
in the versioning document next to Renovate's.

### Skills (by hand, WP3)

- `plan-from-issue`:
  - Section 3 shows the plan in the session as today.
  - Section 4 pushes the plan with `plan-branch.sh` (which now opens the draft pull request) once
    the plan is ready for review. That is no longer after the approval.
  - A new section covers the review: the developer comments on the pull request or in the terminal.
    The session runs `plan-comments.sh`, changes the plan on its branch in the issue's folder
    (commit `Plan: <what changed>`, push), and answers every comment with `--reply` / `--comment`,
    saying what changed or why not. It never resolves a thread: the developer does.
  - Section 5 names the pull request and `mise run agent:run <branch>` as the approval.
- `develop-issue`:
  - Step 0 adds "draft labelled `plan`: the plan is under review → step 2".
  - Step 2 pushes the plan before asking (step 3 moves into it), and prints the pull request's link.
    On **Change it**, or after the developer says they have commented, it reads `plan-comments.sh`
    as well as the terminal answer. It asks again until the plan is approved.
  - Step 4 notes that the plan pull request becomes the implementation pull request.
  - The progress table's "plan" row is recorded once the plan is approved.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Scripts: plan pull request, its comments, the implementation on it

- **Status**: done
- **Depends on**: none
- **Files**: `scripts/agent/lib.sh`, `scripts/agent/plan-branch.sh`, `scripts/agent/implement.sh`,
  `scripts/agent/run.sh`, `scripts/agent/next.sh`, `scripts/agent/status.sh`,
  `scripts/agent/plan-comments.sh` (new), `scripts/agent/plan_comments.py` (new),
  `scripts/agent/test_plan_comments.py` (new), `mise.toml`
- **Steps**:
  - [x] `AGENT_PLAN_LABEL` and `pr_stage` in `lib.sh`.
  - [x] `plan-branch.sh` opens the draft pull request labelled `plan` and links it on the issue.
  - [x] `plan-comments.sh` with `plan_comments.py`: list the unanswered comments, `--reply`, `--comment`.
  - [x] `implement.sh` takes over a plan pull request (title, body, labels), else opens one as
        today; its refusal and `--dry-run` follow `pr_stage`.
  - [x] `run.sh`, `next.sh` and `status.sh` follow `pr_stage` as in the design.
  - [x] The `agent:plan-comments` task, and the descriptions of `agent:plan-branch`, `agent:run`
        and `agent:implement`, in `mise.toml`.
- **Tests**: `test_plan_comments.py`:
  - a resolved thread is not listed;
  - a thread whose last comment is a `plan-reply` is not listed, but one with a developer comment
    after the reply is;
  - comments with the agent-step or progress marker are not listed;
  - pull request comments and review bodies before the latest reply are not listed, later ones are;
  - empty review bodies are not listed.

  `mise run agent:test` passes.

### WP2: CI: no version bump for a plan-only pull request

- **Status**: done
- **Depends on**: none
- **Files**: `scripts/version.sh`, `docs/coding-convention/repository-versioning-and-releases.md`
- **Steps**:
  - [x] `check-bump` passes when every changed file against the merge base is under `.plans/`.
  - [x] The exception in the versioning document: the rules list and the *Version* job's row.
- **Tests**: run `scripts/version.sh check-bump origin/main` on two branches: one that only adds a
  `.plans/` file (passes without a bump), and one that also changes another file (fails as before).
  Write the result in the package's summary.

### WP3: Skills: plan-from-issue and develop-issue (by hand, this session)

- **Status**: done
- **Depends on**: WP1 (the commands it names)
- **Files**: `.claude/skills/plan-from-issue/SKILL.md`, `.claude/skills/develop-issue/SKILL.md`
- **Steps**:
  - [x] `plan-from-issue` as in the design: push for review, the comment loop, the approval.
  - [x] `develop-issue` as in the design: step 0, step 2 with the push and the comments, step 4,
        the progress table.
- **Tests**: none (Markdown). The developer agent does not do this package: the agents may not
  edit `.claude/`. The develop-issue session does it on the plan branch before the agents start, and
  marks it done in the plan.

### WP4: Docs

- **Status**: done
- **Depends on**: WP1, WP2
- **Files**: `docs/agentic-development.md`, `README.md`, `gradle.properties`,
  `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [x] `docs/agentic-development.md`:
    - the flow diagram, with the draft pull request labelled `plan` before the developer's plan
      decision, and the label change on approval;
    - the steps table (steps 2 to 5);
    - the decision bullet "The pull request is the state" (the plan stage);
    - the walkthrough's steps 2 to 4: where to comment, `mise run agent:plan-comments`, approving.
  - [x] `README.md`, *Agentic development*: the sample flow's steps 1 and 2 (draft pull request for
        the plan, comment there or in the session), and the `develop-issue` paragraph.
  - [x] `mise run version:bump minor`.
- **Tests**: none (docs).

## Tests

- `mise run agent:test`: the new `test_plan_comments.py` and the existing tests.
- `mise run check`.
- `scripts/version.sh check-bump` checked by hand on a plan-only branch and on a mixed one (WP2).
- The flow end to end is checked by its first use. Issue #89's plan is pushed with the new
  `plan-branch.sh` once this is merged; that run is the proof for the issue's acceptance criteria.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md` | The flow diagram, the steps table, "The pull request is the state", the walkthrough's steps 2–4 (WP4). |
| Text | `README.md`, *Agentic development* | The sample flow and the `develop-issue` paragraph (WP4). |
| Text | `docs/coding-convention/repository-versioning-and-releases.md` | The plan-only exception to the version bump (WP2). |
| Text | `.claude/skills/plan-from-issue/SKILL.md`, `.claude/skills/develop-issue/SKILL.md` | The plan pull request and its review (WP3). |
| Screenshot | none | No page of the platform changes. |

## Out of scope

- Approving the plan from the pull request itself (a label or a GitHub approval that starts the
  agents): the approval stays in the session or `mise run agent:run`.
- Moving the plan branches already pushed without a pull request: `implement.sh` still opens the
  pull request for them as today.
