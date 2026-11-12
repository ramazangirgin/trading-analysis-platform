# Plan: Comments cleanup: analysis core

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 3 of 9 for this issue; depends on: plans 1 (`276-comment-rules`) and 2 (`276-comment-agent-steps`)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The Javadoc and `//` comments of `domain/analysis/core` follow
[repository-code-comments.md](../docs/coding-convention/repository-code-comments.md): what a reader
would lose stays, as a `//` comment, and the rest is gone. The plan's temporary filter is removed,
so `TAP-J1` reports any new Javadoc in it.

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
  - `domain/analysis/core`: `artifact/backend/domain/analysis/core/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/core/`
- The last package deletes the block `Temporary (#276, plan 3)` from `checkstyle.xml`; Checkstyle
  then reports what is left in the modules, also in files added since, and that package fixes it.
- A `@throws` that names an `AnalysisError` code goes with its Javadoc (decided in plan 1); the
  tests of the services prove the codes.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: `domain/analysis/core`: exception, inbound, part 1 of 7

- **Status**: open
- **Depends on**: none
- **Files**: `exception/AnalysisError.java`, `inbound/AssignUnownedAnalysesPort.java`, `inbound/ChangeAnalysisVisibilityPort.java`, `inbound/EventSubscription.java`, `inbound/GetAnalysisPort.java`, `inbound/GetAnalysisRightsPort.java`, `inbound/GetPartialAnalysisPort.java`, `inbound/ListAnalysesPort.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:core:test` and `mise run check` pass.

### WP2: `domain/analysis/core`: inbound, part 2 of 7

- **Status**: open
- **Depends on**: none
- **Files**: `inbound/ListPartialAnalysesPort.java`, `inbound/ReadAnalysisLogsPort.java`, `inbound/RegisterExternalAnalysisPort.java`, `inbound/RerunAnalysisPort.java`, `inbound/RunEventListener.java`, `inbound/StartAnalysisPort.java`, `inbound/StopAnalysisPort.java`, `inbound/SubscribeAnalysisEventsPort.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:core:test` and `mise run check` pass.

### WP3: `domain/analysis/core`: inbound, model, part 3 of 7

- **Status**: open
- **Depends on**: none
- **Files**: `inbound/ValidateExternalAnalysesPort.java`, `model/Analysis.java`, `model/AnalysisFilter.java`, `model/AnalysisRights.java`, `model/AnalysisScope.java`, `model/AnalysisSource.java`, `model/AnalysisSpec.java`, `model/Analyst.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:core:test` and `mise run check` pass.

### WP4: `domain/analysis/core`: model, part 4 of 7

- **Status**: open
- **Depends on**: none
- **Files**: `model/ExternalAnalysis.java`, `model/ExternalRegistration.java`, `model/ExternalRun.java`, `model/OwnerId.java`, `model/Rating.java`, `model/RunEvent.java`, `model/RunEventType.java`, `model/RunHandle.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:core:test` and `mise run check` pass.

### WP5: `domain/analysis/core`: model, outbound, part 5 of 7

- **Status**: open
- **Depends on**: none
- **Files**: `model/RunOutcome.java`, `model/RunStats.java`, `model/Visibility.java`, `outbound/credentials/CredentialsPort.java`, `outbound/eventstore/EventStorePort.java`, `outbound/persistence/AnalysisRepositoryPort.java`, `outbound/runlog/RunLogPort.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:core:test` and `mise run check` pass.

### WP6: `domain/analysis/core`: outbound, service, part 6 of 7

- **Status**: open
- **Depends on**: none
- **Files**: `outbound/runner/RunEventSink.java`, `outbound/runner/RunnerPort.java`, `service/AnalysisAccessService.java`, `service/AnalysisEventService.java`, `service/AnalysisIdService.java`, `service/AnalysisLogService.java`, `service/AnalysisService.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:core:test` and `mise run check` pass.

### WP7: `domain/analysis/core`: service (main, tests), part 7 of 7

- **Status**: open
- **Depends on**: none
- **Files**: `service/AnalysisSpecService.java`, `service/AnalysisTransitionService.java`, `service/AnalysisVisibilityService.java`, `service/ExternalAnalysisService.java`, `service/AnalysisEventServiceTest.java` (test), `service/ExternalAnalysisValidationServiceTest.java` (test), `service/Fakes.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:analysis:core:test` and `mise run check` pass.

### WP8: Remove the temporary filter

- **Status**: open
- **Depends on**: WP1–WP7
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Delete the block `Temporary (#276, plan 3)`, and fix what Checkstyle then reports in the modules
- **Tests**: `mise run build` green; the Checkstyle tasks of the modules report nothing.

## Tests

Each work package's tests; the last one proves that the rules hold in the modules without a filter.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Temporary filters (#276)" | The row of plan 3 goes |

## Out of scope

- The other modules: their own cleanup plans (the table in `.plans/276-comment-rules.md`).
- New comments in these modules after the merge: the agents' comments step (plan 2) and the rules.
