"""Turns one upstream run into protocol events (docs/event-protocol.md)."""

from __future__ import annotations

import time
import traceback
from collections.abc import Callable
from pathlib import Path
from typing import Any, Protocol

from . import __version__
from .agent_map import ANALYST_AGENTS, ANALYST_REPORTS, DEBATE_FIELDS, REPORT_SECTIONS
from .callbacks import EventCallbackHandler
from .events import EventWriter, truncate
from .pricing import CostMeter, PriceTable
from .spec import RunSpec

EXIT_COMPLETED, EXIT_ERROR, EXIT_STOPPED = 0, 1, 2

# Report sections can be long; the full text is on disk, the event carries up to this.
MAX_SECTION = 100_000
STATS_INTERVAL_S = 2.0

TEAM_AGENTS = (
    "Bull Researcher",
    "Bear Researcher",
    "Research Manager",
    "Trader",
    "Aggressive Analyst",
    "Conservative Analyst",
    "Neutral Analyst",
    "Portfolio Manager",
)


class StopRequested(BaseException):  # noqa: N818 - control flow, not an error
    """Raised by the SIGTERM handler.

    A BaseException, so an upstream `except Exception` cannot swallow a stop.
    """


class Upstream(Protocol):
    resumed: bool

    def run(self, on_chunk: Callable[[dict[str, Any]], None]) -> dict[str, Any]: ...
    def save_reports(self, final_state: dict[str, Any]) -> Path: ...
    def rating(self, final_trade_decision: str) -> str: ...


UpstreamFactory = Callable[[RunSpec, list[Any]], Upstream]


def execute(
    spec: RunSpec, writer: EventWriter, upstream_factory: UpstreamFactory, upstream_version: str
) -> int:
    started = time.monotonic()
    callbacks = EventCallbackHandler(writer, CostMeter(PriceTable.load(), spec.llm_provider))
    tracker = StateTracker(writer, spec)

    def stats() -> dict[str, Any]:
        return {
            **callbacks.stats(),
            "elapsed_s": round(time.monotonic() - started, 1),
        }

    try:
        writer.emit(
            "run_started",
            spec=spec.summary(),
            runner_version=__version__,
            upstream_version=upstream_version,
        )
        upstream = upstream_factory(spec, [callbacks])
        tracker.start()
        last_stats = [0.0]

        def on_chunk(state: dict[str, Any]) -> None:
            tracker.on_state(state)
            now = time.monotonic()
            if now - last_stats[0] >= STATS_INTERVAL_S:
                last_stats[0] = now
                writer.emit("stats", **stats())

        final_state = upstream.run(on_chunk)
        tracker.finish(final_state)
        report_file = upstream.save_reports(final_state)
        decision = final_state.get("final_trade_decision") or ""
        writer.emit("decision", rating=upstream.rating(decision), raw=truncate(decision))
        writer.emit("stats", **stats())
        writer.emit(
            "run_finished",
            status="completed",
            resumed=upstream.resumed,
            report_dir=str(Path(report_file).parent),
        )
        return EXIT_COMPLETED
    except StopRequested:
        writer.emit("stats", **stats())
        writer.emit("run_finished", status="stopped")
        return EXIT_STOPPED
    except Exception as exc:  # noqa: BLE001 - every failure must end in a run_finished event
        writer.emit("stats", **stats())
        writer.emit(
            "run_finished",
            status="error",
            error_type=type(exc).__name__,
            error=truncate(str(exc), 2_000),
            traceback=truncate(traceback.format_exc(), 20_000),
        )
        return EXIT_ERROR


