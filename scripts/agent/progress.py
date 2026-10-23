"""Renders a plan branch's progress (scripts/agent/lib.sh, progress.tsv) as the issue comment.

    progress.py render <progress.tsv> <marker> <branch> [--pr <n>] [--max-rounds <n>]
                       [--max-ci-fixes <n>] [--now <epoch>] [--only usage]

progress.tsv holds one event per line, tab separated: <epoch> <kind> <parent> <name> <text>.

    begin       a step <name> started; a sub-step (a work package, the final checks) names its
                step as <parent>
    end         step <name> ended; <text> is its result: empty (it ended well: its last milestone
                says what it did), "failed" (its "failed: ..." milestone says why), "interrupted",
                or a result of its own
    milestone   <text> happened in step <parent> (sub-step <name>), or outside every step
    usage       an agent run of step <parent> (sub-step <name>) ended; <text> is its usage,
                "tokens=... cost_usd=... model=... limit5h_before=... duration_ms=... turns=..."
                (agent_json.py progress-usage); the plan, the update with main and the orchestration
                are the session's own, read from its transcript
    note        <text>, for the timeline only

The comment says where the run stands, the total time, tokens and cost; every step with its start,
end, duration, tokens and result; the usage per step (model, tokens, cost, the Claude limits before
and after) with the total of everything the issue consumed; the runs, time, tokens and outcomes per kind of step (the
iterations); what is worth a look (the slowest steps, steps run again, retries, failures, the time
spent waiting for CI); and the milestones as a timeline. The agents' tool calls are not in it:
progress.tsv has the milestones only.

Standard library only: run with `uv run --no-project python`.
"""

import argparse
import re
import sys
import time

import agent_json

# The kinds of step, by the step's name; the first match wins.
KINDS = [
    ("plan", re.compile(r"^plan\b")),
    ("implement", re.compile(r"^implement\b")),
    ("work package", re.compile(r"^(WP\d|all\b)")),
    ("final checks", re.compile(r"^final checks\b")),
    ("CI fix", re.compile(r"^CI fix\b")),
    ("CI", re.compile(r"^CI\b")),
    ("review", re.compile(r"^review\b")),
    ("fix", re.compile(r"^fix\b")),
    ("finalise", re.compile(r"^finalise\b")),
    ("update with main", re.compile(r"^update\b")),
]

# Milestones that say an agent had to try again.
RETRY = re.compile(r"resuming the agent|checks failed \(attempt|nothing pushed")

# The clock of the times shown: the machine's local time (the tests use UTC).
LOCALTIME = time.localtime

# The timeline keeps the latest milestones only: a comment holds at most 65536 characters.
TIMELINE_LIMIT = 150
SLOWEST = 3


class Step:
    def __init__(self, name, start, parent=None):
        self.name = name
        self.start = start
        self.parent = parent
        self.end = None
        self.outcome = None  # None (open), "", "failed", "interrupted" or a result of its own
        self.milestones = []
        self.children = []
        self.tokens = 0
        self.cost = 0.0
        self.agent_runs = 0
        self.agent_ms = 0
        self.usages = []  # (epoch, pairs) of every usage event

    @property
    def open(self):
        return self.end is None

    def spent(self, now):
        return (now if self.open else self.end) - self.start

    def total(self, field):
        """A usage figure of the step with its sub-steps."""
        return getattr(self, field) + sum(getattr(c, field) for c in self.children)

    def result(self):
        """What the step did: its last milestone, or why it failed."""
        own = [text for _, text in self.milestones]
        if self.outcome == "failed":
            # The reason can be in a sub-step: the step fails with it.
            every = sorted(self.milestones + [m for c in self.children for m in c.milestones])
            failed = [text for _, text in every if text.startswith("failed:")]
            return failed[-1] if failed else "failed"
        if self.outcome == "interrupted":
            return "interrupted" + (f" (last: {own[-1]})" if own else "")
        if self.outcome:
            return self.outcome
        if own:
            return own[-1]
        return "running" if self.open else "done"


def kind_of(name):
    for kind, pattern in KINDS:
        if pattern.search(name):
            return kind
    return "other"


def parse(lines):
    """The events of progress.tsv: (epoch, kind, parent, name, text) tuples, bad lines skipped."""
    events = []
    for line in lines:
        fields = line.rstrip("\n").split("\t")
        if len(fields) < 4 or not fields[0].isdigit():
            continue
        fields += [""] * (5 - len(fields))
        events.append((int(fields[0]), fields[1], fields[2], fields[3], fields[4]))
    # In the order they happened: a step a session records afterwards (begin --at) comes first.
    return sorted(events, key=lambda event: event[0])


