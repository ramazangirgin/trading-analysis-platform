# Plan: Domain logic out of the models into domain services

- **Issue**: #237 (Move domain logic out of domain objects into domain services)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The records and enums in `domain.<d>.core.model` become data holders. Access rules, state
transitions, status rules, filter factories, spec normalization and cross-field checks move into
the `service` package of their domain. The package structure document says what a model may hold,
and an ArchUnit rule fails the build when a model class gets a method that decides something.
Nothing changes for the user, and the REST API stays the same.

The plan also carries an unrelated fix that the coordinator asked for: `scripts/agent/dashboard.sh`
no longer fails with "dashboard: not updated".

## Affected parts and their conventions

- Backend: [package structure](../docs/coding-convention/backend-java-package-structure.md)
  (including its "Shared libraries" rule on test fixtures), [Checkstyle](../docs/coding-convention/backend-java-checkstyle.md),
  [REST endpoints](../docs/coding-convention/backend-java-rest-api.md) (no endpoint changes).
- Agent scripts: [agentic development](../docs/agentic-development.md) ("Dashboard").

## Design

**What stays in a model**: record components, null checks, defensive copies, constants, the format
check of an identifier or value type (`AnalysisId`, `ReportKey` with `isValidTicker`, `Username`,
`OwnerId`), the stored key of an enum (`Permission.key`/`fromKey`, `PasswordPolicyViolation.code`),
`newId()`, a masking `toString`, and plain `with*` copies that replace fields without a rule
(`withLoginSucceeded` and `withVisibilityOf` included). Everything that decides something moves.
The new helpers are package-private final classes with static methods, like `AnalysisSpecValidator`
and `DecisionRatingParser`, so no service constructor changes. Catalog has nothing to move.

**Identity and settings**: `User.isLockedAt(Instant)` becomes a static
`LoginLockoutPolicy.isLocked(User, Instant)` (`UserManagementService` does not inject the policy).
`PresetAccess.canSee(Preset)` becomes a private method of `PresetService`, its only user.

**Report**: `Report.hasDecision()` is used only by `ImportExistingRunsUseCase` (orchestration),
which cannot reach a report service, so the check moves there as a private method on
`report.sections()`.

**Analysis access**: `AnalysisAccess` keeps `user`, `allUsers`, `share`. `canSee`, `canManage`,
`canChangeTo`, `allowedVisibilities`, `isOwner` and `Visibility.sharedWithUsers()` move unchanged
into a new `AnalysisAccessPolicy` in `domain.analysis.core.service`. The BFF's
`AnalysisToAnalysisDtoMapper` calls `canManage` and `allowedVisibilities` today, and the BFF may
use inbound ports only. So new in `domain.analysis.core.inbound`: `GetAnalysisRightsUseCase` with
`AnalysisRights rights(Analysis analysis, AnalysisAccess access)`, and the record
`AnalysisRights(boolean canManage, List<Visibility> allowedVisibilities)`, implemented by a
package-private `AnalysisRightsService`. `AnalysesApiDelegateImpl` asks for the rights of each
analysis it maps and passes them as the mapper's `@Context` in place of `AnalysisAccess`.
`AnalysisDto` and `openapi.json` do not change. The fake `GetAnalysisUseCase` of the orchestration
tests checks visibility itself.

**Analysis records**: a new `AnalysisTransitions` in `domain.analysis.core.service` takes from
`Analysis` `queued`, `imported` (with `millis`), `running` and `finished` (still rejecting a
non-terminal status), from the enums `AnalysisStatus.isTerminal()` and `RunOutcome.toStatus()`,
and from `AnalysisSpec`'s constructor the strip, the upper/lower case and the sorted, distinct
analysts, as `normalized(AnalysisSpec)`. `AnalysisService.start` normalizes before
`AnalysisSpecValidator.validate`, and `imported` normalizes its spec, so a stored spec is as
normalized as today. `Analysis.UNKNOWN` and `INCOMPLETE_REPORT` stay (orchestration uses
`UNKNOWN`). `AnalysisFilter.partial()` moves into `PartialAnalysisService`, `ownedBy` and
`publicOfOthersThan` into `AnalysisListService`. `ExternalAnalysisService.register` checks first
what the constructors of `ExternalAnalysis` and `ExternalRun` check today (a run history record has
its run, the run's status is terminal, `debateRounds` at least 1; still
`IllegalArgumentException`) and computes the `ref` itself. The factories `reportFiles` and
`runHistory` become private methods of `ImportExistingRunsUseCase`, their only user.

