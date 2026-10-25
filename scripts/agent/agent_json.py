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
    agent_json.py progress-usage <result.json>      the usage marker with the run's duration_ms and
                                                    num_turns, for progress.tsv (progress.py)

    agent_json.py limits <stream.jsonl>             "limit5h=<n> limit7d=<n>" of a probe's stream (last
                                                    event; empty when it has none)
    agent_json.py transcript <session directory>    the newest session transcript of that directory
    agent_json.py session-usage <transcript> <from> <to>
                                                    the usage marker of a session's messages between
                                                    two epochs, with turns=; nothing when there are none
    agent_json.py orchestration-usage <transcript> <progress.tsv> <to>
                                                    the same for the session's orchestration: from the
                                                    first step after the plan, without the plan and the
                                                    update with main; "from=<epoch>" comes first

A <run> is a result file (x.json) or its stream (x.jsonl). A stream whose result is missing (a run
that was interrupted) adds its tokens only, and marks the total partial (`partial=1`).

    agent_json.py steps < comment bodies            "<step> <round> <tokens>" per marker found
    agent_json.py usage-table < comment bodies      Markdown table of the tokens, model and limits per step
    agent_json.py approved-head < comment bodies    the commit the latest review approved, if any,
                                                    moved along the updates with main after it

Standard library only: run with `uv run --no-project python`.
"""

import glob
import json
import os
import re
import sys
import threading
import time
from datetime import datetime

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


WINDOWS = {"5h": "five_hour", "7d": "seven_day"}


def percent(utilization):
    return round(utilization * 100) if isinstance(utilization, (int, float)) else None


def stream_facts(path):
    """The models and the first and last limit utilization (whole percents) of a stream-json log:
    ([model], {window: [first, last]}). A window without an event is absent. The first event comes
    after the run's first model turn, so that turn is missing from a difference of the two."""
    models, limits = [], {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            try:
                event = json.loads(line)
            except ValueError:
                continue
            if event.get("type") == "system" and event.get("subtype") == "init":
                model = event.get("model")
                if model and model not in models:
                    models.append(model)
            elif event.get("type") == "rate_limit_event":
                windows = (event.get("rate_limit_info") or {}).get("unifiedWindows") or {}
                for key, name in WINDOWS.items():
                    value = percent((windows.get(name) or {}).get("utilization"))
                    if value is not None:
                        limits.setdefault(key, [value, value])[1] = value
    return models, limits


def usage(paths):
    """Sums the usage of the given runs: input (uncached + cached), output, cost.

    A run is a result file (`x.json`) or its stream (`x.jsonl`); either path names the run. The
    result is used when it exists, so a run is never counted twice. Without one, the stream is:
    tokens only, and the total is marked partial.

    `models` lists each model once; `limits` takes a window's first value from its first run with
    one and its last value from its last run with one. The runs are given in time order."""
    total = {"in": 0, "cached": 0, "out": 0, "cost": 0.0, "partial": False, "models": [], "limits": {}}
    runs = []
    for path in paths:
        run = path[:-1] if path.endswith(".jsonl") else path
        if run not in runs:
            runs.append(run)
    for run in runs:
        models, limits = stream_facts(run + "l") if os.path.exists(run + "l") else ([], {})
        if os.path.exists(run):
            result = load(run)
        elif os.path.exists(run + "l"):
            result, complete = stream_result(run + "l")
            total["partial"] = total["partial"] or not complete
        else:
            continue
        for model in models or list((result.get("modelUsage") or {})):
            if model not in total["models"]:
                total["models"].append(model)
        for key, (first, last) in limits.items():
            total["limits"].setdefault(key, [first, last])[1] = last
        u = result.get("usage") or {}
        total["in"] += u.get("input_tokens", 0) + u.get("cache_creation_input_tokens", 0)
        total["cached"] += u.get("cache_read_input_tokens", 0)
        total["out"] += u.get("output_tokens", 0)
        total["cost"] += result.get("total_cost_usd") or 0.0
    total["tokens"] = total["in"] + total["cached"] + total["out"]
    return total


# USD per million tokens: input, cache write, cache read, output. A session transcript has no cost,
# so it is computed from this table; update it when models or prices change. A model that is not in
# it has no cost.
PRICES = {
    "claude-opus-5-5": (5.0, 6.25, 0.50, 25.0),
    "claude-sonnet-5-5": (3.0, 3.75, 0.30, 15.0),
    "claude-haiku-5-5": (1.0, 1.25, 0.10, 5.0),
}


def price_of(model):
    for name, prices in PRICES.items():
        if model.startswith(name):
            return prices
    return None


def epoch_of(timestamp):
    try:
        return datetime.fromisoformat(timestamp).timestamp()
    except (TypeError, ValueError):
        return None


def latest_transcript(directory, projects=None):
    """The newest session transcript of a session directory, or None. Claude Code names the
    transcript folder after the directory with every character but letters and digits as `-`."""
    projects = projects or os.path.join(os.path.expanduser("~"), ".claude", "projects")
    folder = os.path.join(projects, re.sub(r"[^A-Za-z0-9]", "-", directory))
    found = glob.glob(os.path.join(folder, "*.jsonl"))
    return max(found, key=os.path.getmtime) if found else None


def session_usage(path, start, end, skip=()):
    """The usage of a session transcript's assistant messages between two epochs, each message ID
    once, as usage() returns it plus `turns`; None when the transcript is missing or has no message
    there. `skip` lists (from, to) windows left out. The cost comes from PRICES and is None when a
    model is not in it. Subagents run in transcripts of their own and are not counted."""
    messages = {}
    try:
        f = open(path, encoding="utf-8")
    except OSError:
        return None
    with f:
        for line in f:
            try:
                event = json.loads(line)
            except ValueError:
                continue
            message = event.get("message") if event.get("type") == "assistant" else None
            if not isinstance(message, dict) or not isinstance(message.get("usage"), dict):
                continue
            at = epoch_of(event.get("timestamp"))
            model = message.get("model") or ""
            if at is None or not start <= at < end or model.startswith("<") or any(a <= at < b for a, b in skip):
                continue
            seen = messages.setdefault(message.get("id") or event.get("uuid") or len(messages), {"model": model})
            for field, value in message["usage"].items():
                if isinstance(value, int):
                    seen[field] = max(seen.get(field, 0), value)
    if not messages:
        return None
    total = {"in": 0, "cached": 0, "out": 0, "cost": 0.0, "partial": False, "models": [], "limits": {}, "turns": len(messages)}
    for seen in messages.values():
        uncached, written = seen.get("input_tokens", 0), seen.get("cache_creation_input_tokens", 0)
        cached, out = seen.get("cache_read_input_tokens", 0), seen.get("output_tokens", 0)
        total["in"] += uncached + written
        total["cached"] += cached
        total["out"] += out
        if seen["model"] and seen["model"] not in total["models"]:
            total["models"].append(seen["model"])
        prices = price_of(seen["model"])
        if prices is None or total["cost"] is None:
            total["cost"] = None
        else:
            total["cost"] += (uncached * prices[0] + written * prices[1] + cached * prices[2] + out * prices[3]) / 1e6
    total["tokens"] = total["in"] + total["cached"] + total["out"]
    return total


def orchestration_window(lines):
    """The window of the session's orchestration in progress.tsv lines: (start, windows to leave out).
    It starts with the first event outside the plan and ends where the caller says; the plan and the
    update with main are the session's steps that have rows of their own."""
    events = []
    for line in lines:
        fields = line.rstrip("\n").split("\t") + [""] * 5
        if fields[0].isdigit():
            events.append((int(fields[0]), fields[1], fields[2], fields[3]))
    skip = []
    for name in ("plan", "update with main"):
        begins = sorted(at for at, kind, parent, step in events if kind == "begin" and not parent and step == name)
        ends = sorted(at for at, kind, parent, step in events if kind == "end" and step == name)
        skip += list(zip(begins, ends))
    started = [at for at, _, parent, step in events if "plan" not in (parent, step)]
    return (min(started) if started else None), skip


def usage_marker(u):
    marker = f"tokens={u['tokens']} tokens_in={u['in']} tokens_cached={u['cached']} tokens_out={u['out']}"
    if u["cost"] is not None:
        marker += f" cost_usd={u['cost']:.4f}"
    if u.get("models"):
        marker += " model=" + ",".join(u["models"])
    for key in WINDOWS:
        if key in u.get("limits", {}):
            before, after = u["limits"][key]
            marker += f" limit{key}_before={before} limit{key}_after={after}"
    return marker + " partial=1" if u["partial"] else marker


def limit_range(before, after):
    return f"{before}% → {after}% ({after - before:+d})"


def limit_cells(rows):
    """The two limit cells of the total row: the first known before to the last known after, per
    window. "at least" when a row has no value, as the limit is shared and its use is unknown."""
    cells = []
    for key in WINDOWS:
        known = [(m[f"limit{key}_before"], m[f"limit{key}_after"]) for m in rows if f"limit{key}_before" in m and f"limit{key}_after" in m]
        if not known:
            cells.append("n/a")
            continue
        text = limit_range(int(known[0][0]), int(known[-1][1]))
        cells.append(f"**{'at least ' if len(known) < len(rows) else ''}{text}**")
    return cells


def usage_table(rows):
    """The Markdown table of the tokens and limits per step. A cost is "at least" when a run was
    cut off, a limit consumption when a row has no limit values."""
    lines = [
        "| Step | Round | Model | Input | Cached input | Output | Cost (API prices) | 5h before → after (Δ) | Weekly before → after (Δ) |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    totals, cost, partial, unpriced = [0, 0, 0], 0.0, False, False
    for m in rows:
        values = [int(m.get(k, 0)) for k in ("tokens_in", "tokens_cached", "tokens_out")]
        totals = [a + b for a, b in zip(totals, values)]
        cost += float(m.get("cost_usd", 0))
        cut = m.get("partial") == "1"
        partial = partial or cut
        unpriced = unpriced or "cost_usd" not in m
        cells = " | ".join(f"{v:,}" for v in values)
        model = m.get("model", "n/a").replace(",", ", ")
        limits = " | ".join(
            limit_range(int(m[f"limit{key}_before"]), int(m[f"limit{key}_after"]))
            if f"limit{key}_before" in m and f"limit{key}_after" in m
            else "n/a"
            for key in WINDOWS
        )
        row_cost = f"{'at least ' if cut else ''}${float(m['cost_usd']):.2f}" if "cost_usd" in m else "n/a"
        lines.append(f"| {m.get('step')} | {m.get('round')} | {model} | {cells} | {row_cost} | {limits} |")
    cells = " | ".join(f"**{v:,}**" for v in totals)
    limits = " | ".join(limit_cells(rows))
    if partial:
        total_cost = f"**at least ${cost:.2f}** (an interrupted run's cost is unknown)"
    elif unpriced:
        total_cost = f"**at least ${cost:.2f}**"
    else:
        total_cost = f"**${cost:.2f}**"
    lines.append(f"| **Total** | | | {cells} | {total_cost} | {limits} |")
    return "\n".join(lines)


def parse_marker(text):
    return dict(pair.split("=", 1) for pair in text.split() if "=" in pair)


def markers(stream):
    for match in MARKER.finditer(stream.read()):
        yield parse_marker(match.group(1))


def approved_head(ms):
    """The commit the latest review approved, moved along the updates with main that followed it."""
    ms = list(ms)
    last = max((i for i, m in enumerate(ms) if m.get("step") == "review"), default=None)
    if last is None or ms[last].get("approved") != "1":
        return ""
    head = ms[last].get("head", "")
    for m in ms[last + 1 :]:
        if m.get("step") == "update-main" and head and m.get("from") == head:
            head = m.get("head", "")
    return head


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
    elif command == "progress-usage":
        result = load(args[0])
        print(f"{usage_marker(usage(args[:1]))} duration_ms={result.get('duration_ms') or 0} turns={result.get('num_turns') or 0}")
    elif command == "limits":
        _, limits = stream_facts(args[0]) if os.path.exists(args[0]) else ([], {})
        print(" ".join(f"limit{key}={last}" for key, (_, last) in limits.items()))
    elif command == "transcript":
        print(latest_transcript(args[0]) or "")
    elif command == "session-usage":
        u = session_usage(args[0], float(args[1]), float(args[2]))
        if u:
            print(f"{usage_marker(u)} turns={u['turns']}")
    elif command == "orchestration-usage":
        with open(args[1], encoding="utf-8") as f:
            start, skip = orchestration_window(f)
        u = session_usage(args[0], start, float(args[2]), skip) if start else None
        if u:
            print(f"from={start} {usage_marker(u)} turns={u['turns']}")
    elif command == "steps":
        for m in markers(sys.stdin):
            print(m.get("step", "?"), m.get("round", "0"), m.get("tokens", "0"))
    elif command == "approved-head":
        head = approved_head(markers(sys.stdin))
        if head:
            print(head)
    elif command == "usage-table":
        print(usage_table(list(markers(sys.stdin))))
    else:
        sys.exit(f"unknown command: {command}")


if __name__ == "__main__":
    main(sys.argv)
