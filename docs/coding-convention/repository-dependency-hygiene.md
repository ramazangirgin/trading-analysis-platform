# Repository: dependency hygiene

One rule for every part of the repository: **declare what you use, use what you declare.** A build
file lists the libraries its code imports, directly, and nothing else. A library that only arrives
through another one (a transitive dependency) can disappear or change with that other library's
next release, and a declaration nobody uses is dead weight that hides what a module really needs.
The build fails on both.

| Part | Tool | Used but not declared | Declared but not used |
|---|---|---|---|
| Backend (Gradle) | [gradle-dependency-analyze](https://github.com/gradle-dependency-analyze/gradle-dependency-analyze) (`ca.cutterslade.analyze` 2.0.0) | `analyzeClassesDependencies`, `analyzeTestClassesDependencies`, `analyzeTestFixturesClassesDependencies` | the same tasks |
| Frontend | [Knip](https://knip.dev/) (`knip --dependencies`) | unlisted packages and binaries | unused dependencies and devDependencies |
| ta-runner | [deptry](https://deptry.com/) | DEP003 (transitive dependency imported), DEP001 (imported, not installed), DEP004 (a dev dependency imported from the package) | DEP002 (declared, not imported) |

## Running them

```sh
./gradlew --console=plain analyzeDependencies   # every backend module, main, test and test fixtures
./gradlew --console=plain :frontend:pnpmDepsCheck   # in artifact/frontend: pnpm run deps:check
cd artifact/ta-runner && uv run deptry .
```

All three are part of `mise run check`. `mise run build` runs the Gradle analysis and Knip (both are
in `check`), and `mise run runner-test` runs deptry, so CI's *Backend and frontend* and *ta-runner*
jobs run them. When the Gradle analysis fails in CI, the reports under
`build/reports/dependency-analyze/` of every module are uploaded with the test reports.

## Backend

- **Library modules declare libraries, the application declares starters.** A module such as
  `bff:api` declares `spring-web`, `spring-context` or `jakarta.validation-api`, never
  `spring-boot-starter-*`. The starters (`spring-boot-starter-webmvc`, `-validation`) are
  `runtimeOnly` in `:backend`, where the application gets its auto-configuration. Versions come from
  the Spring Boot BOM, which the convention plugin imports; only `docker-java-*` have a version in
  `gradle/libs.versions.toml`.
- **The test starter is the one aggregator.** The convention plugin gives every module
  `spring-boot-starter-test`, and its classes (AssertJ, JUnit, Mockito, Spring Test) count as used
  through it (`permitTestAggregatorUse`). A module without tests is not reported for it. Main code
  declares every library it imports.
- **Test fixtures** (`library:persistence`) declare the libraries they use as `testFixturesApi`, and
  what only has to be on the adapters' test runtime classpath (starter, Flyway's PostgreSQL module,
  the driver) as `testFixturesRuntimeOnly`.
- The plugin only runs compilation and the analysis: the convention plugin cuts its dependencies on
  the other tasks of a module (`bootTestRun` would need a database).
- **Out of scope:** `build-logic/checkstyle-rules` is a build of its own without the convention
  plugin.

## Exceptions

An exception goes where the check is configured, always with a comment saying why. The bytecode
cannot show every use (a constant inlined by the compiler, a library loaded by reflection or only by
the framework), so an exception is for those cases; for everything else, fix the declaration.

| Part | Where | How |
|---|---|---|
| Backend | the module's `build.gradle.kts` (or the convention plugin, for all modules) | `permitUnusedDeclared(...)`, `permitUsedUndeclared(...)` and the test variants (`permitTestUnusedDeclared`, ...) in `dependencies { }` |
| Frontend | `artifact/frontend/knip.jsonc` | `ignoreDependencies`, `ignoreBinaries`. JSON with comments |
| ta-runner | `[tool.deptry]` in `artifact/ta-runner/pyproject.toml` | `per_rule_ignores`, `package_module_name_map` |

At the time of writing there are none in the module build files. The exceptions that exist: in the
convention plugin, the test starter as the aggregator for test classes
(`permitTestAggregatorUse`) and permitted as unused in modules without tests
(`permitTestUnusedDeclared`); in `knip.jsonc`, `@types/markdown-it`, which TypeScript picks up
without an import.

## Decisions

- **gradle-dependency-analyze, not dependency-analysis-gradle-plugin.** Issue #121 names the latter;
  the former works on Gradle 9.7.1 here and fails the build with plain configuration. Its tasks are
  not compatible with Gradle's configuration cache, which the repository does not use. If the
  configuration cache is turned on, revisit this: dependency-analysis-gradle-plugin is the
  alternative.
- **TradingAgents decides `langchain-core`'s version.** ta-runner declares it without a range
  (`engine/callbacks.py` imports it) and `uv.lock` pins what TradingAgents resolves to. The import
  contract "only `engine` imports LangChain" stays as it is
  ([ta-runner-python-package-structure.md](ta-runner-python-package-structure.md)).
- **No Git hook.** The Gradle analysis needs the whole backend compiled, which is too slow for
  pre-commit, and [the hooks document](repository-git-hooks.md) has only a pre-commit hook. CI and
  `mise run check` are where it runs.

## Changing it

As for every convention ([README](README.md#changing-a-convention)): show that the check fails on a
deliberate mistake. For each part: remove a used library from a module's `build.gradle.kts` and add
an unused one; add an unused package to `package.json` and import a package that is only
transitive; add an unused package to `[project.dependencies]` and import a dev-only package from
`ta_runner/`.
