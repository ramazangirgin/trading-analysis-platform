from uuid import uuid4

from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, LLMResult

from ta_runner.engine.callbacks import EventCallbackHandler
from ta_runner.pricing.pricing import CostMeter, PriceTable
from ta_runner.protocol.events import EventWriter


def _end(handler, run_id, model_in_response=None, cache_read=0, metadata=None):
    message = AIMessage(
        content="x",
        usage_metadata={
            "input_tokens": 1_000_000,
            "output_tokens": 0,
            "total_tokens": 1_000_000,
            "input_token_details": {"cache_read": cache_read},
        },
        response_metadata=metadata
        or ({"model_name": model_in_response} if model_in_response else {}),
    )
    handler.on_llm_end(LLMResult(generations=[[ChatGeneration(message=message)]]), run_id=run_id)


def test_prices_each_call_by_the_model_it_asked_for(captured, monkeypatch):
    monkeypatch.delenv("TA_RUNNER_PRICES", raising=False)
    handler = EventCallbackHandler(
        EventWriter("r_1", captured.stream), CostMeter(PriceTable.load(), "anthropic")
    )

    first, second = uuid4(), uuid4()
    handler.on_chat_model_start(
        {}, [], run_id=first, invocation_params={"model": "claude-opus-5-5"}
    )
    handler.on_chat_model_start(
        {}, [], run_id=second, invocation_params={"model_name": "claude-haiku-4-5"}
    )
    # The response names a dated snapshot; the requested id is what the price list knows.
    _end(handler, first, model_in_response="claude-opus-5-5-20260901", cache_read=500_000)
    _end(handler, second)

    stats = handler.stats()
    assert stats["llm_calls"] == 2
    assert stats["tokens_in"] == 2_000_000
    # opus: 500k * 4.00 + 500k * 0.20; haiku: 1M * 1.00
    assert stats["cost_usd"] == 2.0 + 0.1 + 1.0


def test_cost_is_unknown_when_a_model_has_no_price(captured):
    handler = EventCallbackHandler(
        EventWriter("r_1", captured.stream), CostMeter(PriceTable.load(), "ollama")
    )
    run_id = uuid4()
    handler.on_chat_model_start({}, [], run_id=run_id, invocation_params={"model": "qwen3:latest"})
    _end(handler, run_id)

    assert handler.stats()["cost_usd"] is None


def test_reads_deepseek_cache_hits_from_its_own_usage_field(captured):
    handler = EventCallbackHandler(
        EventWriter("r_1", captured.stream), CostMeter(PriceTable.load(), "deepseek")
    )
    run_id = uuid4()
    handler.on_chat_model_start(
        {}, [], run_id=run_id, invocation_params={"model": "deepseek-v4-pro"}
    )
    _end(handler, run_id, metadata={"token_usage": {"prompt_cache_hit_tokens": 1_000_000}})

    # All 1M input tokens were cache hits: 0.022 off-peak, or twice that at peak hours.
    assert handler.stats()["cost_usd"] in (0.022, 0.044)
