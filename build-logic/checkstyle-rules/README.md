# Custom Checkstyle checks

Checkstyle's built-in checks cover most of the [backend's rules](../../docs/coding-convention/backend-java-checkstyle.md). The rest,
the project's own rules (`TAP-L1`, `TAP-D2`, ...), are written as **generic, configurable checks** in a
small Java module, `build-logic/checkstyle-rules` (this folder), and configured
as **project rules** in [`config/checkstyle/checkstyle.xml`](../../config/checkstyle/checkstyle.xml).

A check knows nothing about this code base. Which annotation, which method call, which declaration kind
and which advice to give are parameters, so one check serves several rules, and a rule is a configured
instance of a check: a few lines of XML, no Java. The rules, with their reasons and examples, are in the
[rules catalogue](../../docs/coding-convention/backend-java-checkstyle.md#project-rules).

## The checks

| Check | Reports | Project rules |
|---|---|---|
| [`ForbiddenMemberAccessCheck`](docs/ForbiddenMemberAccessCheck.md) | a reference to a forbidden field, a call of a forbidden method or constructor | `TAP-L1` |

Every check has a reference document in
[`docs/`](docs/), written from the
[template](docs/_template.md): what it matches, a table of all its parameters,
configuration examples for at least two different conditions, and its message.

## Module layout and wiring

```
build-logic/checkstyle-rules/            an own Gradle build, included by the root settings.gradle.kts
  src/main/java/tr/girgin/checkstyle/    one class per check: <What>Check extends AbstractCheck
    support/                             shared helpers (name matching, import resolution)
  src/main/resources/tr/girgin/checkstyle/messages.properties   the messages
  src/test/java/tr/girgin/checkstyle/    one test class per check, and the harness and project-wide tests
  src/test/resources/checks/<Check>/     fixtures of one check
  src/test/resources/rules/<TAP-id>/     fixtures of one project rule
```

- The package is `tr.girgin.checkstyle`: the checks are tooling, not backend code, so the backend's
  `PackageName` rule does not apply to them.
- The module is an **own build**, not a project of `build-logic`: `build-logic` is included for plugins
  only, and its projects cannot be used as a library. The root `settings.gradle.kts` includes it with
  `includeBuild("build-logic/checkstyle-rules")`; Gradle builds the jar from source when needed, nothing
  is published.
- The convention plugin `tradinganalysisplatform.java-library` puts the jar on the `checkstyle`
  configuration, so `checkstyleMain` and `checkstyleTest` of every backend module can load the checks.
  `checkstyle.xml` names a check by its full class name:

  ```xml
  <module name="tr.girgin.checkstyle.ForbiddenMemberAccessCheck">
      <property name="id" value="TAP-L1"/>
      <property name="members" value="System#out, System#err, *#printStackTrace(0)"/>
      <property name="suggestion" value="Use an SLF4J logger: log.info(...)."/>
  </module>
  ```
- The module compiles against the Checkstyle API of the version in `gradle/libs.versions.toml` (the same
  that runs the checks), so a Checkstyle upgrade rebuilds and retests the checks.
- `./gradlew build` (so `mise run build` and CI) and `mise run check` run the module's tests, its
  coverage gate and its formatting (`checkstyleRulesCheck`). The pre-commit hook runs
  `checkstyleRulesCheck`, and Checkstyle on the backend, when files of the module are staged.
- The module is formatted by Spotless with Palantir Java Format like the backend
  ([formatting](../../docs/coding-convention/backend-java-formatting.md); `mise run format` covers it). Checkstyle itself does not
  check it: its configuration refers to these checks, which would be a cycle. Its quality gate is its
  tests and the coverage rule.

## The contract of a check

- **No project knowledge in code.** Names of types, annotations, methods, messages and scopes are
  parameters. The project rules live only in `checkstyle.xml`.
- **Parameters** are bean properties (`setX(...)`) with documented defaults, comma-separated lists where
  several values make sense (`setX(String...)`), and a `suggestion` that is appended to the message, so
  each instance says how to fix its finding. An invalid value fails the configuration with an
  `IllegalArgumentException` that names it.
- **Messages** are in `messages.properties`, one key per kind of finding. A finding reads
  `<id>: <what is wrong>. <suggestion>`; the id is the module's `id` (`TAP-L1`).
- **Names without type resolution.** Checkstyle sees source text only. A parameter that names a type
  accepts a simple or a fully qualified name; a qualified name is matched through the file's imports
  (and `java.lang`, and the file's package), so `org.springframework...Value` does not match an
  unrelated `Value`. The helpers in `support/` do this once.
- A check registers only the tokens it needs and keeps no state across files beyond what `beginTree`
  resets.

## Tests

- **Per check**: a test class with fixtures in `src/test/resources/checks/<Check>/`. A fixture marks
  every line that must be reported with a trailing `// violation` comment, and the test asserts that the
  reported lines are exactly the marked ones, so a missing and an extra finding both fail. Cases: every
  parameter and every form of a value, near misses that must not be reported, invalid values, and the
  message with and without `suggestion`. `CheckstyleRunner` is the small harness (Checkstyle's own test
  support is not published as a library).
- **100% coverage**: the build fails (`jacocoTestCoverageVerification`) when a line, a branch or an
  instruction of the module's main code is not covered. Code that cannot be reached is removed, not
  excluded.
- **`ProjectRulesTest`** loads the real `config/checkstyle/checkstyle.xml` and, for every `TAP-*` id
  configured there, runs the rule on `rules/<id>/Violation.java` (every line marked `// violation` is
  reported, with that id, and no other) and on every other file in the folder (`Compliant.java`, a
  suppression fixture, ...: nothing is reported). A rule without fixtures fails the test, so a new rule
  cannot be added without proof that it works and that it does not report compliant code; this replaces
  the manual "show that it fails on a deliberate violation" step for project rules. The optional file
  `rules/<id>/sources` holds `main`, `test` or `both` (the default): the source sets the rule applies to,
  as the config scopes some rules by path.
- **`DocumentationTest`**: every check has a reference document, the index above links it, and the
  document's parameter table lists exactly the check's public setters; every project rule has a row in
  the rules catalogue.

## Adding a check

1. Write `<What>Check extends AbstractCheck` in `tr.girgin.checkstyle`, following the contract above.
   Put helpers that more than one check needs into `support/`.
2. Write its test class and fixtures; reach 100% coverage.
3. Write its reference document from the [template](docs/_template.md) and add
   a row to the table above.
4. Configure it as a project rule (below).

## Adding a project rule

1. Pick the next id of its group (`TAP-<group><n>`, see the
   [id scheme](../../docs/coding-convention/backend-java-checkstyle.md#project-rules)). Use a built-in Checkstyle check
   (`IllegalImport`, `TodoComment`, `RegexpSingleline`, ...) when one fits; otherwise configure a custom
   check, or add one.
2. Add the module to the end of `TreeWalker` in `checkstyle.xml`, with the `id`, its parameters and a
   `suggestion`.
3. Add `rules/<id>/Violation.java` and `Compliant.java` (and a suppression fixture) to the module's
   test resources.
4. Add the row to the rules catalogue, and fix the existing findings in the same pull request: a rule
   goes in at `error`.

## In IntelliJ

The CheckStyle-IDEA plugin shows the project rules when it can load the checks: build the jar with
`./gradlew -p build-logic/checkstyle-rules jar` and add
`build-logic/checkstyle-rules/build/libs/checkstyle-rules.jar` under *Settings | Tools | Checkstyle |
Third-Party Checks*. Rebuild the jar after a check changes.
