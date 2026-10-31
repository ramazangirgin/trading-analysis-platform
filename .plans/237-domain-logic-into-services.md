# Plan: Domain logic out of the models into domain services

- **Issue**: #237 (Move domain logic out of domain objects into domain services)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every model type of the backend becomes pure data: the `model` packages of the domain cores, of
orchestration and of the BFF, and the records in the domain cores' `inbound` and `outbound` packages.
Behaviour lives in injected services: in a domain core, Spring beans in its `service` package, every
one named `*Service`; in orchestration, its use cases. Two ArchUnit rules enforce this. Nothing
changes for the user, and the REST API stays the same.

The plan also carries an unrelated fix that the coordinator asked for: `scripts/agent/dashboard.sh`
no longer fails with "dashboard: not updated".

## Affected parts and their conventions

- Backend: [package structure](../docs/coding-convention/backend-java-package-structure.md)
  (orchestration logic in its use cases; test fixtures under "Shared libraries"),
  [Checkstyle](../docs/coding-convention/backend-java-checkstyle.md),
  [REST endpoints](../docs/coding-convention/backend-java-rest-api.md) (no endpoint changes).
- Agent scripts: [agentic development](../docs/agentic-development.md) ("Dashboard").

## Design

**A model holds its components only.** That rules out null checks, defensive copies, validation or
normalization in constructors, secondary constructors, factories, `with*` or other copies,
`newId()`, overridden `toString`, derived methods and static helpers. Only what the language
generates remains: accessors, `equals`, `hashCode`, `toString`, `values`/`valueOf`. Copies are
made by mappers where a mapper already builds the record (DTO and entity mapping), by services
otherwise, with the canonical constructor; never by the models. Whoever builds a record passes the
collections it should hold. Four exceptions stay:
- the four `RoleId` constants, which name rows seeded by migrations and are used by four services
  and 18 integration tests;
- the `key` field and accessor of `Permission`, which the BFF (authorities) and the database share;
- the same on the BFF's `PermissionEnumDto`, its JSON value;
- the masking `toString` of `PasswordHash`: the generated one would print the hash, in a log of the
  hash or of a `User`.

Logs and messages that print an `AnalysisId` or `OwnerId` use `value()`, since the generated
`toString` differs.

**Service classes**: every top-level class in a domain core's `service` package is a `@Service`
named `*Service`. The ones named otherwise are renamed, and the static ones become beans:
- `AnalysisSpecValidator` → `AnalysisSpecService`
- `AnalysisEventHub` → `AnalysisEventService`
- `LoginLockoutPolicy` → `LoginLockoutService`
- `AuthenticatedUserFactory` → `AuthenticatedUserService`
- `OneTimePasswordGenerator` → `OneTimePasswordService`
- `PasswordPolicy` → `PasswordPolicyService`
- `DecisionRatingParser` → `DecisionRatingService`

Only `services_live_in_service_packages` (`*Service` → `service`) exists today, so a reverse rule is
added.

**Where the behaviour goes, per domain:**

