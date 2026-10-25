"""Resolves merge conflicts that are only the version lines.

    merge_versions.py resolve <file>...     rewrite the version-only conflict hunks in place
    merge_versions.py is-version-file <file>  exit 0 for a version file, 1 otherwise

The version files are the ones scripts/version.sh keeps (gradle.properties,
artifact/frontend/package.json, artifact/ta-runner/ta_runner/__init__.py). A conflict hunk
(`<<<<<<<` ... `=======` ... `>>>>>>>`, with a `|||||||` base section for diff3 style) is resolvable
when every line of its sides is that file's version line; it is replaced by the side after
`=======`, which is the merged branch (MERGE_HEAD). A file with any other hunk is left unchanged and
printed, and the exit status is 1.

Standard library only: run with `uv run --no-project python`.
"""

import re
import sys

VERSION_LINES = {
    "gradle.properties": re.compile(r"^version=.*$"),
    "artifact/frontend/package.json": re.compile(r'^  "version": ".*",$'),
    "artifact/ta-runner/ta_runner/__init__.py": re.compile(r'^__version__ = ".*"$'),
}

START = "<<<<<<<"
BASE = "|||||||"
MIDDLE = "======="
END = ">>>>>>>"


def version_pattern(path):
    return VERSION_LINES.get(path.replace("\\", "/"))


def marker(line, token):
    text = line.rstrip("\r\n")
    return text == token or text.startswith(token + " ")


def resolve_text(text, pattern):
    """The text with every hunk replaced by its merged side, or None when a hunk is not version-only."""
    out = []
    lines = text.splitlines(keepends=True)
    i = 0
    while i < len(lines):
        if not marker(lines[i], START):
            out.append(lines[i])
            i += 1
            continue
        ours, base, theirs = [], [], []
        section = ours
        i += 1
        while i < len(lines) and not marker(lines[i], END):
            if marker(lines[i], BASE) and section is ours:
                section = base
            elif marker(lines[i], MIDDLE):
                section = theirs
            else:
                section.append(lines[i])
            i += 1
        if i >= len(lines):
            return None
        i += 1
        if not all(pattern.match(line.rstrip("\r\n")) for line in ours + base + theirs):
            return None
        out.extend(theirs)
    return "".join(out)


def resolve(paths):
    left = []
    for path in paths:
        pattern = version_pattern(path)
        if pattern is None:
            sys.exit(f"merge_versions.py: {path} is not a version file")
        with open(path, newline="") as handle:
            text = handle.read()
        resolved = resolve_text(text, pattern)
        if resolved is None:
            left.append(path)
            continue
        with open(path, "w", newline="") as handle:
            handle.write(resolved)
    if left:
        print("\n".join(left))
    return 1 if left else 0


def main(argv):
    if len(argv) < 3:
        sys.exit(__doc__)
    if argv[1] == "resolve":
        return resolve(argv[2:])
    if argv[1] == "is-version-file":
        return 0 if version_pattern(argv[2]) else 1
    sys.exit(f"unknown command: {argv[1]}")


if __name__ == "__main__":
    sys.exit(main(sys.argv))
