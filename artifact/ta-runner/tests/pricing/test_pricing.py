import json
from datetime import UTC, datetime

import pytest

from ta_runner.pricing.pricing import CostMeter, PriceTable, Usage

# A Saturday (off-peak everywhere) and a Tuesday 07:30 UTC (DeepSeek peak hours).
WEEKEND = datetime(2026, 9, 26, 12, 0, tzinfo=UTC)
PEAK = datetime(2026, 9, 29, 7, 30, tzinfo=UTC)
OFF_PEAK = datetime(2026, 9, 29, 12, 0, tzinfo=UTC)


@pytest.fixture
def table(monkeypatch):
    monkeypatch.delenv("TA_RUNNER_PRICES", raising=False)
    monkeypatch.delenv("TA_RUNNER_PRICES_JSON", raising=False)
    return PriceTable.load()


def test_prices_input_cached_input_and_output_per_million(table):
    usage = Usage(input_tokens=1_000_000, output_tokens=100_000, cached_tokens=200_000)

    # 800k * 5.00 + 200k * 0.50 + 100k * 30.00, per million
    assert table.cost("openai", "gpt-5.5", usage, WEEKEND) == pytest.approx(4.0 + 0.1 + 3.0)


def test_deepseek_peak_hours_cost_twice_as_much(table):
    usage = Usage(input_tokens=100_000, output_tokens=10_000)
    off_peak = table.cost("deepseek", "deepseek-v4-pro", usage, OFF_PEAK)

    assert off_peak == pytest.approx((100_000 * 0.66 + 10_000 * 1.98) / 1_000_000)
    assert table.cost("deepseek", "deepseek-v4-pro", usage, PEAK) == pytest.approx(2 * off_peak)
    assert table.cost("deepseek", "deepseek-v4-pro", usage, WEEKEND.replace(hour=7)) == off_peak


def test_the_legacy_deepseek_flash_name_is_billed_as_flash(table):
    usage = Usage(input_tokens=10_000, output_tokens=1_000)

    assert table.cost("deepseek", "deepseek-v4-flash", usage, OFF_PEAK) == table.cost(
        "deepseek", "deepseek-flash", usage, OFF_PEAK
    )


def test_long_calls_use_the_long_context_price(table):
    short = Usage(input_tokens=272_000, output_tokens=0)
    long = Usage(input_tokens=272_001, output_tokens=0)

    assert table.cost("openai", "gpt-6-sol", short, WEEKEND) == pytest.approx(272_000 * 2.0 / 1e6)
    assert table.cost("openai", "gpt-6-sol", long, WEEKEND) == pytest.approx(272_001 * 4.0 / 1e6)


def test_dated_price_changes(table):
    usage = Usage(input_tokens=1_000_000, output_tokens=0)

    assert (
        table.cost("google", "gemini-3.8-flash", usage, datetime(2026, 12, 31, tzinfo=UTC)) == 0.75
    )
    assert table.cost("google", "gemini-3.8-flash", usage, datetime(2027, 1, 1, tzinfo=UTC)) == 1.5


def test_unknown_models_have_no_price(table):
    usage = Usage(input_tokens=1, output_tokens=1)

    assert table.cost("openai", "gpt-5.6", usage, WEEKEND) is None
    assert table.cost("ollama", "qwen3:latest", usage, WEEKEND) is None


def test_an_override_file_adds_and_replaces_models(tmp_path):
    override = tmp_path / "prices.json"
    override.write_text(
        json.dumps(
            {
                "providers": {
                    "openai": {"models": {"gpt-5.5": [{"input": 1.0, "output": 2.0}]}},
                    "ollama": {"models": {"qwen3:latest": [{"input": 0, "output": 0}]}},
                }
            }
        )
    )
    table = PriceTable.load(override)
    usage = Usage(input_tokens=1_000_000, output_tokens=1_000_000, cached_tokens=500_000)

    # No cached_input price: cached tokens cost the input price.
    assert table.cost("openai", "gpt-5.5", usage, WEEKEND) == pytest.approx(3.0)
    assert table.cost("ollama", "qwen3:latest", usage, WEEKEND) == 0
    assert table.cost("openai", "gpt-6-sol", usage, WEEKEND) is not None


def test_a_run_with_an_unpriced_call_has_no_cost(table):
    meter = CostMeter(table, "openai")
    meter.add("gpt-5.5", Usage(1_000_000, 0), WEEKEND)
    assert meter.usd == 5.0

    meter.add("gpt-5.6", Usage(10, 10), WEEKEND)
    assert meter.usd is None


def test_prices_can_come_as_json_in_the_environment(monkeypatch):
    monkeypatch.delenv("TA_RUNNER_PRICES", raising=False)
    monkeypatch.setenv(
        "TA_RUNNER_PRICES_JSON",
        json.dumps(
            {"providers": {"ollama": {"models": {"qwen3:latest": [{"input": 0, "output": 0}]}}}}
        ),
    )

    assert PriceTable.load().cost("ollama", "qwen3:latest", Usage(10, 10), WEEKEND) == 0


def test_unreadable_override_json_is_ignored(monkeypatch):
    monkeypatch.setenv("TA_RUNNER_PRICES_JSON", "{ nope")

    assert PriceTable.load().cost("openai", "gpt-5.5", Usage(1_000_000, 0), WEEKEND) == 5.0