class StateTracker:
    """Diffs successive graph states and emits only what changed."""

    def __init__(self, writer: EventWriter, spec: RunSpec) -> None:
        self._writer = writer
        self._analysts = [ANALYST_AGENTS[a] for a in spec.analysts]
        self._analyst_reports = {ANALYST_AGENTS[a]: ANALYST_REPORTS[a] for a in spec.analysts}
        self._status: dict[str, str] = {}
        self._sections: dict[str, str] = {}
        self._debates: dict[tuple[str, str], str] = {}
        self._seen_messages: set[str] = set()

    def start(self) -> None:
        for agent in (*self._analysts, *TEAM_AGENTS):
            self._set(agent, "pending")
        if self._analysts:
            self._set(self._analysts[0], "in_progress")

    def on_state(self, state: dict[str, Any]) -> None:
        self._messages(state)
        self._report_sections(state)
        self._debate_turns(state)
        self._statuses(state)

    def finish(self, final_state: dict[str, Any]) -> None:
        self.on_state(final_state)
        for agent in self._status:
            self._set(agent, "completed")

    def _set(self, agent: str, status: str) -> None:
        if self._status.get(agent) != status:
            self._status[agent] = status
            self._writer.emit("agent_status", agent=agent, status=status)

    def _messages(self, state: dict[str, Any]) -> None:
        for message in state.get("messages") or []:
            key = getattr(message, "id", None) or f"{type(message).__name__}:{hash(str(message))}"
            if key in self._seen_messages:
                continue
            self._seen_messages.add(key)
            content = _text(getattr(message, "content", message))
            tool_calls = [c.get("name") for c in getattr(message, "tool_calls", None) or []]
            if content or tool_calls:
                self._writer.emit(
                    "message",
                    role=getattr(message, "type", "unknown"),
                    name=getattr(message, "name", None),
                    content=truncate(content),
                    tool_calls=tool_calls,
                )

    def _report_sections(self, state: dict[str, Any]) -> None:
        for section, agent in REPORT_SECTIONS.items():
            text = state.get(section)
            if isinstance(text, str) and text.strip() and self._sections.get(section) != text:
                self._sections[section] = text
                self._writer.emit(
                    "report_section",
                    section=section,
                    agent=agent,
                    markdown=truncate(text, MAX_SECTION),
                )

    def _debate_turns(self, state: dict[str, Any]) -> None:
        for state_key, field, speaker in DEBATE_FIELDS:
            debate_state = state.get(state_key) or {}
            text = debate_state.get(field) or ""
            previous = self._debates.get((state_key, field), "")
            if not text.strip() or text == previous:
                continue
            self._debates[(state_key, field)] = text
            # Histories only grow; send the new turn, not the whole transcript again.
            new = text[len(previous) :] if text.startswith(previous) else text
            self._writer.emit(
                "debate",
                debate="investment" if state_key == "investment_debate_state" else "risk",
                speaker=speaker,
                round=debate_state.get("count"),
                content=truncate(new.strip()),
            )

    def _statuses(self, state: dict[str, Any]) -> None:
        active_found = False
        for agent in self._analysts:
            if state.get(self._analyst_reports[agent]):
                self._set(agent, "completed")
            elif not active_found:
                self._set(agent, "in_progress")
                active_found = True
        if active_found:
            return

        invest = state.get("investment_debate_state") or {}
        risk = state.get("risk_debate_state") or {}
        for field, agent in (
            ("bull_history", "Bull Researcher"),
            ("bear_history", "Bear Researcher"),
        ):
            if invest.get(field) and self._status.get(agent) == "pending":
                self._set(agent, "in_progress")
        if invest.get("judge_decision"):
            for agent in ("Bull Researcher", "Bear Researcher", "Research Manager"):
                self._set(agent, "completed")
            if not state.get("trader_investment_plan"):
                self._set("Trader", "in_progress")
        if state.get("trader_investment_plan"):
            self._set("Trader", "completed")
        for field, agent in (
            ("aggressive_history", "Aggressive Analyst"),
            ("conservative_history", "Conservative Analyst"),
            ("neutral_history", "Neutral Analyst"),
        ):
            if risk.get(field) and self._status.get(agent) == "pending":
                self._set(agent, "in_progress")
        if risk.get("judge_decision"):
            for agent in ("Aggressive Analyst", "Conservative Analyst", "Neutral Analyst"):
                self._set(agent, "completed")
            self._set("Portfolio Manager", "completed")


def _text(content: Any) -> str:
    """Message content as plain text; providers return a string or a list of blocks."""
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        parts = [
            block.get("text", "") if isinstance(block, dict) else str(block) for block in content
        ]
        return "\n".join(p for p in parts if p).strip()
    return "" if content is None else str(content).strip()
