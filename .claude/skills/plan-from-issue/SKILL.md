---
name: plan-from-issue
model: claude-opus-5-5
description: Turn a GitHub issue of this repository into one or more reviewed implementation plans (.plans/<issue>-<slug>.md), each pushed on its own plan/<issue>-<slug> branch with a draft pull request labelled plan, reviewed with the user on that pull request and in the session, ready for the agent implementation loop. Use when the user asks to plan an issue, e.g. "/plan-from-issue 32" or "plan issue #61".
---

# Plan from issue

Step 1 and 2 of the agentic development flow
([docs/agentic-development.md](../../../docs/agentic-development.md)).
The plan is what the developer approves and what the developer and review agents are checked
against, so it must be specific enough to implement without guessing, and must follow the
documented architecture and conventions instead of inventing new ones.

Input: an issue number (`$ARGUMENTS`). Ask for it if none was given.

## 1. Read the issue

```sh
gh issue view <n> --json number,title,body,labels,state,comments
```

Read the issues it links to (`#<n>` in the body or comments) the same way, enough to know what is
already done or planned elsewhere. Stop and tell the user if the issue is closed.

## 2. Read the documents of every part it touches

Find the affected parts from the issue and from the code (search for the classes, components and
endpoints it names). Before designing anything, read:

| Always | Root `README.md`, `docs/coding-convention/README.md`, `docs/coding-convention/repository-versioning-and-releases.md` |
|---|---|
| Backend (`artifact/backend`) | `docs/coding-convention/backend-*.md` |
| Frontend (`artifact/frontend`) | `docs/coding-convention/frontend-*.md` |
| ta-runner (`artifact/ta-runner`) | `docs/coding-convention/ta-runner-*.md`, `artifact/ta-runner/README.md` if present |
| Runner ↔ backend contract | `docs/event-protocol.md` |
| End-to-end tests (`e2e/`) | `e2e/` README or config, the existing tests |
| Deploy, CI | `deploy/`, `.github/workflows/`, `docs/coding-convention/repository-*.md` |

Then read the existing code the change builds on, so the plan names real classes, files and
patterns. Note which existing tests cover the area.

## 3. Write the plan(s)

Copy [plan-template.md](plan-template.md) to `.plans/<issue>-<slug>.md` (slug: 2-5 lowercase words
from the issue title, hyphenated) and fill in every section. The template is the plan's structure:
do not read other plans in `.plans/` as models.

- **One reviewable pull request per plan.** When the issue would not fit one (roughly: more than
  one part with substantial changes each, or more than ~1000 changed lines), split it into several
  plans `.plans/<issue>-<slug-a>.md`, `.plans/<issue>-<slug-b>.md`, each naming the plans it
  depends on. A plan that depends on another one is implemented after that one is merged.
- **The issue closes with its last plan.** Fill in "Plan: <n> of <total>" in every plan. The issue
  must stay open until every plan of it is merged: the agent scripts write `Part of #<issue>` into a
  plan's pull request while another `plan/<issue>-*` branch has no merged pull request, and
  `Closes #<issue>` only into the last one. So push every plan of the issue in step 4, never only
  some of them, and never write `Closes #<issue>` into a plan.
