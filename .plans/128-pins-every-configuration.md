# Plan: Security pins on every configuration, and a graph check in dependabot-fix

- **Issue**: #128 (Dependabot alerts from the resolved Gradle graph, and a post-merge check in dependabot-fix)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The seven open Jackson 3 alerts (#22, #26, #30, #37, #39, #44, #47) close after the merge, without
being dismissed, because no Gradle configuration resolves Jackson 3.1.5 any more. `/dependabot-fix`
checks a fix the way GitHub sees it (every configuration of every project, not only the runtime
classpath), and after the merge it checks that the alerts it targeted really closed, and says why
when one did not.

## Findings that change the issue

The issue assumed that GitHub builds the Gradle graph by reading the build files, and asked for a
dependency-submission job in CI. That is not the case:

- **Automatic Dependency Submission is already on** (workflow `dynamic/dependency-graph/auto-submission`,
  detector `actions/gradle-dependency-submission-action`). It runs Gradle on every push to `main`
  and submits the **resolved** graph of every configuration (only `detachedConfiguration*` excluded),
  under the manifest `settings.gradle.kts`. A CI job would submit the same graph a second time.
- The submission of `main` at `bbd51c0` (run 37848844280) lists `tools.jackson.core:jackson-core`,
  `jackson-databind` and `tools.jackson:jackson-bom` at 3.1.7 **and** at 3.1.5 (indirect, from Maven
  Central).
- The 3.1.5 comes from **`:backend:library:persistence`'s `testFixturesCompileClasspath`** (and its
  runtime counterpart). The convention plugin adds the Jackson 3 platform (and the Jackson 2
  platform, the Bouncy Castle and Tomcat constraints) to `implementation` only. The
  `java-test-fixtures` configurations do not extend `implementation`. That module gets Spring Boot's
  BOM on `testFixturesImplementation` by hand (`artifact/backend/library/persistence/build.gradle.kts`),
  and the BOM manages Jackson 3 at 3.1.5.
- #124 checked the fix with `runtimeClasspath` and `buildEnvironment` only, and so does the skill's
  step 2, so neither saw it.

So task 1 of the issue (a submission job in CI) is dropped. The fix is to make the pins reach every
configuration, and to make the skill check every configuration.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Backend build (Gradle) | Versions only in `gradle/libs.versions.toml`; module setup in the convention plugin `build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts`; dependency hygiene: `analyzeDependencies` stays green (docs/coding-convention/repository-dependency-hygiene.md) |
| Skill `dependabot-fix` (`.claude/skills/dependabot-fix/`) | Exact work in the helper `alerts.py` with tests in `test_alerts.py` (run by `mise run skill:test`, in `mise run check` and CI); `SKILL.md` describes the steps; ground rules unchanged (never dismiss, never merge) |
| Docs | README "Dependency updates"; English, plain style |

## Design

### Pins on every source set

In the convention plugin, apply the security pins to the `implementation` bucket of **every source
set** instead of `implementation` alone: `sourceSets.configureEach { ... }` with
`implementationConfigurationName`. That covers `main`, `test` and `testFixtures` (the
`java-test-fixtures` plugin registers its source set, so the callback also runs when the plugin is
applied later), and any source set added later. The pins themselves are unchanged: the Jackson 2 and
Jackson 3 platforms, and the Bouncy Castle and Tomcat constraints with their `because(...)`. Spring
Boot's BOM stays on `implementation` as today; the persistence module keeps its own
`testFixturesImplementation(platform(libs.spring.boot.bom))`.