**Test fixture**: tests outside the analysis core (its adapter, orchestration, `:backend`) build
analyses with `Analysis.queued(…).running(…).finished(…)` and cannot reach `AnalysisTransitions`.
The analysis core gets `java-test-fixtures` (as `library:persistence` has) with `TestAnalyses`
(package `domain.analysis.core.model`): `queued`, `running` and `finished`, built with the
canonical constructor. Core tests use `AnalysisTransitions` directly.

**ArchUnit**: a new rule `domain_models_are_data` in `ArchitectureTest`: a public method of a
top-level class in `domain.*.core.model` is a record accessor, `values`/`valueOf`, `toString`,
`equals`, `hashCode`, an instance `with*` method returning its own class, or one of a named list:
`newId`, `Permission.key`/`fromKey`, `PasswordPolicyViolation.code`, `ReportKey.isValidTicker`.

**Dashboard fix**: `dashboard.sh` removes lib.sh's scratch directory (`rmdir "$AGENT_TMP"`) and never
points `AGENT_TMP` elsewhere, so lib.sh's `retry` (`mktemp "$AGENT_TMP/retry.XXXXXX"`) fails on
every `gh` call. After `work=$(mktemp -d …)` it sets `AGENT_TMP=$work`, as lib.sh does with
`AGENT_TMP=$AGENT_STATE`; `cleanup()` already removes `$work`.

Model methods are removed only in WP13 to WP16, so every module compiles between packages.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: Lock check into LoginLockoutPolicy

- **Status**: open
- **Depends on**: none
- **Files**: in `artifact/backend/domain/identity/core/src/`:
  `main/java/…/identity/core/model/User.java`, `main/java/…/identity/core/service/LoginLockoutPolicy.java`,
  `main/java/…/identity/core/service/AuthenticationService.java`,
  `main/java/…/identity/core/service/UserManagementService.java`,
  `test/java/…/identity/core/model/UserTest.java`, `test/java/…/identity/core/service/LoginLockoutPolicyTest.java`
- **Tests**: the `isLockedAt` cases of `UserTest` move to `LoginLockoutPolicyTest`;
  `AuthenticationServiceTest` and `UserManagementServiceTest` pass unchanged.

### WP2: Preset visibility rule into PresetService

- **Status**: open
- **Depends on**: none
- **Files**: `artifact/backend/domain/settings/core/src/main/java/…/settings/core/model/PresetAccess.java`,
  `artifact/backend/domain/settings/core/src/main/java/…/settings/core/service/PresetService.java`
- **Tests**: `PresetServiceTest` passes unchanged (own, other and unowned presets, with and without
  `allUsers`).

### WP3: Dashboard keeps a scratch directory

- **Status**: open
- **Depends on**: none
- **Files**: `scripts/agent/dashboard.sh`, `scripts/agent/test_dashboard.py`
- **Steps**:
  - [ ] Commit this package on its own, apart from the backend changes.
- **Tests**: a new test in `test_dashboard.py` runs `dashboard.sh start <n>` with `AGENT_DASHBOARD=1`
  in a throwaway git repository, with a fake `gh` on `PATH` (as `test_lib.py` does) that answers the
  calls of `start`; it asserts the run logs neither `mkstemp` nor "not updated", and fails without
  the fix. `mise run agent:test` runs it.

### WP4: Analysis rules as domain services

