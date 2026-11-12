# Plan: Clean up the comments of the report and catalog domains

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 6 of 9 for this issue; depends on: `.plans/276-redundant-comment-checks.md`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The comments and Javadoc of the report and catalog domains pass the test of [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md), and the rules `TAP-J1`, `TAP-C1` and `TAP-C2` apply to it: the pending block for `domain/report`, `domain/catalog` that plan 1 put into `config/checkstyle/checkstyle.xml` is gone, so the build fails on any redundant Javadoc or comment the rules can see there.

## Affected parts and their conventions

- Backend: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) ("Existing comments" is the triage), [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md) (`TAP-J1`, `TAP-C*`)

## Design

- The packages name every file of these modules that held a Javadoc or a comment on `main` at `d0e86794` (not `package-info.java`, which keeps its Javadoc). A path is relative to `artifact/backend/<module>/src/<source set>/java/tr/girgin/backend/trading/analysis/platform/<module package>/` and starts with the source set (`main/`, `testFixtures/`, `test/`); the module package of `bff/api` is `bff/controller/api`, of `bff/impl` `bff/delegate/impl`, otherwise the module path. A file renamed since by another pull request is triaged under its new name.
- Every Javadoc goes, on ports too (`package-info.java` aside). A why it held that passes the test of "Existing comments" becomes a `//` comment above the declaration; an error case a `@throws` named is dropped, since the exception type the code throws shows it. Every other comment is kept when it says why and deleted otherwise. Only comments change: no code, no test, no migration.
- The last package removes the block and fixes what `mise run check` then still reports in these modules, also in a file no package names.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: domain/report/core (1/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/report/core`
- **Files**: `main/inbound/CheckReportDecisionPort.java`, `main/inbound/GetPriceHistoryPort.java`, `main/inbound/GetReportPort.java`, `main/inbound/ScanReportsPort.java`, `main/inbound/ScanRunHistoryPort.java`, `main/inbound/WatchDataDirPort.java`, `main/model/PriceBar.java`, `main/model/PriceHistory.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:report:core:test` passes unchanged

### WP2: domain/report/core (2/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/report/core`
- **Files**: `main/model/PricePoint.java`, `main/model/Report.java`, `main/model/ReportContent.java`, `main/model/ReportKey.java`, `main/model/ReportSection.java`, `main/model/ReportSource.java`, `main/model/RunHistoryEntry.java`, `main/outbound/datadir/DataDirPort.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:report:core:test` passes unchanged

### WP3: domain/report/core (3/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/report/core`
- **Files**: `main/outbound/datadir/DataDirWatchPort.java`, `main/outbound/history/RunHistoryPort.java`, `main/outbound/prices/PriceCachePort.java`, `main/service/DataDirWatchService.java`, `main/service/DecisionRatingService.java`, `main/service/PriceHistoryService.java`, `main/service/ReportService.java`, `test/service/DataDirWatchServiceTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:report:core:test` passes unchanged

### WP4: domain/report/core (4/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/report/core`
- **Files**: `test/service/PriceHistoryServiceTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:report:core:test` passes unchanged

### WP5: domain/report/adapter

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/report/adapter`
- **Files**: `main/datadir/FileSystemDataDirAdapter.java`, `main/datadir/FileSystemDataDirWatchAdapter.java`, `main/datadir/json/FullStateLog.java`, `main/history/JsonRunHistoryAdapter.java`, `main/prices/CsvPriceCacheAdapter.java`, `test/datadir/FileSystemDataDirAdapterTest.java`, `test/datadir/FileSystemDataDirWatchAdapterTest.java`, `test/history/JsonRunHistoryAdapterTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:report:adapter:test` passes unchanged

### WP6: domain/catalog/core (1/2)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/catalog/core`
- **Files**: `main/exception/CatalogUnavailableException.java`, `main/inbound/GetCatalogPort.java`, `main/inbound/GetEngineVersionPort.java`, `main/model/Catalog.java`, `main/model/EngineVersion.java`, `main/model/ModelDefaults.java`, `main/model/ModelOption.java`, `main/model/Provider.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:catalog:core:test` passes unchanged

### WP7: domain/catalog/core (2/2)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/catalog/core`
- **Files**: `main/outbound/runner/EngineInfoPort.java`, `main/service/CatalogService.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:catalog:core:test` passes unchanged

### WP8: domain/catalog/adapter

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/catalog/adapter`
- **Files**: `main/runner/DockerEngineInfoAdapter.java`, `main/runner/TaRunnerEngineInfoAdapter.java`, `main/runner/json/CatalogJson.java`, `main/runner/json/VersionJson.java`, `main/runner/support/DockerClients.java`, `main/runner/support/RunnerKind.java`, `test/runner/DockerEngineInfoAdapterTest.java`, `test/runner/TaRunnerEngineInfoAdapterTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:catalog:adapter:test` passes unchanged

### WP9: Rules on for these modules

- **Status**: open
- **Depends on**: WP1-WP8
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Remove the pending block for `domain/report`, `domain/catalog`
  - [ ] Fix every `TAP-J1` / `TAP-C*` finding left
- **Tests**: `mise run check`; `mise run gradle :backend:domain:report:core:test`, `mise run gradle :backend:domain:report:adapter:test`, `mise run gradle :backend:domain:catalog:core:test`, `mise run gradle :backend:domain:catalog:adapter:test`

## Tests

Each work package's tests. The proof that the rules now apply: a deliberate Javadoc on a private method in one of the modules fails `mise run check` (not committed).

## Docs to update

None: no document names the modules of the pending blocks (plan 1 describes them in general); plan 9, the last, removes that paragraph.

## Out of scope

- The other modules: the other `.plans/276-comments-cleanup-*.md` plans.
- SQL migrations (a merged one is never edited), Python, shell, TypeScript and Vue comments.
