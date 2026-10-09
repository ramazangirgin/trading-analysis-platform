---
name: dependabot-fix
description: Fix the open Dependabot alerts of this repository in one run - group them by release family, find which module, configuration or plugin brings each group in, pick the newest mature (at least 14 days old), stable version that fixes every alert of the group, check it against .github/renovate.json5, and after the developer approves the table, pin it on a branch (one commit per group) and open a pull request listing the alerts it closes. Use when the user asks to fix the Dependabot or security alerts, e.g. "/dependabot-fix", "/dependabot-fix maven", "/dependabot-fix 16 17 18", "fix the Dependabot alerts" or "fix the security alerts".
---

# Fix the Dependabot alerts

Dependabot alerts are on, Dependabot security updates are off: Renovate makes the update pull
requests ([`renovate-update`](../renovate-update/SKILL.md), README "Dependency updates"). Renovate
only moves direct and pinned dependencies, so alerts on **transitive** ones (a plugin's classpath, a
library a BOM manages, a lock file entry) stay open. This skill fixes those: it works out which
version fixes each group of alerts, checks that choice against Renovate's rules, and applies it
after **one approval** (5). It never merges on its own (6).

Input (`$ARGUMENTS`): nothing (all open alerts), an ecosystem (`maven`, `pip`, `npm`), or alert
numbers (`16 17 18`).

The helper [`alerts.py`](alerts.py) does the parts that must be exact. Run it from the repository
root as `uv run --no-project python .claude/skills/dependabot-fix/alerts.py <command>` (with
`~/.local/bin/mise exec --` in front when `uv` is not on PATH). Every command prints JSON:

| Command | What it does |
|---|---|
| `alerts [--ecosystem <e>] [<number>...]` | the open alerts, grouped by ecosystem and release family (Jackson 2, Jackson 3, Bouncy Castle, Tomcat; any other package is its own group), most severe first: packages, alert numbers, GHSA IDs, highest severity, and `minimum_per_line`, the highest first patched version per release line |
| `versions <ecosystem> <package> [--today YYYY-MM-DD]` | the released versions with their date, oldest first, flagged `prerelease`, `mature` and `eligible_from` |
| `candidates <group-id> [--alerts <file>] [--current <version>]` | the versions that fix **every** alert of the group: `suggested` (newest mature, stable, on the current release line, else on the current major), `leaves_line`, `newest_same_major`, `waiting` (fixes, not mature yet), `needs_prerelease`, `needs_major` |
| `renovate <ecosystem> <package> <version> [--current <version>]` | the rules of `.github/renovate.json5` that touch the package (read on every call): disabled updates, `allowedVersions` it breaks, `prBodyNotes` to pass on; `breaks` when one forbids the version |
| `gradle-scan [--alerts <file>] [<group-id>...]` | every configuration of every Gradle project (the root build, `build-logic`, `build-logic/checkstyle-rules`, and their build script classpaths) that resolves a version in a vulnerable range of the Maven groups' alerts; `[]` when none does. About 20 seconds with a warm Gradle |
| `after-merge [--merge <sha>] <number>...` | after the merge, per alert: `fixed` (or `dismissed`), `open: submission not run yet`, `open: still in the graph` with the vulnerable versions the dependency graph still has, or `open: not in the graph any more` (Dependabot has not caught up yet) |

