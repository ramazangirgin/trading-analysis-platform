# Plan: Domain port packages hold only interfaces, their types move to model

- **Issue**: #273 (Domain inbound packages hold only interfaces: move their types to model)
- **Plan**: 1 of 1 for this issue; depends on: none (#237 and #274 are merged: the inbound ports are `*Port` now)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A domain core's `inbound` and `outbound` packages hold only interfaces, the ports (and the
listener/sink interfaces they exchange). Every record a port takes or returns is a model type in the
domain's `model` package. ArchUnit fails the build when a non-interface lands in a port package or
an interface lands in a model package, and `models_are_data` no longer needs to cover the port
packages.

## Affected parts and their conventions

- Backend: [backend-java-package-structure.md](../docs/coding-convention/backend-java-package-structure.md),
  [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md),
  [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md).

## Design

- **The records that move** (all `src/main`, nothing else on `main` has this shape):
  - `domain.analysis.core.inbound.AnalysisRights` → `domain.analysis.core.model`
  - `domain.identity.core.inbound.{CreatedUserAccount, RoleSummary, UserAccount}` → `domain.identity.core.model`
  - `domain.analysis.core.outbound.runner.RunHandle` → `domain.analysis.core.model`

  None clashes with a name already in its `model` package. Each file moves with `git mv`, only its
  package line and imports change; Javadoc stays as it is (model records carry Javadoc today too).
- **`outbound` gets the same rule.** The issue leaves this to the plan. `RunHandle` is the only
  non-interface in any `outbound` package, so the move costs one record and four users, and in
  return `MODEL_PACKAGES` lists only `model` packages. `RunEventSink` (outbound) and
  `EventSubscription`, `RunEventListener` (inbound) are interfaces and stay.
- **Rules in `ArchitectureTest`**, next to `ports_live_in_port_packages` and in its naming style:
  - `port_packages_hold_only_interfaces`: every top-level class in `domain.*.core.inbound..` or
    `domain.*.core.outbound..` is an interface; the `because` says that the types a port takes or
    returns are models.
  - `model_packages_hold_no_interfaces`: no top-level class in `domain.*.core.model..`,
    `orchestration.*.model..` or `bff.controller.api.model..` is an interface (a sealed interface is
    one too, so it needs no separate check).
  - `MODEL_PACKAGES` drops `domain.*.core.inbound..` and `domain.*.core.outbound..`, and
    `models_are_data` drops `.areNotInterfaces()`: with the second rule there is no interface left
    in a model package to exclude.
- Checkstyle's `MissingJavadocType` scope (`core/inbound`, `core/outbound`) stays: it now covers only
  the ports, which is what its comment already says.

## Work packages

The core packages can run in parallel. The BFF, orchestration and adapter packages compile only
after the core package they depend on; the build is green once all are done.

### WP1: Identity core, roles

- **Status**: done
- **Depends on**: none
- **Files**: `artifact/backend/domain/identity/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/inbound/RoleSummary.java` (moved to `core/model/RoleSummary.java`), `.../identity/core/inbound/ManageRolesPort.java`, `.../identity/core/service/RoleManagementService.java`, `artifact/backend/domain/identity/core/src/test/java/.../identity/core/service/RoleManagementServiceTest.java`
- **Steps**:
  - [x] `ManageRolesPort` imports `RoleSummary` from `model` (it used it from its own package without an import).
- **Tests**: `RoleManagementServiceTest` unchanged apart from the import; `mise run gradle :backend:domain:identity:core:test`.

### WP2: Identity core, users

- **Status**: open
- **Depends on**: none
- **Files**: `.../identity/core/inbound/UserAccount.java` and `.../identity/core/inbound/CreatedUserAccount.java` (moved to `core/model/`), `.../identity/core/inbound/ManageUsersPort.java`, `.../identity/core/service/UserManagementService.java`, `artifact/backend/domain/identity/core/src/test/java/.../identity/core/service/UserManagementServiceTest.java`
- **Steps**:
  - [ ] In `model`, `CreatedUserAccount` refers to `UserAccount` in its own package; `UserAccount` drops its imports of `Role`, `UserId`, `Username`.
- **Tests**: `UserManagementServiceTest` unchanged apart from the imports; `mise run gradle :backend:domain:identity:core:test`.

### WP3: Analysis core, rights

- **Status**: open
- **Depends on**: none
- **Files**: `artifact/backend/domain/analysis/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/analysis/core/inbound/AnalysisRights.java` (moved to `core/model/AnalysisRights.java`), `.../analysis/core/inbound/GetAnalysisRightsPort.java`, `.../analysis/core/service/AnalysisAccessService.java`, `artifact/backend/domain/analysis/core/src/test/java/.../analysis/core/service/AnalysisAccessServiceTest.java`
- **Tests**: `AnalysisAccessServiceTest` unchanged apart from the import.

