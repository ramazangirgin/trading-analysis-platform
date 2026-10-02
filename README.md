# TradingAgents Platform

[![CI](https://github.com/ramazangirgin/trading-analysis-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/ramazangirgin/trading-analysis-platform/actions/workflows/ci.yml)

A web platform for running and managing [TradingAgents](https://github.com/TauricResearch/TradingAgents)
analyses without modifying its code: start an analysis from the browser, watch the agents work live,
then read the decision, the reports and the debates, and export them. See [PLAN.md](PLAN.md) for the design and
roadmap and [docs/event-protocol.md](docs/event-protocol.md) for the runner contract.

![An analysis: agent pipeline and the final decision](docs/screenshots/run-decision.png)

| Artifact | Path | Stack |
|---|---|---|
| Platform (backend + UI, one jar) | `artifact/backend`, `artifact/frontend` | Java 25, Spring Boot 4.1 · Vue 3.5, Vite, Naive UI |
| Runner (installed into the TradingAgents runtime) | `artifact/ta-runner` | Python 3.12, uv, TradingAgents `v0.5.1` |
| Docker images and Compose setup | `deploy/`, `artifact/ta-runner/Dockerfile` | Temurin 25 JRE · PostgreSQL 18 · docker-socket-proxy |

Two ways to run it: on your machine with `mise run run` ([Quick start](#quick-start)), or with
Docker Compose, where each analysis runs in its own container and the data is kept in PostgreSQL
([Run with Docker Compose](#run-with-docker-compose)).

## Quick start

### 1. Prerequisites

- [mise](https://mise.jdx.dev/) — it installs the rest: Java 25 (Temurin) and
  [uv](https://docs.astral.sh/uv/), pinned in [`mise.toml`](mise.toml), into its own directory.
  Install it with `curl https://mise.run | sh` (or `brew install mise`).
- An API key for at least one LLM provider (OpenAI, Anthropic, Google, DeepSeek, xAI, Mistral,
  Groq, …), or a local [Ollama](https://ollama.com/), which needs no key.
- Nothing else: Node.js and pnpm are downloaded by the build, Python 3.12 by uv.

### 2. Start the platform

```sh
git clone https://github.com/ramazangirgin/trading-analysis-platform.git
cd trading-analysis-platform
mise trust && mise install   # once: Java 25 and uv for this repository
mise run setup               # once: ta-runner (downloads TradingAgents) and the UI packages
mise run run                 # builds the app if needed and serves it on :8080
```

Open **http://127.0.0.1:8080**. One process serves both the UI and the API; keep the terminal open
while you use it, and stop it with Ctrl+C. The first start takes a few minutes (Gradle's
dependencies, Node, building the UI and the backend); after that it starts in seconds, and after a
`git pull` it rebuilds only what changed. Analyses, presets and keys live on disk
([Where things live](#where-things-live)), so they survive restarts.

If the platform stops while an analysis runs (a crash, a `kill`), the analysis keeps going; the
restarted platform follows it again, or records its result if it finished meanwhile, and starts
queued analyses again. Ctrl+C in the terminal stops the running analyses too.

Changing the code instead? See [Development](#development) — it runs the UI with hot reload.

### 3. Add an API key

Open **Settings**, paste the key next to its provider and press **Save**. Keys are stored in
`~/.tradingagents-platform/secrets.env` (owner-only) and are never shown back. A key already in
`artifact/ta-runner/.env` is picked up too (read-only).

![Settings: provider API keys](docs/screenshots/settings.png)

### 4. Start an analysis

Open **New analysis** and fill in:

| Field | What to enter |
|---|---|
| Ticker | Any Yahoo Finance symbol: `NVDA`, `THYAO.IS`, `BTC-USD` (crypto pairs switch the asset type automatically) |
| Analysis date | Defaults to the last weekday; future dates are rejected |
| Analysts | Market, Sentiment, News, Fundamentals — fewer analysts means a faster, cheaper run |
| Provider, models | A deep-thinking model (research/portfolio managers) and a quick-thinking one (everyone else); you can type a model id that is not listed |
| Debate rounds | 1 is enough to start; every extra round adds LLM calls |
| Report language | The language of the reports and the decision; follows the UI language by default |

Press **Start analysis**. **Save as preset** keeps the provider, model and analyst choices for next
time; **Settings** lists the presets with what each holds, and renames or deletes them.

![New analysis form](docs/screenshots/new-analysis.png)

![Presets in Settings](docs/screenshots/presets.png)

### 5. Watch it and read the results

The analysis page updates live: the agent pipeline (analysts → research debate → trader → risk
debate → portfolio manager), a feed of agent messages and tool calls, the runner log, and token
counts. A full run with all four analysts can take 20 minutes or more, depending on the model and
the debate rounds (the example above: 41 LLM calls, ~1.1M input tokens, 18 minutes). **Stop** ends it; **Run again**
repeats it with the same settings.

![A running analysis: pipeline status and the live feed](docs/screenshots/run-live.png)

When it finishes, the decision card shows the portfolio manager's rating (Buy / Overweight / Hold /
Underweight / Sell) with its reasoning, and the tabs hold everything behind it:

| Reports — one per analyst and manager | Debates — bull vs. bear, then the risk debate |
|---|---|
| ![Reports tab](docs/screenshots/run-reports.png) | ![Debates tab](docs/screenshots/run-debates.png) |

![Stats tab: calls, tokens, cost, duration and the run's settings](docs/screenshots/run-stats.png)

The cost is worked out per LLM call from the provider's published prices
([LLM prices](#llm-prices)); a model without a known price shows no cost.

The market report opens with a price chart — close, 10 EMA, 50 SMA and 200 SMA with volume, over
3 months to a year up to the analysis date, from the price data TradingAgents itself downloaded —
and report tables that are series over periods (quarterly or yearly figures) get a chart beside them:

![A fundamentals table with its chart](docs/screenshots/run-table-chart.png)

### 6. Past analyses

**Analyses** lists every run with its analysis date and start time, decision, model and duration,
filterable by ticker and status.
Runs made outside the platform are imported (read-only) and show up by themselves within about
10 seconds, as the data dir is watched; **Scan data dir** forces a rescan:

- reports the TradingAgents CLI (or another UI) left in `~/.tradingagents/logs`;
- a third-party UI's run history, `~/.tradingagents/runs.json`: its failed runs with their errors,
  and the models, token counts, cost and timing of the finished ones;
- deep-analysis write-ups, `~/.tradingagents/reports/<TICKER>_deep_<DATE>.md`, as a
  *Deep analysis* section of that ticker and date's report.

A run whose report files are still being written is not shown as failed; it appears once it
finishes.

![Analyses list](docs/screenshots/analyses.png)

The **Dashboard** shows the running analyses and the latest decisions at a glance.

![Dashboard](docs/screenshots/dashboard.png)

### 7. Reports and export

**Reports** gathers every finished report in one place: the list of tickers and dates on the left,
the report's contents in the middle (each section with its headings, then the debates), and the
report itself with its charts on the right. The data dir keeps one report per ticker and date, so
when a ticker and date ran several times, this is the newest run's report.

**Markdown**, **HTML** and **PDF** export the open report: the HTML file is a single page that
opens anywhere, and PDF goes through the browser's print dialog (*Save as PDF*). On a phone the page
folds into one column, with the list as a picker and the contents folded above the report.

![Reports: list, contents and the report](docs/screenshots/reports.png)

### Language

The UI is in English. Reports are written in the language chosen in the New Analysis form's
"Report language" field; the analysis page shows it as a tag.

## Run with Docker Compose

For a server (or any machine with Docker or Podman): the platform, PostgreSQL and a socket proxy
run as containers, and every analysis runs in a container of its own that is removed afterwards.

```
browser ──▶ 127.0.0.1:8080 ──▶ platform ──▶ postgres
                                  │
                                  └──▶ docker-socket-proxy ──▶ Docker / Podman engine
                                                                  │  one per analysis
                                                                  ▼
                                                          ta-runner container
```

**Needs:** Docker with Compose v2, or Podman with `docker compose` pointed at its socket; and
[mise](https://mise.jdx.dev/) to build the images.

```sh
cp deploy/.env.example deploy/.env      # set PLATFORM_DATA and POSTGRES_PASSWORD
mise run docker-build                   # builds trading-analysis-platform:0.0.1 and ta-runner:0.1.0
docker compose -f deploy/docker-compose.yml up -d
```

Open **http://127.0.0.1:8080** (or the `PLATFORM_PORT` you set) and add an API key under
**Settings**, as in the [Quick start](#3-add-an-api-key). `docker compose -f deploy/docker-compose.yml
down` stops everything; `up -d` brings it back with all data.

**Data** stays on the machine, in the folder named by `PLATFORM_DATA` (an absolute path):

| Folder | What |
|---|---|
| `postgres/` | The database: analyses and presets. Survives restarts, `down` and image updates. |
| `platform/` | `secrets.env` (API keys, owner-only), `runs/<id>/` (events and runner logs), optional `prices.json` |
| `tradingagents/` | TradingAgents' reports, price cache and memory log, written by the analysis containers |

Back up the whole folder; for the database alone, `docker compose -f deploy/docker-compose.yml exec
postgres pg_dump -U platform platform > platform.sql`.

**How analyses run:** the platform asks the engine, through docker-socket-proxy, for one
`ta-runner` container per analysis: read-only root filesystem, no Linux capabilities, a non-root
user, 2 GB of memory and 2 CPUs, and only the `tradingagents/` folder mounted. The proxy lets the
platform create, start, stop and remove containers and nothing else (no `exec`, images, volumes or
networks). If the platform restarts during an analysis, the container keeps running and the
platform picks it up again.

**Notes**

- **SELinux** (Fedora, RHEL, Podman machines): the Compose file labels its folders with `:z`, and
  the proxy runs without SELinux labelling so it can reach the socket.
- **Podman on macOS:** `PLATFORM_DATA` must be a folder the Podman machine can see. A machine
  created without `--volume` shares no macOS folders; use a path inside the machine (e.g.
  `/var/home/core/trading-analysis-platform`) or recreate it with `podman machine init --volume
  $HOME:$HOME`. `DOCKER_SOCKET` is the socket path inside the machine (`/var/run/docker.sock` on a
  rootful one).
- **Exposing it beyond localhost** needs authentication and HTTPS first (planned, see
  [PLAN.md](PLAN.md) §7); the port is published on 127.0.0.1 only.
- The first start takes about a minute (longer under Podman's VM on macOS).

## Where things live


| Path | What |
|---|---|
| `~/.tradingagents-platform/platform.db` | Analyses and presets (SQLite) |
| `~/.tradingagents-platform/runs/<id>/` | `spec.json`, `events.jsonl` (replayed to the UI), `run.log` (runner stderr) |
| `~/.tradingagents-platform/prices.json` | Optional: your own LLM prices, over the ones ta-runner ships (see below) |
| `~/.tradingagents/logs/` | TradingAgents' own reports, shared with its CLI; runs found here are imported (read-only) |

Override with `PLATFORM_HOME`, `TRADINGAGENTS_HOME` or `TRADINGAGENTS_RESULTS_DIR`. With Docker
Compose everything is under `PLATFORM_DATA` instead ([Run with Docker Compose](#run-with-docker-compose)).

### LLM prices

A run's cost is worked out per LLM call from
[`artifact/ta-runner/ta_runner/pricing/prices.json`](artifact/ta-runner/ta_runner/pricing/prices.json): DeepSeek,
OpenAI, Anthropic and Google, read from their official pricing pages on 2026-09-30 (peak hours,
long-context tiers and cached input included). A model without a price shows no cost rather than
a guess. To add or correct prices, create `~/.tradingagents-platform/prices.json` in the same shape;
its models replace the shipped ones one by one:

```json
{"providers": {"ollama": {"models": {"qwen3:latest": [{"input": 0, "output": 0}]}},
               "openai": {"models": {"gpt-5.6": [{"input": 2.0, "cached_input": 0.2, "output": 12.0}]}}}}
```

Prices are USD per 1M tokens. New runs pick the file up; finished runs keep their cost.

## Development

### Dev servers

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
to just use the app, `mise run run` is enough.

### Tasks

```sh
mise run setup         # uv sync for ta-runner (downloads TradingAgents), frontend packages, Git hooks
mise run dev           # backend on :8080 + Vite with hot reload on http://localhost:5173
mise run build         # all tests (ArchUnit included), lint, and the single jar
mise run run           # the single jar on http://127.0.0.1:8080, rebuilt when something changed
mise run test          # backend + frontend + ta-runner tests
mise run runner-test   # ta-runner lint, import contracts and tests only
mise run check         # quick check before pushing: package structure, lint, type-check (no tests)
mise run hooks         # install the Git hooks (lefthook.yml)
mise run api-types     # refresh the frontend's API types from the running backend
mise run docker-build  # the platform and ta-runner Docker images
```

`mise tasks` lists them. Tasks run with the pinned Java on `PATH` and `JAVA_HOME` set, so nothing
needs overriding; with [mise activated](https://mise.jdx.dev/getting-started.html#activate-mise) in
your shell, plain `./gradlew` and `uv` in this directory use the same versions.

### Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push to any branch (main
included), on every pull request, and on demand (*Run workflow* on the Actions tab):

| Job | What |
|---|---|
| Backend and frontend | `mise run build`: every Gradle test (ArchUnit, the Docker runner against the runner's own Docker), frontend lint and tests, the jar |
| ta-runner | `mise run runner-test`: ruff, import-linter (package structure) and pytest, upstream contract tests included |
| Docker images and Compose smoke test | Both images (GitHub's build cache), the runner image under the platform's lockdown flags, then [`deploy/smoke-test.sh`](deploy/smoke-test.sh): the Compose stack comes up, an analysis runs in its own container, and the data survives a database restart and `down`/`up` |

A last job, **CI passed**, succeeds only if all of them did. New jobs go into its `needs` list, so
the rules on `main` never have to change. Those rules (the repository ruleset "main"):

- Changes reach `main` only through pull requests; no direct or force pushes, and `main` cannot be
  deleted.
- A pull request merges once **CI passed** is green, its branch is up to date with `main`, and a
  code owner has approved it ([`.github/CODEOWNERS`](.github/CODEOWNERS): the admins).
- Admins can merge their own pull requests without an approval, and can merge one whose checks
  failed (GitHub's "bypass" option on the pull request). Nothing merges on its own: auto-merge is off.

Tools come from `mise.toml`, as locally. A newer push to the same branch cancels the run in progress
(not on main). The smoke test also runs locally: `deploy/smoke-test.sh /absolute/path/for/data` after
`mise run docker-build`.

### Coding conventions

Where each kind of class, component or module belongs, and what may import what, is written down in
[doc/coding-convention/](doc/coding-convention/README.md) and checked by the build (ArchUnit,
ESLint, import-linter) and by CI. Git hooks run the static checks on staged files before a commit;
the backend's ArchUnit rules run in that hook too when Java files are staged. `mise run check` runs
every structure check, lint and the type-check by hand.

### Before pushing

- `mise run test` must pass.
- After changing the REST API: with `mise run dev` running (restarted after the change), run
  `mise run api-types`. It saves the backend's OpenAPI spec as `artifact/frontend/openapi.json` and
  regenerates `artifact/frontend/src/shared/api/schema.d.ts`; commit both.

### ta-runner

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

In a container the spec comes from an environment variable instead of a file:

```sh
docker run --rm -e TA_RUNNER_SPEC="$(cat example-spec.json)" -e OPENAI_API_KEY \
  ta-runner:0.1.0 run --spec-env TA_RUNNER_SPEC --out /tmp/run
```

### Testing the Docker runner

`DockerRunnerAdapterTest` and `DockerEngineInfoAdapterTest` run against a real engine at
`DOCKER_HOST` (Docker, or Podman's socket) and are skipped when there is none; the latter also
needs the `ta-runner:0.1.0` image. To run the platform on your machine with the Docker runner
instead of the local venv: `PLATFORM_RUNNER=docker mise run run` (with a `tradingagents-data`
volume, or `TA_RUNNER_DATA_MOUNT` set to a folder the engine can mount).

## License

Licensed under the [Apache License 2.0](LICENSE). Copyright 2026 Ramazan Girgin.

[TradingAgents](https://github.com/TauricResearch/TradingAgents), which `ta-runner` installs, is
also Apache-2.0-licensed and remains under its authors' copyright.
