"""Tests of transcript_stats.py with transcripts written into temporary folders.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import contextlib
import io
import json
import os
import tempfile
import unittest

import transcript_stats

OPUS = "claude-opus-5-5"


def at(second):
    return f"2026-01-01T00:00:{second:02d}+00:00"


def assistant(id, second, blocks, model=OPUS, fresh=1, written=0, cache_read=0, out=0):
    return {
        "type": "assistant",
        "timestamp": at(second),
        "message": {
            "id": id,
            "model": model,
            "content": blocks,
            "usage": {
                "input_tokens": fresh,
                "cache_creation_input_tokens": written,
                "cache_read_input_tokens": cache_read,
                "output_tokens": out,
            },
        },
    }


def use(id, name, **args):
    return {"type": "tool_use", "id": id, "name": name, "input": args}


def result(second, use_id, content):
    return {
        "type": "user",
        "timestamp": at(second),
        "message": {"content": [{"type": "tool_result", "tool_use_id": use_id, "content": content}]},
    }


def write(path, events):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        for event in events:
            f.write(json.dumps(event) + "\n")


class TranscriptStatsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.projects = os.path.join(self.tmp.name, "projects")
        self.folder = os.path.join(self.projects, "folder")
        self.state = os.path.join(self.tmp.name, "agent")
        os.makedirs(os.path.join(self.state, "7-slug"))
        with open(os.path.join(self.state, "7-slug", "wp-WP1.json"), "w") as f:
            json.dump({"session_id": "s1"}, f)
        with open(os.path.join(self.state, "7-slug", "review-1.json"), "w") as f:
            json.dump({"session_id": "s2"}, f)
        write(
            os.path.join(self.folder, "s1.jsonl"),
            [
                assistant("m1", 1, [use("u1", "Read", file_path="/a")], fresh=10, written=100, cache_read=0, out=5),
                # the same message again, as streaming repeats it per content block
                assistant("m1", 1, [use("u2", "Read", file_path="/a")], fresh=10, written=100, cache_read=0, out=5),
                result(2, "u1", "x" * 40),
                result(2, "u2", [{"type": "text", "text": "y" * 10}, {"type": "image", "source": {}}]),
                assistant("m2", 3, [use("u3", "Read", file_path="/a"), use("u4", "Read", file_path="/b")], fresh=1, written=0, cache_read=300, out=7),
                result(4, "u3", "z" * 5),
                result(4, "u4", "z" * 5),
                assistant("m3", 5, [use("u5", "Read", file_path="/b")], fresh=1, written=0, cache_read=200, out=3),
                result(6, "u5", "q"),
            ],
        )
        write(
            os.path.join(self.folder, "s2.jsonl"),
            [assistant("r1", 1, [use("v1", "Bash", command="ls")], model="mystery-1", fresh=2, cache_read=3, out=4), result(2, "v1", "files")],
        )
        write(
            os.path.join(self.folder, "s1", "subagents", "agent-1.jsonl"),
            [assistant("a1", 1, [use("w1", "Grep", pattern="x")], fresh=5, out=1), result(2, "w1", "hit")],
        )

    def report(self, **kwargs):
        steps = transcript_stats.session_steps(self.state, "7")
        paths = sorted(
            [os.path.join(self.folder, "s1.jsonl"), os.path.join(self.folder, "s2.jsonl"), os.path.join(self.folder, "s1", "subagents", "agent-1.jsonl")]
        )
        return transcript_stats.summarise(transcript_stats.collect(paths, kwargs.get("extra", []), steps, kwargs.get("since"), kwargs.get("until")))

    def test_steps_and_subagent_under_parent(self):
        report = self.report()
        steps = {s["step"]: s for s in report["steps"]}
        self.assertEqual({"implement", "review"}, set(steps))
        self.assertEqual(2, steps["implement"]["transcripts"])
        self.assertEqual(4, steps["implement"]["turns"])

    def test_tokens_and_context(self):
        t = next(t for t in self.report()["transcripts"] if t["session"] == "s1" and not t["subagent"])
        self.assertEqual(3, t["turns"])
        self.assertEqual((110, 201, 301), (t["context_first"], t["context_last"], t["context_max"]))
        self.assertEqual(112, t["uncached"])
        self.assertEqual(500, t["cached"])
        self.assertEqual(15, t["output"])

    def test_tools(self):
        tools = {row["tool"]: row for row in self.report()["tools"]}
        self.assertEqual(5, tools["Read"]["calls"])
        self.assertEqual(40 + 10 + 5 + 5 + 1, tools["Read"]["chars"])
        self.assertEqual(1, tools["Read"]["images"])
        self.assertEqual(1, tools["Bash"]["calls"])

    def test_reads_more_than_twice(self):
        again = self.report()["files_read_more_than_twice"]
        self.assertEqual([{"file": "/a", "reads": 3}], again)

    def test_unknown_model_has_no_cost(self):
        steps = {s["step"]: s for s in self.report()["steps"]}
        self.assertIsNone(steps["review"]["cost"])
        self.assertIsNotNone(steps["implement"]["cost"])
        self.assertIn("n/a", transcript_stats.markdown("7", self.report()))

    def test_since_until_cut_extra_transcript(self):
        extra = os.path.join(self.folder, "s1.jsonl")
        cut = transcript_stats.collect([], [extra], {}, 2.0 + transcript_stats.agent_json.epoch_of(at(0)), None)
        self.assertEqual(2, cut[0]["turns"])
        cut = transcript_stats.collect([], [extra], {}, transcript_stats.agent_json.epoch_of(at(0)), transcript_stats.agent_json.epoch_of(at(3)))
        self.assertEqual(1, cut[0]["turns"])

    def test_markdown_and_json_carry_the_same_numbers(self):
        folder = os.path.basename(self.folder)
        argv = ["7", "--projects", self.projects, "--folder", folder, "--state", self.state]
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            transcript_stats.main(argv + ["--json"])
        data = json.loads(out.getvalue())
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            transcript_stats.main(argv)
        text = out.getvalue()
        for tool in data["tools"]:
            self.assertIn(f"| {tool['tool']} | {tool['calls']:,} | {tool['chars']:,} |", text)
        for step in data["steps"]:
            self.assertIn(f"| {step['step']} | {step['transcripts']} | {step['turns']:,} | {step['context_max']:,} |", text)
        self.assertIn("`/a`: 3 reads", text)


if __name__ == "__main__":
    unittest.main()
