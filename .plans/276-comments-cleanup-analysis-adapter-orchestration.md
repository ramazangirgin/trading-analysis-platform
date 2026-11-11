# Plan: Clean up the comments of the analysis adapter, the mapper library and orchestration

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 4 of 9 for this issue; depends on: `.plans/276-redundant-comment-checks.md`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The comments and Javadoc of the analysis adapter, the mapper library and orchestration pass the test of [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md), and the rules `TAP-J1` to `TAP-J3`, `TAP-C1` and `TAP-C2` apply to it: the pending block for `domain/analysis/adapter`, `library/mapper`, `orchestration` that plan 1 put into `config/checkstyle/checkstyle.xml` is gone, so the build fails on any redundant Javadoc or comment the rules can see there.

## Affected parts and their conventions

- Backend: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) ("Existing comments" is the triage), [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md) (`TAP-J*`, `TAP-C*`)

## Design

- The packages name every file of these modules that held a Javadoc or a comment on `main` at `d0e86794` (not `package-info.java`, which keeps its Javadoc). A path is relative to `artifact/backend/<module>/src/<source set>/java/tr/girgin/backend/trading/analysis/platform/<module package>/` and starts with the source set (`main/`, `testFixtures/`, `test/`); the module package of `bff/api` is `bff/controller/api`, of `bff/impl` `bff/delegate/impl`, otherwise the module path. A file renamed since by another pull request is triaged under its new name.
- Each comment and Javadoc is triaged as "Existing comments" says: kept, shortened to the one sentence `TAP-J3` allows (an error case into `@throws`), turned into a `//` comment (a why in a Javadoc outside a port), or deleted. Only comments change: no code, no test, no migration.
- The last package removes the block and fixes what `mise run check` then still reports in these modules, also in a file no package names.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: domain/analysis/adapter (1/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/adapter`
- **Files**: `main/credentials/EnvFileCredentialsAdapter.java`, `main/eventline/RunnerOutputLineParser.java`, `main/eventline/json/RunnerOutputLine.java`, `main/eventline/mapper/RunnerOutputLineToRunEventMapper.java`, `main/eventline/mapper/RunnerOutputLineToRunStatsMapper.java`, `main/eventline/mapper/StringToRatingMapper.java`, `main/eventline/mapper/StringToRunEventTypeMapper.java`, `main/eventline/mapper/StringToRunOutcomeMapper.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:adapter:test` passes unchanged

### WP2: domain/analysis/adapter (2/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/adapter`
- **Files**: `main/eventstore/JsonlEventStoreAdapter.java`, `main/persistence/AnalysisPersistenceConfiguration.java`, `main/persistence/JpaAnalysisRepositoryAdapter.java`, `main/persistence/entity/AnalysisEntity.java`, `main/persistence/entity/AnalysisIdEmbeddable.java`, `main/persistence/entity/AnalysisSpecEmbeddable.java`, `main/persistence/entity/OwnerIdAttributeConverter.java`, `main/persistence/entity/RunStatsEmbeddable.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:adapter:test` passes unchanged

### WP3: domain/analysis/adapter (3/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/adapter`
- **Files**: `main/persistence/mapper/AnalysisIdEmbeddableToAnalysisIdMapper.java`, `main/persistence/mapper/AnalysisIdToAnalysisIdEmbeddableMapper.java`, `main/runlog/RunLogFileAdapter.java`, `main/runner/DockerRunnerAdapter.java`, `main/runner/ProcessRunnerAdapter.java`, `main/runner/spec/RunnerSpec.java`, `main/runner/support/DockerClients.java`, `main/runner/support/EventsFileTail.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:adapter:test` passes unchanged

### WP4: domain/analysis/adapter (4/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/adapter`
- **Files**: `main/runner/support/RunnerKind.java`, `test/AdapterTestSupport.java`, `test/eventline/mapper/RunnerOutputLineToRunEventMapperTest.java`, `test/persistence/AnalysisPersistenceConventionsTest.java`, `test/persistence/JpaAnalysisRepositoryAdapterTest.java`, `test/persistence/V2AnalysisVersionMigrationTest.java`, `test/persistence/V3AnalysisOwnerMigrationTest.java`, `test/persistence/V4AnalysisVisibilityMigrationTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:adapter:test` passes unchanged

### WP5: domain/analysis/adapter (5/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/adapter`
- **Files**: `test/persistence/V5AnalysisPartialVisibilityMigrationTest.java`, `test/persistence/V6AnalysisColumnTypesMigrationTest.java`, `test/runner/DockerRunnerAdapterTest.java`, `test/runner/ProcessRunnerAdapterTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:adapter:test` passes unchanged

### WP6: library/mapper

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/library/mapper`
- **Files**: `main/EnumToLowerCaseNameMapper.java`, `main/OptionalInstantToInstantMapper.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:library:mapper:test` passes unchanged

### WP7: orchestration (1/3)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/orchestration`
- **Files**: `main/analysisdefaults/inbound/ManageAnalysisDefaultsUseCase.java`, `main/analysisstart/inbound/StartAnalysisWithDefaultsUseCase.java`, `main/analysisview/inbound/ViewAnalysesUseCase.java`, `main/analysisview/model/AnalysisOwner.java`, `main/analysisview/model/AnalysisView.java`, `main/health/inbound/SystemHealthUseCase.java`, `main/health/model/HealthCheck.java`, `main/health/model/SystemHealth.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:orchestration:test` passes unchanged

### WP8: orchestration (2/3)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/orchestration`
- **Files**: `main/ownership/inbound/AssignAnalysisOwnersUseCase.java`, `main/ownership/inbound/AssignPresetOwnersUseCase.java`, `main/preview/inbound/GetAnalysisPreviewUseCase.java`, `main/preview/inbound/PreviewCutter.java`, `main/preview/model/AnalysisPreview.java`, `main/preview/model/AnalysisPreviewSummary.java`, `main/report/inbound/GetAnalysisPricesUseCase.java`, `main/report/inbound/GetAnalysisReportUseCase.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:orchestration:test` passes unchanged

### WP9: orchestration (3/3)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/orchestration`
- **Files**: `main/report/inbound/ImportExistingRunsUseCase.java`, `main/report/model/ImportResult.java`, `test/report/inbound/ImportExistingRunsUseCaseTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:orchestration:test` passes unchanged

### WP10: Rules on for these modules

- **Status**: open
- **Depends on**: WP1-WP9
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Remove the pending block for `domain/analysis/adapter`, `library/mapper`, `orchestration`
  - [ ] Fix every `TAP-J*` / `TAP-C*` finding left
- **Tests**: `mise run check`; `mise run gradle :backend:domain:analysis:adapter:test`, `mise run gradle :backend:library:mapper:test`, `mise run gradle :backend:orchestration:test`

## Tests

Each work package's tests. The proof that the rules now apply: a deliberate Javadoc on a private method in one of the modules fails `mise run check` (not committed).

## Docs to update

None: no document names the modules of the pending blocks (plan 1 describes them in general); plan 9, the last, removes that paragraph.

## Out of scope

- The other modules: the other `.plans/276-comments-cleanup-*.md` plans.
- SQL migrations (a merged one is never edited), Python, shell, TypeScript and Vue comments.

## Open questions

- If plan 1's open question makes orchestration's `<feature>/inbound` use cases ports, their Javadoc is triaged like a port's (shortened, `@throws` kept) instead of removed.
