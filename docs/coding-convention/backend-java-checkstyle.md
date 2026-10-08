# Backend: Checkstyle

Static checks on the backend's Java sources (`artifact/backend`, every module, main and test):
naming, imports, size and complexity, bug-prone patterns, design, modifiers and a small set of
Javadoc rules. They come from [Checkstyle](https://checkstyle.org/)'s built-in checks, configured in
[`config/checkstyle/checkstyle.xml`](../../config/checkstyle/checkstyle.xml).

Checkstyle does not check layout (indentation, brace placement, import order, whitespace inside a
line). The formatter does that ([Spotless with Palantir Java Format](backend-java-formatting.md)),
so the two tools never report conflicting rules.
The project's own rules (#55) are generic [custom checks](../../build-logic/checkstyle-rules/README.md),
configured in the same config as [project rules](#project-rules).

## Where it runs

| Where | Command |
|---|---|
| Build | `checkstyleMain` and `checkstyleTest` in every backend module, part of `./gradlew build` / `mise run build`. Any finding fails the build (`maxWarnings = 0`) |
| CI | *Backend and frontend* runs `mise run build`. When the job fails, the reports (`artifact/**/build/reports/checkstyle/`) are uploaded with the test reports |
| By hand | `mise run check`, or `./gradlew checkstyleMain checkstyleTest` |
| Git hook | pre-commit, when `.java` files under `artifact/backend/` are staged; it shares one Gradle run with ArchUnit |

The setup lives in the convention plugin
[`tradinganalysisplatform.java-library`](../../build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts),
so a new module gets it. The tool version is `checkstyle` in
[`gradle/libs.versions.toml`](../../gradle/libs.versions.toml). Generated sources (`build/generated/`,
MapStruct `*Impl`) are not checked. `.properties` files are checked for duplicate keys.

In IntelliJ, the CheckStyle-IDEA plugin can use `config/checkstyle/checkstyle.xml`, so the IDE shows
the same findings as the build.

## Rules

### Imports
- No wildcard imports. Static imports are allowed only for test DSLs: AssertJ, JUnit `Assertions` /
  `Assumptions`, Mockito, MockMvc request builders and result matchers, and ArchUnit's rule
  definitions and predicates.
- No unused or redundant imports.
- Not allowed: `sun.*`, `com.sun.*`, `jdk.internal.*`, JUnit 4 (`org.junit.Assert`, `org.junit.Test`,
  `junit.framework`, …), commons-lang 2 (`org.apache.commons.lang`), and `javax.annotation.Nullable` /
  `Nonnull` (nullness annotations come from JSpecify, #11).
- Not allowed, in main and test code: `org.springframework.jdbc` (`JdbcClient`, `JdbcTemplate`, …).
  Persistence goes through Spring Data JPA ([persistence](backend-java-persistence.md)). `spring-jdbc`
  stays on the classpath, because Spring Data JPA's `spring-orm` is built on it, so the compiler would
  not stop an import. `PersistenceArchitectureTest` bans it in main code; ArchUnit does not import test code, so
  this check covers it there.

### Naming
- Defaults (camelCase, PascalCase) for types, methods, fields, parameters, local, lambda, catch and
  pattern variables, and record components.
- Constants (`static final`) are `UPPER_SNAKE`. A logger may be called `log` or `LOG`.
- Packages are lower case, without underscores, under `tr.girgin.backend`.
- At most two capital letters in a row: `HttpClient`, `JsonRow`, not `HTTPClient`, `JSONRow`.
  `static` and `final` variables are exempt.
- Type parameters are a single capital letter, optionally followed by a digit (`T`, `K`, `V2`).

### Size and complexity

| Check | Limit |
|---|---|
| `LineLength` | 120 characters; `package` / `import` lines and lines with a URL are exempt |
| `FileLength` | 600 lines |
| `MethodLength` | 80 lines, not counting empty lines and comments |
| `ParameterNumber` | 7; `@Override` methods are exempt |
| `CyclomaticComplexity` | 12; a `switch` counts as one decision |
| `NPathComplexity` | 200 |
| `ClassFanOutComplexity` | 25 classes; common `java.*`, Spring annotation and MapStruct packages are not counted |
| `NestedIfDepth` / `NestedTryDepth` / `NestedForDepth` | 3 / 1 / 2 |
| `ReturnCount` | 4 per method or constructor; lambdas and `equals` are exempt |
| `ExecutableStatementCount` | 40 per method |

### Bug-prone patterns
`EmptyCatchBlock` (allowed when the variable is named `ignored`, `expected` or `_`), `EmptyBlock`
(an empty block needs a comment), `EmptyStatement`, `EqualsHashCode`, `CovariantEquals`,
`StringLiteralEquality`, `SimplifyBooleanExpression`, `SimplifyBooleanReturn`, `MissingSwitchDefault`,
`FallThrough`, `DefaultComesLast`, `ModifiedControlVariable`, `InnerAssignment`,
`OneStatementPerLine`, `MultipleVariableDeclarations`, `DeclarationOrder`,
`OverloadMethodsDeclarationOrder`, `HiddenField` (constructor and setter parameters are exempt),
`IllegalCatch` (no `catch (Exception | RuntimeException | Throwable | Error)`), `IllegalThrows`,
`IllegalInstantiation` (no `new Integer(…)`, `new Boolean(…)` and similar boxed types),
`IllegalType` (no `HashMap`, `ArrayList`, … in signatures; a local variable may name what it
creates), `MagicNumber` (main sources only; `-1, 0, 1, 2, 100, 1000`, `hashCode` and annotations are
exempt), `NoFinalizer`, `NoClone`, `SuperClone`, `UnnecessaryParentheses`, unnecessary semicolons,
`UnusedLocalVariable`, `UnusedPrivateField`, `ArrayTypeStyle`, `UpperEll`,
`AvoidDoubleBraceInitialization`, `AvoidNoArgumentSuperConstructorCall`, `PatternVariableAssignment`.

Unused catch parameters, lambda parameters and try-with-resources variables are unnamed (`_`,
Java 22+): `UnusedCatchParameterShouldBeUnnamed`, `UnusedLambdaParameterShouldBeUnnamed`,
`UnusedTryResourceShouldBeUnnamed`.

### Design
`FinalClass`, `HideUtilityClassConstructor`, `InterfaceIsType` (no constant-only interfaces),
`OneTopLevelClass`, `OuterTypeFilename`, `InnerTypeLast`, `MutableException` (exception fields are
`final`), `ThrowsCount` (2), `VisibilityModifier` (fields are private, `protected` included; record
components are not fields).

`SealedShouldHavePermitsList` is not enabled. A sealed type whose subtypes are in the same file may
leave out `permits`, and the compiler already checks that list.

### Modifiers and blocks
`ModifierOrder`, `RedundantModifier` (e.g. `public` on an interface method), `NeedBraces`,
`AvoidNestedBlocks` (allowed in a `case`). Brace placement (`LeftCurly` / `RightCurly`) is left to
the formatter.

### Javadoc
The set is kept small:
- Public types in `core/inbound` and `core/outbound` (use cases and ports, the domain's contracts)
  need a Javadoc comment (`MissingJavadocType`). Adapters, mappers and services do not.
- A Javadoc comment that is present has to be well formed: the first sentence ends with a period
  (`SummaryJavadoc`); the comment sits right before a declaration (`InvalidJavadocPosition`);
  `@throws` matches what the method throws (`JavadocMethod`; `@param` and `@return` are optional);
  block tags have a description and are in the standard order; a one-line comment has no block tags.
  Checkstyle 14 replaced `JavadocStyle` with `SummaryJavadoc` and similar checks.
- `JavadocPackage` (a `package-info.java` in every package) is not enabled. Only a module's root
  package has one, describing its role
  ([package structure](backend-java-package-structure.md)).

### Files and annotations
- Files end with a newline, have no tabs and no trailing whitespace. Spotless enforces the same in
  Java files; these rules stay because they also cover the `.properties` files, which Spotless does
  not format.
- `MissingOverride` (with `{@inheritDoc}`), `MissingDeprecated`, `AnnotationLocation`.

## Project rules

Rules that Checkstyle's built-in checks do not cover are the project's own. Each is a configured
instance of a generic [custom check](../../build-logic/checkstyle-rules/README.md) (or, when one fits, of a
built-in check such as `IllegalImport`, `TodoComment` or `RegexpSingleline`, linked to its
[checkstyle.org](https://checkstyle.org/checks.html) page instead of a reference document), at the end of
`TreeWalker` in `checkstyle.xml`. A finding starts with the rule's id and ends with its advice:
`TAP-L1: Field 'System#out' is not allowed. Use an SLF4J logger: log.info(...).`

**Ids** are `TAP-<group><n>`, with a group per topic (`L`, `D`, `T`, `C`, `J`, `S`, `X`; `L` is
logging). Ids are not reused.

**Suppressing** one is as for any check, with the id: `@SuppressWarnings("checkstyle:TAP-L1")` and a
reason on the same line or the line above (see [Suppressing a finding](#suppressing-a-finding)).

**A project rule goes in at `error`**, with its existing findings fixed in the same pull request: the
build fails on a warning too (`maxWarnings = 0`). Every rule is proved by fixtures in the custom checks
module (`rules/<id>/Violation.java`, `Compliant.java`) that run on every build, so the proof stays in
the repository.

### Rules catalogue

| Id | Rule | Sources | Check | Parameters | Reason | Bad / good |
|---|---|---|---|---|---|---|
| `TAP-L1` | No `System.out`, `System.err` and no `printStackTrace()` without arguments; use SLF4J | main and test | [`ForbiddenMemberAccessCheck`](../../build-logic/checkstyle-rules/docs/ForbiddenMemberAccessCheck.md) | `members = System#out, System#err, *#printStackTrace(0)` | Console output has no level, no logger name and no timestamp, and bypasses the log configuration; a stack trace belongs in the log with the message | `System.out.println("started");` / `log.info("started");` and `e.printStackTrace();` / `log.error("failed", e);` |

## Suppressing a finding

Fix the finding where you can. When the code is right as it is, suppress the finding where it
occurs, on the narrowest element (a method, constructor, field or nested class), and say why in a
comment on the same line or the line above:

```java
@SuppressWarnings("checkstyle:IllegalCatch") // a failing subscriber must not affect the run
private void emit(RunEvent event) {
```

```java
// ConstantName: ArchUnit reports a rule by its field name, so rule names are snake_case sentences.
@SuppressWarnings({"checkstyle:ConstantName", "checkstyle:DeclarationOrder"})
class ArchitectureTest {
```

The check's name comes after `checkstyle:` (the name in the report, without `Check`). There is no
central suppressions file, so each exception is visible where it applies and is reviewed with the
code around it. Two settings in `checkstyle.xml` limit where a check applies. They are not
suppressions:

- `MissingJavadocType` applies to `core/inbound` and `core/outbound` only (`SuppressionSingleFilter`).
- `MagicNumber` applies to main sources only: the build turns it off for test sources, where tests
  spell out the values they expect.

Suppressions in the code today:

| Check | Where | Why |
|---|---|---|
| `IllegalCatch` | runner loops, background threads, event delivery, health checks, Docker probes in tests | fault isolation: one failure must not stop a run, a thread or other subscribers |
| `ParameterNumber` | constructors of `AnalysesApiDelegateImpl`, `DockerRunnerAdapter`, `ImportExistingRunsService` | constructor injection of collaborators and `@Value` settings |
| `ClassFanOutComplexity` | `AnalysesApiDelegateImpl` | the analyses API's delegate, with a use case and a mapper per endpoint |
| `ConstantName`, `DeclarationOrder`, `HideUtilityClassConstructor` | `ArchitectureTest` | ArchUnit rule names, rules grouped by topic, JUnit instantiates the class |
| `HideUtilityClassConstructor` | `TradingPlatformApplication` | Spring instantiates the `@SpringBootApplication` class |
| `VisibilityModifier` | test doubles (`Fakes`, `RecordingSink`) | tests read and set their state directly |
| `LineLength` | test fixtures holding runner protocol lines | a protocol line stays on one line |

## Adding or changing a rule

Follow [Changing a convention](README.md#changing-a-convention): change this document, the config
and the code together, and show that the rule fails on a deliberate violation before merging. For a
[project rule](#project-rules) the fixtures in the custom checks module are that proof. A new check
goes in at `error` with its existing findings fixed in the same pull request: `maxWarnings = 0` makes a
warning fail the build as well, so a check cannot wait at warning level.
