# Plan: <short title>

- **Issue**: #<issue> (<issue title>)
- **Plan**: <n> of <total> for this issue; depends on: <plan names, or "none">
- **Version bump**: <major | minor | patch> (doc/coding-convention/repository-versioning-and-releases.md)

## Goal

<One paragraph: what is different for the user (or the developer) when this pull request is merged.>

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| <backend / frontend / ta-runner / e2e / deploy / CI / docs> | <the documents under doc/coding-convention/ and the rules from them this change must follow> |

## Design

<How it works: the classes, components, endpoints, events and data involved, and where each goes
according to the conventions above. Name the existing code it builds on. Keep it high level: enough
for a developer to implement it without guessing, not the code itself.>

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: <name>

- **Depends on**: none
- **Files**: <paths created or changed>
- **Steps**:
  - [ ] <step>
- **Tests**: <tests added or changed, and what they prove>

### WP2: <name>

- **Depends on**: WP1
- **Files**: ...
- **Steps**:
  - [ ] ...
- **Tests**: ...

## Tests

<The tests as a whole: unit, integration, frontend, e2e. Which check proves the issue's "done when".>

## Docs to update

Every document and screenshot the change makes wrong or incomplete, so the change is documented in
the same pull request. "None" only with the reason.

| What | Where | Change |
|---|---|---|
| Text | <README.md section "…", doc/<doc>.md, doc/coding-convention/<doc>.md, docs/event-protocol.md, …> | <what to add or correct> |
| Screenshot | <docs/screenshots/<name>.png, new or retaken> | <the page and state it shows, the data it needs, the README section that shows it> |

## Out of scope

- <What this plan deliberately leaves out, and where it goes instead (another plan, an issue).>

## Open questions

- <Questions for the developer reviewing the plan. Delete the section when there are none.>
