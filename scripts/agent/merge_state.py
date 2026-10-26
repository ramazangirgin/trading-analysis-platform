"""Decides from a pull request's merge state whether the wait for CI goes on.

    merge_state.py wait-step    the merge state JSON on stdin; prints update or wait

The JSON is `gh pr view <pr> --json mergeable,mergeStateStatus`. A pull request that conflicts with
its base gets no pull_request workflow run, so waiting for CI could only time out: `update` (update
with the base). Every other state, a branch that is only behind included, prints `wait`. The agent
pull requests are drafts, so a conflict is `mergeable` CONFLICTING or `mergeStateStatus` DIRTY, not
the status alone. Missing or unparsable input counts as no conflict.

Standard library only: run with `uv run --no-project python`.
"""

import json
import sys


def wait_step(state):
    if not isinstance(state, dict):
        return "wait"
    if state.get("mergeable") == "CONFLICTING" or state.get("mergeStateStatus") == "DIRTY":
        return "update"
    return "wait"


def main(argv, stdin=None):
    if len(argv) < 2:
        sys.exit(__doc__)
    if argv[1] != "wait-step":
        sys.exit(f"unknown command: {argv[1]}")
    try:
        state = json.loads((stdin or sys.stdin).read())
    except ValueError:
        state = None
    print(wait_step(state))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
