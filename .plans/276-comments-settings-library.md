# Plan: Comments cleanup: settings and the libraries

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 7 of 9 for this issue; depends on: plans 1 (`276-comment-rules`) and 2 (`276-comment-agent-steps`); implemented after plan 6 (`276-comments-report-catalog`), as every cleanup plan edits the temporary filters in `config/checkstyle/checkstyle.xml`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The Javadoc and `//` comments of `domain/settings/core`, `domain/settings/adapter`, `library/mapper` and `library/persistence` follow
[repository-code-comments.md](../docs/coding-convention/repository-code-comments.md): what a reader
would lose stays, as a `//` comment, and the rest is gone. The plan's temporary filter is removed,
so `TAP-J1` reports any new Javadoc in them.

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
  - `domain/settings/core`: `artifact/backend/domain/settings/core/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/settings/core/`
  - `domain/settings/adapter`: `artifact/backend/domain/settings/adapter/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/settings/adapter/`
  - `library/mapper`: `artifact/backend/library/mapper/src/<set>/java/tr/girgin/backend/trading/analysis/platform/library/mapper/`
  - `library/persistence`: `artifact/backend/library/persistence/src/<set>/java/tr/girgin/backend/trading/analysis/platform/library/persistence/`
- The last package deletes the block `Temporary (#276, plan 7)` from `checkstyle.xml`; Checkstyle
  then reports what is left in the modules, also in files added since, and that package fixes it.
- The optimistic-locking promise on a repository port's write method is deleted, not moved:
  `backend-java-persistence.md` "The port promises" states it (plan 1).
- `library/persistence` has a `testFixtures` source set: its Checkstyle task is
  `checkstyleTestFixtures`, which the last package runs too. The library names no domain, also in
  the comments it keeps ([library README](../artifact/backend/library/persistence/README.md)).

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: `domain/settings/core`: inbound, model, part 1 of 2

- **Status**: open
- **Depends on**: none
- **Files**: `inbound/AssignUnownedPresetsPort.java`, `inbound/ManagePresetsPort.java`, `inbound/ManageSecretsPort.java`, `model/AnalysisDefaults.java`, `model/OwnerId.java`, `model/Preset.java`, `model/PresetAccess.java`, `model/SecretSource.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:settings:core:test` and `mise run check` pass.

### WP2: `domain/settings/core`: model, outbound, service (main, tests), part 2 of 2

- **Status**: open
- **Depends on**: none
- **Files**: `model/SecretStatus.java`, `outbound/persistence/AnalysisDefaultsRepositoryPort.java`, `outbound/persistence/PresetRepositoryPort.java`, `outbound/secrets/SecretStorePort.java`, `service/PresetService.java`, `service/SecretsService.java`, `service/PresetServiceTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:settings:core:test` and `mise run check` pass.

### WP3: `domain/settings/adapter`: persistence, part 1 of 2

- **Status**: open
- **Depends on**: none
- **Files**: `persistence/JpaPresetRepositoryAdapter.java`, `persistence/PresetJpaRepository.java`, `persistence/SettingsPersistenceConfiguration.java`, `persistence/entity/PresetEntity.java`, `persistence/entity/PresetIdEmbeddable.java`, `persistence/mapper/PresetIdEmbeddableToPresetIdMapper.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:settings:adapter:test` and `mise run check` pass.

### WP4: `domain/settings/adapter`: persistence, secrets (main, tests), part 2 of 2

- **Status**: open
- **Depends on**: none
- **Files**: `persistence/mapper/PresetIdToPresetIdEmbeddableMapper.java`, `persistence/mapper/PresetToPresetEntityMapper.java`, `secrets/DotenvSecretStoreAdapter.java`, `persistence/SettingsPersistenceConventionsTest.java` (test), `persistence/V2SettingsVersionMigrationTest.java` (test), `persistence/V4SettingsOwnerMigrationTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:settings:adapter:test` and `mise run check` pass.

### WP5: `library/mapper`: root package

- **Status**: open
- **Depends on**: none
- **Files**: `EnumToLowerCaseNameMapper.java`, `OptionalInstantToInstantMapper.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:library:mapper:test` and `mise run check` pass.

### WP6: `library/persistence`: root package (main, test fixtures), part 1 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `ClockDateTimeProvider.java`, `JpaAuditingConfiguration.java`, `CatalogQueries.java` (testFixtures), `ColumnTypeCheck.java` (testFixtures), `DatabaseNamingCheck.java` (testFixtures), `DomainMigrationIsolationCheck.java` (testFixtures), `DomainPersistenceConventionsTest.java` (testFixtures), `EntitySchemaRule.java` (testFixtures)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:library:persistence:test` and `mise run check` pass.

### WP7: `library/persistence`: root package (test fixtures, tests), part 2 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `JpaAdapterTest.java` (testFixtures), `MutableTestClock.java` (testFixtures), `PostgresTestContainer.java` (testFixtures), `TestClockConfiguration.java` (testFixtures), `TestDatabaseConfiguration.java` (testFixtures), `TestcontainersLifecycleContextCustomizerFactory.java` (testFixtures), `ColumnTypeCheckTest.java` (test), `DatabaseNamingCheckTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:library:persistence:test` and `mise run check` pass.

### WP8: `library/persistence`: root package, fixture (tests), part 3 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `DomainMigrationIsolationCheckTest.java` (test), `EntitySchemaRuleTest.java` (test), `TestDatabaseConfigurationTest.java` (test), `fixture/entities/CollectionWithoutSchemaEntity.java` (test), `fixture/entities/CorrectEntity.java` (test), `fixture/entities/NoSchemaEntity.java` (test), `fixture/entities/NoTableEntity.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:library:persistence:test` and `mise run check` pass.

### WP9: `library/persistence`: fixture (tests), part 4 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `fixture/entities/OtherSchemaEntity.java` (test), `fixture/entities/UnqualifiedCastEntity.java` (test), `fixture/entities/UnqualifiedTypeEntity.java` (test), `fixture/entities/empty/NotAnEntity.java` (test), `fixture/sample/SampleEntity.java` (test), `fixture/sample/SamplePersistenceConfiguration.java` (test), `fixture/sample/SamplePersistenceConventionsTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:library:persistence:test` and `mise run check` pass.

### WP10: Remove the temporary filter

- **Status**: open
- **Depends on**: WP1–WP9
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Delete the block `Temporary (#276, plan 7)`, and fix what Checkstyle then reports in the modules
- **Tests**: `mise run build` green; the Checkstyle tasks of the modules report nothing.

## Tests

Each work package's tests; the last one proves that the rules hold in the modules without a filter.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Temporary filters (#276)" | The row of plan 7 goes |
| Text | `artifact/backend/library/persistence/README.md` "Examples point at the made-up domain" | No Javadoc example any more: the examples are in this README and the comments |
| Text | `docs/coding-convention/backend-java-package-structure.md` (the library that names no domain) | "comments or Javadoc examples" becomes "comments" |

## Out of scope

- The other modules: their own cleanup plans (the table in `.plans/276-comment-rules.md`).
- New comments in these modules after the merge: the agents' comments step (plan 2) and the rules.