- **Status**: open
- **Depends on**: none
- **Files**: in `artifact/backend/domain/analysis/core/src/`:
  `main/java/…/analysis/core/service/AnalysisAccessPolicy.java` (new),
  `main/java/…/analysis/core/service/AnalysisTransitions.java` (new),
  `test/java/…/analysis/core/service/AnalysisAccessPolicyTest.java` (new, from `AnalysisAccessTest`),
  `test/java/…/analysis/core/model/AnalysisAccessTest.java` (deleted),
  `test/java/…/analysis/core/service/AnalysisTransitionsTest.java` (new)
- **Tests**: `AnalysisAccessPolicyTest` holds every case of `AnalysisAccessTest`;
  `AnalysisTransitionsTest`: `finished` rejects a non-terminal status, `normalized` cases, `imported`
  cuts times to milliseconds.

### WP5: Rights use case

- **Status**: open
- **Depends on**: WP4
- **Files**: in `artifact/backend/domain/analysis/core/src/`:
  `main/java/…/analysis/core/inbound/GetAnalysisRightsUseCase.java`,
  `main/java/…/analysis/core/inbound/AnalysisRights.java`,
  `main/java/…/analysis/core/service/AnalysisRightsService.java`,
  `test/java/…/analysis/core/service/AnalysisRightsServiceTest.java`
- **Tests**: `AnalysisRightsServiceTest`: owner with and without `analysis:share`, another user, an
  admin on someone else's partial analysis.

### WP6: BFF maps the rights

- **Status**: open
- **Depends on**: WP5
- **Files**: in `artifact/backend/bff/impl/src/main/java/…/bff/delegate/impl/`:
  `AnalysesApiDelegateImpl.java`, `mapper/AnalysisToAnalysisDtoMapper.java`
- **Tests**: `AnalysisSharingIntegrationTest`, `PartialPreviewIntegrationTest` and
  `AnalysisOwnershipIntegrationTest` pass (`canManage`, `canChangeVisibility`,
  `allowedVisibilities` per caller).

### WP7: Test fixture

- **Status**: open
- **Depends on**: none
- **Files**: `artifact/backend/domain/analysis/core/build.gradle.kts`,
  `artifact/backend/domain/analysis/core/src/testFixtures/java/…/analysis/core/model/TestAnalyses.java`,
  `artifact/backend/domain/analysis/adapter/build.gradle.kts`, `artifact/backend/orchestration/build.gradle.kts`,
  `artifact/backend/build.gradle.kts`
- **Tests**: used by WP10 to WP12.

### WP8: Analysis services use the rules

- **Status**: open
- **Depends on**: WP4
- **Files**: in `artifact/backend/domain/analysis/core/src/main/java/…/analysis/core/service/`:
  `AnalysisService.java`, `AnalysisVisibilityService.java`, `AnalysisLogService.java`,
  `AnalysisEventHub.java`, `ExternalAnalysisService.java`, `AnalysisListService.java`,
  `PartialAnalysisService.java`
- **Tests**: the core tests pass unchanged.

### WP9: Analysis core tests use the transitions

- **Status**: open
- **Depends on**: WP4
- **Files**: in `artifact/backend/domain/analysis/core/src/test/java/…/analysis/core/service/`:
  `Fakes.java`, `AnalysisServiceTest.java`, `AnalysisVisibilityServiceTest.java`,
  `AnalysisEventHubTest.java`, `PartialAnalysisServiceTest.java`, `ExternalAnalysisServiceTest.java`
- **Tests**: `ExternalAnalysisServiceTest` adds that each invalid external run is rejected;
  `:backend:domain:analysis:core:test` passes.

### WP10: Analysis adapter tests

- **Status**: open
- **Depends on**: WP7
- **Files**: in `artifact/backend/domain/analysis/adapter/src/test/java/…/analysis/adapter/`:
  `persistence/JpaAnalysisRepositoryAdapterTest.java`, `persistence/JpaPartialVisibilityTest.java`,
  `runner/DockerRunnerAdapterTest.java`
- **Tests**: they pass with `TestAnalyses`.

### WP11: Orchestration

