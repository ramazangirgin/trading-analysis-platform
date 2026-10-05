"""Reads Claude Code's JSON results and the agent-step markers (scripts/agent/lib.sh).

    agent_json.py stream <result.json> <log.jsonl>  Claude Code's stream-json on stdin: progress to
                                                    stderr, the stream to <log.jsonl>, the final
                                                    result to <result.json>
    agent_json.py get <result.json> <key>           a top-level value (structured_output as JSON)
    agent_json.py usage-line <result.json>...       tokens and cost, for the log
    agent_json.py usage-marker <result.json>...     "tokens=... tokens_in=... ..." for a step marker
    agent_json.py steps < comment bodies            "<step> <round> <tokens>" per marker found
    agent_json.py usage-table < comment bodies      Markdown table of the tokens per step
    agent_json.py approved-head < comment bodies    the commit the latest review approved, if any

Standard library only: run with `uv run --no-project python`.
"""

import json
import re
import sys
import threading
import time

MARKER = re.compile(r"<!-- agent-step ([^>]*?) -->")

# Silence (seconds) after which the progress says the agent is still at its last action.
HEARTBEAT = 120

# The input field that best describes a tool call, per tool.
TOOL_DETAIL = {
    "Bash": "command",
    "Read": "file_path",
    "Edit": "file_path",
    "Write": "file_path",
    "Glob": "pattern",
    "Grep": "pattern",
    "Task": "description",
    "TaskCreate": "subject",
    "ToolSearch": "query",
}


def one_line(text, width=140):
    text = " ".join(str(text).split())
    return text if len(text) <= width else text[: width - 1] + "…"


def describe(block):
    """A short line for one content block of an assistant message, or None."""
    if block.get("type") == "text" and block.get("text", "").strip():
        return "says: " + one_line(block["text"])
    if block.get("type") != "tool_use":
        return None
    name, args = block.get("name", "?"), block.get("input") or {}
    if name == "TodoWrite":
        todos = args.get("todos", [])
        doing = [t.get("activeForm") or t.get("content") for t in todos if t.get("status") == "in_progress"]
        done = sum(1 for t in todos if t.get("status") == "completed")
        return f"todo {done}/{len(todos)} done" + (": " + one_line("; ".join(doing)) if doing else "")
    if name == "TaskUpdate":
        return f"task {args.get('taskId', '?')} {args.get('status') or 'updated'}"
    if name == "StructuredOutput":
        return "writing its result"
    detail = args.get(TOOL_DETAIL.get(name, ""), "")
    return f"{name} {one_line(detail)}" if detail else name


def stream(out_path, log_path):
    """Follows a stream-json run: one stderr line per step, so a long run shows it is alive."""
    start = time.monotonic()
    state = {"last": time.monotonic(), "what": "starting", "done": False}
    lock = threading.Lock()

    def say(text):
        elapsed = int(time.monotonic() - start)
        print(f"agent:   [{elapsed // 60:02d}:{elapsed % 60:02d}] {text}", file=sys.stderr, flush=True)

    def heartbeat():
        while True:
            time.sleep(15)
            with lock:
                if state["done"]:
                    return
                idle = time.monotonic() - state["last"]
                if idle >= HEARTBEAT:
                    say(f"still working ({int(idle) // 60} min since: {state['what']})")
                    state["last"] = time.monotonic()

    threading.Thread(target=heartbeat, daemon=True).start()
    result = None
    with open(log_path, "a", encoding="utf-8") as log:
        for line in sys.stdin:
            log.write(line)
            log.flush()
            try:
                event = json.loads(line)
            except ValueError:
                continue
            kind = event.get("type")
            lines = []
            if kind == "system" and event.get("subtype") == "init":
                lines.append(f"session {event.get('session_id')}")
            elif kind == "assistant":
                prefix = "  (subagent) " if event.get("parent_tool_use_id") else ""
                lines += [prefix + d for d in map(describe, event.get("message", {}).get("content", [])) if d]
            elif kind == "user":
                for block in event.get("message", {}).get("content", []) or []:
                    if isinstance(block, dict) and block.get("type") == "tool_result" and block.get("is_error"):
                        content = block.get("content")
                        if isinstance(content, list):
                            content = " ".join(c.get("text", "") for c in content if isinstance(c, dict))
                        lines.append("  tool error: " + one_line(content or "", 120))
            elif kind == "result":
                result = event
            with lock:
                for text in lines:
                    say(text)
                    if not text.startswith("  tool error"):
                        state["what"] = text
                if lines:
                    state["last"] = time.monotonic()
    with lock:
        state["done"] = True
    if result is not None:
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(result, f)


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
    if command == "stream":
        stream(args[0], args[1])
    elif command == "get":
        value = load(args[0]).get(args[1])
        print(json.dumps(value) if isinstance(value, (dict, list)) else value)
    elif command == "usage-line":
        u = usage(args)
        print(f"{u['in']} in, {u['cached']} cached, {u['out']} out, ${u['cost']:.2f} at API prices")
    elif command == "usage-marker":
        u = usage(args)
        print(
            f"tokens={u['tokens']} tokens_in={u['in']} tokens_cached={u['cached']} "
            f"tokens_out={u['out']} cost_usd={u['cost']:.4f}"
        )
    elif command == "steps":
        for m in markers(sys.stdin):
            print(m.get("step", "?"), m.get("round", "0"), m.get("tokens", "0"))
    elif command == "approved-head":
        reviews = [m for m in markers(sys.stdin) if m.get("step") == "review"]
        if reviews and reviews[-1].get("approved") == "1":
            print(reviews[-1].get("head", ""))
    elif command == "usage-table":
        rows = list(markers(sys.stdin))
        print("| Step | Round | Input | Cached input | Output | Cost (API prices) |")
        print("|---|---|---|---|---|---|")
        totals, cost = [0, 0, 0], 0.0
        for m in rows:
            values = [int(m.get(k, 0)) for k in ("tokens_in", "tokens_cached", "tokens_out")]
            totals = [a + b for a, b in zip(totals, values)]
            cost += float(m.get("cost_usd", 0))
            cells = " | ".join(f"{v:,}" for v in values)
            print(f"| {m.get('step')} | {m.get('round')} | {cells} | ${float(m.get('cost_usd', 0)):.2f} |")
        cells = " | ".join(f"**{v:,}**" for v in totals)
        print(f"| **Total** | | {cells} | **${cost:.2f}** |")
    else:
        sys.exit(f"unknown command: {command}")


if __name__ == "__main__":
    main(sys.argv)
