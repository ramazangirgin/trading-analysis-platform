"""Tests of doc_links.py on fixtures in a temporary folder, and on the repository's own docs.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "doc_links.py"


def run(*files):
    result = subprocess.run(
        [sys.executable, str(SCRIPT), *map(str, files)], capture_output=True, text=True, check=False
    )
    return result.returncode, result.stdout.splitlines()


class DocLinksTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name).resolve()
        self.addCleanup(self.tmp.cleanup)

    def write(self, name, text):
        path = self.dir / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_good_relative_link_and_image(self):
        self.write("img/a.png", "x")
        self.write("other.md", "# Other\n")
        doc = self.write("a.md", "[o](other.md) ![i](img/a.png)\n")
        self.assertEqual((0, []), run(doc))

    def test_missing_file(self):
        doc = self.write("a.md", "text\n[o](nope.md)\n")
        code, lines = run(doc)
        self.assertEqual(1, code)
        self.assertEqual([f"{doc}:2: nope.md: file not found"], lines)

    def test_missing_image(self):
        doc = self.write("a.md", "![i](nope.png)\n")
        self.assertEqual(1, run(doc)[0])

    def test_good_and_missing_anchor(self):
        self.write("other.md", "# Other\n\n## Part two\n")
        doc = self.write("a.md", "[g](other.md#part-two)\n[b](other.md#part-three)\n")
        code, lines = run(doc)
        self.assertEqual(1, code)
        self.assertEqual([f"{doc}:2: other.md#part-three: no such heading"], lines)

    def test_bare_anchor_refers_to_own_file(self):
        doc = self.write("a.md", "# Top\n[g](#top) [b](#bottom)\n")
        code, lines = run(doc)
        self.assertEqual(1, code)
        self.assertEqual([f"{doc}:2: #bottom: no such heading"], lines)

    def test_heading_with_punctuation_and_backticks(self):
        self.write("other.md", "## 4. Add an API key (`.env`, step_1)!\n")
        doc = self.write("a.md", "[g](other.md#4-add-an-api-key-env-step_1)\n")
        self.assertEqual((0, []), run(doc))

    def test_repeated_heading_is_numbered(self):
        self.write("other.md", "# Same\n\n# Same\n\n# Same\n")
        doc = self.write("a.md", "[a](other.md#same) [b](other.md#same-1) [c](other.md#same-2) [d](other.md#same-3)\n")
        code, lines = run(doc)
        self.assertEqual(1, code)
        self.assertEqual([f"{doc}:1: other.md#same-3: no such heading"], lines)

    def test_heading_inside_code_fence_is_no_anchor(self):
        self.write("other.md", "# Real\n\n```sh\n# Comment\n```\n")
        doc = self.write("a.md", "[g](other.md#real) [b](other.md#comment)\n")
        code, lines = run(doc)
        self.assertEqual(1, code)
        self.assertEqual([f"{doc}:1: other.md#comment: no such heading"], lines)

    def test_link_inside_code_fence_or_span_is_ignored(self):
        doc = self.write("a.md", "```\n[x](nope.md)\n```\n`[y](nope.md)`\n")
        self.assertEqual((0, []), run(doc))

    def test_external_links_are_skipped(self):
        doc = self.write("a.md", "[h](https://example.invalid/x#y) [m](mailto:a@b.c)\n")
        self.assertEqual((0, []), run(doc))

    def test_repository_docs_have_no_broken_link(self):
        code, lines = run()
        self.assertEqual((0, []), (code, lines))


if __name__ == "__main__":
    unittest.main()