"Mature" is Renovate's `minimumReleaseAge` from `.github/renovate.json5` (14 days), the same age
pnpm (`minimumReleaseAge`) and uv (`exclude-newer`) enforce: a younger version would be dropped
again by the next lock file maintenance. Maven release dates come from the `.pom`'s
`Last-Modified` on Maven Central (`search.maven.org`'s index is stale).

**What GitHub sees.** For Gradle, the alerts come from Automatic Dependency Submission: on every push
to `main` GitHub runs Gradle and submits the resolved graph of **every** configuration (test
fixtures, Checkstyle, the plugins' classpaths), under the manifest `settings.gradle.kts`. A
vulnerable version that any one configuration still resolves keeps its alert open, however clean the
runtime classpath is. That is what `gradle-scan` checks (#128: Jackson 3.1.5 stayed on
`:backend:library:persistence`'s test fixtures after #124 had pinned 3.1.7).

## Ground rules

- **Mature and stable only.** Never apply a version younger than 14 days, a pre-release (alpha,
  beta, RC, milestone, snapshot), or a major jump, and never lower `minimumReleaseAge`. A group that
  only one of these fixes is reported and asked about (5), not applied.
- **The least invasive fix that holds** (6): move the direct parent first, pin the library only
  when no mature parent release brings the fix.
- **Every pin says why and when it goes**: the GHSA IDs and the parent release that makes it
  unnecessary, in the place Renovate reads, so Renovate keeps it current.
- **Green by fixing, never by weakening**: no skipped tests, disabled checks, suppressions or
  `--no-verify`. Lock files are regenerated by their tool (`uv lock`, `with-node.sh pnpm install`),
  never edited by hand.
- **Never dismiss an alert.** The skill may suggest a dismissal (with the reason, for example code
  that is not reachable or a platform that is not shipped); the developer does it.
- **Never merge, approve or use auto-merge** (6). Tools run with mise: `~/.local/bin/mise run <task>`.

## 0. Preflight

```sh
gh auth status
git status --short
```

Stop if `gh` is not logged in or cannot read the alerts (`gh api repos/{owner}/{repo}/dependabot/alerts`
needs the `security_events` scope or a fine-grained token with Dependabot alerts read access). The
working tree must be clean, since 6 creates a branch: if it is not, say which files are in the way
and stop; never stash or discard them. Remember the branch you started on.

## 1. Collect and group

```sh
alerts.py alerts $ARGUMENTS > "$TMPDIR/dependabot-groups.json"
```

Print one line per group: id, number of alerts, highest severity, `minimum_per_line`. Groups with
`unpatched` alerts (no fixed version at all) can only be reported (7). With no open alert, say so
and stop.

## 2. Find where each group comes from

Every Maven alert points at `settings.gradle.kts`, so the alert does not say which module,
configuration or plugin brings the library in. Find it:

| Ecosystem | Commands |
|---|---|
| Maven (Gradle) | `alerts.py gradle-scan --alerts "$TMPDIR/dependabot-groups.json"` first: every project and configuration that resolves a vulnerable version. Then, for the path: `./gradlew -q buildEnvironment` (the root build script classpath: the plugins), `./gradlew -q -p build-logic buildEnvironment dependencies` (the convention plugins' build), `./gradlew -q <project>:dependencies` for every project (`./gradlew -q projects` lists them), then `./gradlew -q <project>:dependencyInsight --dependency <name> --configuration <cfg>` for the path |
| pip (uv) | `uv tree --invert --package <name>` in `artifact/ta-runner/`; also read `[tool.uv] constraint-dependencies` in its `pyproject.toml` (a constraint can hold a version on purpose, for example the Intel-Mac `cryptography<49`) |
| npm (pnpm) | `artifact/frontend/with-node.sh pnpm why <name>` in `artifact/frontend/`, `e2e/with-node.sh pnpm why <name>` in `e2e/` |

Per group, note the resolved version(s) (that is `--current` for 3), what brings each in (a direct
dependency, a BOM, a plugin, a lock file entry), and where a version can be set.

## 3. Check the possible versions

```sh
alerts.py candidates <group-id> --alerts "$TMPDIR/dependabot-groups.json" --current <resolved version>
```

With several resolved versions, pass the highest one in the shipped artifact (the jar, the runner
image). Read the result:

- `suggested`: the version to propose. With `leaves_line`, check whether the line matters: it does
  when a BOM or plugin manages the library on that line (Jackson 3 on Spring Boot 4.1 is on 3.1, so
  3.2 would be a jump; ask), not for a library whose minor numbers mark no line (Bouncy Castle,
  commons-lang3).
- `waiting` only: nothing mature fixes it yet. Report the date it becomes eligible
  (`eligible_from`) and leave the group open.
- `needs_prerelease` or `needs_major`: report, and ask in 5; never apply on your own.

Then look for the **parent** fix first: for the direct dependency, BOM or plugin that brings the
library in, `alerts.py versions <ecosystem> <parent>` and its newest mature release's dependencies
(its `.pom`, `pnpm view <parent>@<version> dependencies`, PyPI's `requires_dist`). If a mature parent
release brings a fixed version, that is the fix.

## 4. Check the Renovate rules

```sh
alerts.py renovate <ecosystem> <package or parent> <version> --current <resolved version>
```

for every suggested version (the parent's, when the fix is a parent update). Drop or flag what
`breaks` (Java stays on 25, Python on 3.12, PostgreSQL no majors, Node.js even majors, the `engines`
field), and keep the `prBodyNotes` for the pull request body (the TradingAgents and TypeScript 6
notes say what else the update needs).

## 5. Show the plan and ask

One table: group, alert numbers, severity, current version(s), what brings it in, suggested version
and its release date, the fix (parent update or pin) and where it is set, Renovate rules touched.
Below it: what waits for a mature release (with the date), what needs a pre-release or a major,
suggested dismissals with the reason.

Ask with `AskUserQuestion`, header `Apply`: **Apply all** (first), **Apply some** (say which in
"Other"), **Stop here**. When a group needs a pre-release, a major or breaks a Renovate rule, add
**Also apply <group>** as a separate option, so it is never part of "Apply all". Change nothing
before the answer.

## 6. Apply

```sh
git fetch origin
git switch -c fix/dependabot-$(date +%F) origin/main   # or stay on the issue's branch when there is one
```

Per group, the least invasive fix that holds:

1. **Update the direct parent** where its version is set (`gradle/libs.versions.toml`,
   `pyproject.toml`, `package.json`) to the mature release that brings the fix.
2. **Otherwise pin the library**, where the repository already sets versions:
   - **Gradle, runtime and test classpaths**: the version and library in `gradle/libs.versions.toml`
     under the "Security pins (Dependabot)" comment, then in the convention plugin
     (`build-logic/src/main/kotlin/tradinganalysisplatform.java-library.gradle.kts`), in its
     `sourceSets.configureEach` block (every source set's `implementation`: main, test, test
     fixtures), a `platform(...)` for a family with a BOM (Jackson) or a constraint with
     `because(...)` for the others (Tomcat, Bouncy Castle).
   - **Gradle, build script classpath** (a plugin brings it): a `classpath(...)` constraint in the
     root `build.gradle.kts`' `buildscript` block, version from the catalog.
   - **uv**: `[tool.uv] constraint-dependencies` in `artifact/ta-runner/pyproject.toml`, then
     `uv lock`; for a lock-only bump within the range, `uv lock --upgrade-package <name>`.
   - **pnpm**: `pnpm.overrides` in the `package.json`, then `with-node.sh pnpm install`.

   The pin's comment names the GHSA IDs, the version that brings the vulnerable one and from
   where, and when the pin can go: "remove when <parent> > <version> brings >= <fixed version>".
3. **Check it is gone**: Gradle: `alerts.py gradle-scan --alerts "$TMPDIR/dependabot-groups.json" <group-id>...`
   must print `[]` for the applied groups; a configuration it still lists is fixed before the
   commit, never left for later. uv and pnpm: rerun the commands of 2 and compare the resolved
   versions with the vulnerable ranges.
4. **Commit per group**: `Fix <family> alerts: <version>` with the GHSA IDs and the alert numbers in
   the body. Then `~/.local/bin/mise run check`, and the task that runs the affected tests:
   `mise run build` (Gradle), `mise run runner-test` (uv), the frontend's tests and `mise run e2e`
   (pnpm). The Git hooks run on commit; fix what they report.
5. **Version bump** in its own commit (`mise run version:bump minor`, see
   [the versioning convention](../../../docs/coding-convention/repository-versioning-and-releases.md)),
   unless the branch already raises it.
6. **Push and open a pull request** (`gh pr create`, label `dependencies` and `security`): the table
   of 5 with the alerts it closes, the before/after versions from 3, what was not applied and why,
   the `prBodyNotes`. Never merge it: the ruleset needs a code owner's review, and merging is the
   developer's decision. Wait for CI (`gh pr checks <pr> --watch --interval 30`) and fix a red run
   like any change.

## 7. Report

- what was fixed, with the pull request and its alerts;
- what waits for a mature release, with the date it becomes eligible;
- what needs a pre-release, a major or a parent that has no fix yet, and was not applied;
- the pins and the parent release that lets each go;
- suggested dismissals, with the reason, for the developer to do;
- that the alerts close only after the merge, once the dependency graph of `main` is updated (8).

Switch back to the branch you started on.

## 8. After the merge

The developer merges (6). Then, for the alerts the pull request said it closes:

```sh
gh run list --workflow "Automatic Dependency Submission" --branch main --limit 1   # wait for it (Gradle)
alerts.py after-merge --merge <merge commit> <number>...
```

Report one line per alert. `open: submission not run yet` and `open: not in the graph any more`
mean wait (Dependabot needs a few minutes after the submission), then run it again. `open: still in
the graph` means the fix missed a configuration or a lock file: run `gradle-scan` (or the commands
of 2) on `main`, say where the version still comes from, and fix it like a new run of this skill.
Never dismiss it to make the list clean.
