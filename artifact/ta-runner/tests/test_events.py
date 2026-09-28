import json

from ta_runner.events import EventWriter, truncate


def test_writes_envelope_with_increasing_seq_to_stream_and_file(tmp_path, captured):
    events_file = tmp_path / "events.jsonl"
    writer = EventWriter("r_1", captured.stream, events_file)

    writer.emit("agent_status", agent="Market Analyst", status="in_progress")
    writer.emit("log", level="INFO", logger="x", message="Türkçe karakterler")
    writer.close()

    lines = captured.stream.getvalue().splitlines()
    assert lines == events_file.read_text(encoding="utf-8").splitlines()
    first, second = (json.loads(line) for line in lines)
    assert first["v"] == 1
    assert first["run_id"] == "r_1"
    assert (first["seq"], second["seq"]) == (1, 2)
    assert first["ts"].endswith("Z")
    assert first["type"] == "agent_status"
    assert first["agent"] == "Market Analyst"
    assert "Türkçe" in lines[1]


def test_truncate_marks_cut_text():
    assert truncate("short", 10) == "short"
    assert truncate(None) is None
    assert truncate("x" * 15, 10) == "x" * 10 + "… [truncated 5 chars]"
