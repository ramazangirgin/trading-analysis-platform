"""Reads Claude Code's JSON results and the agent-step markers (scripts/agent/lib.sh).

    agent_json.py stream <result.json> <log.jsonl> [label]
                                                    Claude Code's stream-json on stdin: progress to
                                                    stderr, the stream to <log.jsonl>, the final
                                                    result to <result.json>. With a label, also a
                                                    milestone line `agent: >> <label>: <phase>` when
                                                    the agent starts a phase (PHASES: tests,
                                                    mise run check and its result, commit); without
                                                    one, none
    agent_json.py get <result.json> <key>           a top-level value (structured_output as JSON)
    agent_json.py usage-line <run>...               tokens and cost, for the log
    agent_json.py usage-marker <run>...             "tokens=... tokens_in=... ..." for a step marker

A <run> is a result file (x.json) or its stream (x.jsonl). A stream whose result is missing (a run
that was interrupted) adds its tokens only, and marks the total partial (`partial=1`).

    agent_json.py steps < comment bodies            "<step> <round> <tokens>" per marker found
    agent_json.py usage-table < comment bodies      Markdown table of the tokens per step
    agent_json.py approved-head < comment bodies    the commit the latest review approved, if any

Standard library only: run with `uv run --no-project python`.
"""

import json
import os
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


CHECK = "mise run check"

# The phases of an agent run that get a milestone line, in the order they are tried: the phase and
# the pattern of the Bash commands that start it. To recognise more, add a row.
_END = r"(?=\s|$)"
PHASES = [
    (CHECK, re.compile(r"\bmise run check" + _END)),
    ("commit", re.compile(r"\bgit commit" + _END)),
    (
        "tests",
        re.compile(
            r"\bmise run (test|runner-test|agent:test|skill:test|e2e)" + _END
            + r"|\./gradlew\b[^|;&]*\s(\S*:)?(test|\w+Test)" + _END
            + r"|\buv run\b[^|;&]*\bpytest" + _END
            + r"|\bpnpm\b[^|;&]*\stest" + _END
            + r"|\bvitest" + _END
        ),
    ),
]


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


def phase_of(command):
    """The phase a Bash command starts (PHASES), or None."""
    for phase, pattern in PHASES:
        if pattern.search(command):
            return phase
    return None


def stream(out_path, log_path, label=""):
    """Follows a stream-json run: one stderr line per step, so a long run shows it is alive. With a
    label, a milestone line (`agent: >> <label>: <phase>`) for each change of phase (PHASES)."""
    start = time.monotonic()
    state = {"last": time.monotonic(), "what": "starting", "done": False}
    lock = threading.Lock()
    phase = None  # the phase of the latest Bash call that started one
    checks = set()  # tool_use_ids of the `mise run check` calls whose result is still to come

    def say(text):
        elapsed = int(time.monotonic() - start)
        print(f"agent:   [{elapsed // 60:02d}:{elapsed % 60:02d}] {text}", file=sys.stderr, flush=True)

    def milestone(text):
        if label:
            print(f"agent: >> {label}: {text}", file=sys.stderr, flush=True)

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
            milestones = []
            if kind == "system" and event.get("subtype") == "init":
                lines.append(f"session {event.get('session_id')}")
            elif kind == "assistant":
                prefix = "  (subagent) " if event.get("parent_tool_use_id") else ""
                content = event.get("message", {}).get("content", [])
                lines += [prefix + d for d in map(describe, content) if d]
                for block in content:
                    if block.get("type") != "tool_use" or block.get("name") != "Bash":
                        continue
                    started = phase_of((block.get("input") or {}).get("command", ""))
                    if started is None:
                        continue
                    if started != phase:
                        phase = started
                        milestones.append(started)
                    if started == CHECK:
                        checks.add(block.get("id"))
            elif kind == "user":
                for block in event.get("message", {}).get("content", []) or []:
                    if not isinstance(block, dict) or block.get("type") != "tool_result":
                        continue
                    if block.get("tool_use_id") in checks:
                        checks.discard(block.get("tool_use_id"))
                        milestones.append(f"{CHECK} {'failed' if block.get('is_error') else 'passed'}")
                        phase = None  # a next check is a new line
                    if block.get("is_error"):
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
                for text in milestones:
                    milestone(text)
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