def pairs(text):
    return dict(pair.split("=", 1) for pair in text.split() if "=" in pair)


def build(events):
    """The top-level steps (with their sub-steps) and the timeline: (epoch, step label, text)."""
    steps, timeline = [], []

    def find_open(name, among):
        for step in reversed(among):
            if step.name == name and step.open:
                return step
        return None

    def every_step():
        for step in steps:
            yield step
            yield from step.children

    def owner_of(parent, name):
        step = find_open(parent, steps) if parent else None
        if step and name:
            step = find_open(name, step.children) or step
        return step

    for at, kind, parent, name, text in events:
        if kind == "begin":
            owner = find_open(parent, steps) if parent else None
            among = owner.children if owner else steps
            # A step begun again while still open: the earlier run ended without a word (killed).
            earlier = find_open(name, among)
            if earlier:
                earlier.end, earlier.outcome = at, "interrupted"
            among.append(Step(name, at, owner))
        elif kind == "end":
            step = find_open(name, list(every_step()))
            if step:
                step.end, step.outcome = at, text
                for child in step.children:
                    if child.open:
                        child.end, child.outcome = at, text or "interrupted"
        elif kind == "milestone":
            step = owner_of(parent, name)
            if step:
                step.milestones.append((at, text))
            timeline.append((at, step.name if step else "", text))
        elif kind == "usage":
            step = owner_of(parent, name)
            if step:
                u = pairs(text)
                step.tokens += int(u.get("tokens", 0))
                step.cost += float(u.get("cost_usd", 0))
                step.agent_ms += int(u.get("duration_ms", 0))
                step.agent_runs += 1
                step.usages.append((at, u))
        elif kind == "note":
            timeline.append((at, "", text))
    return steps, timeline


def duration(seconds):
    seconds = max(0, int(seconds))
    if seconds < 60:
        return f"{seconds}s"
    if seconds < 3600:
        return f"{seconds // 60}m {seconds % 60:02d}s"
    return f"{seconds // 3600}h {seconds % 3600 // 60:02d}m"


def tokens(n):
    if not n:
        return "–"
    if n >= 1_000_000:
        return f"{n / 1_000_000:.1f}M"
    if n >= 1_000:
        return f"{n / 1_000:.0f}k"
    return str(n)


def money(cost):
    return f"${cost:.2f}" if cost else "–"


def clock(epoch, day):
    """The time of day, with the date when it is not the run's first day."""
    moment = LOCALTIME(epoch)
    return time.strftime("%H:%M" if time.strftime("%Y-%m-%d", moment) == day else "%m-%d %H:%M", moment)


def usage_row(step):
    """The usage of a step and its sub-steps as one agent-step marker's pairs (agent_json.py), or None
    without usage. A limit's before comes from the first event that has one, its after from the last."""
    events = sorted((e for s in [step] + step.children for e in s.usages), key=lambda e: e[0])
    if not events:
        return None
    row = {"step": cell(step.name), "round": ""}
    for key in ("tokens_in", "tokens_cached", "tokens_out"):
        row[key] = sum(int(u.get(key, 0)) for _, u in events)
    if all("cost_usd" in u for _, u in events):
        row["cost_usd"] = sum(float(u["cost_usd"]) for _, u in events)
    if any(u.get("partial") == "1" for _, u in events):
        row["partial"] = "1"
    models = []
    for _, u in events:
        models += [m for m in u.get("model", "").split(",") if m and m not in models]
    if models:
        row["model"] = ",".join(models)
    for key in agent_json.WINDOWS:
        before = [u[f"limit{key}_before"] for _, u in events if f"limit{key}_before" in u]
        after = [u[f"limit{key}_after"] for _, u in events if f"limit{key}_after" in u]
        if before and after:
            row[f"limit{key}_before"], row[f"limit{key}_after"] = before[0], after[-1]
    return row


def usage_section(steps):
    rows = [row for row in map(usage_row, steps) if row]
    if not rows:
        return ["**Usage**", "", "No agent usage recorded yet."]
    return [
        "**Usage**",
        "",
        agent_json.usage_table(rows),
        "",
        "The Claude limits are shared with whatever else runs at the same time, so a difference is "
        "approximate; the first turn of a run is not in it. `n/a`: no value could be read.",
    ]


def cell(text):
    return " ".join(str(text).split()).replace("|", "\\|")


