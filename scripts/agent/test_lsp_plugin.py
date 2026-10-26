"""Tests of the repository's LSP plugin (scripts/agent/lsp), which the agents load with AGENT_LSP=1.

    uv run --no-project python -m unittest discover -s scripts/agent -p 'test_*.py'   (mise run agent:test)
"""

import json
import subprocess
import unittest
from pathlib import Path

PLUGIN = Path(__file__).resolve().parent / "lsp"


def servers():
    return json.loads((PLUGIN / ".lsp.json").read_text())


class LspPluginTest(unittest.TestCase):
    def test_every_server_starts_an_existing_wrapper_with_bash(self):
        for name, server in servers().items():
            with self.subTest(server=name):
                self.assertEqual("bash", server["command"])
                wrapper = server["args"][0]
                self.assertTrue(wrapper.startswith("${CLAUDE_PLUGIN_ROOT}/"), wrapper)
                self.assertTrue((PLUGIN / wrapper.removeprefix("${CLAUDE_PLUGIN_ROOT}/")).is_file())

    def test_extensions_cover_the_repository_languages(self):
        extensions = {ext for server in servers().values() for ext in server["extensionToLanguage"]}
        self.assertTrue({".java", ".ts", ".vue", ".py"} <= extensions)

    def test_manifest_has_name_and_version(self):
        manifest = json.loads((PLUGIN / ".claude-plugin" / "plugin.json").read_text())
        self.assertTrue(manifest["name"])
        self.assertTrue(manifest["version"])

    def test_wrappers_are_valid_bash(self):
        for wrapper in sorted(PLUGIN.glob("*.sh")):
            with self.subTest(wrapper=wrapper.name):
                result = subprocess.run(["bash", "-n", str(wrapper)], capture_output=True, text=True, check=False)
                self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()
