# Plan: UseCase for orchestration only, domain inbound ports named *Port

- **Issue**: #274 (UseCase is for orchestration only: rename the domain inbound ports)
- **Plan**: 1 of 1 for this issue; depends on: none (#237 / PR #270 is merged)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

`*UseCase` names only the concrete orchestration beans in `orchestration.<feature>.inbound`. The 32
interfaces in the domain cores' `inbound` packages that the domain services implement are renamed
`*Port` (`GetAnalysisUseCase` → `GetAnalysisPort`), and the build fails when a `*UseCase` lives
outside orchestration or a domain service implements an inbound interface not named `*Port`.

## Affected parts and their conventions

- Backend: [backend-java-package-structure.md](../docs/coding-convention/backend-java-package-structure.md),
  [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md).

## Design

- **The name is `*Port`.** It is the hexagonal term for both sides, and the outbound ports already use
  it; the package (`core.inbound` / `core.outbound`) tells the side. `*Api` would collide with the
  BFF's `*ApiController`, `*ApiDelegate`, `*ApiDelegateImpl` and `ApiException`. An interface named
  `*Service` would break `services_live_in_service_packages` and `service_packages_hold_only_services`
  (#237: everything named `*Service` is a `@Service` bean in `service/`). No renamed port takes the
  simple name of an existing outbound port.
- **The rename**, the same for every port: `git mv` the interface to `<Name>Port`, then update every
  reference (imports, `implements`, constructor and field types, `mock(...)`, `{@link}`); a field or
  variable named after the old type (`validateExternalAnalysesUseCase`, `checkReportDecisionUseCase`
  in `ImportExistingRunsUseCase` and its test) drops the `UseCase` suffix. Orchestration classes,
  their tests and their fields (`useCase`, `importExistingRunsUseCase`) keep their names.
  `EventSubscription` and `RunEventListener` stay as they are: they are the types
  `SubscribeAnalysisEventsPort` exchanges, not ports (like `RunHandle` in `outbound`).
- **Packaging**: a port's interface and all its users change in one package, so every package
  compiles and passes the tests of the modules it touches. The packages share files
  (`AnalysesApiDelegateImpl`, `AnalysisService`, `ImportExistingRunsUseCase`, `SystemHealthUseCase`),
  so they run in order, each depending on the previous one. File paths below are package paths under
  `tr/girgin/backend/trading/analysis/platform/` of their module (`src/test` for a `*Test`).
- **Rules** in `ArchitectureTest` (WP16):
  - `use_cases_live_in_inbound_packages` becomes `use_cases_live_in_orchestration_inbound_packages`:
    a class named `*UseCase` resides in `ORCHESTRATION_INBOUND`. That one rule also says that no
    `*UseCase` exists anywhere else, so no second rule is added. `INBOUND` loses its last user and
    goes.
  - New `domain_services_implement_inbound_ports`: every interface a class in `DOMAIN_SERVICE`
    implements resides in `domain.*.core.inbound` and is named `*Port` (a custom `ArchCondition`, like
    `DataOnlyCondition`). Today the services implement nothing else.
  - New `ports_live_in_port_packages`: a class named `*Port` resides in `..core.inbound..` or
    `..core.outbound..`.
  - `PersistenceArchitectureTest` and the custom Checkstyle checks do not name `UseCase` (checked);
    only the comments of the Javadoc scope in `config/checkstyle/checkstyle.xml` (the
    `MissingJavadocType` suppression and `MissingJavadocPortType`) say "use cases and ports".

## Work packages

### WP1: Rename analysis ports: visibility, logs, events

- **Status**: done
- **Depends on**: none
- **Files**: `domain/analysis/core/inbound/ChangeAnalysisVisibilityUseCase.java`,
  `domain/analysis/core/inbound/ReadAnalysisLogsUseCase.java`,
  `domain/analysis/core/inbound/SubscribeAnalysisEventsUseCase.java`,
  `domain/analysis/core/inbound/RunEventListener.java`,
  `domain/analysis/core/service/AnalysisVisibilityService.java`,
  `domain/analysis/core/service/AnalysisLogService.java`,
  `domain/analysis/core/service/AnalysisEventService.java`,
  `bff/delegate/impl/AnalysesApiDelegateImpl.java`
- **Steps**:
  - [x] Rename the three ports as in "Design".
- **Tests**: the existing tests of `:backend:domain:analysis:core` and `:backend:bff:impl` pass.

### WP2: Rename analysis ports: start, rerun, stop

- **Status**: done
- **Depends on**: WP1
- **Files**: `domain/analysis/core/inbound/StartAnalysisUseCase.java`,
  `domain/analysis/core/inbound/RerunAnalysisUseCase.java`,
  `domain/analysis/core/inbound/StopAnalysisUseCase.java`,
  `domain/analysis/core/service/AnalysisService.java`,
  `bff/delegate/impl/AnalysesApiDelegateImpl.java`,
  `orchestration/analysisstart/inbound/StartAnalysisWithDefaultsUseCase.java`
- **Steps**:
  - [x] Rename the three ports as in "Design".
- **Tests**: the existing tests of the analysis core, `:backend:bff:impl` and `:backend:orchestration` pass.

### WP3: Rename GetAnalysisUseCase

- **Status**: done
- **Depends on**: WP2
- **Files**: `domain/analysis/core/inbound/GetAnalysisUseCase.java`,
  `domain/analysis/core/service/AnalysisService.java`,
  `domain/analysis/core/service/AnalysisLogService.java`,
  `bff/delegate/impl/AnalysesApiDelegateImpl.java`,
  `orchestration/report/inbound/GetAnalysisReportUseCase.java`,
  `orchestration/report/inbound/GetAnalysisReportUseCaseTest.java`,
  `orchestration/report/inbound/GetAnalysisPricesUseCase.java`,
  `orchestration/report/inbound/GetAnalysisPricesUseCaseTest.java`
- **Steps**:
  - [x] Rename the port as in "Design".
- **Tests**: the existing tests of the analysis core, `:backend:bff:impl` and `:backend:orchestration` pass.

### WP4: Rename the partial analysis ports

- **Status**: done
- **Depends on**: WP3
- **Files**: `domain/analysis/core/inbound/GetPartialAnalysisUseCase.java`,
  `domain/analysis/core/inbound/ListPartialAnalysesUseCase.java`,
  `domain/analysis/core/service/PartialAnalysisService.java`,
  `orchestration/preview/inbound/GetAnalysisPreviewUseCase.java`,
  `orchestration/preview/inbound/GetAnalysisPreviewUseCaseTest.java`,
  `orchestration/preview/inbound/ListAnalysisPreviewsUseCase.java`,
  `orchestration/preview/inbound/ListAnalysisPreviewsUseCaseTest.java`
- **Steps**:
  - [x] Rename the two ports as in "Design".
- **Tests**: the existing tests of the analysis core and `:backend:orchestration` pass.

### WP5: Rename analysis ports: list, rights

- **Status**: done
- **Depends on**: WP4
- **Files**: `domain/analysis/core/inbound/ListAnalysesUseCase.java`,
  `domain/analysis/core/inbound/GetAnalysisRightsUseCase.java`,
  `domain/analysis/core/service/AnalysisListService.java`,
  `domain/analysis/core/service/AnalysisAccessService.java`,
  `bff/delegate/impl/AnalysesApiDelegateImpl.java`,
  `orchestration/health/inbound/SystemHealthUseCase.java`,
  `orchestration/analysisview/inbound/ViewAnalysesUseCase.java`
- **Steps**:
  - [x] Rename the two ports as in "Design".
- **Tests**: the existing tests of the analysis core, `:backend:bff:impl` and `:backend:orchestration` pass.

### WP6: Rename analysis ports: unowned, external

- **Status**: done
- **Depends on**: WP5
- **Files**: `domain/analysis/core/inbound/AssignUnownedAnalysesUseCase.java`,
  `domain/analysis/core/inbound/RegisterExternalAnalysisUseCase.java`,
  `domain/analysis/core/service/AnalysisService.java`,
  `domain/analysis/core/service/ExternalAnalysisService.java`,
  `orchestration/ownership/inbound/AssignAnalysisOwnersUseCase.java`,
  `orchestration/ownership/inbound/AssignAnalysisOwnersUseCaseTest.java`,
  `orchestration/report/inbound/ImportExistingRunsUseCase.java`
- **Steps**:
  - [x] Rename the two ports as in "Design".
- **Tests**: the existing tests of the analysis core and `:backend:orchestration` pass.

### WP7: Rename the import-checking ports

- **Status**: done
- **Depends on**: WP6
- **Files**: `domain/analysis/core/inbound/ValidateExternalAnalysesUseCase.java`,
  `domain/report/core/inbound/CheckReportDecisionUseCase.java`,
  `domain/report/core/inbound/WatchDataDirUseCase.java`,
  `domain/analysis/core/service/ExternalAnalysisValidationService.java`,
  `domain/report/core/service/ReportService.java`,
  `domain/report/core/service/DataDirWatchService.java`,
  `orchestration/report/inbound/ImportExistingRunsUseCase.java`,
  `orchestration/report/inbound/ImportExistingRunsUseCaseTest.java`
- **Steps**:
  - [x] Rename the three ports as in "Design", with the two fields named after them.
- **Tests**: the existing tests of the analysis and report cores and `:backend:orchestration` pass.

### WP8: Rename the scan ports and GetEngineVersionUseCase

- **Status**: done
- **Depends on**: WP7
- **Files**: `domain/report/core/inbound/ScanReportsUseCase.java`,
  `domain/report/core/inbound/ScanRunHistoryUseCase.java`,
  `domain/catalog/core/inbound/GetEngineVersionUseCase.java`,
  `domain/report/core/service/ReportService.java`,
  `domain/report/core/service/RunHistoryService.java`,
  `domain/catalog/core/service/CatalogService.java`,
  `orchestration/report/inbound/ImportExistingRunsUseCase.java`,
  `orchestration/health/inbound/SystemHealthUseCase.java`
- **Steps**:
  - [x] Rename the three ports as in "Design".
- **Tests**: the existing tests of the report and catalog cores and `:backend:orchestration` pass.

### WP9: Rename the report and price ports

- **Status**: done
- **Depends on**: WP8
- **Files**: `domain/report/core/inbound/GetReportUseCase.java`,
  `domain/report/core/inbound/GetPriceHistoryUseCase.java`,
  `domain/report/core/service/ReportService.java`,
  `domain/report/core/service/PriceHistoryService.java`,
  `orchestration/preview/inbound/PreviewCutter.java`,
  `orchestration/preview/inbound/GetAnalysisPreviewUseCaseTest.java`,
  `orchestration/report/inbound/GetAnalysisReportUseCase.java`,
  `orchestration/report/inbound/GetAnalysisPricesUseCase.java`
- **Steps**:
  - [x] Rename the two ports as in "Design".
- **Tests**: the existing tests of the report core and `:backend:orchestration` pass.

### WP10: Rename GetCatalogUseCase

- **Status**: done
- **Depends on**: WP9
- **Files**: `domain/catalog/core/inbound/GetCatalogUseCase.java`,
  `domain/catalog/core/service/CatalogService.java`,
  `bff/delegate/impl/CatalogApiDelegateImpl.java`,
  `orchestration/analysisdefaults/inbound/ManageAnalysisDefaultsUseCase.java`,
  `orchestration/analysisstart/inbound/StartAnalysisWithDefaultsUseCase.java`,
  `orchestration/analysisstart/inbound/StartAnalysisWithDefaultsUseCaseTest.java`,
  `orchestration/health/inbound/SystemHealthUseCase.java`,
  `orchestration/health/inbound/SystemHealthUseCaseTest.java`
- **Steps**:
  - [x] Rename the port as in "Design".
- **Tests**: the existing tests of the catalog core, `:backend:bff:impl` and `:backend:orchestration` pass.

### WP11: Rename the preset and secret ports

- **Status**: done
- **Depends on**: WP10
- **Files**: `domain/settings/core/inbound/ManagePresetsUseCase.java`,
  `domain/settings/core/inbound/ManageSecretsUseCase.java`,
  `domain/settings/core/service/PresetService.java`,
  `domain/settings/core/service/SecretsService.java`,
  `bff/delegate/impl/SettingsApiDelegateImpl.java`,
  `orchestration/health/inbound/SystemHealthUseCase.java`,
  `orchestration/health/inbound/SystemHealthUseCaseTest.java`
- **Steps**:
  - [x] Rename the two ports as in "Design".
- **Tests**: the existing tests of the settings core, `:backend:bff:impl` and `:backend:orchestration` pass.

### WP12: Rename AssignUnownedPresetsUseCase and ManageRolesUseCase

- **Status**: done
- **Depends on**: WP11
- **Files**: `domain/settings/core/inbound/AssignUnownedPresetsUseCase.java`,
  `domain/identity/core/inbound/ManageRolesUseCase.java`,
  `domain/settings/core/service/PresetService.java`,
  `domain/identity/core/service/RoleManagementService.java`,
  `bff/delegate/impl/RolesApiDelegateImpl.java`,
  `orchestration/ownership/inbound/AssignPresetOwnersUseCase.java`,
  `orchestration/ownership/inbound/AssignPresetOwnersUseCaseTest.java`
- **Steps**:
  - [x] Rename the two ports as in "Design".
- **Tests**: the existing tests of the settings and identity cores, `:backend:bff:impl` and `:backend:orchestration` pass.

### WP13: Rename the authentication ports

- **Status**: done
- **Depends on**: WP12
- **Files**: `domain/identity/core/inbound/AuthenticateUseCase.java`,
  `domain/identity/core/inbound/ChangePasswordUseCase.java`,
  `domain/identity/core/service/AuthenticationService.java`,
  `domain/identity/core/service/PasswordChangeService.java`,
  `bff/delegate/impl/AuthApiDelegateImpl.java`,
  `bff/delegate/impl/config/SecurityConfig.java`,
  `bff/delegate/impl/config/RoleAuthoritiesRefreshFilter.java`,
  `bff/delegate/impl/config/RoleAuthoritiesRefreshFilterTest.java`
- **Steps**:
  - [x] Rename the two ports as in "Design".
- **Tests**: the existing tests of the identity core and `:backend:bff:impl` pass.

### WP14: Rename the user management ports

- **Status**: done
- **Depends on**: WP13
- **Files**: `domain/identity/core/inbound/ManageUsersUseCase.java`,
  `domain/identity/core/inbound/UnlockUserUseCase.java`,
  `domain/identity/core/inbound/CreateFirstAdminUseCase.java`,
  `domain/identity/core/service/UserManagementService.java`,
  `domain/identity/core/service/UserLockService.java`,
  `domain/identity/core/service/FirstAdminService.java`,
  `bff/delegate/impl/UsersApiDelegateImpl.java`,
  `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/FirstAdminIntegrationTest.java`
- **Steps**:
  - [x] Rename the three ports as in "Design".
- **Tests**: the existing tests of the identity core, `:backend:bff:impl` and `FirstAdminIntegrationTest` pass.

### WP15: Rename UserDirectoryUseCase

- **Status**: open
- **Depends on**: WP14
- **Files**: `domain/identity/core/inbound/UserDirectoryUseCase.java`,
  `domain/identity/core/service/UserDirectoryService.java`,
  `bff/delegate/impl/SettingsApiDelegateImpl.java`,
  `orchestration/analysisview/inbound/ViewAnalysesUseCase.java`,
  `orchestration/analysisview/inbound/ViewAnalysesUseCaseTest.java`,
  `orchestration/ownership/inbound/AssignAnalysisOwnersUseCase.java`,
  `orchestration/ownership/inbound/AssignAnalysisOwnersUseCaseTest.java`,
  `orchestration/ownership/inbound/AssignPresetOwnersUseCase.java`,
  `orchestration/ownership/inbound/AssignPresetOwnersUseCaseTest.java`,
  `orchestration/report/inbound/ImportExistingRunsUseCase.java`,
  `orchestration/report/inbound/ImportExistingRunsUseCaseTest.java`,
  `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/PresetOwnershipIntegrationTest.java`,
  `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/AnalysisOwnershipIntegrationTest.java`
- **Steps**:
  - [ ] Rename the port as in "Design". It has 13 files, over the cap of 8: one port with that many
    users cannot be split without a temporary alias interface, and every change is the same
    one-line type rename.
- **Tests**: the existing tests of the identity core, `:backend:bff:impl`, `:backend:orchestration`
  and the two ownership integration tests pass.

### WP16: Rules for UseCase and Port names

- **Status**: open
- **Depends on**: WP15
- **Files**: `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/ArchitectureTest.java`,
  `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Change and add the rules named in "Design".
  - [ ] In the two comments of `checkstyle.xml` on the Javadoc scope, "use cases and ports" becomes
    "inbound and outbound ports".
- **Tests**: `ArchitectureTest` passes; adding a `*UseCase` interface to a domain core's `inbound`
  makes `use_cases_live_in_orchestration_inbound_packages` and `domain_services_implement_inbound_ports`
  fail (checked locally, not committed).

## Tests

After WP16, `grep -rn "UseCase" artifact/backend --include=*.java` finds only orchestration types,
their tests and the rule in `ArchitectureTest`; `mise run build` and `mise run check` pass.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-package-structure.md` "Package layout" | `domain/<d>/core/inbound/`: `*Port` interfaces (and the types they hand out); `service/`: the `@Service` beans implement the inbound ports |
| Text | `docs/coding-convention/backend-java-package-structure.md` "Where each class kind goes" | the `*UseCase` row: only `orchestration.<f>.inbound`, rule `use_cases_live_in_orchestration_inbound_packages`; new rows for `*Port` (`core.inbound` or `core.outbound`, `ports_live_in_port_packages`) and for the interfaces a domain service implements (`domain_services_implement_inbound_ports`) |
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Javadoc" and the `ClassFanOutComplexity` row | "use cases and ports" → "inbound and outbound ports"; "a use case and a mapper per endpoint" → "an inbound port or use case and a mapper per endpoint" |
| Text | `CLAUDE.md` "Backend modules" | "`inbound` use cases" → "`inbound` ports" |
| Text | `docs/login-and-backend.md`, the `PUT /api/analyses/{id}/visibility` row | "checked by the use case" → "checked by the analysis domain" |
| Screenshot | none | no page changes |

## Out of scope

- #273 (domain `inbound` holds only interfaces, records move to `model`): planned on its own. It
  touches the same `inbound/` line of the package structure document and `ArchitectureTest`; the
  second of the two to merge takes main in.
- The plans in `.plans/` keep the old names: they record what was done.

## Open questions

None. The developer decided to keep the 15 sequential packages (8 files each, WP15 excepted) so each
compiles alone.
