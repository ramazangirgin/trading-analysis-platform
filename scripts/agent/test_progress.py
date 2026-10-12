"""Tests of progress.py: the steps, iterations and timeline of a run, from progress.tsv.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import time
import unittest

import progress

T0 = 1_760_000_000  # a fixed start, in UTC below
MARKER = "<!-- agent-progress plan=61-x -->"


def tsv(*events):
    """progress.tsv lines from (seconds after T0, kind, parent, name, text) tuples."""
    return [f"{T0 + at}\t{kind}\t{parent}\t{name}\t{text}\n" for at, kind, parent, name, text in events]


def usage(tokens, cost, ms=0):
    return f"tokens={tokens} cost_usd={cost} duration_ms={ms} turns=3"


A_RUN = tsv(
    (0, "begin", "", "plan", ""),
    (600, "end", "", "plan", "approved"),
    (700, "begin", "", "implement", ""),
    (710, "begin", "implement", "WP1 (1 of 2)", ""),
    (711, "milestone", "implement", "WP1 (1 of 2)", "WP1 (1 of 2): started"),
    (900, "usage", "implement", "WP1 (1 of 2)", usage(1_000_000, 1.5, 180_000)),
    (910, "milestone", "implement", "WP1 (1 of 2)", "WP1 (1 of 2): done (2 commits)"),
    (910, "end", "implement", "WP1 (1 of 2)", ""),
    (920, "begin", "implement", "WP2 (2 of 2)", ""),
    (1000, "milestone", "implement", "WP2 (2 of 2)", "WP2 (2 of 2): not finished, resuming the agent"),
    (1500, "usage", "implement", "WP2 (2 of 2)", usage(2_000_000, 2.5, 500_000)),
    (1510, "milestone", "implement", "WP2 (2 of 2)", "WP2 (2 of 2): not done: out of scope"),
    (1510, "end", "implement", "WP2 (2 of 2)", ""),
    (1600, "milestone", "implement", "", "draft pull request #7 opened: https://x/7"),
    (1600, "end", "", "implement", ""),
    (1610, "begin", "", "CI abc1234", ""),
    (2510, "end", "", "CI abc1234", "failed: Backend"),
    (2520, "begin", "", "CI fix 1", ""),
    (2900, "usage", "CI fix 1", "", usage(500_000, 0.5)),
    (2900, "milestone", "CI fix 1", "", "CI fix 1: 1 commit(s) pushed"),
    (2900, "end", "", "CI fix 1", ""),
    (2910, "begin", "", "CI def5678", ""),
    (3510, "end", "", "CI def5678", "passed"),
    (3520, "begin", "", "review R1", ""),
    (3900, "usage", "review R1", "", usage(800_000, 3.0)),
    (3900, "milestone", "review R1", "", "review R1: 2 findings (0 CRITICAL, 1 MAJOR, 1 MINOR)"),
    (3900, "end", "", "review R1", ""),
)


class RenderTest(unittest.TestCase):
    def setUp(self):
        self.addCleanup(setattr, progress, "LOCALTIME", progress.LOCALTIME)
        progress.LOCALTIME = time.gmtime

    def render(self, lines, now=None, **options):
        return progress.render(progress.parse(lines), MARKER, "plan/61-x", now=now, **options)

    def test_starts_with_the_marker(self):
        self.assertTrue(self.render(A_RUN).startswith(MARKER + "\n"))

    def test_lists_every_step_with_times_duration_and_result(self):
        text = self.render(A_RUN, pr="7")
        self.assertIn("| 1 | plan | 08:53 | 09:03 | 10m 00s | – | – | approved |", text)
        self.assertIn("| 2 | implement | 09:05 | 09:20 | 15m 00s | 3.0M | $4.00 | draft pull request #7 opened: https://x/7 |", text)
        self.assertIn("|  | ↳ WP1 (1 of 2) | 09:05 | 09:08 | 3m 20s | 1.0M | $1.50 | WP1 (1 of 2): done (2 commits) |", text)
        self.assertIn("| 4 | CI fix 1 |", text)
        self.assertIn("**Pull request**: #7", text)

    def test_totals_the_time_tokens_and_cost(self):
        text = self.render(A_RUN)
        self.assertIn("**Elapsed**: 1h 05m", text)
        self.assertIn("**Agents**: 4 run(s), 11m 20s", text)
        self.assertIn("**Tokens**: 4.3M · **Cost** (API prices): $7.50", text)

    def test_counts_the_iterations_per_kind_of_step(self):
        text = self.render(A_RUN, max_rounds=2, max_ci_fixes=3)
        self.assertIn("| work package | 2 | 13m 10s | 3.0M | $4.00 | 1 done, 1 not done |", text)
        self.assertIn("| CI | 2 | 25m 00s | – | – | 1 passed, 1 failed |", text)
        self.assertIn("| CI fix | 1 of 3 |", text)
        self.assertIn("| review | 1 of 2 | 6m 20s | 800k | $3.00 | R1: 2 findings (0 CRITICAL, 1 MAJOR, 1 MINOR) |", text)

    def test_points_at_slow_repeated_and_retried_steps(self):
        lines = A_RUN + tsv((4000, "begin", "", "review R1", ""), (4100, "end", "", "review R1", ""))
        text = self.render(lines)
        self.assertIn("Slowest steps: CI abc1234 15m 00s", text)
        self.assertIn("Waiting for CI: 25m 00s over 2 run(s)", text)
        self.assertIn("Run more than once: review R1 ×2", text)
        self.assertIn("Retries: 09:10 WP2 (2 of 2): not finished, resuming the agent", text)
        self.assertIn("Steps beyond the plan's path: CI fix ×1", text)

    def test_status_running_with_the_open_sub_step(self):
        text = self.render(A_RUN[:9], now=T0 + 1000)
        self.assertIn("🔄 running: **implement › WP2 (2 of 2)** (for 5m 00s)", text)
        self.assertIn("| running | 1m 20s … |", text)

    def test_status_failed_names_the_reason(self):
        lines = tsv(
            (0, "begin", "", "review R1", ""),
            (5, "milestone", "review R1", "", "failed: the agent wrote no result"),
            (6, "end", "", "review R1", "failed"),
        )
        self.assertIn("❌ failed at **review R1**: failed: the agent wrote no result", self.render(lines))

    def test_a_failed_step_takes_the_reason_of_its_sub_step(self):
        lines = tsv(
            (0, "begin", "", "implement", ""),
            (1, "begin", "implement", "final checks", ""),
            (2, "milestone", "implement", "final checks", "failed: checks broke"),
            (3, "end", "", "implement", "failed"),
        )
        self.assertIn("| 1 | implement | 08:53 | 08:53 | 3s | – | – | failed: checks broke |", self.render(lines))

    def test_status_stopped_and_ready(self):
        stopped = tsv((0, "milestone", "", "", "stopping: CI still fails after 3 fix attempts"))
        self.assertIn("⏸️ stopped: CI still fails after 3 fix attempts", self.render(tsv((0, "begin", "", "fix R1", ""), (1, "end", "", "fix R1", "")) + [stopped[0].replace(str(T0), str(T0 + 2))]))
        ready = tsv((0, "begin", "", "finalise", ""), (9, "end", "", "finalise", ""))
        self.assertIn("✅ ready for review", self.render(ready))

    def test_a_step_begun_again_while_open_was_interrupted(self):
        lines = tsv((0, "begin", "", "implement", ""), (50, "begin", "", "implement", ""), (90, "end", "", "implement", ""))
        text = self.render(lines)
        self.assertIn("| 1 | implement | 08:53 | 08:54 | 50s | – | – | interrupted |", text)
        self.assertIn("| implement | 2 | 1m 30s | – | – | 1 interrupted |", text)

    def test_sorts_a_step_recorded_afterwards(self):
        lines = tsv((700, "begin", "", "implement", "")) + tsv((0, "begin", "", "plan", ""), (600, "end", "", "plan", ""))
        text = self.render(lines, now=T0 + 800)
        self.assertLess(text.index("| plan |"), text.index("| implement |"))

    def test_escapes_pipes_and_skips_bad_lines(self):
        lines = ["garbage\n"] + tsv((0, "begin", "", "plan", ""), (1, "note", "", "", "a | b"))
        self.assertIn("a \\| b", self.render(lines))

    def test_without_events(self):
        self.assertIn("No step has run yet.", self.render([]))


if __name__ == "__main__":
    unittest.main()
