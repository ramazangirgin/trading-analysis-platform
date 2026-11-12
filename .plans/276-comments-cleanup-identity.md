# Plan: Clean up the comments of the identity domain

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 5 of 9 for this issue; depends on: `.plans/276-redundant-comment-checks.md`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The comments and Javadoc of the identity domain pass the test of [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md), and the rules `TAP-J1`, `TAP-C1` and `TAP-C2` apply to it: the pending block for `domain/identity` that plan 1 put into `config/checkstyle/checkstyle.xml` is gone, so the build fails on any redundant Javadoc or comment the rules can see there.

## Affected parts and their conventions

- Backend: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) ("Existing comments" is the triage), [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md) (`TAP-J1`, `TAP-C*`)

## Design

- The packages name every file of these modules that held a Javadoc or a comment on `main` at `d0e86794` (not `package-info.java`, which keeps its Javadoc). A path is relative to `artifact/backend/<module>/src/<source set>/java/tr/girgin/backend/trading/analysis/platform/<module package>/` and starts with the source set (`main/`, `testFixtures/`, `test/`); the module package of `bff/api` is `bff/controller/api`, of `bff/impl` `bff/delegate/impl`, otherwise the module path. A file renamed since by another pull request is triaged under its new name.
- Every Javadoc goes, on ports too (`package-info.java` aside). A why it held that passes the test of "Existing comments" becomes a `//` comment above the declaration; an error case a `@throws` named is dropped, since the exception type the code throws shows it. Every other comment is kept when it says why and deleted otherwise. Only comments change: no code, no test, no migration.
- The last package removes the block and fixes what `mise run check` then still reports in these modules, also in a file no package names.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: domain/identity/core (1/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/identity/core`
- **Files**: `main/inbound/AuthenticatePort.java`, `main/inbound/ChangePasswordPort.java`, `main/inbound/CreateFirstAdminPort.java`, `main/inbound/CreatedUserAccount.java`, `main/inbound/ManageRolesPort.java`, `main/inbound/ManageUsersPort.java`, `main/inbound/RoleSummary.java`, `main/inbound/UnlockUserPort.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:identity:core:test` passes unchanged

### WP2: domain/identity/core (2/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/identity/core`
- **Files**: `main/inbound/UserAccount.java`, `main/inbound/UserDirectoryPort.java`, `main/model/AuthenticatedUser.java`, `main/model/PasswordHash.java`, `main/model/PasswordPolicyViolation.java`, `main/model/Permission.java`, `main/model/Role.java`, `main/model/RoleId.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:identity:core:test` passes unchanged

### WP3: domain/identity/core (3/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/identity/core`
- **Files**: `main/model/User.java`, `main/outbound/password/PasswordHasherPort.java`, `main/outbound/persistence/RoleRepositoryPort.java`, `main/outbound/persistence/UserRepositoryPort.java`, `main/service/AuthenticatedUserService.java`, `main/service/AuthenticationService.java`, `main/service/FirstAdminService.java`, `main/service/LoginLockoutService.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:identity:core:test` passes unchanged

### WP4: domain/identity/core (4/4)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/identity/core`
- **Files**: `main/service/OneTimePasswordService.java`, `main/service/PasswordPolicyService.java`, `main/service/RoleManagementService.java`, `main/service/UserManagementService.java`, `main/service/UsernameService.java`, `test/service/AuthenticationServiceTest.java`, `test/service/InMemoryRoleRepository.java`, `test/service/InMemoryUserRepository.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:identity:core:test` passes unchanged

### WP5: domain/identity/adapter (1/3)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/identity/adapter`
- **Files**: `main/password/DelegatingPasswordHasherAdapter.java`, `main/persistence/IdentityPersistenceConfiguration.java`, `main/persistence/JpaRoleRepositoryAdapter.java`, `main/persistence/JpaUserRepositoryAdapter.java`, `main/persistence/RoleJpaRepository.java`, `main/persistence/UserJpaRepository.java`, `main/persistence/entity/PasswordHashAttributeConverter.java`, `main/persistence/entity/RoleEntity.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:identity:adapter:test` passes unchanged

### WP6: domain/identity/adapter (2/3)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/identity/adapter`
- **Files**: `main/persistence/entity/RoleIdAttributeConverter.java`, `main/persistence/entity/RoleIdEmbeddable.java`, `main/persistence/entity/UserEntity.java`, `main/persistence/entity/UserIdEmbeddable.java`, `main/persistence/entity/UsernameAttributeConverter.java`, `main/persistence/mapper/InstantToOptionalInstantMapper.java`, `main/persistence/mapper/RoleIdEmbeddableToRoleIdMapper.java`, `main/persistence/mapper/RoleIdToRoleIdEmbeddableMapper.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:identity:adapter:test` passes unchanged

### WP7: domain/identity/adapter (3/3)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/domain/identity/adapter`
- **Files**: `main/persistence/mapper/UserIdEmbeddableToUserIdMapper.java`, `main/persistence/mapper/UserIdToUserIdEmbeddableMapper.java`, `main/persistence/mapper/UserToUserEntityMapper.java`, `test/persistence/IdentityPersistenceConventionsTest.java`, `test/persistence/JpaIdentityRepositoryTest.java`, `test/persistence/V2IdentityVersionMigrationTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:domain:identity:adapter:test` passes unchanged

### WP8: Rules on for these modules

- **Status**: open
- **Depends on**: WP1-WP7
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Remove the pending block for `domain/identity`
  - [ ] Fix every `TAP-J1` / `TAP-C*` finding left
- **Tests**: `mise run check`; `mise run gradle :backend:domain:identity:core:test`, `mise run gradle :backend:domain:identity:adapter:test`

## Tests

Each work package's tests. The proof that the rules now apply: a deliberate Javadoc on a private method in one of the modules fails `mise run check` (not committed).

## Docs to update

None: no document names the modules of the pending blocks (plan 1 describes them in general); plan 9, the last, removes that paragraph.

## Out of scope

- The other modules: the other `.plans/276-comments-cleanup-*.md` plans.
- SQL migrations (a merged one is never edited), Python, shell, TypeScript and Vue comments.
