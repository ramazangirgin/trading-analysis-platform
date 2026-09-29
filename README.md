# TradingAgents Platform

A web platform for running and managing [TradingAgents](https://github.com/TauricResearch/TradingAgents)
analyses without modifying its code. See [PLAN.md](PLAN.md) for the design and roadmap and
[docs/event-protocol.md](docs/event-protocol.md) for the runner contract.

| Artifact | Path | Stack |
|---|---|---|
| Platform (backend + UI, one jar) | `artifact/backend`, `artifact/frontend` | Java 25, Spring Boot 4.1 · Vue 3.5, Vite, Naive UI |
| Runner (installed into the TradingAgents runtime) | `artifact/ta-runner` | Python 3.12, uv, TradingAgents `v0.5.1` |

## Prerequisites

- A JDK 17+ to launch Gradle. Java 25 itself is downloaded by the Gradle toolchain.
- [uv](https://docs.astral.sh/uv/) for `ta-runner` (it downloads Python 3.12).
- Nothing else: Node.js and pnpm are downloaded by the build.

## Getting started

```sh
make setup      # uv sync for ta-runner (downloads TradingAgents), frontend packages
make dev        # backend on :8080 + Vite with hot reload on http://localhost:5173
make build      # all tests (ArchUnit included), lint, and the single jar
make run        # the jar on http://127.0.0.1:8080 (needs Java 25: make run JAVA=/path/to/java)
make test       # backend + frontend + ta-runner tests
```

`make help` lists the targets; `JAVA_HOME`, `UV` and `JAVA` can be overridden on the command line.

Provider API keys: add them on the **Settings** page (stored in
`~/.tradingagents-platform/secrets.env`, owner-only), or keep them in
`artifact/ta-runner/.env`, which the platform reads but never writes.

Where things live:

| Path | What |
|---|---|
| `~/.tradingagents-platform/platform.db` | Analyses and presets (SQLite) |
| `~/.tradingagents-platform/runs/<id>/` | `spec.json`, `events.jsonl` (replayed to the UI), `run.log` (runner stderr) |
| `~/.tradingagents/logs/` | TradingAgents' own reports, shared with its CLI; runs found here are imported (read-only) |

Override with `PLATFORM_HOME`, `TRADINGAGENTS_HOME` or `TRADINGAGENTS_RESULTS_DIR`.

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
