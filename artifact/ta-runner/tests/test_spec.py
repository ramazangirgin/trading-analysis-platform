import json

import pytest

from ta_runner.spec import RunSpec, SpecError


def test_normalizes_ticker_provider_and_analyst_order(spec_dict):
    spec = RunSpec.from_dict(spec_dict)

    assert spec.ticker == "NVDA"
    assert spec.llm_provider == "openai"
    assert spec.analysts == ("market", "news")
    assert spec.max_debate_rounds == 1
    assert spec.asset_type == "stock"


def test_defaults_to_all_analysts(spec_dict):
    del spec_dict["analysts"]

    assert RunSpec.from_dict(spec_dict).analysts == ("market", "social", "news", "fundamentals")


@pytest.mark.parametrize("ticker", ["BRK.B", "THYAO.IS", "BTC-USD", "^GSPC", "7203.T"])
def test_accepts_real_ticker_shapes(spec_dict, ticker):
    spec_dict["ticker"] = ticker

    assert RunSpec.from_dict(spec_dict).ticker == ticker


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("ticker", "../etc"),
        ("ticker", "NV DA"),
        ("trade_date", "2026-9-25"),
        ("trade_date", "yesterday"),
        ("run_id", "../../x"),
        ("analysts", ["market", "astrology"]),
        ("analysts", []),
        ("asset_type", "bond"),
        ("max_debate_rounds", 0),
        ("max_debate_rounds", True),
        ("deep_think_llm", "gpt; rm -rf /"),
        ("config_overrides", ["not", "a", "dict"]),
    ],
)
def test_rejects_invalid_values(spec_dict, field, value):
    spec_dict[field] = value

    with pytest.raises(SpecError):
        RunSpec.from_dict(spec_dict)


@pytest.mark.parametrize("field", ["run_id", "ticker", "trade_date", "llm_provider"])
def test_rejects_missing_required_fields(spec_dict, field):
    del spec_dict[field]

    with pytest.raises(SpecError, match=field):
        RunSpec.from_dict(spec_dict)


def test_reads_spec_file(tmp_path, spec_dict):
    path = tmp_path / "spec.json"
    path.write_text(json.dumps(spec_dict), encoding="utf-8")

    assert RunSpec.from_file(path).run_id == "r_test"


def test_reports_unreadable_spec_file(tmp_path):
    path = tmp_path / "spec.json"
    path.write_text("{not json", encoding="utf-8")

    with pytest.raises(SpecError, match="cannot read spec"):
        RunSpec.from_file(path)
