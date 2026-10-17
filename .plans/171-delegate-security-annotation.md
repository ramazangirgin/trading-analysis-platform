# Plan: Enforce a security annotation on every BFF delegate method

- **Issue**: #171 (RBAC: enforce a security annotation on every BFF delegate method)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

`mise run check` (and so the pre-commit hook and CI) fails when a public method of a BFF delegate
implementation (`*ApiDelegateImpl`) has neither `@RequiresPermission(Permission...)` nor the new
marker `@NoPermissionRequired("reason")`. The finding names the method and its class. A new
endpoint can no longer be added without deciding who may call it, and the build says so before the
integration tests run. The four open or session-only routes of `AuthApiDelegateImpl` (login,
logout, me, password) carry the marker, each with its reason.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Custom Checkstyle checks (`build-logic/checkstyle-rules`) | `build-logic/checkstyle-rules/README.md`: a generic check without project knowledge (class and annotation names are parameters), bean-property parameters with a `suggestion`, messages in `messages.properties` as `<id>: <what is wrong>. <suggestion>`, names matched without type resolution through `support/` (`Imports`, `TypeName`, `SourceNames`), per-check fixtures with `// violation` marks, 100% line/branch/instruction coverage, a reference document from `docs/_template.md`, a row in the checks index. Formatted with Palantir Java Format (`mise run format`). |
| Checkstyle config and project rules | `docs/coding-convention/backend-java-checkstyle.md` → "Project rules": an id `TAP-<group><n>`, the module at the end of `TreeWalker`, fixtures `rules/<id>/Violation.java`, `Compliant.java` (and a suppression fixture), a row in the rules catalogue, at `error` with the existing findings fixed in the same pull request. |
| Backend, BFF (`artifact/backend/bff/impl`) | `docs/coding-convention/backend-java-package-structure.md`: security code (the annotations included) lives in `bff.delegate.impl.config`; an endpoint's permission is on its delegate method in `bff.delegate.impl`. `backend-java-checkstyle.md`: Javadoc summary sentence, suppression rules. |
| Docs | Root `README.md` (the RBAC paragraph), `docs/coding-convention/*` as listed below. |

## Design

**Why Checkstyle, not ArchUnit.** The issue prefers a simple Checkstyle check. The rule is about the
annotations on the methods of one kind of class, all in one file, so a small generic check reads it
from the source; it reports at `checkstyleMain` time with a file and line, before any test runs.

### The check: `RequiredMethodAnnotationCheck`

A new generic check in `build-logic/checkstyle-rules`, package `tr.girgin.checkstyle`, next to
`ForbiddenMemberAccessCheck`. It knows nothing about the BFF.

- **Reports**: a public method, declared directly in a class whose simple name matches one of the
  `classes` patterns, that carries none of the `annotations`.
- **Parameters** (bean properties):
  - `classes` (required, comma-separated): simple class-name patterns. A pattern is a Java
    identifier with an optional `*` at its start and/or its end: `*ApiDelegateImpl`,
    `Foo*`, `*Delegate*`, `Exact`, or `*` alone for every class. Anything else (a dot, a `*` in the
    middle, an empty value) fails the configuration with an `IllegalArgumentException` naming it.
    The matcher goes into `support/` as `SimpleNamePattern` (parse + `matches(String)`), next to
    `Identifiers`, which gets the identifier syntax it needs.
  - `annotations` (required, comma-separated): the annotation types, any one of which satisfies
    the rule, each simple or fully qualified, parsed with `TypeName` and matched against the
    method's annotations through the file's `Imports` (as `ForbiddenMemberAccessCheck` matches
    types): `@RequiresPermission` with an `import ...config.RequiresPermission;`, or written
    qualified, matches the qualified parameter; an unrelated `com.example.RequiresPermission` does
    not.
  - `suggestion` (optional): appended to the message.
  - A missing `classes` or `annotations` fails the configuration in `finishLocalSetup`, as in
    `ForbiddenMemberAccessCheck`.
