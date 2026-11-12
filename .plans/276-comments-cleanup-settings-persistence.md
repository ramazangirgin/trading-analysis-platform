# Plan: Clean up the comments of the settings domain and the persistence library

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 7 of 9 for this issue; depends on: `.plans/276-redundant-comment-checks.md`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The comments and Javadoc of the settings domain and the persistence library pass the test of [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md), and the rules `TAP-J1`, `TAP-C1` and `TAP-C2` apply to it: the pending block for `domain/settings`, `library/persistence` that plan 1 put into `config/checkstyle/checkstyle.xml` is gone, so the build fails on any redundant Javadoc or comment the rules can see there.

## Affected parts and their conventions

- Backend: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) ("Existing comments" is the triage), [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md) (`TAP-J1`, `TAP-C*`)

## Design

- The packages name every file of these modules that held a Javadoc or a comment on `main` at `d0e86794` (not `package-info.java`, which keeps its Javadoc). A path is relative to `artifact/backend/<module>/src/<source set>/java/tr/girgin/backend/trading/analysis/platform/<module package>/` and starts with the source set (`main/`, `testFixtures/`, `test/`); the module package of `bff/api` is `bff/controller/api`, of `bff/impl` `bff/delegate/impl`, otherwise the module path. A file renamed since by another pull request is triaged under its new name.
- Every Javadoc goes, on ports too (`package-info.java` aside). A why it held that passes the test of "Existing comments" becomes a `//` comment above the declaration; an error case a `@throws` named is dropped, since the exception type the code throws shows it. Every other comment is kept when it says why and deleted otherwise. Only comments change: no code, no test, no migration.
- The last package removes the block and fixes what `mise run check` then still reports in these modules, also in a file no package names.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: domain/settings/core (1/2)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/settings/core`
- **Files**: `main/inbound/AssignUnownedPresetsPort.java`, `main/inbound/ManagePresetsPort.java`, `main/inbound/ManageSecretsPort.java`, `main/model/AnalysisDefaults.java`, `main/model/OwnerId.java`, `main/model/Preset.java`, `main/model/PresetAccess.java`, `main/model/SecretSource.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:settings:core:test` passes unchanged

### WP2: domain/settings/core (2/2)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/settings/core`
- **Files**: `main/model/SecretStatus.java`, `main/outbound/persistence/AnalysisDefaultsRepositoryPort.java`, `main/outbound/persistence/PresetRepositoryPort.java`, `main/outbound/secrets/SecretStorePort.java`, `main/service/PresetService.java`, `main/service/SecretsService.java`, `test/service/PresetServiceTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:settings:core:test` passes unchanged

### WP3: domain/settings/adapter (1/2)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/settings/adapter`
- **Files**: `main/persistence/JpaPresetRepositoryAdapter.java`, `main/persistence/PresetJpaRepository.java`, `main/persistence/SettingsPersistenceConfiguration.java`, `main/persistence/entity/PresetEntity.java`, `main/persistence/entity/PresetIdEmbeddable.java`, `main/persistence/mapper/PresetIdEmbeddableToPresetIdMapper.java`, `main/persistence/mapper/PresetIdToPresetIdEmbeddableMapper.java`, `main/persistence/mapper/PresetToPresetEntityMapper.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:settings:adapter:test` passes unchanged

### WP4: domain/settings/adapter (2/2)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/settings/adapter`
- **Files**: `main/secrets/DotenvSecretStoreAdapter.java`, `test/persistence/SettingsPersistenceConventionsTest.java`, `test/persistence/V2SettingsVersionMigrationTest.java`, `test/persistence/V4SettingsOwnerMigrationTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:settings:adapter:test` passes unchanged

### WP5: library/persistence (1/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/library/persistence`
- **Files**: `main/ClockDateTimeProvider.java`, `main/JpaAuditingConfiguration.java`, `testFixtures/CatalogQueries.java`, `testFixtures/ColumnTypeCheck.java`, `testFixtures/DatabaseNamingCheck.java`, `testFixtures/DomainMigrationIsolationCheck.java`, `testFixtures/DomainPersistenceConventionsTest.java`, `testFixtures/EntitySchemaRule.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:library:persistence:test` passes unchanged

### WP6: library/persistence (2/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/library/persistence`
- **Files**: `testFixtures/JpaAdapterTest.java`, `testFixtures/MutableTestClock.java`, `testFixtures/PostgresTestContainer.java`, `testFixtures/TestClockConfiguration.java`, `testFixtures/TestDatabaseConfiguration.java`, `testFixtures/TestcontainersLifecycleContextCustomizerFactory.java`, `test/ColumnTypeCheckTest.java`, `test/DatabaseNamingCheckTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:library:persistence:test` passes unchanged

### WP7: library/persistence (3/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/library/persistence`
- **Files**: `test/DomainMigrationIsolationCheckTest.java`, `test/EntitySchemaRuleTest.java`, `test/TestDatabaseConfigurationTest.java`, `test/fixture/entities/CollectionWithoutSchemaEntity.java`, `test/fixture/entities/CorrectEntity.java`, `test/fixture/entities/NoSchemaEntity.java`, `test/fixture/entities/NoTableEntity.java`, `test/fixture/entities/OtherSchemaEntity.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:library:persistence:test` passes unchanged

### WP8: library/persistence (4/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/library/persistence`
- **Files**: `test/fixture/entities/UnqualifiedCastEntity.java`, `test/fixture/entities/UnqualifiedTypeEntity.java`, `test/fixture/entities/empty/NotAnEntity.java`, `test/fixture/sample/SampleEntity.java`, `test/fixture/sample/SamplePersistenceConfiguration.java`, `test/fixture/sample/SamplePersistenceConventionsTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:library:persistence:test` passes unchanged

### WP9: Rules on for these modules

- **Status**: open
- **Depends on**: WP1-WP8
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Remove the pending block for `domain/settings`, `library/persistence`
  - [ ] Fix every `TAP-J1` / `TAP-C*` finding left
- **Tests**: `mise run check`; `mise run gradle :backend:domain:settings:core:test`, `mise run gradle :backend:domain:settings:adapter:test`, `mise run gradle :backend:library:persistence:test`

## Tests

Each work package's tests. The proof that the rules now apply: a deliberate Javadoc on a private method in one of the modules fails `mise run check` (not committed).

## Docs to update

None: no document names the modules of the pending blocks (plan 1 describes them in general); plan 9, the last, removes that paragraph.

## Out of scope

- The other modules: the other `.plans/276-comments-cleanup-*.md` plans.
- SQL migrations (a merged one is never edited), Python, shell, TypeScript and Vue comments.
