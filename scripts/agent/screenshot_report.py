"""Reports the screenshots written since a stamp file, so an agent never has to open a PNG.

    screenshot_report.py <stamp file> [<dir>]

One line per PNG in <dir> (default docs/screenshots) modified after the stamp file:
"<name>.png  <width>x<height>  <KB> KB  new|changed|unchanged", then a summary line. Exits 1 when a
written image is suspicious: under 100 pixels wide or high, or under 0.01 bytes per pixel (a
near-uniform, likely blank page).
"""

import struct
import subprocess
import sys
from pathlib import Path

MIN_SIDE = 100
MIN_BYTES_PER_PIXEL = 0.01
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def dimensions(path):
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != PNG_SIGNATURE or head[12:16] != b"IHDR":
        raise ValueError(f"{path.name} is not a PNG")
    return struct.unpack(">II", head[16:24])


def git_state(path):
    result = subprocess.run(
        ["git", "status", "--porcelain", "--", path.name],
        cwd=path.parent,
        capture_output=True,
        text=True,
    )
    code = result.stdout[:2]
    if result.returncode != 0 or code in ("??", "A ", "AM"):
        return "new"
    return "changed" if code.strip() else "unchanged"


def problem(width, height, size):
    if width < MIN_SIDE or height < MIN_SIDE:
        return f"only {width}x{height} pixels"
    if size / (width * height) < MIN_BYTES_PER_PIXEL:
        return f"{size} bytes for {width}x{height} pixels: probably a blank page"
    return None


def main(argv):
    if len(argv) not in (2, 3):
        print(__doc__, file=sys.stderr)
        return 2
    stamp = Path(argv[1]).stat().st_mtime
    directory = Path(argv[2]) if len(argv) == 3 else Path(__file__).resolve().parents[2] / "docs" / "screenshots"
    counts = {"new": 0, "changed": 0, "unchanged": 0}
    problems = []
    for path in sorted(directory.glob("*.png")):
        if path.stat().st_mtime <= stamp:
            continue
        width, height = dimensions(path)
        size = path.stat().st_size
        state = git_state(path)
        counts[state] += 1
        print(f"{path.name}  {width}x{height}  {size / 1024:.0f} KB  {state}")
        reason = problem(width, height, size)
        if reason:
            problems.append(f"{path.name}: {reason}")
    total = sum(counts.values())
    print(f"{total} screenshots written: {counts['new']} new, {counts['changed']} changed, {counts['unchanged']} unchanged")
    for line in problems:
        print(f"suspicious: {line}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
