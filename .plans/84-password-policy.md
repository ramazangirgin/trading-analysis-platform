# Plan: Password policy

- **Issue**: #84 (Auth: password policy)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Weak passwords are rejected wherever a password is set today: changing your own password
(`POST /api/auth/password`) and the configured first-admin password (`PLATFORM_ADMIN_PASSWORD`). One
`PasswordPolicy` in the identity core checks a minimum length (default 12, configurable), a maximum
length (128), a bundled list of the 10,000 most common passwords, and that the password is not the
username. It has no composition rules (NIST SP 800-63B). A rejected change answers 400
`weak_password` with machine-readable reasons, which the change-password form shows under the
new-password field. A configured first-admin password that breaks the policy stops the startup with
a message that names the broken rules. The admin endpoints that set passwords (#88, not built yet)
use the same policy when they are built.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend (`domain/identity/core`) | `backend-java-package-structure.md`: the policy is a package-private class in `domain.identity.core.service` (next to `OneTimePasswordGenerator`), the new error code goes into `IdentityError` in `core.exception`; the core depends on no infrastructure (`domain_core_does_not_depend_on_infrastructure`). `backend-java-checkstyle.md`, `backend-java-formatting.md` (Spotless, `mise run format`) |
| Backend (`bff/impl`) | `backend-java-package-structure.md`: `IdentityExceptionToApiExceptionMapper` in `bff.delegate.impl.mapper.error` maps the new code; no change to `bff/api` (the error shape `ApiErrorDto(errorCode, message, params)` stays) |
| Frontend (`artifact/frontend`) | `frontend-folder-structure.md`: the reason → message logic is a pure function in `features/auth/model/` (tested like `passwordGate.ts`), the form in `features/auth/components/`; messages in both locales (`shared/i18n/locales/en.json`, `tr.json`; `locales.spec.ts` checks they have the same keys); Prettier, ESLint boundaries |
| End-to-end tests (`e2e/`) | the existing `e2e/tests/password.e2e.ts` and its helpers in `e2e/tests/support.ts` |
| Deploy | `deploy/.env.example`, `deploy/docker-compose.yml` pass the new setting the way they pass `PLATFORM_ADMIN_PASSWORD` |
| Docs | root `README.md`; English only |

## Design

### The policy (identity core)

`PasswordPolicy` (`domain/identity/core/.../core/service/PasswordPolicy.java`), a package-private
Spring `@Component`, so every service of the package (today `PasswordChangeService` and
`FirstAdminService`, later #88's user management) uses the one instance:

- `void check(CharSequence password, Username username)`: collects every broken rule, in this
  order, and throws one `IdentityException(IdentityError.WEAK_PASSWORD, <message>, params)` when
  any is broken. `params`: `reasons` (a `List<String>` of the reason codes below, in this order),
  `minLength`, `maxLength`. The message is English and names the reasons (`"The password breaks the
  password policy: too_short, common"`); it never contains the password.
- Reasons (the enum `PasswordPolicyViolation` in `core.model`, written lowercase in the params:
  `too_short`, `too_long`, `common`, `same_as_username`):
  - `too_short`: fewer than `minLength` characters. Length is counted in Unicode code points
    (`codePointCount`), so an emoji or a Turkish letter counts as one. No trimming, spaces count.
  - `too_long`: more than `maxLength` (128) code points. Fixed, not configurable: it bounds the
    hashing work and is far above any password a person types.
  - `common`: the password, lower-cased with `Locale.ROOT`, is in the bundled list.
  - `same_as_username`: equals the username, ignoring case (`equalsIgnoreCase`, as usernames are
    compared case-insensitively).
- `minLength` comes from `platform.password.min-length` (`@Value("${platform.password.min-length:12}")`,
  like `FirstAdminService` reads its settings). The constructor rejects a value below 8 (NIST's
  floor) or above 64 with an `IllegalArgumentException` naming the property and the allowed range,
  which stops the startup. A package-private constructor takes `minLength` and the list's content
  for the unit tests.
- `minLength()` is package-visible, so `OneTimePasswordGenerator` can be asked for a password of at
  least that length (below).

The common-password list is a resource of the core module,
`domain/identity/core/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/identity/core/service/common-passwords.txt`:
SecLists' `Passwords/Common-Credentials/10k-most-common.txt` (MIT licence), one password per line,
with a `#` header naming the source URL, the SecLists commit and the licence. The loader skips blank
and `#` lines, lower-cases every entry, and keeps them in an immutable `Set<String>`, loaded once in
the constructor (about 10k short strings, well under 1 MB). A missing or empty resource fails the
startup (`IllegalStateException`), so a packaging mistake never turns the check off silently.
Most entries are shorter than 12 characters; they are kept, since the minimum length can be set
down to 8.

### Where it is applied

- **`PasswordChangeService.changePassword`**: after the current-password check and before the
  `PASSWORD_UNCHANGED` check, `policy.check(newPassword, user.username())`. A wrong current password
  is still reported first, so the policy answer never tells an attacker with a stolen session
  anything new. Nothing is saved when the policy throws.
- **`FirstAdminService.createFirstAdminIfNoUser`**: only when a password is configured and no user
  exists (the only case where the configured password is used), `policy.check(password, name)`
  before hashing. A violation is turned into an `IllegalStateException` with the message
  `"PLATFORM_ADMIN_PASSWORD breaks the password policy: <reasons> (at least <min> and at most
  <max> characters, not a common password, not the username). Set another one, or leave it empty
  to get a generated password."`. The exception propagates out of the `ContextRefreshedEvent`
  listener and stops the startup, as an invalid username does today. The password is never logged.
  When users exist, the configured password is ignored as today and not checked: an unused value
  must not stop a running installation.
- **The generated first-admin password**: `OneTimePasswordGenerator.generate(int length)` takes the
  length; `FirstAdminService` asks for `Math.max(24, policy.minLength())`, so a raised minimum never
  produces a generated password that breaks it. The generated password is not run through the
  policy (it is random, 24+ characters of `A-Z a-z 0-9`).
- **#88 (admin creates a user, resets a password)** does not exist yet; its services live in the
  same package and call the same `PasswordPolicy`. The Javadoc of `ChangePasswordUseCase` and
  `CreateFirstAdminUseCase` replaces "The password policy check … (#84) belongs in the
  implementation" with what is now checked and the `WEAK_PASSWORD` error.

### API

`IdentityError.WEAK_PASSWORD`; `IdentityExceptionToApiExceptionMapper` maps it to 400, error code
`weak_password`, with the params as they are. Example answer of `POST /api/auth/password`:

```json
{"errorCode": "weak_password",
 "message": "The password breaks the password policy: too_short, common",
 "params": {"reasons": ["too_short", "common"], "minLength": 12, "maxLength": 128}}
```

The request and response types do not change, so `openapi.json` and `schema.d.ts` stay as they are
(`params` is already a free-form object). No endpoint exposes the policy's settings: the reasons and
lengths come with the 400.

### Configuration

`artifact/backend/src/main/resources/application.properties`:
`platform.password.min-length=${PLATFORM_PASSWORD_MIN_LENGTH:12}`, next to `platform.admin.*`.
`deploy/docker-compose.yml` passes `PLATFORM_PASSWORD_MIN_LENGTH: ${PLATFORM_PASSWORD_MIN_LENGTH:-12}`
to the platform service; `deploy/.env.example` lists it, commented, with the default and the range.

### Frontend

- `features/auth/model/passwordPolicy.ts`: `weakPasswordMessages(params: Record<string, unknown>):
  { key: string; params: Record<string, unknown> }[]`, one entry per known reason, in the order
  given (`too_short` → `password.policy.too_short` with `{ minLength }`, `too_long` →
  `password.policy.too_long` with `{ maxLength }`, `common` → `password.policy.common`,
  `same_as_username` → `password.policy.same_as_username`). Unknown reasons are skipped; when no
  reason is known it returns `[{ key: 'errors.weak_password', params: {} }]`, so the field always
  shows something.
- `ChangePasswordForm.vue`: `weak_password` becomes a third field error. The new-password
  `NFormItem` gets `validation-status="error"` and its `feedback` lists the messages, one per line
  (a `#feedback` slot with one `<div>` per message). The other field errors and the top alert stay
  as they are. Typing in the new-password field clears the field error.
- `en.json` / `tr.json`: `errors.weak_password` ("The password does not meet the password policy."),
  and `password.policy.too_short` ("At least {minLength} characters."), `too_long` ("At most
  {maxLength} characters."), `common` ("This password is too common; choose another one."),
  `same_as_username` ("The password must not be your username."), with Turkish translations.

## Work packages

### WP1: The policy in the identity core

- **Status**: done
- **Depends on**: none
- **Files**:
  - `artifact/backend/domain/identity/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/service/PasswordPolicy.java` (new)
  - `artifact/backend/domain/identity/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/model/PasswordPolicyViolation.java` (new)
  - `artifact/backend/domain/identity/core/src/main/resources/tr/girgin/backend/trading/analysis/platform/domain/identity/core/service/common-passwords.txt` (new)
  - `artifact/backend/domain/identity/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/exception/IdentityError.java`
  - `artifact/backend/domain/identity/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/service/PasswordChangeService.java`
  - `artifact/backend/domain/identity/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/service/FirstAdminService.java`
  - `artifact/backend/domain/identity/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/service/OneTimePasswordGenerator.java`
  - `artifact/backend/domain/identity/core/src/main/java/tr/girgin/backend/trading/analysis/platform/domain/identity/core/inbound/ChangePasswordUseCase.java`, `CreateFirstAdminUseCase.java` (Javadoc)
  - tests: `…/core/service/PasswordPolicyTest.java` (new), `PasswordChangeServiceTest.java`, `FirstAdminServiceTest.java`, `OneTimePasswordGeneratorTest.java`
- **Steps**:
  - [x] Download SecLists' `10k-most-common.txt` at a pinned commit, add the header (source URL,
        commit, MIT licence), save it as the resource.
  - [x] `PasswordPolicyViolation`, `IdentityError.WEAK_PASSWORD`, `PasswordPolicy` as in the design.
  - [x] Apply it in `PasswordChangeService` and `FirstAdminService`; `OneTimePasswordGenerator.generate(int)`.
  - [x] Update the two use cases' Javadoc.
  - [x] Check that every password the tests and scripts use passes the policy
        (`TestSession.PASSWORD`, `PasswordChangeIntegrationTest.NEW_PASSWORD`,
        `FirstAdminConfiguredPasswordIntegrationTest.PASSWORD`, `e2e-test-password`,
        `smoke-test-only-password`, the e2e new passwords, `configured-secret` in
        `FirstAdminServiceTest`: 17 characters, fine); change any that is in the list.
- **Tests**:
  - `PasswordPolicyTest`: each rule alone (11/12 characters at the default minimum, 128/129 at the
    maximum, code points: a 12-character password of non-BMP characters passes, an entry of the
    list in another case, the username in another case); several broken rules come back in the
    fixed order with `minLength`/`maxLength` in the params; the message does not contain the
    password; a configured minimum of 7 and 65 is rejected, 8 and 64 accepted; a lowered minimum (8)
    lets an 8-character uncommon password through; the bundled resource loads and contains, for
    example, `password` and `123456`; an empty list resource fails.
  - `PasswordChangeServiceTest`: a weak new password throws `WEAK_PASSWORD` and saves nothing; a
    wrong current password with a weak new one still throws `WRONG_CURRENT_PASSWORD`.
  - `FirstAdminServiceTest`: a configured weak password (too short; the username; a common one)
    fails with `IllegalStateException` naming `PLATFORM_ADMIN_PASSWORD` and the reasons, saves no
    user and logs nothing containing the password; with users present a weak configured password is
    ignored without failing; the generator is asked for `max(24, minLength)` characters.
  - `OneTimePasswordGeneratorTest`: the given length, the alphabet.

### WP2: API answer and startup integration

- **Status**: done
- **Depends on**: WP1
- **Files**:
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/mapper/error/IdentityExceptionToApiExceptionMapper.java`
  - `artifact/backend/src/main/resources/application.properties`
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/PasswordChangeIntegrationTest.java`
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/FirstAdminWeakPasswordIntegrationTest.java` (new)
  - the mapper's test, if there is one (`…/bff/delegate/impl/mapper/error/`)
  - `deploy/docker-compose.yml`, `deploy/.env.example`
- **Steps**:
  - [x] Map `WEAK_PASSWORD` to 400 in the mapper's switch.
  - [x] Add `platform.password.min-length` to `application.properties`; pass and document
        `PLATFORM_PASSWORD_MIN_LENGTH` in the deploy files.
- **Tests**:
  - `PasswordChangeIntegrationTest`: a too-short new password answers 400 `weak_password` with
    `params.reasons == ["too_short"]`, `minLength` 12 and `maxLength` 128, and the old password
    still logs in; a common password (`password1234567` or another long entry of the list) answers
    `["common"]`; the username as password answers `["same_as_username"]`.
  - `FirstAdminWeakPasswordIntegrationTest`: starting the application (`SpringApplicationBuilder`
    against the test PostgreSQL container used by the other integration tests, empty identity
    schema) with a short, distinctive `platform.admin.password` (`Xy7-weak`) fails, and the root
    cause's message names `PLATFORM_ADMIN_PASSWORD` and `too_short` and does not contain `Xy7-weak`;
    no user is created. A second case: an
    application start with `platform.password.min-length=7` fails naming the property.

### WP3: Frontend and end-to-end

- **Status**: done
- **Depends on**: WP2
- **Files**:
  - `artifact/frontend/src/features/auth/model/passwordPolicy.ts` (new), `passwordPolicy.spec.ts` (new)
  - `artifact/frontend/src/features/auth/components/ChangePasswordForm.vue`
  - `artifact/frontend/src/shared/i18n/locales/en.json`, `tr.json`
  - `e2e/tests/password.e2e.ts`
- **Steps**:
  - [x] `weakPasswordMessages` as in the design (only the e2e test needs WP2's backend answer).
  - [x] The form's third field error, cleared on input.
  - [x] The new messages in both locales.
- **Tests**:
  - `passwordPolicy.spec.ts`: every reason maps to its key and params, the order is kept, unknown
    reasons are skipped, no known reason falls back to `errors.weak_password`.
  - `locales.spec.ts` (existing) proves both locales have the keys.
  - `password.e2e.ts`: a new test, "a weak new password shows the policy's reasons": with the
    correct current password (`E2E_PASSWORD`) and `short` as new and repeated password, the form
    stays on `/password`, the new-password field shows "At least 12 characters.", and the user can
    still log in with `E2E_PASSWORD` afterwards (nothing changed). It must not change the e2e
    user's password, since the other tests run in parallel with it.

### WP4: Docs

- **Status**: done
- **Depends on**: WP1–WP3
- **Files**: `README.md`
- **Steps**: the changes in "Docs to update".
- **Tests**: none (text only).

## Tests

- Unit: `PasswordPolicyTest` covers each rule and the configuration bounds; the service tests prove
  both places that set a password today use the policy ("each rule has tests, and every place that
  sets a password uses the same policy").
- Integration: `PasswordChangeIntegrationTest` (the 400 with its reasons through the real stack) and
  `FirstAdminWeakPasswordIntegrationTest` (a weak configured first-admin password stops startup with
  a clear message).
- Frontend: `passwordPolicy.spec.ts`; e2e: the weak-password case in `password.e2e.ts`.
- `mise run check` and the full build (`mise run build`) pass.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md` "3. Log in" (Quick start) | A configured `PLATFORM_ADMIN_PASSWORD` must meet the password policy (at least 12 characters, not a common password, not the username), or the platform does not start and says why |
| Text | `README.md`, the change-password / auth API paragraph (around "`POST /api/auth/password` … answers 200 … or 400 `wrong_current_password` / `password_unchanged`") | Add 400 `weak_password` with `params.reasons` (`too_short`, `too_long`, `common`, `same_as_username`), `minLength`, `maxLength`; a short paragraph on the policy: 12 to 128 characters, no composition rules (NIST SP 800-63B), checked against the 10,000 most common passwords (SecLists), not the username; the minimum is set with `PLATFORM_PASSWORD_MIN_LENGTH` (8 to 64) |
| Text | `README.md` "Run with Docker Compose" | `.env` may set `PLATFORM_PASSWORD_MIN_LENGTH` |
| Text | `README.md` CI table, *End-to-end tests* row | "change password (menu, wrong current password)" becomes "change password (menu, wrong current password, weak new password)" |
| Text | `deploy/.env.example` | `PLATFORM_PASSWORD_MIN_LENGTH`, commented, default 12, range 8–64 |
| Screenshot | none | `docs/screenshots/change-password.png` shows the empty form, which does not change; the policy messages only appear after a rejected submit |

## Out of scope

- The admin endpoints that create users and reset passwords: #88, which calls this policy.
- Showing the minimum length before the first submit (a policy endpoint or a live strength meter).
- Checking against breached-password services (Have I Been Pwned): it would send prefixes of
  password hashes to an outside service; the bundled list keeps the platform offline.
- Unicode normalisation (NFKC) of passwords: hashing stores what the user typed, as today.
- Rechecking existing passwords: users keep theirs until they change it.
