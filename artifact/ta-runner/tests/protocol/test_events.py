import json

import pytest

from ta_runner.protocol.events import EventWriter, truncate


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


class _BrokenStream:
    def write(self, _line):
        raise BrokenPipeError(32, "Broken pipe")

    def flush(self):
        pass


def test_keeps_writing_the_file_when_stdout_is_gone(tmp_path):
    events_file = tmp_path / "events.jsonl"
    writer = EventWriter("r_1", _BrokenStream(), events_file)

    writer.emit("agent_status", agent="Market Analyst", status="in_progress")
    writer.emit("run_finished", status="completed")
    writer.close()

    lines = [json.loads(line) for line in events_file.read_text(encoding="utf-8").splitlines()]
    assert [event["seq"] for event in lines] == [1, 2]
    assert lines[-1]["type"] == "run_finished"


def test_a_broken_stdout_without_a_file_is_an_error():
    writer = EventWriter("r_1", _BrokenStream())

    with pytest.raises(BrokenPipeError):
        writer.emit("log", message="x")


def test_truncate_marks_cut_text():
    assert truncate("short", 10) == "short"
    assert truncate(None) is None
    assert truncate("x" * 15, 10) == "x" * 10 + "… [truncated 5 chars]"
