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

## Platform

```sh
./gradlew build                     # all tests (ArchUnit included), lint, and the single jar
java -jar artifact/backend/build/libs/backend-0.0.1-SNAPSHOT.jar   # http://127.0.0.1:8080
```

Dev mode, with UI hot reload:

```sh
./gradlew :backend:bootRun          # API on :8080
cd artifact/frontend && pnpm dev    # UI on :5173, proxies /api and /actuator to :8080
```

`pnpm` for dev mode: `corepack enable`, or use the copy the build downloaded
(`artifact/frontend/.gradle/pnpm/`).

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
