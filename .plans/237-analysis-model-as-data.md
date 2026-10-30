# Plan: Analysis records as data, and the rule enforced

- **Issue**: #237 (Move domain logic out of domain objects into domain services)
- **Plan**: 3 of 3 for this issue; depends on: `237-identity-report-settings-services`, `237-analysis-access-policy`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The analysis model's state transitions, status rules, filter factories, spec normalization and the
checks of imported runs move into the analysis `service` package. With this last domain done, the
package structure document says what a domain model may hold, and an ArchUnit rule fails the build
when a model class gets a method that decides something. Nothing changes for the user.

## Affected parts and their conventions

- Backend: [package structure](../docs/coding-convention/backend-java-package-structure.md)
  (including its "Shared libraries" rule on test fixtures), [Checkstyle](../docs/coding-convention/backend-java-checkstyle.md).

## Design

What stays in a model is set in plan 1's "Design"; this plan writes it down and enforces it.

- **`AnalysisTransitions`**, a new package-private final class (static methods) in
  `domain.analysis.core.service`, takes from `Analysis` `queued`, `imported` (with `millis`),
  `running` and `finished` (still rejecting a non-terminal status), and from the enums
  `AnalysisStatus.isTerminal()` and `RunOutcome.toStatus()`. The constants `Analysis.UNKNOWN` and
  `INCOMPLETE_REPORT` stay (orchestration uses `UNKNOWN`), and so do the `with*` copies.
- **`AnalysisSpec`** keeps its null checks; the strip, upper/lower case and the sorted, distinct
  analysts become `AnalysisTransitions.normalized(AnalysisSpec)`. `AnalysisService.start` normalizes
  before `AnalysisSpecValidator.validate`, and `imported` normalizes its spec, so a stored spec is
  as normalized as today.
- **`AnalysisFilter`** keeps `ALL`, both constructors and the set copy; `partial()` moves into
  `PartialAnalysisService`, `ownedBy` and `publicOfOthersThan` into `AnalysisListService`, as
  private methods.
- **`ExternalAnalysis`** and **`ExternalRun`** keep their null checks and copies.
  `ExternalAnalysisService.register` checks first what their constructors check today (a run history
  record has its run, the run's status is terminal, `debateRounds` at least 1; still
  `IllegalArgumentException`), and computes the `ref` itself. The factories `reportFiles` and
  `runHistory` become private methods of `ImportExistingRunsUseCase`, their only user.
- **Test fixture**: tests outside the core (analysis adapter, orchestration, `:backend`) build
  analyses with `Analysis.queued(…).running(…).finished(…)` and cannot reach `AnalysisTransitions`.
  The analysis core gets `java-test-fixtures` (as `library:persistence` has) with `TestAnalyses`
  (package `domain.analysis.core.model`): `queued`, `running` and `finished` built with the
  canonical constructor. Those three modules add `testImplementation(testFixtures(project(":backend:domain:analysis:core")))`.
  Core tests use `AnalysisTransitions` directly.
- **ArchUnit**: a new rule `domain_models_are_data` in `ArchitectureTest`: a public method of a
  top-level class in `domain.*.core.model` is a record accessor, `values`/`valueOf`, `toString`,
  `equals`, `hashCode`, an instance `with*` method returning its own class, or one of a named list:
  `newId`, `Permission.key`/`fromKey`, `PasswordPolicyViolation.code`, `ReportKey.isValidTicker`.

## Work packages

### WP1: AnalysisTransitions

- **Status**: open
- **Depends on**: none
- **Files**: in `artifact/backend/domain/analysis/core/src/`:
  `main/java/…/analysis/core/service/AnalysisTransitions.java` (new),
  `test/java/…/analysis/core/service/AnalysisTransitionsTest.java` (new)
- **Steps**:
  - [ ] The model keeps its methods until WP8, so the modules compile between packages.
- **Tests**: `AnalysisTransitionsTest`: `finished` rejects a non-terminal status, `normalized` cases,
  `imported` times cut to milliseconds (the `ExternalAnalysisServiceTest` cases pass unchanged).

### WP2: Test fixture

- **Status**: open
- **Depends on**: none
- **Files**: `artifact/backend/domain/analysis/core/build.gradle.kts`,
  `artifact/backend/domain/analysis/core/src/testFixtures/java/…/analysis/core/model/TestAnalyses.java`,
  `artifact/backend/domain/analysis/adapter/build.gradle.kts`, `artifact/backend/orchestration/build.gradle.kts`,
  `artifact/backend/build.gradle.kts`
