# Plan: Collaborators named after their type: BFF, orchestration, application tests

- **Issue**: #275 (Name injected collaborators after their type: enforce, document, refactor)
- **Plan**: 3 of 3 for this issue; depends on: `275-collaborator-name-rule`, `275-rename-domain-collaborators`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every declaration in `bff/impl`, `orchestration` and the application's tests (`artifact/backend/src/test`)
is named as rule `TAP-D9` asks (`GetAnalysisPort getAnalysisPort`), and the rule's temporary filter
is gone: `TAP-D9` applies to the whole backend, main and test. No behaviour changes.

## Affected parts and their conventions

- Backend: [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md)
  (rule `TAP-D9` and its temporary filter, added by plan 1, narrowed by plan 2).

## Design

**The change, the same in every package.** Rename each field, parameter and local variable that
`TAP-D9` reports, and its uses in the file. The names are private to the file, so packages do not
depend on each other. Pick the type's lower camel case; take a qualifier only where a scope holds
two of the type or the plain name would hide a field (`HiddenField`), or where the old name said
something the type does not (`followUps` → `followUpScheduledExecutorService`). A constructor
parameter of a Spring bean that selects one of several beans by its name would need a
`@Qualifier`; none is known today (`SecurityConfig` already injects `securityContextRepository` and
`jsonMapper` by their type names).

**Finding the declarations.** The filter exempts these modules until WP11, so a package removes them
from the `TAP-D9` filter in `config/checkstyle/checkstyle.xml` locally, runs `mise run gradle
:backend:<module>:checkstyleMain :backend:<module>:checkstyleTest` (`:backend:checkstyleTest` for the
application) and restores the file before committing. The file lists come from a regex count on
`main` at f3def285 (about 270 declarations in 66 files); a file the check reports that no package
names goes with WP11.

**Paths.** A path in **Files** is the package path below
`tr/girgin/backend/trading/analysis/platform/` and names its module (`bff/delegate/impl/...` is in
`artifact/backend/bff/impl`, `orchestration/...` in `artifact/backend/orchestration`); a bare file
name is in `artifact/backend/src/test`. `*Test.java`, `TestSession.java` and `UsersApiTestBase.java`
are test sources, the others main.

## Work packages

