"""Summarises the agent transcripts of an issue: turns, context size, tokens, tool calls and tool
output by tool, files read more than twice. Markdown, ready for an issue comment (--json: the same as JSON).

    transcript_stats.py <issue> [--projects DIR] [--folder NAME] [--state DIR]
                        [--transcript FILE]... [--since EPOCH] [--until EPOCH] [--json]

Claude Code keeps the transcripts in <projects>/<folder>/<session>.jsonl (subagents in
<session>/subagents/*.jsonl). The default folder is the issue's worktree, <main checkout>-<issue>;
--transcript adds files, such as the orchestration session of the main checkout, which every issue
shares: --since and --until (epochs) cut those files to the issue's time. A session is matched to
its step by the session_id in the state directory's results (.git/agent/<issue>-*/*.json).

Standard library only: run with `uv run --no-project python`.
"""

import argparse
import glob
import json
import os
import re
import subprocess
import sys

import agent_json

STEPS = [
    (re.compile(r"^(wp-|implement-retry-)"), "implement"),
    (re.compile(r"^review-"), "review"),
    (re.compile(r"^fix-ci-"), "CI fix"),
    (re.compile(r"^fix-"), "fix"),
    (re.compile(r"^update-main"), "update with main"),
]
SESSION = "session"
TOP_FILES = 20


def step_of(result_name):
    for pattern, step in STEPS:
        if pattern.search(result_name):
            return step
    return SESSION


def session_steps(state_dir, issue):
    """{session id: step} from the results (*.json) in the issue's state directories."""
    steps = {}
    for path in sorted(glob.glob(os.path.join(state_dir, f"{issue}-*", "*.json"))):
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except (OSError, ValueError):
            continue
        if isinstance(data, dict) and data.get("session_id"):
            steps.setdefault(data["session_id"], step_of(os.path.basename(path)))
    return steps


def result_size(content):
    """(characters, images) of a tool_result's content."""
    if isinstance(content, str):
        return len(content), 0
    chars = images = 0
    for block in content or []:
        if not isinstance(block, dict):
            continue
        if block.get("type") == "image":
            images += 1
        else:
            chars += len(block.get("text") or "")
    return chars, images


def read_transcript(path, since=None, until=None):
    """The figures of one transcript file; None when it has no assistant message in the window."""
    messages, order = {}, []
    calls, uses, results = {}, {}, []
    reads = {}
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
            message = event.get("message")
            if not isinstance(message, dict):
                continue
            if since is not None or until is not None:
                at = agent_json.epoch_of(event.get("timestamp"))
                if at is None or (since is not None and at < since) or (until is not None and at >= until):
                    continue
            content = message.get("content")
            if event.get("type") == "assistant":
                model = message.get("model") or ""
                if isinstance(message.get("usage"), dict) and not model.startswith("<"):
                    key = message.get("id") or event.get("uuid") or len(messages)
                    if key not in messages:
                        order.append(key)
                    seen = messages.setdefault(key, {"model": model})
                    for field, value in message["usage"].items():
                        if isinstance(value, int):
                            seen[field] = max(seen.get(field, 0), value)
                for block in content if isinstance(content, list) else []:
                    if not isinstance(block, dict) or block.get("type") != "tool_use" or block.get("id") in uses:
                        continue
                    name = block.get("name") or "?"
                    uses[block["id"]] = name
                    calls[name] = calls.get(name, 0) + 1
                    target = (block.get("input") or {}).get("file_path")
                    if name == "Read" and target:
                        reads[target] = reads.get(target, 0) + 1
            elif event.get("type") == "user":
                for block in content if isinstance(content, list) else []:
                    if isinstance(block, dict) and block.get("type") == "tool_result":
                        results.append((block.get("tool_use_id"), *result_size(block.get("content"))))
    if not messages:
        return None
    output = {}
    for use_id, chars, images in results:
        entry = output.setdefault(uses.get(use_id, "?"), {"chars": 0, "images": 0})
        entry["chars"] += chars
        entry["images"] += images
    contexts, cost = [], 0.0
    uncached = cached = out = 0
    for key in order:
        seen = messages[key]
        fresh, written = seen.get("input_tokens", 0), seen.get("cache_creation_input_tokens", 0)
        read, produced = seen.get("cache_read_input_tokens", 0), seen.get("output_tokens", 0)
        contexts.append(fresh + written + read)
        uncached += fresh + written
        cached += read
        out += produced
        prices = agent_json.price_of(seen["model"])
        if prices is None or cost is None:
            cost = None
        else:
            cost += (fresh * prices[0] + written * prices[1] + read * prices[2] + produced * prices[3]) / 1e6
    return {
        "turns": len(order),
        "context_first": contexts[0],
        "context_last": contexts[-1],
        "context_max": max(contexts),
        "uncached": uncached,
        "cached": cached,
        "output": out,
        "tokens": uncached + cached + out,
        "cost": cost,
        "calls": calls,
        "tool_output": output,
        "reads": reads,
    }


def session_of(path):
    """(session id, is a subagent) of a transcript path."""
    parts = os.path.normpath(path).split(os.sep)
    if len(parts) >= 3 and parts[-2] == "subagents":
        return parts[-3], True
    return os.path.splitext(parts[-1])[0], False


def collect(paths, extra, steps, since, until):
    """The per-transcript figures with their step. `extra` paths are cut to the window."""
    found = []
    for path in paths:
        found.append((path, None, None))
    for path in extra:
        found.append((path, since, until))
    transcripts = []
    for path, start, end in found:
        stats = read_transcript(path, start, end)
        if stats is None:
            continue
        session, subagent = session_of(path)
        stats.update(file=path, session=session, subagent=subagent, step=steps.get(session, SESSION))
        transcripts.append(stats)
    return transcripts


