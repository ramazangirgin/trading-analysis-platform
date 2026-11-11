# Plan: Collaborators named after their type: domains and library/mapper

- **Issue**: #275 (Name injected collaborators after their type: enforce, document, refactor)
- **Plan**: 2 of 3 for this issue; depends on: `275-collaborator-name-rule`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every declaration in the domain modules (`domain/<d>/core`, `domain/<d>/adapter`) and in
`library/mapper` is named as rule `TAP-D9` asks (`UserRepositoryPort userRepositoryPort`), and the
rule's temporary filter no longer exempts these modules. No behaviour changes.

## Affected parts and their conventions

- Backend: [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md)
  (rule `TAP-D9` and its temporary filter, added by plan 1).

## Design

**The change, the same in every package.** Rename each field, parameter and local variable that
`TAP-D9` reports, and its uses in the file. The names are private to the file (no record component
has a collaborator type today), so packages do not depend on each other. Pick the type's lower
camel case; take a qualifier (`strictUserManagementService`, `specJsonMapper`) only where a scope
holds two of the type or the plain name would hide a field (`HiddenField`). Where the old name said
something the type does not (`followUps`, `specWriter`), the qualifier keeps it.

**Finding the declarations.** The filter exempts these modules until WP13, so a package removes
`domain|library[\\/]mapper` from the `TAP-D9` filter in `config/checkstyle/checkstyle.xml` locally,
runs `mise run gradle :backend:domain:<d>:<core|adapter>:checkstyleMain
:backend:domain:<d>:<core|adapter>:checkstyleTest` and restores the file before committing. The
file lists below come from a regex count on `main` at f3def285 (about 230 declarations in 84 files);
a file the check reports that no package names goes with WP13.

**Paths.** A path in **Files** is the package path below
`tr/girgin/backend/trading/analysis/platform/` and names its module: `domain/analysis/adapter/...` is
in `artifact/backend/domain/analysis/adapter`. `*Test.java` and `Fakes.java` are under `src/test`,
the others under `src/main`.

## Work packages

Packages without a dependency between them can be implemented in parallel. Each package's tests:
the tests of its modules pass unchanged (`mise run gradle :backend:domain:<d>:<core|adapter>:test`)
and Checkstyle reports no `TAP-D9` in its files with the filter lifted.

### WP1: Analysis adapter, main and runner tests

- **Status**: done
- **Depends on**: none
- **Files**: `domain/analysis/adapter/eventline/RunnerOutputLineParser.java`,
  `domain/analysis/adapter/eventstore/JsonlEventStoreAdapter.java`,
  `domain/analysis/adapter/persistence/JpaAnalysisRepositoryAdapter.java`,
  `domain/analysis/adapter/runner/DockerRunnerAdapter.java`,
  `domain/analysis/adapter/runner/ProcessRunnerAdapter.java`,
  `domain/analysis/adapter/runner/DockerRunnerAdapterTest.java`,
  `domain/analysis/adapter/runner/ProcessRunnerAdapterTest.java`,
  `domain/analysis/adapter/eventstore/JsonlEventStoreAdapterTest.java`
- **Tests**: as above.

### WP2: Analysis adapter tests

