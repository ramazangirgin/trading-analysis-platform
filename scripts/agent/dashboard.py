"""The step graph of an agent process (a plan branch) for the Agent Dashboard issue.

    dashboard.py graph <record.json> [--now <epoch>] [--max-rounds <n>] [--max-ci-fixes <n>]

A record is the JSON that scripts/agent/dashboard.sh gathers for one plan branch:

    branch        plan/<issue>-<slug>
    pr            null, or {number, url, state, isDraft, labels: [names], headRefOid}
    plan_text     the plan file on the branch, or ""
    repo_url      https://github.com/<owner>/<repo>
    comments      the agent account's comments: [{url, body}], oldest first
    ci_runs       [{headSha, url, status, conclusion}], newest first
    progress_file the branch's progress.tsv in the state directory, or ""
    run_alive     a live run holds the branch's lock (run.pid)

The graph is the flow (plan, plan approved, the work packages, checks, then the steps of progress.tsv
in the order they ran, then the next expected step) as a Mermaid `flowchart LR`, coloured by the state
of each step. Without a progress.tsv (a run of another machine) the steps come from the plan and from
the agent-step markers of the comments.

    dashboard.py section <record.json> [same options]     the section of the process
    dashboard.py active <record.json>                     exit 0 when the process is listed, else 1
    dashboard.py merge <old body file> [--section <file>]... --active <branch>... [--now <epoch>]

A record has two more fields for the section: issue_title and progress_comment_url (or absent).
merge prints the new body: the given sections replace or add, sections of branches not in --active
are dropped, the others stay.

Standard library only: run with `uv run --no-project python`.
"""

import argparse
import dataclasses
import json
import re
import sys
import time
import types

import agent_json
import plan
import progress

SECTION_START = "<!-- agent-process"
SECTION_END = "<!-- /agent-process -->"
SECTION = re.compile(r"<!-- agent-process (\S+) -->.*?<!-- /agent-process -->", re.DOTALL)
BODY_MARKER = "<!-- agent-dashboard -->"
BODY_LIMIT = 60_000

STATES = ("done", "running", "waiting", "failed", "stopped", "todo")

CLASSES = {
    "done": "fill:#d1fae5,stroke:#10b981,color:#064e3b",
    "running": "fill:#dbeafe,stroke:#3b82f6,color:#1e3a8a",
    "waiting": "fill:#fef9c3,stroke:#eab308,color:#713f12",
    "failed": "fill:#fee2e2,stroke:#ef4444,color:#7f1d1d",
    "stopped": "fill:#ffedd5,stroke:#f97316,color:#7c2d12,stroke-dasharray:5 5",
    "todo": "fill:#f3f4f6,stroke:#9ca3af,color:#374151",
}

# The agent-step markers' names of the steps, by the kind of step progress.py gives them.
MARKER_STEPS = {"CI fix": "ci-fix", "review": "review", "fix": "fix", "update with main": "update-main", "finalise": "finalise"}


@dataclasses.dataclass
class Node:
    label: str
    state: str
    key: tuple = ()
    minutes: int = None
    note: str = ""


@dataclasses.dataclass
class Top:
    """A top-level step after the implementation, from progress.tsv or from a marker."""

    name: str
    kind: str
    state: str
    round: int = 0
    approved: bool = False
    start: int = None
    occurrence: int = 0


def pull_request(record):
    return record.get("pr") or {}


def labels_of(record):
    return {label if isinstance(label, str) else label.get("name", "") for label in pull_request(record).get("labels", [])}


def markers_of(record):
    """(url, marker pairs) of every agent-step marker in the comments, in comment order."""
    found = []
    for comment in record.get("comments", []):
        for match in agent_json.MARKER.finditer(comment.get("body", "")):
            found.append((comment.get("url", ""), agent_json.parse_marker(match.group(1))))
    return found


def read_events(record):
    path = record.get("progress_file")
    if not path:
        return []
    try:
        with open(path, encoding="utf-8") as f:
            return progress.parse(f)
    except OSError:
        return []


def packages_of(record):
    if not record.get("plan_text"):
        return []
    try:
        return plan.ordered(plan.parse(record["plan_text"]))
    except plan.PlanError:
        return []


def number_of(name):
    match = re.search(r"(\d+)$", name)
    return int(match.group(1)) if match else 0


def state_of(step, alive, waits=False):
    if step.open:
        if not alive:
            return "stopped"
        return "waiting" if waits else "running"
    outcome = step.outcome or ""
    if outcome.startswith("failed"):
        return "failed"
    if outcome == "interrupted":
        return "stopped"
    return "done"


