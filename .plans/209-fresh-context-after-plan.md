# Plan: develop-issue starts implementing with a fresh context after the plan

- **Issue**: #209 (develop-issue: start implementing with a fresh context after the plan)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

In a `/develop-issue` run, the session that starts the implementation no longer carries what the
plan was written from (issue, README, convention documents, source files). The plan is written, and
changed after review comments, by a subagent that returns only the plan's path and a short summary.
After the approval the skill records it durably, pushes the plan branch and ends with "run `/clear`,
then `/develop-issue <n>`"; the run that follows finds the recorded approval, does not ask for it
again and goes straight to the implementation. The context at the first step after the approval is
the skill, the plan and the approval: well under 100k tokens.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Agent scripts (`scripts/agent/`, Python and Bash) | `docs/agentic-development.md`; Python tests next to the helpers (`test_*.py`, run by `mise run agent:test`); `repository-code-comments.md` (no comments that restate code) |
| Skills (`.claude/skills/develop-issue`, `.claude/skills/plan-from-issue`) | Agents may not edit files under `.claude/`: WP3 is done by hand (see Design) |
| Docs | `docs/agentic-development.md`; `repository-versioning-and-releases.md` (every pull request raises the version) |

## Design

Both options of the issue are combined; each has its own job.

**1. The plan is written in a subagent.** `develop-issue` step 2 no longer invokes `plan-from-issue`
in the session. It starts one `Agent` (general-purpose) with a prompt that says: in the issue's
folder, follow `.claude/skills/plan-from-issue/SKILL.md` sections 1 to 3 for issue `<n>`, push each
plan with `plan-branch.sh` (section 4), and return only each plan's path, branch, pull request link
and a summary of at most 10 lines (goal, work packages, open questions). The session reads the plan
file from the folder or the branch to show it, which is the only plan-related reading it does. The
review loop stays in the session (the developer's answers, `plan-comments.sh <pr>`, the question
with `AskUserQuestion`), but each change round hands the comments to a subagent that changes the plan
on its branch, commits `Plan: …`, pushes, answers every comment with `plan-comments.sh --reply` /
`--comment`, and returns what changed in a few lines. `plan-from-issue` itself stays usable alone in
a session; it gains one sentence saying that `develop-issue` runs it in a subagent.

**2. A recorded approval, and a fresh context after it.** On the approval, `develop-issue` records
the plan step (`progress.sh begin/end plan`, unchanged, with the change-round count), pushes the
plan branch, and posts the approval on the plan's pull request as a comment that starts with a
hidden marker `<!-- plan-approved sha=<head sha of the plan branch> -->`. Then it ends with the
message "approved; run `/clear`, then `/develop-issue <n>`" and starts nothing else. The approval is
on the pull request, so it survives `/clear`, a new machine and a new session, and it is tied to the
commit that was approved: a later plan commit makes it stale and the skill asks again.

`scripts/agent/plan-comments.sh` gets two options, implemented in `plan_comments.py`
(the module that already reads the pull request's comments and recognises the session's own
`plan-reply` markers):

- `plan-comments.sh --approve <pr | branch>`: posts the marker comment for the branch's current head.
  It refuses when the head is not pushed (local commits ahead of `origin/<branch>`).
- `plan-comments.sh --approved <pr | branch>`: exit 0 and print the sha when the newest
  `plan-approved` comment of the agent's login names the branch's current head sha, exit 1 otherwise.
  Only the agent login's comments count, as for the `plan-reply` markers.

`develop-issue` step 0 uses `--approved` for a plan branch whose pull request is an open draft
labelled `plan`: approved means "go on to step 3's switch back to `origin/$base`, then step 4" with
no `AskUserQuestion`; not approved keeps today's question ("Use the existing plan / Write it again
/ Stop here") and the approval question. A pull request labelled `agent` or ready for review is
unchanged (step 4 and step 6).

**3. Skills are edited by hand.** `.claude/skills/develop-issue/SKILL.md` (steps 0, 2, 3, the
"One approval" and "The issue's own folder" sections: the subagent, the `--approve` call, the end
message, the `--approved` check) and the one sentence in `.claude/skills/plan-from-issue/SKILL.md`
are the developer's to change, because the agents may not edit `.claude/`. The plan item comes back
"not done" and the developer agent's final comment must list it; the exact text is in WP3 so it can
be applied without guessing.

