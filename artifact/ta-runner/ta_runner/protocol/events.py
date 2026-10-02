"""JSONL event writer: one JSON object per line, on stdout and in <out>/events.jsonl."""

from __future__ import annotations

import json
import sys
import threading
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, TextIO

from ta_runner import PROTOCOL_VERSION

# Keeps one event line bounded; full texts live in the report files.
MAX_TEXT = 8_000


def truncate(text: str | None, limit: int = MAX_TEXT) -> str | None:
    if text is None or len(text) <= limit:
        return text
    return text[:limit] + f"… [truncated {len(text) - limit} chars]"


class EventWriter:
    """Thread-safe: LangChain callbacks may fire from worker threads."""

    def __init__(self, run_id: str, stream: TextIO, events_file: Path | None = None) -> None:
        self._run_id = run_id
        self._stream: TextIO | None = stream
        self._file = events_file.open("a", encoding="utf-8") if events_file else None
        self._seq = 0
        self._lock = threading.Lock()

    def emit(self, event_type: str, **payload: Any) -> None:
        with self._lock:
            self._seq += 1
            event = {
                "v": PROTOCOL_VERSION,
                "ts": datetime.now(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
                "run_id": self._run_id,
                "seq": self._seq,
                "type": event_type,
                **payload,
            }
            line = json.dumps(event, ensure_ascii=False, default=str) + "\n"
            # File first: once the platform has read a line from stdout, a reconnecting client
            # replaying events.jsonl must already find it there.
            if self._file:
                self._file.write(line)
                self._file.flush()
            if self._stream:
                self._write_stream(line)

    def _write_stream(self, line: str) -> None:
        try:
            self._stream.write(line)
            self._stream.flush()
        except (BrokenPipeError, ValueError, OSError) as exc:
            if not self._file:
                raise
            # The platform that read stdout has gone (restarted, crashed). The run is worth
            # finishing: events.jsonl still gets every event, and a restarted platform follows it.
            self._stream = None
            print(
                f"ta-runner: stdout is gone ({exc.__class__.__name__}); writing events.jsonl only",
                file=sys.stderr,
                flush=True,
            )

    def close(self) -> None:
        with self._lock:
            if self._file:
                self._file.close()
                self._file = None
