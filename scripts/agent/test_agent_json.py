"""Tests of the usage figures in agent_json.py, including runs that were interrupted, and of the
phase milestones of its `stream`.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import io
import json
import os
import tempfile
import unittest
from unittest import mock

import agent_json


def message(id, input_tokens, output_tokens, parent=None, cache_read=0, cache_creation=0):
    return {
        "type": "assistant",
        "parent_tool_use_id": parent,
        "message": {
            "id": id,
            "usage": {
                "input_tokens": input_tokens,
                "cache_creation_input_tokens": cache_creation,
                "cache_read_input_tokens": cache_read,
                "output_tokens": output_tokens,
            },
        },
    }


RESULT = {
    "type": "result",
    "is_error": False,
    "total_cost_usd": 1.25,
    "usage": {
        "input_tokens": 30,
        "cache_creation_input_tokens": 20,
        "cache_read_input_tokens": 400,
        "output_tokens": 60,
    },
}

# Two messages: "a" arrives as two events (one per content block) with the same usage, "b" is a
# subagent's.
EVENTS = [
    message("a", 10, 20, cache_read=100, cache_creation=5),
    message("a", 10, 20, cache_read=100, cache_creation=5),
    message("b", 7, 3, parent="toolu_1", cache_read=50),
]


class UsageTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def path(self, name):
        return os.path.join(self.dir.name, name)

    def write_stream(self, name, events):
        with open(self.path(name), "w", encoding="utf-8") as f:
            for event in events:
                f.write(json.dumps(event) + "\n")
        return self.path(name)

    def write_result(self, name, result=RESULT):
        with open(self.path(name), "w", encoding="utf-8") as f:
            json.dump(result, f)
        return self.path(name)

    def test_complete_run_is_its_result(self):
        result = self.write_result("a.json")
        total = agent_json.usage([result])
        self.assertEqual((total["in"], total["cached"], total["out"], total["cost"]), (50, 400, 60, 1.25))
        self.assertEqual(total["tokens"], 510)
        self.assertFalse(total["partial"])

    def test_stream_with_a_result_event_equals_the_result(self):
        stream = self.write_stream("a.jsonl", EVENTS + [RESULT])
        self.assertEqual(agent_json.usage([stream]), agent_json.usage([self.write_result("b.json")]))

    def test_cut_off_stream_sums_its_messages_once_each(self):
        stream = self.write_stream("a.jsonl", EVENTS)
        total = agent_json.usage([stream])
        # a: 10 + 5 in, 100 cached, 20 out; b (subagent): 7 in, 50 cached, 3 out.
        self.assertEqual((total["in"], total["cached"], total["out"]), (22, 150, 23))
        self.assertEqual(total["cost"], 0.0)
        self.assertTrue(total["partial"])

    def test_later_event_of_a_message_may_have_more_output(self):
        stream = self.write_stream("a.jsonl", [message("a", 10, 1), message("a", 10, 40)])
        self.assertEqual(agent_json.usage([stream])["out"], 40)

    def test_result_path_of_a_cut_off_run_reads_its_stream(self):
        self.write_stream("a.jsonl", EVENTS)
        total = agent_json.usage([self.path("a.json")])
        self.assertEqual(total["tokens"], 22 + 150 + 23)
        self.assertTrue(total["partial"])

    def test_result_and_its_stream_are_not_counted_twice(self):
        result = self.write_result("a.json")
        stream = self.write_stream("a.jsonl", EVENTS)
        total = agent_json.usage([result, stream])
        self.assertEqual(total["tokens"], 510)
        self.assertFalse(total["partial"])

    def test_complete_and_interrupted_runs_together(self):
        result = self.write_result("a.json")
        stream = self.write_stream("b.jsonl", EVENTS)
        total = agent_json.usage([result, stream])
        self.assertEqual(total["tokens"], 510 + 195)
        self.assertEqual(total["cost"], 1.25)
        self.assertTrue(total["partial"])

    def test_missing_run_adds_nothing(self):
        self.assertEqual(agent_json.usage([self.path("none.json")])["tokens"], 0)

    def test_marker_is_partial_only_for_a_cut_off_run(self):
        complete = agent_json.usage_marker(agent_json.usage([self.write_result("a.json")]))
        cut = agent_json.usage_marker(agent_json.usage([self.write_stream("b.jsonl", EVENTS)]))
        self.assertNotIn("partial", complete)
        self.assertTrue(cut.endswith(" partial=1"))
        self.assertEqual(agent_json.parse_marker(cut)["tokens"], "195")


class UsageTableTest(unittest.TestCase):
    def test_table_without_a_partial_row_has_exact_costs(self):
        rows = [{"step": "implement", "round": "0", "tokens_in": "1", "tokens_cached": "2", "tokens_out": "3", "cost_usd": "0.5"}]
        table = agent_json.usage_table(rows)
        self.assertNotIn("at least", table)
        self.assertIn("**$0.50**", table)

    def test_table_with_a_partial_row_says_at_least(self):
        rows = [
            {"step": "implement", "round": "0", "tokens_in": "1", "tokens_cached": "2", "tokens_out": "3", "cost_usd": "0.5", "partial": "1"},
            {"step": "review", "round": "1", "tokens_in": "1", "tokens_cached": "2", "tokens_out": "3", "cost_usd": "1.0"},
        ]
        lines = agent_json.usage_table(rows).splitlines()
        self.assertIn("at least $0.50", lines[2])
        self.assertNotIn("at least", lines[3])
        self.assertIn("**at least $1.50** (an interrupted run's cost is unknown)", lines[4])


def bash(id, command):
    block = {"type": "tool_use", "id": id, "name": "Bash", "input": {"command": command}}
    return {"type": "assistant", "message": {"content": [block]}}


def tool_result(id, is_error=False):
    block = {"type": "tool_result", "tool_use_id": id, "is_error": is_error, "content": "x"}
    return {"type": "user", "message": {"content": [block]}}


class PhaseTest(unittest.TestCase):
    def run_stream(self, events, *label):
        """Runs `stream` on the events; returns (stderr lines, result path)."""
        with tempfile.TemporaryDirectory() as tmp:
            out, log = os.path.join(tmp, "r.json"), os.path.join(tmp, "r.jsonl")
            stdin = io.StringIO("".join(json.dumps(e) + "\n" for e in events))
            stderr = io.StringIO()
            with mock.patch("sys.stdin", stdin), mock.patch("sys.stderr", stderr):
                agent_json.stream(out, log, *label)
            with open(out, encoding="utf-8") as f:
                self.assertEqual(json.load(f), RESULT)
            return stderr.getvalue().splitlines()

    def milestones(self, lines):
        return [line for line in lines if line.startswith("agent: >> ")]

    def events(self):
        return [
            bash("1", "./gradlew :backend:test"),
            tool_result("1"),
            bash("2", "mise run agent:test"),
            tool_result("2"),
            bash("3", "mise run check"),
            tool_result("3", is_error=True),
            bash("4", "mise run check"),
            tool_result("4"),
            bash("5", "git commit -m x"),
            tool_result("5"),
            RESULT,
        ]

    def test_phases_are_written_once_while_they_last(self):
        lines = self.run_stream(self.events(), "WP1")
        self.assertEqual(
            self.milestones(lines),
            [
                "agent: >> WP1: tests",
                "agent: >> WP1: mise run check",
                "agent: >> WP1: mise run check failed",
                "agent: >> WP1: mise run check",
                "agent: >> WP1: mise run check passed",
                "agent: >> WP1: commit",
            ],
        )

    def test_tool_lines_stay(self):
        lines = self.run_stream(self.events(), "WP1")
        self.assertTrue(any("Bash ./gradlew :backend:test" in line for line in lines))
        self.assertTrue(any("Bash git commit -m x" in line for line in lines))

    def test_without_a_label_there_are_no_milestones(self):
        lines = self.run_stream(self.events())
        self.assertEqual(self.milestones(lines), [])
        self.assertTrue(any("Bash mise run check" in line for line in lines))

    def test_other_commands_do_not_end_a_phase(self):
        events = [bash("1", "mise run test"), bash("2", "ls"), bash("3", "mise run test"), RESULT]
        self.assertEqual(self.milestones(self.run_stream(events, "x")), ["agent: >> x: tests"])

    def test_phase_patterns(self):
        cases = [
            ("mise run check", "mise run check"),
            ("cd a && mise run check", "mise run check"),
            ("mise run checkstyle", None),
            ("mise run format", None),
            ("git commit -m 'x'", "commit"),
            ("git status", None),
            ("mise run test", "tests"),
            ("mise run runner-test", "tests"),
            ("mise run agent:test", "tests"),
            ("mise run skill:test", "tests"),
            ("mise run e2e", "tests"),
            ("mise run build", None),
            ("./gradlew :backend:test", "tests"),
            ("./gradlew --console=plain :backend:test --tests Foo", "tests"),
            ("./gradlew integrationTest", "tests"),
            ("./gradlew build", None),
            ("./gradlew :backend:testClasses", None),
            ("uv run pytest -q", "tests"),
            ("uv run python x.py", None),
            ("artifact/frontend/with-node.sh pnpm test", "tests"),
            ("artifact/frontend/with-node.sh pnpm exec vitest run", "tests"),
            ("artifact/frontend/with-node.sh pnpm build", None),
        ]
        for command, phase in cases:
            with self.subTest(command=command):
                self.assertEqual(agent_json.phase_of(command), phase)


if __name__ == "__main__":
    unittest.main()
