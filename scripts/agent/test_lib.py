"""Tests of lib.sh: retries of gh and git calls, and the announced exits.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)

Bash snippets source lib.sh with a fake gh and git on PATH that fail a given number of times.
"""

import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

AGENT_DIR = Path(__file__).resolve().parent

FAKE_COMMAND = """#!/bin/sh
counter="$FAKE_DIR/$(basename "$0").count"
n=$(cat "$counter" 2>/dev/null || echo 0)
n=$((n + 1))
echo "$n" >"$counter"
if [ "$n" -le "${FAKE_FAILS:-0}" ]; then
  echo "$FAKE_STDERR" >&2
  [ -z "${FAKE_SLEEP:-}" ] || sleep "$FAKE_SLEEP"
  exit "${FAKE_STATUS:-1}"
fi
echo ok
"""

CONNECTION_RESET = "fatal: unable to access 'https://github.com/o/r.git/': Connection reset by peer"


class LibTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp(prefix="test-lib-"))
        self.addCleanup(subprocess.run, ["rm", "-rf", str(self.dir)])
        bin_dir = self.dir / "bin"
        bin_dir.mkdir()
        for name in ("gh", "git"):
            (bin_dir / name).write_text(FAKE_COMMAND)
            (bin_dir / name).chmod(0o755)

    def run_snippet(self, body, **env):
        script = self.dir / "snippet.sh"
        script.write_text(f'. "$AGENT_SCRIPTS/lib.sh"\n{body}\n')
        environment = {
            **os.environ,
            "PATH": f"{self.dir / 'bin'}:{os.environ['PATH']}",
            "AGENT_SCRIPTS": str(AGENT_DIR),
            "AGENT_REPO_ROOT": str(self.dir),
            "AGENT_TMP": str(self.dir / "tmp"),
            "AGENT_RETRY_DELAY": "0",
            "FAKE_DIR": str(self.dir),
            "FAKE_STDERR": CONNECTION_RESET,
            **env,
        }
        return subprocess.run(["bash", str(script)], env=environment, capture_output=True, text=True, timeout=60)

    def calls(self, name):
        return int((self.dir / f"{name}.count").read_text())

    def test_a_transient_error_is_retried_until_it_succeeds(self):
        run = self.run_snippet("gh pr list", FAKE_FAILS="2")
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(self.calls("gh"), 3)
        self.assertIn("agent: >> retrying gh pr (1 of 3): ", run.stderr)
        self.assertIn("agent: >> retrying gh pr (2 of 3): ", run.stderr)
        self.assertEqual(run.stdout.strip(), "ok")

    def test_a_rejected_push_is_called_once_and_keeps_its_status(self):
        run = self.run_snippet(
            'git push origin x || echo "status=$?"', FAKE_FAILS="5", FAKE_STATUS="1", FAKE_STDERR=" ! [rejected] x -> x"
        )
        self.assertEqual(self.calls("git"), 1)
        self.assertIn("status=1", run.stdout)
        self.assertNotIn("retrying", run.stderr)

    def test_an_http_404_is_called_once(self):
        run = self.run_snippet(
            'gh api x || echo "status=$?"', FAKE_FAILS="5", FAKE_STATUS="1", FAKE_STDERR="gh: Not Found (HTTP 404)"
        )
        self.assertEqual(self.calls("gh"), 1)
        self.assertIn("status=1", run.stdout)

    def test_retries_limit_the_calls(self):
        run = self.run_snippet('gh api x || echo "status=$?"', FAKE_FAILS="9", AGENT_RETRIES="1")
        self.assertEqual(self.calls("gh"), 2)
        self.assertIn("status=1", run.stdout)

    def test_an_attempt_over_the_call_timeout_is_transient(self):
        run = self.run_snippet("gh api x", FAKE_FAILS="1", FAKE_SLEEP="30", FAKE_STDERR="", AGENT_CALL_TIMEOUT="1")
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(self.calls("gh"), 2)
        self.assertIn("agent: >> retrying gh api (1 of 3): timed out after 1s", run.stderr)

    def test_git_without_a_remote_is_not_wrapped(self):
        run = self.run_snippet('git -C . status || echo "status=$?"', FAKE_FAILS="5")
        self.assertEqual(self.calls("git"), 1)
        self.assertIn("status=1", run.stdout)

    def test_git_options_before_the_subcommand_are_skipped(self):
        run = self.run_snippet("git -c http.x=y -C . fetch origin", FAKE_FAILS="1")
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(self.calls("git"), 2)

    def test_a_failing_command_is_announced_once(self):
        run = self.run_snippet("echo before\nfalse\necho after")
        self.assertEqual(run.returncode, 86)
        self.assertNotIn("after", run.stdout)
        failed = [line for line in run.stderr.splitlines() if "failed:" in line]
        self.assertEqual(len(failed), 1, run.stderr)
        self.assertRegex(failed[0], r"^agent: >> failed: snippet\.sh: false exited 1 \(snippet\.sh:\d+\)$")

    def test_die_is_announced_with_the_step(self):
        run = self.run_snippet('step_begin "review R1"\ndie "boom"')
        self.assertEqual(run.returncode, 86)
        self.assertIn("agent: >> failed: review R1: boom", run.stderr)
        self.assertEqual(run.stderr.count("failed:"), 1)

    def test_a_child_that_exited_86_is_not_announced_again(self):
        (self.dir / "child.sh").write_text("exit 86\n")
        run = self.run_snippet(f'bash "{self.dir / "child.sh"}"')
        self.assertEqual(run.returncode, 86)
        self.assertNotIn("failed:", run.stderr)

    def test_a_stop_status_is_kept_without_a_failed_line(self):
        run = self.run_snippet("exit 87")
        self.assertEqual(run.returncode, 87)
        self.assertNotIn("failed:", run.stderr)

    def test_a_helper_writes_no_failed_line(self):
        run = self.run_snippet("false", AGENT_NO_ANNOUNCE="1")
        self.assertEqual(run.returncode, 1)
        self.assertNotRegex(run.stderr, re.compile(r"failed:"))


if __name__ == "__main__":
    unittest.main()
