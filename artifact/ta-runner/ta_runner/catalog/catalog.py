"""What the installed TradingAgents offers: providers, models, analysts.

Read from upstream at run time so neither the backend nor the UI keeps a hand-maintained
list (PLAN.md section 10).
"""

from __future__ import annotations

from typing import Any

from ta_runner.engine import compat
from ta_runner.engine.agent_map import ANALYST_AGENTS
from ta_runner.protocol.spec import ANALYST_ORDER, ASSET_TYPES

# Upstream's picker entry that means "type your own model id".
_CUSTOM = "custom"


def build_catalog() -> dict[str, Any]:
    providers = []
    for provider, modes in compat.model_options().items():
        options = {
            mode: [
                {"id": model_id, "label": label}
                for label, model_id in modes.get(mode, [])
                if model_id != _CUSTOM
            ]
            for mode in ("quick", "deep")
        }
        providers.append(
            {
                "id": provider,
                "api_key_env": compat.api_key_env(provider),
                "custom_model_allowed": any(
                    model_id == _CUSTOM for entries in modes.values() for _, model_id in entries
                ),
                "quick_models": options["quick"],
                "deep_models": options["deep"],
            }
        )
    return {
        "upstream_version": compat.upstream_version(),
        "defaults": compat.default_models(),
        "providers": providers,
        "analysts": [{"id": key, "agent": ANALYST_AGENTS[key]} for key in ANALYST_ORDER],
        "asset_types": list(ASSET_TYPES),
    }