- **Status**: open
- **Depends on**: WP7
- **Files**: `artifact/backend/orchestration/src/main/java/…/orchestration/report/inbound/ImportExistingRunsUseCase.java`,
  and in `artifact/backend/orchestration/src/test/java/…/orchestration/`:
  `report/inbound/ImportExistingRunsUseCaseTest.java`, `report/inbound/GetAnalysisReportUseCaseTest.java`,
  `report/inbound/GetAnalysisPricesUseCaseTest.java`, `preview/inbound/GetAnalysisPreviewUseCaseTest.java`,
  `preview/inbound/ListAnalysisPreviewsUseCaseTest.java`, `analysisstart/inbound/StartAnalysisWithDefaultsUseCaseTest.java`
- **Tests**: `:backend:orchestration:test`; `ImportExistingRunsUseCaseTest` proves the imports are
  the same (an unsettled report without a decision is still skipped).

### WP12: Integration tests

- **Status**: open
- **Depends on**: WP7
- **Files**: in `artifact/backend/src/test/java/…/`: `PartialPreviewConfigurationIntegrationTest.java`,
  `PartialPreviewIntegrationTest.java`, `AnalysisOwnershipIntegrationTest.java`,
  `AnalysisSharingIntegrationTest.java`
- **Tests**: they pass with `TestAnalyses`.

### WP13: Report without hasDecision

- **Status**: open
- **Depends on**: WP11
- **Files**: `artifact/backend/domain/report/core/src/main/java/…/report/core/model/Report.java`,
  `artifact/backend/domain/report/core/src/test/java/…/report/core/service/ReportServiceTest.java`
- **Tests**: `ReportServiceTest` asserts on the sections instead of `hasDecision()`.

### WP14: Analysis access types lose the rules

- **Status**: open
- **Depends on**: WP6, WP8, WP11
- **Files**: in `artifact/backend/domain/analysis/core/src/main/java/…/analysis/core/model/`:
  `AnalysisAccess.java`, `Visibility.java`
- **Tests**: `mise run build`.

### WP15: Analysis records lose the rules

- **Status**: open
- **Depends on**: WP8, WP9, WP10, WP11, WP12
- **Files**: in `artifact/backend/domain/analysis/core/src/main/java/…/analysis/core/model/`:
  `Analysis.java`, `AnalysisStatus.java`, `RunOutcome.java`, `AnalysisFilter.java`,
  `AnalysisSpec.java`, `ExternalAnalysis.java`, `ExternalRun.java`
- **Tests**: `mise run build`.

### WP16: The rule, checked

- **Status**: open
- **Depends on**: WP1, WP2, WP13, WP14, WP15
- **Files**: `artifact/backend/src/test/java/…/ArchitectureTest.java`
- **Tests**: `ArchitectureTest` passes; adding a non-`with` public method to a model record makes
  `domain_models_are_data` fail (checked once by hand).

## Tests

Each work package's tests; `mise run build` and `mise run agent:test`.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Package layout" (`model/`) and "Where each class kind goes" | A model holds data only (what may stay, as in "Design"); rules go into the domain's `service` package; a row for `domain_models_are_data` |
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Shared libraries" | Name the analysis core's test fixture (`TestAnalyses`) beside the persistence library's |

The dashboard fix needs no doc change: it restores what "Dashboard" in `docs/agentic-development.md`
already describes.

## Out of scope

- Orchestration `model` types: the issue covers the domain cores.
- The ticker pattern duplicated in `AnalysisSpecValidator` and `ReportKey`.

## Open questions

- Plain `with*` copies stay in the records (Java has no `with` expression yet, and removing them
  would turn every call into a full constructor call). Should they move too?
- Format checks of identifiers stay in the compact constructors, so no invalid `AnalysisId` or
  `ReportKey` (both are directory names) can exist. Agreed?
- An invalid external run now fails at its `register` call instead of when the import builds it, so
  runs registered before it in the same import stay. No run history entry can be invalid today
  (its status is always terminal). Agreed?
