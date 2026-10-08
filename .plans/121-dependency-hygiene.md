# Plan: Dependency hygiene in all three parts

- **Issue**: #121 (Dependency hygiene: fail on unused and undeclared dependencies (Gradle, Vue, Python))
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every part of the repository follows one rule: declare what you use, use what you declare. The build
fails when a Java module uses a library it only gets transitively or declares one it does not use
(gradle-dependency-analyze), when the frontend's `package.json` lists an unused package or misses a
used one (Knip), and when ta-runner's `pyproject.toml` does the same (deptry). CI runs all three, and
they pass on `main`. Every exception is configured where the check is, with its reason. No runtime
behaviour changes: the jar, the UI and ta-runner work as before.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend (Gradle build) | `backend-java-package-structure.md` (modules, what depends on what); `backend-java-persistence.md` (JPA / Hibernate only, the shared persistence library and its test fixtures); `backend-java-checkstyle.md` and the "Suppressions in place" rule: an exception sits in the build file it applies to, with a reason. Versions only in `gradle/libs.versions.toml`, shared build setup only in the `build-logic` convention plugins |
| Frontend | `frontend-folder-structure.md`; exact versions in `package.json`; pnpm's 14-day `minimumReleaseAge` (`pnpm-workspace.yaml`); Gradle tasks in `artifact/frontend/build.gradle.kts` next to `pnpmLint` / `pnpmTest` |
| ta-runner | `ta-runner-python-package-structure.md` (only `engine` imports LangChain, only `engine/compat.py` imports TradingAgents); dev tools in `[dependency-groups] dev`; uv's 14-day `exclude-newer` |
| CI, mise, docs | `docs/coding-convention/README.md` ("Changing a convention": document, check and code change together; show that the check fails on a deliberate mistake); `repository-git-hooks.md` (pre-commit only, no slow checks there); `repository-versioning-and-releases.md` |

## Design

### Java: `ca.cutterslade.analyze` 2.0.0

A spike on `main` (Gradle 9.7.1, the `build-logic` included build) showed that the plugin works there
with two adjustments, and found the findings listed below. The repository does not use the
configuration cache (`gradle.properties`, CI), and the plugin's tasks are not compatible with it
(they hold a `Project`). That is not a blocker today: see "Out of scope".

- **Version catalog**: version `dependencyAnalyze = "2.0.0"`, a library
  `dependency-analyze-gradle-plugin` (`ca.cutterslade.analyze:ca.cutterslade.analyze.gradle.plugin`) for
  `build-logic/build.gradle.kts`, and a plugin `dependency-analyze` (`ca.cutterslade.analyze`) for the
  root build. Renovate's gradle manager picks up both.
- **Root project**: the plugin refuses to run unless it is also applied to the root project
  ("Dependency analysis plugin must also be applied to the root project"). Apply it in the root
  `build.gradle.kts` with `alias(libs.plugins.dependency.analyze)`, with a comment explaining why.
