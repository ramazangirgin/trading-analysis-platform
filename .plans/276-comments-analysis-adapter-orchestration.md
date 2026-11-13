# Plan: Comments cleanup: analysis adapter and orchestration

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 4 of 9 for this issue; depends on: plans 1 (`276-comment-rules`) and 2 (`276-comment-agent-steps`); implemented after plan 3 (`276-comments-analysis-core`), as every cleanup plan edits the temporary filters in `config/checkstyle/checkstyle.xml`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The Javadoc and `//` comments of `domain/analysis/adapter` and `orchestration` follow
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
  - `domain/analysis/adapter`: `artifact/backend/domain/analysis/adapter/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/adapter/`
  - `orchestration`: `artifact/backend/orchestration/src/<set>/java/tr/girgin/backend/trading/analysis/platform/orchestration/`
- The last package deletes the block `Temporary (#276, plan 4)` from `checkstyle.xml`; Checkstyle
  then reports what is left in the modules, also in files added since, and that package fixes it.
- The orchestration `<feature>/inbound` use cases are not ports (decided in plan 1): their
  Javadoc goes like any other, `@throws` with error codes included.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: `domain/analysis/adapter`: credentials, eventline, part 1 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `credentials/EnvFileCredentialsAdapter.java`, `eventline/RunnerOutputLineParser.java`, `eventline/json/RunnerOutputLine.java`, `eventline/mapper/RunnerOutputLineToRunEventMapper.java`, `eventline/mapper/RunnerOutputLineToRunStatsMapper.java`, `eventline/mapper/StringToRatingMapper.java`, `eventline/mapper/StringToRunEventTypeMapper.java`, `eventline/mapper/StringToRunOutcomeMapper.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:adapter:test` and `mise run check` pass.

### WP2: `domain/analysis/adapter`: eventstore, persistence, part 2 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `eventstore/JsonlEventStoreAdapter.java`, `persistence/AnalysisPersistenceConfiguration.java`, `persistence/JpaAnalysisRepositoryAdapter.java`, `persistence/entity/AnalysisEntity.java`, `persistence/entity/AnalysisIdEmbeddable.java`, `persistence/entity/AnalysisSpecEmbeddable.java`, `persistence/entity/OwnerIdAttributeConverter.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:adapter:test` and `mise run check` pass.

### WP3: `domain/analysis/adapter`: persistence, runlog, runner, part 3 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `persistence/entity/RunStatsEmbeddable.java`, `persistence/mapper/AnalysisIdEmbeddableToAnalysisIdMapper.java`, `persistence/mapper/AnalysisIdToAnalysisIdEmbeddableMapper.java`, `runlog/RunLogFileAdapter.java`, `runner/DockerRunnerAdapter.java`, `runner/ProcessRunnerAdapter.java`, `runner/spec/RunnerSpec.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:adapter:test` and `mise run check` pass.

### WP4: `domain/analysis/adapter`: runner, root package, eventline, persistence (main, tests), part 4 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `runner/support/DockerClients.java`, `runner/support/EventsFileTail.java`, `runner/support/RunnerKind.java`, `AdapterTestSupport.java` (test), `eventline/mapper/RunnerOutputLineToRunEventMapperTest.java` (test), `persistence/AnalysisPersistenceConventionsTest.java` (test), `persistence/JpaAnalysisRepositoryAdapterTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:adapter:test` and `mise run check` pass.

### WP5: `domain/analysis/adapter`: persistence, runner (tests), part 5 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `persistence/V2AnalysisVersionMigrationTest.java` (test), `persistence/V3AnalysisOwnerMigrationTest.java` (test), `persistence/V4AnalysisVisibilityMigrationTest.java` (test), `persistence/V5AnalysisPartialVisibilityMigrationTest.java` (test), `persistence/V6AnalysisColumnTypesMigrationTest.java` (test), `runner/DockerRunnerAdapterTest.java` (test), `runner/ProcessRunnerAdapterTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:adapter:test` and `mise run check` pass.

### WP6: `orchestration`: analysisdefaults, analysisstart, analysisview, health, part 1 of 3

- **Status**: open
- **Depends on**: none
- **Files**: `analysisdefaults/inbound/ManageAnalysisDefaultsUseCase.java`, `analysisstart/inbound/StartAnalysisWithDefaultsUseCase.java`, `analysisview/inbound/ViewAnalysesUseCase.java`, `analysisview/model/AnalysisOwner.java`, `analysisview/model/AnalysisView.java`, `health/inbound/SystemHealthUseCase.java`, `health/model/HealthCheck.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:orchestration:test` and `mise run check` pass.

### WP7: `orchestration`: health, ownership, preview, part 2 of 3

- **Status**: open
- **Depends on**: none
- **Files**: `health/model/SystemHealth.java`, `ownership/inbound/AssignAnalysisOwnersUseCase.java`, `ownership/inbound/AssignPresetOwnersUseCase.java`, `preview/inbound/GetAnalysisPreviewUseCase.java`, `preview/inbound/PreviewCutter.java`, `preview/model/AnalysisPreview.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:orchestration:test` and `mise run check` pass.

### WP8: `orchestration`: preview, report (main, tests), part 3 of 3

- **Status**: open
- **Depends on**: none
- **Files**: `preview/model/AnalysisPreviewSummary.java`, `report/inbound/GetAnalysisPricesUseCase.java`, `report/inbound/GetAnalysisReportUseCase.java`, `report/inbound/ImportExistingRunsUseCase.java`, `report/model/ImportResult.java`, `report/inbound/ImportExistingRunsUseCaseTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:orchestration:test` and `mise run check` pass.

### WP9: Remove the temporary filter

- **Status**: open
- **Depends on**: WP1–WP8
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Delete the block `Temporary (#276, plan 4)`, and fix what Checkstyle then reports in the modules
- **Tests**: `mise run build` green; the Checkstyle tasks of the modules report nothing.

## Tests

Each work package's tests; the last one proves that the rules hold in the modules without a filter.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Temporary filters (#276)" | The row of plan 4 goes |

## Out of scope

- The other modules: their own cleanup plans (the table in `.plans/276-comment-rules.md`).
- New comments in these modules after the merge: the agents' comments step (plan 2) and the rules.
