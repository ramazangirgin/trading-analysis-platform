"""Contract tests: the upstream surface compat.py relies on.

Run on every upstream version bump. Builds the real graph (no LLM call is made) so node
wiring, state keys and the semi-internal APIs are checked against the pinned release.
"""

import inspect

import pytest

from ta_runner.engine import compat
from ta_runner.protocol.spec import RunSpec


@pytest.fixture
def isolated_spec(tmp_path, spec_dict, monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test-not-used")
    spec_dict["config_overrides"] = {
        "results_dir": str(tmp_path / "logs"),
        "data_cache_dir": str(tmp_path / "cache"),
        "memory_log_path": str(tmp_path / "memory" / "trading_memory.md"),
    }
    return RunSpec.from_dict(spec_dict)


def test_upstream_version_is_the_pinned_release():
    assert compat.upstream_version() == "0.6.0"


def test_graph_exposes_the_methods_compat_uses():
    from tradingagents.graph.trading_graph import TradingAgentsGraph

    for name in (
        "create_run_state",
        "begin_checkpoint",
        "checkpoint_input",
        "end_checkpoint",
        "clear_checkpoint_on_success",
        "record_decision",
        "stream_run",
    ):
        assert callable(getattr(TradingAgentsGraph, name)), name
    params = inspect.signature(TradingAgentsGraph.create_run_state).parameters
    assert list(params)[1:4] == ["company_name", "trade_date", "asset_type"]


def test_config_carries_the_spec(isolated_spec):
    config = compat.build_config(isolated_spec)

    assert config["llm_provider"] == "openai"
    assert config["deep_think_llm"] == "gpt-5.4-mini"
    assert config["max_debate_rounds"] == 1
    assert config["output_language"] == "English"
    assert config["results_dir"].endswith("logs")


def test_graph_builds_and_streams_full_state_values(isolated_spec):
    run = compat.UpstreamRun(isolated_spec, callbacks=[])
    graph = run._graph

    nodes = set(graph.graph.get_graph().nodes)
    for expected in (
        "Market Analyst",
        "News Analyst",
        "Bull Researcher",
        "Bear Researcher",
        "Research Manager",
        "Trader",
        "Portfolio Manager",
    ):
        assert expected in nodes, f"missing node {expected!r} in {sorted(nodes)}"

    state = graph.propagator.create_initial_state("NVDA", "2026-09-25")
    for key in (
        "market_report",
        "sentiment_report",
        "news_report",
        "fundamentals_report",
        "investment_debate_state",
        "risk_debate_state",
    ):
        assert key in state, key
    for key in ("bull_history", "bear_history", "count"):
        assert key in state["investment_debate_state"], key
    for key in ("aggressive_history", "conservative_history", "neutral_history", "count"):
        assert key in state["risk_debate_state"], key


@pytest.mark.parametrize(
    ("final_state", "rating"),
    [
        ({"final_rating": "Sell", "final_trade_decision": "**Rating**: Buy"}, "Sell"),
        ({"final_trade_decision": "**Rating**: Buy"}, "Buy"),
        ({"final_trade_decision": "Final rating: Overweight"}, "Overweight"),
        ({"final_trade_decision": "FINAL TRANSACTION PROPOSAL: **SELL**"}, "REVIEW"),
        ({"final_trade_decision": "no idea"}, "REVIEW"),
    ],
)
def test_rating_is_the_portfolio_managers_own(final_state, rating):
    assert compat.UpstreamRun.rating(final_state) == rating
