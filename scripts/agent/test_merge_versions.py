"""Tests of merge_versions.py: which conflict hunks are resolved and how.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import os
import tempfile
import unittest

import merge_versions

GRADLE = "gradle.properties"
PACKAGE = "artifact/frontend/package.json"
INIT = "artifact/ta-runner/ta_runner/__init__.py"


def hunk(ours, theirs, base=None):
    middle = f"||||||| base\n{base}\n" if base else ""
    return f"<<<<<<< HEAD\n{ours}\n{middle}=======\n{theirs}\n>>>>>>> origin/main\n"


class ResolveTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def write(self, name, text):
        path = os.path.join(self.dir.name, name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as handle:
            handle.write(text)
        return path

    def read(self, path):
        with open(path) as handle:
            return handle.read()

    def test_gradle_properties_takes_main_side(self):
        path = self.write(GRADLE, "group=x\n" + hunk("version=1.3.0", "version=1.2.1") + "other=1\n")
        self.assertEqual(merge_versions.resolve([path]), 0)
        self.assertEqual(self.read(path), "group=x\nversion=1.2.1\nother=1\n")

    def test_package_json_takes_main_side(self):
        ours, theirs = '  "version": "1.3.0",', '  "version": "1.2.1",'
        path = self.write(PACKAGE, '{\n  "name": "a",\n' + hunk(ours, theirs) + '  "private": true\n}\n')
        self.assertEqual(merge_versions.resolve([path]), 0)
        self.assertEqual(self.read(path), '{\n  "name": "a",\n  "version": "1.2.1",\n  "private": true\n}\n')

    def test_init_py_diff3_style(self):
        path = self.write(
            INIT, hunk('__version__ = "1.3.0"', '__version__ = "1.2.1"', base='__version__ = "1.2.0"')
        )
        self.assertEqual(merge_versions.resolve([path]), 0)
        self.assertEqual(self.read(path), '__version__ = "1.2.1"\n')

    def test_hunk_with_a_dependency_line_leaves_the_file_unchanged(self):
        text = '{\n' + hunk('  "version": "1.3.0",\n  "dep": "1"', '  "version": "1.2.1",') + "}\n"
        path = self.write(PACKAGE, text)
        self.assertEqual(merge_versions.resolve([path]), 1)
        self.assertEqual(self.read(path), text)

    def test_one_resolvable_and_one_not_leaves_the_file_whole(self):
        text = hunk("version=1.3.0", "version=1.2.1") + "mid=1\n" + hunk("a=1", "a=2")
        path = self.write(GRADLE, text)
        self.assertEqual(merge_versions.resolve([path]), 1)
        self.assertEqual(self.read(path), text)

    def test_unterminated_hunk_is_left(self):
        text = "<<<<<<< HEAD\nversion=1\n=======\nversion=2\n"
        path = self.write(GRADLE, text)
        self.assertEqual(merge_versions.resolve([path]), 1)
        self.assertEqual(self.read(path), text)

    def test_other_file_is_refused(self):
        path = self.write("README.md", hunk("a", "b"))
        with self.assertRaises(SystemExit):
            merge_versions.resolve([path])

    def test_is_version_file(self):
        self.assertEqual(merge_versions.main(["x", "is-version-file", GRADLE]), 0)
        self.assertEqual(merge_versions.main(["x", "is-version-file", INIT]), 0)
        self.assertEqual(merge_versions.main(["x", "is-version-file", "README.md"]), 1)


if __name__ == "__main__":
    unittest.main()
