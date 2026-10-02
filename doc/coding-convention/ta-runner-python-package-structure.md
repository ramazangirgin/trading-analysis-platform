# ta-runner: Python package structure

How the `ta_runner` package (`artifact/ta-runner`) is split, which part may import which, and the
[import-linter](https://import-linter.readthedocs.io/) contracts and ruff rules in
[`pyproject.toml`](../../artifact/ta-runner/pyproject.toml) that enforce it.

## Layout

```
ta_runner/
  __init__.py       __version__, PROTOCOL_VERSION
  __main__.py       the CLI: run, catalog, version (`python -m ta_runner`, `ta-runner`)
  catalog/          catalog.py: providers, models and analysts for `ta-runner catalog`
  engine/           runs one analysis on TradingAgents
    runner.py         the run: callbacks in, events out, exit codes
    callbacks.py      LangChain callback handler: LLM / tool calls, usage, cost
    agent_map.py      upstream agent and report names
    compat.py         the ONLY module that imports TradingAgents
  pricing/          pricing.py + prices.json: LLM cost per call
  protocol/         the contract with the backend (docs/event-protocol.md)
    spec.py           the run spec read in
    events.py         the JSONL events written out
tests/              mirrors ta_runner/
  protocol/ engine/ catalog/ pricing/
  conftest.py       shared fixtures
  test_stop_signal.py   tests of __main__ (SIGTERM handling)
```

## Layers

```
__main__  ->  catalog  ->  engine  ->  pricing  ->  protocol
```

A module may import from its own sub-package and from any layer to its right, never to its left.
`catalog` sits above `engine` because it reads upstream's model list through `engine.compat`.
`ta_runner/__init__.py` (version constants) may be imported by every layer.

| Rule | Why | Checked by |
|---|---|---|
| The layer order above | the protocol is the stable contract; the engine changes with upstream | import-linter `layers` contract |
| Only `ta_runner.engine.compat` imports `tradingagents` | one place to adapt when upstream changes (BE-0002); covered by `tests/engine/test_upstream_contract.py` | import-linter `forbidden` contract; ruff `TID251` (banned-api) |
| Only `engine` imports `langchain_core` / `langchain` / `langgraph` | the LangChain callback API is an engine detail | import-linter `forbidden` contract |
| `protocol` and `pricing` import none of the engine's dependencies | both are plain data and arithmetic, testable without upstream installed | import-linter `forbidden` contract; layers |
| Relative imports only within one sub-package (`from .callbacks import ...`); across sub-packages the full path (`from ta_runner.protocol.events import ...`) | the import says which layer it crosses | ruff `TID252` (`ban-relative-imports = "parents"`) |

import-linter resolves imports transitively for `layers`; the `forbidden` contracts check direct
imports only (`allow_indirect_imports`), since everything above `engine` reaches upstream through it
by design.

## Tests

Tests mirror the package: a module `ta_runner/<sub>/<module>.py` is tested in
`tests/<sub>/test_<module>.py` (more than one file per module is fine, e.g.
`tests/engine/test_callbacks_cost.py`). Tests of `__main__` stay in `tests/`. Only
`tests/engine/test_upstream_contract.py` imports `tradingagents` directly (ruff per-file ignore).

## Running the checks

```sh
mise run runner-test          # ruff check, lint-imports, pytest
cd artifact/ta-runner && uv run lint-imports
```

They also run in CI (*ta-runner* job) and in the pre-commit hook when files under
`artifact/ta-runner/` are staged.

The package keeps working as before: `python -m ta_runner` and the `ta-runner` script run
`ta_runner.__main__:main`, and the Dockerfile copies the whole `ta_runner/` folder, `prices.json`
included.