| Domain | Model behaviour | Moves to |
|---|---|---|
| identity | `User` copies, `UserId.newId` | new `UserChangeService` |
| identity | `User.isLockedAt`, `withLoginSucceeded` | `LoginLockoutService` (`isLocked`, `unlocked`), injected into `AuthenticationService`, `UserManagementService`, `UserLockService` |
| identity | `Role` copies, `RoleId.newId` | private methods of `RoleManagementService`, their only user |
| identity | `Username` format | new `UsernameService`. `AuthenticationService`, `FirstAdminService` and `UserManagementService` take the name as a `String` through it, with the same `INVALID_USERNAME` error |
| identity | `PasswordPolicyViolation.code` | `PasswordPolicyService` |
| identity | `Permission.fromKey` (unused) | deleted |
| settings | `PresetAccess.canSee`, `PresetId.newId` | private methods of `PresetService` |
| settings | `OwnerId` blank check | dropped: the ID is the authenticated user's |
| report | `ReportKey` ticker format | new `ReportKeyService`. `ReportService` and `PriceHistoryService` answer empty for an invalid ticker; `ReportService` and `RunHistoryService` drop scanned entries with one. The datadir and history adapters and orchestration (`PreviewCutter`, `GetAnalysisReportUseCase`, `GetAnalysisPricesUseCase`) stop calling `isValidTicker`. No path is built from an unchecked ticker, since adapters read by key only for a key the service has checked |
| report | `Report.hasDecision`, the `EnumMap` copies | new inbound `CheckReportDecisionUseCase` (`boolean hasDecision(Report report)`), implemented by `ReportService`, which also builds the maps. `ImportExistingRunsUseCase` calls the port |
| analysis | `AnalysisAccess` rules, `Visibility.sharedWithUsers` | new `AnalysisAccessService`, which implements the new inbound `GetAnalysisRightsUseCase` (`AnalysisRights rights(Analysis analysis, AnalysisAccess access)`, record `AnalysisRights(boolean canManage, List<Visibility> allowedVisibilities)`). `AnalysesApiDelegateImpl` passes the rights of each analysis as the mapper's `@Context` in place of `AnalysisAccess` |
| analysis | `Analysis` factories, transitions and copies, `UNKNOWN`, `INCOMPLETE_REPORT`, `RunStats.EMPTY`, `AnalysisStatus.isTerminal`, `RunOutcome.toStatus` | new `AnalysisTransitionService`. Orchestration hands over null models for an unknown run, and the service fills in "unknown" |
| analysis | `AnalysisId` format and `newId` | new `AnalysisIdService`. Every service method that takes an `AnalysisId` checks it before any port call and answers `NOT_FOUND`, as `StringToAnalysisIdMapper` (BFF, now a plain constructor call) does today |
| analysis | `AnalysisSpec` normalization | `AnalysisSpecService.normalized`, used by `AnalysisService.start` before validating, and by `AnalysisTransitionService` on import |
| analysis | `AnalysisFilter` factories, the secondary constructor, `ALL` (unused) | private methods of `PartialAnalysisService` and `AnalysisListService`; callers use the canonical constructor |
| analysis | `ExternalAnalysis`/`ExternalRun` checks | new inbound `ValidateExternalAnalysesUseCase` (`void validate(List<ExternalAnalysis> externals)`), implemented by new `ExternalAnalysisValidationService`. `ImportExistingRunsUseCase` validates the whole batch before registering any run, so an invalid run stops the import before anything is written |
| analysis | `ExternalAnalysis.ref` | `ExternalAnalysisService` |
| analysis | factories `reportFiles`/`runHistory` | private methods of `ImportExistingRunsUseCase` |
| analysis | `RunEvent` payload copy | the eventline adapter's `RunnerOutputLineToRunEventMapper` |
| orchestration | `SystemHealth.overall()` | `SystemHealthUseCase` computes it into a new `overall` component (the BFF mapper keeps calling `overall()`) |

Catalog, the other orchestration models and the remaining records (`AuthenticatedUser`,
`AnalysisDefaults`, `SecretStatus`, `RunHandle`, ...) lose only null checks and copies.

**Test fixture**: `Analysis` has 18 components, and 13 test classes in three modules (analysis
adapter, orchestration, `:backend`) build analyses in a given state. So the analysis core gets
`java-test-fixtures` (as `library:persistence` has), with `TestAnalyses` in package
`domain.analysis.core.model`: `newId`, `queued`, `running`, `finished` and `withVisibility`, built
with the canonical constructor. Other tests use canonical constructors, and core tests use the
services.

**ArchUnit** (`ArchitectureTest`):
- `models_are_data` covers the top-level classes, interfaces excluded, in `domain.*.core.model`,
  `domain.*.core.inbound`, `domain.*.core.outbound`, `orchestration.*.model` and
  `bff.controller.api.model`. They may declare no method other than record accessors, `equals`,
  `hashCode`, `toString`, `values`, `valueOf` and an enum's zero-argument field accessor. Their
  constructors may call nothing but the super constructor, which catches compact-constructor code.
  `toString` counts only where the compiler generates it (its body is one `invokedynamic` to
  `ObjectMethods`), except on `PasswordHash`, the one class the rule names by its name.
- `service_packages_hold_only_services` requires every top-level class in `domain.*.core.service` to
  be annotated `@Service` and named `*Service`.

