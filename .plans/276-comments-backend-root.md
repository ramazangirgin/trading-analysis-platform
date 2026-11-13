# Plan: Comments cleanup: backend root module and leftovers

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 9 of 9 for this issue; depends on: plans 1 to 8 (`276-comment-rules` ... `276-comments-bff`)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The Javadoc and `//` comments of the root module (`artifact/backend/src`) follow
[repository-code-comments.md](../docs/coding-convention/repository-code-comments.md): what a reader
would lose stays, as a `//` comment, and the rest is gone. The plan's temporary filter is removed,
so `TAP-J1` and `TAP-C1` report any new Javadoc or restating comment in it. No temporary filter is left.

## Affected parts and their conventions

- Backend: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md),
  [backend-java-checkstyle.md, "Temporary filters (#276)"](../docs/coding-convention/backend-java-checkstyle.md).

## Design

- Every Javadoc and `//` comment of the listed files is deleted, kept, or (the reason a Javadoc
  held) rewritten as a `//` comment, as `repository-code-comments.md` says. Nothing else changes,
  except an import that only a deleted Javadoc's `{@link}` used (`UnusedImports`).
- The files are those of the modules that held a Javadoc or a `//` comment on `main` at
  `753c8699`. A path in a work package is relative to the Java package root of its module (named
  in the work package's title), in `src/main/java` unless marked `(test)` or `(testFixtures)`:
  - the root module (`artifact/backend/src`): `artifact/backend/src/<set>/java/tr/girgin/backend/trading/analysis/platform/`
- The last package deletes the block `Temporary (#276, plan 9)` from `checkstyle.xml`; Checkstyle
  then reports what is left in the modules, also in files added since, and that package fixes it.
- The block of this plan also holds `TAP-C1`'s filter: the root module's tests are where
  `TAP-C1` reported in plan 1 (section headings such as `// --- BFF ---` above a rule whose name
  holds the same words). Such a heading is deleted, or rewritten so it says what the name does not.
- After this plan no temporary filter is left: the last package also fixes a finding in any
  module (a Javadoc added since another cleanup plan was merged).

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: root module: root package (main, tests), part 1 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `TradingPlatformApplication.java`, `AnalysesApiIntegrationTest.java` (test), `AnalysisDefaultsApiIntegrationTest.java` (test), `AnalysisOwnershipIntegrationTest.java` (test), `AnalysisSharingIntegrationTest.java` (test), `ArchitectureTest.java` (test), `AuthApiIntegrationTest.java` (test), `DataOnlyCondition.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:test` and `mise run check` pass.

### WP2: root module: root package (tests), part 2 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `DoNotIncludeTestFixtures.java` (test), `EndpointPermissionsIntegrationTest.java` (test), `FirstAdminConfiguredPasswordIntegrationTest.java` (test), `FirstAdminIntegrationTest.java` (test), `FirstAdminWeakPasswordIntegrationTest.java` (test), `LoginLockoutIntegrationTest.java` (test), `OrchestrationArchitectureTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:test` and `mise run check` pass.

### WP3: root module: root package (tests), part 3 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `PartialPreviewConfigurationIntegrationTest.java` (test), `PartialPreviewIntegrationTest.java` (test), `PasswordChangeIntegrationTest.java` (test), `PersistenceArchitectureTest.java` (test), `PresetOwnershipIntegrationTest.java` (test), `PresetsApiIntegrationTest.java` (test), `RolePermissionsIntegrationTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:test` and `mise run check` pass.

### WP4: root module: root package (tests), part 4 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `RolesApiIntegrationTest.java` (test), `TestPlatformHome.java` (test), `TestSession.java` (test), `TradingPlatformApplicationTests.java` (test), `UserLifecycleIntegrationTest.java` (test), `UsersApiIntegrationTest.java` (test), `UsersApiTestBase.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:test` and `mise run check` pass.

### WP5: Remove the temporary filter

- **Status**: open
- **Depends on**: WP1–WP4
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Delete the block `Temporary (#276, plan 9)`, and fix what Checkstyle then reports in the modules
  - [ ] Fix every finding of `TAP-J1` and `TAP-C1` that `mise run build` reports in another module
- **Tests**: `mise run build` green with no temporary filter left; the Checkstyle tasks of the modules report nothing.

## Tests

Each work package's tests; the last one proves that the rules hold in the modules without a filter.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Temporary filters (#276)" | The paragraph goes, with every mention of the temporary filters |
| Text | `docs/coding-convention/repository-code-comments.md` "How it is checked" | The link to the temporary filters goes |

## Out of scope

- The triage of the other modules: plans 3 to 8. This plan only fixes what the rules report there.
- New comments in these modules after the merge: the agents' comments step (plan 2) and the rules.
