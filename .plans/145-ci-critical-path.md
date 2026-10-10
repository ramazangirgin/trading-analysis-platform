# Plan: Shorten the CI critical path

- **Issue**: #145 (CI: shorten the critical path (parallel e2e tests, ta-runner image beside the build, faster e2e setup))
- **Plan**: 1 of 1 for this issue; depends on: none
- **Version bump**: minor (docs/coding-convention/repository-versioning-and-releases.md)

## Goal

A CI run gets shorter, and it no longer grows by a test's full duration with every new end-to-end
test. The end-to-end tests run in parallel against the one platform. The ta-runner image builds,
and passes its lockdown check, at the same time as the build job. On a warm cache the e2e job skips
the Gradle and uv setup it does today. The build job makes one Gradle call instead of two. Every
test, check and smoke test still runs, and **CI passed** still requires every job. Bumping the
version still empties the caches; #144 fixes that.

## Affected parts and their conventions

| Part | Conventions that apply |
|---|---|
| CI (`.github/workflows/ci.yml`) | The header comment lists every job, and every job is in `ci-passed`'s `needs` (the comment above `ci-passed`, README "Continuous integration"). Actions are pinned to the major versions the workflow already uses (`actions/cache@v6`, `actions/upload-artifact@v7`, `actions/download-artifact@v8`, `docker/build-push-action@v7`, `docker/setup-buildx-action@v4`). No new actions. |
| e2e (`e2e/`) | Tests stay in `e2e/tests/*.e2e.ts`, with shared helpers in `tests/support.ts`. One platform (`start-platform.sh`) for the whole run. Comments say why, in the existing style. |
| Formatting | `docs/coding-convention/backend-java-formatting.md` ("Where it runs") and the tool table in `docs/coding-convention/README.md`: CI checks the formatting of every file, before the rest of the build. `mise run format-check` stays as it is for local use. |
| Versioning | `docs/coding-convention/repository-versioning-and-releases.md`: a minor bump in its own commit (`mise run version:bump minor`). |

## Design

### 1. End-to-end tests in parallel

**Shared state, as checked in the current tests:**

- The four analyses (`NVDA` in `analysis.e2e.ts`, `INTC` and `QCOM` in `compare.e2e.ts`, `AMD` in
  `reports.e2e.ts`) each use a ticker of their own, and they find their rows by ticker. Running
  side by side changes nothing for them.
- `settings.e2e.ts` has two tests, and they touch different state (the `OPENAI_API_KEY` credential,
  the presets). The key hint on *New analysis* (`form.noKeyHint`) shows whenever the provider has
  an `apiKeyEnv`, whether or not a key is set (`NewAnalysisPage.vue`), so the key test does not
  affect the tests that wait for that hint. The preset test is the only one that creates presets,
  so its `No presets yet.` check still holds.
- The platform has no users to separate the tests by. The tests are independent already, so no
  per-test user or data is needed. Each test keeps its own ticker; a new test that starts an
  analysis picks a ticker no other test uses (said in a comment in `support.ts`).
- **Concurrency limit:** `AnalysisService` runs at most `platform.analysis.max-concurrent-runs` (2)
  analyses and queues the rest. With several workers, up to four analyses run at once (compare's
  two, reports, analysis), and the queue would put back the waiting the parallel run removes.
  `start-platform.sh` starts the jar with `--platform.analysis.max-concurrent-runs=4`, with a
  comment explaining why. Replays use almost no CPU, and the production default stays 2.

**Config** (`e2e/playwright.config.ts`): `fullyParallel: true`, and
`workers: Number(process.env.E2E_WORKERS ?? 3)`. GitHub's `ubuntu-24.04` runners have 4 vCPUs, and
three Chrome workers plus the jar, PostgreSQL and the replaying ta-runner fit on them.
`E2E_WORKERS=1` brings back the serial run for debugging. Replace the comment "tests share its
database, so they run one at a time" with one that says the tests share the one platform and stay
independent (own tickers, no global state they assume they own). `webServer` still starts one
platform for every worker, and `globalSetup` still warms the catalog once.

**Screenshots** (`e2e/playwright.screenshots.config.ts`) keep running one at a time: they spread
`base`, so they set `workers: 1` and `fullyParallel: false` explicitly. List pages (`analyses`)
must show the same rows on every run.

No sharding across jobs: with three workers the longest test (about 15 s), plus the platform's
start, sets the step's time.

### 2. The ta-runner image in its own job

New job **`runner-image`**, "ta-runner image", with no `needs`, so it runs beside `build`:

1. checkout, the `Version` step (`scripts/version.sh`), `docker/setup-buildx-action@v4`;
2. the current *ta-runner image* step, moved unchanged (`load: true`, both tags, `gha` cache scope
   `ta-runner`);
