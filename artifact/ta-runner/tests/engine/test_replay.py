"""Replaying a recording through the real event flow, and recording a run to replay it."""

import json
from pathlib import Path

from ta_runner.engine.replay import CHUNKS_FILE, recording, replaying
from ta_runner.engine.runner import EXIT_COMPLETED, execute
from ta_runner.protocol.spec import RunSpec

# The recording the end-to-end UI tests replay (e2e/ at the repository root).
FIXTURE = Path(__file__).parent.parent / "fixtures" / "replay-run"
FAST = 1_000.0


def spec(spec_dict, tmp_path) -> RunSpec:
    return RunSpec.from_dict(
        {
            **spec_dict,
            "analysts": ["market", "social", "news", "fundamentals"],
            "config_overrides": {"results_dir": str(tmp_path / "results")},
        }
    )


def test_a_replayed_run_ends_with_its_decision_and_report_files(captured, spec_dict, tmp_path):
    exit_code = execute(
        spec(spec_dict, tmp_path), captured.writer, replaying(FIXTURE, FAST), "0.5.1"
    )

    assert exit_code == EXIT_COMPLETED
    assert [e["rating"] for e in captured.of_type("decision")] == ["Overweight"]
    sections = {e["section"] for e in captured.of_type("report_section")}
    assert {"market_report", "sentiment_report", "final_trade_decision"} <= sections
    tools = [t for e in captured.of_type("message") for t in e["tool_calls"]]
    assert "get_indicators" in tools
    report_dir = Path(captured.events[-1]["report_dir"])
    assert report_dir == tmp_path / "results" / "NVDA" / "2026-09-25" / "reports"
    assert "Overweight" in (report_dir / "5_portfolio" / "decision.md").read_text()


def test_recording_a_replay_gives_back_the_recording(captured, spec_dict, tmp_path):
    target = tmp_path / "recorded"

    factory = recording(replaying(FIXTURE, FAST), target)
    execute(spec(spec_dict, tmp_path), captured.writer, factory, "0.5.1")

    def chunks(path: Path) -> list[dict]:
        lines = (path / CHUNKS_FILE).read_text(encoding="utf-8").splitlines()
        return [{k: v for k, v in json.loads(line).items() if k != "at_s"} for line in lines]

    assert chunks(target) == chunks(FIXTURE)
