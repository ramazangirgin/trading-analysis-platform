# Plan: <short title>

- **Issue**: #<issue> (<issue title>)
- **Plan**: <n> of <total> for this issue; depends on: <plan names, or "none">
- **Version bump**: <major | minor | patch> (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

<One paragraph: what is different for the user (or the developer) when this pull request is merged.>

## Affected parts and their conventions

<One line per part: the convention documents to follow, as links. No rule is restated here.>

## Design

<Only what the documents and the code do not show: the decisions, each with the reason, the new
types and where they go, and the existing code they build on. State a fact once: the work packages
do not repeat it, and nothing here restates a convention. Not the code itself.>

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: <name>

- **Status**: open
- **Depends on**: none
- **Files**: <paths created or changed>
- **Steps**:
  - [ ] <a step the name does not already say>
- **Tests**: <tests added or changed, and what they prove>

## Tests

<Only what no work package covers: how the issue's "done when" is proved. "Each work package's tests" when that is all.>

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | <docs/<topic>.md section "…", docs/coding-convention/<doc>.md, …> | <what to add or correct, in one phrase> |
| Screenshot | <docs/screenshots/<name>.png, new or retaken> | <the page and state it shows, the data it needs> |

"None" only with the reason. A doc change is listed here and nowhere else: no work package step
repeats it.

## Out of scope

- <What this plan deliberately leaves out, and where it goes instead.>

## Open questions

- <Questions for the developer reviewing the plan. Delete the section when there are none.>
