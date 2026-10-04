"""Review findings: from the review agent's output to a GitHub review, and back (scripts/agent/).

    review.py build <round> <agent output.json> <pr files.json> <out dir>
        Numbers the findings R<round>-<n>, writes <out dir>/review.json (the GitHub review: one
        inline comment per finding that sits on a line of the diff), <out dir>/summary.md (the step
        comment: counts, the findings table and the findings themselves, encoded, for fix.sh) and
        <out dir>/findings.json.
    review.py findings <round>  < step comment bodies
        The findings of review round <round>, as JSON.
    review.py comment-ids  < review comments JSON
        {"R1-3": <comment id>, ...} for the agent's inline comments.
    review.py outcomes  < step comment bodies
        {"R1-3": {"outcome": "fixed", "reason": ...}, ...} from the fix steps.
    review.py findings-table  < step comment bodies
        Markdown table of every round's findings and their outcome (open when none was recorded).
    review.py field <findings.json> <index> <key>     one field of a finding (lists as JSON)
    review.py lookup <object.json> <key>              a value, empty when missing
    review.py contains <list.json> <number>           exit status 0 if the list holds it
    review.py record <outcomes.json> <id> <outcome> <reason>
    review.py outcomes-section <outcomes.json>        Markdown table and the encoded outcomes

Standard library only: run with `uv run --no-project python`.
"""

import base64
import json
import re
import sys

PRIORITIES = ["CRITICAL", "MAJOR", "MINOR"]
HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")
FINDING_MARKER = re.compile(r"<!-- agent-finding id=(R\d+-\d+) -->")
STEP_MARKER = re.compile(r"<!-- agent-step ([^>]*?) -->")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def encode(name, value):
    data = base64.b64encode(json.dumps(value).encode()).decode()
    return f"<!-- agent-{name} {data} -->"


def decode_all(name, text):
    for match in re.finditer(rf"<!-- agent-{name} ([A-Za-z0-9+/=]+) -->", text):
        yield json.loads(base64.b64decode(match.group(1)))


def commentable_lines(files):
    """Right-side line numbers GitHub accepts a review comment on, per file: the diff's hunks."""
    lines = {}
    for f in files:
        seen = set()
        new_line = 0
        for row in (f.get("patch") or "").splitlines():
            hunk = HUNK.match(row)
            if hunk:
                new_line = int(hunk.group(1))
                continue
            if row.startswith("-"):
                continue
            seen.add(new_line)
            new_line += 1
        lines[f["filename"]] = seen
    return lines


def cell(text):
    """Text for a Markdown table cell: one line, no column separators."""
    return " ".join(str(text).split()).replace("|", "\\|")


def location(item):
    return f"`{item['path']}:{item['line']}`" if item.get("line") else f"`{item['path']}`"


def comment_body(finding):
    parts = [
        f"<!-- agent-finding id={finding['id']} -->",
        f"**{finding['id']} · {finding['priority']}** · {finding['title']}",
        "",
        finding["problem"],
        "",
        "**Possible solutions**",
    ]
    parts += [f"{i}. {s}" for i, s in enumerate(finding["solutions"], 1)]
    if finding.get("also_at"):
        parts += ["", "Same finding also at: " + ", ".join(location(a) for a in finding["also_at"])]
    return "\n".join(parts)