def status(steps, timeline, now):
    if not steps:
        return "⏳ not started"
    running = [s for s in steps if s.open]
    if running:
        step = running[-1]
        sub = [c for c in step.children if c.open]
        label = step.name + (f" › {sub[-1].name}" if sub else "")
        return f"🔄 running: **{cell(label)}** (for {duration(now - step.start)})"
    last = steps[-1]
    stops = [text for _, _, text in timeline if text.startswith("stopping:")]
    if stops and timeline[-1][2] == stops[-1]:
        return f"⏸️ stopped: {cell(stops[-1][len('stopping:'):].strip())}"
    if last.outcome == "failed":
        return f"❌ failed at **{cell(last.name)}**: {cell(last.result())}"
    if last.outcome == "interrupted":
        return f"⚠️ interrupted at **{cell(last.name)}**"
    if any(kind_of(s.name) == "finalise" and s.outcome == "" for s in steps):
        return "✅ ready for review"
    return f"⏳ between steps (last: **{cell(last.name)}**: {cell(last.result())})"


def outcomes(kind, runs):
    """The outcomes of the runs of one kind of step, in a few words."""
    results = [s.result() for s in runs]
    if kind == "work package":
        not_done = sum(1 for r in results if "not done" in r)
        finished = sum(1 for s, r in zip(runs, results) if not s.open and not s.outcome and "not done" not in r)
        parts = [f"{finished} done", f"{not_done} not done" if not_done else ""]
    elif kind == "CI":
        passed = sum(1 for r in results if r.startswith("passed"))
        red = sum(1 for r in results if r.startswith("failed"))
        parts = [f"{passed} passed", f"{red} failed" if red else ""]
    elif kind == "review":
        parts = [f"{s.name.split()[-1]}: {re.sub(r'^review R[0-9]+: ', '', r)}" for s, r in zip(runs, results)]
    else:
        failed = sum(1 for s in runs if s.outcome == "failed")
        parts = [f"{failed} failed" if failed else ""]
    interrupted = sum(1 for s in runs if s.outcome == "interrupted")
    parts.append(f"{interrupted} interrupted" if interrupted else "")
    return ", ".join(p for p in parts if p)


def worth_a_look(steps, timeline, now, elapsed, day):
    """What may have slowed the run down or been done twice: lines for the comment."""
    every = [s for step in steps for s in [step] + step.children]
    lines = []
    # The orchestration spans the run, so it would always head the ranking.
    slowest = sorted(
        (s for s in every if not s.children and s.name != "orchestration"), key=lambda s: s.spent(now), reverse=True
    )[:SLOWEST]
    if slowest and elapsed:
        lines.append(
            "Slowest steps: "
            + ", ".join(f"{cell(s.name)} {duration(s.spent(now))} ({100 * s.spent(now) // elapsed}%)" for s in slowest)
        )
    ci = [s for s in steps if kind_of(s.name) == "CI"]
    if ci and elapsed:
        waited = sum(s.spent(now) for s in ci)
        lines.append(f"Waiting for CI: {duration(waited)} over {len(ci)} run(s) ({100 * waited // elapsed}% of the run)")
    names = {}
    for s in every:
        names[s.name] = names.get(s.name, 0) + 1
    again = [f"{cell(n)} ×{c}" for n, c in names.items() if c > 1]
    if again:
        lines.append("Run more than once: " + ", ".join(again))
    retries = [f"{clock(at, day)} {cell(text)}" for at, _, text in timeline if RETRY.search(text)]
    if retries:
        lines.append("Retries: " + "; ".join(retries))
    for s in every:
        if s.outcome in ("failed", "interrupted"):
            lines.append(f"{s.outcome.capitalize()}: {cell(s.name)} at {clock(s.end, day)}: {cell(s.result())}")
    extra = [kind_of(s.name) for s in steps if kind_of(s.name) in ("CI fix", "update with main")]
    if extra:
        lines.append(
            "Steps beyond the plan's path: "
            + ", ".join(f"{k} ×{extra.count(k)}" for k in dict.fromkeys(extra))
        )
    return lines