def summarise(transcripts):
    steps = {}
    for t in transcripts:
        s = steps.setdefault(
            t["step"],
            {"step": t["step"], "transcripts": 0, "turns": 0, "context_max": 0, "cached": 0, "uncached": 0, "output": 0, "cost": 0.0},
        )
        s["transcripts"] += 1
        s["turns"] += t["turns"]
        s["context_max"] = max(s["context_max"], t["context_max"])
        for key in ("cached", "uncached", "output"):
            s[key] += t[key]
        s["cost"] = None if s["cost"] is None or t["cost"] is None else s["cost"] + t["cost"]
    tools = {}
    reads = {}
    for t in transcripts:
        for name, count in t["calls"].items():
            tools.setdefault(name, {"tool": name, "calls": 0, "chars": 0, "images": 0})["calls"] += count
        for name, entry in t["tool_output"].items():
            row = tools.setdefault(name, {"tool": name, "calls": 0, "chars": 0, "images": 0})
            row["chars"] += entry["chars"]
            row["images"] += entry["images"]
        for target, count in t["reads"].items():
            reads[target] = reads.get(target, 0) + count
    total = sum(row["chars"] for row in tools.values())
    for row in tools.values():
        row["share"] = row["chars"] / total if total else 0.0
    again = sorted(((target, n) for target, n in reads.items() if n > 2), key=lambda item: (-item[1], item[0]))
    return {
        "steps": list(steps.values()),
        "transcripts": [
            {k: v for k, v in t.items() if k not in ("calls", "tool_output", "reads")}
            | {"calls": sum(t["calls"].values()), "output_chars": sum(e["chars"] for e in t["tool_output"].values())}
            for t in transcripts
        ],
        "tools": sorted(tools.values(), key=lambda row: (-row["chars"], row["tool"])),
        "files_read_more_than_twice": [{"file": target, "reads": n} for target, n in again[:TOP_FILES]],
    }


def money(cost):
    return "n/a" if cost is None else f"${cost:.2f}"


def markdown(issue, report):
    lines = [f"### Agent transcripts of #{issue}", "", "| Step | Transcripts | Turns | Max context | Cached input | Uncached input | Output | Cost |", "|---|---|---|---|---|---|---|---|"]
    for s in report["steps"]:
        lines.append(
            f"| {s['step']} | {s['transcripts']} | {s['turns']:,} | {s['context_max']:,} | {s['cached']:,} | {s['uncached']:,} | {s['output']:,} | {money(s['cost'])} |"
        )
    lines += [
        "",
        "| Transcript | Step | Turns | Context first / last / max | Cached | Uncached | Output | Cost | Tool calls | Tool output (chars) |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    for t in report["transcripts"]:
        name = t["session"] + (" (subagent)" if t["subagent"] else "")
        lines.append(
            f"| {name} | {t['step']} | {t['turns']:,} | {t['context_first']:,} / {t['context_last']:,} / {t['context_max']:,} "
            f"| {t['cached']:,} | {t['uncached']:,} | {t['output']:,} | {money(t['cost'])} | {t['calls']:,} | {t['output_chars']:,} |"
        )
    lines += ["", "| Tool | Calls | Output (chars) | Share of output | Images |", "|---|---|---|---|---|"]
    for row in report["tools"]:
        lines.append(f"| {row['tool']} | {row['calls']:,} | {row['chars']:,} | {row['share']:.1%} | {row['images']:,} |")
    lines += ["", "Files read more than twice:", ""]
    again = report["files_read_more_than_twice"]
    lines += [f"- `{r['file']}`: {r['reads']} reads" for r in again] if again else ["none"]
    return "\n".join(lines)


def default_folder(issue):
    common = subprocess.run(["git", "rev-parse", "--git-common-dir"], capture_output=True, text=True, check=True).stdout.strip()
    main = os.path.dirname(os.path.abspath(common))
    return re.sub(r"[^A-Za-z0-9]", "-", f"{main}-{issue}")


def main(argv):
    parser = argparse.ArgumentParser(prog="transcript_stats.py", description=__doc__.split("\n\n")[0])
    parser.add_argument("issue")
    parser.add_argument("--projects", default=os.path.join(os.path.expanduser("~"), ".claude", "projects"))
    parser.add_argument("--folder")
    parser.add_argument("--state")
    parser.add_argument("--transcript", action="append", default=[])
    parser.add_argument("--since", type=float)
    parser.add_argument("--until", type=float)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)

    state = args.state
    if state is None:
        common = subprocess.run(["git", "rev-parse", "--git-common-dir"], capture_output=True, text=True, check=True).stdout.strip()
        state = os.path.join(os.path.abspath(common), "agent")
    folder = os.path.join(args.projects, args.folder or default_folder(args.issue))
    paths = sorted(glob.glob(os.path.join(folder, "*.jsonl")) + glob.glob(os.path.join(folder, "*", "subagents", "*.jsonl")))
    transcripts = collect(paths, args.transcript, session_steps(state, args.issue), args.since, args.until)
    if not transcripts:
        sys.exit(f"no transcripts for #{args.issue} in {folder}")
    report = summarise(transcripts)
    print(json.dumps(report, indent=2) if args.json else markdown(args.issue, report))


if __name__ == "__main__":
    main(sys.argv[1:])
