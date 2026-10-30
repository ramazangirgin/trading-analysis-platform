# Plan: Analysis access rules into a domain service

- **Issue**: #237 (Move domain logic out of domain objects into domain services)
- **Plan**: 2 of 3 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Who may see, manage and change the visibility of an analysis is decided in one place of the
analysis domain's `service` package instead of in `AnalysisAccess` and `Visibility`. The BFF gets
the caller's rights on an analysis through an inbound use case. The API and the UI stay the same.

## Affected parts and their conventions

- Backend: [package structure](../docs/coding-convention/backend-java-package-structure.md),
  [REST endpoints](../docs/coding-convention/backend-java-rest-api.md) (no endpoint changes).

## Design

What stays in a model is set in plan 1's "Design".

- `AnalysisAccess` keeps `user`, `allUsers`, `share` and its null check. `canSee`, `canManage`,
  `canChangeTo`, `allowedVisibilities` and `isOwner` move unchanged into a new package-private final
  class `AnalysisAccessPolicy` (static methods) in `domain.analysis.core.service`, which also takes
  `Visibility.sharedWithUsers()` (removed from the enum). Its callers: `AnalysisService`,
  `AnalysisVisibilityService`, `AnalysisLogService`, `AnalysisEventHub`.
- The BFF's `AnalysisToAnalysisDtoMapper` calls `access.canManage` and `access.allowedVisibilities`
  today, and the BFF may use inbound ports only. New in `domain.analysis.core.inbound`:
  `GetAnalysisRightsUseCase` with `AnalysisRights rights(Analysis analysis, AnalysisAccess access)`,
  and the record `AnalysisRights(boolean canManage, List<Visibility> allowedVisibilities)`.
  `AnalysisRightsService` (package-private `@Service`) implements it with the policy.
  `AnalysesApiDelegateImpl` asks for the rights of each analysis it maps and passes them as the
  mapper's `@Context` in place of `AnalysisAccess`; `canChangeVisibility` stays "allowed visibilities
  not empty". `AnalysisDto` and `openapi.json` do not change.
- The fake `GetAnalysisUseCase` in the orchestration tests checks visibility itself (all users,
  owner, or not `PRIVATE`).

## Work packages

### WP1: AnalysisAccessPolicy

- **Status**: open
- **Depends on**: none
- **Files**: in `artifact/backend/domain/analysis/core/src/`:
  `main/java/…/analysis/core/service/AnalysisAccessPolicy.java` (new),
  `test/java/…/analysis/core/service/AnalysisAccessPolicyTest.java` (new, from `AnalysisAccessTest`),
  `test/java/…/analysis/core/model/AnalysisAccessTest.java` (deleted)
- **Steps**:
  - [ ] `AnalysisAccess` keeps its methods until WP5, so the module compiles between packages.
- **Tests**: `AnalysisAccessPolicyTest` holds every case of `AnalysisAccessTest`, against the policy.

### WP2: Rights use case

- **Status**: open
- **Depends on**: WP1
- **Files**: in `artifact/backend/domain/analysis/core/src/`:
  `main/java/…/analysis/core/inbound/GetAnalysisRightsUseCase.java`,
  `main/java/…/analysis/core/inbound/AnalysisRights.java`,
  `main/java/…/analysis/core/service/AnalysisRightsService.java`,
  `test/java/…/analysis/core/service/AnalysisRightsServiceTest.java`
- **Tests**: `AnalysisRightsServiceTest`: owner with and without `analysis:share`, another user, an
  admin on someone else's partial analysis.

### WP3: BFF maps the rights

- **Status**: open
- **Depends on**: WP2
- **Files**: in `artifact/backend/bff/impl/src/main/java/…/bff/delegate/impl/`:
  `AnalysesApiDelegateImpl.java`, `mapper/AnalysisToAnalysisDtoMapper.java`
- **Tests**: `AnalysisSharingIntegrationTest`, `PartialPreviewIntegrationTest` and
  `AnalysisOwnershipIntegrationTest` pass unchanged (`canManage`, `canChangeVisibility` and
  `allowedVisibilities` per caller).

### WP4: Orchestration test fakes

- **Status**: open
- **Depends on**: none
- **Files**: in `artifact/backend/orchestration/src/test/java/…/orchestration/report/inbound/`:
  `GetAnalysisReportUseCaseTest.java`, `GetAnalysisPricesUseCaseTest.java`
- **Tests**: both pass unchanged otherwise.

### WP5: Callers use the policy, the model loses the rules

- **Status**: open
- **Depends on**: WP1, WP3, WP4
- **Files**: in `artifact/backend/domain/analysis/core/src/main/java/…/analysis/core/`:
  `model/AnalysisAccess.java`, `model/Visibility.java`, `service/AnalysisService.java`,
  `service/AnalysisVisibilityService.java`, `service/AnalysisLogService.java`,
  `service/AnalysisEventHub.java`
- **Tests**: `AnalysisServiceTest`, `AnalysisVisibilityServiceTest`, `AnalysisLogServiceTest` and
  `AnalysisEventHubTest` pass unchanged.

## Tests

Each work package's tests; `mise run build`.

## Docs to update

None: no behaviour or API change. The rule of what a model may hold is documented in plan 3.

## Out of scope

- `Analysis` transitions, enum rules, `AnalysisFilter`, `AnalysisSpec`, `ExternalAnalysis`,
  `ExternalRun`, the documented rule and its ArchUnit check: plan 3.
- Other domains: plan 1.