def render(events, marker, branch, pr=None, max_rounds=None, max_ci_fixes=None, now=None, only=None):
    steps, timeline = build(events)
    if only == "usage":
        return "\n".join(usage_section(steps)) + "\n"
    now = now if now is not None else int(time.time())
    first = events[0][0] if events else now
    last = events[-1][0] if events else now
    running = any(s.open for s in steps)
    elapsed = (now if running else last) - first
    day = time.strftime("%Y-%m-%d", LOCALTIME(first))
    every = [s for step in steps for s in [step] + step.children]
    total_tokens = sum(s.tokens for s in every)
    total_cost = sum(s.cost for s in every)
    usage_rows = [row for row in map(usage_row, steps) if row]
    inexact = any("cost_usd" not in row or row.get("partial") == "1" for row in usage_rows)
    if inexact and total_cost:
        total_cost_text = f"at least {money(total_cost)}"
    else:
        total_cost_text = money(total_cost)
    agent_seconds = sum(s.agent_ms for s in every) // 1000
    agent_runs = sum(s.agent_runs for s in every)

    out = [marker, f"### Agent progress: `{branch}`", ""]
    out.append(f"**Status**: {status(steps, timeline, now)}  ")
    facts = [f"**Pull request**: #{pr}"] if pr else []
    facts += [
        f"**Started**: {time.strftime('%Y-%m-%d %H:%M %Z', LOCALTIME(first))}",
        f"**Last update**: {clock(last, day)}",
    ]
    out.append(" · ".join(facts) + "  ")
    out.append(
        f"**Elapsed**: {duration(elapsed)} · **Agents**: {agent_runs} run(s), {duration(agent_seconds)} · "
        f"**Tokens**: {tokens(total_tokens)} · **Cost** (API prices): {total_cost_text}"
    )
    out.append("")
    if not steps:
        out.append("No step has run yet.")
        return "\n".join(out) + "\n"

    out += ["| # | Step | Started | Finished | Duration | Tokens | Cost | Result |", "|---|---|---|---|---|---|---|---|"]

    def row(number, name, step):
        finished = "running" if step.open else clock(step.end, day)
        spent = duration(step.spent(now)) + (" …" if step.open else "")
        out.append(
            f"| {number} | {cell(name)} | {clock(step.start, day)} | {finished} | {spent} | "
            f"{tokens(step.total('tokens'))} | {money(step.total('cost'))} | {cell(step.result())} |"
        )

    # Last, not among the script's steps it overlaps.
    listed = sorted(steps, key=lambda s: s.name == "orchestration")
    for number, step in enumerate(listed, 1):
        row(number, step.name, step)
        for child in step.children:
            row("", f"↳ {child.name}", child)

    out += [""] + usage_section(steps)

    # The iterations: runs, time, tokens and outcomes per kind of step, in the order they first ran.
    limits = {"review": max_rounds, "CI fix": max_ci_fixes}
    kinds = {}
    for step in every:
        kinds.setdefault(kind_of(step.name), []).append(step)
    out += ["", "**Iterations**", "", "| Step | Runs | Time | Tokens | Cost | Outcomes |", "|---|---|---|---|---|---|"]
    for kind, runs in kinds.items():
        count = f"{len(runs)} of {limits[kind]}" if limits.get(kind) else str(len(runs))
        # A step's own usage only: its sub-steps have rows of their own kind.
        out.append(
            f"| {kind} | {count} | {duration(sum(s.spent(now) for s in runs))} | "
            f"{tokens(sum(s.tokens for s in runs))} | {money(sum(s.cost for s in runs))} | {cell(outcomes(kind, runs))} |"
        )

    notes = worth_a_look(steps, timeline, now, elapsed, day)
    if notes:
        out += ["", "**Worth a look**", ""] + [f"- {line}" for line in notes]

    shown = timeline[-TIMELINE_LIMIT:]
    out += ["", f"<details><summary>Timeline ({len(timeline)} milestones)</summary>", ""]
    if len(shown) < len(timeline):
        out += [f"The latest {len(shown)}; all of them are in run.log.", ""]
    out += ["| Time | Since start | Step | Milestone |", "|---|---|---|---|"]
    for at, step, text in shown:
        out.append(f"| {clock(at, day)} | +{duration(at - first)} | {cell(step)} | {cell(text)} |")
    out += ["", "</details>"]
    return "\n".join(out) + "\n"


def main(argv):
    parser = argparse.ArgumentParser(prog="progress.py")
    sub = parser.add_subparsers(dest="command", required=True)
    one = sub.add_parser("render")
    one.add_argument("file")
    one.add_argument("marker")
    one.add_argument("branch")
    one.add_argument("--pr")
    one.add_argument("--max-rounds", type=int)
    one.add_argument("--max-ci-fixes", type=int)
    one.add_argument("--now", type=int)
    one.add_argument("--only", choices=["usage"])
    args = parser.parse_args(argv[1:])
    try:
        with open(args.file, encoding="utf-8") as f:
            events = parse(f)
    except FileNotFoundError:
        events = []
    sys.stdout.write(render(events, args.marker, args.branch, args.pr, args.max_rounds, args.max_ci_fixes, args.now, args.only))


if __name__ == "__main__":
    main(sys.argv)
