# TradingAgents Platform — Detailed Plan

> Status: **v0.11** (2026-09-28) · All open decisions resolved (✅). Phase 0 done; next step: Phase 1.

### Decisions
| # | Topic | Decision |
|---|---|---|
| D1 | Target environment | ✅ Mac first (native), then a Linux server (Docker Compose) |
| D2 | Execution model | ✅ `RunnerPort` with two adapters: Phase 1 local process, Phase 2 Docker |
| D3 | UI component library | ✅ Naive UI |
| D4 | Upstream source | ✅ GitHub tag, pinned (`v0.5.1`; `v0.4.2` does not exist upstream, so Phase 0 pinned the latest release) |
| D5 | UI language | ✅ Turkish + English (vue-i18n), **Turkish by default** |
| D6 | Number of users | ✅ Single user for now; data model designed so it can be extended to multiple users later |
| D7 | Existing data | ✅ `~/.tradingagents` is imported into the platform (see §3.6) |
| D8 | Docker model on the server | ✅ One container per analysis (Docker runner adapter, via the Docker socket) |
| D9 | Phase ordering | ✅ Nothing pulled forward from Phase 3; roadmap stays as is |
| D10 | Dependency boundary | ✅ Platform (backend + frontend) has **no dependency on TradingAgents code**; only the separately installed `ta-runner` imports it (§3.2) |
| D11 | Backend stack & structure | ✅ Java + Spring Boot, following [job-radar](https://github.com/ramazangirgin/job-radar): **a modular monolith with a backend-for-frontend (BFF) in front and hexagonal domains behind it. All code lives under `tr.girgin.backend.trading.analysis.platform`** (§3.4) |
| D12 | Packaging | ✅ Frontend and backend ship as **one artifact / one container**: the Vue build is bundled into the Spring Boot jar and served by it. Code lives in `artifact/backend` and `artifact/frontend`; `artifact/ta-runner` is the third, separate artifact (§3.9) |

## 1. Goals and Scope

A management platform that runs on top of the [TauricResearch/TradingAgents](https://github.com/TauricResearch/TradingAgents) framework **without modifying its code**:

- Start analyses from a web UI (ticker, date, analyst selection, depth, provider/model, language)
- List running / past analyses, stop them, re-run them
- Live stream: agent pipeline status, agent messages, tool calls, tokens/cost
- Log viewing (live tail + history), report reader (markdown), export (md/html/pdf)
- Settings: API key management, provider/model lists, presets, health checks

Reference UX: [TheLocalLab/TradingAgents-GUI](https://github.com/TheLocalLab/TradingAgents-GUI) (Analyze / Reports / Chat / Health tabs, visual pipeline, decision card).

### Out of scope (for now)
- Real order placement / broker integration
- Multi-user support (D6: first release is single-user; the `analyses`/`presets` tables are designed so an `owner_id` can be added later, but there is no user/role management for now)

---

## 2. Current State Analysis (inspected local clones)

### 2.1 Upstream `TradingAgents` (v0.4.x, `be952b8`)
| Topic | Finding | Impact on the platform |
|---|---|---|
| Entry point | `tradingagents` = Typer CLI (`cli.main:app`), **interactive** (questionary prompts for ticker, date, analysts) | **No** HTTP API and no headless mode. The Docker image only runs the interactive CLI → a runner is needed |
| Library API | `TradingAgentsGraph(selected_analysts, debug, config, callbacks)` → `.propagate(ticker, date, asset_type)` or `.graph.stream(...)` | `ta-runner` uses `graph.stream` + LangChain `callbacks` for the live event stream |
| Config | `DEFAULT_CONFIG` + `TRADINGAGENTS_*` env overrides (`default_config.py`) | Run parameters can be passed via env and/or a config dict |
| Outputs | `~/.tradingagents/logs/<TICKER>/<DATE>/reports/{1_analysts..5_portfolio, complete_report.md}`, `TradingAgentsStrategy_logs/full_states_log_<date>.json`, `memory/trading_memory.md`, `cache/` | The backend reads and indexes this directory (plain files, no code dependency) |
| Checkpoint | `checkpoint_enabled` + SQLite (`begin_checkpoint`/`checkpoint_input`/`end_checkpoint`) | "Resume where it left off" comes for free |
| Docker | `python:3.12-slim`, `ENTRYPOINT ["tradingagents"]`, volume `/home/appuser/.tradingagents` | The image is reused as the base of the runner image |

### 2.2 `TradingAgents-GUI` (TheLocalLab fork)
- Flask + a single `index.html` (vanilla JS), mixed into the upstream code as a **fork** (the model we want to avoid).
- Good ideas (to adopt): run manager + event queue + **SSE** stream, `agent_map` (node → agent mapping), live cost calculation, run history persistence, report export, presets, health tab, first-run wizard.
- Weaknesses (to avoid):
  - Analyses run as **threads in the same process** → Stop is "cooperative" (only between chunks), a crash affects the whole server, weak parallelism.
  - Builds `init_state` by hand → skips upstream's `past_context` / `instrument_context` / memory-log logic (drifts from upstream).
  - Being a fork of upstream makes pulling updates hard.

### 2.3 Reference backend: `job-radar`
Spring Boot 4.1.1, Java 25, Gradle 9.7.1 (Kotlin DSL, version catalog, convention plugins in `build-logic/`), MapStruct 1.6.3, ArchUnit 1.5.0. Each architectural layer is its own Gradle subproject under `artifact/backend/`, so dependency rules are enforced by the compile classpath first and by `ArchitectureTest` second. This plan copies that structure, build setup and rule set 1:1 and adds the platform's domains (§3.4).

### 2.4 Local environment notes
- Docker 29.8 installed.
- JDK: only 7/8/17 installed → Java 25 is provisioned by the Gradle toolchain (foojay resolver), no manual install needed.
- System Python is 3.9 (upstream needs ≥3.10); `ta-runner` gets its own `uv`-managed Python 3.12 venv. `uv` installed in Phase 0 (`pip install --user uv`; the local Homebrew is too old to have it).
- `~/.tradingagents` contains real past runs (NVDA, MU, GOOG…) → ready-made data for the first import test.

---

## 3. Architecture

### 3.1 Overview

```
┌──────────────────────┐   REST + SSE    ┌──────────────────────────────────────────────┐
│  Frontend            │ ──────────────▶ │  Backend (Spring Boot, modular monolith)     │
│  Vue 3.5 + Vite + TS │ ◀────────────── │  tr.girgin.backend.trading.analysis.platform  │
│  Pinia + Naive UI    │                 │  ┌────────────────────────────────────────┐  │
└──────────────────────┘                 │  │ bff  (controllers → delegates)         │  │
                                         │  └───────────────┬────────────────────────┘  │
                                         │  ┌───────────────▼────────────────────────┐  │
                                         │  │ orchestration  │ domain.analysis        │  │
                                         │  │                │ domain.report          │  │
                                         │  │                │ domain.catalog         │  │
                                         │  │                │ domain.settings        │  │
                                         │  └───────────────┬────────────────────────┘  │
                                         │        outbound adapters (runner, persistence,│
                                         │        eventstore, datadir, secrets, export)  │
                                         └──────────┬───────────────────────────────────┘
                                                    │ spec.json in / JSONL events out
                              ┌─────────────────────┴──────────────────┐
                              ▼                                        ▼
                  adapter.runner (process)                  adapter.runner (docker)
                  ProcessBuilder + uv venv                  docker-java, container per run
                              │                                        │
                              ▼                                        ▼
                     ┌───────────────────────────────────────────────────┐
                     │  ta-runner (Python, lives in the TradingAgents     │
                     │  runtime) — import tradingagents (pinned tag)      │
                     │  graph.stream → JSONL events on stdout             │
                     └───────────────────────────────────────────────────┘
                                         │
                                         ▼
                           shared data dir (~/.tradingagents)
                           reports / full_states / memory / cache
```

### 3.2 Key design decisions

1. **The platform (backend + frontend) has zero dependency on TradingAgents code.** The Java backend cannot import Python anyway; it only knows three things: the run spec it sends (`spec.json`), the event stream it receives (JSONL), and the output files in the data dir.
2. **`ta-runner` is the only thing that touches TradingAgents.** Upstream has no HTTP API and its CLI prompts interactively, so it cannot be driven headlessly. A thin Python runner (§3.3) is installed **into the TradingAgents runtime** (its venv or Docker image), not into the platform. It is a separate deliverable with its own versioning, released against a pinned upstream tag.
3. **Zero changes to upstream.** Upstream is consumed as-is: `pip install "tradingagents @ git+https://github.com/TauricResearch/TradingAgents@v0.5.1"` or the upstream Docker image, with `ta-runner` added on top.
4. **Each analysis is a separate process/container.** Real Stop (SIGTERM → SIGKILL), isolation, parallel runs, independence from backend crashes.
5. **Runner ↔ platform contract = spec in, JSONL events out** (one JSON object per line on stdout). Wherever the runner runs (local, Docker, K8s), the backend reads it the same way. The same events are also written to `run_dir/events.jsonl` → past events survive a backend restart and the UI can replay them. The contract is versioned (`"v": 1`) and documented in `docs/event-protocol.md`.
6. **Where the runner runs is an outbound-adapter concern.** The `analysis` domain defines a `RunnerPort`; `adapter.runner` provides a local-process and a Docker implementation, selected by property (see §4).

**Rejected alternative — driving the upstream CLI directly** (pty automation of the questionary prompts, parsing rich terminal output): no runner code, but brittle against any prompt/UI change upstream and yields no structured events.

### 3.3 `ta-runner` (Python, ~300-500 lines) — lives in the TradingAgents runtime, not in the platform
A **non-interactive** single-run executor that uses upstream as a library. It is the only component that imports `tradingagents`; the backend just launches it (subprocess or container) and reads its output.

```
python -m ta_runner run     --spec /runs/<id>/spec.json --out /runs/<id>
python -m ta_runner catalog                       # providers / models / analysts as JSON
python -m ta_runner version                       # runner + upstream version as JSON
```
- `spec.json`: ticker, date, asset_type, analysts, provider, deep/quick model, debate/risk rounds, output_language, checkpoint, vendor overrides.
- Config: `DEFAULT_CONFIG.copy()` + spec overrides (with type coercion).
- The initial state is built **the way upstream does it**: `memory_log.get_past_context`, `resolve_instrument_context`, `propagator.create_initial_state`, `begin_checkpoint`/`checkpoint_input`/`end_checkpoint` → same behavior as the CLI.
- Each node chunk from `graph.graph.stream(...)` is processed and turned into events.
- LangChain `BaseCallbackHandler` → LLM call count, tokens in/out, tool calls, cost.
- On completion: `save_reports()` / `write_report_tree`, an `_log_state` equivalent, `memory_log.store_decision`, `process_signal` → decision (Buy/Overweight/Hold/Underweight/Sell/REVIEW).
- SIGTERM is caught → a `run_finished{status: stopped}` event is written and the process exits cleanly.
- Exit codes: 0 success, 2 stopped, 1 error.

> ⚠️ Risk: `ta-runner` relies on some *semi-internal* upstream APIs (`propagator`, `memory_log`, `_log_state`). Mitigation: keep all these calls in a single `compat.py` module + **contract tests** that run on every upstream version upgrade (see §9).

#### Event protocol (JSONL, versioned)
```json
{"v":1,"ts":"2026-09-28T10:00:01Z","run_id":"r_ab12","seq":17,"type":"agent_status","agent":"Market Analyst","status":"in_progress"}
```
| type | payload |
|---|---|
| `run_started` | spec summary, upstream version, model info |
| `agent_status` | agent, `pending/in_progress/completed/error` |
| `message` | agent, role, content (truncated; full text in file) |
| `tool_call` / `tool_result` | tool name, arguments, duration, error |
| `report_section` | section key (`market_report`, `investment_plan`…), markdown |
| `debate` | `bull/bear/aggressive/conservative/neutral/judge`, round |
| `stats` | llm_calls, tool_calls, tokens_in/out, cost_usd, elapsed_s |
| `log` | level, logger, message (Python `logging` → JSON handler) |
| `decision` | rating, raw final_trade_decision |
| `run_finished` | status `completed/stopped/error`, error, traceback |

Runner output is **untrusted input** (same stance as job-radar's model output): the backend parses each line into a permissive record, then mappers normalize it into the closed domain model (unknown `type` → kept as a raw `log` event, unknown rating → `REVIEW`, oversized payloads truncated).

### 3.4 Backend (Java 25, Spring Boot 4.1.1) — job-radar structure

**A modular monolith with a backend-for-frontend (BFF) in front and hexagonal domains behind it. All code lives under `tr.girgin.backend.trading.analysis.platform`.**

#### Stack
| | |
|---|---|
| Java | 25 (LTS), via Gradle toolchain |
| Build | Gradle 9.7.1, Kotlin DSL, version catalog (`gradle/libs.versions.toml`), convention plugins (`build-logic/`) |
| Framework | Spring Boot 4.1.1 (webmvc, actuator, virtual threads enabled for SSE and process I/O) |
| Persistence | Spring Data JDBC + Flyway; SQLite by default (single file next to the data dir), PostgreSQL via profile on the server |
| JSON | Jackson (JSONL parsing in the runner adapter) |
| Docker | docker-java (Phase 2, Docker runner adapter) |
| Mapping | MapStruct 1.6.3 (`unmappedTargetPolicy=ERROR`) |
| Architecture tests | ArchUnit 1.5.0 |
| API docs | springdoc-openapi 3.1.1 (`starter-webmvc-api`, the Boot 4 line), spec served at `/api/openapi` → used to generate the frontend TS client |
| Static UI | the frontend build is served from the jar's `classpath:/static` (D12), with an SPA fallback to `index.html` for client-side routes |

#### Package layout
```
tr.girgin.backend.trading.analysis.platform
├── TradingPlatformApplication
├── bff
│   ├── controller.api     REST/SSE controllers, delegate interfaces, web DTOs (controller.api.model)
│   └── delegate.impl      delegate implementations: map DTOs <-> domain, call inbound ports
├── orchestration          use cases spanning more than one domain
└── domain.<domain>
    ├── core
    │   ├── model              domain model
    │   ├── inbound            use-case interfaces (inbound ports)
    │   ├── outbound.<type>    interfaces to the outside world (outbound ports)
    │   └── service            inbound port implementations
    └── adapter.<type>         outbound port implementations
```

#### Domains
| Domain | Responsibility | Inbound ports (examples) | Outbound port types → adapters |
|---|---|---|---|
| `analysis` | Run lifecycle: validate spec, queue (concurrency limit, one active run per ticker+date), start/stop/rerun, event fan-out, logs | `StartAnalysisUseCase`, `StopAnalysisUseCase`, `RerunAnalysisUseCase`, `GetAnalysisUseCase`, `ListAnalysesUseCase`, `SubscribeAnalysisEventsUseCase`, `ReadAnalysisLogsUseCase` | `runner` → `ProcessRunnerAdapter` (Phase 1), `DockerRunnerAdapter` (Phase 2); `persistence` → JDBC; `eventstore` → `events.jsonl` file adapter; `credentials` → reads provider keys from the secrets file to pass as env to the runner |
| `report` | Report index and reading, sections, debates, export md/html/pdf, import of existing `~/.tradingagents` data | `ListReportsUseCase`, `GetReportUseCase`, `ExportReportUseCase`, `ScanDataDirUseCase` | `datadir` → file-system reader for upstream/GUI outputs (§3.6); `persistence` → JDBC; `export` → HTML/PDF renderer |
| `catalog` | Providers, models, analysts available in the installed upstream version | `GetCatalogUseCase`, `GetEngineVersionUseCase` | `runner` → runs `ta-runner catalog` / `version`, cached |
| `settings` | Settings, presets, API keys (write + masked read + test) | `GetSettingsUseCase`, `UpdateSettingsUseCase`, `ManageSecretsUseCase`, `ManagePresetsUseCase` | `secrets` → `.env` file (0600); `persistence` → JDBC; `keytest` → minimal provider call to validate a key |

`orchestration` (cross-domain, since domains never depend on each other):
- `ImportExistingRunsUseCase` — `report` scans the data dir, `analysis` registers the found runs as `source=external`.
- `SystemHealthUseCase` — runner availability (`catalog`), data dir writable (`report`), keys configured (`settings`), queue state (`analysis`); also exposed as Actuator health indicators.
- `StartAnalysisFromPresetUseCase` — loads a preset (`settings`) and starts a run (`analysis`).
- Phase 3: `ScheduledAnalysisUseCase` (watchlists + cron).

#### Request flows
```
AnalysesApiController -> AnalysesApiDelegate -> AnalysesApiDelegateImpl
    -> StartAnalysisUseCase -> AnalysisService
    -> RunnerPort -> ProcessRunnerAdapter | DockerRunnerAdapter -> ta-runner

ta-runner stdout (JSONL)
    -> ProcessRunnerAdapter (virtual thread per run, reads lines)
    -> RunnerOutputLineToRunEventMapper -> RunEventSink (defined in core.outbound.runner, implemented in core.service)
    -> EventStorePort (events.jsonl)  +  in-memory fan-out to subscribers
    -> SubscribeAnalysisEventsUseCase -> AnalysisEventsApiDelegateImpl -> SseEmitter -> browser
```

#### REST API (served by `bff.controller.api`)
- **Analyses**: `POST /api/analyses`, `GET /api/analyses?status=&ticker=`, `GET /api/analyses/{id}`, `POST /api/analyses/{id}/stop`, `POST /api/analyses/{id}/rerun`, `DELETE /api/analyses/{id}`
- **Live stream**: `GET /api/analyses/{id}/events` (SSE, resume via `Last-Event-ID` = `seq`), `GET /api/events` (global run status changes → dashboard)
- **Logs**: `GET /api/analyses/{id}/logs?level=&q=&tail=`, `GET /api/analyses/{id}/logs/download`
- **Reports**: `GET /api/reports` (filter by ticker/date/decision), `GET /api/analyses/{id}/report`, `GET /api/analyses/{id}/export?format=md|html|pdf`, `POST /api/reports/rescan`
- **Settings**: `GET/PUT /api/settings`, `GET/PUT /api/secrets` (returns only "set/unset" + last 4 characters, **never** the values), `POST /api/secrets/test`, `GET/POST/PUT/DELETE /api/presets`
- **Catalog**: `GET /api/catalog` (providers, models, analysts, engine version)
- **Health**: `GET /api/health` (aggregated, from `SystemHealthUseCase`); Actuator `health`, `metrics` under `/actuator`
- Errors: `ApiExceptionHandler` returns `error_code` + parameters; the frontend translates the text (D5).

#### Enforced rules
`ArchitectureTest` is taken over from job-radar unchanged, with its package constants rebased onto `tr.girgin.backend.trading.analysis.platform` (e.g. `tr.girgin.backend.trading.analysis.platform.bff.controller.api..`):

| Rule | |
|---|---|
| `bff.controller.api` does not use domain models | web DTOs only |
| `bff.controller.api` depends on nothing else in the project | |
| `bff.controller.api` is used only by `api` and `impl` | |
| `bff.delegate.impl` is used only by itself | |
| The BFF never touches outbound ports | inbound ports only |
| `domain` and `orchestration` never depend on the BFF | |
| `core.service` is used only by `core.service` | reached through inbound ports |
| `adapter` is used only by `adapter` | |
| Adapters implement no project interfaces other than outbound ports and mappers | |
| Every adapter `@Component`, mappers aside, implements an outbound port | |
| Domains do not depend on each other | cross-domain logic goes to `orchestration` |
| Domains do not depend on `orchestration` | |
| MapStruct mappers live in `adapter` or `bff.delegate.impl` | mapping happens at boundaries |
| Mappers are named `SourceToTargetMapper` | |
| Mapper methods are named `map` | |

Platform-specific additions:
| Rule | |
|---|---|
| `core` does not depend on docker-java, JDBC, Jackson or `java.lang.ProcessBuilder` | infrastructure is an adapter concern |
| Only `adapter.runner` may start processes or talk to Docker | single place for command execution |

Services, adapters, mappers and delegate implementations are package-private, so most of these rules are also backed by the compiler. Each domain's `core` and `adapter` are separate Gradle subprojects, so a `core` cannot even see adapter libraries on its classpath.

#### Mapping
As in job-radar: objects carry no mapping logic; every conversion is a dedicated MapStruct mapper (`SourceToTargetMapper`, single `map` method, composed via `uses`). Examples:
```
RunnerOutputLineToRunEventMapper            (adapter.runner)
├── StringToRunEventTypeMapper               unknown type -> LOG
└── StringToRatingMapper                     "Overweight", "BUY", "hold." -> closed enum, else REVIEW

AnalysisToAnalysisDtoMapper                 (bff.delegate.impl)
├── RatingToRatingDtoMapper
└── RunStatsToRunStatsDtoMapper
    └── DurationToMillisMapper
```

### 3.5 Data model (Flyway migrations)
- `analyses(id, ticker, trade_date, asset_type, spec_json, status, runner, runner_ref(pid/container_id), engine_version, rating, decision_text, stats_json, created_at, started_at, ended_at, error_code, error, source[platform|external])`
- Events stay in `events.jsonl` per run (the DB keeps only the summary, so it stays small).
- `reports(id, analysis_id, source_path, content_hash, sections_json, indexed_at)`
- `presets(id, name, spec_json)`, `schedules(id, cron, preset_id, tickers, enabled)` (Phase 3), `settings(key, value)`
- Secrets are not stored in the DB: `.env` file (0600) or Docker secret.

### 3.6 Importing existing data (D7)
The platform uses **the same data dir** as upstream (`~/.tradingagents`, property `platform.data-dir`). This keeps the memory log (upstream learning from past decisions) and the cache continuous, and new runs made with the CLI show up too.

Scanned sources (found locally):
| Source | Content | Imported as |
|---|---|---|
| `logs/<TICKER>/<DATE>/reports/` (`1_analysts…5_portfolio`, `complete_report.md`) | Report tree | Analysis record + sections |
| `logs/<TICKER>/<DATE>/reports/run.json` | TradingAgents-GUI metadata (run_id, stats, cost) | Stats/cost fields |
| `logs/<TICKER>/TradingAgentsStrategy_logs/full_states_log_<date>.json` | Full state | Debates, decision |
| `runs.json` | TradingAgents-GUI run history (status, error) | Interrupted / failed runs |
| `reports/*.md` (e.g. `MU_deep_2026-09-28.md`) | `deep-analysis` skill output | "External report" |
| `memory/trading_memory.md` | Upstream memory log | Read-only, "Decision history" view (Phase 3) |

Rules:
- **Read-only:** the importer never modifies or deletes source files; "delete" in the UI only hides the platform record of an external run (deleting files is a separate, confirmed action).
- **Idempotent:** record key `(source, ticker, trade_date, path)` + content hash; re-scanning creates no duplicates, changed files are updated.
- **Resilient:** a missing/corrupt file does not skip the run, it is marked "partial" (e.g. `NVDA/2026-09-27` → no decision). Misspelled tickers (`NVDIA`, `NVIDIA`) are imported as is; merging/tagging them in the UI comes later.
- **Triggering:** full scan on startup, then change watching (`java.nio.file.WatchService`) + a "Rescan" button in the UI.

### 3.7 Language policy (D5)
- **UI strings:** `artifact/frontend/src/i18n/{tr,en}.json`, Turkish by default.
- **Analysis output language is a separate setting:** `output_language` (upstream config) in the New Analysis form defaults to the UI language (TR → `Turkish`) but can be changed per run. Upstream keeps internal debates in English; show this as a note in the UI.
- **Fixed labels are translated:** agent names (Market Analyst → Piyasa Analisti), stages, ratings (Buy → Al, Overweight → Ağırlık Artır, Hold → Tut, Underweight → Ağırlık Azalt, Sell → Sat, REVIEW → İncele). Raw values stay in English in the DB; translation happens only at display time.
- The backend returns an `error_code` + parameters; the frontend translates the text.

### 3.8 Frontend (Vue 3.5 + Vite 8 + TypeScript)
Current versions (npm, 2026-09-28): `vue 3.5.43`, `vite 8.3.1`, `vue-router 5.3.1`, `pinia 4.0.3`, `@vueuse/core 15.0.0`, `naive-ui 2.45.3` (✅ chosen).

| Page | Content |
|---|---|
| **Dashboard** | Active runs (live), recent decisions, daily cost, quick "New analysis" |
| **New Analysis** | Step-by-step form (ticker + asset type auto-detection, date, analyst cards, depth, report length, provider/model, language, checkpoint), save/load presets, cost estimate |
| **Run Detail** | Visual pipeline (Analysts → Research debate → Trader → Risk debate → Portfolio), tabs: *Live Feed*, *Reports*, *Debates* (bull/bear chat view), *Tool calls*, *Logs*, *Stats*; decision card (color-coded), Stop / Rerun |
| **Runs (History)** | Table: filters (ticker, date, status, decision, provider), sorting, bulk delete, compare |
| **Reports** | 3 panes: index / table of contents / reader, export md/html/pdf |
| **Logs** | Run picker + level filter + search + live tail (virtual scroll) |
| **Settings** | API keys (masked), default provider/model, runner selection, concurrency limit, theme, language |
| **Health** | Diagnostics checklist, first-run wizard |

Technical: `EventSource` composable (`useRunStream(runId)` — reconnect + `Last-Event-ID`), Pinia stores (`runs`, `settings`, `catalog`), `markdown-it` + `DOMPurify` for report rendering, `vue-virtual-scroller` for the log list, ECharts (cost/decision charts, Phase 3), vue-i18n (**default `tr`**, `en` as second language; chosen in Settings and stored in localStorage; date/number/currency formatting per locale via `Intl`; CI test that catches missing translation keys), TS client generated from the backend's OpenAPI spec (`openapi-typescript`).

### 3.9 Packaging: one artifact, one container (D12)
Frontend and backend are developed in separate folders but **built, shipped and run together**:

- `artifact/frontend` is a Gradle subproject (`:frontend`) that runs `pnpm install` + `pnpm build` via the `com.github.node-gradle.node` plugin (Node/pnpm versions pinned in the version catalog, downloaded by Gradle — no global Node needed for a build).
- `:backend` depends on the `:frontend` build output and copies `dist/` into `classpath:/static`. `./gradlew :backend:bootJar` produces **one jar** containing API + UI.
- Spring Boot serves the UI on the same origin as `/api` → no CORS, no separate web server, SSE and cookies work without proxy config.
- SPA routing: a small web config in `bff.controller.api` forwards non-`/api`, non-`/actuator`, non-asset paths to `index.html`.
- **Dev mode:** `pnpm dev` (Vite, hot reload) proxies `/api` to `localhost:8080`; the backend runs with `./gradlew :backend:bootRun`. Only in dev are they two processes.
- **Container:** one `platform` image (JRE 25 base, the single jar). The only other image is the `ta-runner` image, which is the TradingAgents runtime, not part of the platform.

---

## 4. Execution Model: Docker and Alternatives

All options are implementations of the `analysis` domain's `RunnerPort` (`start(spec, sink) -> handle`, `stop(handle)`, `status(handle)`) in `domain/analysis/adapter` under `adapter.runner`. The active one is chosen by property (`platform.runner=process|docker`).

| Option | How | Pros | Cons | Best for |
|---|---|---|---|---|
| **A. Process runner** | `ProcessBuilder` starts `python -m ta_runner run` in a separate `uv`-managed venv (upstream pinned) | No Docker needed, fastest dev loop, low resource use, lightweight on macOS | Needs Python 3.12 + uv on the host, weak isolation | **Development, single-user laptop** |
| **B. Docker runner (container per run)** | docker-java runs the `ta-runner` image (derived from the upstream image) with `--rm`; stdout = event stream; volume = data dir | Full isolation, reproducible, Stop = `docker stop`, resource limits (`--memory/--cpus`) | Docker socket access (security), container startup time (~1-2 s), Docker Desktop RAM on macOS | **Server / NAS / homelab** |
| **C. Long-lived worker container + queue** | Worker containers pull jobs from a queue and run each in a subprocess | No Docker socket needed, horizontal scaling | Extra component (queue), more complex | Multi-user / heavy use |
| **D. Kubernetes Job** | One K8s Job per run | Cloud scale | Overkill | Later |
| **E. Serverless (Modal / Cloud Run Jobs)** | One cloud job per run | No servers, scalable | Cost of long runs (5-25 min), vendor lock-in | Later / optional |

**Deployment combinations:**
1. **Fully native (no Docker)**: the platform jar (backend + UI) run with `java -jar`, `ta-runner` in a `uv` venv. `launchd` service on macOS, `systemd` on Linux. *(Phase 1)*
2. **Docker Compose**: `platform` container (backend + UI, one jar) + `docker-socket-proxy`; `ta-runner` containers are started per analysis by the platform (`platform.runner=docker`). Optional Caddy in front only for TLS when exposed beyond localhost. *(Phase 2, server)*
3. **Hybrid**: platform native, runs in Docker (isolation while keeping the platform easy to debug).

> ✅ **D1/D2:** Native on the Mac first (A), then Docker on the server (B) — same `RunnerPort`. The same `ta_runner` code runs inside the image; only the adapter changes.
> ✅ **D8:** On the server, one container per analysis (B). C (worker + queue) is out of the plan for now.
> Docker socket access = root on the host, so the following safeguards are applied in Phase 2:
> - The socket is accessed not directly but through **docker-socket-proxy** (only `containers` create/start/stop/logs/inspect allowed; `exec`/`images`/`volumes` disabled).
> - Runner containers: `--rm`, `--read-only` root FS, only the data dir and run dir mounted as volumes, `--memory`/`--cpus` limits, `--cap-drop ALL`, `no-new-privileges`, non-root user, only an allow-listed image name/tag can be started.
> - Containers carry a `ta.platform.run_id=<id>` label → on backend restart, orphaned containers are found via the label and reconciled.

### Note on using upstream "while it runs in Docker"
The upstream image is an interactive CLI; sending an analysis to a running container would require a server inside it, and there is none. So there are two clean paths:
- A thin image that uses the upstream image as its **base** and adds `ta_runner` on top (`FROM tradingagents:<tag>` + `COPY ta_runner`) → **upstream code is untouched**.
- Or `docker run --entrypoint python <upstream-image> -m ta_runner` with the runner mounted as a volume (no image build needed at all).

---

## 5. Project Structure (monorepo, job-radar layout)

Deployable artifacts live under `artifact/`; project paths stay short (`:backend:bff:api` → `artifact/backend/bff/api`), exactly like job-radar's `settings.gradle.kts`.

```
TradingAgents-Platform/
├── PLAN.md
├── README.md
├── settings.gradle.kts            # rootProject.name="trading-analysis-platform", includes + placeUnderArtifact()
├── build.gradle.kts
├── gradle.properties              # group=tr.girgin.backend.trading.analysis.platform
├── gradle/libs.versions.toml      # single source of truth for versions
├── build-logic/                   # convention plugins: tradinganalysisplatform.java-library, tradinganalysisplatform.mapstruct
├── gradlew, gradlew.bat
├── artifact/
│   ├── backend/                   :backend                        Spring Boot app, ArchitectureTest
│   │   ├── bff/
│   │   │   ├── api/               :backend:bff:api                controllers, delegate interfaces, web DTOs
│   │   │   └── impl/              :backend:bff:impl               delegate implementations, DTO mappers
│   │   ├── orchestration/         :backend:orchestration          cross-domain use cases
│   │   └── domain/
│   │       ├── analysis/{core,adapter}   :backend:domain:analysis:core|adapter
│   │       ├── report/{core,adapter}     :backend:domain:report:core|adapter
│   │       ├── catalog/{core,adapter}    :backend:domain:catalog:core|adapter
│   │       └── settings/{core,adapter}   :backend:domain:settings:core|adapter
│   ├── frontend/                  :frontend   Vue 3.5 + Vite + TS (pnpm); build bundled into the backend jar
│   │   ├── build.gradle.kts       node-gradle plugin: pnpm install / build / test
│   │   ├── package.json
│   │   └── src/ (pages/, components/, composables/, stores/, api/, i18n/)
│   └── ta-runner/                 Python (uv) — separate artifact, NOT part of the platform jar;
│       ├── pyproject.toml         installed into the TradingAgents runtime. The ONLY code that imports upstream.
│       ├── ta_runner/ (__main__, spec, compat, events, callbacks, agent_map, catalog)
│       ├── Dockerfile             FROM the upstream image
│       └── tests/
├── deploy/
│   ├── Dockerfile                 platform image: JRE 25 + the single jar (backend + UI)
│   ├── docker-compose.yml         platform + docker-socket-proxy (+ optional Caddy for TLS)
│   └── launchd/  systemd/
└── docs/ (event-protocol.md, runbook.md)
```

| Subproject | Depends on |
|---|---|
| `backend` | all modules, **runtime only** — it assembles them but never compiles against them; bundles the `:frontend` build output as static resources |
| `frontend` | Node/pnpm (via node-gradle), no Java dependencies; talks to the backend only through `/api` |
| `bff:api` | Spring Web only |
| `bff:impl` | `bff:api`, the `core` of each domain it serves, `orchestration` |
| `orchestration` | the `core` of the domains it composes |
| `domain:<d>:core` | Spring Context, SLF4J — no JDBC, Jackson, Docker or process libraries |
| `domain:<d>:adapter` | `domain:<d>:core` + its infrastructure (Spring Data JDBC, Jackson, docker-java…) |
| `ta-runner` | `tradingagents` (pinned tag) — separate Python build, no link to the Gradle build |

---

## 6. Roadmap

### Phase 0 — Skeleton and spike (2-3 days) ✅
- [x] Gradle skeleton copied from job-radar: `settings.gradle.kts`, `build-logic` convention plugins, version catalog, Java 25 toolchain, `ArchitectureTest` (job-radar rules + the two platform rules; `archunit.properties` allows empty selections while modules are empty)
- [x] Empty modules: `bff:api`, `bff:impl`, `orchestration`, `domain:{analysis,report,catalog,settings}:{core,adapter}`; context-load test green
- [x] Frontend skeleton (`artifact/frontend` as Gradle subproject `:frontend`, pnpm, Vite, Vue 3.5, Naive UI, vue-i18n, eslint/prettier); `bootJar` bundles it and serves `index.html` at `/` (SPA fallback for client routes; unknown `/api` paths and missing assets stay 404)
- [x] `ta-runner` skeleton (`uv`, Python 3.12, upstream pinned) + spike: spec → `graph.stream` → JSONL, with `compat.py`, event tracker, callbacks, SIGTERM handling and contract tests against the real v0.5.1 graph
- [x] End-to-end run with a real ticker and a cheap model (2026-09-29): NVDA / 2026-09-25, market analyst only, DeepSeek `deepseek-v4-flash` → `Overweight` in 4m20s, 12 LLM calls, ~77k tokens in / 48k out; 88 events with gapless `seq`, `events.jsonl` identical to stdout, full report tree + `full_states_log` + memory-log entry written, API key absent from all output
- [x] Event protocol v1 document (`docs/event-protocol.md`)

Phase 0 notes:
- TypeScript is pinned to 6.0.x: `typescript-eslint` does not support TypeScript 7 yet.
- Intel Mac: `cryptography>=49` ships no x86_64 macOS wheels; `ta-runner` constrains it to `<49` on Intel Macs only (`[tool.uv] constraint-dependencies`).
- Gradle itself needs a JDK 17+ to launch (e.g. `JAVA_HOME=/usr/local/opt/openjdk@17`); Java 25 comes from the toolchain.

### Phase 1 — MVP, no Docker (1.5-2 weeks)
- [ ] `ta-runner`: full event set, callbacks, SIGTERM, report saving, decision extraction, checkpoint, `catalog` / `version` commands
- [ ] `analysis` domain: model, inbound ports, service (queue + concurrency limit + per ticker/date lock), `ProcessRunnerAdapter`, event store, JDBC persistence, SSE with `Last-Event-ID`
- [ ] `report` domain: data dir reader, report index, sections/debates
- [ ] `catalog` and `settings` domains (secrets `.env`, presets)
- [ ] `orchestration`: `ImportExistingRunsUseCase` (tested against real `~/.tradingagents` data), `SystemHealthUseCase`
- [ ] BFF: controllers + delegates + DTO mappers for all of the above; `ApiExceptionHandler` with `error_code`
- [ ] Frontend: layout + router, New Analysis form, Runs list, Run Detail (pipeline + live feed + reports + decision card + stop), Logs tab; `tr` (default) + `en`
- [ ] Dev startup: `./gradlew :backend:bootRun` + `pnpm dev` (Vite proxy to `/api`), wrapped by a `Makefile`; production = one jar

### Phase 2 — Docker and hardening (1 week)
- [ ] `artifact/ta-runner/Dockerfile` (upstream image as base), `DockerRunnerAdapter` (D8), docker-socket-proxy, resource limits and security flags, label-based orphan container reconciliation
- [ ] `deploy/Dockerfile` (single platform image: backend + UI), PostgreSQL profile, `deploy/docker-compose.yml` (platform + docker-socket-proxy; optional Caddy for TLS)
- [ ] Finding/reconciling orphaned runs on backend restart (by pid/container id)
- [ ] Reports page (3 panes), export md/html/pdf, rerun, presets UI
- [ ] Single-user auth (D6): Spring Security, password from config → session cookie; mandatory when exposed beyond localhost, optional on localhost
- [ ] Settings + first-run wizard + health page

### Phase 3 — Value-add features (later)
- [ ] Scheduled analyses + watchlist, notifications (Telegram/email/browser)
- [ ] Dashboard charts: cost trend, per-ticker decision history
- [ ] Run comparison (same ticker, different model/date)
- [ ] Post-decision performance tracking (using upstream reflection / memory-log data)
- [ ] Chat over reports (Spring AI in a new domain adapter, as in job-radar)
- [ ] Batch analysis (ticker list), backtest mode (date range)

---

## 7. Security

- API keys: only in `.env` (0600) / Docker secret; the backend **never** returns the values; masking in logs (Logback pattern/filter for `sk-…` → `sk-…****`).
- Default bind `127.0.0.1` (`server.address`); if exposed externally, auth + HTTPS (Caddy) are mandatory.
- Docker socket = root on the host → access only through docker-socket-proxy with a minimal permission set (see §4, D8).
- Input validation (Bean Validation on DTOs + domain checks): ticker regex, date, allow-listed providers/models — against path traversal and command injection. Processes are started only with an argument list (`ProcessBuilder`, no shell).
- Report HTML rendering: sanitized with DOMPurify in the frontend and an allow-list sanitizer in the backend export (LLM output is untrusted input).

## 8. Observability
- Structured (JSON) logging in the backend and `ta-runner`; per-run `run.log` + `events.jsonl`.
- Actuator `health` + `metrics` (Micrometer): run durations, queue length, runner failures, token/cost counters; optional Prometheus registry.
- Optional: LangSmith / Langfuse callback in `ta-runner` (enabled via env).

## 9. Testing Strategy
- **`ta-runner` contract tests:** have the upstream APIs we use (class/method signatures, state keys, node names) changed? Run the graph briefly with a fake chat model (no LLM). Runs in CI on every upstream version upgrade.
- **Architecture:** `ArchitectureTest` (ArchUnit) — job-radar rules + platform additions (§3.4).
- **Domain services:** unit tests against stub outbound ports (e.g. `AnalysisServiceTest` with a fake `RunnerPort`: event flow, stop, queue, per ticker/date lock).
- **Mappers:** one test per mapper, especially runner output normalization (malformed/unknown events, rating spellings).
- **Adapters:** `ProcessRunnerAdapter` against a fake runner script that prints canned JSONL; data dir reader against a fixture copy of `~/.tradingagents`; JDBC adapters with SQLite (and PostgreSQL via Testcontainers in Phase 2).
- **BFF:** `@WebMvcTest` per controller, SSE included.
- **Frontend:** Vitest (composables/stores), Playwright (new analysis → live feed → report; with a fake SSE server).
- **E2E smoke:** a single ticker with a real cheap model (manual / nightly).

## 10. Tracking Upstream Updates
- Tag pinned in `artifact/ta-runner/pyproject.toml`. New upstream release → branch → contract tests → update `agent_map`/`compat` → merge. The backend is unaffected unless the event protocol version changes.
- `ta-runner catalog` reads provider/model/analyst lists dynamically from upstream → no hand-maintained lists in the backend or UI.
- Health page shows the engine version in use + a "new version available" warning (GitHub releases API).

## 11. Risks
| Risk | Impact | Mitigation |
|---|---|---|
| Upstream internal API changes (node names, state keys, `_log_state`) | `ta-runner` breaks | `compat.py` isolation, contract tests, version pin |
| Long runs (5-25 min) + SSE disconnects | UI loses the stream | `events.jsonl` + `Last-Event-ID` replay |
| Runs in flight during a backend restart | Orphaned processes/containers | Reconciliation on startup, `runner_ref` record |
| Parallel runs writing to the same cache/memory files | Race conditions | Concurrency limit; one active run per ticker+date lock |
| LLM cost blow-up | Bill | Cost estimate, per-run / daily budget limit, `max_tokens` |
| Rate limits (429) | Run failure | Expose the `llm_max_retries` setting in the UI |
| Three toolchains (JVM + Python + Node) | Setup friction | Gradle toolchain auto-provisions Java 25; `uv` pins Python; one `Makefile` for dev startup |

---

## 12. Decision Log (all resolved)

1. ✅ Target environment → D1
2. ✅ Docker model on the server → D8 (one container per analysis)
3. ✅ Number of users → D6 (single user for now)
4. ✅ UI library → D3 (Naive UI)
5. ✅ UI language → D5 (TR + EN, TR default)
6. ✅ Nothing pulled forward from Phase 3 → D9
7. ✅ Existing data → D7 (imported, data dir shared)
8. ✅ Upstream source → D4 (pinned GitHub tag; local clone for reference only)
9. ✅ Dependency boundary → D10 (platform never depends on TradingAgents code)
10. ✅ Backend → D11 (Java 25 + Spring Boot 4.1.1, job-radar structure under `tr.girgin.backend.trading.analysis.platform`)
11. ✅ Packaging → D12 (frontend bundled into the backend jar, one container; `artifact/backend` + `artifact/frontend`, plus `artifact/ta-runner`)
