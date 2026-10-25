"""Tests of scripts/quiet.sh against throwaway commands."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path

QUIET = Path(__file__).resolve().parents[1] / "quiet.sh"


class QuietTest(unittest.TestCase):
    def run_quiet(self, name, script, quiet=True):
        with tempfile.TemporaryDirectory() as tmp:
            env = {k: v for k, v in os.environ.items() if k != "AGENT_QUIET"}
            env["QUIET_LOG_DIR"] = tmp
            if quiet:
                env["AGENT_QUIET"] = "1"
            result = subprocess.run(
                ["bash", str(QUIET), name, "sh", "-c", script],
                env=env,
                capture_output=True,
                text=True,
            )
            log = Path(tmp, f"{name}.log")
            return result, log.read_text() if log.exists() else None

    def test_without_agent_quiet_output_is_passed_through(self):
        result, log = self.run_quiet("t", "echo a; echo b >&2; exit 3", quiet=False)
        self.assertEqual(3, result.returncode)
        self.assertEqual("a\n", result.stdout)
        self.assertEqual("b\n", result.stderr)
        self.assertIsNone(log)

    def test_success_prints_one_line_and_writes_the_log(self):
        result, log = self.run_quiet("t", "echo a; echo b >&2")
        self.assertEqual(0, result.returncode)
        self.assertEqual(1, len(result.stdout.splitlines()))
        self.assertRegex(result.stdout, r"^ok: t \(\d+s; full output: .*t\.log\)\n$")
        self.assertEqual("a\nb\n", log)

    def test_failure_prints_excerpt_and_keeps_status(self):
        script = (
            "for i in $(seq 1 100); do echo noise $i; done;"
            "echo 'Error: boom'; echo 'the line after';"
            "for i in $(seq 101 200); do echo noise $i; done; exit 7"
        )
        result, log = self.run_quiet("t", script)
        self.assertEqual(7, result.returncode)
        lines = result.stdout.splitlines()
        self.assertRegex(lines[0], r"^failed: t \(exit 7; full output: .*t\.log\)$")
        excerpt = lines[1:]
        self.assertIn("Error: boom", excerpt)
        self.assertIn("the line after", excerpt)
        self.assertIn("noise 200", excerpt)
        self.assertNotIn("noise 50", excerpt)
        self.assertEqual(len(excerpt), len(set(excerpt)))
        self.assertEqual(2 + 30, len(excerpt))
        self.assertEqual(202, len(log.splitlines()))

    def test_excerpt_is_limited_to_60_matching_lines(self):
        script = "for i in $(seq 1 500); do echo error $i; done; exit 1"
        result, _ = self.run_quiet("t", script)
        self.assertLessEqual(len(result.stdout.splitlines()) - 1, 60 + 30)
        self.assertIn("error 500", result.stdout)

    def test_tail_line_already_matched_is_not_repeated(self):
        result, _ = self.run_quiet("t", "echo 'failed once'; echo x; exit 1")
        self.assertEqual(["failed once", "x"], result.stdout.splitlines()[1:])


if __name__ == "__main__":
    unittest.main()
