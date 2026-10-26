"""Tests of merge_state.py: which merge states end the wait for CI.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import contextlib
import io
import json
import unittest

import merge_state


def state(mergeable, status):
    return {"mergeable": mergeable, "mergeStateStatus": status}


class WaitStepTest(unittest.TestCase):
    def test_dirty_updates(self):
        self.assertEqual("update", merge_state.wait_step(state("CONFLICTING", "DIRTY")))
        self.assertEqual("update", merge_state.wait_step({"mergeStateStatus": "DIRTY"}))

    def test_conflicting_draft_or_unknown_updates(self):
        for status in ("UNKNOWN", "DRAFT"):
            self.assertEqual("update", merge_state.wait_step(state("CONFLICTING", status)), status)

    def test_other_states_wait(self):
        for status in ("BEHIND", "BLOCKED", "CLEAN", "UNSTABLE", "UNKNOWN", "HAS_HOOKS", "DRAFT"):
            self.assertEqual("wait", merge_state.wait_step(state("MERGEABLE", status)), status)
        self.assertEqual("wait", merge_state.wait_step(state("UNKNOWN", "UNKNOWN")))

    def test_missing_fields_wait(self):
        self.assertEqual("wait", merge_state.wait_step({}))
        self.assertEqual("wait", merge_state.wait_step(None))
        self.assertEqual("wait", merge_state.wait_step([]))


class MainTest(unittest.TestCase):
    def run_main(self, text):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = merge_state.main(["merge_state.py", "wait-step"], io.StringIO(text))
        self.assertEqual(0, code)
        return out.getvalue().strip()

    def test_prints_update_on_conflict(self):
        self.assertEqual("update", self.run_main(json.dumps(state("CONFLICTING", "DIRTY"))))

    def test_prints_wait(self):
        self.assertEqual("wait", self.run_main(json.dumps(state("MERGEABLE", "BEHIND"))))

    def test_empty_or_invalid_input_waits(self):
        self.assertEqual("wait", self.run_main(""))
        self.assertEqual("wait", self.run_main("not json"))

    def test_unknown_command_exits(self):
        with self.assertRaises(SystemExit):
            merge_state.main(["merge_state.py", "other"], io.StringIO("{}"))


if __name__ == "__main__":
    unittest.main()
