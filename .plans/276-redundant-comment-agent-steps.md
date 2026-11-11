# Plan: A comments step for the planning, developer and review agents

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 2 of 9 for this issue; depends on: `.plans/276-redundant-comment-checks.md`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every stage that writes or judges code asks of each comment: "would a reader lose something if this
comment were deleted?" The developer agent ends its package with that check of its own diff, the
review agent reports a comment or Javadoc that fails it as a `MINOR` finding (also where no rule of
plan 1 catches it), the fix agent deletes it, and the planning skill checks that a plan asks for no
comment. `repository-code-comments.md` states the test, with an example to delete and one to keep,
and what the review flags.

## Affected parts and their conventions

- Agent prompts: [agentic-development.md](../docs/agentic-development.md)
- [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md)

## Design

- `scripts/agent/prompts/implement.md`: the "Comments follow …" rule names the test; the commits
  section gets a last step before the last commit: read the comment and Javadoc lines that
  `git diff {{BASE}}...HEAD` adds or changes, apply the test, delete what fails (a Javadoc Checkstyle
  does not require, too).
- `scripts/agent/prompts/review.md`: the comments part of "Conventions" says that `TAP-J*`,
  `TAP-C*` and ruff `ERA` check part of it, and that the review applies the test to every comment and
  Javadoc the diff adds or changes, in every language: one that fails is `MINOR` ("A comment or
  Javadoc that restates the code"), repeats in `also_at`; a comment the change makes wrong (the code
  under it no longer does what it says) is `MAJOR`, since it misleads.
- `scripts/agent/prompts/fix.md`: a finding about a comment that restates the code is fixed by
  deleting it (a required Javadoc: shortening it to its one sentence), never by rewording it, and
  never declined because the code around has the same kind.
- The project has no `/code-review` guidance of its own (no skill under `.claude/skills/`), so there
  is nothing to change there.
- **Manual, by the developer on this branch** (headless agents cannot edit `.claude/`):
  - `.claude/skills/plan-from-issue/SKILL.md`, "Keep it short", the list of what to delete before
    step 4: add "a request for a comment or Javadoc, anywhere in the plan; a comment is never a doc
    to update";
  - `.claude/skills/plan-from-issue/plan-template.md`, under the "Docs to update" table: "Never a
    comment or Javadoc: the plan asks for none (repository-code-comments.md)."
  The implementing agent does not touch them; the final comment of the pull request lists them as
  open for the developer.

## Work packages

### WP1: Prompts

- **Status**: open
- **Depends on**: none
- **Files**: `scripts/agent/prompts/implement.md`, `scripts/agent/prompts/review.md`,
  `scripts/agent/prompts/fix.md`
- **Steps**:
  - [ ] The three prompt changes of Design
- **Tests**: `mise run agent:test` and `mise run check` pass (no test reads the prompts)

## Tests

Each work package's tests. The prompts take effect on the next agent run; the developer checks on the
first cleanup plan (3 to 8) that the review reports a restating comment as `MINOR`.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/repository-code-comments.md`, new "The test" at the top, "Examples", "How it is checked" | the one-line test; to delete: `// Save the user` above `userRepositoryPort.save(user);`, and `/** An analysis ID is also the run directory name, so its format is strict. */` on a type that no longer checks it (wrong); to keep: `# File first: once the platform has read a line from stdout, a reconnecting client replaying events.jsonl must already find it there.` (`ta_runner/protocol/events.py`) and a suppression's reason; what the review flags (`MINOR`, a wrong comment `MAJOR`) and that the fix agent deletes |
| Text | `docs/agentic-development.md` table "The flow" rows 1, 5 and 6, and "Review findings" | the plan asks for no comment; the developer agent checks its diff's comments; the review's comment findings and their priorities |

## Out of scope

- The rules themselves: plan 1. The existing comments: plans 3 to 9.
- The two `.claude/skills/plan-from-issue/` edits are the developer's, by hand (Design).
