# ta-runner Event Protocol — v1

The contract between `ta-runner` (the only component that imports TradingAgents) and the
platform backend. The backend knows nothing else about a run: it writes a **spec** in, reads
**JSONL events** out, and reads the report files the run leaves in the data dir.

Wherever the runner runs (local process, Docker container), the backend reads it the same way.

## 1. Invocation

```
python -m ta_runner run --spec <run_dir>/spec.json --out <run_dir>
python -m ta_runner version
python -m ta_runner catalog
```

| Stream | Content |
|---|---|
| stdout | Protocol events only, one JSON object per line (UTF-8, `\n`-terminated, flushed per line) |
| stderr | Everything else: Python logging, upstream/library prints. The backend keeps it as the raw run log |
| `<run_dir>/events.jsonl` | Byte-identical copy of stdout, appended — survives a backend restart and is replayed to SSE clients |

The runner redirects file descriptor 1 to stderr at startup, so stray `print()` calls in
upstream or third-party code cannot corrupt the stream.

### Exit codes

| Code | Meaning | Last event |
|---|---|---|
| `0` | Completed | `run_finished{status: "completed"}` |
| `1` | Error (invalid spec, provider error, crash) | `run_finished{status: "error"}` |
| `2` | Stopped by SIGTERM / SIGINT | `run_finished{status: "stopped"}` |

A process that exits without a `run_finished` event (killed with SIGKILL, OOM) is treated by the
backend as `error` with `error_code = runner_died`.

**Stop:** the backend sends SIGTERM, waits a grace period, then SIGKILL. A second SIGTERM
terminates immediately.

### `version`

Prints one JSON object (not an event) and exits 0:

```json
{"runner_version": "0.1.0", "protocol_version": 1, "upstream_version": "0.5.1"}
```

### `catalog`

Prints one JSON object (not an event) and exits 0: what the installed upstream offers, so the
platform keeps no hand-maintained lists.

```json
{
  "upstream_version": "0.5.1",
  "defaults": {"llm_provider": "deepseek", "deep_think_llm": "deepseek-v4-pro", "quick_think_llm": "deepseek-v4-flash"},
  "providers": [
    {"id": "deepseek", "api_key_env": "DEEPSEEK_API_KEY", "custom_model_allowed": true,
     "quick_models": [{"id": "deepseek-v4-flash", "label": "…"}], "deep_models": [{"id": "deepseek-v4-pro", "label": "…"}]}
  ],
  "analysts": [{"id": "market", "agent": "Market Analyst"}, …],
  "asset_types": ["stock", "crypto"]
}
```

`api_key_env` is null for keyless providers (Ollama, Bedrock). `defaults` reflect upstream's
`TRADINGAGENTS_*` environment overrides.

## 2. Spec (`spec.json`)

| Field | Type | Required | Rules |
|---|---|---|---|
| `run_id` | string | ✓ | `[A-Za-z0-9_-]{1,64}` |
| `ticker` | string | ✓ | Upper-cased; `^?[A-Z0-9][A-Z0-9.\-=]{0,19}` (e.g. `NVDA`, `BRK.B`, `THYAO.IS`, `BTC-USD`, `^GSPC`) |
| `trade_date` | string | ✓ | `YYYY-MM-DD`, not in the future (upstream check) |
| `llm_provider` | string | ✓ | Lower-cased; must be supported by the installed upstream |
| `deep_think_llm`, `quick_think_llm` | string | ✓ | Model ids, `[A-Za-z0-9._:/@-]{1,128}` |
| `analysts` | string[] | | Subset of `market`, `social`, `news`, `fundamentals`; default all; run in that fixed order |
| `asset_type` | string | | `stock` (default) or `crypto` |
| `max_debate_rounds`, `max_risk_discuss_rounds` | int | | 1–10, default 1 |
| `output_language` | string | | Default `English`. Internal debates stay in English (upstream behavior) |
| `checkpoint_enabled` | bool | | Resume from the last completed node of an interrupted run with the same ticker/date/shape |
| `backend_url` | string | | Provider endpoint override |
| `config_overrides` | object | | Merged into upstream `DEFAULT_CONFIG` before the fields above are applied |

