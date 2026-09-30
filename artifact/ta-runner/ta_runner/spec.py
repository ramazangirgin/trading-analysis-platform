"""Run spec: the only input the platform gives the runner.

Validated strictly here even though the backend validates too: the runner is a separate
deliverable and must not turn a malformed spec into a path or a provider call.
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path
from typing import Any

ANALYST_ORDER = ("market", "social", "news", "fundamentals")
ASSET_TYPES = ("stock", "crypto")

# Exchange suffixes (NVDA, BRK.B, THYAO.IS, 7203.T), crypto pairs (BTC-USD) and indices (^GSPC).
_TICKER = re.compile(r"^\^?[A-Z0-9][A-Z0-9.\-=]{0,19}$")
_RUN_ID = re.compile(r"^[A-Za-z0-9_\-]{1,64}$")
_MODEL = re.compile(r"^[A-Za-z0-9._:/@\-]{1,128}$")
_PROVIDER = re.compile(r"^[a-z0-9_\-]{1,32}$")


class SpecError(ValueError):
    """The spec is malformed; the runner exits without starting the graph."""


@dataclass(frozen=True)
class RunSpec:
    run_id: str
    ticker: str
    trade_date: str
    analysts: tuple[str, ...]
    llm_provider: str
    deep_think_llm: str
    quick_think_llm: str
    asset_type: str = "stock"
    max_debate_rounds: int = 1
    max_risk_discuss_rounds: int = 1
    output_language: str = "English"
    checkpoint_enabled: bool = False
    backend_url: str | None = None
    # Passed through to the upstream config as is (e.g. temperature, data_vendors).
    config_overrides: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def from_file(cls, path: Path) -> RunSpec:
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise SpecError(f"cannot read spec {path}: {exc}") from exc
        return cls.from_dict(raw)

    @classmethod
    def from_env(cls, name: str) -> RunSpec:
        """The spec as JSON in an environment variable: how a runner container gets it."""
        text = os.environ.get(name)
        if not text:
            raise SpecError(f"environment variable {name} is not set")
        try:
            raw = json.loads(text)
        except json.JSONDecodeError as exc:
            raise SpecError(f"cannot read spec from {name}: {exc}") from exc
        return cls.from_dict(raw)

    @classmethod
    def from_dict(cls, raw: Any) -> RunSpec:
        if not isinstance(raw, dict):
            raise SpecError("spec must be a JSON object")

        def required(key: str) -> Any:
            if raw.get(key) in (None, ""):
                raise SpecError(f"missing required field: {key}")
            return raw[key]

        run_id = _match(_RUN_ID, "run_id", required("run_id"))
        ticker = _match(_TICKER, "ticker", str(required("ticker")).strip().upper())
        trade_date = _iso_date(required("trade_date"))

        analysts_raw = raw.get("analysts")
        if analysts_raw is None:
            analysts_raw = list(ANALYST_ORDER)
        if not isinstance(analysts_raw, list) or not analysts_raw:
            raise SpecError("analysts must be a non-empty list")
        unknown = sorted(set(analysts_raw) - set(ANALYST_ORDER))
        if unknown:
            raise SpecError(f"unknown analysts: {unknown}")
        # Upstream runs analysts in a fixed order regardless of how they were picked.
        analysts = tuple(a for a in ANALYST_ORDER if a in analysts_raw)

        asset_type = raw.get("asset_type", "stock")
        if asset_type not in ASSET_TYPES:
            raise SpecError(f"asset_type must be one of {ASSET_TYPES}")

        overrides = raw.get("config_overrides") or {}
        if not isinstance(overrides, dict):
            raise SpecError("config_overrides must be an object")

        return cls(
            run_id=run_id,
            ticker=ticker,
            trade_date=trade_date,
            analysts=analysts,
            llm_provider=_match(_PROVIDER, "llm_provider", str(required("llm_provider")).lower()),
            deep_think_llm=_match(_MODEL, "deep_think_llm", required("deep_think_llm")),
            quick_think_llm=_match(_MODEL, "quick_think_llm", required("quick_think_llm")),
            asset_type=asset_type,
            max_debate_rounds=_rounds(raw, "max_debate_rounds"),
            max_risk_discuss_rounds=_rounds(raw, "max_risk_discuss_rounds"),
            output_language=str(raw.get("output_language") or "English"),
            checkpoint_enabled=bool(raw.get("checkpoint_enabled", False)),
            backend_url=raw.get("backend_url") or None,
            config_overrides=overrides,
        )

    def summary(self) -> dict[str, Any]:
        return {
            "ticker": self.ticker,
            "trade_date": self.trade_date,
            "asset_type": self.asset_type,
            "analysts": list(self.analysts),
            "llm_provider": self.llm_provider,
            "deep_think_llm": self.deep_think_llm,
            "quick_think_llm": self.quick_think_llm,
            "max_debate_rounds": self.max_debate_rounds,
            "max_risk_discuss_rounds": self.max_risk_discuss_rounds,
            "output_language": self.output_language,
            "checkpoint_enabled": self.checkpoint_enabled,
        }


def _match(pattern: re.Pattern[str], name: str, value: Any) -> str:
    if not isinstance(value, str) or not pattern.fullmatch(value):
        raise SpecError(f"invalid {name}: {value!r}")
    return value


def _iso_date(value: Any) -> str:
    try:
        parsed = date.fromisoformat(str(value))
    except ValueError as exc:
        raise SpecError(f"trade_date must be YYYY-MM-DD, got {value!r}") from exc
    if parsed.isoformat() != value:
        raise SpecError(f"trade_date must be YYYY-MM-DD, got {value!r}")
    return value


def _rounds(raw: dict[str, Any], key: str) -> int:
    value = raw.get(key, 1)
    if isinstance(value, bool) or not isinstance(value, int) or not 1 <= value <= 10:
        raise SpecError(f"{key} must be an integer between 1 and 10")
    return value
