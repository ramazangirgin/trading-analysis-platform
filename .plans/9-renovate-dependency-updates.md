# Plan: Automated dependency updates with Renovate

- **Issue**: #9 (Automated dependency updates with Renovate)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

Every dependency the repository pins is kept current by the hosted Renovate app: grouped update
pull requests once a week, plus weekly lock file maintenance, never merged automatically. The
Gradle version catalog, the frontend's and e2e's pnpm packages, ta-runner's uv dependencies (the
TradingAgents release included), `mise.toml`'s tools, the Dockerfiles, the Compose file and the
workflows' actions are all covered, and Renovate's dependency dashboard issue lists them. An update
pull request runs the full CI and can merge without a hand-made version bump: update pull requests
are exempt from the bump and ship with the next release.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| Renovate (`.github/renovate.json5`) | Configuration next to the tool it configures, commented like the other config files (`lefthook.yml`, `mise.toml`, `ci.yml`): every non-default setting says why |
| Versioning and releases (`.github/workflows/ci.yml` *Version* job, `.github/workflows/release.yml`) | `docs/coding-convention/repository-versioning-and-releases.md`: one version, raised by every pull request into `main`, every commit on `main` a release. This plan adds an exception for Renovate's pull requests (Design, "Version bump of update pull requests"), so the document changes in the same pull request, with the decision and its reasons (`docs/coding-convention/README.md`, "Changing a convention") |
| CI (`.github/workflows/ci.yml`) | Root README "Continuous integration": **CI passed** stays the one required check; no new job |
| ta-runner (`artifact/ta-runner/pyproject.toml`, `uv.lock`, `ta_runner/engine/compat.py`, `tests/engine/test_upstream_contract.py`) | `docs/coding-convention/ta-runner-python-package-structure.md`: TradingAgents is imported only by `engine/compat.py`, and a TradingAgents bump goes together with `compat.py` and the contract tests (comment in `pyproject.toml`) |

## Design

### Repository configuration

One file, `.github/renovate.json5` (JSON5 instead of the issue's `renovate.json`, for the comments;
Renovate reads both), read by the hosted Renovate app. Extends `config:recommended`, which enables
the dependency dashboard issue, and sets:

- **Schedule**: `schedule: ["before 6am on monday"]`, `timezone: "Europe/Berlin"`; lock file
  maintenance on (`lockFileMaintenance.enabled: true`) on the same schedule. `automerge: false`
  (the default, set explicitly with a comment: the ruleset needs an admin to merge).
- **Labels**: `labels: ["dependencies"]`, so the release notes list the updates under "Build, CI
  and dependencies" (`.github/release.yml`).
- **Managers in use**, enabled by `config:recommended` without extra config: `gradle` (the version
  catalog `gradle/libs.versions.toml`), `gradle-wrapper`, `npm` (pnpm: `artifact/frontend/`,
  `e2e/`, `packageManager` fields included), `pep621` with uv (`artifact/ta-runner/pyproject.toml`,
  `uv.lock`), `dockerfile` (`deploy/Dockerfile`, `artifact/ta-runner/Dockerfile`), `docker-compose`
  (`deploy/docker-compose.yml`), `github-actions` (`.github/workflows/*.yml`), `mise` (`mise.toml`:
  java, uv, lefthook).
- **Custom (regex) managers**:
  1. *Node.js and pnpm of the build*: `node` and `pnpm` in `gradle/libs.versions.toml`'s
     `[versions]` are read by node-gradle, not by Gradle's dependency resolution, so the `gradle`
     manager does not see them. One regex manager per entry: `node` with datasource
     `node-version`, `pnpm` with datasource `npm` (package `pnpm`).
  2. *TradingAgents*: the tag in the archive URL in `artifact/ta-runner/pyproject.toml`
     (`.../TradingAgents/archive/refs/tags/v<version>.tar.gz`) and the pinned version in
     `test_upstream_contract.py` (`compat.upstream_version() == "<version>"`), datasource
     `github-tags`, `depName` `TauricResearch/TradingAgents`, `extractVersion` stripping the `v`.
     Both files carry the same dependency, so one update pull request changes both.
- **Groups** (`packageRules`):

  | Group | Matches | Restrictions |
  |---|---|---|
  | Spring Boot | the `springBoot` catalog version (BOM and plugin) and `springdoc` | none |
  | Gradle build | the other catalog entries (checkstyle, spotless, palantir, jacoco, archunit, mapstruct, docker-java, node-gradle) and the Gradle wrapper | none |
  | Frontend tooling | the `npm` manager in `artifact/frontend/` and `e2e/`, and the Node.js and pnpm regex managers | Node.js: even (LTS) majors only |
  | Python dependencies | `pep621` / uv in `artifact/ta-runner/` | none |
  | TradingAgents | the TradingAgents regex manager | alone, never grouped; the pull request body (`prBodyNotes`) says to update `uv.lock` (`uv lock`) and adapt `engine/compat.py` before merging: CI fails until then |
  | Docker base images | `dockerfile` and `docker-compose`, except postgres | `eclipse-temurin` stays on 25, `python` on 3.12 (`requires-python`): no major or minor jumps for these |
  | PostgreSQL | `postgres` in Compose | its own group, no major updates (a major needs a data migration); when #10 adds Testcontainers, its `postgres:18.x` reference joins this group (noted on #10, not done here) |
  | GitHub Actions | `github-actions` | none |
  | Tools | the `mise` manager and the `ghcr.io/astral-sh/uv` image in `artifact/ta-runner/Dockerfile` | `java` stays on 25, as above; uv in `mise.toml` and in the Dockerfile in one pull request |

  Major updates stay in their group but get a pull request of their own (`separateMajorMinor`, the
  default), so a breaking upgrade never hides in a batch.

