# Plan: dependabot-fix skill, and the fixes for the open alerts

- **Issue**: #124 (Claude Code skill: fix Dependabot alerts with grouped, mature, Renovate-compliant upgrades)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A developer runs `/dependabot-fix` (all open alerts), `/dependabot-fix maven` (one ecosystem) or
`/dependabot-fix 16 17 18` (alert numbers) and gets the open Dependabot alerts grouped by release
family, where each group comes from, the newest mature (at least 14 days old), stable version that
fixes every alert of the group, the Renovate rules that version touches, and, after approving the
table, a branch with one commit per group and a pull request listing the alerts it closes. The same
pull request applies that flow to today's 48 alerts: the Maven groups that have a mature stable fix
are pinned (Jackson 2, Jackson 3, Bouncy Castle, Tomcat, commons-lang3); the two that have none
(`kotlin-gradle-plugin`, Intel-Mac `cryptography`) are reported and left open.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Claude Code skill (`.claude/skills/dependabot-fix/`) | Shape of `.claude/skills/renovate-update/` (frontmatter `name` / `description` with trigger phrases, a helper for the exact parts, a command table, "Ground rules", numbered sections, `~/.local/bin/mise run <task>`). Never merge, approve or use auto-merge; never lower `minimumReleaseAge`; never pin a release younger than 14 days. Docs English only. |
| Helper script (Python) | Like `scripts/agent/*.py`: standard library only, run with `uv run --no-project python`, module docstring listing the commands, `unittest` tests in `test_*.py` next to it, no network in the tests. |
| Backend build (Gradle) | Versions only in `gradle/libs.versions.toml` (its header: "Bump here, nowhere else"), each with a comment saying why; the module setup in the convention plugin `build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts`; dependency hygiene (`analyzeDependencies` must stay green, docs/coding-convention/repository-dependency-hygiene.md). |
| CI, `mise.toml` | A new test task is part of `mise run check` and of the CI job that already runs `agent:test`; the README's CI table and task list follow. |
| Versioning | One `minor` bump with `mise run version:bump minor` in its own commit. |

## Design

### The helper: `.claude/skills/dependabot-fix/alerts.py`

Standard library only (`json`, `re`, `urllib.request`, `subprocess` for `gh`, `datetime`,
`xml.etree`). Every command prints JSON on stdout (errors to stderr, exit 1). The network calls sit
behind a small set of functions (`fetch_alerts`, `fetch_versions`) that the tests replace.

| Command | What it does |
|---|---|
| `alerts [--ecosystem <e>] [<number>...]` | Reads the open alerts (`gh api --paginate repos/{owner}/{repo}/dependabot/alerts?state=open&per_page=100`), keeps number, ecosystem, package, manifest, scope, severity, GHSA and CVE IDs, vulnerable range and first patched version, and groups them. Output: a list of groups, each with its id, ecosystem, family, packages, alerts, highest severity, and the **required minimum** per release line (the highest first patched version of the alerts on that line). |
| `versions <ecosystem> <package> [--today YYYY-MM-DD]` | The released versions with their release date, sorted by date, each flagged `prerelease`, `mature` (at least `minimumReleaseAge` days old) and `eligible_from` (release date + the age). |
| `candidates <group-id> [--alerts <file>] [--today ...]` | For one group (from `alerts`, or a saved `alerts` output): the versions that fix every alert of the group (in no vulnerable range of any of them), split into `suggested` (newest mature, stable, same major as the current line), `waiting` (fixes, not mature yet, with `eligible_from`), `needs_prerelease` and `needs_major` (only these fix it: report and ask, never apply). |
| `renovate <ecosystem> <package> <version>` | Reads `.github/renovate.json5` on every call and reports the rules that touch the package: `minimumReleaseAge` (from the file, not hard-coded; also the age `versions` and `candidates` use), rules with `enabled: false` that match it (by `matchPackageNames` / `matchDepNames`, glob and `/regex/` patterns, with `matchUpdateTypes`), `allowedVersions` (`/regex/` or a range) it breaks, and `prBodyNotes` to pass on to the pull request. |

