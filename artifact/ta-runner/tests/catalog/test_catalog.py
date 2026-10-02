import json

from ta_runner.__main__ import main
from ta_runner.catalog.catalog import build_catalog


def test_catalog_lists_upstream_providers_models_and_analysts():
    catalog = build_catalog()

    providers = {p["id"]: p for p in catalog["providers"]}
    assert {"openai", "anthropic", "google", "deepseek", "ollama"} <= providers.keys()
    assert providers["deepseek"]["api_key_env"] == "DEEPSEEK_API_KEY"
    assert providers["ollama"]["api_key_env"] is None
    assert providers["deepseek"]["quick_models"], "deepseek offers quick models"
    assert all(m["id"] != "custom" for p in providers.values() for m in p["quick_models"])
    assert providers["groq"]["custom_model_allowed"] is True
    assert [a["id"] for a in catalog["analysts"]] == ["market", "social", "news", "fundamentals"]
    assert catalog["asset_types"] == ["stock", "crypto"]
    assert catalog["upstream_version"] == "0.5.1"
    assert catalog["defaults"]["llm_provider"]


def test_catalog_command_prints_one_json_object(capsys):
    assert main(["catalog"]) == 0

    assert json.loads(capsys.readouterr().out)["providers"]