**Dashboard fix**: `dashboard.sh` removes lib.sh's scratch directory (`rmdir "$AGENT_TMP"`) and never
points `AGENT_TMP` elsewhere, so lib.sh's `retry` (`mktemp "$AGENT_TMP/retry.XXXXXX"`) fails on
every `gh` call. After `work=$(mktemp -d …)` it sets `AGENT_TMP=$work`, as lib.sh does with
`AGENT_TMP=$AGENT_STATE`; `cleanup()` already removes `$work`.

**Order**: services first, then their callers, then tests. Model methods are removed last, so main
code compiles after every package. A rename is one file and moves together with its main users.
File paths below are relative to the package root of the named module and source set.

## Work packages

### WP1: Dashboard keeps a scratch directory

- **Status**: done
- **Depends on**: none
- **Files**: `scripts/agent/dashboard.sh`, `scripts/agent/test_dashboard.py`
- **Steps**:
  - [x] Commit this package on its own, apart from the backend changes.
- **Tests**: a new test in `test_dashboard.py` runs `dashboard.sh start <n>` with `AGENT_DASHBOARD=1`
  in a throwaway git repository, with a fake `gh` on `PATH` (as `test_lib.py` does) that answers the
  calls of `start`. It asserts the run logs neither `mkstemp` nor "not updated", and it fails without
  the fix. `mise run agent:test` runs it.

### WP2: Identity services renamed

- **Status**: done
- **Depends on**: none
- **Files**: identity core, main: `service/LoginLockoutPolicy.java` (renamed), `service/AuthenticatedUserFactory.java` (renamed),
  `service/OneTimePasswordGenerator.java` (renamed), `service/PasswordPolicy.java` (renamed, gains `code`),
  `service/AuthenticationService.java`, `service/FirstAdminService.java`, `service/PasswordChangeService.java`,
  `service/UserManagementService.java`
- **Tests**: `:backend:test` compiles; the core tests follow in WP5.

### WP3: New identity services

- **Status**: done
- **Depends on**: WP2
- **Files**: identity core: `main/…/service/UserChangeService.java`, `main/…/service/UsernameService.java`,
  `main/…/service/LoginLockoutService.java`, `test/…/service/UserChangeServiceTest.java`,
  `test/…/service/UsernameServiceTest.java`
- **Tests**: `UserChangeServiceTest` holds the copy cases of `UserTest`. `UsernameServiceTest` holds
  the cases of `UsernameTest`.

### WP4: Identity services use them

- **Status**: done
- **Depends on**: WP3
- **Files**: identity core, main: `service/AuthenticationService.java`, `service/UserManagementService.java`,
  `service/UserLockService.java`, `service/PasswordChangeService.java`, `service/FirstAdminService.java`,
  `service/RoleManagementService.java`
- **Tests**: WP5 and WP6.

### WP5: Identity service tests

- **Status**: done
- **Depends on**: WP4
- **Files**: identity core, test: `service/LoginLockoutPolicyTest.java` (renamed, gains the `isLockedAt` cases of `UserTest`),
  `service/OneTimePasswordGeneratorTest.java` (renamed), `service/PasswordPolicyTest.java` (renamed),
  `service/AuthenticationServiceTest.java`, `service/UserManagementServiceTest.java`,
  `service/UserLockServiceTest.java`, `service/PasswordChangeServiceTest.java`, `service/FirstAdminServiceTest.java`
- **Tests**: they pass with the injected services; `AuthenticationServiceTest` and
  `UserManagementServiceTest` keep proving `INVALID_USERNAME`.

### WP6: Identity model tests

- **Status**: done
- **Depends on**: WP4
- **Files**: identity core, test: `service/RoleManagementServiceTest.java`, `model/UserTest.java` (deleted),
  `model/RoleTest.java` (deleted), `model/UsernameTest.java` (deleted), `model/PermissionTest.java`
- **Tests**: `RoleManagementServiceTest` covers the role copies; `PermissionTest` keeps "keys are unique";
  `PasswordHashTest` stays as it is.

### WP7: Identity models, users and roles