Packages without a dependency between them can be implemented in parallel. Each package's tests:
the tests of its module pass unchanged (`mise run gradle :backend:<module>:test`, the
application's with `:backend:test`) and Checkstyle reports no `TAP-D9` in its files with the filter
lifted.

### WP1: BFF delegates, part 1

- **Status**: done
- **Depends on**: none
- **Files**: `bff/delegate/impl/AnalysesApiDelegateImpl.java`,
  `bff/delegate/impl/AuthApiDelegateImpl.java`, `bff/delegate/impl/CatalogApiDelegateImpl.java`,
  `bff/delegate/impl/HealthApiDelegateImpl.java`,
  `bff/delegate/impl/PublicAnalysesApiDelegateImpl.java`,
  `bff/delegate/impl/ReportsApiDelegateImpl.java`
- **Tests**: as above.

### WP2: BFF delegates, part 2, and config

- **Status**: done
- **Depends on**: none
- **Files**: `bff/delegate/impl/RolesApiDelegateImpl.java`,
  `bff/delegate/impl/SettingsApiDelegateImpl.java`, `bff/delegate/impl/UsersApiDelegateImpl.java`,
  `bff/delegate/impl/config/RoleAuthoritiesRefreshFilter.java`,
  `bff/delegate/impl/config/SecurityConfig.java`,
  `bff/delegate/impl/error/DomainExceptionHandler.java`
- **Tests**: as above.

### WP3: BFF tests, part 1

- **Status**: done
- **Depends on**: none
- **Files**: `bff/delegate/impl/config/LoginAttemptLimiterTest.java`,
  `bff/delegate/impl/config/RoleAuthoritiesRefreshFilterTest.java`,
  `bff/delegate/impl/mapper/AnalysisPreviewToAnalysisPreviewDtoMapperTest.java`,
  `bff/delegate/impl/mapper/AnalysisViewToAnalysisDtoMapperTest.java`,
  `bff/delegate/impl/mapper/PresetToPresetDtoMapperTest.java`,
  `bff/delegate/impl/mapper/PriceHistoryToPriceHistoryDtoMapperTest.java`,
  `bff/delegate/impl/mapper/ReportToAnalysisReportDtoMapperTest.java`
- **Tests**: as above.

### WP4: BFF tests, part 2

- **Status**: done
- **Depends on**: none
- **Files**: `bff/delegate/impl/mapper/RoleSummaryToRoleDtoMapperTest.java`,
  `bff/delegate/impl/mapper/UserAccountToUserDtoMapperTest.java`,
  `bff/delegate/impl/mapper/error/AnalysisExceptionToApiExceptionMapperTest.java`,
  `bff/delegate/impl/mapper/error/CatalogExceptionToApiExceptionMapperTest.java`,
  `bff/delegate/impl/mapper/error/IdentityExceptionToApiExceptionMapperTest.java`,
  `bff/delegate/impl/mapper/error/SettingsExceptionToApiExceptionMapperTest.java`
- **Tests**: as above.

### WP5: Orchestration use cases, part 1

- **Status**: done
- **Depends on**: none
- **Files**: `orchestration/analysisdefaults/inbound/ManageAnalysisDefaultsUseCase.java`,
  `orchestration/analysisstart/inbound/StartAnalysisWithDefaultsUseCase.java`,
  `orchestration/analysisview/inbound/ViewAnalysesUseCase.java`,
  `orchestration/health/inbound/SystemHealthUseCase.java`,
  `orchestration/ownership/inbound/AssignAnalysisOwnersUseCase.java`,
  `orchestration/ownership/inbound/AssignPresetOwnersUseCase.java`
- **Tests**: as above.

### WP6: Orchestration use cases, part 2

- **Status**: open
- **Depends on**: none
- **Files**: `orchestration/preview/inbound/GetAnalysisPreviewUseCase.java`,
  `orchestration/preview/inbound/ListAnalysisPreviewsUseCase.java`,
  `orchestration/preview/inbound/PreviewCutter.java`,
  `orchestration/report/inbound/GetAnalysisPricesUseCase.java`,
  `orchestration/report/inbound/GetAnalysisReportUseCase.java`,
  `orchestration/report/inbound/ImportExistingRunsUseCase.java`
- **Tests**: as above.

### WP7: Orchestration tests, part 1

- **Status**: open
- **Depends on**: none
- **Files**: `orchestration/analysisdefaults/inbound/ManageAnalysisDefaultsUseCaseTest.java`,
  `orchestration/analysisstart/inbound/StartAnalysisWithDefaultsUseCaseTest.java`,
  `orchestration/analysisview/inbound/ViewAnalysesUseCaseTest.java`,
  `orchestration/health/inbound/SystemHealthUseCaseTest.java`,
  `orchestration/ownership/inbound/AssignAnalysisOwnersUseCaseTest.java`,
  `orchestration/ownership/inbound/AssignPresetOwnersUseCaseTest.java`
- **Tests**: as above.

### WP8: Orchestration tests, part 2

- **Status**: open
- **Depends on**: none
- **Files**: `orchestration/preview/inbound/GetAnalysisPreviewUseCaseTest.java`,
  `orchestration/preview/inbound/ListAnalysisPreviewsUseCaseTest.java`,
  `orchestration/report/inbound/GetAnalysisPricesUseCaseTest.java`,
  `orchestration/report/inbound/GetAnalysisReportUseCaseTest.java`,
  `orchestration/report/inbound/ImportExistingRunsUseCaseTest.java`
- **Tests**: as above.

### WP9: Application integration tests, part 1

- **Status**: open
- **Depends on**: none
- **Files**: `AnalysesApiIntegrationTest.java`, `AnalysisDefaultsApiIntegrationTest.java`,
  `AnalysisOwnershipIntegrationTest.java`, `AnalysisSharingIntegrationTest.java`,
  `AnalysisStartDefaultsIntegrationTest.java`, `AuthApiIntegrationTest.java`,
  `EndpointPermissionsIntegrationTest.java`, `FirstAdminConfiguredPasswordIntegrationTest.java`
- **Tests**: as above.

### WP10: Application integration tests, part 2

- **Status**: open
- **Depends on**: none
- **Files**: `FirstAdminIntegrationTest.java`, `LoginLockoutIntegrationTest.java`,
  `PartialPreviewConfigurationIntegrationTest.java`, `PartialPreviewIntegrationTest.java`,
  `PasswordChangeIntegrationTest.java`, `PresetOwnershipIntegrationTest.java`,
  `PresetsApiIntegrationTest.java`, `RolePermissionsIntegrationTest.java`
- **Tests**: as above.

### WP11: Application test support, and TAP-D9 everywhere

- **Status**: open
- **Depends on**: WP1-WP10
- **Files**: `TestSession.java`, `UsersApiTestBase.java`, `config/checkstyle/checkstyle.xml`,
  `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] Delete the `TAP-D9` filter (and its XML comment) from `checkstyle.xml`.
  - [ ] Rename what the check still reports anywhere in the backend (files no package named).
- **Tests**: `mise run check` and `mise run build` pass without the filter.

## Tests

Each work package's tests; WP11 proves that no `TAP-D9` finding is left in the backend.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Suppressing a finding" | drop the temporary `TAP-D9` filter from the list of settings that limit where a check applies (seven becomes six) |
| Screenshot | none | no page changes |

## Out of scope

- Nothing else of #275: the check, the rule and its documentation are plan 1, the domains plan 2.