- **Status**: done (two tests of `JpaAnalysisRepositoryAdapterTest` moved to `JpaAnalysisOwnerTest` to stay under Checkstyle's FileLength)
- **Depends on**: none
- **Files**: `domain/analysis/adapter/credentials/EnvFileCredentialsAdapterTest.java`,
  `domain/analysis/adapter/eventline/mapper/RunnerOutputLineToRunEventMapperTest.java`,
  `domain/analysis/adapter/persistence/JpaAnalysisRepositoryAdapterTest.java`,
  `domain/analysis/adapter/persistence/JpaPartialVisibilityTest.java`,
  `domain/analysis/adapter/persistence/V2AnalysisVersionMigrationTest.java`,
  `domain/analysis/adapter/persistence/V3AnalysisOwnerMigrationTest.java`,
  `domain/analysis/adapter/persistence/V4AnalysisVisibilityMigrationTest.java`,
  `domain/analysis/adapter/persistence/V5AnalysisPartialVisibilityMigrationTest.java`
- **Tests**: as above.

### WP3: Analysis core services

- **Status**: done
- **Depends on**: none
- **Files**: `domain/analysis/core/service/AnalysisEventService.java`,
  `domain/analysis/core/service/AnalysisListService.java`,
  `domain/analysis/core/service/AnalysisLogService.java`,
  `domain/analysis/core/service/AnalysisService.java`,
  `domain/analysis/core/service/AnalysisVisibilityService.java`,
  `domain/analysis/core/service/ExternalAnalysisService.java`,
  `domain/analysis/core/service/PartialAnalysisService.java`,
  `domain/analysis/adapter/persistence/V6AnalysisColumnTypesMigrationTest.java`
- **Tests**: as above.

### WP4: Analysis core tests

- **Status**: done
- **Depends on**: none
- **Files**: `domain/analysis/core/service/AnalysisEventServiceTest.java`,
  `domain/analysis/core/service/AnalysisServiceInvalidIdTest.java`,
  `domain/analysis/core/service/AnalysisServiceTest.java`,
  `domain/analysis/core/service/AnalysisVisibilityServiceTest.java`,
  `domain/analysis/core/service/Fakes.java`,
  `domain/analysis/core/service/PartialAnalysisServiceTest.java`
- **Tests**: as above.

### WP5: Catalog

- **Status**: done
- **Depends on**: none
- **Files**: `domain/catalog/adapter/runner/DockerEngineInfoAdapter.java`,
  `domain/catalog/adapter/runner/TaRunnerEngineInfoAdapter.java`,
  `domain/catalog/adapter/runner/DockerEngineInfoAdapterTest.java`,
  `domain/catalog/adapter/runner/TaRunnerEngineInfoAdapterTest.java`,
  `domain/catalog/adapter/runner/mapper/CatalogJsonToCatalogMapperTest.java`,
  `domain/catalog/core/service/CatalogService.java`,
  `domain/catalog/core/service/CatalogServiceTest.java`
- **Tests**: as above.

### WP6: Identity adapter and three core services

- **Status**: done
- **Depends on**: none
- **Files**: `domain/identity/adapter/persistence/JpaRoleRepositoryAdapter.java`,
  `domain/identity/adapter/persistence/JpaUserRepositoryAdapter.java`,
  `domain/identity/adapter/password/DelegatingPasswordHasherAdapterTest.java`,
  `domain/identity/adapter/persistence/JpaIdentityRepositoryTest.java`,
  `domain/identity/adapter/persistence/V2IdentityVersionMigrationTest.java`,
  `domain/identity/core/service/AuthenticationService.java`,
  `domain/identity/core/service/UserDirectoryService.java`,
  `domain/identity/core/service/UserLockService.java`
- **Tests**: as above.

### WP7: Identity core services and their tests

- **Status**: done
- **Depends on**: none
- **Files**: `domain/identity/core/service/FirstAdminService.java`,
  `domain/identity/core/service/PasswordChangeService.java`,
  `domain/identity/core/service/RoleManagementService.java`,
  `domain/identity/core/service/UserManagementService.java`,
  `domain/identity/core/service/AuthenticationServiceTest.java`,
  `domain/identity/core/service/FirstAdminServiceTest.java`,
  `domain/identity/core/service/LoginLockoutServiceTest.java`,
  `domain/identity/core/service/PasswordChangeServiceTest.java`
- **Tests**: as above.

### WP8: Identity core tests

- **Status**: done (the fake user repository of `UserManagementServiceTest` extracted to `InMemoryUserRepository` to stay under Checkstyle's FileLength)
- **Depends on**: none
- **Files**: `domain/identity/core/service/PasswordPolicyServiceTest.java`,
  `domain/identity/core/service/RoleManagementServiceTest.java`,
  `domain/identity/core/service/UserDirectoryServiceTest.java`,
  `domain/identity/core/service/UserLockServiceTest.java`,
  `domain/identity/core/service/UserManagementServiceTest.java`
- **Tests**: as above.

### WP9: Report adapter and the data dir watch

- **Status**: done
- **Depends on**: none
- **Files**: `domain/report/adapter/datadir/FileSystemDataDirAdapter.java`,
  `domain/report/adapter/datadir/FileSystemDataDirWatchAdapter.java`,
  `domain/report/adapter/history/JsonRunHistoryAdapter.java`,
  `domain/report/adapter/datadir/FileSystemDataDirAdapterTest.java`,
  `domain/report/adapter/datadir/FileSystemDataDirWatchAdapterTest.java`,
  `domain/report/core/service/DataDirWatchService.java`,
  `domain/report/core/service/DataDirWatchServiceTest.java`
- **Tests**: as above.

### WP10: Report core and library/mapper

- **Status**: done
- **Depends on**: none
- **Files**: `domain/report/core/service/PriceHistoryService.java`,
  `domain/report/core/service/ReportService.java`,
  `domain/report/core/service/RunHistoryService.java`,
  `domain/report/core/service/ReportServiceTest.java`,
  `library/mapper/DurationToMillisMapperTest.java`,
  `library/mapper/EnumToLowerCaseNameMapperTest.java`,
  `library/mapper/OptionalInstantToInstantMapperTest.java`,
  `library/mapper/SharedMapperConfigTest.java`
- **Tests**: as above, and `mise run gradle :backend:library:mapper:test`.

### WP11: Settings adapter

- **Status**: done
- **Depends on**: none
- **Files**: `domain/settings/adapter/persistence/JpaAnalysisDefaultsRepositoryAdapter.java`,
  `domain/settings/adapter/persistence/JpaPresetRepositoryAdapter.java`,
  `domain/settings/adapter/persistence/JpaAnalysisDefaultsRepositoryAdapterTest.java`,
  `domain/settings/adapter/persistence/JpaPresetRepositoryAdapterTest.java`,
  `domain/settings/adapter/persistence/V2SettingsVersionMigrationTest.java`,
  `domain/settings/adapter/persistence/V4SettingsOwnerMigrationTest.java`,
  `domain/settings/adapter/secrets/DotenvSecretStoreAdapterTest.java`
- **Tests**: as above.

### WP12: Settings core

- **Status**: done
- **Depends on**: none
- **Files**: `domain/settings/core/service/PresetService.java`,
  `domain/settings/core/service/SecretsService.java`,
  `domain/settings/core/service/PresetServiceTest.java`,
  `domain/settings/core/service/SecretsServiceTest.java`
- **Tests**: as above.

### WP13: TAP-D9 on in the domains and library/mapper

- **Status**: done
- **Depends on**: WP1-WP12
- **Files**: `config/checkstyle/checkstyle.xml`, `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [x] Remove `domain|library[\\/]mapper` from the `TAP-D9` filter's pattern, leaving
    `[\\/]artifact[\\/]backend[\\/](bff[\\/]impl|orchestration|src[\\/]test)[\\/]`.
  - [x] Rename what the check still reports in these modules (files no package named).
- **Tests**: `mise run check` and `mise run build` pass with the narrowed filter.

## Tests

Each work package's tests; WP13 proves that no `TAP-D9` finding is left in these modules.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Suppressing a finding" | the temporary `TAP-D9` filter now exempts only `bff/impl`, `orchestration` and the application tests |
| Screenshot | none | no page changes |

## Out of scope

- `bff/impl`, `orchestration` and the application tests, and deleting the filter: plan 3.
