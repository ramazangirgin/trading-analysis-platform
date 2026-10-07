# Plan: identity domain with users, roles and permissions tables

- **Issue**: #79 (Auth: identity domain with users, roles and permissions tables)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The backend gets a fifth domain, `identity`, that stores users, roles and the permissions of each
role, in the file database and in PostgreSQL. It is the storage the rest of #78 builds on (login in #80, the
first admin in #82, built-in roles in #87, user management in #88). Nothing uses it yet: no
endpoint, UI or behaviour changes, and existing data is untouched. The repository is tested against
both databases.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| backend | `docs/coding-convention/backend-java-package-structure.md`: two Gradle projects `:backend:domain:identity:core` and `:backend:domain:identity:adapter`; core sub-packages only `model`, `outbound`, `exception` (no `inbound`/`service` yet, nothing uses the domain); adapter packages one per port (`persistence`, `password`), the adapter class at the package root, rows in `row`, MapStruct mappers `*To*Mapper` with one `map` method in `mapper`; classes package-private unless another package needs them; `domains_are_independent` (identity depends on no other domain). `backend-java-checkstyle.md` (Javadoc, naming, suppressions in place with a reason), `backend-java-formatting.md` (`mise run format`). |
| backend, database | Flyway versions are global across domains, in order of creation: the next free one is `V5`. Times are stored as UTC fixed-width ISO-8601 `TEXT`, as in `analyses` and `presets`. `db/migration-postgresql` only where PostgreSQL needs different types (see Design). |
| docs | `backend-java-package-structure.md` lists the domains and adapter packages; `README.md` lists what the database holds. |

## Design

### Gradle

- `settings.gradle.kts`: include `:backend:domain:identity:core` and `:backend:domain:identity:adapter`
  (they land under `artifact/backend/domain/identity/…` through `placeUnderArtifact`).
- `artifact/backend/domain/identity/core/build.gradle.kts`: like settings core
  (`tradinganalysisplatform.java-library`, `spring-context`, `slf4j-api`).
- `artifact/backend/domain/identity/adapter/build.gradle.kts`: like settings adapter
  (`tradinganalysisplatform.mapstruct`, core, `spring-context`, `spring-jdbc`, `slf4j-api`) plus
  `spring-security-crypto` (new catalog entry `spring-security-crypto`, version from the Spring Boot
  BOM). Only the crypto module, **not** `spring-boot-starter-security`: the starter would switch on
  Spring Security's auto-configuration and lock every endpoint, which is #80's job. Test
  dependencies: `flyway-core`, the file database's JDBC driver, and for PostgreSQL `postgresql`,
  `flyway-database-postgresql` and Testcontainers' PostgreSQL module plus its JUnit Jupiter
  integration (new catalog entries, versions from the Spring Boot BOM; Testcontainers 2.x
  coordinates, check them against the BOM).
- `artifact/backend/build.gradle.kts`: `runtimeOnly` both new projects, like the other domains.

### Migration

One migration for both databases,
`domain/identity/adapter/src/main/resources/db/migration/V5__identity_create_users_and_roles.sql`:

```
users            id TEXT PK, username TEXT NOT NULL, password_hash TEXT NOT NULL,
                 enabled BOOLEAN NOT NULL, must_change_password BOOLEAN NOT NULL,
                 failed_login_count INTEGER NOT NULL DEFAULT 0, locked_until TEXT,
                 created_at TEXT NOT NULL, updated_at TEXT NOT NULL
                 UNIQUE INDEX users_username_lower ON users (lower(username))
roles            id TEXT PK, name TEXT NOT NULL UNIQUE, built_in BOOLEAN NOT NULL,
                 description TEXT NOT NULL DEFAULT ''
role_permissions role_id TEXT NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
                 permission TEXT NOT NULL, PRIMARY KEY (role_id, permission)
user_roles       user_id TEXT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                 role_id TEXT NOT NULL REFERENCES roles (id), PRIMARY KEY (user_id, role_id)
                 INDEX user_roles_role_id ON user_roles (role_id)
```

