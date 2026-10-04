# Replay recording

`chunks.jsonl` is one analysis (NVDA, 2026-09-25, all four analysts) as the upstream graph
streams it. `TA_RUNNER_REPLAY` plays it back through the real runner, so the end-to-end UI
tests (`e2e/`) and `tests/engine/test_replay.py` see real events and real
report files without calling an LLM. The format is described in `ta_runner/engine/replay.py`.

This recording is hand-written in the shape of a real run, and kept short (about nine seconds).
To replace it with a real one, run an analysis with `TA_RUNNER_RECORD` set, then trim it:

```sh
TA_RUNNER_RECORD=tests/fixtures/replay-run uv run python -m ta_runner run --spec spec.json --out /tmp/run
```

Record it again when upgrading the pinned TradingAgents version: the recording holds upstream's
state, not the platform's events.
