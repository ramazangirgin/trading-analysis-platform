# Plan: Clean up the comments of the backend root module

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 9 of 9 for this issue; depends on: `.plans/276-redundant-comment-checks.md`, and plans 3 to 8 (`.plans/276-comments-cleanup-*.md`): this is the last plan of the issue
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The comments and Javadoc of the backend root module pass the test of [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md), and the rules `TAP-J1` to `TAP-J3`, `TAP-C1` and `TAP-C2` apply to it: the pending block for the root module (`artifact/backend/src/`) that plan 1 put into `config/checkstyle/checkstyle.xml` is gone, so the build fails on any redundant Javadoc or comment the rules can see there. With it the last block goes, and #276 is done.

## Affected parts and their conventions

- Backend: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) ("Existing comments" is the triage), [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md) (`TAP-J*`, `TAP-C*`)

## Design

- The packages name every file of these modules that held a Javadoc or a comment on `main` at `d0e86794` (not `package-info.java`, which keeps its Javadoc). A path is relative to `artifact/backend/<module>/src/<source set>/java/tr/girgin/backend/trading/analysis/platform/<module package>/` and starts with the source set (`main/`, `testFixtures/`, `test/`); the module package of `bff/api` is `bff/controller/api`, of `bff/impl` `bff/delegate/impl`, of the root module empty, otherwise the module path. A file renamed since by another pull request is triaged under its new name.
- Each comment and Javadoc is triaged as "Existing comments" says: kept, shortened to the one sentence `TAP-J3` allows (an error case into `@throws`), turned into a `//` comment (a why in a Javadoc outside a port), or deleted. Only comments change: no code, no test, no migration.
- The last package removes the block and fixes what `mise run check` then still reports in these modules, also in a file no package names.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: root module (1/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend`
- **Files**: `main/TradingPlatformApplication.java`, `test/AnalysesApiIntegrationTest.java`, `test/AnalysisDefaultsApiIntegrationTest.java`, `test/AnalysisOwnershipIntegrationTest.java`, `test/AnalysisSharingIntegrationTest.java`, `test/AnalysisStartDefaultsIntegrationTest.java`, `test/ArchitectureTest.java`, `test/AuthApiIntegrationTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:test` passes unchanged

### WP2: root module (2/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend`
- **Files**: `test/DataOnlyCondition.java`, `test/DoNotIncludeTestFixtures.java`, `test/EndpointPermissionsIntegrationTest.java`, `test/FirstAdminConfiguredPasswordIntegrationTest.java`, `test/FirstAdminIntegrationTest.java`, `test/FirstAdminWeakPasswordIntegrationTest.java`, `test/LoginLockoutIntegrationTest.java`, `test/PartialPreviewConfigurationIntegrationTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:test` passes unchanged

### WP3: root module (3/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend`
- **Files**: `test/PartialPreviewIntegrationTest.java`, `test/PasswordChangeIntegrationTest.java`, `test/PersistenceArchitectureTest.java`, `test/PresetOwnershipIntegrationTest.java`, `test/PresetsApiIntegrationTest.java`, `test/RolePermissionsIntegrationTest.java`, `test/RolesApiIntegrationTest.java`, `test/TestPlatformHome.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:test` passes unchanged

### WP4: root module (4/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend`
- **Files**: `test/TestSession.java`, `test/TradingPlatformApplicationTests.java`, `test/UserLifecycleIntegrationTest.java`, `test/UsersApiIntegrationTest.java`, `test/UsersApiTestBase.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:test` passes unchanged

### WP5: Rules on for these modules

- **Status**: open
- **Depends on**: WP1-WP4
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Remove the pending block for the root module (`artifact/backend/src/`)
  - [ ] Fix every `TAP-J*` / `TAP-C*` finding left
- **Tests**: `mise run check`; `mise run gradle :backend:test`

## Tests

Each work package's tests. The proof that the rules now apply: a deliberate Javadoc on a private method in one of the modules fails `mise run check` (not committed).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md`, the paragraph on the pending cleanup blocks (plan 1) | removed: no module is exempt any more |
| Text | `docs/coding-convention/repository-code-comments.md` | any mention of modules not cleaned yet removed |

## Out of scope

- The other modules: the other `.plans/276-comments-cleanup-*.md` plans.
- SQL migrations (a merged one is never edited), Python, shell, TypeScript and Vue comments.
