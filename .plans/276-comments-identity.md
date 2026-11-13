# Plan: Comments cleanup: identity

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 5 of 9 for this issue; depends on: plans 1 (`276-comment-rules`) and 2 (`276-comment-agent-steps`); implemented after plan 4 (`276-comments-analysis-adapter-orchestration`), as every cleanup plan edits the temporary filters in `config/checkstyle/checkstyle.xml`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The Javadoc and `//` comments of `domain/identity/core` and `domain/identity/adapter` follow
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
  - `domain/identity/core`: `artifact/backend/domain/identity/core/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/`
  - `domain/identity/adapter`: `artifact/backend/domain/identity/adapter/src/<set>/java/tr/girgin/backend/trading/analysis/platform/domain/identity/adapter/`
- The last package deletes the block `Temporary (#276, plan 5)` from `checkstyle.xml`; Checkstyle
  then reports what is left in the modules, also in files added since, and that package fixes it.
- The optimistic-locking promise on a repository port's write method is deleted, not moved:
  `backend-java-persistence.md` "The port promises" states it (plan 1).
- A `@throws` that names an `IdentityError` code (`ManageUsersPort`, `ManageRolesPort`,
  `UnlockUserPort`, `PasswordPolicyService`) goes with its Javadoc (decided in plan 1).

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: `domain/identity/core`: inbound, model, part 1 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `inbound/AuthenticatePort.java`, `inbound/ChangePasswordPort.java`, `inbound/CreateFirstAdminPort.java`, `inbound/ManageRolesPort.java`, `inbound/ManageUsersPort.java`, `inbound/UnlockUserPort.java`, `inbound/UserDirectoryPort.java`, `model/AuthenticatedUser.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:identity:core:test` and `mise run check` pass.

### WP2: `domain/identity/core`: model, part 2 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `model/CreatedUserAccount.java`, `model/PasswordHash.java`, `model/PasswordPolicyViolation.java`, `model/Permission.java`, `model/Role.java`, `model/RoleId.java`, `model/RoleSummary.java`, `model/User.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:identity:core:test` and `mise run check` pass.

### WP3: `domain/identity/core`: model, outbound, service, part 3 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `model/UserAccount.java`, `outbound/password/PasswordHasherPort.java`, `outbound/persistence/RoleRepositoryPort.java`, `outbound/persistence/UserRepositoryPort.java`, `service/AuthenticatedUserService.java`, `service/AuthenticationService.java`, `service/FirstAdminService.java`, `service/LoginLockoutService.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:identity:core:test` and `mise run check` pass.

### WP4: `domain/identity/core`: service (main, tests), part 4 of 4

- **Status**: open
- **Depends on**: none
- **Files**: `service/OneTimePasswordService.java`, `service/PasswordPolicyService.java`, `service/RoleManagementService.java`, `service/UserManagementService.java`, `service/UsernameService.java`, `service/AuthenticationServiceTest.java` (test), `service/InMemoryRoleRepository.java` (test), `service/InMemoryUserRepository.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:identity:core:test` and `mise run check` pass.

### WP5: `domain/identity/adapter`: password, persistence, part 1 of 3

- **Status**: open
- **Depends on**: none
- **Files**: `password/DelegatingPasswordHasherAdapter.java`, `persistence/IdentityPersistenceConfiguration.java`, `persistence/JpaRoleRepositoryAdapter.java`, `persistence/JpaUserRepositoryAdapter.java`, `persistence/RoleJpaRepository.java`, `persistence/UserJpaRepository.java`, `persistence/entity/PasswordHashAttributeConverter.java`, `persistence/entity/RoleEntity.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:identity:adapter:test` and `mise run check` pass.

### WP6: `domain/identity/adapter`: persistence, part 2 of 3

- **Status**: open
- **Depends on**: none
- **Files**: `persistence/entity/RoleIdAttributeConverter.java`, `persistence/entity/RoleIdEmbeddable.java`, `persistence/entity/UserEntity.java`, `persistence/entity/UserIdEmbeddable.java`, `persistence/entity/UsernameAttributeConverter.java`, `persistence/mapper/InstantToOptionalInstantMapper.java`, `persistence/mapper/RoleIdEmbeddableToRoleIdMapper.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:identity:adapter:test` and `mise run check` pass.

### WP7: `domain/identity/adapter`: persistence (main, tests), part 3 of 3

- **Status**: open
- **Depends on**: none
- **Files**: `persistence/mapper/RoleIdToRoleIdEmbeddableMapper.java`, `persistence/mapper/UserIdEmbeddableToUserIdMapper.java`, `persistence/mapper/UserIdToUserIdEmbeddableMapper.java`, `persistence/mapper/UserToUserEntityMapper.java`, `persistence/IdentityPersistenceConventionsTest.java` (test), `persistence/JpaIdentityRepositoryTest.java` (test), `persistence/V2IdentityVersionMigrationTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:domain:identity:adapter:test` and `mise run check` pass.

### WP8: Remove the temporary filter

- **Status**: open
- **Depends on**: WP1–WP7
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Delete the block `Temporary (#276, plan 5)`, and fix what Checkstyle then reports in the modules
- **Tests**: `mise run build` green; the Checkstyle tasks of the modules report nothing.

## Tests

Each work package's tests; the last one proves that the rules hold in the modules without a filter.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Temporary filters (#276)" | The row of plan 5 goes |

## Out of scope

- The other modules: their own cleanup plans (the table in `.plans/276-comment-rules.md`).
- New comments in these modules after the merge: the agents' comments step (plan 2) and the rules.