- **Status**: open
- **Depends on**: WP4
- **Files**: identity core, main: `model/User.java`, `model/Role.java`, `model/RoleId.java`, `model/UserId.java`,
  `model/Username.java`
- **Tests**: `:backend:test` compiles.

### WP8: Identity models, the rest

- **Status**: open
- **Depends on**: WP4
- **Files**: identity core, main: `model/AuthenticatedUser.java`, `model/PasswordHash.java`,
  `model/PasswordPolicyViolation.java`, `model/Permission.java`
- **Tests**: `:backend:test` compiles.

### WP9: Identity adapter test

- **Status**: open
- **Depends on**: none
- **Files**: identity adapter, test: `persistence/JpaIdentityRepositoryTest.java`
- **Tests**: it passes with canonical constructors in place of `with*`.

### WP10: Settings

- **Status**: open
- **Depends on**: none
- **Files**: settings core, main: `service/PresetService.java`, `model/PresetAccess.java`, `model/Preset.java`,
  `model/PresetId.java`, `model/OwnerId.java`, `model/AnalysisDefaults.java`, `model/SecretStatus.java`
- **Tests**: `PresetServiceTest` passes unchanged (own, other and unowned presets, with and without
  `allUsers`).

### WP11: Catalog models

- **Status**: open
- **Depends on**: none
- **Files**: catalog core, main: `model/AnalystOption.java`, `model/Catalog.java`, `model/ModelOption.java`,
  `model/Provider.java`
- **Tests**: `CatalogServiceTest` and the catalog adapter tests pass.

### WP12: Report services

- **Status**: open
- **Depends on**: none
- **Files**: report core, main: `inbound/CheckReportDecisionUseCase.java`, `service/ReportKeyService.java`,
  `service/DecisionRatingParser.java` (renamed), `service/ReportService.java`, `service/RunHistoryService.java`,
  `service/PriceHistoryService.java`
- **Tests**: WP13.

### WP13: Report service tests

- **Status**: open
- **Depends on**: WP12
- **Files**: report core, test: `service/ReportKeyServiceTest.java`, `service/ReportServiceTest.java`,
  `service/PriceHistoryServiceTest.java`
- **Tests**: `ReportKeyServiceTest` holds the ticker cases. `ReportServiceTest` covers
  `hasDecision`, invalid tickers dropped from a scan and answered empty. `PriceHistoryServiceTest`
  covers an invalid ticker answered empty.

### WP14: Report adapters

- **Status**: open
- **Depends on**: WP12
- **Files**: report adapter: `main/…/datadir/FileSystemDataDirAdapter.java`, `main/…/history/JsonRunHistoryAdapter.java`,
  `test/…/datadir/FileSystemDataDirAdapterTest.java`, `test/…/history/JsonRunHistoryAdapterTest.java`
- **Tests**: the adapter tests no longer expect invalid tickers to be skipped, since WP13 proves the
  services drop them.

### WP15: Analysis access service

- **Status**: open
- **Depends on**: none
- **Files**: analysis core: `main/…/inbound/GetAnalysisRightsUseCase.java`, `main/…/inbound/AnalysisRights.java`,
  `main/…/service/AnalysisAccessService.java`, `test/…/service/AnalysisAccessServiceTest.java`,
  `test/…/model/AnalysisAccessTest.java` (deleted)
- **Tests**: `AnalysisAccessServiceTest` holds every case of `AnalysisAccessTest`, plus `rights`
  for an owner with and without `analysis:share`, another user, and an admin on someone else's
  partial analysis.

### WP16: Analysis transition, ID and spec services

- **Status**: open
- **Depends on**: none
- **Files**: analysis core: `main/…/service/AnalysisTransitionService.java`, `main/…/service/AnalysisIdService.java`,
  `main/…/service/AnalysisSpecValidator.java` (renamed, gains `normalized`), `main/…/service/AnalysisService.java`,
  `test/…/service/AnalysisTransitionServiceTest.java`, `test/…/service/AnalysisIdServiceTest.java`,
  `test/…/service/AnalysisSpecServiceTest.java`
- **Tests**: `finished` rejects a non-terminal status; `imported` cuts times to milliseconds and fills
  "unknown"; ID format cases; `normalized` cases.

### WP17: External analysis validation