def build(round_no, agent_output, files_path, out_dir):
    with open(agent_output, encoding="utf-8") as f:
        output = json.load(f)["structured_output"]
    with open(files_path, encoding="utf-8") as f:
        lines = commentable_lines(json.load(f))

    findings = sorted(output["findings"], key=lambda x: PRIORITIES.index(x["priority"]))
    comments, general = [], []
    for n, finding in enumerate(findings, 1):
        finding["id"] = f"R{round_no}-{n}"
        line = finding.get("line")
        if line and line in lines.get(finding["path"], ()):
            finding["inline"] = True
            comments.append({"path": finding["path"], "line": line, "side": "RIGHT", "body": comment_body(finding)})
        elif lines.get(finding["path"]):
            # The file is in the diff, the line is not: on the nearest line of the diff, saying so. (A
            # review cannot hold file-level comments; GitHub rejects subject_type there.)
            anchor = min(lines[finding["path"]], key=lambda n: abs(n - (line or 0)))
            finding["inline"] = True
            note = f"_On line {line} of this file, outside the diff._\n\n" if line else "_On this file._\n\n"
            body = comment_body(finding).replace("\n", "\n" + note, 1)
            comments.append({"path": finding["path"], "line": anchor, "side": "RIGHT", "body": body})
        else:
            finding["inline"] = False
            general.append(finding)

    counts = {p: sum(1 for f in findings if f["priority"] == p) for p in PRIORITIES}
    count_line = ", ".join(f"{counts[p]} {p}" for p in PRIORITIES)
    another = "yes" if output.get("another_round_needed") else "no"

    review_body = [f"Review round {round_no} by the review agent: {count_line}."]
    for finding in general:
        review_body += ["", "---", "", f"On {location(finding)}, not part of the diff:", "", comment_body(finding)]
    with open(f"{out_dir}/review.json", "w", encoding="utf-8") as f:
        json.dump({"event": "COMMENT", "body": "\n".join(review_body), "comments": comments}, f)

    summary = [
        f"### Review round {round_no}",
        "",
        output["summary"],
        "",
        f"**Findings**: {count_line}. **Another round needed**: {another}.",
    ]
    if findings:
        summary += ["", "| ID | Priority | Where | Finding |", "|---|---|---|---|"]
        summary += [f"| {f['id']} | {f['priority']} | {location(f)} | {cell(f['title'])} |" for f in findings]
    summary += ["", encode("findings", {"round": round_no, "findings": findings})]
    with open(f"{out_dir}/summary.md", "w", encoding="utf-8") as f:
        f.write("\n".join(summary) + "\n")
    with open(f"{out_dir}/findings.json", "w", encoding="utf-8") as f:
        json.dump(findings, f)


def main(argv):
    command, args = argv[1], argv[2:]
    if command == "build":
        build(int(args[0]), *args[1:])
    elif command == "findings":
        found = [r for r in decode_all("findings", sys.stdin.read()) if r["round"] == int(args[0])]
        print(json.dumps(found[-1]["findings"] if found else []))
    elif command == "comment-ids":
        ids = {}
        for comment in json.load(sys.stdin):
            match = FINDING_MARKER.search(comment.get("body") or "")
            if match:
                ids[match.group(1)] = comment["id"]
        print(json.dumps(ids))
    elif command == "outcomes":
        outcomes = {}
        for record in decode_all("outcomes", sys.stdin.read()):
            outcomes.update(record)
        print(json.dumps(outcomes))
    elif command == "findings-table":
        text = sys.stdin.read()
        outcomes = {}
        for record in decode_all("outcomes", text):
            outcomes.update(record)
        print("| ID | Priority | Finding | Outcome |\n|---|---|---|---|")
        for review in decode_all("findings", text):
            for f in review["findings"]:
                outcome = outcomes.get(f["id"], {}).get("outcome", "open")
                print(f"| {f['id']} | {f['priority']} | {cell(f['title'])} | {outcome} |")
    elif command == "field":
        value = load(args[0])[int(args[1])].get(args[2], "")
        print(json.dumps(value) if isinstance(value, (dict, list)) else value)
    elif command == "lookup":
        print(load(args[0]).get(args[1], ""))
    elif command == "contains":
        sys.exit(0 if int(args[1]) in load(args[0]) else 1)
    elif command == "record":
        outcomes = load(args[0])
        outcomes[args[1]] = {"outcome": args[2], "reason": args[3]}
        with open(args[0], "w", encoding="utf-8") as f:
            json.dump(outcomes, f)
    elif command == "outcomes-section":
        outcomes = load(args[0])
        if outcomes:
            print("| ID | Outcome | Note |\n|---|---|---|")
            for key, value in outcomes.items():
                print(f"| {key} | {value['outcome']} | {cell(value['reason'])} |")
            print()
        print(encode("outcomes", outcomes))
    else:
        sys.exit(f"unknown command: {command}")


if __name__ == "__main__":
    main(sys.argv)
