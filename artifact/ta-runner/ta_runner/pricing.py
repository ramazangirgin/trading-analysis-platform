"""LLM cost: prices.json (shipped) plus an optional override file, applied per LLM call.

Prices are per 1M tokens and can depend on the call's time (DeepSeek's peak hours, a dated price
change) and size (long-context tiers), so each call is priced on its own. A run's cost is only
known when every call's model has a price; otherwise it stays None rather than a partial sum.
"""

from __future__ import annotations

import json
import logging
import os
from dataclasses import dataclass
from datetime import UTC, date, datetime
from importlib import resources
from pathlib import Path
from typing import Any

log = logging.getLogger(__name__)

OVERRIDE_ENV = "TA_RUNNER_PRICES"
PER_TOKENS = 1_000_000


@dataclass(frozen=True)
class Usage:
    """One LLM call. ``input_tokens`` includes ``cached_tokens``, as LangChain reports it."""

    input_tokens: int
    output_tokens: int
    cached_tokens: int = 0


class PriceTable:
    def __init__(self, providers: dict[str, Any]) -> None:
        self._providers = providers

    @classmethod
    def load(cls, override: Path | None = None) -> PriceTable:
        shipped = json.loads(
            resources.files(__package__).joinpath("prices.json").read_text("utf-8")
        )
        providers = shipped["providers"]
        path = override or _override_path()
        if path and path.is_file():
            try:
                extra = json.loads(path.read_text("utf-8")).get("providers", {})
            except (OSError, ValueError) as exc:
                log.warning("Ignoring unreadable price file %s: %s", path, exc)
                extra = {}
            for provider, entry in extra.items():
                merged = providers.setdefault(provider, {"models": {}, "aliases": {}})
                merged.setdefault("models", {}).update(entry.get("models", {}))
                merged.setdefault("aliases", {}).update(entry.get("aliases", {}))
        return cls(providers)

    def cost(self, provider: str, model: str, usage: Usage, at: datetime) -> float | None:
        """USD for one call, or None when the model has no price for that date."""
        price = self._price(provider, model, at.date())
        if price is None:
            return None
        peak = price.get("peak")
        long = price.get("long_context")
        if long and usage.input_tokens > long["above_input_tokens"]:
            price = long
        cached = min(usage.cached_tokens, usage.input_tokens)
        cached_price = price.get("cached_input", price["input"])
        usd = (
            (usage.input_tokens - cached) * price["input"]
            + cached * cached_price
            + usage.output_tokens * price["output"]
        ) / PER_TOKENS
        return usd * _peak_multiplier(peak, at)

    def _entries(self, provider: str, model: str) -> list[dict[str, Any]] | None:
        entry = self._providers.get(provider)
        if not entry:
            return None
        models = entry.get("models", {})
        name = entry.get("aliases", {}).get(model, model)
        return models.get(name)

    def _price(self, provider: str, model: str, day: date) -> dict[str, Any] | None:
        for period in self._entries(provider, model) or []:
            if "from" in period and day < date.fromisoformat(period["from"]):
                continue
            if "until" in period and day > date.fromisoformat(period["until"]):
                continue
            return period
        return None


def _peak_multiplier(peak: dict[str, Any] | None, at: datetime) -> float:
    if not peak:
        return 1.0
    utc = at.astimezone(UTC)
    if utc.weekday() not in peak.get("weekdays", range(7)):
        return 1.0
    for start, end in peak.get("hours_utc", []):
        if start <= utc.hour < end:
            return float(peak["multiplier"])
    return 1.0


def _override_path() -> Path | None:
    value = os.environ.get(OVERRIDE_ENV)
    return Path(value) if value else None


class CostMeter:
    """Adds up a run's calls. Thread-safety is the caller's (the callback handler holds a lock)."""

    def __init__(self, table: PriceTable, provider: str) -> None:
        self._table = table
        self._provider = provider
        self._usd = 0.0
        self._unpriced: set[str] = set()

    def add(self, model: str | None, usage: Usage, at: datetime | None = None) -> None:
        cost = None
        if model:
            cost = self._table.cost(self._provider, model, usage, at or datetime.now(UTC))
        if cost is None:
            name = model or "?"
            if name not in self._unpriced:
                log.warning(
                    "No price for %s/%s; the run's cost stays unknown", self._provider, name
                )
            self._unpriced.add(name)
            return
        self._usd += cost

    @property
    def usd(self) -> float | None:
        return None if self._unpriced else round(self._usd, 6)
