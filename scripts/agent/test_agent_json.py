"""Tests of the usage figures in agent_json.py, including runs that were interrupted, and of the
phase milestones of its `stream`.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import io
import json
import os
import tempfile
import unittest
from datetime import datetime, timezone
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

    def test_get_reads_the_subtype_of_a_max_turns_result(self):
        result = self.write_result("wp-WP1.json", dict(RESULT, subtype="error_max_turns", is_error=True))
        out = io.StringIO()
        with mock.patch("sys.stdout", out):
            agent_json.main(["agent_json.py", "get", result, "subtype"])
        self.assertEqual(out.getvalue().strip(), "error_max_turns")

    def test_usage_marker_sums_a_package_and_its_continuation(self):
        first = self.write_result("wp-WP1.json", dict(RESULT, subtype="error_max_turns", is_error=True))
        second = self.write_result("wp-WP1-2.json")
        marker = agent_json.parse_marker(agent_json.usage_marker(agent_json.usage([first, second])))
        self.assertEqual(marker["tokens"], str(2 * 510))
        self.assertNotIn("partial", agent_json.usage_marker(agent_json.usage([first, second])))


def init(model):
    return {"type": "system", "subtype": "init", "model": model}


def limit_event(five_hour, seven_day):
    windows = {"five_hour": {"utilization": five_hour}, "seven_day": {"utilization": seven_day}}
    return {"type": "rate_limit_event", "rate_limit_info": {"unifiedWindows": windows}}


class LimitsTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def run_of(self, name, events, result=RESULT):
        with open(os.path.join(self.dir.name, name + ".jsonl"), "w", encoding="utf-8") as f:
            for event in events:
                f.write(json.dumps(event) + "\n")
        with open(os.path.join(self.dir.name, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(result, f)
        return os.path.join(self.dir.name, name + ".json")

    def test_first_and_last_limit_event_of_a_run(self):
        run = self.run_of("a", [init("claude-opus-5-5"), limit_event(0.08, 0.30), limit_event(0.10, 0.31), limit_event(0.12, 0.32), RESULT])
        total = agent_json.usage([run])
        self.assertEqual(total["limits"], {"5h": [8, 12], "7d": [30, 32]})
        self.assertEqual(total["models"], ["claude-opus-5-5"])
        marker = agent_json.parse_marker(agent_json.usage_marker(total))
        self.assertEqual(marker["limit5h_before"], "8")
        self.assertEqual(marker["limit5h_after"], "12")
        self.assertEqual(marker["limit7d_before"], "30")
        self.assertEqual(marker["limit7d_after"], "32")
        self.assertEqual(marker["model"], "claude-opus-5-5")

    def test_run_without_limit_events_has_no_keys(self):
        run = self.run_of("a", [RESULT])
        marker = agent_json.usage_marker(agent_json.usage([run]))
        self.assertNotIn("limit", marker)
        self.assertNotIn("model=", marker)
        table = agent_json.usage_table([agent_json.parse_marker(marker) | {"step": "fix", "round": "1"}])
        self.assertEqual(table.splitlines()[2].count("n/a"), 3)

    def test_model_falls_back_to_the_results_model_usage(self):
        run = self.run_of("a", [], dict(RESULT, modelUsage={"claude-sonnet-5-5": {}}))
        self.assertEqual(agent_json.usage([run])["models"], ["claude-sonnet-5-5"])

    def test_runs_of_a_step_take_the_first_before_and_the_last_after(self):
        first = self.run_of("a", [init("m1"), limit_event(0.10, 0.20), limit_event(0.15, 0.25)])
        second = self.run_of("b", [init("m2"), limit_event(0.16, 0.26), limit_event(0.22, 0.30)])
        third = self.run_of("c", [init("m1")])
        total = agent_json.usage([first, second, third])
        self.assertEqual(total["limits"], {"5h": [10, 22], "7d": [20, 30]})
        self.assertEqual(total["models"], ["m1", "m2"])

    def test_cut_off_stream_has_limits_and_model(self):
        path = os.path.join(self.dir.name, "a.jsonl")
        with open(path, "w", encoding="utf-8") as f:
            for event in [init("m1"), limit_event(0.5, 0.6), message("a", 1, 1), limit_event(0.52, 0.61)]:
                f.write(json.dumps(event) + "\n")
        total = agent_json.usage([path])
        self.assertEqual(total["limits"], {"5h": [50, 52], "7d": [60, 61]})
        self.assertTrue(total["partial"])


class ToolsTest(unittest.TestCase):
    def tools_of(self, events):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "a.jsonl")
            with open(path, "w", encoding="utf-8") as f:
                for event in events:
                    f.write(json.dumps(event) + "\n")
            return agent_json.stream_tools(path)

    def test_tools_of_the_init_message(self):
        event = dict(init("m1"), tools=["Read", "Grep", "LSP"])
        self.assertEqual(self.tools_of([message("a", 1, 1), event, RESULT]), ["Read", "Grep", "LSP"])

    def test_stream_without_init_message_has_no_tools(self):
        self.assertEqual(self.tools_of([message("a", 1, 1), RESULT]), [])


class UsageTableTest(unittest.TestCase):
    def test_row_shows_model_and_limits(self):
        row = {"step": "review", "round": "1", "model": "m1,m2", "tokens_in": "1", "tokens_cached": "2", "tokens_out": "3",
               "cost_usd": "0.5", "limit5h_before": "8", "limit5h_after": "12", "limit7d_before": "30", "limit7d_after": "30"}
        line = agent_json.usage_table([row]).splitlines()[2]
        self.assertEqual(line, "| review | 1 | m1, m2 | 1 | 2 | 3 | $0.50 | 8% → 12% (+4) | 30% → 30% (+0) |")

    def test_old_marker_without_the_new_keys_renders(self):
        rows = [{"step": "implement", "round": "0", "tokens_in": "1", "tokens_cached": "2", "tokens_out": "3", "cost_usd": "0.5"}]
        lines = agent_json.usage_table(rows).splitlines()
        self.assertEqual(lines[2], "| implement | 0 | n/a | 1 | 2 | 3 | $0.50 | n/a | n/a |")
        self.assertEqual(lines[3], "| **Total** | | | **1** | **2** | **3** | **$0.50** | n/a | n/a |")

    def test_total_is_the_first_before_to_the_last_after(self):
        base = {"round": "0", "tokens_in": "1", "tokens_cached": "1", "tokens_out": "1", "cost_usd": "1"}
        rows = [
            dict(base, step="implement", limit5h_before="10", limit5h_after="20", limit7d_before="40", limit7d_after="41"),
            dict(base, step="review", limit5h_before="21", limit5h_after="30", limit7d_before="41", limit7d_after="43"),
        ]
        total = agent_json.usage_table(rows).splitlines()[4]
        self.assertTrue(total.endswith("| **10% → 30% (+20)** | **40% → 43% (+3)** |"))

    def test_total_says_at_least_with_a_row_without_limits(self):
        base = {"round": "0", "tokens_in": "1", "tokens_cached": "1", "tokens_out": "1", "cost_usd": "1"}
        rows = [
            dict(base, step="implement", limit5h_before="10", limit5h_after="20", limit7d_before="40", limit7d_after="41"),
            dict(base, step="fix"),
        ]
        total = agent_json.usage_table(rows).splitlines()[4]
        self.assertIn("**at least 10% → 20% (+10)**", total)
        self.assertIn("**at least 40% → 41% (+1)**", total)

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


def turn(id, at, model="claude-opus-5-5", input_tokens=100, output_tokens=10, cache_read=1000, cache_creation=0):
    event = message(id, input_tokens, output_tokens, cache_read=cache_read, cache_creation=cache_creation)
    event["timestamp"] = at
    event["message"]["model"] = model
    return event


class SessionUsageTest(unittest.TestCase):
    START = datetime(2026, 10, 1, 10, 0, tzinfo=timezone.utc).timestamp()

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def transcript(self, events, name="s.jsonl"):
        path = os.path.join(self.dir.name, name)
        with open(path, "w", encoding="utf-8") as f:
            for event in events:
                f.write(json.dumps(event) + "\n")
        return path

    def at(self, minutes):
        return self.START + minutes * 60

    def test_sums_the_window_and_counts_a_message_once(self):
        path = self.transcript(
            [
                turn("before", "2026-10-01T09:59:00.000Z"),
                turn("a", "2026-10-01T10:01:00.000Z", cache_creation=50),
                turn("a", "2026-10-01T10:01:01.000Z", cache_creation=50),
                turn("b", "2026-10-01T10:02:00.000Z", model="claude-sonnet-5-5"),
                turn("after", "2026-10-01T10:30:00.000Z"),
                {"type": "user", "timestamp": "2026-10-01T10:03:00.000Z", "message": {"content": "hi"}},
                turn("synthetic", "2026-10-01T10:03:00.000Z", model="<synthetic>"),
            ]
        )
        total = agent_json.session_usage(path, self.at(0), self.at(10))
        self.assertEqual((total["in"], total["cached"], total["out"], total["turns"]), (250, 2000, 20, 2))
        self.assertEqual(total["models"], ["claude-opus-5-5", "claude-sonnet-5-5"])
        opus = 100 * 5.0 + 50 * 6.25 + 1000 * 0.5 + 10 * 25.0
        sonnet = 100 * 3.0 + 1000 * 0.3 + 10 * 15.0
        self.assertAlmostEqual(total["cost"], (opus + sonnet) / 1e6)
        marker = agent_json.parse_marker(agent_json.usage_marker(total))
        self.assertEqual(marker["model"], "claude-opus-5-5,claude-sonnet-5-5")

    def test_unknown_model_has_tokens_and_no_cost(self):
        path = self.transcript([turn("a", "2026-10-01T10:01:00.000Z"), turn("b", "2026-10-01T10:02:00.000Z", model="mystery-1")])
        total = agent_json.session_usage(path, self.at(0), self.at(10))
        self.assertEqual(total["tokens"], 2 * 1110)
        self.assertIsNone(total["cost"])
        marker = agent_json.usage_marker(total)
        self.assertNotIn("cost_usd", marker)
        row = agent_json.parse_marker(marker) | {"step": "plan", "round": "0"}
        lines = agent_json.usage_table([row]).splitlines()
        self.assertIn("| n/a |", lines[2])
        self.assertIn("**at least $0.00**", lines[3])

    def test_dated_model_name_is_priced(self):
        path = self.transcript([turn("a", "2026-10-01T10:01:00.000Z", model="claude-haiku-5-5-20260101")])
        self.assertIsNotNone(agent_json.session_usage(path, self.at(0), self.at(10))["cost"])

    def test_windows_to_skip(self):
        path = self.transcript([turn("a", "2026-10-01T10:01:00.000Z"), turn("b", "2026-10-01T10:05:00.000Z")])
        total = agent_json.session_usage(path, self.at(0), self.at(10), skip=[(self.at(4), self.at(6))])
        self.assertEqual(total["turns"], 1)

    def test_no_transcript_or_no_message_in_the_window_is_empty(self):
        self.assertIsNone(agent_json.session_usage(os.path.join(self.dir.name, "none.jsonl"), 0, 1e12))
        path = self.transcript([turn("a", "2026-10-01T10:01:00.000Z")])
        self.assertIsNone(agent_json.session_usage(path, self.at(20), self.at(30)))

    def test_latest_transcript_of_a_directory(self):
        folder = os.path.join(self.dir.name, "-Users-me-git-my-repo")
        os.mkdir(folder)
        old, new = os.path.join(folder, "old.jsonl"), os.path.join(folder, "new.jsonl")
        for path, mtime in ((old, 1000), (new, 2000)):
            open(path, "w").close()
            os.utime(path, (mtime, mtime))
        self.assertEqual(agent_json.latest_transcript("/Users/me/git/my_repo", self.dir.name), new)
        self.assertIsNone(agent_json.latest_transcript("/Users/me/other", self.dir.name))

    def test_orchestration_window_leaves_out_plan_and_update(self):
        lines = [
            "2000\tbegin\t\timplement\t",
            "2500\tbegin\t\tupdate with main\t",
            "2600\tend\t\tupdate with main\tmerged",
            "1000\tbegin\t\tplan\t",
            "1500\tusage\tplan\t\ttokens=1",
            "1500\tend\t\tplan\tapproved",
            "bad line",
        ]
        start, skip = agent_json.orchestration_window(lines)
        self.assertEqual(start, 2000)
        self.assertEqual(sorted(skip), [(1000, 1500), (2500, 2600)])

    def test_probe_limits_are_the_last_event_of_its_stream(self):
        path = self.transcript([limit_event(0.08, 0.30)], name="probe.jsonl")
        _, limits = agent_json.stream_facts(path)
        self.assertEqual({k: v[1] for k, v in limits.items()}, {"5h": 8, "7d": 30})


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


def comments(*markers):
    return "\n".join(f"<!-- agent-step {m} -->\nbody" for m in markers)


class ApprovedHeadTest(unittest.TestCase):
    def approved(self, *markers):
        with mock.patch("sys.stdin", io.StringIO(comments(*markers))), mock.patch("sys.stdout", io.StringIO()) as out:
            agent_json.main(["", "approved-head"])
        return out.getvalue().strip()

    def steps(self, *markers):
        with mock.patch("sys.stdin", io.StringIO(comments(*markers))), mock.patch("sys.stdout", io.StringIO()) as out:
            agent_json.main(["", "steps"])
        return out.getvalue().splitlines()

    APPROVE = "step=review round=1 approved=1 head=aaa"

    def test_an_update_from_the_approved_head_moves_the_approval(self):
        self.assertEqual(self.approved(self.APPROVE, "step=update-main round=1 from=aaa head=bbb"), "bbb")

    def test_a_chain_of_updates_is_followed(self):
        self.assertEqual(
            self.approved(
                self.APPROVE,
                "step=update-main round=1 from=aaa head=bbb",
                "step=update-main round=1 from=bbb head=ccc",
            ),
            "ccc",
        )

    def test_an_update_from_another_head_changes_nothing(self):
        self.assertEqual(self.approved(self.APPROVE, "step=update-main round=1 from=zzz head=bbb"), "aaa")

    def test_an_update_before_the_approval_is_ignored(self):
        self.assertEqual(self.approved("step=update-main round=0 from=aaa head=bbb", self.APPROVE), "aaa")

    def test_a_review_with_findings_approves_nothing(self):
        self.assertEqual(
            self.approved(self.APPROVE, "step=review round=2 approved=0 head=bbb", "step=update-main round=2 from=bbb head=ccc"),
            "",
        )

    def test_steps_lists_the_update_with_its_round(self):
        self.assertEqual(self.steps("step=update-main round=2 from=aaa head=bbb tokens=70"), ["update-main 2 70"])


if __name__ == "__main__":
    unittest.main()
