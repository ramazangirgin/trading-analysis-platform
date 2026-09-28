"""LangChain callback: counts LLM/tool calls and tokens, reports tool calls as events."""

from __future__ import annotations

import threading
import time
from typing import Any
from uuid import UUID

from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.messages import AIMessage
from langchain_core.outputs import LLMResult

from .events import EventWriter, truncate


class EventCallbackHandler(BaseCallbackHandler):
    def __init__(self, writer: EventWriter) -> None:
        super().__init__()
        self._writer = writer
        self._lock = threading.Lock()
        self._tool_started: dict[UUID, tuple[str, float]] = {}
        self.llm_calls = 0
        self.tool_calls = 0
        self.tokens_in = 0
        self.tokens_out = 0

    def on_chat_model_start(self, serialized: dict[str, Any], messages: Any, **kwargs: Any) -> None:
        with self._lock:
            self.llm_calls += 1

    def on_llm_start(self, serialized: dict[str, Any], prompts: list[str], **kwargs: Any) -> None:
        with self._lock:
            self.llm_calls += 1

    def on_llm_end(self, response: LLMResult, **kwargs: Any) -> None:
        try:
            message = response.generations[0][0].message  # type: ignore[attr-defined]
        except (AttributeError, IndexError, TypeError):
            return
        usage = getattr(message, "usage_metadata", None) if isinstance(message, AIMessage) else None
        if usage:
            with self._lock:
                self.tokens_in += usage.get("input_tokens", 0)
                self.tokens_out += usage.get("output_tokens", 0)

    def on_tool_start(
        self, serialized: dict[str, Any], input_str: str, *, run_id: UUID, **kwargs: Any
    ) -> None:
        name = (serialized or {}).get("name") or kwargs.get("name") or "tool"
        with self._lock:
            self.tool_calls += 1
            self._tool_started[run_id] = (name, time.monotonic())
        self._writer.emit("tool_call", tool=name, args=truncate(input_str, 2_000))

    def on_tool_end(self, output: Any, *, run_id: UUID, **kwargs: Any) -> None:
        self._tool_finished(run_id, error=None, output=output)

    def on_tool_error(self, error: BaseException, *, run_id: UUID, **kwargs: Any) -> None:
        self._tool_finished(run_id, error=repr(error), output=None)

    def _tool_finished(self, run_id: UUID, error: str | None, output: Any) -> None:
        with self._lock:
            name, started = self._tool_started.pop(run_id, ("tool", time.monotonic()))
        text = getattr(output, "content", output)
        self._writer.emit(
            "tool_result",
            tool=name,
            duration_ms=round((time.monotonic() - started) * 1000),
            error=error,
            output=truncate(None if text is None else str(text), 2_000),
        )

    def stats(self) -> dict[str, int]:
        with self._lock:
            return {
                "llm_calls": self.llm_calls,
                "tool_calls": self.tool_calls,
                "tokens_in": self.tokens_in,
                "tokens_out": self.tokens_out,
            }