- **What it reads**: tokens `CLASS_DEF` (to know the class's simple name) and `METHOD_DEF`. A method
  is checked when its `MODIFIERS` contain `public` and its parent `OBJBLOCK` belongs to a
  `CLASS_DEF` whose name matches. Not checked: constructors, methods of nested, local or anonymous
  classes inside a matching class (they are checked only when their own name matches), interfaces,
  records and enums (`CLASS_DEF` only), non-public methods. Static public methods are checked too:
  the rule is "every public method", which keeps it simple to state.
- **Where it reports**: on the method's name (the `IDENT` child of `METHOD_DEF`), so the line is the
  signature, not the first annotation.
- **Message** (`messages.properties`, key `required.method.annotation`):
  `{0}Method ''{1}'' of ''{2}'' has none of the annotations {3}.{4}` with the id prefix, the
  method name, the class's simple name, the annotations as `@<simple name>` joined by `, `, and the
  suggestion, built like `ForbiddenMemberAccessCheck` (`idPrefix()`, `suggestionSuffix()`).
  Example: `TAP-A1: Method 'login' of 'AuthApiDelegateImpl' has none of the annotations
  @RequiresPermission, @NoPermissionRequired. Declare who may call it: ...`. The two helpers are
  copied as they are; extracting a common base class is not part of this plan.

### The marker: `@NoPermissionRequired`

`artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/config/NoPermissionRequired.java`,
next to `RequiresPermission` and shaped like it: `@Target(ElementType.METHOD)`,
`@Retention(RetentionPolicy.RUNTIME)`, `@Documented`, `public @interface NoPermissionRequired { String value(); }`.
`value` is the reason and has no default, so the compiler requires one. Javadoc: the delegate method
needs no permission (an open route, or one that needs only the session `SecurityConfig` requires);
it changes nothing at runtime (`MethodSecurityConfig`'s pointcut matches `@RequiresPermission`
only); it exists so that each exception to the `TAP-A1` rule is visible and has a reason.

`AuthApiDelegateImpl` gets it on its four methods:

| Method | Reason |
|---|---|
| `login` | `"the login itself: the caller has no session yet"` |
| `logout` | `"ends the caller's own session, also one that has expired"` |
| `me` | `"tells the client whether it has a session: 401 without one"` |
| `changePassword` | `"every user changes their own password: a session only"` |

`RequiresPermission`'s Javadoc adds one sentence: every public delegate method has it or
`@NoPermissionRequired`, which the Checkstyle rule `TAP-A1` enforces.

### The project rule `TAP-A1`

A new group **`A`, access control**, in the id scheme (`L`, `D`, `T`, `C`, `J`, `S`, `X` today; `S`
is taken by suppressions). Configured at the end of `TreeWalker` in `config/checkstyle/checkstyle.xml`,
after `TAP-L1`:

```xml
<module name="tr.girgin.checkstyle.RequiredMethodAnnotationCheck">
    <property name="id" value="TAP-A1"/>
    <property name="classes" value="*ApiDelegateImpl"/>
    <property name="annotations" value="
        tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.RequiresPermission,
        tr.girgin.backend.trading.analysis.platform.bff.delegate.impl.config.NoPermissionRequired"/>
    <property name="suggestion" value="Declare who may call it: @RequiresPermission(Permission...), or @NoPermissionRequired(&quot;reason&quot;) for a route that needs no permission."/>
</module>
```

It applies to main and test sources (no `sources` file, no path filter): no test class is named
`*ApiDelegateImpl`. The `*ApiDelegateImpl` name is already enforced for the delegate root by
ArchUnit (`delegate_root_holds_only_delegate_impls`), so every delegate implementation is matched.

Every current delegate method passes: the methods of `AnalysesApiDelegateImpl`,
`CatalogApiDelegateImpl`, `HealthApiDelegateImpl`, `ReportsApiDelegateImpl`,
`SettingsApiDelegateImpl` and `UsersApiDelegateImpl` already carry `@RequiresPermission`;
`AuthApiDelegateImpl`'s get the marker.

`EndpointPermissionsIntegrationTest` stays as it is: it is the runtime proof (a permission is
enforced, and its `OPEN` list also covers the OpenAPI routes, which have no delegate). Its class
Javadoc gets a sentence that `TAP-A1` catches a missing annotation at build time already.

## Work packages

### WP1: The generic check `RequiredMethodAnnotationCheck`

- **Status**: done
- **Depends on**: none
- **Files**:
  - `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/RequiredMethodAnnotationCheck.java` (new)
  - `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/support/SimpleNamePattern.java` (new)
  - `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/support/Identifiers.java` (an `isIdentifier` helper, if needed)
  - `build-logic/checkstyle-rules/src/main/resources/tr/girgin/checkstyle/messages.properties`
  - `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/RequiredMethodAnnotationCheckTest.java` (new)
  - `build-logic/checkstyle-rules/src/test/java/tr/girgin/checkstyle/support/SimpleNamePatternTest.java` (new, if the pattern's cases are clearer there than through the check)
  - `build-logic/checkstyle-rules/src/test/resources/checks/RequiredMethodAnnotationCheck/*.java` (new fixtures)
  - `build-logic/checkstyle-rules/docs/RequiredMethodAnnotationCheck.md` (new)
  - `build-logic/checkstyle-rules/README.md` (a row in "The checks")
- **Steps**:
  - [x] Write `SimpleNamePattern` and the check as designed above.
  - [x] Add the message key.
  - [x] Write the fixtures and the test class, in the style of `ForbiddenMemberAccessCheckTest`
        (a parameterised "reports exactly the marked lines" over fixtures, plus message and
        invalid-value tests); reach 100% coverage.
  - [x] Write the reference document from `docs/_template.md` (what it matches, the parameter table
        listing exactly `classes`, `annotations`, `suggestion`, at least two configuration examples,
        the message, the project rules that use it) and add the index row.
- **Tests**: `RequiredMethodAnnotationCheckTest` with fixtures for: a public method without any
  annotation (reported), with an unrelated annotation only (`@Override`, reported), with one of the
  annotations (simple name with import, qualified in the code, on-demand import, same package:
  not reported), with an annotation of the same simple name from another import (reported for a
  qualified parameter, not for a simple one); non-public, constructor, nested class's method,
  anonymous class's method, interface method (not reported); a class whose name does not match (not
  reported); each pattern form (`*Suffix`, `Prefix*`, `*Middle*`, `Exact`, `*`); several patterns
  and several annotations; the message with and without `id` and `suggestion`, and that it names
  the method and class; invalid `classes` and `annotations` values and missing required
  parameters throw `IllegalArgumentException`/`CheckstyleException` naming them. `DocumentationTest`
  passes with the new document and index row.

### WP2: The marker, the project rule `TAP-A1` and the docs

- **Depends on**: WP1
- **Files**:
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/config/NoPermissionRequired.java` (new)
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/config/RequiresPermission.java` (Javadoc)
  - `artifact/backend/bff/impl/src/main/java/tr/girgin/backend/trading/analysis/platform/bff/delegate/impl/AuthApiDelegateImpl.java`
  - `artifact/backend/src/test/java/tr/girgin/backend/trading/analysis/platform/EndpointPermissionsIntegrationTest.java` (Javadoc only)
  - `config/checkstyle/checkstyle.xml`
  - `build-logic/checkstyle-rules/src/test/resources/rules/TAP-A1/Violation.java`, `Compliant.java`, `Suppressed.java` (new)
  - `docs/coding-convention/backend-java-checkstyle.md`
  - `docs/coding-convention/backend-java-package-structure.md`
  - `README.md`
  - the version files (`mise run version:bump minor`)
- **Steps**:
  - [ ] Add `@NoPermissionRequired` and put it, with the reasons above, on the four methods of
        `AuthApiDelegateImpl`.
  - [ ] Configure `TAP-A1` in `checkstyle.xml` as above.
  - [ ] Add the `rules/TAP-A1` fixtures, in `package tr.girgin.backend.trading.analysis.platform.bff.delegate.impl;`
        with the imports of the real annotations: `Violation.java` (an `*ApiDelegateImpl` with a
        public method without either annotation, and one with only `@Override`, both marked),
        `Compliant.java` (`@RequiresPermission`, `@NoPermissionRequired("...")`, a private helper
        without either), `Suppressed.java` (`@SuppressWarnings("checkstyle:TAP-A1")` with a reason).
  - [ ] Update the docs (table below).
  - [ ] Run `mise run check`; it passes with every current delegate method. Make sure that removing
        `@RequiresPermission` from one delegate method locally fails it with the method's name
        (then put it back).
  - [ ] Bump the version (minor).
- **Tests**: `ProjectRulesTest` proves `TAP-A1` on its fixtures (the marked lines exactly, with the
  id; nothing on the compliant and suppressed ones) and fails without them. `DocumentationTest`
  checks the catalogue row. `checkstyleMain` on `bff/impl` passes on the real delegates.
  `EndpointPermissionsIntegrationTest` still passes (nothing changes at runtime).

## Tests

- Unit: `RequiredMethodAnnotationCheckTest` (every parameter and form, the near misses, the
  message, the invalid values; 100% coverage of the module).
- Project rule: `ProjectRulesTest` for `TAP-A1` on `rules/TAP-A1/*`, the proof that the rule
  reports a delegate method without an annotation, naming it, and accepts the marker.
- Real code: `checkstyleMain` of the backend modules, part of `mise run check` and CI, proves that
  every current delegate method passes.
- Unchanged runtime guard: `EndpointPermissionsIntegrationTest`.
- The issue's "done when" is proved by `ProjectRulesTest` (a delegate method without
  `@RequiresPermission` fails with a message naming the method) and by `mise run check` passing on
  the current code.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `build-logic/checkstyle-rules/README.md` → "The checks" | A row: `RequiredMethodAnnotationCheck`, reports a public method of a selected class without one of the required annotations, project rule `TAP-A1`. |
