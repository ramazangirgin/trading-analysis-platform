# Plan: CI skips redundant work and starts the jar consumers earlier

- **Issue**: #224 (CI is slow: skip application jobs for docs/pipeline-only changes, narrow the server-e2e trigger, drop duplicate push runs, start images and e2e earlier)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A CI run does only the work its change needs. A pull request that changes only documents, plans or
the agent pipeline finishes in about a minute and still reports **CI passed**. A pull request that
touches no deploy files stops running the 8 minute server end-to-end job (a `mise.toml` change no
longer triggers it). A push to a pull request branch starts one run, not two. The platform jar is
built by a job of its own, so the platform image and the end-to-end tests start while the backend
and frontend tests still run. Every check that ran before still runs when its inputs change, and
**CI passed** still fails when any job fails or is cancelled.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| CI (`.github/workflows/ci.yml`) | The header comment lists every job and the triggers; every job is in `ci-passed`'s `needs`. Actions stay on the major versions already used (`actions/checkout@v7`, `actions/upload-artifact@v7`, `actions/download-artifact@v8`); no new action, so the change detection is a script, not a path-filter action. |
| Scripts (`scripts/`) | Plain Bash like `scripts/version.sh`; a task in `mise.toml` for its test. |
| Releases | `docs/coding-convention/repository-versioning-and-releases.md`: `release.yml` runs after **CI passed** on a push to `main` and downloads `platform-jar` from that run. |
| Dependency hygiene, versioning | `repository-dependency-hygiene.md` (no new dependency here); a minor bump in its own commit (`mise run version:bump minor`). |
| Docs | README "Continuous integration" table and `deploy/server/README.md` "Testing" name the triggers and must say the new ones. |

## Design

**Areas.** A new first job `changes` (needs no other job) checks out with `fetch-depth: 0`, runs
`scripts/ci-changes.sh` and exposes four outputs, each `true` or `false`:

| Output | True when a changed file is | Jobs it gates |
|---|---|---|
| `app` | anything except the non-application set below | `build`, `jar`, `e2e`, `images`, `runner-image`, `runner-image-arm64` |
| `runner` | `app`, or under `scripts/agent/` or `.claude/skills/` | `runner` (it holds `agent:test` and `skill:test`) |
| `deploy` | under `deploy/`, `mise.toml`, `.github/` | `server` |
| `server_e2e` | under `deploy/`, `artifact/ta-runner/Dockerfile`, `.github/workflows/ci.yml`, or `scripts/ci-changes.sh` | `server-e2e` |

The non-application set is the allow-list of what cannot change the jar, an image or the deployment:
`docs/`, `.plans/`, `README.md`, `CLAUDE.md`, `.claude/`, `scripts/agent/`. Everything else, including
a file nobody listed (`gradle/`, `build-logic/`, `mise.toml`, `.github/`, `e2e/`, `artifact/`), is an
application change: a new kind of file runs everything until someone lists it. The issue names five
areas (backend, frontend, runner, e2e, deploy); the jobs are not split that finely (`build` is one
job for backend and frontend, `e2e` and the images need the whole jar), so a finer split would gate
the same jobs.

The base of the diff: a pull request uses `git merge-base origin/<base_ref> HEAD`; on demand
(`workflow_dispatch`), on a push to `main` and when no base can be found, every output is `true`.
A push to `main` must build the jar and run everything, since `release.yml` releases that run's
`platform-jar`. The script prints the changed files and each output to the job log.

**Skipped jobs.** Each gated job gets `needs: changes` and `if: needs.changes.outputs.<area> == 'true'`.
`images` (`needs: [jar, runner-image]`) and `e2e` (`needs: jar`) need no `if:` of their own: a
skipped dependency skips them, which is right, because every one of those dependencies is gated by
`app`. `version` always runs and does not need `changes`.

**`CI passed`.** `needs` gains `changes` and `jar`. The loop accepts `success` and `skipped` and
fails on `failure`, `cancelled` and anything else. A job skipped because `changes` failed is not
hidden: `changes` itself is in `needs` and fails the check.

**Triggers.** `push` runs on `main` only (`branches: [main]`); `pull_request` covers every branch with
a pull request, `workflow_dispatch` stays. The header comment drops "Pull requests run even when
their branch already ran on push" and says that a branch without a pull request has no CI until one
is opened. `fix-ci.sh` finds the failed run with `gh run list --workflow ci.yml --commit <head>`,
which also finds a pull request run, so the agent scripts keep working. `release.yml` only reacts to
`push` runs on `main`, which stay.

**`server-e2e`.** Its own diff step goes (the `changes` job decides); the job is gated by
`server_e2e`. `mise.toml` leaves its trigger: the deploy test installs Ansible from `mise.toml`,
but the `server` job (gated by `deploy`, which includes `mise.toml`) already installs and runs it
with the same versions. Because a skipped job now counts as passed, the "Nothing to test" step goes
too.