## Work packages

### WP1: Approval marker in the plan-comment helper

- **Depends on**: none
- **Status**: done
- **Files**: `scripts/agent/plan_comments.py`, `scripts/agent/plan-comments.sh`,
  `scripts/agent/test_plan_comments.py`
- **Steps**:
  - [x] Add the marker format `<!-- plan-approved sha=<sha> -->` and a function that finds the newest
    marker comment of the agent login and returns its sha.
  - [x] Add `--approve <pr | branch>`: resolve the branch and its pushed head, refuse when ahead of
    origin, post the comment (marker plus one visible line: "Plan approved at `<short sha>`").
  - [x] Add `--approved <pr | branch>`: print the sha and exit 0 when it equals the head, exit 1
    otherwise; update the script's usage text.
- **Tests**: Python tests in `test_plan_comments.py`: no marker, marker for the head, marker for an
  older sha (stale), a marker from another login ignored, newest marker wins, malformed marker
  ignored.

### WP2: Documentation

- **Depends on**: WP1
- **Status**: done
- **Files**: `docs/agentic-development.md`
- **Steps**:
  - [x] In "From Claude Code, with one approval: the plan": the plan is written and changed in a
    subagent that returns the path and a summary; after the approval the session ends with "run
    `/clear`, then `/develop-issue <n>`"; the approval is the `plan-approved` comment on the plan's
    pull request, tied to the plan's head commit; a later run does not ask again, a changed plan
    does.
  - [x] In "Review the plan" (walkthrough step 3/4): mention `plan-comments.sh --approve` and
    `--approved`.
  - [x] Add the context numbers' purpose in one sentence: why the implementation starts fresh.
- **Tests**: `mise run check`.

### WP3: Skills (by hand)

- **Depends on**: WP1
- **Status**: not done: agents may not edit files under `.claude/`; the developer applies it by hand
- **Files**: `.claude/skills/develop-issue/SKILL.md`, `.claude/skills/plan-from-issue/SKILL.md`
- **Steps**:
  - [ ] `develop-issue` step 0: for a draft labelled `plan`, run `plan-comments.sh --approved <pr>`;
    approved skips the questions of step 2.
  - [ ] `develop-issue` step 2: plan writing and each change round in a subagent (prompts as in
    Design 1); the session shows the plan from its file and keeps the question and the loop.
  - [ ] `develop-issue` step 3: after recording the plan step and pushing, run
    `plan-comments.sh --approve <pr>` and end with the `/clear` message; the run (step 4) starts in
    the next session.
  - [ ] `plan-from-issue`: one sentence that `develop-issue` runs sections 1 to 4 in a subagent.
- **Tests**: none automated. Checked by a `/develop-issue` run on a small issue: the transcript's
  context at the first step after the approval is under 100k tokens, and `/develop-issue <n>` after
  `/clear` goes to step 4 without a question.

## Tests

`mise run agent:test` covers WP1. The acceptance criteria on the session's context and on the
repeated approval are checked in a real run (WP3), not in CI. `mise run check` for the docs.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/agentic-development.md`, "From Claude Code, with one approval: the plan" | Subagent planning, recorded approval, `/clear` then `/develop-issue <n>` |
| Text | `docs/agentic-development.md`, walkthrough step 3 | `plan-comments.sh --approve` / `--approved` |
| Text | `.claude/skills/develop-issue/SKILL.md`, `.claude/skills/plan-from-issue/SKILL.md` | WP3, by hand |
| Screenshot | none | No page changes |

The root `README.md` does not describe `develop-issue`'s steps, so it is unchanged.

## Out of scope

- One session per issue (#210) and Sonnet after the plan (#211).
- Token reductions elsewhere in the run (#208).
- Starting the implementation automatically after the approval, without `/clear`: a skill cannot
  clear its own context.

## Decisions

- The approval is a pull request comment tied to the plan's head sha, not a label: a changed plan
  invalidates it without anything to remove.