Provider API keys are **not** part of the spec; the backend passes them as environment variables.
Data locations come from upstream's `TRADINGAGENTS_RESULTS_DIR`, `TRADINGAGENTS_CACHE_DIR` and
`TRADINGAGENTS_MEMORY_LOG_PATH` (default `~/.tradingagents/...`).

## 3. Event envelope

```json
{"v":1,"ts":"2026-09-28T10:00:01.123Z","run_id":"r_ab12","seq":17,"type":"agent_status","agent":"Market Analyst","status":"in_progress"}
```

| Field | Meaning |
|---|---|
| `v` | Protocol version (this document: `1`) |
| `ts` | UTC, ISO-8601 with milliseconds |
| `run_id` | From the spec (empty string only for a spec that could not be parsed) |
| `seq` | Starts at 1, +1 per event, no gaps. Used as the SSE `id` / `Last-Event-ID` |
| `type` | One of §4 |

Payload fields sit next to the envelope fields. Long texts are truncated and end with
`… [truncated N chars]`; the full text is in the report files.

## 4. Event types

| `type` | Payload | Notes |
|---|---|---|
| `run_started` | `spec` (summary of §2 without overrides), `runner_version`, `upstream_version` | Always the first event of a valid spec |
| `agent_status` | `agent`, `status` ∈ `pending` / `in_progress` / `completed` / `error` | Emitted only on change. Agents: the selected analysts (`Market Analyst`, `Sentiment Analyst`, `News Analyst`, `Fundamentals Analyst`), then `Bull Researcher`, `Bear Researcher`, `Research Manager`, `Trader`, `Aggressive Analyst`, `Conservative Analyst`, `Neutral Analyst`, `Portfolio Manager` |
| `message` | `role` (`human` / `ai` / `tool` / …), `name`, `content` (≤ 8,000 chars), `tool_calls` (tool names) | One per new graph message |
| `tool_call` | `tool`, `args` (≤ 2,000 chars) | From LangChain callbacks |
| `tool_result` | `tool`, `duration_ms`, `error` (null on success), `output` (≤ 2,000 chars) | |
| `report_section` | `section`, `agent`, `markdown` (≤ 100,000 chars) | Sections: `market_report`, `sentiment_report`, `news_report`, `fundamentals_report`, `investment_plan`, `trader_investment_plan`, `final_trade_decision`. Re-emitted when the text changes |
| `debate` | `debate` ∈ `investment` / `risk`, `speaker` ∈ `bull` / `bear` / `aggressive` / `conservative` / `neutral` / `judge`, `round`, `content` | `content` is the new turn only, not the whole transcript |
| `stats` | `llm_calls`, `tool_calls`, `tokens_in`, `tokens_out`, `cost_usd` (null until pricing lands in Phase 1), `elapsed_s` | At most every 2 s while running, and once before `run_finished` |
| `log` | `level`, `logger`, `message` (≤ 4,000 chars) | Upstream (`tradingagents.*`) INFO+ and any WARNING+ |
| `decision` | `rating` ∈ `Buy` / `Overweight` / `Hold` / `Underweight` / `Sell` / `REVIEW`, `raw` | `REVIEW` = upstream could not parse a rating |
| `run_finished` | `status` ∈ `completed` / `stopped` / `error`; on completed: `resumed`, `report_dir`; on error: `error_type`, `error`, `traceback` | Always the last event |

## 5. Files a completed run leaves behind

Same layout as the upstream CLI and TradingAgents-GUI, so the importer reads both the same way:

```
<results_dir>/<TICKER>/<DATE>/reports/            1_analysts/ … 5_portfolio/, complete_report.md
<results_dir>/<TICKER>/TradingAgentsStrategy_logs/full_states_log_<DATE>.json
<memory_log_path>                                   decision appended for later reflection
```

## 6. Backend handling rules

Runner output is **untrusted input**:

- A line that is not valid JSON, or lacks the envelope, is kept as a raw `log` event (level `WARN`).
- An unknown `type` is kept as a `log` event; it never fails the run.
- An unknown `rating` becomes `REVIEW`; unknown `status` values become `error`.
- Payloads over the backend's own limits are truncated again.

## 7. Versioning

- Adding an event type or an optional field is **compatible**: `v` stays `1`; consumers ignore
  what they don't know (see §6).
- Renaming/removing a field, changing a meaning, or changing exit codes bumps `v`. The backend
  rejects a runner whose `version.protocol_version` it does not support (health check).
