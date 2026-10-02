"""The runner's event flow against a scripted upstream: no LLM, no network."""

from pathlib import Path

from ta_runner.engine.runner import EXIT_COMPLETED, EXIT_ERROR, EXIT_STOPPED, StopRequested, execute
from ta_runner.protocol.spec import RunSpec


class Message:
    def __init__(self, id_, type_, content, tool_calls=None):
        self.id, self.type, self.content, self.tool_calls = id_, type_, content, tool_calls or []


def scripted_states():
    """Accumulated graph states as upstream streams them (stream_mode="values")."""
    base = {
        "messages": [Message("m1", "human", "NVDA")],
        "investment_debate_state": {},
        "risk_debate_state": {},
    }
    yield dict(base)
    yield {
        **base,
        "messages": [*base["messages"], Message("m2", "ai", "", [{"name": "get_stock_data"}])],
    }
    yield {**base, "market_report": "# Market\nUptrend."}
    yield {**base, "market_report": "# Market\nUptrend.", "news_report": "# News\nQuiet."}
    invest = {"bull_history": "Bull: buy.", "bear_history": "", "judge_decision": "", "count": 1}
    state = {
        **base,
        "market_report": "# Market\nUptrend.",
        "news_report": "# News\nQuiet.",
        "investment_debate_state": invest,
    }
    yield state
    invest = {
        **invest,
        "bull_history": "Bull: buy.\nBull: more.",
        "judge_decision": "Go long.",
        "count": 2,
    }
    state = {**state, "investment_debate_state": invest, "investment_plan": "Plan: buy."}
    yield state
    state = {**state, "trader_investment_plan": "Trader: buy 10%."}
    yield state
    risk = {
        "aggressive_history": "All in.",
        "conservative_history": "Careful.",
        "neutral_history": "Balanced.",
        "judge_decision": "Overweight.",
        "count": 3,
    }
    yield {**state, "risk_debate_state": risk, "final_trade_decision": "Rating: Overweight"}


class FakeUpstream:
    resumed = False

    def __init__(self, fail_at=None, stop_at=None):
        self.fail_at, self.stop_at = fail_at, stop_at
        self.saved = None

    def run(self, on_chunk):
        final = {}
        for index, state in enumerate(scripted_states()):
            if index == self.fail_at:
                raise RuntimeError("provider exploded")
            if index == self.stop_at:
                raise StopRequested()
            final = state
            on_chunk(state)
        return final

    def save_reports(self, final_state):
        self.saved = final_state
        return Path("/data/logs/NVDA/2026-09-25/reports/complete_report.md")

    def rating(self, decision):
        return "Overweight" if "Overweight" in decision else "REVIEW"


def run(captured, spec_dict, upstream):
    spec = RunSpec.from_dict(spec_dict)
    return execute(spec, captured.writer, lambda _spec, _callbacks: upstream, "0.5.1")


def test_completed_run_emits_the_full_event_story(captured, spec_dict):
    upstream = FakeUpstream()

    assert run(captured, spec_dict, upstream) == EXIT_COMPLETED

    events = captured.events
    assert events[0]["type"] == "run_started"
    assert events[0]["upstream_version"] == "0.5.1"
    assert events[0]["spec"]["analysts"] == ["market", "news"]
    assert [e["seq"] for e in events] == list(range(1, len(events) + 1))

    sections = [e["section"] for e in captured.of_type("report_section")]
    assert sections == [
        "market_report",
        "news_report",
        "investment_plan",
        "trader_investment_plan",
        "final_trade_decision",
    ]

    tool_messages = [e for e in captured.of_type("message") if e["tool_calls"]]
    assert tool_messages[0]["tool_calls"] == ["get_stock_data"]

    bull = [e for e in captured.of_type("debate") if e["speaker"] == "bull"]
    assert [e["content"] for e in bull] == ["Bull: buy.", "Bull: more."]

    assert captured.of_type("decision") == [
        {**captured.of_type("decision")[0], "rating": "Overweight", "raw": "Rating: Overweight"}
    ]
    finished = events[-1]
    assert finished["type"] == "run_finished"
    assert finished["status"] == "completed"
    assert finished["report_dir"] == "/data/logs/NVDA/2026-09-25/reports"
    assert upstream.saved["final_trade_decision"] == "Rating: Overweight"


def test_agent_statuses_follow_the_pipeline(captured, spec_dict):
    run(captured, spec_dict, FakeUpstream())

    transitions = [(e["agent"], e["status"]) for e in captured.of_type("agent_status")]
    assert transitions.index(("Market Analyst", "in_progress")) < transitions.index(
        ("Market Analyst", "completed")
    )
    assert transitions.index(("News Analyst", "in_progress")) < transitions.index(
        ("News Analyst", "completed")
    )
    assert ("Sentiment Analyst", "pending") not in transitions  # not selected
    final = {}
    for agent, status in transitions:
        final[agent] = status
    assert set(final.values()) == {"completed"}


def test_failure_ends_with_an_error_event(captured, spec_dict):
    assert run(captured, spec_dict, FakeUpstream(fail_at=3)) == EXIT_ERROR

    finished = captured.events[-1]
    assert finished["type"] == "run_finished"
    assert finished["status"] == "error"
    assert finished["error_type"] == "RuntimeError"
    assert finished["error"] == "provider exploded"
    assert "Traceback" in finished["traceback"]
    assert not captured.of_type("decision")


def test_stop_ends_with_a_stopped_event(captured, spec_dict):
    assert run(captured, spec_dict, FakeUpstream(stop_at=2)) == EXIT_STOPPED

    assert captured.events[-1]["status"] == "stopped"
    assert captured.events[-2]["type"] == "stats"