- **Convention plugin** `tradinganalysisplatform.java-library`: apply `id("ca.cutterslade.analyze")`,
  so every backend module gets `analyzeClassesDependencies`, `analyzeTestClassesDependencies` (and
  `analyzeTestFixturesClassesDependencies` in `library:persistence`) and `analyzeDependencies`, which
  the plugin wires into `check`. Configure `dependencyAnalysis` (or the 2.0.0 extension's name):
  `warnUsedUndeclared`, `warnUnusedDeclared`, `warnSuperfluous` off (fail), `justWarn` off,
  `logDependencyInformationToFiles` on.
- **BOM for the plugin's helper configuration**: the plugin copies `api` declarations into its own
  `apiHelper` configuration, which does not see the BOM on `implementation`, so the `api` starters of
  `bff:api` resolve without a version. Add the Spring Boot BOM to the helper configuration in the
  convention plugin (`configurations.matching { it.name == "apiHelper" }`), with a comment.
- **Not the custom Checkstyle checks**: `build-logic/checkstyle-rules` is its own build and does not
  use the convention plugin. It stays out of scope for this pull request.
- **Check the task graph**: in the spike, `analyzeDependencies` also ran `:backend:bootTestRun` (and
  failed there). Find what pulls it in (most likely the plugin resolving the Spring Boot plugin's
  `testAndDevelopmentOnly` / `developmentOnly` configurations) and make sure that
  `./gradlew analyzeDependencies` runs only compilation and the analysis.

**Findings on `main`, and how each one is fixed.** The rule: fix the declaration, and allow an
exception only where the bytecode cannot show the use. Every catalog entry added gets no version (the
BOM manages it), except `docker-java-api` / `docker-java-transport`, which take `version.ref =
"dockerJava"`.

| Module | Used, undeclared | Declared, unused | Fix |
|---|---|---|---|
| `bff:api` | `spring-web`, `spring-webmvc`, `spring-context`, `spring-core`, `jakarta.validation-api` | `spring-boot-starter-webmvc`, `spring-boot-starter-validation` | Library modules declare libraries, not starters: `api(...)` the five libraries. The two starters move to `:backend` as `runtimeOnly`, where the application gets its auto-configuration (Tomcat, Jackson, Hibernate Validator) as before |
| `bff:impl` | `spring-beans`, `spring-context`, `spring-web`, `spring-webmvc`, `jackson-core` | | Declare them (`implementation`) |
| `orchestration`, `domain:analysis:core`, `domain:report:core` | `spring-beans` | | Declare it |
| `domain:catalog:core` | `spring-beans` | `slf4j-api` | Declare `spring-beans`, remove `slf4j-api` |
| `domain:identity:core` | | `slf4j-api`, `spring-context` | Remove both |
| `domain:settings:core` | | `slf4j-api` | Remove it |
| `domain:analysis:adapter` | `docker-java-api`, `docker-java-transport`, `spring-data-commons`, `spring-beans`, `spring-core`, `spring-tx`, `jackson-core` | | Declare them. `docker-java-transport-httpclient5` stays if the code uses it, otherwise it becomes `runtimeOnly` (the analysis says which) |
| `domain:catalog:adapter` | `docker-java-api`, `docker-java-transport`, `spring-beans`, `spring-core`, `jackson-core` | `slf4j-api` | Declare them, remove `slf4j-api` |
| `domain:identity:adapter` | `spring-data-commons`, `spring-beans`, `spring-tx` | `slf4j-api` | Declare them, remove `slf4j-api` |
| `domain:settings:adapter` | `spring-data-commons`, `spring-beans`, `spring-tx` | `hibernate-core`, `slf4j-api` | Declare them, remove `hibernate-core` (no `@JdbcTypeCode` in this module) and `slf4j-api` |
| `domain:report:adapter` | `spring-beans`, `jackson-core` | `mapstruct` | Declare them. `mapstruct` comes from the `tradinganalysisplatform.mapstruct` plugin: if the module has no mapper, switch it to `tradinganalysisplatform.java-library`; if it has one, find out why the plugin does not see it before allowing anything |
| `library:persistence` (test fixtures) | `spring-boot-autoconfigure`, `spring-boot-data-jpa`, `spring-boot-hibernate`, `spring-boot-transaction`, `spring-test` | `spring-boot-starter-data-jpa`, `spring-boot-test`, `flyway-database-postgresql` | Declare the libraries as `testFixturesApi`. The starter and the runtime-only pieces the adapter tests need (`flyway-database-postgresql`, `postgresql`, and what the starter brought in at runtime, such as Hikari) become `testFixturesRuntimeOnly`, which still reaches the adapters' test runtime classpath. `spring-boot-test` goes if the fixtures do not use it |
| `library:persistence`, `library:mapper` (tests) | mapper: `assertj-core`, `junit-jupiter-api` | `spring-boot-starter-test` | See the test starter below |

The run stopped at the first failing task per module and the `:backend` module itself reported
nothing, so after the fixes run `./gradlew analyzeDependencies --continue` again until it is green:
more findings can show up.

**The test starter.** The convention plugin gives every module `spring-boot-starter-test`
(`testImplementation`) and `junit-platform-launcher` (`testRuntimeOnly`, not analysed). Keep them
there, and declare `spring-boot-starter-test` an aggregator for the test classes through the
plugin's aggregator support (check the exact 2.0.0 API in its README: the `permitAggregatorUse`
setting or configuration), so a test may use AssertJ, JUnit, Mockito and Spring Test through it,
and a module without tests does not fail. Only the test starter is treated this way. Main code
declares its libraries.

**Allowed exceptions** sit in the module's or the convention plugin's build file, each with a
reason comment. Expected: none in the module build files after the fixes above. Every exception
added during the implementation is listed in the pull request description.

### Frontend: Knip

