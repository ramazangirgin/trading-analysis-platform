"""Replays a recorded run instead of calling the LLMs, and records runs to replay.

The end-to-end UI tests run the real runner on a recording: everything from the state chunks
on (events, report files, the decision, a stop) is the real code path; only the graph is not.

A recording is a directory with ``chunks.jsonl``. Each line is one state chunk as
``graph.stream`` yielded it (stream_mode="values"), stored as the change to the previous one:

    {"at_s": 1.5, "set": {"market_report": "..."}, "add_messages": [{"id": "m2", ...}]}

``at_s`` is the time since the run started; ``set`` holds the top-level keys that changed;
``add_messages`` the messages that were not in the previous chunk.
"""

from __future__ import annotations

import json
import time
from collections.abc import Callable
from pathlib import Path
from types import SimpleNamespace
from typing import IO, Any

from ta_runner.protocol.spec import RunSpec

from . import compat
from .runner import Upstream, UpstreamFactory

CHUNKS_FILE = "chunks.jsonl"


class ReplayRun:
    """An upstream that plays a recording back at its recorded pace (scaled by ``speed``)."""

    resumed = False

    def __init__(self, spec: RunSpec, recording: Path, speed: float = 1.0) -> None:
        self._spec = spec
        self._chunks = recording / CHUNKS_FILE
        self._speed = speed

    def run(self, on_chunk: Callable[[dict[str, Any]], None]) -> dict[str, Any]:
        started = time.monotonic()
        state: dict[str, Any] = {}
        with self._chunks.open(encoding="utf-8") as lines:
            for line in lines:
                if not line.strip():
                    continue
                chunk = json.loads(line)
                wait = chunk.get("at_s", 0) / self._speed - (time.monotonic() - started)
                if wait > 0:
                    time.sleep(wait)
                state.update(chunk.get("set") or {})
                state["messages"] = [
                    *(state.get("messages") or []),
                    *(SimpleNamespace(**m) for m in chunk.get("add_messages") or []),
                ]
                on_chunk(dict(state))
        return state

    def save_reports(self, final_state: dict[str, Any]) -> Path:
        return compat.write_reports(self._spec, compat.results_dir(self._spec), final_state)

    @staticmethod
    def rating(final_trade_decision: str) -> str:
        return compat.rating(final_trade_decision)


def replaying(recording: Path, speed: float = 1.0) -> UpstreamFactory:
    return lambda spec, _callbacks: ReplayRun(spec, recording, speed)


def recording(factory: UpstreamFactory, target: Path) -> UpstreamFactory:
    """Wraps an upstream so every chunk it streams is also written to ``target``."""

    def build(spec: RunSpec, callbacks: list[Any]) -> Upstream:
        return _Recorder(factory(spec, callbacks), target)

    return build


class _Recorder:
    def __init__(self, upstream: Upstream, target: Path) -> None:
        self._upstream = upstream
        self._target = target

    @property
    def resumed(self) -> bool:
        return self._upstream.resumed

    def run(self, on_chunk: Callable[[dict[str, Any]], None]) -> dict[str, Any]:
        self._target.mkdir(parents=True, exist_ok=True)
        with (self._target / CHUNKS_FILE).open("w", encoding="utf-8") as out:
            writer = _ChunkWriter(out)

            def record(state: dict[str, Any]) -> None:
                writer.write(state)
                on_chunk(state)

            return self._upstream.run(record)

    def save_reports(self, final_state: dict[str, Any]) -> Path:
        return self._upstream.save_reports(final_state)

    def rating(self, final_trade_decision: str) -> str:
        return self._upstream.rating(final_trade_decision)


class _ChunkWriter:
    def __init__(self, out: IO[str]) -> None:
        self._out = out
        self._started = time.monotonic()
        self._previous: dict[str, str] = {}
        self._message_ids: set[str] = set()

    def write(self, state: dict[str, Any]) -> None:
        changed = {}
        for key, value in state.items():
            if key == "messages":
                continue
            encoded = json.dumps(value, default=str, sort_keys=True)
            if self._previous.get(key) != encoded:
                self._previous[key] = encoded
                changed[key] = json.loads(encoded)
        added = []
        for message in state.get("messages") or []:
            entry = _message(message)
            if entry["id"] not in self._message_ids:
                self._message_ids.add(entry["id"])
                added.append(entry)
        chunk = {"at_s": round(time.monotonic() - self._started, 2), "set": changed}
        if added:
            chunk["add_messages"] = added
        self._out.write(json.dumps(chunk, default=str, ensure_ascii=False) + "\n")
        self._out.flush()


def _message(message: Any) -> dict[str, Any]:
    """The message fields the event stream reads (StateTracker._messages), as plain JSON."""
    return {
        "id": getattr(message, "id", None) or f"{type(message).__name__}:{hash(str(message))}",
        "type": getattr(message, "type", "unknown"),
        "name": getattr(message, "name", None),
        "content": getattr(message, "content", str(message)),
        "tool_calls": [
            {"name": c.get("name")} for c in getattr(message, "tool_calls", None) or []
        ],
    }
