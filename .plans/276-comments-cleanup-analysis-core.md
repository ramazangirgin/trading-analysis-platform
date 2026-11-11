# Plan: Clean up the comments of the analysis domain core

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 3 of 9 for this issue; depends on: `.plans/276-redundant-comment-checks.md`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The comments and Javadoc of the analysis domain core pass the test of [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md), and the rules `TAP-J1` to `TAP-J3`, `TAP-C1` and `TAP-C2` apply to it: the pending block for `domain/analysis/core` that plan 1 put into `config/checkstyle/checkstyle.xml` is gone, so the build fails on any redundant Javadoc or comment the rules can see there.

## Affected parts and their conventions

- Backend: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) ("Existing comments" is the triage), [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md) (`TAP-J*`, `TAP-C*`)

## Design

- The packages name every file of these modules that held a Javadoc or a comment on `main` at `d0e86794` (not `package-info.java`, which keeps its Javadoc). A path is relative to `artifact/backend/<module>/src/<source set>/java/tr/girgin/backend/trading/analysis/platform/<module package>/` and starts with the source set (`main/`, `testFixtures/`, `test/`); the module package of `bff/api` is `bff/controller/api`, of `bff/impl` `bff/delegate/impl`, otherwise the module path. A file renamed since by another pull request is triaged under its new name.
- Each comment and Javadoc is triaged as "Existing comments" says: kept, shortened to the one sentence `TAP-J3` allows (an error case into `@throws`), turned into a `//` comment (a why in a Javadoc outside a port), or deleted. Only comments change: no code, no test, no migration.
- The last package removes the block and fixes what `mise run check` then still reports in these modules, also in a file no package names.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: domain/analysis/core (1/7)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/core`
- **Files**: `main/exception/AnalysisError.java`, `main/inbound/AnalysisRights.java`, `main/inbound/AssignUnownedAnalysesPort.java`, `main/inbound/ChangeAnalysisVisibilityPort.java`, `main/inbound/EventSubscription.java`, `main/inbound/GetAnalysisPort.java`, `main/inbound/GetAnalysisRightsPort.java`, `main/inbound/GetPartialAnalysisPort.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:core:test` passes unchanged

### WP2: domain/analysis/core (2/7)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/core`
- **Files**: `main/inbound/ListAnalysesPort.java`, `main/inbound/ListPartialAnalysesPort.java`, `main/inbound/ReadAnalysisLogsPort.java`, `main/inbound/RegisterExternalAnalysisPort.java`, `main/inbound/RerunAnalysisPort.java`, `main/inbound/RunEventListener.java`, `main/inbound/StartAnalysisPort.java`, `main/inbound/StopAnalysisPort.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:core:test` passes unchanged

### WP3: domain/analysis/core (3/7)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/core`
- **Files**: `main/inbound/SubscribeAnalysisEventsPort.java`, `main/inbound/ValidateExternalAnalysesPort.java`, `main/model/Analysis.java`, `main/model/AnalysisFilter.java`, `main/model/AnalysisScope.java`, `main/model/AnalysisSource.java`, `main/model/AnalysisSpec.java`, `main/model/Analyst.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:core:test` passes unchanged

### WP4: domain/analysis/core (4/7)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/core`
- **Files**: `main/model/ExternalAnalysis.java`, `main/model/ExternalRegistration.java`, `main/model/ExternalRun.java`, `main/model/OwnerId.java`, `main/model/Rating.java`, `main/model/RunEvent.java`, `main/model/RunEventType.java`, `main/model/RunOutcome.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:core:test` passes unchanged

### WP5: domain/analysis/core (5/7)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/core`
- **Files**: `main/model/RunStats.java`, `main/model/Visibility.java`, `main/outbound/credentials/CredentialsPort.java`, `main/outbound/eventstore/EventStorePort.java`, `main/outbound/persistence/AnalysisRepositoryPort.java`, `main/outbound/runlog/RunLogPort.java`, `main/outbound/runner/RunEventSink.java`, `main/outbound/runner/RunHandle.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:core:test` passes unchanged

### WP6: domain/analysis/core (6/7)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/core`
- **Files**: `main/outbound/runner/RunnerPort.java`, `main/service/AnalysisAccessService.java`, `main/service/AnalysisEventService.java`, `main/service/AnalysisIdService.java`, `main/service/AnalysisLogService.java`, `main/service/AnalysisService.java`, `main/service/AnalysisSpecService.java`, `main/service/AnalysisTransitionService.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:core:test` passes unchanged

### WP7: domain/analysis/core (7/7)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/analysis/core`
- **Files**: `main/service/AnalysisVisibilityService.java`, `main/service/ExternalAnalysisService.java`, `test/service/AnalysisEventServiceTest.java`, `test/service/ExternalAnalysisValidationServiceTest.java`, `test/service/Fakes.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:analysis:core:test` passes unchanged

### WP8: Rules on for these modules

- **Status**: open
- **Depends on**: WP1-WP7
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Remove the pending block for `domain/analysis/core`
  - [ ] Fix every `TAP-J*` / `TAP-C*` finding left
- **Tests**: `mise run check`; `mise run gradle :backend:domain:analysis:core:test`

## Tests

Each work package's tests. The proof that the rules now apply: a deliberate Javadoc on a private method in one of the modules fails `mise run check` (not committed).

## Docs to update

None: no document names the modules of the pending blocks (plan 1 describes them in general); plan 9, the last, removes that paragraph.

## Out of scope

- The other modules: the other `.plans/276-comments-cleanup-*.md` plans.
- SQL migrations (a merged one is never edited), Python, shell, TypeScript and Vue comments.
