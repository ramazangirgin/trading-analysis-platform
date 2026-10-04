"""Reads Claude Code's JSON results and the agent-step markers (scripts/agent/lib.sh).

    agent_json.py get <result.json> <key>           a top-level value (structured_output as JSON)
    agent_json.py usage-line <result.json>...       tokens and cost, for the log
    agent_json.py usage-marker <result.json>...     "tokens=... tokens_in=... ..." for a step marker
    agent_json.py steps < comment bodies            "<step> <round> <tokens>" per marker found
    agent_json.py usage-table < comment bodies      Markdown table of the tokens per step

Standard library only: run with `uv run --no-project python`.
"""

import json
import re
import sys

MARKER = re.compile(r"<!-- agent-step ([^>]*?) -->")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def usage(paths):
    """Sums the usage of the given results: input (uncached + cached), output, cost."""
    total = {"in": 0, "cached": 0, "out": 0, "cost": 0.0}
    for path in paths:
        result = load(path)
        u = result.get("usage") or {}
        total["in"] += u.get("input_tokens", 0) + u.get("cache_creation_input_tokens", 0)
        total["cached"] += u.get("cache_read_input_tokens", 0)
        total["out"] += u.get("output_tokens", 0)
        total["cost"] += result.get("total_cost_usd") or 0.0
    total["tokens"] = total["in"] + total["cached"] + total["out"]
    return total


def parse_marker(text):
    return dict(pair.split("=", 1) for pair in text.split() if "=" in pair)


def markers(stream):
    for match in MARKER.finditer(stream.read()):
        yield parse_marker(match.group(1))


def main(argv):
    command, args = argv[1], argv[2:]
    if command == "get":
        value = load(args[0]).get(args[1])
        print(json.dumps(value) if isinstance(value, (dict, list)) else value)
    elif command == "usage-line":
        u = usage(args)
        print(f"{u['in']} in, {u['cached']} cached, {u['out']} out, ${u['cost']:.2f} (Claude prices)")
    elif command == "usage-marker":
        u = usage(args)
        print(
            f"tokens={u['tokens']} tokens_in={u['in']} tokens_cached={u['cached']} "
            f"tokens_out={u['out']} cost_usd={u['cost']:.4f}"
        )
    elif command == "steps":
        for m in markers(sys.stdin):
            print(m.get("step", "?"), m.get("round", "0"), m.get("tokens", "0"))
    elif command == "usage-table":
        rows = list(markers(sys.stdin))
        print("| Step | Round | Input | Cached input | Output |")
        print("|---|---|---|---|---|")
        totals = [0, 0, 0]
        for m in rows:
            values = [int(m.get(k, 0)) for k in ("tokens_in", "tokens_cached", "tokens_out")]
            totals = [a + b for a, b in zip(totals, values)]
            print(f"| {m.get('step')} | {m.get('round')} | " + " | ".join(f"{v:,}" for v in values) + " |")
        print("| **Total** | | " + " | ".join(f"**{v:,}**" for v in totals) + " |")
    else:
        sys.exit(f"unknown command: {command}")


if __name__ == "__main__":
    main(sys.argv)