def stream_result(path):
    """The result of a stream-json log as (result, complete). A run cut off before its `result`
    event has none: its messages' usage is summed instead (no cost), each message ID once, as a
    message can arrive as several events that repeat its usage; subagents' messages included."""
    messages, result = {}, None
    with open(path, encoding="utf-8") as f:
        for line in f:
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if event.get("type") == "result":
                result = event
            elif event.get("type") == "assistant":
                message = event.get("message") or {}
                if not isinstance(message.get("usage"), dict):
                    continue
                seen = messages.setdefault(message.get("id") or event.get("uuid") or len(messages), {})
                for field, value in message["usage"].items():
                    if isinstance(value, int):
                        seen[field] = max(seen.get(field, 0), value)
    if result is not None:
        return result, True
    summed = {}
    for seen in messages.values():
        for field, value in seen.items():
            summed[field] = summed.get(field, 0) + value
    return {"usage": summed}, False


def usage(paths):
    """Sums the usage of the given runs: input (uncached + cached), output, cost.

    A run is a result file (`x.json`) or its stream (`x.jsonl`); either path names the run. The
    result is used when it exists, so a run is never counted twice. Without one, the stream is:
    tokens only, and the total is marked partial."""
    total = {"in": 0, "cached": 0, "out": 0, "cost": 0.0, "partial": False}
    runs = []
    for path in paths:
        run = path[:-1] if path.endswith(".jsonl") else path
        if run not in runs:
            runs.append(run)
    for run in runs:
        if os.path.exists(run):
            result = load(run)
        elif os.path.exists(run + "l"):
            result, complete = stream_result(run + "l")
            total["partial"] = total["partial"] or not complete
        else:
            continue
        u = result.get("usage") or {}
        total["in"] += u.get("input_tokens", 0) + u.get("cache_creation_input_tokens", 0)
        total["cached"] += u.get("cache_read_input_tokens", 0)
        total["out"] += u.get("output_tokens", 0)
        total["cost"] += result.get("total_cost_usd") or 0.0
    total["tokens"] = total["in"] + total["cached"] + total["out"]
    return total


def usage_marker(u):
    marker = (
        f"tokens={u['tokens']} tokens_in={u['in']} tokens_cached={u['cached']} "
        f"tokens_out={u['out']} cost_usd={u['cost']:.4f}"
    )
    return marker + " partial=1" if u["partial"] else marker


def usage_table(rows):
    """The Markdown table of the tokens per step; a cost is "at least" when a run was cut off."""
    lines = ["| Step | Round | Input | Cached input | Output | Cost (API prices) |", "|---|---|---|---|---|---|"]
    totals, cost, partial = [0, 0, 0], 0.0, False
    for m in rows:
        values = [int(m.get(k, 0)) for k in ("tokens_in", "tokens_cached", "tokens_out")]
        totals = [a + b for a, b in zip(totals, values)]
        cost += float(m.get("cost_usd", 0))
        cut = m.get("partial") == "1"
        partial = partial or cut
        cells = " | ".join(f"{v:,}" for v in values)
        lines.append(
            f"| {m.get('step')} | {m.get('round')} | {cells} | {'at least ' if cut else ''}${float(m.get('cost_usd', 0)):.2f} |"
        )
    cells = " | ".join(f"**{v:,}**" for v in totals)
    if partial:
        lines.append(f"| **Total** | | {cells} | **at least ${cost:.2f}** (an interrupted run's cost is unknown) |")
    else:
        lines.append(f"| **Total** | | {cells} | **${cost:.2f}** |")
    return "\n".join(lines)


def parse_marker(text):
    return dict(pair.split("=", 1) for pair in text.split() if "=" in pair)


def markers(stream):
    for match in MARKER.finditer(stream.read()):
        yield parse_marker(match.group(1))


def main(argv):
    command, args = argv[1], argv[2:]
    if command == "stream":
        stream(args[0], args[1], args[2] if len(args) > 2 else "")
    elif command == "get":
        value = load(args[0]).get(args[1])
        print(json.dumps(value) if isinstance(value, (dict, list)) else value)
    elif command == "usage-line":
        u = usage(args)
        cost = f"at least ${u['cost']:.2f}" if u["partial"] else f"${u['cost']:.2f}"
        print(f"{u['in']} in, {u['cached']} cached, {u['out']} out, {cost} at API prices")
    elif command == "usage-marker":
        print(usage_marker(usage(args)))
    elif command == "steps":
        for m in markers(sys.stdin):
            print(m.get("step", "?"), m.get("round", "0"), m.get("tokens", "0"))
    elif command == "approved-head":
        reviews = [m for m in markers(sys.stdin) if m.get("step") == "review"]
        if reviews and reviews[-1].get("approved") == "1":
            print(reviews[-1].get("head", ""))
    elif command == "usage-table":
        print(usage_table(list(markers(sys.stdin))))
    else:
        sys.exit(f"unknown command: {command}")


if __name__ == "__main__":
    main(sys.argv)
