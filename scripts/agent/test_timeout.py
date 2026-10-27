"""Tests of timeout.py: status pass-through, the limit and the process group.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import os
import signal
import subprocess
import sys
import time
import unittest
from pathlib import Path

SCRIPT = str(Path(__file__).with_name("timeout.py"))
BACKGROUND_SLEEP = "sleep 60 & echo $!; wait"


def start(seconds, *command):
    return subprocess.Popen([sys.executable, SCRIPT, str(seconds), *command], stdout=subprocess.PIPE, text=True)


def is_gone(pid):
    for _ in range(50):
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return True
        time.sleep(0.1)
    return False


class TimeoutTest(unittest.TestCase):
    def start(self, seconds, *command):
        process = start(seconds, *command)
        self.addCleanup(process.stdout.close)
        return process

    def test_passes_the_exit_status_through(self):
        for status in (0, 3, 86):
            run = subprocess.run([sys.executable, SCRIPT, "10", "sh", "-c", f"exit {status}"])
            self.assertEqual(status, run.returncode)

    def test_command_over_the_limit_ends_with_124_and_its_children_are_gone(self):
        process = self.start(1, "sh", "-c", BACKGROUND_SLEEP)
        child = int(process.stdout.readline())
        self.assertEqual(124, process.wait(timeout=30))
        self.assertTrue(is_gone(child))

    def test_term_ends_the_command_and_exits_143(self):
        process = self.start(60, "sh", "-c", BACKGROUND_SLEEP)
        child = int(process.stdout.readline())
        process.send_signal(signal.SIGTERM)
        self.assertEqual(143, process.wait(timeout=30))
        self.assertTrue(is_gone(child))

    def test_int_exits_130(self):
        process = self.start(60, "sleep", "60")
        time.sleep(0.5)
        process.send_signal(signal.SIGINT)
        self.assertEqual(130, process.wait(timeout=30))

    def test_missing_command_exits_127(self):
        run = subprocess.run([sys.executable, SCRIPT, "10", "no-such-command-here"], stderr=subprocess.DEVNULL)
        self.assertEqual(127, run.returncode)

    def test_usage_error_exits_2(self):
        run = subprocess.run([sys.executable, SCRIPT, "10"], stderr=subprocess.DEVNULL)
        self.assertEqual(2, run.returncode)


if __name__ == "__main__":
    unittest.main()
