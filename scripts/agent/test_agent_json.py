"""Tests of the usage figures in agent_json.py, including runs that were interrupted.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import json
import os
import tempfile
import unittest

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


if __name__ == "__main__":
    unittest.main()