Details:

- **Release families** (one target version per group), a table in the script: `com.fasterxml.jackson.core:*`
  (Jackson 2), `tools.jackson.core:*` (Jackson 3), `org.bouncycastle:*-jdk18on`,
  `org.apache.tomcat.embed:tomcat-embed-*`; every other package is its own family. The group id is
  `<ecosystem>:<family>` (`maven:jackson2`, `maven:org.apache.commons:commons-lang3`).
- **Release line**: an alert whose vulnerable range starts at `>= X.Y.0` belongs to line `X.Y`; the
  group's required minimum is computed per line, so Jackson 2's advisories with fixes in 2.18.11,
  2.21.7 and 2.22.3 give one minimum per line, and `candidates` picks the line in use (the one passed
  with `--line`, default: the highest line of the group).
- **Versions and ranges**: a comparator for Maven, PEP 440 and npm-style versions (numeric segments,
  then qualifiers; `alpha`, `beta`, `b`, `rc`, `cr`, `M<n>`, `-SNAPSHOT`, `dev`, `a<n>`, `-pre` are
  pre-releases); GitHub's range syntax (`>= 2.19.0, <= 2.21.6`, `< 1.85`, `= 1.2.3`).
- **Registries**: Maven Central's `maven-metadata.xml` for the version list and the `Last-Modified`
  header of each version's `.pom` (HEAD) for its date. Not `search.maven.org`: its index stops in
  mid-2025 (checked on 2026-10-08: no Spring Boot 4, no Jackson 3 releases). PyPI's JSON API
  (`/pypi/<name>/json`, the earliest `upload_time` of a release's files). The npm registry
  (`/<name>`, the `time` field). Only the newest 30 versions are dated, to keep the HEAD requests few.
- **JSON5**: a string-aware converter to JSON (line and block comments outside strings, unquoted
  keys, single-quoted strings, trailing commas), then `json.loads`. Tested on the real
  `.github/renovate.json5` (its strings contain `//` in URLs and `\\` escapes).

### The skill: `.claude/skills/dependabot-fix/SKILL.md`

In the shape of `renovate-update/SKILL.md`, with the issue's flow as sections:

0. **Preflight**: `gh auth status`, clean working tree (never stash), remember the branch.
1. **Collect and group**: `alerts.py alerts $ARGUMENTS`.
2. **Find where each group comes from**: the commands per ecosystem (Gradle: `buildEnvironment` on
   the root build and on `build-logic`, `dependencies` / `dependencyInsight --dependency <name>
   --configuration <cfg>` on every project; uv: `uv tree --invert --package <name>` in
   `artifact/ta-runner/`; pnpm: `with-node.sh pnpm why <name>` in `artifact/frontend/` and `e2e/`).
   Every Maven alert points at `settings.gradle.kts`, so this step is what tells runtime classpath,
   build script classpath (a plugin) and `build-logic` apart. Per group: resolved version(s), what
   brings it in, where a version can be set.
3. **Check the possible versions**: `alerts.py candidates <group>`, and for each direct parent
   (Spring Boot, the plugin, the package) `alerts.py versions` to see whether a mature parent
   release already brings the fix.
4. **Check the Renovate rules**: `alerts.py renovate` for every suggestion.
5. **Show the plan and ask** (`AskUserQuestion`, header `Apply`): one table, group, alerts,
   severity, current version(s), source, suggested version and its date, where it is set, Renovate
   rules touched. Options **Apply all** (first), **Apply some** (say which), **Stop here**. Groups
   that need a pre-release or a major, or that would break a Renovate rule, are only reported, with
   the question whether to apply them anyway as a separate option. Dismissals are only suggested
   (with the reason), never done.
6. **Apply**: least invasive fix first (move the direct parent to a mature release that brings the
   fix), otherwise a pin where the repository already sets versions: Gradle in
   `gradle/libs.versions.toml` plus a platform or constraint in the convention plugin (runtime) or
   in the root `build.gradle.kts`' `buildscript` block (a plugin's classpath); uv in
   `[tool.uv] constraint-dependencies` and `uv lock`; pnpm `pnpm.overrides` and `pnpm install`.
   Every pin carries a comment: the advisory IDs, and when it can go (the parent release that brings
   the fix). Branch `fix/dependabot-<date>` (or the current issue branch), one commit per group
   (`Fix <family> alerts: <version> (<GHSA IDs>)`), `mise run check`, the build task that covers it
   (`mise run build` for Gradle, `mise run runner-test`, the frontend's tests), a version bump in its
   own commit, push, a pull request listing the alerts it closes. Never merge.
7. **Report**: fixed, waiting for a mature release (with the date), needing a pre-release or major
   (not applied), pins and when they can go, suggested dismissals. After CI is green, the alerts
   only close when Dependabot rescans `main`: say so instead of waiting.

Ground rules as in `renovate-update`: mature only, green by fixing never by weakening, lock files
regenerated by their tool, never merge / approve / `--auto` / `--admin`, `~/.local/bin/mise`.

### The fixes for today's alerts (as of 2026-10-08, mature = released on or before 2026-09-24)

Found with `buildEnvironment` and `dependencies` on every project and on `build-logic`:

| Group | Alerts | Resolved now, brought in by | Parent with a fix? | Target (release date) | Where |
|---|---|---|---|---|---|
| Jackson 2 (`com.fasterxml.jackson.core`) | 30 (high, medium) | 2.21.5 / 2.22.1 at runtime (`docker-java-core` 3.7.1, Spring Boot 4.1.1's BOM, `swagger-core-jakarta` 2.2.55 via springdoc 3.1.1); 2.14.2 on the root build script classpath (node-gradle plugin 7.1.0) | No: docker-java 3.7.1, springdoc 3.1.1, node-gradle 7.1.0 and Spring Boot 4.1.1 are the newest stable releases | 2.22.3 (2026-09-21), the line already in the jar | `jackson2` + `jackson2-bom` in the catalog; platform in the convention plugin; `buildscript` constraint in root `build.gradle.kts` |
| Jackson 3 (`tools.jackson.core`) | 8 (high, medium) | 3.1.5 at runtime (Spring Boot 4.1.1's BOM) and on the root build script classpath (Spring Boot Gradle plugin) | No: Spring Boot 4.1.1 is the newest stable | 3.1.7 (2026-09-22), staying on Spring Boot 4.1's 3.1 line (not 3.2.3) | `jackson3` + `jackson3-bom`; platform in the convention plugin; `buildscript` constraint |
| Bouncy Castle | 6 (critical, high, medium) | 1.82 (`bcprov`, `bcpkix`, `bcutil`) via `docker-java-core` 3.7.1 | No: 3.7.1 is the newest | 1.86 (2026-09-11) | `bouncycastle` + the three libraries; constraints in the convention plugin |
| Tomcat | 3 (critical) | 11.0.24 via Spring Boot 4.1.1's BOM | No: 4.1.1 is the newest stable | 11.0.26 (2026-09-09), with `tomcat-embed-el` and `-websocket` | `tomcat` + the three libraries; constraints in the convention plugin |
| commons-lang3 | 1 (medium) | 3.16.0 on the root build script classpath (Spring Boot Gradle plugin → `commons-compress` 1.27.1); runtime already has 3.20.0 | No | the helper's suggestion (3.20.0 or newer mature) | `commonsLang3` + library; `buildscript` constraint |
| kotlin-gradle-plugin | 1 (medium) | 2.4.0 in `build-logic`, Gradle's embedded `kotlin-dsl` | Only Gradle itself | none: fixed only in 2.4.20-Beta1 (pre-release) | not applied, reported |
| cryptography (pip) | 3 (high, medium) | 50.0.1 everywhere except Intel Macs: 48.0.1 there, held by `constraint-dependencies` `cryptography<49` (no x86_64 macOS wheels from 49 on) | n/a | none without dropping Intel-Mac support | not applied; the report suggests a dismissal (dev-only, Intel Macs) for the developer to decide |

The developer agent re-runs `alerts.py` on that day's alerts and uses its suggestions; when a newer
mature release appears, or a parent now brings the fix, that wins over this table, and the pull
request says so. The convention plugin covers every backend module (all apply
`tradinganalysisplatform.java-library`); `build-logic/checkstyle-rules` imports the Spring Boot BOM
for its tests only and gets no pin unless `dependencies` shows a vulnerable version there.

## Work packages

### WP1: Helper script and its tests

- **Status**: not done: the developer run could not write files under .claude/skills/dependabot-fix/ (Write and mkdir were refused)
- **Depends on**: none
- **Files**: `.claude/skills/dependabot-fix/alerts.py`, `.claude/skills/dependabot-fix/test_alerts.py`
- **Steps**:
  - [ ] Version comparator, pre-release detection, GitHub range parser
  - [ ] JSON5 reader and the `renovate` command
  - [ ] `alerts` (fetch, normalise, family grouping, per-line required minimum)
  - [ ] `versions` (Maven metadata + `.pom` `Last-Modified`, PyPI, npm; maturity and `eligible_from`)
  - [ ] `candidates` (fixes every alert, mature, stable, same major; `waiting`, `needs_prerelease`, `needs_major`)
  - [ ] Run each command once for real against today's alerts and registries, and fix what differs from the table above
- **Tests**: `test_alerts.py` (unittest, no network: the fetch functions replaced by fixtures built
  from today's alerts): version ordering and pre-releases (`2.4.20-Beta1`, `4.2.0-M2`, `3.0.0-rc4`,
  `1.85.2`); range matching (`>= 2.19.0, <= 2.21.6`, `< 1.85`); grouping of the Jackson 2 alerts into
  one group with minimums 2.18.11 / 2.21.7 / 2.22.3 per line; maturity with a fixed `--today`
  (2.22.3 mature on 2026-10-08, 3.2.3 not before 2026-10-06); `candidates` putting the Kotlin alert
  under `needs_prerelease`; the JSON5 reader on the real `.github/renovate.json5` (equals a
  hand-checked subset: `minimumReleaseAge`, the rule descriptions); `renovate` reporting the
  `node` `allowedVersions` rule for `node 25.0.0`, the disabled `java` major for 26, and the
  TypeScript 6 `prBodyNotes`.

### WP2: The skill

- **Status**: not done: the developer run could not write files under .claude/skills/dependabot-fix/ (Write and mkdir were refused)
- **Depends on**: WP1 (the commands it names)
- **Files**: `.claude/skills/dependabot-fix/SKILL.md`
- **Steps**:
  - [ ] Frontmatter (`name: dependabot-fix`, a `description` with trigger phrases: "/dependabot-fix", "fix the Dependabot alerts", "fix the security alerts")
  - [ ] Command table of `alerts.py`, ground rules, sections 0–7 as in the design
- **Tests**: none of its own (Markdown); WP4 follows it.

### WP3: Test task in check and CI

- **Status**: done
- **Depends on**: WP1
- **Files**: `mise.toml`, `.github/workflows/ci.yml`
- **Steps**:
  - [x] Task `skill:test` ("Python tests of the Claude Code skills' helpers"):
        `uv run --no-project python -m unittest discover -s .claude/skills/dependabot-fix -p 'test_*.py'`
  - [x] Add it to `check` after `agent:test`
  - [x] CI, *ta-runner* job: a step "Skill helpers' Python tests (.claude/skills)" after the agent scripts' step
- **Tests**: `mise run skill:test` and `mise run check` pass; CI runs the step.

### WP4: Fix today's alerts by following the skill

- **Depends on**: WP1, WP2
- **Files**: `gradle/libs.versions.toml`, `build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts`, `build.gradle.kts`
- **Steps**:
  - [ ] Sections 1–4 of the skill on the open alerts; record the table of section 5 in the pull request body
  - [ ] Catalog: the versions and libraries of the table, under a comment block "Security pins (Dependabot)": each with its GHSA IDs and "remove when <parent> brings ≥ <version>" (Spring Boot > 4.1.1 for Tomcat and Jackson 3; docker-java > 3.7.1 for Bouncy Castle and Jackson 2)
  - [ ] Convention plugin: `implementation(platform(...))` for the two Jackson BOMs and `constraints { implementation(...) { because("<GHSA IDs>") } }` for Tomcat and Bouncy Castle, next to the Spring Boot BOM
  - [ ] Root `build.gradle.kts`: `buildscript { dependencies { constraints { classpath(...) } } }` for Jackson 2, Jackson 3 and commons-lang3, with the same comments
  - [ ] One commit per group (Jackson 2, Jackson 3, Bouncy Castle, Tomcat, commons-lang3)
  - [ ] Check with `./gradlew buildEnvironment` and `dependencies` / `dependencyInsight` on every project that no vulnerable version is left on any runtime, test or build script classpath; paste the before/after versions into the pull request body
- **Tests**: `mise run check` (includes `analyzeDependencies`), `mise run build` (every backend
  test against the new Tomcat, Jackson and Bouncy Castle; the Docker runner tests use docker-java
  with the new Bouncy Castle), `mise run e2e` if Docker is available.

### WP5: Docs and version

- **Depends on**: WP2, WP3, WP4
- **Files**: `README.md`, `gradle.properties`, `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [ ] README, "Dependency updates": a paragraph on `/dependabot-fix` after the `renovate-update` one
  - [ ] README: the `mise run check` line and the CI table's ta-runner row name `skill:test`
  - [ ] `mise run version:bump minor`, its own commit
- **Tests**: `scripts/version.sh check-bump origin/main`

## Tests

- Unit: `test_alerts.py` (WP1), run by `mise run skill:test`, `mise run check` and CI.
- Integration: `mise run build` with the pins (all backend tests, Testcontainers), and the
  before/after `buildEnvironment` / `dependencies` output in the pull request body, which proves the
  vulnerable versions are gone from the build.
- Done when: the skill and helper are in place and tested, CI is green, and the pull request lists
  the alerts it closes (Jackson 2, Jackson 3, Bouncy Castle, Tomcat, commons-lang3: 45 of 48 as of
  2026-10-08) and the ones left open with the reason. The alerts themselves close when Dependabot
  rescans `main` after the merge.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Dependency updates" | A paragraph: Renovate only moves direct and pinned dependencies, so alerts on transitive ones stay open; `/dependabot-fix` groups them, picks the newest mature fix, checks it against `renovate.json5`, asks, then pins it with a comment that says when the pin can go. Inputs (none, ecosystem, alert numbers). |
| Text | `README.md`, `mise run check` line and the CI table (ta-runner row) | Name the skill helpers' tests (`mise run skill:test`). |
| Text | `gradle/libs.versions.toml` | The "Security pins (Dependabot)" comment block (WP4). |
| Screenshot | none | No page of the app changes. |

## Out of scope

- Turning on Dependabot security updates or version updates (Renovate stays the update tool).
- Dismissing alerts: the skill and the pull request only suggest it (`cryptography` on Intel Macs).
- The `kotlin-gradle-plugin` alert: its only fix is a pre-release of Gradle's embedded Kotlin; it
  goes with a Gradle release that ships Kotlin ≥ 2.4.20 (Renovate's wrapper update).
- Dropping the Intel-Mac `cryptography<49` constraint.