### WP4: Analysis core, run handle

- **Status**: open
- **Depends on**: none
- **Files**: `.../analysis/core/outbound/runner/RunHandle.java` (moved to `core/model/RunHandle.java`), `.../analysis/core/outbound/runner/RunnerPort.java`, `.../analysis/core/service/AnalysisService.java`, `artifact/backend/domain/analysis/core/src/test/java/.../analysis/core/service/AnalysisServiceTest.java`, `.../analysis/core/service/Fakes.java` (test)
- **Tests**: `AnalysisServiceTest` unchanged apart from the import; `mise run gradle :backend:domain:analysis:core:test`.

### WP5: Analysis runner adapters

- **Status**: open
- **Depends on**: WP4
- **Files**: `artifact/backend/domain/analysis/adapter/src/main/java/.../analysis/adapter/runner/ProcessRunnerAdapter.java`, `.../adapter/runner/DockerRunnerAdapter.java`, `artifact/backend/domain/analysis/adapter/src/test/java/.../adapter/runner/ProcessRunnerAdapterTest.java`, `.../adapter/runner/DockerRunnerAdapterTest.java`
- **Tests**: the two adapter tests, imports only; `mise run gradle :backend:domain:analysis:adapter:test`.

### WP6: Orchestration

- **Status**: open
- **Depends on**: WP3
- **Files**: `artifact/backend/orchestration/src/main/java/.../orchestration/analysisview/model/AnalysisView.java`, `artifact/backend/orchestration/src/test/java/.../orchestration/analysisview/inbound/ViewAnalysesUseCaseTest.java`
- **Tests**: `ViewAnalysesUseCaseTest`, import only.

### WP7: BFF impl

- **Status**: open
- **Depends on**: WP1, WP2, WP3
- **Files**: `artifact/backend/bff/impl/src/main/java/.../bff/delegate/impl/UsersApiDelegateImpl.java`, `.../bff/delegate/impl/mapper/RoleSummaryToRoleDtoMapper.java`, `.../bff/delegate/impl/mapper/UserAccountToUserDtoMapper.java`, `artifact/backend/bff/impl/src/test/java/.../bff/delegate/impl/mapper/RoleSummaryToRoleDtoMapperTest.java`, `.../mapper/UserAccountToUserDtoMapperTest.java`, `.../mapper/AnalysisViewToAnalysisDtoMapperTest.java`
- **Tests**: the three mapper tests, imports only; `mise run gradle :backend:bff:impl:test`.

### WP8: ArchUnit rules

- **Status**: open
- **Depends on**: WP1-WP7
- **Files**: `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/ArchitectureTest.java`
- **Steps**:
  - [ ] Add `port_packages_hold_only_interfaces` and `model_packages_hold_no_interfaces`; trim `MODEL_PACKAGES` and `models_are_data` as in "Design".
- **Tests**: `ArchitectureTest` passes. Before the moves (or with one record moved back temporarily) `port_packages_hold_only_interfaces` fails naming it, which proves the rule bites; not committed.

## Tests

Each work package's tests, then `mise run build` (every module and `ArchitectureTest`) and
`mise run check`. A `grep -rn 'core\.inbound\.\(AnalysisRights\|UserAccount\|CreatedUserAccount\|RoleSummary\)\|outbound\.runner\.RunHandle' artifact`
finds nothing.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Package layout" | `inbound/` line: "`*Port` interfaces only; the types they take and return are in `model/`"; `outbound/<port>/` line likewise ("`*Port` interfaces and the interfaces they exchange; their data types are in `model/`") |
| Text | same file, "Where each class kind goes" | new rows for `port_packages_hold_only_interfaces` and `model_packages_hold_no_interfaces`; the `models_are_data` row names `domain.<d>.core.model`, `orchestration.<f>.model` and `bff.controller.api.model` only, without "(not interfaces)" |
| Screenshot | none | no page changes |

`backend-java-checkstyle.md` and `repository-code-comments.md` say "public types in `core/inbound`
and `core/outbound` (inbound and outbound ports)", which stays true.

## Out of scope

- Orchestration `inbound` packages: they hold concrete use case beans and helpers
  (`preview.inbound.PreviewCutter`, the `@ConfigurationProperties` record `PreviewSettings`), not
  ports, and the new rule does not cover them. Whether `PreviewSettings` belongs in `model` is a
  separate question for its own issue.
- Mapper rules (#271, #272) and the port renaming (#274): already merged.
