# Plan: Project Checkstyle rules, part 6: tests (X)

- **Issue**: #55 (Java: project-specific Checkstyle rules (custom checks))
- **Plan**: 6 of 7 for this issue; depends on: `55-checkstyle-rules-module` and
  `55-configuration-properties` (its `RequiredAnnotationAttributeCheck` carries X2)
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Backend test sources follow four rules, checked by `checkstyleTest`: a class with tests is named
`*Test` (`*IntegrationTest` for Spring context tests), `@Disabled` gives a reason, assertions use
AssertJ only, and tests wait with Awaitility instead of `Thread.sleep`. Each finding carries a
`TAP-X*` id. X1 gets a new generic check, `AnnotatedTypeNameCheck`; the others reuse checks from
plans 1 and 3 and the built-in `IllegalImport`.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Custom checks module, Checkstyle config, docs | The contract from plan 1: generic checks with documented parameters, a reference per check, index and catalogue rows, `ProjectRulesTest` fixtures per rule, 100% coverage; test-only scope through a `SuppressionSingleFilter`, like the existing `MissingJavadocPortType` scope |
| Backend tests (`artifact/backend/**/src/test`) | `backend-java-checkstyle.md`; dependencies come from the version catalog `gradle/libs.versions.toml` |

## Design

### `AnnotatedTypeNameCheck` (new)

Requires a name format for types that carry, or have members carrying, given annotations.

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `typeAnnotations` | list of type names | empty | Types annotated with one of these are checked (`RestController`) |
| `memberAnnotations` | list of type names | empty | Types with a method or field annotated with one of these are checked (`Test`, `ArchTest`) |
| `format` | regex | (required) | The type's simple name must match (`^.*Test$`) |
| `topLevelOnly` | boolean | `true` | Only top-level types; with `false`, nested types too (JUnit `@Nested` classes have their own names) |
| `suggestion` | string | empty | Appended to the message |

At least one of `typeAnnotations` / `memberAnnotations` must be set (validated). Names are simple or
qualified, resolved through imports.

### Rules

| Id | Rule | Check and parameters | Hits today → fix |
|---|---|---|---|
| TAP-X1 | A top-level class with a `@Test` / `@ParameterizedTest` / `@RepeatedTest` / `@TestFactory` method, or `@ArchTest` members, ends in `Test`. Helpers (`Fakes`, `TestPlatformHome`, `AdapterTestSupport`, `RecordingSink`) have no such members and are not matched | `AnnotatedTypeNameCheck`: `memberAnnotations = org.junit.jupiter.api.Test, org.junit.jupiter.params.ParameterizedTest, org.junit.jupiter.api.RepeatedTest, org.junit.jupiter.api.TestFactory, com.tngtech.archunit.junit.ArchTest`, `format = ^.*Test$` | 1: `TradingPlatformApplicationTests` → renamed `TradingPlatformApplicationIntegrationTest` (it starts the Spring context, like `AnalysesApiIntegrationTest`) |
| TAP-X2 | `@Disabled` has a reason: `@Disabled("#123: flaky on CI")` | `RequiredAnnotationAttributeCheck` (plan 3): `annotation = org.junit.jupiter.api.Disabled`, `attributes = value` | 0 |
| TAP-X3 | AssertJ only: no `org.junit.jupiter.api.Assertions`, no Hamcrest | built-in `IllegalImport`, own instance (`illegalPkgs` `org.hamcrest`, `illegalClasses` `org.junit.jupiter.api.Assertions`); `org.junit.jupiter.api.Assertions.*` is removed from `AvoidStaticImport`'s `excludes` | 0 |
| TAP-X4 | No `Thread.sleep` in tests: wait for a condition with Awaitility, or drive time with a test `Clock` | `ForbiddenMemberAccessCheck`: `members = Thread#sleep(), TimeUnit.*#sleep()` | 8 (below) |

All four ids are scoped to test sources by a `SuppressionSingleFilter` with `id` `^TAP-X\d+$` and
`files` matching `[\\/]src[\\/]main[\\/]`.

### Awaitility

`org.awaitility:awaitility` is added as a test dependency. Its version is managed by the Spring
Boot BOM already in the catalog (`spring-boot-bom`), so the catalog gets an entry without a version,
like `spring-boot-starter-test`, and the modules whose tests wait (`domain:analysis:adapter`,
`domain:report:core`, `:backend`) add it with `testImplementation`. `org.awaitility.Awaitility.*` is
added to `AvoidStaticImport`'s `excludes` (a fluent test DSL, like AssertJ).

### The 8 existing sleeps

Each is either a **wait for something to happen** (replace with
`await().atMost(...).until(...)`/`untilAsserted(...)`), a **check that nothing happens in a period**
(replace with `await().during(...).atMost(...).until(...)`), or a **simulated slow collaborator**
inside a fake (a fake stream or process that must take time; keep it with
`@SuppressWarnings("checkstyle:TAP-X4") // simulates a slow ...` on the smallest element).