- **Case-insensitive username**: a unique expression index on `lower(username)`, which both
  databases support, and lookups by `lower(username) = lower(:username)`. The username
  keeps the case it was entered in. The file database's `lower()` folds ASCII only, so the core restricts
  usernames to ASCII (see `Username`), and both databases then agree.
- **No PostgreSQL-specific migration**: every column is `TEXT`, `BOOLEAN` or a small `INTEGER`
  (`failed_login_count` fits 4 bytes), which mean the same in both databases. The V4_1 reason (4-byte
  `REAL`/`INTEGER` for large values) does not apply. The migration's header comment says so.
- `user_roles.role_id` has no cascade: deleting a role still assigned to users fails, which is the
  rule #90 needs. The file database enforces foreign keys because the datasource URL sets `foreign_keys=true`.
- No rows are seeded: the built-in roles come with #87, the first admin with #82.

### Core (`….domain.identity.core`)

- `model/`
  - `UserId`, `RoleId`: records around a `String`, `newId()` from a random UUID (like `PresetId`).
  - `Username`: record; non-blank, 3 to 64 characters of `[A-Za-z0-9._@-]`, else
    `IdentityException` with `IdentityError.INVALID_USERNAME`.
  - `PasswordHash`: record around the encoded hash (`{bcrypt}…`); `toString()` returns
    `PasswordHash[***]`, so a hash never ends up in a log line.
  - `Permission`: enum with a `key()` (`analysis:run`, …) and `fromKey(String)`; the initial
    catalogue from #87: `analysis:run`, `analysis:read`, `analysis:read:all`, `analysis:delete`,
    `preset:read`, `preset:manage`, `preset:read:all`, `settings:read`, `settings:keys:write`,
    `user:read`, `user:manage`, `role:manage`, `audit:read`. An unknown key read from the database
    throws `IdentityException` (`UNKNOWN_PERMISSION`).
  - `User`: record `id, username, passwordHash, enabled, mustChangePassword, failedLoginCount,
    lockedUntil (Optional<Instant>), createdAt, updatedAt, roleIds (Set<RoleId>)`; null checks and
    an immutable copy of the set in the compact constructor.
  - `Role`: record `id, name, builtIn, description, permissions (Set<Permission>)`; same checks.
- `exception/`: `IdentityException` and `IdentityError` (`INVALID_USERNAME`, `UNKNOWN_PERMISSION`),
  shaped like `SettingsException` / `SettingsError`.
- `outbound/persistence/`
  - `UserRepositoryPort`: `Optional<User> findById(UserId)`, `Optional<User> findByUsername(Username)`
    (case-insensitive), `List<User> findAll()`, `long count()`, `void save(User)` (insert or update
    the user and replace its role assignments).
  - `RoleRepositoryPort`: `Optional<Role> findById(RoleId)`, `Optional<Role> findByName(String)`,
    `List<Role> findAll()`, `void save(Role)` (insert or update the role and replace its
    permissions).
  - Saving a user whose username is taken by another user (in any case) fails; the adapter throws
    Spring's `DuplicateKeyException` as it comes on PostgreSQL. On the file database Spring has no
    error codes, so it is an `UncategorizedSQLException`, the same type as a foreign-key failure;
    the contract tests pin both. Turning it into a domain error is left to #88, the first caller,
    which cannot rely on the exception type on the file database (e.g. check `findByUsername`
    before saving, or register a `SQLExceptionTranslator` for its unique-constraint error).
- `outbound/password/PasswordHasherPort`: `PasswordHash hash(CharSequence rawPassword)`,
  `boolean matches(CharSequence rawPassword, PasswordHash hash)`,
  `boolean needsRehash(PasswordHash hash)` (true when the stored algorithm is not the current
  default, so a later login can upgrade it).

### Adapter (`….domain.identity.adapter`)

