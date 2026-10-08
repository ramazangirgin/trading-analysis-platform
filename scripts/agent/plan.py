"""Reads the work packages of a plan (.plans/<issue>-<slug>.md) for the agent scripts.

    plan.py packages <plan>   one line per package, in implementation order: "<id>\\t<status>\\t<name>"
    plan.py next <plan>       the first open package whose dependencies are all finished, or nothing
    plan.py check <plan> <id> problems with package <id> after its run, or nothing
    plan.py remaining <plan>  the packages that are neither done nor "not done", or nothing
    plan.py not-done <plan>   "- <id>: <reason>" per package marked "not done", for the PR body

A package is a `### WP<id>: <name>` heading (outside code fences) with a `- **Status**:` line
(`open`, `done`, or `not done: <reason>`), a `- **Depends on**:` line (`none`, a list `WP1, WP1c`,
a range `WP2-WP4` or `WP2–WP4` in document order, text in parentheses is ignored) and `- [ ]` /
`- [x]` steps. A missing Status line counts as `open`. A plan without any such heading is one
package, `all`, whose Status line is in the lines before the first `##` heading.

The implementation order is a topological order of the Depends-on lines that keeps the order of the
document among packages that are free at the same time. An unknown ID or a cycle is an error that
names the line.

Standard library only: run with `uv run --no-project python`.
"""

import re
import sys

HEADING = re.compile(r"^### (WP[0-9][0-9A-Za-z]*):\s*(.*?)\s*$")
STATUS = re.compile(r"^\s*- \*\*Status\*\*:\s*(.*?)\s*$")
DEPENDS = re.compile(r"^\s*- \*\*Depends on\*\*:\s*(.*?)\s*$")
STEP = re.compile(r"^\s*- \[([ xX])\]\s*(.*)$")
ID = re.compile(r"WP[0-9][0-9A-Za-z]*")
RANGE = re.compile(r"(WP[0-9][0-9A-Za-z]*)\s*[–—-]\s*(WP[0-9][0-9A-Za-z]*)")
NOT_DONE = re.compile(r"not done:\s*(\S.*)")

OPEN, DONE, NOT_DONE_STATUS, INVALID = "open", "done", "not done", "invalid"
FINISHED = (DONE, NOT_DONE_STATUS)


class PlanError(Exception):
    pass


class Package:
    def __init__(self, id, name, line):
        self.id = id
        self.name = name
        self.line = line
        self.status_text = None
        self.depends_text = None
        self.depends_line = line
        self.depends = []
        self.steps = []  # (ticked, text)

    @property
    def status(self):
        text = self.status_text
        if text is None or text == OPEN:
            return OPEN
        if text == DONE:
            return DONE
        if NOT_DONE.fullmatch(text):
            return NOT_DONE_STATUS
        return INVALID

    @property
    def reason(self):
        match = NOT_DONE.fullmatch(self.status_text or "")
        return match.group(1) if match else ""


def parse(text):
    """The packages of a plan, in document order."""
    packages = []
    current = None
    in_fence = False
    for number, line in enumerate(text.splitlines(), 1):
        if line.lstrip().startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        heading = HEADING.match(line)
        if heading:
            current = Package(heading.group(1), heading.group(2), number)
            packages.append(current)
            continue
        if line.startswith("#"):
            # Any other heading ends the package; a sub-heading (####) stays inside it.
            current = current if line.startswith("####") else None
            continue
        if current is None:
            continue
        status, depends, step = STATUS.match(line), DEPENDS.match(line), STEP.match(line)
        if status and current.status_text is None:
            current.status_text = status.group(1)
        elif depends and current.depends_text is None:
            current.depends_text = depends.group(1)
            current.depends_line = number
        elif step:
            current.steps.append((step.group(1) != " ", step.group(2)))
    if not packages:
        packages = [parse_whole(text)]
    resolve(packages)
    return packages


def parse_whole(text):
    """A plan without work packages: one package, `all`. Its Status line is in the preamble."""
    package = Package("all", "the whole plan", 1)
    in_fence = False
    for line in text.splitlines():
        if line.lstrip().startswith("```"):
            in_fence = not in_fence
        elif in_fence:
            continue
        elif line.startswith("## "):
            break
        else:
            status = STATUS.match(line)
            if status and package.status_text is None:
                package.status_text = status.group(1)
    for line in text.splitlines():
        step = STEP.match(line)
        if step:
            package.steps.append((step.group(1) != " ", step.group(2)))
    return package


