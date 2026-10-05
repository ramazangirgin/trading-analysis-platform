# Plan: Project Checkstyle rules, part 1: the custom checks module

- **Issue**: #55 (Java: project-specific Checkstyle rules (custom checks))
- **Plan**: 1 of 7 for this issue; depends on: none. Plans 2 to 6 add their checks to this module
  and follow the format, documentation and tests set up here.
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The backend's own coding rules are written as **generic, configurable Checkstyle checks** in a
small Java module, `build-logic/checkstyle-rules`. A check knows nothing about this code base: it
takes parameters (which annotation, which method call, which declaration kinds, which suggestion)
and the project rules (`TAP-L1`, `TAP-D2`, ...) are configured instances of those checks in
`config/checkstyle/checkstyle.xml`. Every check has a reference document with all its parameters and
examples of configuring it for different conditions; one index document lists and links every check
and every project rule. The module's tests cover 100% of its lines and branches, and the build fails
below that. This plan builds the module, its wiring, documentation and test setup, and delivers the
first check, `ForbiddenMemberAccessCheck`, with the first rule, `TAP-L1`.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Build (`build-logic/`, root `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`) | Convention plugins in `build-logic`; versions in the catalog; one `./gradlew build` builds and tests everything (`mise run build`, CI's *Backend and frontend* job) |
| Checkstyle (`config/checkstyle/checkstyle.xml`) | `docs/coding-convention/backend-java-checkstyle.md`: one config for every module; `maxWarnings = 0`; suppressions only in the code (`@SuppressWarnings("checkstyle:<id>")` + reason) |
| Docs (`docs/coding-convention/`) | One topic per document, named `<part>-<topic>.md`; listed in `docs/coding-convention/README.md`; "Changing a convention": document, check and code change together |
| Git hooks (`lefthook.yml`) | `docs/coding-convention/repository-git-hooks.md`: fast checks on staged files |

## Design

### Where the code lives

```
build-logic/checkstyle-rules/            own Gradle build (included by the root build)
  settings.gradle.kts                    rootProject.name = "checkstyle-rules"; root version catalog
  build.gradle.kts                       java-library, compileOnly checkstyle (catalog version),
                                         JUnit 5 + AssertJ, jacoco with a 100% gate, Spotless
  src/main/java/tr/girgin/checkstyle/
    ForbiddenMemberAccessCheck.java      one class per check, `<What>Check extends AbstractCheck`
    support/                             shared helpers (name matching, import resolution, ...)
  src/test/java/tr/girgin/checkstyle/
    ForbiddenMemberAccessCheckTest.java  one test class per check
    CheckstyleRunner.java                test harness (below)
    ProjectRulesTest.java                runs config/checkstyle/checkstyle.xml on the rule fixtures
    DocumentationTest.java               every check is documented, every parameter too
  src/test/resources/
    checks/<CheckName>/*.java            fixtures for one check
    rules/<TAP-id>/{Violation,Compliant}.java   fixtures for one project rule
```

- **Package** `tr.girgin.checkstyle`: the checks are not backend code, so they are not under
  `tr.girgin.backend` (the backend's `PackageName` rule does not apply).
- **Own build, not part of `build-logic`**: `build-logic` is included for plugins only
  (`pluginManagement { includeBuild("build-logic") }`), so its projects cannot be used as a
  library dependency. The root `settings.gradle.kts` adds `includeBuild("build-logic/checkstyle-rules")`
  at the top level; Gradle then builds the jar from source when needed, nothing is published.

### How the checks get into the build

- `tradinganalysisplatform.java-library` (the convention plugin that already configures Checkstyle)
  adds `dependencies { add("checkstyle", "tradinganalysisplatform:checkstyle-rules") }`, so the jar
  is on the classpath of `checkstyleMain` / `checkstyleTest` of every backend module.
- `checkstyle.xml` refers to a check by its full class name and configures it per use:

  ```xml
  <module name="tr.girgin.checkstyle.ForbiddenMemberAccessCheck">
      <property name="id" value="TAP-L1"/>
      <property name="members" value="System#out, System#err, *#printStackTrace()"/>
      <property name="suggestion" value="Use an SLF4J logger: log.info(...)."/>
  </module>
  ```
- The root `build.gradle.kts` applies `base` and makes `check` depend on
  `gradle.includedBuild("checkstyle-rules").task(":check")`, so `./gradlew build` (`mise run build`,
  CI) runs the module's tests and coverage gate. `mise run check` gets the module's `check` too
  (its tests take seconds).
- `lefthook.yml`: the backend Checkstyle job also runs when files under `config/checkstyle/` or
  `build-logic/checkstyle-rules/` are staged, and the module's tests run when its files are staged.
- The module is formatted by Spotless with Palantir Java Format like the backend. It is **not**
  checked by Checkstyle itself: its config references the checks being built (a cycle). Its quality
  gate is its tests and the coverage rule.
- Checkstyle API: `compileOnly("com.puppycrawl.tools:checkstyle:<catalog version>")`, the same
  version as `toolVersion`, so a Checkstyle upgrade rebuilds and retests the checks.

### Generic checks: the contract every check follows

- **No project knowledge in code.** Names of types, annotations, methods, messages and scopes are
  parameters. The project rules live only in `checkstyle.xml`.
- **Parameters** are bean properties (`setX(...)`), with documented defaults, comma-separated lists
  where several values make sense, and a `suggestion` parameter appended to the message, so each
  instance says how to fix its finding.
- **Messages**: one `messages.properties` per check package with a key per finding kind; the
  finding reads `<id>: <what is wrong>. <suggestion>` (the id comes from the module's `id`).
- **Name matching without type resolution**: Checkstyle sees source text only. A parameter that
  names a type accepts a simple or a fully qualified name; a check resolves a simple name through the
  file's imports (and `java.lang`) when the parameter is qualified, so `org.springframework...Value`
  does not match an unrelated `Value`. The shared helper in `support/` does this once.
- A check registers only the tokens it needs and has no mutable state across files beyond what
  `beginTree` resets.

### `ForbiddenMemberAccessCheck` (first check)

Reports a reference to a forbidden field or a call of a forbidden method.

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `members` | list of patterns | (required) | `Type#field` for a field (`System#out`); `Type#method()` for a call with any arguments; `Type#method(n)` for a call with exactly `n` arguments (`Instant#now(0)`); `*` as type for any receiver (`*#printStackTrace()`); `*` at the end of a name for a prefix (`Executors#new*()`); `Type#new()` / `Type#new(n)` for a constructor call (`Random#new()`); `Type.*` as receiver for any constant or field of a type (`TimeUnit.*#sleep()` matches `TimeUnit.SECONDS.sleep(1)`). `Type` is simple or qualified |
| `suggestion` | string | empty | Appended to the message |

It will also carry T1, T3, C1, C2 and X4 in later plans, which is the point of making it generic.

### Tests and coverage

- **Harness** `CheckstyleRunner` (test code): builds a `DefaultConfiguration` for one check with
  given properties (or loads a config file), runs a `Checker` on fixture files and returns the
  findings as `(line, column, id, message)`. Fixtures mark expected findings with a trailing
  `// violation` comment; the test asserts the findings equal the marked lines, so a missing and an
  extra finding both fail. Checkstyle's own `AbstractModuleTestSupport` is not published as a
  library, hence the small harness.
- **Per check**: a test per parameter and per value form (simple / qualified / wildcard / argument
  count), near misses that must not be reported, invalid parameter values (a clear
  `IllegalArgumentException` at configuration time), and the message text with and without
  `suggestion`.
- **Coverage gate**: `jacocoTestCoverageVerification` with minimum 1.0 for `LINE`, `BRANCH` and
  `INSTRUCTION` over the module's main classes, and `check` depends on it. Code that cannot be
  reached is removed, not excluded. Jacoco is a Gradle core plugin (no new dependency); its tool
  version comes from the catalog.
- **`ProjectRulesTest`**: loads the real `config/checkstyle/checkstyle.xml` and, for every `TAP-*`
  id configured there, runs it on `rules/<id>/Violation.java` (every marked line reported, with
  that id) and `rules/<id>/Compliant.java` (nothing reported). It fails if an id in the config has
  no fixtures, so a new rule cannot be added without them. Fixtures are copied into a temporary
  `src/main/java` or `src/test/java` path before the run, because the config scopes some rules by
  path; a rule's `Sources` (main / test / both) decide which.
  This replaces the manual "show it fails on a deliberate violation" step for project rules: the
  proof stays in the repository and runs on every build.
- **`DocumentationTest`**: for every `*Check` class, the reference document exists, the index links
  it, and the document's parameter table lists exactly the check's public setters (excluding the
  inherited `id` / `severity`). For every `TAP-*` id in the config, the rules catalogue has a row.

### Documentation

| Document | Content |
|---|---|
| `docs/coding-convention/backend-java-checkstyle-custom-checks.md` (new, the **index**) | What the custom checks are and why generic; the module layout and wiring (above); a table of **every check**: name, one-line purpose, link to its reference, the `TAP-*` rules using it; how to add a check (contract, tests, 100% coverage, reference doc, index row); how to use them in IntelliJ (CheckStyle-IDEA: add `build-logic/checkstyle-rules/build/libs/checkstyle-rules.jar` as a third-party check, built with `./gradlew :checkstyle-rules:jar`) |
| `docs/coding-convention/backend-java-checkstyle-checks/<CheckName>.md` (new, one per check) | Purpose; what it matches and what it does not (no type resolution, near misses); **parameter table** (name, type, default, required, meaning, accepted forms); **configuration examples for at least two different conditions** (e.g. `ForbiddenMemberAccessCheck` for `System.out` and for `Instant.now()` without arguments) with violating and compliant code for each; message format; the `TAP-*` rules that use it |
| `docs/coding-convention/backend-java-checkstyle.md` | New section **Project rules**: the id scheme (`TAP-<group><n>`, groups L, D, T, C, J, S, X), the **rules catalogue** (id, rule, sources, the check with a link to its reference, the parameters used, reason, bad / good example); how to suppress one (`@SuppressWarnings("checkstyle:TAP-L1")` + reason); a project rule goes in at `error` with its hits fixed in the same pull request (`maxWarnings = 0` makes a warning fail the build too, so "start at warning" in "Adding or changing a rule" is corrected); built-in checks (`IllegalImport`, `TodoComment`, `RegexpSingleline`) are used for a rule when one fits, with a link to their checkstyle.org page instead of a reference document |
| `docs/coding-convention/README.md` | Table rows for the index document; the enforcement table names the custom checks module |

The reference documents follow one template (WP4), so every check reads the same.

### First rule

| Id | Rule | Check and parameters | Sources | Hits today |
|---|---|---|---|---|
| TAP-L1 | No `System.out` / `System.err` / `printStackTrace()`; use SLF4J | `ForbiddenMemberAccessCheck`, `members = System#out, System#err, *#printStackTrace(0)` | main and test | 0 |

The suppression mechanism with a hyphenated id is verified in `ProjectRulesTest` (a fixture with
`@SuppressWarnings("checkstyle:TAP-L1")` reports nothing). If `SuppressWarningsFilter` does not
accept the hyphen, every id switches to `TapL1` form and the docs follow.

## Work packages

### WP1: Module and build wiring

- **Depends on**: none
- **Files**: `build-logic/checkstyle-rules/settings.gradle.kts`, `build-logic/checkstyle-rules/build.gradle.kts`,
  `settings.gradle.kts`, `build.gradle.kts`, `build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts`,
  `gradle/libs.versions.toml` (jacoco tool version, test library entries if missing), `lefthook.yml`,
  `mise.toml` (`check` task)
- **Steps**:
  - [ ] Create the build with Spotless, JUnit, AssertJ, jacoco and the 100% gate.
  - [ ] Include it; add the `checkstyle` dependency in the convention plugin; root `check` depends on
        the module's `check`; `mise run check` runs it.
  - [ ] Hook globs as described.
  - [ ] Confirm the backend's `checkstyleMain` resolves a class from the module (WP3's config).
- **Tests**: `./gradlew build` runs the module's tests and gate; the CI job needs no change, since
  it runs `mise run build` (state this in the PR).

### WP2: `ForbiddenMemberAccessCheck`, shared helpers and harness

- **Depends on**: WP1
- **Files**: `ForbiddenMemberAccessCheck.java`, `support/*`, `messages.properties`,
  `CheckstyleRunner.java`, `ForbiddenMemberAccessCheckTest.java`, `src/test/resources/checks/ForbiddenMemberAccessCheck/*`
- **Steps**:
  - [ ] Pattern parsing with validation; matching of `IDENT` / `DOT` / `METHOD_CALL` with argument
        counts; import-based resolution in `support/`.
  - [ ] Harness and tests as described.
- **Tests**: every pattern form (constructor calls included), every near miss (a local variable called `out`, `System.out` in a
  comment or string, `Instant.now(clock)` against `Instant#now(0)`, a static import of `out`),
  invalid patterns, message with and without suggestion; 100% coverage.

### WP3: `TAP-L1`, `ProjectRulesTest`, `DocumentationTest`

- **Depends on**: WP2
- **Files**: `config/checkstyle/checkstyle.xml`, `ProjectRulesTest.java`, `DocumentationTest.java`,
  `src/test/resources/rules/TAP-L1/{Violation,Compliant}.java` and a suppression fixture
- **Steps**:
  - [ ] Section `<!-- Project rules (#55): configured custom checks; see backend-java-checkstyle.md -->`
        at the end of `TreeWalker`, with `TAP-L1`; update the file's header comment.
  - [ ] `ProjectRulesTest` and `DocumentationTest` as described.
  - [ ] Backend `checkstyleMain` / `checkstyleTest` clean.
- **Tests**: the two test classes; they fail if a rule or a check lacks fixtures or docs.

### WP4: Documentation

- **Depends on**: WP2, WP3
- **Files**: `docs/coding-convention/backend-java-checkstyle-custom-checks.md` (new),
  `docs/coding-convention/backend-java-checkstyle-checks/ForbiddenMemberAccessCheck.md` (new),
  `docs/coding-convention/backend-java-checkstyle-checks/_template.md` (new, the reference template),
  `docs/coding-convention/backend-java-checkstyle.md`, `docs/coding-convention/README.md`
- **Steps**:
  - [ ] Write the documents as in Design; the intro of `backend-java-checkstyle.md` links the index
        instead of "Project-specific rules (#55) are added to the same config."
- **Tests**: `DocumentationTest` passes.

## Tests

`mise run build`: the module's unit tests with 100% line and branch coverage, `ProjectRulesTest`
(TAP-L1 on its fixtures, with the real config), `DocumentationTest`, and every backend module's
Checkstyle with the new rule.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/backend-java-checkstyle-custom-checks.md` | New index |
| Text | `docs/coding-convention/backend-java-checkstyle-checks/` | New: template, `ForbiddenMemberAccessCheck.md` |
| Text | `docs/coding-convention/backend-java-checkstyle.md` | Project rules section, catalogue with TAP-L1, severity correction, intro link |
| Text | `docs/coding-convention/README.md` | Index row; enforcement table |
| Text | `docs/coding-convention/repository-git-hooks.md` | New globs of the backend job and the module's tests |
| Text | Root `README.md` | The CI table row of *Backend and frontend* names the custom Checkstyle checks' tests |
| Screenshot | none | No UI change |

## Out of scope

- The other rules: L2, L3 (plan 2), D (plan 3), T and C (plan 4), J and S (plan 5), X (plan 6),
  G and D3 in ArchUnit (plan 7). L4 moves to #46 (plan 2 comments there).