3. the current *ta-runner image under the platform's lockdown* step, moved unchanged;
4. `docker save ta-runner:<version> ta-runner:latest -o "$RUNNER_TEMP/ta-runner-image.tar"`, then
   `actions/upload-artifact@v7` as `ta-runner-image`, with `retention-days: 1` and
   `if-no-files-found: error`.

The **`images`** job is renamed "Platform image and Compose smoke test". It gets
`needs: [build, runner-image]`, downloads `ta-runner-image` next to `platform-jar`, and loads it
(`docker load -i …`) before the platform image step. Its platform image and smoke test steps stay
as they are; the ta-runner build and lockdown steps leave it.

**Why `docker save` and not a second build from the `gha` cache:** the smoke test then uses the
very image that passed the lockdown check, and nothing depends on whether the other job has
finished writing the cache yet. The image is about 550 MB, and the artifact upload compresses it.
`runner-image` (about 1 min) finishes well before `build` (2+ min), so `images` does not wait
on it.

`ci-passed`: `needs: [build, runner, runner-image, images, version, e2e]`.

### 3. A shorter e2e setup

In the `e2e` job:

- **Node, pnpm and the e2e packages**: the existing `actions/cache@v6` step gets an `id: node` and
  also caches `e2e/node_modules`. The key stays
  `e2e-${{ runner.os }}-${{ hashFiles('gradle/libs.versions.toml', 'e2e/pnpm-lock.yaml') }}`, with
  its restore key.
- **Skip Gradle on an exact hit**: the *Gradle cache* step (`setup-gradle`) and
  `./gradlew :frontend:nodeSetup :frontend:pnpmSetup` run only when
  `steps.node.outputs.cache-hit != 'true'`. Only an exact hit is safe: after a partial restore an
  older Node.js may be the only one in `.gradle/nodejs`, and `with-node.sh` would pick it.
- **ta-runner's venv**: a new cache step (`id: venv`) before the uv cache, with the paths
  `artifact/ta-runner/.venv` and `~/.local/share/uv/python` (the venv links to the Python that uv
  downloaded, so the venv is useless without it). The key is
  `ta-runner-venv-${{ runner.os }}-${{ hashFiles('artifact/ta-runner/uv.lock', 'artifact/ta-runner/pyproject.toml', 'artifact/ta-runner/.python-version', 'mise.toml') }}`,
  with no restore key, since a partial venv is not worth restoring. The existing *uv cache* step
  runs only when `steps.venv.outputs.cache-hit != 'true'`.
