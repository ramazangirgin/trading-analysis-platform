# Plan: Clean up the comments of the BFF

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 8 of 9 for this issue; depends on: `.plans/276-redundant-comment-checks.md`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The comments and Javadoc of the BFF pass the test of [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md), and the rules `TAP-J1` to `TAP-J3`, `TAP-C1` and `TAP-C2` apply to it: the pending block for `bff` that plan 1 put into `config/checkstyle/checkstyle.xml` is gone, so the build fails on any redundant Javadoc or comment the rules can see there.

## Affected parts and their conventions

- Backend: [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) ("Existing comments" is the triage), [backend-java-checkstyle.md](../docs/coding-convention/backend-java-checkstyle.md) (`TAP-J*`, `TAP-C*`)

## Design

- The packages name every file of these modules that held a Javadoc or a comment on `main` at `d0e86794` (not `package-info.java`, which keeps its Javadoc). A path is relative to `artifact/backend/<module>/src/<source set>/java/tr/girgin/backend/trading/analysis/platform/<module package>/` and starts with the source set (`main/`, `testFixtures/`, `test/`); the module package of `bff/api` is `bff/controller/api`, of `bff/impl` `bff/delegate/impl`, otherwise the module path. A file renamed since by another pull request is triaged under its new name.
- Each comment and Javadoc is triaged as "Existing comments" says: kept, shortened to the one sentence `TAP-J3` allows (an error case into `@throws`), turned into a `//` comment (a why in a Javadoc outside a port), or deleted. Only comments change: no code, no test, no migration.
- The last package removes the block and fixes what `mise run check` then still reports in these modules, also in a file no package names.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: bff/api (1/3)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/bff/api`
- **Files**: `main/AnalysesApiController.java`, `main/CatalogApiController.java`, `main/HealthApiController.java`, `main/PublicAnalysesApiController.java`, `main/ReportsApiController.java`, `main/config/SpaWebConfig.java`, `main/error/ApiException.java`, `main/model/AnalysisPreviewDto.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:bff:api:test` passes unchanged

### WP2: bff/api (2/3)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/bff/api`
- **Files**: `main/model/AnalysisPreviewSummaryDto.java`, `main/model/AnalysisReportDto.java`, `main/model/ApiErrorDto.java`, `main/model/CurrentUserDto.java`, `main/model/PresetDto.java`, `main/model/PresetValuesDto.java`, `main/model/PriceHistoryDto.java`, `main/model/PricePointDto.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:bff:api:test` passes unchanged

### WP3: bff/api (3/3)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/bff/api`
- **Files**: `main/model/RunEventDto.java`, `main/model/SaveAnalysisDefaultsRequest.java`, `main/model/SavePresetRequest.java`, `main/model/SecretStatusDto.java`, `main/model/SystemHealthDto.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:bff:api:test` passes unchanged

### WP4: bff/impl (1/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/bff/impl`
- **Files**: `main/AnalysesApiDelegateImpl.java`, `main/AuthApiDelegateImpl.java`, `main/CatalogApiDelegateImpl.java`, `main/HealthApiDelegateImpl.java`, `main/ReportsApiDelegateImpl.java`, `main/SettingsApiDelegateImpl.java`, `main/UsersApiDelegateImpl.java`, `main/config/ApiAccessDeniedHandler.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:bff:impl:test` passes unchanged

### WP5: bff/impl (2/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/bff/impl`
- **Files**: `main/config/ApiAuthenticationEntryPoint.java`, `main/config/ApiErrorWriter.java`, `main/config/CsrfCookieFilter.java`, `main/config/CurrentUser.java`, `main/config/ExpiredSessionStrategy.java`, `main/config/LoginAttemptLimiter.java`, `main/config/LoginRateLimitFilter.java`, `main/config/MethodSecurityConfig.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:bff:impl:test` passes unchanged

### WP6: bff/impl (3/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/bff/impl`
- **Files**: `main/config/NoPermissionRequired.java`, `main/config/PasswordChangeRequiredFilter.java`, `main/config/PublicRateLimitFilter.java`, `main/config/PublicRequestLimiter.java`, `main/config/RequiresPermission.java`, `main/config/RequiresPermissionAuthorizationManager.java`, `main/config/RoleAuthoritiesRefreshFilter.java`, `main/config/SecurityConfig.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:bff:impl:test` passes unchanged

### WP7: bff/impl (4/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/bff/impl`
- **Files**: `main/config/SessionAuthorities.java`, `main/config/SessionEventStreams.java`, `main/config/UserSessions.java`, `main/error/DomainExceptionHandler.java`, `main/mapper/PresetToPresetDtoMapper.java`, `main/mapper/ReportSectionToStringMapper.java`, `main/mapper/RunEventToRunEventDtoMapper.java`, `main/mapper/error/AnalysisExceptionToApiExceptionMapper.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:bff:impl:test` passes unchanged

### WP8: bff/impl (5/5)

- **Status**: open
- **Depends on**: none
- **Module**: `artifact/backend/bff/impl`
- **Files**: `main/mapper/error/IdentityExceptionToApiExceptionMapper.java`, `test/config/LoginAttemptLimiterTest.java`, `test/config/LoginRateLimitFilterTest.java`, `test/config/PublicRateLimitFilterTest.java`, `test/config/PublicRequestLimiterTest.java`
- **Steps**:
  - [ ] Triage (Design)
- **Tests**: `mise run gradle :backend:bff:impl:test` passes unchanged

### WP9: Rules on for these modules

- **Status**: open
- **Depends on**: WP1-WP8
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Remove the pending block for `bff`
  - [ ] Fix every `TAP-J*` / `TAP-C*` finding left
- **Tests**: `mise run check`; `mise run gradle :backend:bff:api:test`, `mise run gradle :backend:bff:impl:test`

## Tests

Each work package's tests. The proof that the rules now apply: a deliberate Javadoc on a private method in one of the modules fails `mise run check` (not committed).

## Docs to update

None: no document names the modules of the pending blocks (plan 1 describes them in general); plan 9, the last, removes that paragraph.

## Out of scope

- The other modules: the other `.plans/276-comments-cleanup-*.md` plans.
- SQL migrations (a merged one is never edited), Python, shell, TypeScript and Vue comments.
