"""Checks the relative links and anchors of the Markdown documents.

    doc_links.py [<file>...]

Default files: README.md, CLAUDE.md, docs/**/*.md and deploy/server/README.md of the repository.
For every Markdown link and image with a relative target the target must exist, and a target with
an #anchor must have a heading whose GitHub slug is that anchor. External links are not checked.
Prints "<file>:<line>: <target>: <why>" per broken link and exits 1 when there is one.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LINK = re.compile(r"\]\(\s*<?([^)\s>]*)>?(?:\s+\"[^\"]*\")?\s*\)")
CODE_SPAN = re.compile(r"`[^`]*`")
HEADING = re.compile(r"^ {0,3}#{1,6}\s+(.*?)\s*#*\s*$")
FENCE = re.compile(r"^ {0,3}(`{3,}|~{3,})")
EXTERNAL = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]*:")


def slug(heading):
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", heading)
    text = text.replace("`", "").lower()
    text = re.sub(r"[^\w\s-]", "", text)
    return text.replace(" ", "-")


def prose_lines(path):
    """Yields (line number, line) outside code fences."""
    fence = None
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        match = FENCE.match(line)
        if match:
            marker = match.group(1)[0]
            if fence is None:
                fence = marker
            elif fence == marker:
                fence = None
            continue
        if fence is None:
            yield number, line


def anchors(path):
    seen = {}
    result = set()
    for _, line in prose_lines(path):
        match = HEADING.match(line)
        if not match:
            continue
        base = slug(match.group(1))
        count = seen.get(base, 0)
        seen[base] = count + 1
        result.add(base if count == 0 else f"{base}-{count}")
    return result


def check(path, anchor_cache):
    problems = []
    for number, line in prose_lines(path):
        for target in LINK.findall(CODE_SPAN.sub("", line)):
            if not target or EXTERNAL.match(target):
                continue
            file_part, _, anchor = target.partition("#")
            destination = (path.parent / file_part).resolve() if file_part else path
            if not destination.exists():
                problems.append(f"{display(path)}:{number}: {target}: file not found")
                continue
            if anchor and destination.suffix == ".md" and destination.is_file():
                if destination not in anchor_cache:
                    anchor_cache[destination] = anchors(destination)
                if anchor not in anchor_cache[destination]:
                    problems.append(f"{display(path)}:{number}: {target}: no such heading")
    return problems


def display(path):
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def default_files():
    files = [ROOT / "README.md", ROOT / "CLAUDE.md", ROOT / "deploy/server/README.md"]
    files += sorted((ROOT / "docs").rglob("*.md"))
    return [f for f in files if f.exists()]


def main(argv):
    files = [Path(a).resolve() for a in argv] or default_files()
    cache = {}
    problems = [p for f in files for p in check(f, cache)]
    for problem in problems:
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