- **Tests**: used by WP5 to WP7.

### WP3: Core services use the transitions

- **Status**: open
- **Depends on**: WP1
- **Files**: in `artifact/backend/domain/analysis/core/src/main/java/…/analysis/core/service/`:
  `AnalysisService.java`, `ExternalAnalysisService.java`, `AnalysisEventHub.java`,
  `AnalysisListService.java`, `PartialAnalysisService.java`
- **Tests**: the core tests pass unchanged; WP4 adds to `ExternalAnalysisServiceTest` that each
  invalid external run is rejected.

### WP4: Core tests use the transitions

- **Status**: open
- **Depends on**: WP1
- **Files**: in `artifact/backend/domain/analysis/core/src/test/java/…/analysis/core/service/`:
  `Fakes.java`, `AnalysisServiceTest.java`, `AnalysisVisibilityServiceTest.java`,
  `AnalysisEventHubTest.java`, `PartialAnalysisServiceTest.java`, `ExternalAnalysisServiceTest.java`
- **Tests**: `:backend:domain:analysis:core:test`.

### WP5: Adapter tests

- **Status**: open
- **Depends on**: WP2
- **Files**: in `artifact/backend/domain/analysis/adapter/src/test/java/…/analysis/adapter/`:
  `persistence/JpaAnalysisRepositoryAdapterTest.java`, `persistence/JpaPartialVisibilityTest.java`,
  `runner/DockerRunnerAdapterTest.java`
- **Tests**: they pass with `TestAnalyses`.

### WP6: Orchestration

- **Status**: open
- **Depends on**: WP2
- **Files**: `artifact/backend/orchestration/src/main/java/…/orchestration/report/inbound/ImportExistingRunsUseCase.java`,
  and in `artifact/backend/orchestration/src/test/java/…/orchestration/`:
  `report/inbound/ImportExistingRunsUseCaseTest.java`, `report/inbound/GetAnalysisReportUseCaseTest.java`,
  `report/inbound/GetAnalysisPricesUseCaseTest.java`, `preview/inbound/GetAnalysisPreviewUseCaseTest.java`,
  `preview/inbound/ListAnalysisPreviewsUseCaseTest.java`, `analysisstart/inbound/StartAnalysisWithDefaultsUseCaseTest.java`
- **Tests**: `:backend:orchestration:test`; `ImportExistingRunsUseCaseTest` proves the imports are
  the same.

### WP7: Integration tests

- **Status**: open
- **Depends on**: WP2
- **Files**: in `artifact/backend/src/test/java/…/`: `PartialPreviewConfigurationIntegrationTest.java`,
  `PartialPreviewIntegrationTest.java`, `AnalysisOwnershipIntegrationTest.java`,
  `AnalysisSharingIntegrationTest.java`
- **Tests**: they pass with `TestAnalyses`.

### WP8: The model loses the rules

- **Status**: open
- **Depends on**: WP3, WP4, WP5, WP6, WP7
- **Files**: in `artifact/backend/domain/analysis/core/src/main/java/…/analysis/core/model/`:
  `Analysis.java`, `AnalysisStatus.java`, `RunOutcome.java`, `AnalysisFilter.java`,
  `AnalysisSpec.java`, `ExternalAnalysis.java`, `ExternalRun.java`
- **Tests**: `mise run build`.

### WP9: The rule, checked

- **Status**: open
- **Depends on**: WP8
- **Files**: `artifact/backend/src/test/java/…/ArchitectureTest.java`
- **Tests**: `ArchitectureTest` passes; adding a non-`with` public method to a model record makes
  `domain_models_are_data` fail (checked once by hand).

## Tests

Each work package's tests; `mise run build`.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Package layout" (`model/`) and "Where each class kind goes" | A model holds data only (what may stay, as in plan 1's "Design"); rules go into the domain's `service` package; a row for `domain_models_are_data` |
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Shared libraries" | Name the analysis core's test fixture (`TestAnalyses`) beside the persistence library's |

## Out of scope

- Orchestration `model` types, and the other domains (plan 1) and access rules (plan 2).

## Open questions

- An invalid external run now fails at its `register` call instead of when the import builds it, so
  runs registered before it in the same import stay. No run history entry can be invalid today
  (its status is always terminal). Agreed?