def tops_from_steps(steps, alive):
    tops = []
    seen = {}
    for step in steps:
        kind = progress.kind_of(step.name)
        if kind in ("plan", "implement", "work package", "final checks") or step.name == "orchestration":
            continue
        seen[kind] = seen.get(kind, 0) + 1
        tops.append(
            Top(
                step.name,
                kind,
                state_of(step, alive, waits=kind == "CI"),
                round=number_of(step.name),
                approved=kind == "review" and "approved" in step.result(),
                start=step.start,
                occurrence=seen[kind],
            )
        )
    return tops


def tops_from_markers(markers, record):
    tops = []
    seen = {}
    by_step = {value: key for key, value in MARKER_STEPS.items()}
    for _, marker in markers:
        kind = by_step.get(marker.get("step"))
        if not kind:
            continue
        number = int(marker.get("round", 0) or 0)
        seen[kind] = seen.get(kind, 0) + 1
        name = {"CI fix": f"CI fix {number}", "review": f"review R{number}", "fix": f"fix R{number}"}.get(kind, kind)
        tops.append(Top(name, kind, "done", number, marker.get("approved") == "1", occurrence=seen[kind]))
    head = pull_request(record).get("headRefOid", "")
    run = next((r for r in record.get("ci_runs", []) if head and r.get("headSha") == head), None)
    if run:
        if run.get("status") != "completed":
            state = "waiting"
        else:
            state = "done" if run.get("conclusion") == "success" else "failed"
        tops.append(Top(f"CI {head[:7]}", "CI", state))
    return tops


def link_key(top):
    if top.kind == "CI":
        return ("ci", top.name.split()[-1])
    if top.kind in MARKER_STEPS:
        return ("comment", MARKER_STEPS[top.kind], top.round, top.occurrence)
    return ()


def next_label(tops, implemented, max_rounds, max_ci_fixes):
    """The label of the step expected after the last one, or None."""
    if not tops:
        return "CI" if implemented else None
    last = tops[-1]
    if last.state != "done" and not (last.kind == "CI" and last.state == "failed"):
        return None
    if last.kind in ("CI fix", "fix", "update with main"):
        return "CI"
    if last.kind == "review":
        return "finalise" if last.approved else f"fix R{last.round}"
    if last.kind != "CI":
        return None
    if last.state == "failed":
        fixes = sum(1 for t in tops if t.kind == "CI fix")
        return f"CI fix {fixes + 1}" if max_ci_fixes is None or fixes < max_ci_fixes else None
    reviews = [t for t in tops if t.kind == "review"]
    if not reviews:
        return "review R1"
    latest = reviews[-1]
    if latest.approved:
        return "finalise"
    if not any(t.kind == "fix" and t.round == latest.round for t in tops):
        return f"fix R{latest.round}"
    return f"review R{latest.round + 1}" if max_rounds is None or latest.round < max_rounds else "finalise"


def package_nodes(packages, implement, alive, now):
    """One node per work package, then the final checks."""
    children = implement.children if implement else []
    entries = packages
    if not entries:
        entries = [
            types.SimpleNamespace(id=c.name.split()[0], name="", status=plan.OPEN)
            for c in children
            if progress.kind_of(c.name) != "final checks"
        ]
    nodes = []
    for package in entries:
        found = [c for c in children if c.name.split()[0] == package.id]
        if found:
            child = found[-1]
            state = state_of(child, alive)
            not_done = state == "done" and "not done" in child.result()
            minutes = child.spent(now) // 60 if state == "running" else None
            nodes.append(Node(package.id, state, ("package", package), minutes, "not done" if not_done else ""))
        elif package.status == plan.DONE:
            nodes.append(Node(package.id, "done", ("package", package)))
        elif package.status == plan.NOT_DONE_STATUS:
            nodes.append(Node(package.id, "done", ("package", package), note="not done"))
        else:
            nodes.append(Node(package.id, "todo", ("package", package)))
    checks = [c for c in children if progress.kind_of(c.name) == "final checks"]
    if checks:
        state = state_of(checks[-1], alive)
        minutes = checks[-1].spent(now) // 60 if state == "running" else None
        nodes.append(Node("checks", state, ("files",), minutes))
    else:
        done = implement is not None and not implement.open and not (implement.outcome or "").startswith("failed")
        nodes.append(Node("checks", "done" if done else "todo", ("files",)))
    return nodes


