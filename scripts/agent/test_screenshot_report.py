"""Tests of screenshot_report.py in a temporary Git repository.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import os
import random
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib
from pathlib import Path

REPORT = Path(__file__).resolve().parent / "screenshot_report.py"


def png(width, height, noisy):
    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    rng = random.Random(1)
    rows = b"".join(
        b"\x00" + (rng.randbytes(width * 3) if noisy else b"\xff" * (width * 3)) for _ in range(height)
    )
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


class ScreenshotReportTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.shots = self.root / "docs" / "screenshots"
        self.shots.mkdir(parents=True)
        self.stamp = self.root / "stamp"
        self.stamp.touch()
        os.utime(self.stamp, (1000, 1000))
        self.git("init", "-q")

    def git(self, *args):
        subprocess.run(
            ["git", "-c", "user.name=t", "-c", "user.email=t@t", *args], cwd=self.root, check=True, capture_output=True
        )

    def write(self, name, data, mtime=2000):
        path = self.shots / name
        path.write_bytes(data)
        os.utime(path, (mtime, mtime))

    def report(self):
        return subprocess.run(
            [sys.executable, str(REPORT), str(self.stamp), str(self.shots)], capture_output=True, text=True
        )

    def test_reads_dimensions_and_size_and_exits_0_for_a_normal_image(self):
        self.write("a.png", png(200, 150, noisy=True))
        result = self.report()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertRegex(result.stdout, r"a\.png  200x150  \d+ KB  new")
        self.assertIn("1 screenshots written: 1 new, 0 changed, 0 unchanged", result.stdout)

    def test_new_changed_and_unchanged(self):
        self.write("same.png", png(200, 150, noisy=True), mtime=500)
        self.write("edit.png", png(200, 150, noisy=True), mtime=500)
        self.git("add", ".")
        self.git("commit", "-q", "-m", "x")
        self.write("same.png", (self.shots / "same.png").read_bytes())
        self.write("edit.png", png(220, 150, noisy=True))
        self.write("fresh.png", png(200, 160, noisy=True))
        lines = self.report().stdout.splitlines()
        self.assertTrue(any(l.startswith("edit.png") and l.endswith("changed") for l in lines), lines)
        self.assertTrue(any(l.startswith("same.png") and l.endswith("unchanged") for l in lines), lines)
        self.assertTrue(any(l.startswith("fresh.png") and l.endswith("new") for l in lines), lines)

    def test_a_file_older_than_the_stamp_is_not_listed(self):
        self.write("old.png", png(200, 150, noisy=True), mtime=500)
        result = self.report()
        self.assertNotIn("old.png", result.stdout)
        self.assertIn("0 screenshots written", result.stdout)

    def test_a_uniform_image_fails(self):
        self.write("blank.png", png(1440, 900, noisy=False))
        result = self.report()
        self.assertEqual(1, result.returncode)
        self.assertIn("blank.png", result.stderr)
        self.assertIn("blank", result.stderr)

    def test_a_tiny_image_fails(self):
        self.write("tiny.png", png(50, 50, noisy=True))
        result = self.report()
        self.assertEqual(1, result.returncode)
        self.assertIn("tiny.png: only 50x50 pixels", result.stderr)


if __name__ == "__main__":
    unittest.main()
