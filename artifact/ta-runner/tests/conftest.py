import io
import json

import pytest

from ta_runner.events import EventWriter


class CapturedEvents:
    def __init__(self) -> None:
        self.stream = io.StringIO()
        self.writer = EventWriter("r_test", self.stream)

    @property
    def events(self) -> list[dict]:
        return [json.loads(line) for line in self.stream.getvalue().splitlines()]

    def of_type(self, event_type: str) -> list[dict]:
        return [e for e in self.events if e["type"] == event_type]


@pytest.fixture
def captured() -> CapturedEvents:
    return CapturedEvents()


@pytest.fixture
def spec_dict() -> dict:
    return {
        "run_id": "r_test",
        "ticker": "nvda",
        "trade_date": "2026-09-25",
        "analysts": ["news", "market"],
        "llm_provider": "OpenAI",
        "deep_think_llm": "gpt-5.4-mini",
        "quick_think_llm": "gpt-5.4-mini",
    }
