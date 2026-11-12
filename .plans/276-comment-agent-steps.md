# Plan: A "comments" step for the planning, developer, review and fix agents

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 2 of 9 for this issue; depends on: plan 1 (`276-comment-rules`)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every stage that writes or judges code asks of each comment the same question, "would a reader lose
something if this comment were deleted?": a plan never asks for a comment, the developer agent
checks its own diff before its last commit, the review agent reports a comment or Javadoc that
restates the code as a `MINOR` finding of its own, and the fix agent deletes it. This covers what
the build rules of plan 1 cannot judge.

## Affected parts and their conventions

- Agent prompts and skills: [docs/agentic-development.md](../docs/agentic-development.md),
  [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) (as plan 1
  leaves it).

## Design

- The prompts point at `repository-code-comments.md` for the rule and the test, and say only what
  the agent does with it; they no longer say "Javadoc only where Checkstyle requires it" (plan 1:
  no Javadoc in backend Java, `TAP-J1`).
- **Developer agent** (`scripts/agent/prompts/implement.md`, the "Comments follow" rule): before
  its last commit, it reads the comments its package adds or changes (`git diff {{BASE}}...HEAD`
  and the uncommitted changes; `implement.sh` substitutes `{{BASE}}`) and deletes each that fails
  the test.
- **Review agent** (`scripts/agent/prompts/review.md`, "Conventions"): the build catches only
  `TAP-J1`, `TAP-C1`, `TAP-C2` and ruff `ERA`; for every comment and Javadoc the change adds or
  touches it asks the test. One that fails is a `MINOR` finding titled "Comment restates the code",
  also when it was copied from the code around; the other places go into `also_at`. File headers
  and migrations as the document rules them out, as today.
- **Fix agent** (`scripts/agent/prompts/fix.md`): a "Comment restates the code" finding is fixed
  by deleting the comment, keeping only a reason it holds that the code cannot show; it is not
  declined because the code around has the same kind.
- **Planning skill**: headless agents may not edit `.claude/`, so WP1 is the developer's (status
  `not done: manual`), with the exact text below. The develop-issue session or the developer
  commits it on this branch after the agents' run.
- No `/code-review` guidance exists in the project (the command is Claude Code's own), so nothing
  changes there.

## Work packages

### WP1: Planning skill (manual)

- **Status**: not done: manual (headless agents cannot edit `.claude/`)
- **Depends on**: none
- **Files**: `.claude/skills/plan-from-issue/SKILL.md`, `.claude/skills/plan-from-issue/plan-template.md`
- **Steps**:
  - [ ] In `SKILL.md`, section 3, replace the bullet

    ```text
    - **No comments in the plan**: never ask for comments or Javadoc ("with Javadoc in the style of
      …"). The developer agent adds a comment only when it is really needed
      ([code comments](../../../docs/coding-convention/repository-code-comments.md)), and Checkstyle
      asks for the Javadoc it requires. Never plan a comment on a table, a column or a migration, nor a
      comment that explains a design decision the plan already records.
    ```

    with

    ```text
    - **No comments in the plan**: never ask for comments or Javadoc ("with Javadoc in the style of
      …"). The developer agent adds a comment only when a reader would lose something without it
      ([code comments](../../../docs/coding-convention/repository-code-comments.md)), and backend Java
      has no Javadoc (`TAP-J1`). Never plan a comment on a table, a column or a migration, nor a
      comment that explains a design decision the plan already records.
    ```

  - [ ] In `SKILL.md`, "Keep it short: one home for every fact", after the bullet that ends
    "(such a package still names at most 8 files, so split it by module or directory).", add

    ```text
    - a step, a test or a "Docs to update" row that asks for a code comment or Javadoc: a comment is
      never a doc to update, and the developer agent decides on one itself.
    ```

  - [ ] In `plan-template.md`, replace

    ```text
    "None" only with the reason. A doc change is listed here and nowhere else: no work package step
    repeats it.
    ```

    with

    ```text
    "None" only with the reason. A doc change is listed here and nowhere else: no work package step
    repeats it. A code comment or Javadoc is never a doc to update, and no step asks for one.
    ```

  - [ ] `mise run skill:test` and `mise run agent:test` pass (the link check covers the skill)
- **Tests**: `mise run skill:test`, `mise run agent:test`.

### WP2: Developer, review and fix prompts

- **Status**: open
- **Depends on**: none
- **Files**: `scripts/agent/prompts/implement.md`, `scripts/agent/prompts/review.md`, `scripts/agent/prompts/fix.md`
- **Steps**:
  - [ ] Change the three prompts as the Design says
- **Tests**: `mise run agent:test` (the scripts render the prompts; no test asserts their text).

## Tests

Each work package's tests. The behaviour is proved in use: the review of plan 3, the first cleanup,
reports a restating comment as `MINOR` "Comment restates the code" when one is left.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/repository-code-comments.md` | New section "How the agents apply it": the plan never asks for a comment, the developer agent's check of its own diff, the review's `MINOR` finding "Comment restates the code" (also for copied ones), the fix agent deletes it; the `.claude/` skills are edited by hand |
| Text | `docs/agentic-development.md` | The step table (rows 1, 5, 6 and 7) and "Review findings": one phrase each linking that section |

## Out of scope

- The build rules: plan 1. The cleanup: plans 3 to 9.
- The "Comments only when needed" bullets of the other skills (`pr-ready`, `dependabot-fix`,
  `renovate-fix`, `merge-main-into-branch`): still right as they are.
