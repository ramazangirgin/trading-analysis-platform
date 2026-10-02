"""Every call into TradingAgents lives here, and nowhere else.

Some of these are semi-internal upstream APIs (``_log_state``, ``_resuming``,
``propagator``). Keeping them in one module means an upstream upgrade touches one file,
and the contract tests (PLAN.md section 9) exercise exactly this surface.
Mirrors the upstream CLI's run path (cli/run.py in v0.5.1) so a platform run behaves
like a CLI run: settled decision log, past context, instrument identity, checkpoints.
"""

from __future__ import annotations

import copy
from collections.abc import Callable
from importlib import metadata
from pathlib import Path
from typing import Any

from tradingagents.agents.rating import parse_rating
from tradingagents.dataflows.config import run_config
from tradingagents.dataflows.symbols import safe_ticker_component
from tradingagents.default_config import DEFAULT_CONFIG
from tradingagents.graph.trading_graph import TradingAgentsGraph
from tradingagents.llm_clients.api_key_env import get_api_key_env
from tradingagents.llm_clients.model_catalog import MODEL_OPTIONS
from tradingagents.reporting import write_report_tree

from ta_runner.protocol.spec import RunSpec


def upstream_version() -> str:
    try:
        return metadata.version("tradingagents")
    except metadata.PackageNotFoundError:
        return "unknown"


def model_options() -> dict[str, dict[str, list[tuple[str, str]]]]:
    """Provider -> {"quick"|"deep": [(label, model id)]}, as the upstream CLI offers them."""
    return MODEL_OPTIONS


def api_key_env(provider: str) -> str | None:
    """Env var holding the provider's API key; None for keyless providers (ollama, bedrock)."""
    return get_api_key_env(provider)


def default_models() -> dict[str, str]:
    return {
        "llm_provider": DEFAULT_CONFIG["llm_provider"],
        "deep_think_llm": DEFAULT_CONFIG["deep_think_llm"],
        "quick_think_llm": DEFAULT_CONFIG["quick_think_llm"],
    }


def build_config(spec: RunSpec) -> dict[str, Any]:
    """DEFAULT_CONFIG (with its TRADINGAGENTS_* env overrides) + the spec's choices."""
    config = copy.deepcopy(DEFAULT_CONFIG)
    config.update(copy.deepcopy(spec.config_overrides))
    config.update(
        llm_provider=spec.llm_provider,
        deep_think_llm=spec.deep_think_llm,
        quick_think_llm=spec.quick_think_llm,
        backend_url=spec.backend_url,
        max_debate_rounds=spec.max_debate_rounds,
        max_risk_discuss_rounds=spec.max_risk_discuss_rounds,
        output_language=spec.output_language,
        checkpoint_enabled=spec.checkpoint_enabled,
    )
    return config


class UpstreamRun:
    """One analysis on the upstream graph, streamed chunk by chunk."""

    def __init__(self, spec: RunSpec, callbacks: list[Any]) -> None:
        self._spec = spec
        self._callbacks = callbacks
        self._graph = TradingAgentsGraph(
            list(spec.analysts), debug=False, config=build_config(spec), callbacks=callbacks
        )

    @property
    def results_dir(self) -> Path:
        return Path(self._graph.config["results_dir"])

    def run(self, on_chunk: Callable[[dict[str, Any]], None]) -> dict[str, Any]:
        """Stream the graph, hand every state chunk to ``on_chunk``, return the final state.

        On success the decision is logged for reflection, the full state is written to
        ``full_states_log_<date>.json`` and the checkpoint is cleared; a failure or stop
        keeps the checkpoint so a rerun can resume.
        """
        graph, s = self._graph, self._spec
        with run_config(graph.config):
            init_state = graph.create_run_state(s.ticker, s.trade_date, s.asset_type)
            args = graph.propagator.get_graph_args(callbacks=self._callbacks)
            thread_id = graph.begin_checkpoint(s.ticker, s.trade_date, s.asset_type)
            if thread_id is not None:
                args.setdefault("config", {}).setdefault("configurable", {})["thread_id"] = (
                    thread_id
                )
            try:
                final_state: dict[str, Any] = {}
                for chunk in graph.graph.stream(graph.checkpoint_input(init_state), **args):
                    final_state.update(chunk)
                    on_chunk(final_state)
                graph._log_state(s.trade_date, final_state)
                graph.record_decision(s.ticker, s.trade_date, final_state)
                graph.clear_checkpoint_on_success(s.ticker, s.trade_date, s.asset_type)
                return final_state
            finally:
                graph.end_checkpoint()

    @property
    def resumed(self) -> bool:
        return bool(getattr(self._graph, "_resuming", False))

    def save_reports(self, final_state: dict[str, Any]) -> Path:
        """Report tree in the same place the upstream CLI uses:
        ``<results_dir>/<TICKER>/<DATE>/reports`` (PLAN.md section 3.6)."""
        s = self._spec
        target = self.results_dir / safe_ticker_component(s.ticker) / s.trade_date / "reports"
        return write_report_tree(final_state, s.ticker, target)

    @staticmethod
    def rating(final_trade_decision: str) -> str:
        """Buy / Overweight / Hold / Underweight / Sell, or REVIEW when unparseable."""
        return parse_rating(final_trade_decision or "")