### Version bump of update pull requests

Every pull request into `main` must raise the version (*Version* job, `check-bump`), but the hosted
Renovate app cannot run `scripts/version.sh` on its branches (`postUpgradeTasks` need a self-hosted
Renovate). So update pull requests are exempt from the bump: the *Version bumped* step skips
`check-bump` when the pull request's head branch starts with `renovate/` (`github.head_ref`) and
its author is `renovate[bot]` (`github.event.pull_request.user.login`). `check-sync` still runs.
Merged, they do not release on their own: `release.yml` already skips a version that has a release,
so the updates ship with the next pull request that raises the version.
`repository-versioning-and-releases.md` gets this exception and its reason, and its "every commit
on `main` is a release" becomes "every version raised on `main` is released".

### Done when (after the merge, by the admin)

Installing the Renovate GitHub app for this repository is a setting, not code: the admin does it
after the merge (Renovate then reads `.github/renovate.json5` from `main`, with no onboarding pull
request), and checks the dashboard issue against the managers above and that the first update pull
requests run the full CI.

## Work packages

### WP1: Repository configuration

- **Depends on**: none
- **Files**: `.github/renovate.json5` (new)
- **Steps**:
  - [ ] Write the configuration as designed, every non-default setting with a comment.
  - [ ] Validate it: `e2e/with-node.sh pnpm dlx --package renovate renovate-config-validator --strict .github/renovate.json5`.
  - [ ] Dry run against the working tree, to list what every manager finds:
        `GITHUB_COM_TOKEN=$(gh auth token) LOG_LEVEL=debug e2e/with-node.sh pnpm dlx renovate --platform=local --dry-run=lookup`.
        Check the extracted dependencies include every file and manager of the Design, both custom
        managers included.
- **Tests**: the validator passes; the dry run's extracted dependencies cover each manager and file
  of the Design (quote the counts per manager in the pull request's summary).

### WP2: Version check exemption for update pull requests

- **Depends on**: none
- **Files**: `.github/workflows/ci.yml` (*Version* job and its line in the header comment),
  `docs/coding-convention/repository-versioning-and-releases.md`
- **Steps**:
  - [ ] Add the `renovate/` branch and `renovate[bot]` author condition to the *Version bumped*
        step's `if`, with a comment pointing at the convention.
  - [ ] Write the exception and its reason into the convention document's rules and its "Where it
        is checked and used" table, and change its release wording.
- **Tests**: this pull request's own CI still runs `check-bump` (its branch is not `renovate/`);
  the first Renovate pull request after the merge passes the *Version* job without a bump.

### WP3: Documentation and version

- **Depends on**: WP1, WP2
- **Files**: `README.md`, `artifact/ta-runner/pyproject.toml` (comment only), `gradle.properties`,
  `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [ ] README, "Continuous integration": a "Dependency updates" paragraph after the jobs table:
        the hosted Renovate app, weekly on Monday morning, the groups, the dashboard issue, no
        automerge, update pull requests skip the version bump and ship with the next release, and
        what to do with a TradingAgents update.
  - [ ] README, "Versioning and releases": the exception, one sentence linking the convention.
  - [ ] `pyproject.toml`: the bump comment names Renovate's TradingAgents pull request.
  - [ ] `mise run version:bump minor`.
- **Tests**: `mise run check`; `scripts/version.sh check-bump origin/main`.

## Tests

No application code changes, so no unit tests. The proof is the configuration validator, the local
dry run listing every manager's dependencies (WP1), and CI on this pull request. The issue's "done
when" (the dashboard issue lists every dependency; the first update pull requests run the full CI)
is checked after the merge, once the admin has installed the app.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Continuous integration" | New "Dependency updates" paragraph (WP3) |
| Text | `README.md`, "Versioning and releases" | Update pull requests are exempt from the bump |
| Text | `docs/coding-convention/repository-versioning-and-releases.md` | The exception, its reason, the changed release wording (WP2) |
| Text | `.github/workflows/ci.yml` header comment | The *Version* job's line mentions the exception |
| Text | `artifact/ta-runner/pyproject.toml` comment | Renovate opens the TradingAgents pull request |
| Screenshot | none | No page changes |

## Out of scope

- Installing the Renovate app and triaging the first batch of updates: a setting and follow-up
  work after the merge.
- Pinning actions to commit SHAs: #8 (the `github-actions` manager keeps the pins current once #8
  has made them).
- Dependabot alerts and security updates: #3 (they stay on alongside Renovate).
- Testcontainers' `postgres` image: #10, which joins the `postgres` group when it adds it.
- A CI job validating the Renovate configuration: Renovate reports an invalid configuration in an
  issue of its own and stops; revisit if that proves too late.
