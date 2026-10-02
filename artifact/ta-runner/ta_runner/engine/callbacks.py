"""LangChain callback: counts LLM/tool calls, tokens and cost, reports tool calls as events."""

from __future__ import annotations

import threading
import time
from typing import Any
from uuid import UUID

from langchain_core.callbacks import BaseCallbackHandler
from langchain_core.messages import AIMessage
from langchain_core.outputs import LLMResult

from ta_runner.pricing.pricing import CostMeter, Usage
from ta_runner.protocol.events import EventWriter, truncate


class EventCallbackHandler(BaseCallbackHandler):
    def __init__(self, writer: EventWriter, cost: CostMeter | None = None) -> None:
        super().__init__()
        self._writer = writer
        self._cost = cost
        self._lock = threading.Lock()
        self._tool_started: dict[UUID, tuple[str, float]] = {}
        # The model each running LLM call asked for, to price it when it ends.
        self._models: dict[UUID, str | None] = {}
        self.llm_calls = 0
        self.tool_calls = 0
        self.tokens_in = 0
        self.tokens_out = 0

    def on_chat_model_start(self, serialized: dict[str, Any], messages: Any, **kwargs: Any) -> None:
        self._llm_started(kwargs)

    def on_llm_start(self, serialized: dict[str, Any], prompts: list[str], **kwargs: Any) -> None:
        self._llm_started(kwargs)

    def _llm_started(self, kwargs: dict[str, Any]) -> None:
        with self._lock:
            self.llm_calls += 1
            if kwargs.get("run_id") is not None:
                self._models[kwargs["run_id"]] = _requested_model(kwargs)

    def on_llm_end(self, response: LLMResult, **kwargs: Any) -> None:
        with self._lock:
            requested = self._models.pop(kwargs.get("run_id"), None)
        try:
            message = response.generations[0][0].message  # type: ignore[attr-defined]
        except (AttributeError, IndexError, TypeError):
            return
        usage = getattr(message, "usage_metadata", None) if isinstance(message, AIMessage) else None
        if usage:
            tokens_in = usage.get("input_tokens", 0)
            tokens_out = usage.get("output_tokens", 0)
            cached = _cached_tokens(usage, message.response_metadata or {})
            model = requested or (message.response_metadata or {}).get("model_name")
            with self._lock:
                self.tokens_in += tokens_in
                self.tokens_out += tokens_out
                if self._cost:
                    self._cost.add(model, Usage(tokens_in, tokens_out, cached))

    def on_llm_error(self, error: BaseException, **kwargs: Any) -> None:
        with self._lock:
            self._models.pop(kwargs.get("run_id"), None)

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

    def stats(self) -> dict[str, Any]:
        with self._lock:
            return {
                "llm_calls": self.llm_calls,
                "tool_calls": self.tool_calls,
                "tokens_in": self.tokens_in,
                "tokens_out": self.tokens_out,
                "cost_usd": self._cost.usd if self._cost else None,
            }


def _cached_tokens(usage: dict[str, Any], response_metadata: dict[str, Any]) -> int:
    """Input tokens served from the provider's prompt cache (cheaper), however it reports them."""
    cached = (usage.get("input_token_details") or {}).get("cache_read") or 0
    if not cached:
        # DeepSeek's own field, which LangChain's OpenAI mapping does not know.
        cached = (response_metadata.get("token_usage") or {}).get("prompt_cache_hit_tokens") or 0
    return int(cached)


def _requested_model(kwargs: dict[str, Any]) -> str | None:
    """The model a call asked for, as the provider's chat model class reports it."""
    params = kwargs.get("invocation_params") or {}
    metadata = kwargs.get("metadata") or {}
    for value in (params.get("model"), params.get("model_name"), metadata.get("ls_model_name")):
        if isinstance(value, str) and value:
            return value
    return None