- `knip` as a devDependency in `artifact/frontend/package.json` (exact version, a release at least 14
  days old so pnpm's `minimumReleaseAge` accepts it), a `knip.json` in `artifact/frontend/` with the
  entry `src/main.ts` and the project `src/**/*.{ts,vue}`, and the script
  `"deps:check": "knip --dependencies"`. Knip's Vite, Vitest, ESLint and TypeScript plugins find the
  config files' imports on their own.
- Expected false positives, each in `knip.json`'s `ignoreDependencies` (or the matching option) with a
  comment saying why (JSON with comments: use `knip.jsonc` if the comments need it):
  `@typescript/native` and the `typescript` alias (binaries, used by `vue-tsc` / `tsc`), and
  `openapi-typescript` if Knip does not see the `api:types` script. `@types/*` that Knip reports as
  unused: check whether the types are really used. Remove a package Knip reports as unused only after
  checking it is not used.
- Gradle: `pnpmDepsCheck` in `artifact/frontend/build.gradle.kts`, built like `pnpmLint` (inputs:
  `sources` plus `knip.json`), and `tasks.check { dependsOn(pnpmLint, pnpmTest, pnpmDepsCheck) }`.
  So `mise run build` and CI's *Backend and frontend* job run it.

### ta-runner: deptry

- `deptry` in `[dependency-groups] dev` (`>=` like the other tools), `uv lock`.
- `[tool.deptry]` in `pyproject.toml`: `known_first_party = ["ta_runner"]`, the default scan of
  `ta_runner/` and `tests/`. Confirm that the PEP 735 `dev` group counts as development dependencies
  (DEP004 for a dev package imported from `ta_runner/`) and that `tradingagents` (direct URL) maps to
  the `tradingagents` module.
- Known finding: `ta_runner/engine/callbacks.py` and `tests/engine/test_callbacks_cost.py` import
  `langchain_core`, which comes only through `tradingagents` (DEP003). Fix: declare `langchain-core`
  in `[project.dependencies]` without a version range, with a comment that TradingAgents decides its
  version and `uv.lock` pins it. The import contract "Only engine imports LangChain" stays as it is.
- `mise run runner-test` gets `uv run deptry .`, so CI's *ta-runner* job runs it with no workflow
  change.

### Local runs and CI

- `mise run check` also runs the three: `analyzeClassesDependencies analyzeTestClassesDependencies`
  in its Gradle call (plus the persistence module's test fixtures task through `analyzeDependencies`,
  so simply `analyzeDependencies`), `:frontend:pnpmDepsCheck`, and `uv run deptry .` next to ruff and
  `lint-imports`. Update its description.
- CI: no new job. The *Backend and frontend* job's failure upload also takes
  `artifact/**/build/reports/dependency-analysis/` (the files `logDependencyInformationToFiles`
  writes). Check the real report path in the build output.
- No Git hook: the analysis needs the whole backend compiled, too slow for pre-commit, and the hooks
  document allows only pre-commit.

## Work packages

WP2 and WP3 do not depend on WP1. WP4 needs all three.

### WP1: Java dependency analysis

- **Depends on**: none
- **Files**: `gradle/libs.versions.toml`, `build-logic/build.gradle.kts`,
  `build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts`, root
  `build.gradle.kts`, every `artifact/backend/**/build.gradle.kts` with a finding, possibly
  `artifact/backend/domain/report/adapter/build.gradle.kts` switching its plugin
- **Steps**:
  - [ ] Catalog entries, plugin in `build-logic` and on the root, applied in the convention plugin, configured to fail, report files on
  - [ ] BOM on the `apiHelper` configuration
  - [ ] Find and remove what makes the analysis run `bootTestRun`
  - [ ] Test starter as an aggregator for test classes
  - [ ] Fix the findings module by module (table above), rerun with `--continue` until green
  - [ ] `./gradlew build` green; the jar starts, `/actuator/info` answers, and the UI loads
- **Tests**: the existing backend tests (ArchUnit, adapter tests on PostgreSQL, Spring Boot tests
  `TradingPlatformApplicationTests`, `AnalysesApiIntegrationTest`) prove that the moved starters and
  test-fixture dependencies change nothing at runtime. The e2e job proves the jar still serves the
  UI. Proof that the check fails: temporarily remove `spring-beans` from one module and add an unused
  library to another. Show both failures in the pull request description, then revert.

### WP2: Frontend Knip check

- **Depends on**: none
- **Files**: `artifact/frontend/package.json`, `artifact/frontend/pnpm-lock.yaml`,
  `artifact/frontend/knip.json` (new), `artifact/frontend/build.gradle.kts`,
  `artifact/frontend/.prettierignore` only if Knip's file needs it
- **Steps**:
  - [ ] Add Knip, its config and the `deps:check` script
  - [ ] Fix or justify every finding
  - [ ] `pnpmDepsCheck` Gradle task, wired into `check`
- **Tests**: `./gradlew :frontend:check` green. Proof that it fails: an unused package added to
  `package.json` and an import of a transitive-only package, both shown in the pull request
  description and reverted.

### WP3: ta-runner deptry check

- **Depends on**: none
- **Files**: `artifact/ta-runner/pyproject.toml`, `artifact/ta-runner/uv.lock`, `mise.toml`
  (`runner-test`)
- **Steps**:
  - [ ] Add deptry to the dev group and `[tool.deptry]`
  - [ ] Declare `langchain-core`, fix or justify the other findings, with a comment for every ignore
  - [ ] `uv run deptry .` in `runner-test`
- **Tests**: `mise run runner-test` green, upstream contract tests included. The ta-runner image
  still builds (CI's images job). Proof that it fails: an unused package in `[project.dependencies]`
  and an import from `ta_runner/` of a dev-only package, shown and reverted.

### WP4: mise, CI, convention doc, README, version

- **Depends on**: WP1, WP2, WP3
- **Files**: `mise.toml` (`check`), `.github/workflows/ci.yml` (report upload, the jobs comment at
  the top), `docs/coding-convention/repository-dependency-hygiene.md` (new),
  `docs/coding-convention/README.md`, `README.md`, `gradle.properties`,
  `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py` (version bump)
- **Steps**:
  - [ ] `mise run check` runs the three checks; description updated
  - [ ] CI uploads the dependency-analysis reports on failure
  - [ ] The convention document and the README changes (below)
  - [ ] `mise run version:bump minor`
- **Tests**: `mise run check` green; CI green on the pull request.

## Tests

No new test classes. The proof is the existing suites staying green while the dependency
declarations change (backend unit, adapter and Spring Boot tests, frontend Vitest, ta-runner pytest,
e2e, the Compose smoke test), plus the three checks themselves:
`./gradlew build` (with `analyzeDependencies` and `:frontend:pnpmDepsCheck` in `check`) and
`mise run runner-test` (with deptry) are green on the branch, and each one is shown to fail on a
deliberate mistake (WP1–WP3), as `docs/coding-convention/README.md` requires for a new rule.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/coding-convention/repository-dependency-hygiene.md` (new) | The rule (declare what you use, use what you declare), one tool per part and what it checks (used-undeclared, unused-declared; Knip's unused / unlisted packages; deptry's DEP001–DEP004), how to run each locally (`./gradlew analyzeDependencies`, `pnpm run deps:check`, `uv run deptry .`, all in `mise run check`), how to allow an exception (where it goes, always with a reason), the decisions: library modules declare libraries and the application module the starters; the test starter is the one aggregator; why gradle-dependency-analyze and not dependency-analysis-gradle-plugin (the one named in the issue, works on Gradle 9.7.1; not compatible with the configuration cache, which the repository does not use; revisit when it does); no Git hook (too slow for pre-commit) |
| Text | `docs/coding-convention/README.md` | A row in the documents table, three rows in "How the conventions are enforced" (backend, frontend, ta-runner), the `mise run check` sentence |
| Text | `README.md`, "Tasks" | `mise run build`, `runner-test` and `check` lines mention the dependency checks |
| Text | `README.md`, "Continuous integration" | *Backend and frontend* (dependency analysis, Knip) and *ta-runner* (deptry) rows |
| Text | `README.md`, "Coding conventions" | One sentence: the dependency declarations are checked too, link to the new document |
| Screenshot | none | No page changes |

## Out of scope

- **The configuration cache**: the repository does not enable it, so the plugin's incompatibility
  does not matter today. If it is turned on later, the dependency analysis is the thing to
  revisit (dependency-analysis-gradle-plugin is the alternative). The convention document says so.
- **`build-logic/checkstyle-rules`**: a separate build without the convention plugin; a follow-up
  issue if wanted.
- **Knip for `e2e/package.json`**, and Knip's unused files and exports: follow-up issues if wanted.
- **A pre-push hook**: the hooks document has only pre-commit; adding a hook kind is its own decision.
- **Renovate configuration**: no change. The gradle (version catalog), npm and pep621 (uv) managers
  already cover the new entries; check the dependency dashboard after the merge.