**Early jar.** A new job `jar` ("Platform jar") runs `./gradlew --console=plain :backend:bootJar`
(the task `mise run e2e` uses; it builds the frontend it bundles) and uploads `platform-jar`
(`artifact/backend/build/libs/platform.jar`, as `build` does now). `build` stops uploading the jar.
`images` and `e2e` change `needs` from `build` to `jar`. They start when the jar is built, while
`build` still runs its tests; `CI passed` still needs `build`, so a red test run fails the check
and the jar is never released (`release.yml` runs only after **CI passed**). The jar job and
`build` both compile once; the extra compute is off the critical path.

## Work packages

### WP1: Change detection script and its test

- **Status**: done
- **Depends on**: none
- **Files**: `scripts/ci-changes.sh` (new), `scripts/test-ci-changes.sh` (new), `mise.toml` (task `ci:test-changes`)
- **Steps**:
  - [x] `scripts/ci-changes.sh <base-sha-or-empty>`: reads `git diff --name-only <base> HEAD`, prints `app=`, `runner=`, `deploy=`, `server_e2e=` lines for `$GITHUB_OUTPUT` and a log of the files and results; empty base means all `true`
  - [x] `scripts/test-ci-changes.sh`: builds a throwaway Git repository and feeds the script changed-file sets; `mise run ci:test-changes` runs it
- **Tests**: the test script covers: only `docs/x.md` (all four false); only `.plans/` and `README.md`; `scripts/agent/x.sh` (only `runner`); `.claude/skills/x/SKILL.md` (only `runner`); `.claude/settings.json` (all false); `artifact/backend/...` (`app`, `runner`); `mise.toml` (`app`, `runner`, `deploy`, not `server_e2e`); `deploy/Dockerfile` (all four); `.github/workflows/ci.yml` (all four); an unlisted new top-level file (`app`); an empty base (all true); a mixed set of docs and backend (`app`).

### WP2: Workflow

- **Status**: done
- **Depends on**: WP1
- **Files**: `.github/workflows/ci.yml`
- **Steps**:
  - [x] Triggers: `push` on `main` only; header comment rewritten
  - [x] `changes` job (checkout with `fetch-depth: 0`, `scripts/ci-changes.sh`, the script's test) and `needs`/`if` on `build`, `runner`, `runner-image`, `runner-image-arm64`, `server`, `server-e2e`, `images`, `e2e`
  - [x] `jar` job; `build` without the jar upload; `images` and `e2e` need `jar`
  - [x] `server-e2e` without its diff step and the "Nothing to test" step
  - [x] `ci-passed`: `needs` with `changes` and `jar`, accepts `success` and `skipped`
  - [x] Header comment: the new jobs, the areas, the triggers
- **Tests**: no unit test for YAML. On this pull request's own CI: the run starts (valid syntax), the `changes` outputs are in the job log, and `CI passed` is green. A follow-up check after merge: a docs-only pull request finishes in about a minute with `CI passed` (see "Tests").

### WP3: Docs

- **Status**: done
- **Depends on**: WP2
- **Files**: `README.md`, `deploy/server/README.md`, `docs/coding-convention/repository-versioning-and-releases.md` if it names the push runs
- **Steps**:
  - [x] README "Continuous integration": the triggers, a row for *Changes* and *Platform jar*, the skipped-job rule and `CI passed` accepting `skipped`, the server end-to-end trigger, the task list row for `ci:test-changes`
  - [x] `deploy/server/README.md`: the `server:test` trigger without `mise.toml`
- **Tests**: none (text); `mise run check` passes.

## Tests

- `mise run ci:test-changes` (WP1) proves the area rules, run in CI by the `changes` job.
- The pull request's own CI is the check for the workflow: it changes `ci.yml` and `scripts/`, so
  `app`, `runner`, `deploy` and `server_e2e` are all true and every job runs, `jar` before `build`
  ends, and `CI passed` is green.
- The acceptance cases that need a different change set (a docs-only pull request in about a
  minute, one run per push, no `server-e2e` run for a Java change) are checked on the next such
  pull requests; the developer agent cannot run them from this branch.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | README.md "Continuous integration" | Triggers (push on `main` only, pull requests, on demand), the new *Changes* and *Platform jar* jobs, which jobs a documents-only change skips, `CI passed` accepting `skipped`, the server end-to-end trigger without `mise.toml` |
| Text | README.md task list | `ci:test-changes` |
| Text | deploy/server/README.md "Testing" | `server:test` runs when `deploy/`, the ta-runner Dockerfile, the CI workflow or `scripts/ci-changes.sh` change |
| Text | ci.yml header | The jobs, areas and triggers |
| Screenshot | none | No page changes |

## Out of scope

- Speeding up the server image build inside `server-e2e` (issue finding 3: reuse the image caches or
  build in parallel). It needs changes under `deploy/server/` and its own measurements; it goes into
  a separate issue.
- Splitting formatting and lint from the build job (issue finding 5): no total gain.
- Running `server-e2e` on a schedule or on `main` only.

## Open questions

- Drop the follow-up on finding 3 from this issue, or keep the issue open for it? Default: this plan
  closes the issue and finding 3 becomes a new issue.
- A branch without a pull request now has no CI (push runs on `main` only). Is that acceptable?
  Default: yes, the agent flow and `/pr-ready` always open a pull request first.