| Text | `build-logic/checkstyle-rules/docs/RequiredMethodAnnotationCheck.md` (new) | The check's reference document from the template. |
| Text | `docs/coding-convention/backend-java-checkstyle.md` → "Project rules" | The new group `A` (access control) in the id list; a catalogue row for `TAP-A1`: every public method of a `*ApiDelegateImpl` carries `@RequiresPermission` or `@NoPermissionRequired("reason")`; main and test; the check and its parameters; reason: `SecurityConfig` only requires a session for `/api/**`, so a method without one is open to every logged-in user; bad/good example. |
| Text | `docs/coding-convention/backend-java-package-structure.md` → "Where each class kind goes" | The "Permission rule of an endpoint" row: a route that needs no permission carries `@NoPermissionRequired("reason")` (also in `bff.delegate.impl.config`); the Checkstyle rule `TAP-A1` fails the build when a delegate method has neither; `EndpointPermissionsIntegrationTest` proves it at runtime. Rule column: `bff_api_holds_no_security_code`, `TAP-A1` (Checkstyle). |
| Text | `README.md`, the paragraph "Every other `/api/**` route needs a permission" | The build fails (Checkstyle rule `TAP-A1`) while a delegate method has neither `@RequiresPermission` nor `@NoPermissionRequired("reason")`, the marker of the auth routes; `EndpointPermissionsIntegrationTest` checks it at runtime. |
| Screenshot | none | No page or view changes. |

## Out of scope

- Reporting a method that carries both annotations, or an empty reason in `@NoPermissionRequired("")`:
  not asked for; the reason is required by the compiler, its content is the reviewer's.
- Deriving `EndpointPermissionsIntegrationTest`'s `OPEN` list from the marker: the list also holds
  the OpenAPI routes, which have no delegate method.
- A shared base class for the checks' id-prefix and suggestion helpers.