def analyse(record, now=None, max_rounds=None, max_ci_fixes=None):
    """The process of a record: its stage, current step, who is waited for and its nodes."""
    now = now if now is not None else int(time.time())
    labels = labels_of(record)
    alive = bool(record.get("run_alive"))
    markers = markers_of(record)
    events = read_events(record)
    steps, timeline = progress.build(events)
    under_review = "plan" in labels
    packages = packages_of(record)
    implement = next((s for s in reversed(steps) if progress.kind_of(s.name) == "implement"), None)
    plan_step = next((s for s in reversed(steps) if progress.kind_of(s.name) == "plan"), None)

    if steps:
        tops = tops_from_steps(steps, alive)
    elif markers:
        tops = tops_from_markers(markers, record)
    else:
        tops = []
    if steps:
        implemented = bool(implement) and not implement.open and not (implement.outcome or "").startswith("failed")
    else:
        implemented = any(m.get("step") == "implement" for _, m in markers) or bool(tops)

    last_marker = markers[-1][1].get("step") if markers else ""
    open_step = any(s.open for s in steps if s.name != "orchestration")
    stop_flag = not (alive and open_step) and (
        last_marker == "stop" or bool(timeline and timeline[-1][2].startswith("stopping:"))
    )

    nodes = []
    if under_review:
        pstate = "failed" if plan_step and plan_step.outcome == "failed" else "waiting"
    elif plan_step:
        pstate = state_of(plan_step, alive)
    else:
        pstate = "done"
    nodes.append(Node("plan", pstate, ("plan",)))
    nodes.append(Node("plan approved", "todo" if under_review else "done", ("pr",)))
    wp_nodes = package_nodes(packages, implement, alive, now)
    if not steps and implemented:
        wp_nodes[-1].state = "done"
    nodes += wp_nodes
    for top in tops:
        minutes = int((now - top.start) // 60) if top.state == "running" and top.start is not None else None
        nodes.append(Node(top.name, top.state, link_key(top), minutes))

    ready = bool(tops) and tops[-1].kind == "finalise" and tops[-1].state == "done"
    stopped = stop_flag or (not alive and open_step)
    if stop_flag and not ready:
        tail = nodes[-1]
        if tail.state == "done":
            tail.state = "stopped"
    if ready:
        nodes.append(Node("ready for review", "done", ("pr",)))
    elif not stopped and not under_review:
        label = next_label(tops, implemented, max_rounds, max_ci_fixes)
        if label:
            nodes.append(Node(label, "todo"))

    if under_review:
        stage = "plan review"
    elif stopped:
        stage = "stopped"
    elif ready:
        stage = "ready for review"
    else:
        stage = "agent loop"

    current = next((s for s in reversed(steps) if s.open and s.name != "orchestration"), None)
    if current:
        sub = [c for c in current.children if c.open]
        step_name = current.name + (f" › {sub[-1].name}" if sub else "")
    elif under_review:
        step_name = "plan"
    else:
        shown = [n for n in nodes if n.state != "todo"]
        step_name = shown[-1].label if shown else ""

    if stage in ("plan review", "stopped", "ready for review"):
        waiting_for = "developer"
    elif any(n.state == "waiting" and n.label.startswith("CI") for n in nodes):
        waiting_for = "CI"
    else:
        waiting_for = "nobody"

    return {
        "stage": stage,
        "step": step_name,
        "waiting_for": waiting_for,
        "nodes": nodes,
        "alive": alive,
        "steps": steps,
        "events": events,
        "packages": packages,
        "tops": tops,
        "markers": markers,
    }


def slug(heading):
    """GitHub's anchor of a heading."""
    return re.sub(r"[^\w\s-]", "", heading.lower()).strip().replace(" ", "-")


def plan_url(record):
    branch = record.get("branch", "")
    path = record.get("plan_path") or ".plans/" + branch.split("/", 1)[-1] + ".md"
    return f"{record.get('repo_url', '')}/blob/{branch}/{path}"


def url_of(key, record, markers):
    """The URL a node links to, or ""."""
    if not key:
        return ""
    pr = pull_request(record)
    kind = key[0]
    if kind == "plan":
        return plan_url(record) if record.get("repo_url") else ""
    if kind == "pr":
        return pr.get("url", "")
    if kind == "files":
        return pr["url"] + "/files" if pr.get("url") else ""
    if kind == "package":
        package = key[1]
        if package.id == "all":
            return plan_url(record) if record.get("repo_url") else ""
        return f"{plan_url(record)}#{slug(f'{package.id}: {package.name}')}" if record.get("repo_url") else ""
    if kind == "ci":
        run = next((r for r in record.get("ci_runs", []) if r.get("headSha", "").startswith(key[1])), None)
        return run.get("url", "") if run else ""
    if kind == "comment":
        _, step, number, occurrence = key
        found = [url for url, m in markers if m.get("step") == step and (step in ("update-main", "finalise") or int(m.get("round", 0) or 0) == number)]
        if not found:
            return ""
        return found[min(occurrence, len(found)) - 1] if step in ("update-main", "finalise") else found[-1]
    return ""


def escape(text):
    for char, entity in (("#", "#35;"), ('"', "#quot;"), ("<", "#lt;"), (">", "#gt;")):
        text = text.replace(char, entity)
    return text


def node_label(node):
    label = escape(node.label)
    if node.note:
        label += f"<br/>{escape(node.note)}"
    if node.minutes is not None:
        label += f"<br/>{node.minutes} min"
    return label


def graph(record, now=None, max_rounds=None, max_ci_fixes=None, process=None):
    """The Mermaid block of a record's flow."""
    process = process or analyse(record, now, max_rounds, max_ci_fixes)
    lines = ["```mermaid", "flowchart LR"]
    nodes = process["nodes"]
    for number, node in enumerate(nodes, 1):
        lines.append(f'  n{number}["{node_label(node)}"]:::{node.state}')
    for number in range(1, len(nodes)):
        lines.append(f"  n{number} --> n{number + 1}")
    for state in STATES:
        lines.append(f"  classDef {state} {CLASSES[state]}")
    for number, node in enumerate(nodes, 1):
        url = url_of(node.key, record, process["markers"])
        if url:
            lines.append(f'  click n{number} href "{url}" _blank')
    lines.append("```")
    return "\n".join(lines)


def is_active(record):
    """Whether the process is listed: a live run, an open pull request labelled plan or agent, or an
    open draft whose loop gave up (the newest marker is stop) and waits for the developer."""
    if record.get("run_alive"):
        return True
    pr = pull_request(record)
    if pr.get("state") != "OPEN":
        return False
    if labels_of(record) & {"plan", "agent"}:
        return True
    markers = markers_of(record)
    return bool(pr.get("isDraft")) and bool(markers) and markers[-1][1].get("step") == "stop"


def issue_number(branch):
    match = re.match(r"plan/(\d+)-", branch)
    return int(match.group(1)) if match else 0


def work_package(nodes):
    """'k of n' for the last work package begun, or ""."""
    packages = [n for n in nodes if n.key and n.key[0] == "package"]
    busy = [i for i, n in enumerate(packages, 1) if n.state not in ("done", "todo")]
    finished = [i for i, n in enumerate(packages, 1) if n.state == "done" and not n.note]
    current = busy[0] if busy else (finished[-1] if finished else 0)
    return f"{current} of {len(packages)}" if len(packages) > 1 and current else ""


def facts(record, process, now, max_rounds):
    stage = f"**Stage**: {process['stage']}"
    parts = [stage, f"**Step**: {process['step'] or '–'}"]
    package = work_package(process["nodes"])
    if package:
        parts.append(f"**Work package**: {package}")
    reviews = [t.round for t in process["tops"] if t.kind == "review"]
    if reviews:
        parts.append(f"**Round**: {reviews[-1]}" + (f" of {max_rounds}" if max_rounds else ""))
    if process["events"]:
        total = progress.totals(process["steps"], process["events"], now)
        day = time.strftime("%Y-%m-%d", progress.LOCALTIME(total["first"]))
        parts += [
            f"**Started**: {time.strftime('%Y-%m-%d %H:%M %Z', progress.LOCALTIME(total['first']))}",
            f"**Last update**: {progress.clock(total['last'], day)}",
            f"**Elapsed**: {progress.duration(total['elapsed'])}",
            f"**Tokens**: {progress.tokens(total['tokens'])}",
            f"**Cost**: {total['cost_text']}",
        ]
    parts.append(f"**Waiting for**: {process['waiting_for']}")
    return " · ".join(parts)


def links(record, process):
    """The (label, text, url) rows of the link table; a row without a URL is left out."""
    repo = record.get("repo_url", "")
    branch = record.get("branch", "")
    pr = pull_request(record)
    number = issue_number(branch)
    rows = [
        ("Issue", f"#{number}", f"{repo}/issues/{number}" if repo and number else ""),
        ("Plan", "plan file", plan_url(record) if repo else ""),
        ("Pull request", f"#{pr['number']}" if pr.get("number") else "", pr.get("url", "")),
        ("Files", "changed files", pr["url"] + "/files" if pr.get("url") else ""),
        ("Progress comment", "comment", record.get("progress_comment_url", "")),
    ]
    reviews = {}
    final = ""
    for url, marker in process["markers"]:
        if marker.get("step") == "review":
            reviews[int(marker.get("round", 0) or 0)] = url
        elif marker.get("step") == "finalise":
            final = url
    rows += [(f"Review R{n}", "comment", url) for n, url in sorted(reviews.items())]
    rows.append(("Final comment", "comment", final))
    head = pr.get("headRefOid", "")
    run = next((r for r in record.get("ci_runs", []) if head and r.get("headSha") == head), None)
    rows.append(("CI (head)", head[:7], run.get("url", "") if run else ""))
    rows.append(("Branch", f"`{branch}`", f"{repo}/tree/{branch}" if repo and branch else ""))
    return [(label, text, url) for label, text, url in rows if url]


def link_table(record, process):
    lines = ["| Link | |", "|---|---|"]
    lines += [f"| {label} | [{text}]({url}) |" for label, text, url in links(record, process)]
    return "\n".join(lines)


def section(record, now=None, max_rounds=None, max_ci_fixes=None):
    """The dashboard section of one process."""
    now = now if now is not None else int(time.time())
    process = analyse(record, now, max_rounds, max_ci_fixes)
    branch = record.get("branch", "")
    title = " ".join(record.get("issue_title", "").split())
    heading = f"### #{issue_number(branch)} {title}: `{branch}`" if title else f"### #{issue_number(branch)}: `{branch}`"
    return "\n".join(
        [
            f"{SECTION_START} {branch} -->",
            heading,
            facts(record, process, now, max_rounds),
            graph(record, process=process),
            link_table(record, process),
            SECTION_END,
        ]
    )


def sections_of(body):
    """The sections of a body by branch, in body order."""
    return {m.group(1): m.group(0) for m in SECTION.finditer(body)}


def without_links(text):
    return re.sub(r"\n\| Link \| \|\n(?:\|.*\n)*", "\n", text)


def merge(old_body, new_sections, active, now=None):
    """The new body: own sections replace or add, sections of branches not in active go, others stay."""
    now = now if now is not None else int(time.time())
    found = sections_of(old_body) if BODY_MARKER in old_body else {}
    found.update(new_sections)
    kept = sorted(((b, s) for b, s in found.items() if b in active), key=lambda item: (issue_number(item[0]), item[0]))
    header = [BODY_MARKER, "# Agent Dashboard", "", f"Updated {time.strftime('%Y-%m-%d %H:%M %Z', progress.LOCALTIME(now))}", ""]
    notes = []

    def build():
        body = [section for _, section in kept] or ["No active agent processes"]
        return "\n".join(header + ["\n\n".join(body)] + notes) + "\n"

    text = build()
    if len(text) > BODY_LIMIT:
        kept = [(b, without_links(s)) for b, s in kept]
        notes[:] = ["", "The link tables are left out: the body would exceed GitHub's size limit."]
        text = build()
    omitted = 0
    while len(text) > BODY_LIMIT and len(kept) > 1:
        kept.pop()
        omitted += 1
        notes[:] = ["", f"The link tables and {omitted} process(es) are left out: the body would exceed GitHub's size limit."]
        text = build()
    return text


def main(argv):
    parser = argparse.ArgumentParser(prog="dashboard.py")
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("graph", "section", "active"):
        one = sub.add_parser(name)
        one.add_argument("record")
        one.add_argument("--now", type=int)
        one.add_argument("--max-rounds", type=int)
        one.add_argument("--max-ci-fixes", type=int)
    joined = sub.add_parser("merge")
    joined.add_argument("old_body")
    joined.add_argument("--section", action="append", default=[])
    joined.add_argument("--active", nargs="*", default=[])
    joined.add_argument("--now", type=int)
    args = parser.parse_args(argv[1:])
    if args.command == "merge":
        with open(args.old_body, encoding="utf-8") as f:
            old = f.read()
        new = {}
        for path in args.section:
            with open(path, encoding="utf-8") as f:
                new.update(sections_of(f.read()))
        sys.stdout.write(merge(old, new, set(args.active), args.now))
        return 0
    with open(args.record, encoding="utf-8") as f:
        record = json.load(f)
    if args.command == "active":
        return 0 if is_active(record) else 1
    render = graph if args.command == "graph" else section
    print(render(record, args.now, args.max_rounds, args.max_ci_fixes))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
