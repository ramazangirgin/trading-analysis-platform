# Plan: Comments cleanup: BFF

- **Issue**: #276 (Redundant comments: enforce, add LLM planning and review steps, document, clean up)
- **Plan**: 8 of 9 for this issue; depends on: plans 1 (`276-comment-rules`) and 2 (`276-comment-agent-steps`); implemented after plan 7 (`276-comments-settings-library`), as every cleanup plan edits the temporary filters in `config/checkstyle/checkstyle.xml`
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The Javadoc and `//` comments of `bff/api` and `bff/impl` follow
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
  - `bff/api`: `artifact/backend/bff/api/src/<set>/java/tr/girgin/backend/trading/analysis/platform/bff/controller/api/`
  - `bff/impl`: `artifact/backend/bff/impl/src/<set>/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/`
- The last package deletes the block `Temporary (#276, plan 8)` from `checkstyle.xml`; Checkstyle
  then reports what is left in the modules, also in files added since, and that package fixes it.
- springdoc reads no Javadoc (no `therapi-runtime-javadoc`), so `openapi.json` and the frontend's
  API types do not change.

## Work packages

Packages without a dependency between them can be implemented in parallel.

### WP1: `bff/api`: root package, config, error, part 1 of 3

- **Status**: open
- **Depends on**: none
- **Files**: `AnalysesApiController.java`, `CatalogApiController.java`, `HealthApiController.java`, `PublicAnalysesApiController.java`, `ReportsApiController.java`, `config/SpaWebConfig.java`, `error/ApiException.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:bff:api:test` and `mise run check` pass.

### WP2: `bff/api`: model, part 2 of 3

- **Status**: open
- **Depends on**: none
- **Files**: `model/AnalysisPreviewDto.java`, `model/AnalysisPreviewSummaryDto.java`, `model/AnalysisReportDto.java`, `model/ApiErrorDto.java`, `model/CurrentUserDto.java`, `model/PresetDto.java`, `model/PresetValuesDto.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:bff:api:test` and `mise run check` pass.

### WP3: `bff/api`: model, part 3 of 3

- **Status**: open
- **Depends on**: none
- **Files**: `model/PriceHistoryDto.java`, `model/PricePointDto.java`, `model/RunEventDto.java`, `model/SaveAnalysisDefaultsRequest.java`, `model/SavePresetRequest.java`, `model/SecretStatusDto.java`, `model/SystemHealthDto.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:bff:api:test` and `mise run check` pass.

### WP4: `bff/impl`: root package, config, part 1 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `AnalysesApiDelegateImpl.java`, `AuthApiDelegateImpl.java`, `CatalogApiDelegateImpl.java`, `HealthApiDelegateImpl.java`, `ReportsApiDelegateImpl.java`, `UsersApiDelegateImpl.java`, `config/ApiAccessDeniedHandler.java`, `config/ApiAuthenticationEntryPoint.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:bff:impl:test` and `mise run check` pass.

### WP5: `bff/impl`: config, part 2 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `config/ApiErrorWriter.java`, `config/CsrfCookieFilter.java`, `config/CurrentUser.java`, `config/ExpiredSessionStrategy.java`, `config/LoginAttemptLimiter.java`, `config/LoginRateLimitFilter.java`, `config/MethodSecurityConfig.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:bff:impl:test` and `mise run check` pass.

### WP6: `bff/impl`: config, part 3 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `config/NoPermissionRequired.java`, `config/PasswordChangeRequiredFilter.java`, `config/PublicRateLimitFilter.java`, `config/PublicRequestLimiter.java`, `config/RequiresPermission.java`, `config/RequiresPermissionAuthorizationManager.java`, `config/RoleAuthoritiesRefreshFilter.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:bff:impl:test` and `mise run check` pass.

### WP7: `bff/impl`: config, error, mapper, part 4 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `config/SecurityConfig.java`, `config/SessionAuthorities.java`, `config/SessionEventStreams.java`, `config/UserSessions.java`, `error/DomainExceptionHandler.java`, `mapper/PresetToPresetDtoMapper.java`, `mapper/ReportSectionToStringMapper.java`
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:bff:impl:test` and `mise run check` pass.

### WP8: `bff/impl`: mapper, config (main, tests), part 5 of 5

- **Status**: open
- **Depends on**: none
- **Files**: `mapper/RunEventToRunEventDtoMapper.java`, `mapper/error/AnalysisExceptionToApiExceptionMapper.java`, `mapper/error/IdentityExceptionToApiExceptionMapper.java`, `config/LoginAttemptLimiterTest.java` (test), `config/LoginRateLimitFilterTest.java` (test), `config/PublicRateLimitFilterTest.java` (test), `config/PublicRequestLimiterTest.java` (test)
- **Steps**:
  - [ ] Delete, keep or rewrite each Javadoc and `//` comment of the files (Design)
- **Tests**: none new; `mise run gradle :backend:bff:impl:test` and `mise run check` pass.

### WP9: Remove the temporary filter

- **Status**: open
- **Depends on**: WP1–WP8
- **Files**: `config/checkstyle/checkstyle.xml`
- **Steps**:
  - [ ] Delete the block `Temporary (#276, plan 8)`, and fix what Checkstyle then reports in the modules
- **Tests**: `mise run build` green; the Checkstyle tasks of the modules report nothing.

## Tests

Each work package's tests; the last one proves that the rules hold in the modules without a filter.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` "Temporary filters (#276)" | The row of plan 8 goes |

## Out of scope

- The other modules: their own cleanup plans (the table in `.plans/276-comment-rules.md`).
- New comments in these modules after the merge: the agents' comments step (plan 2) and the rules.
