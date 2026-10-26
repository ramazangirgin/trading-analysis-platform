# Plan: Build the two images side by side on the server

- **Issue**: #233 (CI: speed up the server image build in the server deploy end-to-end job)
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

The first deploy to a server, and with it the *Server deploy end-to-end* CI job, builds the
ta-runner image while Gradle builds the platform jar, instead of one after the other. The job gets
shorter by the length of the ta-runner build, every check of `test-deploy.sh` still runs, and a real
server's first deploy and every upgrade that rebuilds get faster the same way. The CI table and
`deploy/server/README.md` say how the images are built, and the stale "20 to 30 minutes" goes.

## Measurements (before)

From the logs of recent successful `server-e2e` jobs (timestamps of the `==` steps of
`test-deploy.sh` and of the Ansible tasks):

| Phase | Job 121488765715 (09:14, before the package-upgrade skip) | Job 121588207962 (main, 13:05) | Job 121570249611 (12:25) |
|---|---|---|---|
| Whole job | 9 min 05 s | 6 min 33 s | 5 min 50 s |
| Setup scripts (steps 1 to 3) | 39 s | 42 s (40 s of it `adduser` copying the runner's `/etc/skel`) | 9 s |
| First deploy | 428 s (package upgrade 187 s) | 268 s | 264 s |
| of which *Build the images* (`mise run docker-build`, cold) | 169 s | **185 s** | **168 s** |
| Second deploy (`changed=0`) | 21 s | 22 s | 18 s |
| Tunnel, firewall, backup | 3 s | 3 s | 3 s |
| Third deploy (another commit, rebuilds with warm caches) | 37 s (build 16 s) | 39 s (build 16 s) | 34 s (build 16 s) |
| Key-only SSH | < 1 s | < 1 s | < 1 s |

The package upgrade that made the issue's 8.5 minutes is already skipped (`packages-upgraded`
marker in `test-deploy.sh`). What is left is dominated by the first, cold image build: about
170 to 185 s, half of the job. The rest of the first deploy (apt, Docker, mise, Compose up, health
wait: about 80 s) is spread over many short tasks.

The build is one task, `mise run docker-build`, which runs three commands one after the other:
`./gradlew :backend:bootJar` (cold on the server: the Gradle distribution, every dependency,
Node.js, the pnpm packages, compiling, the Vite build), `docker build` of the platform image (needs
the jar), `docker build` of the ta-runner image (needs nothing of the other two: pulls
`python:3.12-slim` and uv, `uv sync` of TradingAgents and its dependencies). The log does not show
how the 170 to 185 s split between them, since the task runs async; splitting the task (below)
makes each part a task of its own with its own timestamps. For scale: over the last 100 CI runs,
the `runner-image` job's Buildx build of the same Dockerfile took 15 to 57 s (mostly 24 to 32 s)
with its `type=gha` cache, so the cold build on the server is expected at about 40 to 70 s, which is
roughly what building it beside Gradle saves (the job then about 5 minutes).

Options from the issue, weighed:

- **Build the two images in parallel** (chosen): the ta-runner build needs neither the jar nor the
  platform image, so it can run beside the Gradle build. It shortens every first build and every
  rebuild, on CI and on real servers, and keeps the CI job a faithful copy of a server deploy.
- **Reuse the `type=gha` Buildx caches** of the `runner-image` and `images` jobs: not chosen. The
  playbook builds as the `platform` user with plain `docker build` on the docker driver, which
  cannot read the GitHub Actions cache without a docker-container builder and the Actions runtime
  token on the "server". That would be CI-only plumbing in the playbook, and the job would no longer
  show what a fresh server's first build does.
- **Load the `ta-runner-image` artifact** from the `runner-image` job: not chosen. The playbook
  builds whenever `/etc/trading-analysis-platform/built-commit` is missing, so a loaded image is
  only useful as a build cache, which the docker driver uses only with inline cache metadata (a
  change to every ta-runner build). It would also make `server-e2e` wait for `runner-image` (about
  a minute) and run only when that job runs (the `app` area).

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| deploy (`deploy/server/`) | The playbook's own rules: every task idempotent (a second deploy reports `changed=0`), `ansible-lint` production profile (`mise run server:check`), commands run as `app_user` with `app_user_environment`; the role/README layout described in `deploy/server/README.md` |
| mise tasks (`mise.toml`) | A task per command a developer or the server runs, `description` on each; sub-tasks named `<task>:<part>` as `server:*` and `agent:*` are; a task list change goes into `docs/development.md` |
| CI (`.github/workflows/ci.yml`) | The job comment block at the top lists every job; `docs/ci-and-releases.md` is the CI table |
| docs | Plain style of the existing documents; [repository-code-comments.md](../docs/coding-convention/repository-code-comments.md) for the comments in `mise.toml`, the role and the workflow (say why, not what) |
| version | `mise run version:bump minor` ([repository-versioning-and-releases.md](../docs/coding-convention/repository-versioning-and-releases.md)) |