Keep the "Security pins (Dependabot)" comment and say why it is per source set ("GitHub's dependency
submission records every configuration, test fixtures included").

### Helper: scan every configuration

New command in `alerts.py`:

`gradle-scan [--alerts <file>] [<group-id>...]`: runs `./gradlew -q <project>:dependencies` for the
root project and every project (`./gradlew -q projects`), and `buildEnvironment` for the root and
`-p build-logic`. It parses the trees per configuration and prints, per group, every
`(project, configuration, package, resolved version)` where the **resolved** version (the right side
of `->`, or the version itself when there is none) is inside a vulnerable range of the group's
alerts. The output is empty when nothing vulnerable resolves anywhere. The tree parsing is a pure
function (text in, records out), tested without Gradle.

### Helper: check the alerts after the merge

New command `after-merge <alert-number>...`:

- reads the alerts (`state`, `fixed_at`) and the latest *Automatic Dependency Submission* run on
  `main` (`gh run list --workflow "Automatic Dependency Submission" --branch main`): its head SHA,
  and whether it is newer than the merge commit;
- for every alert still open, the versions of its package in the dependency graph
  (`gh api repos/{owner}/{repo}/dependency-graph/sbom`) that are in its vulnerable range;
- prints per alert: `fixed`, `open: submission not run yet` (with the run's SHA), or
  `open: still in the graph` with the versions found.

The decision logic (alert, submission, SBOM packages → status) is a pure function, tested with
recorded JSON.

### Skill steps

- Step 2 (find where each group comes from), Maven: run `alerts.py gradle-scan` first; it lists every
  configuration that resolves a vulnerable version. Keep the `dependencyInsight` commands for the
  path.
- Step 6.3 (check it is gone): `alerts.py gradle-scan` must print nothing for the applied groups.
  A remaining entry is fixed before the commit.
- Step 7 (report) and a new last step after the merge: once the developer has merged, wait for the
  *Automatic Dependency Submission* run on `main`, then `alerts.py after-merge <alerts of the pull
  request>`, and report every alert that did not close, with the reason. Dependabot also needs a
  few minutes after the submission; `open: submission not run yet` means wait, not fix.

## Work packages

### WP1: Pins on every source set

- **Status**: done
- **Depends on**: none
- **Files**: `build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts`
- **Steps**:
  - [x] Move the Jackson 2 and 3 platforms and the Bouncy Castle and Tomcat constraints into a
    `sourceSets.configureEach` block on each source set's `implementationConfigurationName`
  - [x] Comment why (every configuration is submitted to GitHub, test fixtures included)
  - [x] `./gradlew -q :backend:library:persistence:dependencies` shows no `tools.jackson.core:*:3.1.5`
    without `-> 3.1.7` in any configuration
  - [x] `mise run check` and `mise run build` green (`analyzeDependencies` included)
- **Tests**: the build itself; the `gradle-scan` run of WP2 against the repository prints nothing
  for Jackson 2, Jackson 3, Bouncy Castle, Tomcat and commons-lang3 (paste the output in the pull
  request).

### WP2: Helper commands `gradle-scan` and `after-merge`

- **Status**: not done: Edit on .claude/skills/dependabot-fix/ was denied by the permission mode, so alerts.py and test_alerts.py were not changed
- **Depends on**: none
- **Files**: `.claude/skills/dependabot-fix/alerts.py`, `.claude/skills/dependabot-fix/test_alerts.py`
- **Steps**:
  - [ ] Parse `gradle dependencies` / `buildEnvironment` output into
    `(configuration, group:name, requested, resolved)` records (`->` replacements, `(c)`, `(*)`,
    `(n)` not resolved, `FAILED`)
  - [ ] `gradle-scan`: run the commands, match the records against the groups' vulnerable ranges
    (the existing `in_range`), print JSON
  - [ ] `after-merge`: alert state, latest submission run on `main`, SBOM versions in range, one status
    per alert
- **Tests**: `test_alerts.py`: the parser on a recorded tree with a replaced version, a constraint, an
  omitted repeat and a configuration that resolves nothing; a record in and out of range; the
  `after-merge` statuses (`fixed`, `submission not run yet`, `still in the graph`) from recorded JSON.

### WP3: The skill and the README

- **Status**: not done: Edit on .claude/skills/dependabot-fix/SKILL.md was denied by the permission mode, and WP2 (the commands it documents) is not done
- **Depends on**: WP2
- **Files**: `.claude/skills/dependabot-fix/SKILL.md`, `README.md`
- **Steps**:
  - [ ] SKILL.md: the two commands in the helper table; step 2 and 6.3 use `gradle-scan`; the
    after-merge check; the note that GitHub's graph is the resolved graph of every configuration,
    submitted by Automatic Dependency Submission on every push to `main`
  - [ ] README "Dependency updates": the alerts close once Automatic Dependency Submission has run on
    `main` after the merge; `/dependabot-fix` checks every configuration and reports alerts that stay
    open
- **Tests**: none (text).

### WP4: Version bump

- **Status**: done
- **Depends on**: WP1, WP2, WP3
- **Files**: `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [x] `mise run version:bump minor` in its own commit
- **Tests**: the *Version* CI job.

## Tests

- `mise run skill:test`: the new parser and status tests (WP2).
- `mise run check` and `mise run build`: the convention plugin change keeps every check green (WP1).
- `alerts.py gradle-scan` on the branch prints nothing for the groups pinned in #124 (WP1 + WP2).
- Done when, after the merge and the next *Automatic Dependency Submission* run on `main`, the
  submitted graph has no Jackson 3.1.5 and `alerts.py after-merge 22 26 30 37 39 44 47` reports all
  seven `fixed`. That is checked after the merge, in the pull request's hand-over.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Dependency updates" (the `dependabot-fix` paragraph) | Alerts close after Automatic Dependency Submission runs on `main`; the skill checks every configuration and reports alerts that stay open |
| Text | `.claude/skills/dependabot-fix/SKILL.md` | The new commands and steps (WP3) |
| Text | `gradle/libs.versions.toml` | None: the pins' comments and removal conditions stay right |
| Screenshot | none | No page changes |

## Out of scope

- A dependency-submission job in CI (task 1 of the issue): Automatic Dependency Submission already
  submits the resolved graph; a second one would add nothing.
- `kotlin-gradle-plugin` (#15) and `cryptography` (#1, #2, #3), as in the issue.
- Dismissing alerts.