- The install step splits into one step per tool, so each one's time shows in the run. `uv sync
  --frozen` and `pnpm install --frozen-lockfile` always run: on a hit they take about a second, and
  they still prove that the cached tree matches the lock file.
- **e2e formatting moves here** (see 4): the *Type-check* step becomes "Type-check and formatting"
  and also runs `e2e/with-node.sh pnpm run format-check`.

### 4. One Gradle call in the build job

`./gradlew build` already includes every Java `spotlessCheck` (the convention plugin wires it into
`check`, and the root `spotlessCheck` adds the checkstyle-rules build's) and the frontend's Prettier
check (`:frontend:pnpmLint`, `eslint . && prettier --check .`). The only formatting that
`mise run format-check` adds is the e2e Prettier check, and that needs only the e2e packages,
which the e2e job installs anyway.

The *Formatting* and *Build and test* steps become one step, "Formatting, build and test":

```sh
./gradlew --console=plain spotlessCheck :frontend:pnpmLint build
```

The formatting tasks come first on the command line, and they have no dependencies apart from
`pnpmInstall`, so Gradle starts them first. Without `--continue`, a formatting failure stops the
build before any later task starts (tasks already running in parallel finish). A comment in the
workflow says this, and points at `mise run format-check` for local use. `mise run build` is no
longer called in CI. `mise.toml` stays unchanged.

### 5. Header comment of `ci.yml`

The job list gets `runner-image` (the ta-runner image and its lockdown check, beside the build).
`images` changes to the platform image and the Compose smoke test (needs `build` and
`runner-image`). `e2e` gets "in parallel (playwright.config.ts)" and the e2e formatting. `build`
changes to "formatting first, in the same Gradle run". The comment above `e2e` ("Last: it needs the
jar …") is kept.

## Work packages

### WP1: Parallel end-to-end tests

- **Status**: done
- **Depends on**: none
- **Files**: `e2e/playwright.config.ts`, `e2e/playwright.screenshots.config.ts`,
  `e2e/start-platform.sh`, `e2e/tests/support.ts` (comment only)
- **Steps**:
  - [x] `fullyParallel: true`, `workers` from `E2E_WORKERS` (default 3), new comment
  - [x] `workers: 1`, `fullyParallel: false` in the screenshots config, with the reason
  - [x] `--platform.analysis.max-concurrent-runs=4` on the jar's command line in `start-platform.sh`,
        with the reason, and the header comment updated
  - [x] a comment in `support.ts` at `completedAnalysis`: every test uses a ticker of its own
- **Tests**: `mise run e2e` passes locally with the default workers, three times in a row (no
  flakes from the order). `E2E_WORKERS=1 mise run e2e` passes too. `mise run screenshots` still
  produces the same screenshots (no diff in `docs/screenshots/`, or only pixel noise, which is not
  committed).

### WP2: CI workflow

- **Status**: done
- **Depends on**: none
- **Files**: `.github/workflows/ci.yml`
- **Steps**:
  - [x] note: CI runs the e2e tests the way WP1 configures them, but neither package's files depend
        on the other's, so they can be implemented in parallel
  - [x] `build`: one step `./gradlew --console=plain spotlessCheck :frontend:pnpmLint build`,
        with the comment from design 4
  - [x] new `runner-image` job (design 2), and `images` reduced and renamed, `needs: [build, runner-image]`
  - [x] `e2e`: venv cache, conditional uv cache, `setup-gradle` and the Gradle setup only on a miss,
        `e2e/node_modules` in the Node cache, one step per install, e2e format-check (design 3)
  - [x] `ci-passed` needs `runner-image`
  - [x] header comment (design 5)
- **Tests**: the pull request's own CI run is the test.
  - Every job passes, and *CI passed* lists `runner-image` among its results.
  - `runner-image` starts at the same time as `build`.
  - The *End-to-end tests* step takes under about 40 s.
  - A second push with no lock-file change (for example the fix rounds) shows the venv and Node
    caches hit, and *Gradle cache* and the Gradle setup skipped.
  - Put the timings of the first and a warm run (build, images, e2e, total) in the pull request
    body.
  - Lint the workflow with `actionlint` if it is installed locally (it is not a project tool;
    leave it out otherwise).

### WP3: Docs and version

- **Depends on**: WP1, WP2
- **Files**: `README.md`, `docs/coding-convention/backend-java-formatting.md`,
  `docs/coding-convention/README.md`, the three version files (`mise run version:bump minor`)
- **Steps**:
  - [ ] docs as listed below
  - [ ] `mise run version:bump minor` in a commit of its own
  - [ ] `mise run check`
- **Tests**: `mise run check`; `scripts/version.sh check-bump origin/main`.

## Tests

No product code changes, so there are no new unit or integration tests. The proof is:

- `mise run e2e`, run locally, passes in parallel and with `E2E_WORKERS=1`;
- the pull request's CI: every job is green, and the timings are in the pull request body. That
  covers the issue's criteria: e2e step under about 40 s, `runner-image` beside `build`,
  **CI passed** requiring every job, about 3–3.5 min on a warm cache with no version bump. This
  pull request bumps the version, so its first run is cold (#144). Judge the warm time on a later
  push of the pull request, and report the total from there.
- no step is dropped: every check of the old workflow still runs somewhere. The e2e Prettier check
  moves to the e2e job. Java Spotless and frontend Prettier were already part of `build`.

## Docs to update

| What | Where | Change |
|---|---|---|
| Text | `README.md`, "Continuous integration" table | *Backend and frontend*: one Gradle run, `spotlessCheck` and frontend lint first, then `build`. New row *ta-runner image*: built beside the build job, under the platform's lockdown flags, handed to the images job. *Docker images and Compose smoke test* becomes *Platform image and Compose smoke test*. *End-to-end tests*: run in parallel (3 workers), plus the e2e formatting. |
| Text | `README.md`, `mise run format-check` line in the task list | unchanged (still true locally); check that the wording still fits |
| Text | `docs/coding-convention/backend-java-formatting.md`, "Where it runs", CI row | CI runs `spotlessCheck` (and the frontend's Prettier) first in the same Gradle run as the build, `./gradlew spotlessCheck :frontend:pnpmLint build`; no `--continue`, so a formatting failure stops it |
| Text | `docs/coding-convention/README.md`, the "CI runs all of them" bullet | the build job's one Gradle run instead of `mise run format-check` and `mise run build`; the e2e formatting in the e2e job |
| Text | `.github/workflows/ci.yml` header comment | design 5 |
| Screenshot | none | no page changes |

## Out of scope

- Cache misses after a version bump: #144.
- Sharding the e2e tests across jobs: not needed with three workers; revisit when the e2e step
  passes about 40 s again.
- Caching the venv in the `runner` job: it is not on the critical path.
- Changing `mise run format-check`, `mise run build` or `mise run e2e`.