- **Status**: open
- **Depends on**: none
- **Files**: analysis core: `main/…/inbound/ValidateExternalAnalysesUseCase.java`,
  `main/…/service/ExternalAnalysisValidationService.java`, `test/…/service/ExternalAnalysisValidationServiceTest.java`
- **Tests**: each invalid case is rejected: a run history record without its run, a non-terminal
  run, `debateRounds` below 1.

### WP18: Analysis services use them

- **Status**: open
- **Depends on**: WP15, WP16
- **Files**: analysis core, main: `service/AnalysisService.java`, `service/AnalysisVisibilityService.java`,
  `service/AnalysisLogService.java`, `service/AnalysisEventHub.java` (renamed), `service/ExternalAnalysisService.java`,
  `service/AnalysisListService.java`, `service/PartialAnalysisService.java`
- **Tests**: WP19.

### WP19: Analysis service tests

- **Status**: open
- **Depends on**: WP18
- **Files**: analysis core, test: `service/Fakes.java`, `service/AnalysisServiceTest.java`,
  `service/AnalysisVisibilityServiceTest.java`, `service/AnalysisEventHubTest.java` (renamed),
  `service/PartialAnalysisServiceTest.java`, `service/ExternalAnalysisServiceTest.java`, `service/AnalysisLogServiceTest.java`
- **Tests**: `:backend:domain:analysis:core:test`; an invalid ID answers `NOT_FOUND` without a port call.

### WP20: Test fixture

- **Status**: open
- **Depends on**: none
- **Files**: `artifact/backend/domain/analysis/core/build.gradle.kts`,
  `artifact/backend/domain/analysis/core/src/testFixtures/java/…/analysis/core/model/TestAnalyses.java`,
  `artifact/backend/domain/analysis/adapter/build.gradle.kts`, `artifact/backend/orchestration/build.gradle.kts`,
  `artifact/backend/build.gradle.kts`
- **Tests**: used by WP22, WP25 and WP26.

### WP21: BFF

- **Status**: open
- **Depends on**: WP15
- **Files**: bff impl, main: `AnalysesApiDelegateImpl.java`, `mapper/AnalysisToAnalysisDtoMapper.java`,
  `mapper/StringToAnalysisIdMapper.java`
- **Tests**: `AnalysisSharingIntegrationTest`, `PartialPreviewIntegrationTest` and
  `AnalysisOwnershipIntegrationTest` pass (`canManage`, `canChangeVisibility`,
  `allowedVisibilities` per caller, 404 for a malformed ID).

### WP22: Analysis adapter

- **Status**: open
- **Depends on**: WP20
- **Files**: analysis adapter: `main/…/eventline/mapper/RunnerOutputLineToRunEventMapper.java`,
  `main/…/persistence/mapper/AnalysisEntityToAnalysisMapper.java`, `test/…/persistence/JpaAnalysisRepositoryAdapterTest.java`,
  `test/…/persistence/JpaPartialVisibilityTest.java`, `test/…/runner/DockerRunnerAdapterTest.java`
- **Tests**: they pass with `TestAnalyses`, and a payload with null values is still kept.

### WP23: Orchestration use cases

- **Status**: open
- **Depends on**: WP12, WP17
- **Files**: orchestration, main: `report/inbound/ImportExistingRunsUseCase.java`, `report/inbound/GetAnalysisReportUseCase.java`,
  `report/inbound/GetAnalysisPricesUseCase.java`, `preview/inbound/PreviewCutter.java`
- **Tests**: WP25.

### WP24: Orchestration models

- **Status**: open
- **Depends on**: none
- **Files**: orchestration: `main/…/health/inbound/SystemHealthUseCase.java`, `main/…/health/model/SystemHealth.java`,
  `main/…/health/model/HealthCheck.java`, `main/…/preview/model/AnalysisPreview.java`,
  `main/…/preview/model/AnalysisPreviewPage.java`, `test/…/health/inbound/SystemHealthUseCaseTest.java`
- **Tests**: `SystemHealthUseCaseTest` proves `overall` is the worst check, `UP` without checks.

### WP25: Orchestration tests