- `persistence/`
  - `JdbcUserRepositoryAdapter`, `JdbcRoleRepositoryAdapter`: `JdbcClient`, SQL in text blocks,
    upserts with `INSERT … ON CONFLICT (id) DO UPDATE` (as `JdbcPresetRepositoryAdapter`), then
    `DELETE` and re-`INSERT` of the user's roles or the role's permissions. `save` is
    `@Transactional`, so a user or role is never half-written (the first `@Transactional` in the
    code base; Spring Boot's JDBC auto-configuration provides the transaction manager). Loading
    reads the user or role rows, then their assignments in one query per list (no N+1:
    `WHERE user_id IN (…)` or one join, grouped in Java).
  - `row/`: `UserRow`, `RoleRow`, `UserRoleRow`, `RolePermissionRow` (records, table shapes).
  - `mapper/`: MapStruct mappers between rows and the domain records, plus scalar converters
    (`StringToUserIdMapper`, `StringToRoleIdMapper`, `StringToUsernameMapper`,
    `StringToPasswordHashMapper`, `StringToPermissionMapper` and the reverse directions, and
    `Instant` ↔ fixed-width ISO-8601 `String`, the format of `analyses`). Where MapStruct cannot
    map the assignments, the adapter assembles them and the mapper takes the row only (`roleIds`
    / `permissions` ignored and set by the adapter, or a small factory method); keep the
    one-`map`-method rule.
- `password/DelegatingPasswordHasherAdapter`: wraps
  `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (default `{bcrypt}`, BCrypt strength
  10; Argon2 would need Bouncy Castle on the classpath, so it is left for later; the `{id}` prefix
  lets the default change without touching stored hashes). `needsRehash` uses
  `upgradeEncoding`.

## Work packages

### WP1: Gradle modules and core model

- **Depends on**: none
- **Files**: `settings.gradle.kts`, `gradle/libs.versions.toml`, `artifact/backend/build.gradle.kts`,
  `artifact/backend/domain/identity/core/build.gradle.kts`,
  `artifact/backend/domain/identity/core/src/main/java/…/domain/identity/core/package-info.java`,
  `…/core/model/{UserId,RoleId,Username,PasswordHash,Permission,User,Role}.java`,
  `…/core/exception/{IdentityException,IdentityError}.java`,
  `…/core/outbound/persistence/{UserRepositoryPort,RoleRepositoryPort}.java`,
  `…/core/outbound/password/PasswordHasherPort.java`,
  `artifact/backend/domain/identity/core/src/test/java/…/core/model/*Test.java`
- **Steps**:
  - [ ] Include the two projects and add the core module with its build file and `package-info`
  - [ ] Model records, `Permission` enum, exception and error code
  - [ ] The three ports, with Javadoc on the contract (case-insensitive lookup, replace semantics of
        `save`, duplicate username)
- **Tests**: `UsernameTest` (valid and invalid names, ASCII only), `PermissionTest` (`fromKey`
  round-trips every constant, unknown key throws), `PasswordHashTest` (`toString` hides the value),
  `UserTest`/`RoleTest` (null checks, sets are copied and immutable).

### WP2: Migration and persistence adapters

- **Depends on**: WP1
- **Files**: `artifact/backend/domain/identity/adapter/build.gradle.kts`,
  `…/domain/identity/adapter/package-info.java`,
  `…/adapter/src/main/resources/db/migration/V5__identity_create_users_and_roles.sql`,
  `…/adapter/persistence/{JdbcUserRepositoryAdapter,JdbcRoleRepositoryAdapter}.java`,
  `…/adapter/persistence/row/*.java`, `…/adapter/persistence/mapper/*.java`,
  `…/adapter/src/test/java/…/adapter/persistence/{IdentityRepositoryContractTest, one test class per database}.java`
- **Steps**:
  - [ ] Adapter module build file (with test dependencies) and `package-info`
  - [ ] Migration
  - [ ] Rows, mappers, both JDBC adapters
  - [ ] Tests against both databases
- **Tests**: one abstract `IdentityRepositoryContractTest` with the cases, run by two subclasses
  that each supply a `DataSource`, a `JdbcClient` and a `DataSourceTransactionManager` (with
  `@EnableTransactionManagement`) and run Flyway with `baselineVersion("4")` (only this domain's
  migration is on the test classpath, as in `JdbcPresetRepositoryAdapterTest`):
  - the file database: a temp file with `foreign_keys=true`, like the application's URL;
  - PostgreSQL: a Testcontainers `postgres:18.6` container (the Compose version, kept in step by Renovate),
    `@Testcontainers(disabledWithoutDocker = true)` so a machine without Docker skips it, as the
    Docker runner tests do; CI's build job has Docker, so it runs there.

  Cases: a user with all fields (including `lockedUntil`) and two roles round-trips; updating a
  user replaces its fields and role assignments; `findByUsername` matches in any case; a second
  user with the same username in another case is rejected; `count` and `findAll`; a role with
  permissions round-trips and a save replaces its permissions; `findByName`; a role still assigned
  to a user cannot be deleted by SQL (foreign key, shows the constraint is enforced on both
  databases); a failing save leaves no partial assignment (transaction).

### WP3: Password hashing adapter

- **Depends on**: WP1
- **Files**: `…/adapter/password/DelegatingPasswordHasherAdapter.java`,
  `…/adapter/src/test/java/…/adapter/password/DelegatingPasswordHasherAdapterTest.java`
- **Steps**:
  - [ ] The adapter over `DelegatingPasswordEncoder`
- **Tests**: a hash starts with `{bcrypt}` and differs from the raw password; `matches` accepts the
  right and rejects a wrong password; two hashes of the same password differ (salt);
  `needsRehash` is false for a fresh hash and true for a hash with another `{id}` (e.g. `{noop}`
  or `{pbkdf2}`), and a hash of another algorithm still `matches`.

### WP4: Docs and version

- **Depends on**: WP2, WP3
- **Files**: `docs/coding-convention/backend-java-package-structure.md`, `README.md`,
  `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [ ] Docs below
  - [ ] `mise run version:bump minor` (0.17.0 → 0.18.0), `mise run check`
- **Tests**: none of its own; `mise run build` runs the ArchUnit rules over the new modules.

## Tests

- Unit tests of the core model (WP1) and the hashing adapter (WP3).
- Repository tests against both databases (WP2) prove "the repository round-trips users,
  roles and assignments".
- "The app starts with the new tables": on the file database, `TradingPlatformApplicationTests` and
  `AnalysesApiIntegrationTest` start the application with every migration including V5; on
  PostgreSQL, the PostgreSQL test runs the same migration, and CI's Compose smoke test
  (`deploy/smoke-test.sh`) starts the real image on PostgreSQL 18. "Existing data is untouched":
  V5 only creates new tables.
- `ArchitectureTest` covers the new packages (its rules are written for `domain.*`).
- No frontend or e2e change.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-package-structure.md`, "Modules" | Add `identity` to the list of domains |
| Text | same, "Today's adapter packages" | Add `domain.identity.adapter.password` (`DelegatingPasswordHasherAdapter`) and `domain.identity.adapter.persistence` (`JdbcRoleRepositoryAdapter`, `JdbcUserRepositoryAdapter`; `row`, `mapper`) |
| Text | `README.md`, the Compose data table (`postgres/`) and the local files table (`platform.db`) | "analyses and presets" → "analyses, presets and users" |
| Text | `README.md`, CI table, "Backend and frontend" row | Add the repository tests against PostgreSQL in a container (Testcontainers) to the list of Gradle tests |
| Screenshot | none | No page changes |

## Out of scope

- Login, sessions, CSRF, `UserDetailsService` (#80); the first admin (#82); password change (#83);
  password policy (#84); lockout logic (#85, the columns are added here).
- Seeding the built-in roles ADMIN, ANALYST, VIEWER and endpoint checks (#87).
- Use cases and services of the identity domain, a domain error for a duplicate username (#88).
- Deleting roles (#90), `owner_id` columns (#91, #92), the audit table (#93).
- Argon2 (needs Bouncy Castle); the `{id}` prefix keeps the switch open.

## Decisions

- **Testcontainers** is added as a new test tool, for the identity repository's PostgreSQL tests
  only (skipped without Docker, run in CI's build job). No test used it before; PostgreSQL was only
  exercised by the Compose smoke test.
- **The permission catalogue** goes into the `Permission` enum here, with #87's initial list; #87
  adds the built-in roles and the endpoint checks and may adjust the list.
