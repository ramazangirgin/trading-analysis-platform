"""SIGTERM against a real process: the run must end with run_finished{stopped} and exit 2."""

import json
import signal
import subprocess
import sys
import textwrap

SCRIPT = textwrap.dedent(
    """
    import sys, time
    from ta_runner.__main__ import _install_stop_handler
    from ta_runner.events import EventWriter
    from ta_runner.runner import execute
    from ta_runner.spec import RunSpec

    class SlowUpstream:
        resumed = False
        def run(self, on_chunk):
            print("ready", file=sys.stderr, flush=True)
            while True:
                time.sleep(0.05)

    spec = RunSpec.from_dict({
        "run_id": "r_stop", "ticker": "NVDA", "trade_date": "2026-09-25",
        "llm_provider": "openai", "deep_think_llm": "m", "quick_think_llm": "m",
    })
    _install_stop_handler()
    sys.exit(execute(spec, EventWriter("r_stop", sys.stdout), lambda s, c: SlowUpstream(), "x"))
    """
)


def test_sigterm_stops_the_run_cleanly():
    process = subprocess.Popen(
        [sys.executable, "-c", SCRIPT],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    assert process.stderr.readline().strip() == "ready"

    process.send_signal(signal.SIGTERM)
    stdout, _ = process.communicate(timeout=10)

    assert process.returncode == 2
    events = [json.loads(line) for line in stdout.splitlines()]
    assert events[-1]["type"] == "run_finished"
    assert events[-1]["status"] == "stopped"