| Test | Lines | Expected kind |
|---|---|---|
| `DockerRunnerAdapterTest` | 219, 250 | 219: wait; 250: inside a fake, likely simulation |
| `ProcessRunnerAdapterTest` | 260 | inside a fake loop, likely simulation |
| `DataDirWatchServiceTest` | 32, 37, 72 | "nothing fires during the quiet period": `during` |
| `DataDirWatchServiceTest` | 60 | changes spaced out in time: simulation, or drive `DataDirWatchService`'s `nanoTime` supplier instead |
| `AnalysesApiIntegrationTest` | 308 | wait for a run state: `await` |

The developer reads each before choosing; the table is the expectation, not the verdict.

## Work packages

### WP1: Awaitility and the existing sleeps

- **Depends on**: none
- **Files**: `gradle/libs.versions.toml`, `artifact/backend/domain/analysis/adapter/build.gradle.kts`,
  `artifact/backend/domain/report/core/build.gradle.kts`, `artifact/backend/build.gradle.kts`,
  `DockerRunnerAdapterTest.java`, `ProcessRunnerAdapterTest.java`, `DataDirWatchServiceTest.java`,
  `AnalysesApiIntegrationTest.java`
- **Steps**:
  - [ ] Add Awaitility as described.
  - [ ] Replace or suppress each sleep as described.
  - [ ] Run each changed test class 5 times (`--rerun`) to make sure it is not flaky.
- **Tests**: the changed tests still prove the same behaviour; none is weakened (same assertions,
  timeouts at least as generous as the old sleeps).

### WP2: Rename the context test

- **Depends on**: none
- **Files**: `artifact/backend/src/test/java/.../TradingPlatformApplicationTests.java` →
  `TradingPlatformApplicationIntegrationTest.java`; any reference to the old name (search the repo:
  docs, `lefthook.yml`, workflows)
- **Steps**:
  - [ ] `git mv` and rename the class.
- **Tests**: it still runs in `:backend:test`.

### WP3: `AnnotatedTypeNameCheck`

- **Depends on**: none (plan 1 merged)
- **Files**: `build-logic/checkstyle-rules/src/main/java/tr/girgin/checkstyle/AnnotatedTypeNameCheck.java`,
  `messages.properties`, its test class, `src/test/resources/checks/AnnotatedTypeNameCheck/*`
- **Tests**: type and member annotations, each alone and together; a helper class without such
  members (not reported); nested types with `topLevelOnly` on and off; records, enums, interfaces;
  qualified and simple names; neither annotation list set (configuration error). 100% coverage.

### WP4: Rules X1 to X4

- **Depends on**: WP1, WP2, WP3 (and plan 3 merged)
- **Files**: `config/checkstyle/checkstyle.xml`, `src/test/resources/rules/TAP-X1/*` ... `TAP-X4/*`
- **Steps**:
  - [ ] Add the instances, the test-only scope filter, the `AvoidStaticImport` changes.
  - [ ] Fixtures for `ProjectRulesTest` (placed in a test source path), with near misses as compliant
        code: a helper class `Fakes`, `@Disabled("reason")`, AssertJ `assertThat`; and a
        `Thread.sleep` in a main source path, which X4 does not report (C1 from plan 4 does).
  - [ ] Backend Checkstyle clean.
- **Tests**: `ProjectRulesTest`.

### WP5: Documentation

- **Depends on**: WP4
- **Files**: `docs/coding-convention/backend-java-checkstyle-checks/AnnotatedTypeNameCheck.md` (new),
  `RequiredAnnotationAttributeCheck.md`, `ForbiddenMemberAccessCheck.md` ("used by"),
  `docs/coding-convention/backend-java-checkstyle-custom-checks.md`,
  `docs/coding-convention/backend-java-checkstyle.md`
- **Steps**:
  - [ ] Reference document for `AnnotatedTypeNameCheck` with configuration examples for at least
        two conditions (X1, and e.g. `@RestController` types end in `Controller`).
  - [ ] "Used by" of the reused checks; index rows.
  - [ ] Catalogue rows X1 to X4, with reasons and bad / good examples (a sleep and its Awaitility
        replacement).
  - [ ] Imports section: JUnit `Assertions` is no longer allowed as a static import; Awaitility is.
  - [ ] Test-only scope next to the other scopes in "Suppressing a finding"; any `TAP-X4`
        suppression in the suppressions table.
- **Tests**: `DocumentationTest`.

## Tests

`mise run build`: `AnnotatedTypeNameCheck`'s unit tests at 100% coverage, `ProjectRulesTest` with the
X fixtures, `DocumentationTest`, backend Checkstyle clean; the reworked tests are stable over
repeated runs.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle.md` | Catalogue rows X; imports; scopes; suppressions table |
| Text | `docs/coding-convention/backend-java-checkstyle-checks/` | New `AnnotatedTypeNameCheck.md`; "used by" of the reused checks |
| Text | `docs/coding-convention/backend-java-checkstyle-custom-checks.md` | New index row; rules columns |
| Text | Root `README.md` | None, unless it names `TradingPlatformApplicationTests` (WP2 searches) |
| Screenshot | none | No UI change |

## Out of scope

- Converting `DataDirWatchService`'s real scheduler to a fake one for fully time-driven tests.

## Open questions

- Awaitility is a new test library (the plan's only new tool). The alternative is to drop X4, or to
  keep the sleeps with suppressions. Add it?
