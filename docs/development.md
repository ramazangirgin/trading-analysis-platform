# Development

## Dev servers

```sh
mise run dev
```

This starts two processes, and keeps them running until Ctrl+C:

| Process | Port | What it does |
|---|---|---|
| Spring Boot backend | 8080 | The API (`/api/...`), the database, starting and stopping analyses (it launches ta-runner) |
| Vite dev server | 5173 | Serves the Vue UI straight from `artifact/frontend/src`, reloads the browser on every change, and forwards `/api/...` to the backend |

Open **http://localhost:5173** (not :8080) while developing. UI changes show up immediately; backend
changes need a restart of `mise run dev`. Nothing listens on 5173 unless `mise run dev` is running —
to just use the app, `mise run run` is enough. `mise run dev` needs a login too, with the same first
admin ([Quick start](user-guide.md#3-log-in)).

## Tasks

```sh
mise run setup         # uv sync for ta-runner (downloads TradingAgents), frontend packages, Git hooks, a container engine if there is none
mise run container-engine  # check Docker or Podman, and install one when there is none (scripts/container-engine.sh)
mise run dev           # backend on :8080 + Vite with hot reload on http://localhost:5173 (starts the local PostgreSQL)
mise run build         # all tests (ArchUnit included), lint (Checkstyle included), dependency checks (Gradle analysis, Knip), and the single jar
mise run run           # the single jar on http://127.0.0.1:8080, rebuilt when something changed (starts the local PostgreSQL)
mise run db            # start the local PostgreSQL container (run and dev do it for you)
mise run db:stop       # stop it; the data stays in the volume
mise run db:reset      # remove the container and its volume: the next start is an empty database
mise run test          # backend + frontend + ta-runner tests
mise run runner-test   # ta-runner lint, import contracts, dependency check (deptry) and tests only
mise run e2e           # end-to-end tests: the jar on a throwaway PostgreSQL container, in Google Chrome with login on (a seeded user), ta-runner replaying a recording
mise run screenshots   # retake the README's screenshots (e2e/screenshots/*.shot.ts), the same way
mise run check         # quick check before pushing: package structure, every domain with persistence has its conventions test, lint, formatting, type-check, dependency declarations (Gradle analysis, Knip, deptry), the agent scripts' and skill helpers' Python tests (`agent:test`, `skill:test`; no other tests)
mise run format        # format every Java (Spotless), frontend and e2e (Prettier) file
mise run format-check  # check the formatting of every Java, frontend and e2e file, as CI does
mise run hooks         # install the Git hooks (lefthook.yml)
mise run api-types     # refresh the frontend's API types from the running backend
mise run docker-build  # the platform and ta-runner Docker images
mise run server:deploy # set up the server in deploy/server/inventory.ini over SSH and deploy the Compose stack on it (Ansible)
mise run server:tunnel # open the deployed UI from this machine: an SSH tunnel, then http://127.0.0.1:8080 (with a domain, https://<domain> works too)
mise run server:check  # ansible-lint and the playbook's syntax, without a server (not part of check)
mise run server:test-setup  # the server setup scripts against a throwaway Ubuntu container (needs Docker or Podman)
mise run server:test   # the whole server deployment with THIS machine as the server: throwaway machines only (CI)
mise run ci:test-changes  # the tests of scripts/ci-changes.sh, which decides the CI jobs a change needs
mise run gradle :backend:test   # Gradle with --console=plain; the arguments after the task name go to Gradle
mise run agent:stats 244       # token, tool and context statistics of an issue's agent transcripts (docs/agentic-development.md)
mise run version       # the platform version
mise run version:bump minor   # raise it (major, minor or patch) in every file; see Versioning and releases
```

With `AGENT_QUIET=1` (set for the agents) `check`, `format`, `format-check`, `runner-test` and `gradle`
print one `ok:` line per command, or on a failure an excerpt and the path of the full log in
`build/quiet/`.

`run`, `dev`, `build`, `test`, `e2e` and `screenshots` need Docker or Podman (a PostgreSQL container;
the backend tests start theirs through Testcontainers; on a Podman machine the build points
Testcontainers' cleanup container at the machine's own socket, so no local setting is needed). `mise tasks` lists them. Tasks run with the pinned Java on `PATH` and `JAVA_HOME` set, so nothing
needs overriding; with [mise activated](https://mise.jdx.dev/getting-started.html#activate-mise) in
your shell, plain `./gradlew` and `uv` in this directory use the same versions.

## Coding conventions

Where each kind of class, component or module belongs, and what may import what, is written down in
[docs/coding-convention/](coding-convention/README.md) and checked by the build (ArchUnit,
ESLint, import-linter) and by CI. The backend's Java code is also checked by Checkstyle
([rules and suppressions](coding-convention/backend-java-checkstyle.md)) and formatted with
Spotless and Palantir Java Format ([formatting](coding-convention/backend-java-formatting.md));
the frontend is formatted with Prettier. The dependency declarations are checked too: each part
declares what it uses and nothing else ([dependency hygiene](coding-convention/repository-dependency-hygiene.md)).
Git hooks run the static checks on staged files before a
commit; the backend's formatting check (changed files only), Checkstyle and ArchUnit rules run in
that hook too when Java files are staged, and CI checks the formatting of every file.
`mise run check` runs every structure check, lint, formatting check and the type-check by hand;
`mise run format` fixes the formatting.

## Before pushing

- `mise run test` must pass.
- After changing the REST API: with `mise run dev` running (restarted after the change), run
  `mise run api-types`. It saves the backend's OpenAPI spec as `artifact/frontend/openapi.json` and
  regenerates `artifact/frontend/src/shared/api/schema.d.ts`; commit both.

## ta-runner

```sh
cd artifact/ta-runner
uv sync
uv run pytest                       # unit, SIGTERM and upstream contract tests (no LLM calls)
uv run python -m ta_runner version

# Real analysis: needs the provider key in the environment (e.g. OPENAI_API_KEY).
# Edit example-spec.json (ticker, date, provider, models) first.
uv run python -m ta_runner run --spec example-spec.json --out /tmp/ta-run
```

Events stream to stdout as JSONL and are copied to `<out>/events.jsonl`; reports land under
`~/.tradingagents/logs/<TICKER>/<DATE>/reports/` (override with `TRADINGAGENTS_RESULTS_DIR`).

`TA_RUNNER_RECORD=<dir>` also records a run; `TA_RUNNER_REPLAY=<dir>` plays a recording back
through the same code path without calling an LLM (`TA_RUNNER_REPLAY_SPEED` speeds it up). The
end-to-end tests replay [`tests/fixtures/replay-run`](../artifact/ta-runner/tests/fixtures/replay-run).

In a container the spec comes from an environment variable instead of a file:

```sh
docker run --rm -e TA_RUNNER_SPEC="$(cat example-spec.json)" -e OPENAI_API_KEY \
  ta-runner:latest run --spec-env TA_RUNNER_SPEC --out /tmp/run
```

## Testing the Docker runner

`DockerRunnerAdapterTest` and `DockerEngineInfoAdapterTest` run against a real engine at
`DOCKER_HOST` (Docker, or Podman's socket) and are skipped when there is none; the latter also
needs the `ta-runner:latest` image. To run the platform on your machine with the Docker runner
instead of the local venv: `PLATFORM_RUNNER=docker mise run run` (with a `tradingagents-data`
volume, or `TA_RUNNER_DATA_MOUNT` set to a folder the engine can mount).
