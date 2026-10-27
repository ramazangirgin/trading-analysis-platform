"""Tests of dashboard.py: the step graph of an agent process, from a record.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import os
import re
import tempfile
import unittest

import dashboard

T0 = 1_760_000_000
REPO = "https://github.com/o/r"
PR = "https://github.com/o/r/pull/7"
BRANCH = "plan/61-x"

PLAN = """# Plan

## Work packages

### WP1: First part

- **Status**: done
- **Depends on**: none

### WP2: Second, with `code`

- **Status**: open
- **Depends on**: WP1

### WP3: Third

- **Status**: not done: out of scope
- **Depends on**: WP1
"""


def tsv(*events):
    return "".join(f"{T0 + at}\t{kind}\t{parent}\t{name}\t{text}\n" for at, kind, parent, name, text in events)


def marker(step, round_=0, **extra):
    pairs = "".join(f" {k}={v}" for k, v in extra.items())
    return f"<!-- agent-step step={step} round={round_}{pairs} -->"


def comment(step, round_=0, **extra):
    return {"url": f"{REPO}/pull/7#c-{step}-{round_}", "body": marker(step, round_, **extra) + "\nbody"}


class GraphTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = directory.name

    def record(self, events="", labels=("agent",), alive=True, comments=(), plan_text=PLAN, draft=True, **pr):
        path = ""
        if events:
            path = os.path.join(self.directory, "progress.tsv")
            with open(path, "w", encoding="utf-8") as f:
                f.write(events)
        return {
            "branch": BRANCH,
            "repo_url": REPO,
            "pr": {"number": 7, "url": PR, "state": "OPEN", "isDraft": draft, "labels": [{"name": n} for n in labels], **pr},
            "plan_text": plan_text,
            "comments": list(comments),
            "ci_runs": [],
            "progress_file": path,
            "run_alive": alive,
        }

    def analyse(self, record, at=0, **options):
        return dashboard.analyse(record, T0 + at, **options)

    def states(self, process):
        return [(n.label, n.state) for n in process["nodes"]]

    def test_plan_review_without_progress(self):
        process = self.analyse(self.record(labels=("plan",), alive=False))
        self.assertEqual(
            self.states(process),
            [
                ("plan", "waiting"),
                ("plan approved", "todo"),
                ("WP1", "done"),
                ("WP2", "todo"),
                ("WP3", "done"),
                ("checks", "todo"),
            ],
        )
        self.assertEqual((process["stage"], process["waiting_for"]), ("plan review", "developer"))

    def test_plan_review_with_every_package_open(self):
        text = PLAN.replace("done\n", "open\n").replace("not done: out of scope", "open")
        process = self.analyse(self.record(labels=("plan",), alive=False, plan_text=text))
        self.assertEqual([s for _, s in self.states(process)][2:], ["todo"] * 4)

    def test_mid_implementation_with_a_running_package(self):
        events = tsv(
            (0, "begin", "", "plan", ""),
            (60, "end", "", "plan", "approved"),
            (100, "begin", "", "implement", ""),
            (110, "begin", "implement", "WP1 (1 of 3)", ""),
            (200, "end", "implement", "WP1 (1 of 3)", ""),
            (210, "begin", "implement", "WP2 (2 of 3)", ""),
        )
        process = self.analyse(self.record(events), at=210 + 12 * 60 + 5)
        self.assertEqual(
            self.states(process),
            [
                ("plan", "done"),
                ("plan approved", "done"),
                ("WP1", "done"),
                ("WP2", "running"),
                ("WP3", "done"),
                ("checks", "todo"),
            ],
        )
        self.assertEqual(process["nodes"][3].minutes, 12)
        self.assertEqual((process["stage"], process["waiting_for"]), ("agent loop", "nobody"))
        self.assertEqual(process["step"], "implement › WP2 (2 of 3)")

    def test_a_package_not_done_is_done_with_a_note(self):
        events = tsv(
            (100, "begin", "", "implement", ""),
            (110, "begin", "implement", "WP2 (2 of 3)", ""),
            (120, "milestone", "implement", "WP2 (2 of 3)", "WP2 (2 of 3): not done: out of scope"),
            (130, "end", "implement", "WP2 (2 of 3)", ""),
        )
        node = self.analyse(self.record(events), at=200)["nodes"][3]
        self.assertEqual((node.state, node.note), ("done", "not done"))
        self.assertIn('n4["WP2<br/>not done"]:::done', dashboard.graph(self.record(events), T0 + 200))

    ROUND_TWO = tsv(
        (0, "begin", "", "plan", ""),
        (60, "end", "", "plan", "approved"),
        (100, "begin", "", "implement", ""),
        (110, "begin", "implement", "WP1 (1 of 3)", ""),
        (120, "end", "implement", "WP1 (1 of 3)", ""),
        (130, "begin", "implement", "final checks", ""),
        (140, "end", "implement", "final checks", ""),
        (150, "end", "", "implement", ""),
        (160, "begin", "", "CI abc1234", ""),
        (260, "end", "", "CI abc1234", "failed: Backend"),
        (270, "begin", "", "CI fix 1", ""),
        (300, "end", "", "CI fix 1", ""),
        (310, "begin", "", "CI def5678", ""),
        (400, "end", "", "CI def5678", "passed"),
        (410, "begin", "", "review R1", ""),
        (420, "milestone", "review R1", "", "review R1: 2 findings (0 CRITICAL, 1 MAJOR, 1 MINOR)"),
        (430, "end", "", "review R1", ""),
        (440, "begin", "", "fix R1", ""),
        (500, "end", "", "fix R1", ""),
        (510, "begin", "", "CI 0123456", ""),
        (600, "end", "", "CI 0123456", "passed"),
        (610, "begin", "", "review R2", ""),
    )

    def test_review_round_two(self):
        process = self.analyse(self.record(self.ROUND_TWO), at=610 + 5 * 60, max_rounds=2)
        self.assertEqual(
            self.states(process)[6:],
            [
                ("CI abc1234", "failed"),
                ("CI fix 1", "done"),
                ("CI def5678", "done"),
                ("review R1", "done"),
                ("fix R1", "done"),
                ("CI 0123456", "done"),
                ("review R2", "running"),
            ],
        )
        self.assertEqual(process["nodes"][-1].minutes, 5)
        self.assertEqual(process["step"], "review R2")

    def test_next_step_after_each_kind_of_step(self):
        def next_after(extra, **options):
            events = self.ROUND_TWO.rsplit(f"{T0 + 610}", 1)[0] + extra
            process = self.analyse(self.record(events), at=2000, **options)
            return process["nodes"][-1]

        self.assertEqual(next_after("").label, "review R2")
        approved = next_after(
            tsv(
                (610, "begin", "", "review R2", ""),
                (620, "milestone", "review R2", "", "review R2: approved abc1234"),
                (630, "end", "", "review R2", ""),
            )
        )
        self.assertEqual((approved.label, approved.state), ("finalise", "todo"))
        findings = next_after(tsv((610, "begin", "", "review R2", ""), (620, "milestone", "review R2", "", "review R2: 1 findings"), (630, "end", "", "review R2", "")))
        self.assertEqual(findings.label, "fix R2")

    def test_green_ci_after_the_last_round_goes_to_finalise(self):
        events = self.ROUND_TWO.rsplit(f"{T0 + 610}", 1)[0]
        last = self.analyse(self.record(events), at=2000, max_rounds=1)["nodes"][-1]
        self.assertEqual(last.label, "finalise")

    def test_next_step_after_fix_update_and_finalise(self):
        base = self.ROUND_TWO.rsplit(f"{T0 + 510}", 1)[0]
        after_fix = self.analyse(self.record(base), at=2000)["nodes"][-1]
        self.assertEqual(after_fix.label, "CI")
        updated = tsv((510, "begin", "", "update with main", ""), (520, "end", "", "update with main", ""))
        self.assertEqual(self.analyse(self.record(base + updated), at=2000)["nodes"][-1].label, "CI")

    def test_failed_ci_expects_a_ci_fix_until_the_limit(self):
        events = self.ROUND_TWO.split(f"{T0 + 270}")[0]
        process = self.analyse(self.record(events), at=2000, max_ci_fixes=2)
        self.assertEqual(self.states(process)[-2:], [("CI abc1234", "failed"), ("CI fix 1", "todo")])
        process = self.analyse(self.record(events), at=2000, max_ci_fixes=0)
        self.assertEqual(self.states(process)[-1], ("CI abc1234", "failed"))

    def test_a_running_ci_is_waiting_for_ci(self):
        events = self.ROUND_TWO.split(f"{T0 + 260}")[0]
        process = self.analyse(self.record(events), at=300)
        self.assertEqual(self.states(process)[-1], ("CI abc1234", "waiting"))
        self.assertEqual(process["waiting_for"], "CI")

    FINISHED = ROUND_TWO + tsv(
        (620, "milestone", "review R2", "", "review R2: approved abc1234"),
        (630, "end", "", "review R2", ""),
        (640, "begin", "", "finalise", ""),
        (700, "end", "", "finalise", ""),
    )

    def test_ready_for_review(self):
        process = self.analyse(self.record(self.FINISHED, labels=(), draft=False), at=800)
        self.assertEqual(self.states(process)[-2:], [("finalise", "done"), ("ready for review", "done")])
        self.assertEqual(process["stage"], "ready for review")

    def test_an_open_step_without_a_live_run_is_stopped(self):
        process = self.analyse(self.record(self.ROUND_TWO, alive=False), at=900)
        self.assertEqual(self.states(process)[-1], ("review R2", "stopped"))
        self.assertEqual((process["stage"], process["waiting_for"]), ("stopped", "developer"))

    def test_an_interrupted_step_is_stopped(self):
        events = self.ROUND_TWO + tsv((650, "end", "", "review R2", "interrupted"))
        process = self.analyse(self.record(events, alive=False), at=900)
        self.assertEqual(self.states(process)[-1], ("review R2", "stopped"))

    def test_a_stop_marker_ends_the_last_step_as_stopped(self):
        events = self.ROUND_TWO.split(f"{T0 + 610}")[0]
        record = self.record(events, labels=(), alive=False, comments=[comment("stop")])
        process = self.analyse(record, at=900)
        self.assertEqual(self.states(process)[-1], ("CI 0123456", "stopped"))
        self.assertEqual(process["stage"], "stopped")

    def test_markers_without_progress(self):
        record = self.record(
            comments=[
                comment("implement"),
                comment("review", 1, approved=0, head="abc"),
                comment("fix", 1),
                comment("review", 2, approved=1, head="def"),
            ],
            labels=("agent",),
            alive=False,
            headRefOid="def5678aaaa",
        )
        record["ci_runs"] = [{"headSha": "def5678aaaa", "url": f"{REPO}/actions/runs/9", "status": "completed", "conclusion": "failure"}]
        process = self.analyse(record, max_ci_fixes=3)
        self.assertEqual(
            self.states(process)[-6:],
            [
                ("checks", "done"),
                ("review R1", "done"),
                ("fix R1", "done"),
                ("review R2", "done"),
                ("CI def5678", "failed"),
                ("CI fix 1", "todo"),
            ],
        )
        self.assertEqual(process["stage"], "agent loop")

    def test_markers_fallback_expects_ci_after_the_implementation_without_a_run(self):
        record = self.record(comments=[comment("implement")], alive=False, headRefOid="def5678aaaa")
        self.assertEqual(self.states(self.analyse(record))[-2:], [("checks", "done"), ("CI", "todo")])

    def test_without_a_plan_the_packages_come_from_the_steps(self):
        events = tsv((100, "begin", "", "implement", ""), (110, "begin", "implement", "all", ""))
        process = self.analyse(self.record(events, plan_text=""), at=200)
        self.assertEqual(self.states(process)[2:4], [("all", "running"), ("checks", "todo")])

    def test_escapes_labels(self):
        self.assertEqual(dashboard.escape('a "b" #1 <c>'), "a #quot;b#quot; #35;1 #lt;c#gt;")

    def test_the_block_has_nodes_edges_classes_and_links(self):
        text = dashboard.graph(self.record(self.ROUND_TWO), T0 + 610 + 120)
        lines = text.splitlines()
        self.assertEqual(lines[:2], ["```mermaid", "flowchart LR"])
        self.assertEqual(lines[-1], "```")
        self.assertIn('  n1["plan"]:::done', lines)
        self.assertIn('  n13["review R2<br/>2 min"]:::running', lines)
        self.assertIn("  n1 --> n2", lines)
        for state in dashboard.STATES:
            self.assertTrue(any(line.startswith(f"  classDef {state} ") for line in lines))
        self.assertEqual(sum(1 for line in lines if re.fullmatch(r"  n\d+ --> n\d+", line)), 12)

    def test_links(self):
        comments = [comment("ci-fix", 1), comment("review", 1), comment("fix", 1)]
        record = self.record(self.ROUND_TWO, comments=comments)
        record["ci_runs"] = [{"headSha": "abc1234ffff", "url": f"{REPO}/actions/runs/5", "status": "completed", "conclusion": "failure"}]
        text = dashboard.graph(record, T0 + 800)
        plan_file = f"{REPO}/blob/{BRANCH}/.plans/61-x.md"
        self.assertIn(f'click n1 href "{plan_file}" _blank', text)
        self.assertIn(f'click n2 href "{PR}" _blank', text)
        self.assertIn(f'click n3 href "{plan_file}#wp1-first-part" _blank', text)
        self.assertIn(f'click n6 href "{PR}/files" _blank', text)
        self.assertIn(f'href "{REPO}/actions/runs/5" _blank', text)
        self.assertIn(f'href "{REPO}/pull/7#c-ci-fix-1" _blank', text)
        self.assertIn(f'href "{REPO}/pull/7#c-review-1" _blank', text)
        self.assertNotIn("c-review-2", text)

    def test_slug_of_a_heading(self):
        self.assertEqual(dashboard.slug("WP2: Second, with `code`"), "wp2-second-with-code")


if __name__ == "__main__":
    unittest.main()
