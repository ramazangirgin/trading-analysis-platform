---
name: plan-from-issue
description: Turn a GitHub issue of this repository into one or more reviewed implementation plans (.plans/<issue>-<slug>.md), each pushed on its own plan/<issue>-<slug> branch, ready for the agent implementation loop. Use when the user asks to plan an issue, e.g. "/plan-from-issue 32" or "plan issue #61".
---

# Plan from issue

Step 1 and 2 of the agentic development flow
([doc/coding-convention/repository-agentic-development.md](../../../doc/coding-convention/repository-agentic-development.md)).
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

| Always | Root `README.md`, `doc/coding-convention/README.md`, `doc/coding-convention/repository-versioning-and-releases.md` |
|---|---|
| Backend (`artifact/backend`) | `doc/coding-convention/backend-*.md` |
| Frontend (`artifact/frontend`) | `doc/coding-convention/frontend-*.md` |
| ta-runner (`artifact/ta-runner`) | `doc/coding-convention/ta-runner-*.md`, `artifact/ta-runner/README.md` if present |
| Runner ↔ backend contract | `docs/event-protocol.md` |
| End-to-end tests (`e2e/`) | `e2e/` README or config, the existing tests |
| Deploy, CI | `deploy/`, `.github/workflows/`, `doc/coding-convention/repository-*.md` |

Then read the existing code the change builds on, so the plan names real classes, files and
patterns. Note which existing tests cover the area.

## 3. Write the plan(s)

Copy [plan-template.md](plan-template.md) to `.plans/<issue>-<slug>.md` (slug: 2-5 lowercase words
from the issue title, hyphenated) and fill in every section.

- **One reviewable pull request per plan.** When the issue would not fit one (roughly: more than
  one part with substantial changes each, or more than ~1000 changed lines), split it into several
  plans `.plans/<issue>-<slug-a>.md`, `.plans/<issue>-<slug-b>.md`, each naming the plans it
  depends on. A plan that depends on another one is implemented after that one is merged.
- **Work packages**: list the files each one creates or changes, so packages without a dependency
  can be implemented in parallel. Every package names its tests.
- **Conventions**: for each affected part, name the conventions that apply and where the new code
  goes according to them (package, folder, layer). Do not propose a new layer, tool or pattern
  without saying so explicitly in "Open questions".
- **Version bump**: always one (`minor` unless the change is breaking, `major`, or a hotfix,
  `patch`).
- **Docs to update**: the README sections and documents whose content the change makes wrong.
- Write in English, in the plain style of the existing documents.

Show the plan(s) to the user and adjust them until the user is content, before step 4.

## 4. Push one branch per plan

For each plan file, after the user agrees:

```sh
scripts/agent/plan-branch.sh .plans/<issue>-<slug>.md
```

It creates `plan/<issue>-<slug>` from `origin/main` with exactly one commit that adds only that
plan file, pushes it, and comments the branch link on the issue. It never touches `main`. Run it
from a clean working tree; the plan file may be untracked.

## 5. Tell the user what is next

Print the branch link(s) and the next steps: review the plan on the branch (edit it there and push,
or ask for changes), then let the agents take it to a pull request ready for review:

```sh
mise run agent:run plan/<issue>-<slug>
```