- **Status**: open
- **Depends on**: WP20, WP23
- **Files**: orchestration, test: `report/inbound/ImportExistingRunsUseCaseTest.java`, `report/inbound/GetAnalysisReportUseCaseTest.java`,
  `report/inbound/GetAnalysisPricesUseCaseTest.java`, `preview/inbound/GetAnalysisPreviewUseCaseTest.java`,
  `preview/inbound/ListAnalysisPreviewsUseCaseTest.java`, `analysisstart/inbound/StartAnalysisWithDefaultsUseCaseTest.java`
- **Tests**: `ImportExistingRunsUseCaseTest` proves the imports are the same, an unsettled report
  without a decision is still skipped, and an invalid run stops the import before any `register`
  call.

### WP26: Analysis integration tests

- **Status**: open
- **Depends on**: WP20
- **Files**: `:backend`, test: `PartialPreviewConfigurationIntegrationTest.java`, `PartialPreviewIntegrationTest.java`,
  `AnalysisOwnershipIntegrationTest.java`, `AnalysisSharingIntegrationTest.java`
- **Tests**: they pass with `TestAnalyses` and canonical constructors.

### WP27: Identity and settings integration tests

- **Status**: open
- **Depends on**: none
- **Files**: `:backend`, test: `UsersApiTestBase.java`, `UserLifecycleIntegrationTest.java`,
  `LoginLockoutIntegrationTest.java`, `TestSession.java`, `UsersApiIntegrationTest.java`,
  `PresetOwnershipIntegrationTest.java`
- **Tests**: they pass with canonical constructors in place of `with*` and `newId`.

### WP28: Report models

- **Status**: open
- **Depends on**: WP13, WP14, WP23
- **Files**: report core, main: `model/PriceBar.java`, `model/PriceHistory.java`, `model/Report.java`,
  `model/ReportContent.java`, `model/ReportKey.java`, `model/RunHistoryEntry.java`
- **Tests**: `mise run build`.

### WP29: Analysis models, analyses

- **Status**: open
- **Depends on**: WP18, WP19, WP21, WP22, WP23, WP25, WP26
- **Files**: analysis core, main: `model/Analysis.java`, `model/AnalysisAccess.java`, `model/AnalysisFilter.java`,
  `model/AnalysisId.java`, `model/AnalysisSpec.java`, `model/AnalysisStatus.java`, `model/OwnerId.java`,
  `model/Visibility.java`
- **Tests**: `mise run build`.

### WP30: Analysis models, runs and imports

- **Status**: open
- **Depends on**: WP29
- **Files**: analysis core, main: `model/ExternalAnalysis.java`, `model/ExternalRegistration.java`,
  `model/ExternalRun.java`, `model/PartialAnalysisPage.java`, `model/RunEvent.java`, `model/RunOutcome.java`,
  `model/RunStats.java`, `outbound/runner/RunHandle.java`
- **Tests**: `mise run build`.

### WP31: The rules, checked

- **Status**: open
- **Depends on**: WP5-WP11, WP24, WP27, WP28, WP30
- **Files**: `:backend`, test: `ArchitectureTest.java`
- **Tests**: `ArchitectureTest` passes. By hand, once: a method or a compact constructor added to a
  model record makes `models_are_data` fail, and a non-`@Service` class in a `service` package
  makes `service_packages_hold_only_services` fail.

## Tests

Each work package's tests; `mise run build` and `mise run agent:test`.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Package layout" (`model/` of domains and orchestration, `service/`) | Models hold their components only, with the four exceptions of "Design" and why each stays; copies by mappers or services. `service/` holds `@Service` beans named `*Service` |
| Text | same document, "Where each class kind goes" | Rows for `models_are_data` (naming its `PasswordHash` exception) and `service_packages_hold_only_services` |
| Text | same document, "Shared libraries" | Name the analysis core's test fixture (`TestAnalyses`) beside the persistence library's |

The dashboard fix needs no doc change: it restores what "Dashboard" in `docs/agentic-development.md`
already describes.

## Out of scope

- Adapter data shapes (`entity`, `json`, `spec`): JPA and Jackson mapping targets, not model types.
- The ticker pattern that `AnalysisSpecService` and `ReportKeyService` both hold (two domains).

## Open questions

None.