- **No comments in the plan**: never ask for comments or Javadoc ("with Javadoc in the style of
  …"). The developer agent adds a comment only when it is really needed
  ([code comments](../../../docs/coding-convention/repository-code-comments.md)), and Checkstyle
  asks for the Javadoc it requires. Never plan a comment on a table, a column or a migration, nor a
  comment that explains a design decision the plan already records.
- **REST endpoints** ([REST endpoints](../../../docs/coding-convention/backend-java-rest-api.md)):
  for every new or changed BFF endpoint, the plan names its success status and return type, and
  where each error is answered. An endpoint that only answers `200` returns its DTO, never
  `ResponseEntity` (that is for `201`, `204` or a varying status). A domain error is thrown and
  answered by `DomainExceptionHandler`: a new domain exception gets an
  `*ExceptionToApiExceptionMapper` and a handler method there. Never plan a `try` / `catch` in a
  delegate that maps an exception.
- **Work packages**: list the files each one creates or changes, so packages without a dependency
  can be implemented in parallel. Every package names its tests.
- **Conventions**: for each affected part, name the conventions that apply and where the new code
  goes according to them (package, folder, layer). Do not propose a new layer, tool or pattern
  without saying so explicitly in "Open questions".
- **Version bump**: always one (`minor` unless the change is breaking, `major`, or a hotfix,
  `patch`).
- **Docs to update**: every document and screenshot the change makes wrong or incomplete; the change
  is documented in the same pull request, never "later" or "separately". Go through:
  - the root `README.md`: the section describing the feature (a new feature gets a paragraph where
    users look for it), the CI table when tests or jobs change, the task list when `mise.toml`
    changes;
  - `docs/` and `docs/coding-convention/` when a convention, tool or flow changes;
    `docs/event-protocol.md` when the runner ↔ backend contract changes; `openapi.json` and the
    frontend's API types when the REST API changes (`mise run api-types`);
  - **screenshots**: the README shows every page (`docs/screenshots/*.png`). A new page or view gets
    a new screenshot, and a page whose look changes (a new button, column, tab, panel) gets its
    screenshot retaken. Name the file, the page and state it shows, and the data it needs. The
    developer agent takes them with `mise run screenshots` (a `e2e/screenshots/<name>.shot.ts` per
    screenshot, against the built jar with a replayed run, 1440×900, dark theme).
- Write in English, in the plain style of the existing documents.

Show the plan(s) to the user. Adjust them while the user asks for changes here, then push them for
review (step 4): the review itself happens on the pull request and in this session (step 5).

## 4. Push one branch and draft pull request per plan

For each plan file:

```sh
scripts/agent/plan-branch.sh .plans/<issue>-<slug>.md
```

It creates `plan/<issue>-<slug>` from `origin/main` with exactly one commit that adds only that
plan file, pushes it, opens a **draft pull request** labelled `plan` (the plan as its only change,
no closing keyword) and comments the pull request's link on the issue. It never touches `main`. Run
it from a clean working tree; the plan file may be untracked. Once pushed, the script removes the
untracked local copy: the branch holds the plan, and a leftover file would make `mise run agent:run`
stop with "the working tree is not clean".

After the last branch is pushed, check `git status --short`: it must be empty. If a plan file is
still there (for example one that was not pushed), compare it with its branch
(`git show origin/plan/<issue>-<slug>:.plans/<issue>-<slug>.md | cmp - .plans/<issue>-<slug>.md`)
and remove it only when it is identical; otherwise tell the user.

## 5. Review the plan with the user

Print each plan pull request's link. The user comments where they prefer: inline on the plan's
lines or as a comment on the pull request, or here in the session. Until the user approves:

1. Collect what is unanswered: the user's answer in the session, and
   `scripts/agent/plan-comments.sh <pr>` (`mise run agent:plan-comments`), which lists the open
   review threads (comment ID, `path:line`, author, text) and the pull request comments and review
   bodies (with their IDs) that no reply of the session names yet.
2. Change the plan on its branch: `git switch plan/<issue>-<slug>` (in a clean checkout), edit
   `.plans/<issue>-<slug>.md`, commit (`Plan: <what changed>`) and push. A comment that asks a
   question gets an answer, and the plan changes when the answer belongs in it.
3. Answer every comment from step 1, with what changed (the commit) or why not:
   `plan-comments.sh --reply <comment id> <file>` on a review thread,
   `plan-comments.sh --comment <pr> <file> <id>...` for the pull request comments and review bodies,
   naming the IDs it answers (the listing prints them). Both mark the reply, so what it answers is
   not listed again; a comment the reply does not name stays listed. Never resolve a thread: that is
   the user's.
4. Show what changed, and ask again.

Open questions in the plan must be answered in it before it can be approved.

## 6. Tell the user what is next

Print the pull request link(s) and the next steps: comment on the plan or ask for changes (step 5),
then approve it by letting the agents take it to a pull request ready for review. The same pull
request carries on: the implementation is pushed to it, and its label changes from `plan` to
`agent`.

```sh
mise run agent:run plan/<issue>-<slug>
```

For a split issue, say that it stays open until the pull request of its last plan is merged, which
closes it (`Closes #<issue>`); the earlier ones only reference it (`Part of #<issue>`).