## Design

### mise tasks

Split `[tasks.docker-build]` in `mise.toml` into two tasks, and keep `docker-build` as it is for
developers:

- `docker-build:platform`: `./gradlew --console=plain :backend:bootJar`, then the platform
  `docker build` (the two current commands, unchanged).
- `docker-build:runner`: the ta-runner `docker build` (the current third command, unchanged).
- `docker-build`: runs `{ task = "docker-build:platform" }` then `{ task = "docker-build:runner" }`
  in its `run` list (the same `{ task = ... }` form `setup` and `check` already use), so it still
  builds one after the other. Its existing comment (one after the other because of Podman's VM on
  macOS) stays with it, and gains that the server builds them side by side (see below).

Tags, build arguments and the order inside each task stay as they are, so `deploy/.env`, Compose,
the smoke test and the docs that name `mise run docker-build` keep working.

### Playbook (`deploy/server/roles/app/tasks/main.yml`)

Replace the single *Build the images* task with three, all `when: app_build_needed`, all as
`app_user` with `app_user_environment` and `chdir: "{{ app_dir }}"` like the current one:

1. **Start building the ta-runner image**: `mise run docker-build:runner`, `async: 3600`,
   `poll: 0`, `register: app_runner_build`, `changed_when: true`.
2. **Build the platform image**: `mise run docker-build:platform`, `async: 3600`, `poll: 15`
   (the current task's settings), `changed_when: true`.
3. **Wait for the ta-runner image**: `ansible.builtin.async_status` with
   `jid: "{{ app_runner_build.ansible_job_id }}"`, `register: app_runner_build_result`,
   `until: app_runner_build_result.finished`, `retries: 240`, `delay: 15` (the same hour as the
   async limit). It must run with the same `become_user` (and environment) as step 1: the job's
   status file is in that user's `~/.ansible_async`. A failed ta-runner build fails this task with
   the command's output in the result, as the single task did.

*Record the commit the images were built from* stays after these three, so a failure in either
build leaves `built-commit` unchanged and the next deploy builds again (the existing rule). When
step 2 fails while the ta-runner build still runs, the play stops; the background build finishes
on its own and is redone by the next deploy, which is acceptable.

The second deploy skips all three (`app_build_needed` false), so `changed=0` holds; the third
deploy (another commit) runs them, so "a deploy of another commit rebuilds" holds and its
`built-commit` check in `test-deploy.sh` still passes.

Memory: the two builds side by side raise the peak on the server. The README asks for 4 GB plus
the playbook's 4 GB swap; Gradle (default daemon heap) plus `uv sync` fit in that, and CI runners
(16 GB) are not the limit. Decided in the review: every server deploy builds side by side, with no
variable to turn it off; the 4 GB swap covers the peak on a small server.

### CI job

- `server-e2e` keeps running `mise run server:test`; nothing CI-specific is added to the build.
- Its `timeout-minutes` drops from 60 to 20: the job takes about 6 minutes, and the unexplained
  60-minute hang of run 40024112997 (issue, "Related") would then fail after 20 minutes instead of
  holding a runner for an hour. The playbook's own `async: 3600` stays (a slow real server).
  Decided in the review: the timeout drops to 20 minutes.
- The job comment ("It builds the images on the "server" (20 to 30 minutes)") gets the real
  duration and says the two images are built side by side.

### Measuring the result

The pull request's own `server-e2e` run (it runs: the change touches `deploy/` and `ci.yml`) is
the "after" measurement. The developer agent reads that job's log the same way as above (the
timestamps of the three build tasks and of each `==` step) and adds a before/after table to the
pull request description: the whole job, the first deploy, and the build tasks. If the run hangs or
times out, the log is read for the task it stopped in (the issue's "Related" note) and that is
written into the pull request description.

## Work packages

### WP1: Split the docker-build task

- **Status**: done
- **Depends on**: none
- **Files**: `mise.toml`, `docs/development.md`, `gradle.properties`,
  `artifact/frontend/package.json`, `artifact/ta-runner/ta_runner/__init__.py`
- **Steps**:
  - [x] Add `[tasks."docker-build:platform"]` and `[tasks."docker-build:runner"]` with the current
        commands and a `description` each; make `[tasks.docker-build]` run the two as
        `{ task = ... }` entries, one after the other; keep its comment, and say that the server
        deploy builds them side by side.
  - [x] `docs/development.md`: add the two tasks to the task list next to `docker-build`.
  - [x] `mise run version:bump minor` (writes the three version files).
- **Tests**: `mise run docker-build` still builds both images with both tags
  (`docker image ls trading-analysis-platform` and `ta-runner` show the version and `latest`);
  `mise tasks` lists the two new tasks; `mise run check` passes (version sync).

### WP2: Build the images side by side in the playbook, and document it

- **Depends on**: WP1
- **Files**: `deploy/server/roles/app/tasks/main.yml`, `.github/workflows/ci.yml`,
  `deploy/server/test-deploy.sh`, `deploy/server/README.md`, `docs/ci-and-releases.md`
- **Steps**:
  - [ ] Replace *Build the images* with the three tasks of "Playbook" above.
  - [ ] `ci.yml`: `server-e2e` `timeout-minutes: 20`; correct its comment (duration, images built
        side by side on the runner as on a server, no CI cache).
  - [ ] `test-deploy.sh`: replace "Takes 20 to 30 minutes" in the header with the measured
        duration on a CI runner (about 5 to 6 minutes; the images are still built on the "server").
  - [ ] `deploy/server/README.md`: see "Docs to update".
  - [ ] `docs/ci-and-releases.md`: see "Docs to update".
  - [ ] After CI ran: the before/after table in the pull request description ("Measuring the
        result").
- **Tests**: `mise run server:check` (ansible-lint production profile, the playbook's syntax);
  the PR's *Server deploy end-to-end* job passes with every check of `test-deploy.sh` (first
  deploy, second deploy `changed=0`, tunnel, firewall, backup, the third deploy rebuilds and
  `built-commit` is the second commit, key-only SSH) and is shorter than the "before" runs above.

## Tests

No unit tests: the change is in a mise task, an Ansible role and CI configuration.

- `mise run check` and `mise run server:check` locally.
- CI's *Server deploy (Ansible)* job (ansible-lint, syntax, setup scripts) and *Server deploy
  end-to-end* job (the whole deployment). The latter proves the issue's "done when": every
  `test-deploy.sh` check still passes, the first deploy's build tasks show the ta-runner build
  overlapping the Gradle build, and the job is shorter than the measurements above (recorded in
  the pull request description).
- The *Platform image and Compose smoke test* job is unchanged (it builds with Buildx in CI and
  does not use the mise task).

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `docs/ci-and-releases.md`, CI table row *Server deploy end-to-end* | Say how its images are built: on the runner as on a fresh server, by the playbook, the ta-runner image beside the Gradle build of the jar, then the platform image; no CI cache, so it shows a server's first build; about 6 minutes |
| Text | `deploy/server/README.md`, "What the deploy does" (diagram and the `app` row) | `mise run docker-build` → the ta-runner image (`docker-build:runner`) built beside the jar and platform image (`docker-build:platform`) |
| Text | `deploy/server/README.md`, "Testing" table row `server:test` | Replace "20 to 30 minutes" with the measured duration; say the images are built as on a fresh server |
| Text | `deploy/server/README.md`, troubleshooting "The build on the server fails or is slow" | It runs the two tasks side by side; which task's output to read (*Build the platform image* or *Wait for the ta-runner image*); each can be run alone on the server as `app_user` to retry one |
| Text | `docs/development.md` task list | `docker-build:platform`, `docker-build:runner` (WP1) |
| Text | `.github/workflows/ci.yml` job comment, `deploy/server/test-deploy.sh` header | Durations (WP2) |
| Screenshot | none | No page of the UI changes |

## Out of scope

- The runner's oversized `/etc/skel`, which made `adduser deploy` take 40 s in one run and 6 s in
  another: not reliable enough to plan from two runs; a follow-up issue if it shows again.
- Caching the `platform` user's Gradle home or the Docker build cache between CI runs: CI-only
  plumbing on the "server" that would hide what a server's first build does (see "Measurements").
- The other ~80 s of the first deploy (apt, Docker, mise installs, Compose up, the health wait):
  each task is short and does what a fresh server needs.
- The `images` and `runner-image` jobs: unchanged.

## Open questions

None. The review decided:

- Every server deploy builds the two images side by side, with no `app_parallel_build` variable;
  the 4 GB swap covers the peak memory on a small server.
- The `server-e2e` job's timeout drops from 60 to 20 minutes.
