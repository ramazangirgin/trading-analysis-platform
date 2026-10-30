# Plan: Rules out of the identity, report and settings models

- **Issue**: #237 (Move domain logic out of domain objects into domain services)
- **Plan**: 1 of 3 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The identity, report and settings models stop carrying rules: the lock check, the "has a decision"
check and the preset visibility rule move into the services of their domain. Nothing changes for the
user. The analysis domain follows in plans 2 and 3, which also write the rule down and enforce it.
The plan also carries an unrelated fix that the coordinator asked for: `scripts/agent/dashboard.sh
start <issue>` no longer fails with "dashboard: not updated".

## Affected parts and their conventions

- Backend: [package structure](../docs/coding-convention/backend-java-package-structure.md),
  [Checkstyle](../docs/coding-convention/backend-java-checkstyle.md).
- Agent scripts: [agentic development](../docs/agentic-development.md) ("Dashboard").

## Design

What stays in a model, across all three plans (plan 3 writes it into the package structure document
and an ArchUnit rule): record components, null checks, defensive copies, constants, the format
invariant of an identifier or value type (`AnalysisId`, `ReportKey` with `isValidTicker`,
`Username`, `OwnerId`), the stored key of an enum (`Permission.key`/`fromKey`,
`PasswordPolicyViolation.code`), `newId()`, a masking `toString`, and plain `with*` copies that
replace fields without a rule. Everything that decides something moves into the domain's `service`
package. The helpers are package-private final classes with static methods, like
`AnalysisSpecValidator` and `DecisionRatingParser`, so no service constructor changes.

Per domain in this plan:

- **identity**: `User.isLockedAt(Instant)` moves to `LoginLockoutPolicy` as a static
  `isLocked(User, Instant)` (`UserManagementService` does not inject the policy). The `with*`
  methods of `User` and `Role`, `withLoginSucceeded` included, stay.
- **report**: `Report.hasDecision()` is used only by `ImportExistingRunsUseCase` (orchestration),
  which cannot reach a report service, so the check moves there as a private method on
  `report.sections()`. The `EnumMap` copy in `Report`'s constructor stays.
- **settings**: `PresetAccess.canSee(Preset)` becomes a private method of `PresetService`, its only
  user. `PresetAccess` keeps `user` and `allUsers`.
- **catalog**: nothing carries logic.

**Dashboard fix**: `dashboard.sh` removes lib.sh's scratch directory (`rmdir "$AGENT_TMP"`) and never
points `AGENT_TMP` elsewhere, so lib.sh's `retry` (`mktemp "$AGENT_TMP/retry.XXXXXX"`) fails on
every `gh` call. After `work=$(mktemp -d …)` it sets `AGENT_TMP=$work`, as lib.sh does with
`AGENT_TMP=$AGENT_STATE`; `cleanup()` already removes `$work`.

## Work packages

### WP1: Lock check into LoginLockoutPolicy

- **Status**: open
- **Depends on**: none
- **Files**: in `artifact/backend/domain/identity/core/src/`:
  `main/java/…/identity/core/model/User.java`, `main/java/…/identity/core/service/LoginLockoutPolicy.java`,
  `main/java/…/identity/core/service/AuthenticationService.java`,
  `main/java/…/identity/core/service/UserManagementService.java`,
  `test/java/…/identity/core/model/UserTest.java`, `test/java/…/identity/core/service/LoginLockoutPolicyTest.java`
- **Steps**:
  - [ ] Replace each `user.isLockedAt(now)` with `LoginLockoutPolicy.isLocked(user, now)`.
- **Tests**: the `isLockedAt` cases of `UserTest` move to `LoginLockoutPolicyTest` (a lock is in
  force until its instant); `AuthenticationServiceTest` and `UserManagementServiceTest` pass unchanged.

### WP2: Decision check in the import

- **Status**: open
- **Depends on**: none
- **Files**: `artifact/backend/orchestration/src/main/java/…/orchestration/report/inbound/ImportExistingRunsUseCase.java`
- **Steps**:
  - [ ] Check `ReportSection.FINAL_TRADE_DECISION` in `report.sections()` instead of `hasDecision()`.
- **Tests**: `ImportExistingRunsUseCaseTest` passes unchanged (an unsettled report without a decision
  is still skipped).

### WP3: Report without hasDecision

- **Status**: open
- **Depends on**: WP2
- **Files**: `artifact/backend/domain/report/core/src/main/java/…/report/core/model/Report.java`,
  `artifact/backend/domain/report/core/src/test/java/…/report/core/service/ReportServiceTest.java`
- **Steps**:
  - [ ] Remove `hasDecision()`.
- **Tests**: `ReportServiceTest` asserts on the sections instead of `hasDecision()`.

### WP4: Preset visibility rule into PresetService

- **Status**: open
- **Depends on**: none
- **Files**: `artifact/backend/domain/settings/core/src/main/java/…/settings/core/model/PresetAccess.java`,
  `artifact/backend/domain/settings/core/src/main/java/…/settings/core/service/PresetService.java`
- **Steps**:
  - [ ] A preset without an owner stays visible to `allUsers` only.
- **Tests**: `PresetServiceTest` passes unchanged (it covers own, other and unowned presets, with and
  without `allUsers`).

### WP5: Dashboard keeps a scratch directory

- **Status**: open
- **Depends on**: none
- **Files**: `scripts/agent/dashboard.sh`, `scripts/agent/test_dashboard.py`
- **Steps**:
  - [ ] Commit this package on its own, apart from the backend changes.
- **Tests**: a new test in `test_dashboard.py` runs `dashboard.sh start <n>` with `AGENT_DASHBOARD=1`
  in a throwaway git repository, with a fake `gh` on `PATH` (as `test_lib.py` does) that answers the
  calls of `start`; it asserts the run logs neither `mkstemp` nor "not updated". It fails without
  the fix. `mise run agent:test` runs it.

## Tests

Each work package's tests; `mise run build` for the backend.

## Docs to update

None: no behaviour changes. The rule of what a model may hold is documented in plan 3, when every
domain follows it; the dashboard fix restores what "Dashboard" in `docs/agentic-development.md`
already describes.

## Out of scope

- The analysis domain: plan 2 (access rules) and plan 3 (records, the documented rule, ArchUnit).
- Orchestration `model` types: the issue covers the domain cores.
- The ticker pattern duplicated in `AnalysisSpecValidator` and `ReportKey`.

## Open questions

- Plain `with*` copies stay in the records (Java has no `with` expression yet, and removing them
  would turn every call into a full constructor call). Should they move too?
- Format invariants of identifiers stay in the compact constructors, so no invalid `AnalysisId` or
  `ReportKey` (both are directory names) can exist. Agreed?