def resolve(packages):
    """Turns each Depends-on text into package IDs; fails on an unknown ID."""
    ids = [p.id for p in packages]
    for package in packages:
        text = re.sub(r"\([^)]*\)", "", package.depends_text or "")
        text = re.sub(r"\band\b", ",", text)
        for part in text.split(","):
            part = part.strip().rstrip(".").strip()
            if not part or part.lower() == "none":
                continue
            where = f"line {package.depends_line}"
            span = RANGE.fullmatch(part)
            if span:
                first, last = span.groups()
                for id in (first, last):
                    if id not in ids:
                        raise PlanError(f"{where}: unknown package {id} in 'Depends on' of {package.id}")
                a, b = ids.index(first), ids.index(last)
                if a > b:
                    raise PlanError(f"{where}: the range {part} of {package.id} is not in document order")
                package.depends += ids[a : b + 1]
            elif ID.fullmatch(part):
                if part not in ids:
                    raise PlanError(f"{where}: unknown package {part} in 'Depends on' of {package.id}")
                package.depends.append(part)
            else:
                raise PlanError(f"{where}: cannot read '{part}' in 'Depends on' of {package.id}")


def ordered(packages):
    """Topological order, document order among the packages that are free at the same time."""
    done, result, pending = set(), [], list(packages)
    while pending:
        for package in pending:
            if all(d in done for d in package.depends):
                break
        else:
            cycle = ", ".join(f"{p.id} (line {p.depends_line})" for p in pending)
            raise PlanError(f"dependency cycle among {cycle}")
        pending.remove(package)
        done.add(package.id)
        result.append(package)
    return result


def load(path):
    with open(path, encoding="utf-8") as f:
        try:
            return ordered(parse(f.read()))
        except PlanError as error:
            raise PlanError(f"{path}: {error}") from None


def find(packages, id):
    for package in packages:
        if package.id == id:
            return package
    raise PlanError(f"no package {id}")


def next_package(packages):
    """The first open package whose dependencies are done or not done (it is attempted anyway)."""
    status = {p.id: p.status for p in packages}
    for package in packages:
        if package.status in (OPEN, INVALID) and all(status[d] in FINISHED for d in package.depends):
            return package
    return None


def problems(package):
    """What is wrong with a package after its run, one line each."""
    found = []
    if package.status == INVALID:
        found.append(
            f"{package.id}: the Status line is malformed ('{package.status_text}'); "
            "it must be `open`, `done` or `not done: <reason>`"
        )
    elif package.status == OPEN:
        how = "has no Status line" if package.status_text is None else "is still `open`"
        found.append(f"{package.id}: it {how}; set `- **Status**: done` or `- **Status**: not done: <reason>`")
    elif package.status == DONE:
        unticked = [text for ticked, text in package.steps if not ticked]
        if unticked:
            found.append(f"{package.id}: marked done, but {len(unticked)} step(s) are not ticked (`- [x]`):")
            found += [f"  - {text}" for text in unticked]
    return found


def line_of(package):
    return f"{package.id}\t{package.status}\t{package.name}"


def lines_of(command, packages, args):
    """The output lines of a command; empty for "nothing"."""
    if command == "packages":
        return [line_of(p) for p in packages]
    if command == "next":
        package = next_package(packages)
        return [package.id] if package else []
    if command == "check":
        return problems(find(packages, args[0]))
    if command == "remaining":
        return [line_of(p) for p in packages if p.status not in FINISHED]
    if command == "not-done":
        return [f"- {p.id}: {p.reason}" for p in packages if p.status == NOT_DONE_STATUS]
    return None


def main(argv):
    if len(argv) < 3:
        sys.exit(__doc__)
    command, path = argv[1], argv[2]
    try:
        lines = lines_of(command, load(path), argv[3:])
        if lines is None:
            sys.exit(f"unknown command: {command}")
        if lines:
            print("\n".join(lines))
    except (PlanError, OSError, IndexError) as error:
        sys.exit(f"plan.py: {error}")


if __name__ == "__main__":
    main(sys.argv)
