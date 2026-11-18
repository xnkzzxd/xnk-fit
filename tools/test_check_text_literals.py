"""Tests that the text-literal rule works. Run: python3 -m unittest discover -s tools"""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("check_text_literals.py")


def run(files: dict[str, str]):
    with tempfile.TemporaryDirectory() as tmp:
        for rel, body in files.items():
            path = Path(tmp) / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(body, encoding="utf-8")
        return subprocess.run(
            [sys.executable, str(SCRIPT), tmp], capture_output=True, text=True
        )


class TextLiteralRule(unittest.TestCase):
    def test_flags_hardcoded_text_in_ui(self):
        result = run({"app/ui/today/A.kt": 'Text("Hari ini")\n'})
        self.assertEqual(result.returncode, 1)
        self.assertIn("A.kt:1", result.stdout)

    def test_flags_content_description(self):
        result = run({"app/ui/A.kt": 'Icon(x, contentDescription = "Tambah")\n'})
        self.assertEqual(result.returncode, 1)

    def test_resource_text_passes(self):
        result = run({"app/ui/A.kt": "Text(stringResource(R.string.nav_today))\n"})
        self.assertEqual(result.returncode, 0, result.stdout)

    def test_non_ui_package_is_ignored(self):
        result = run({"app/domain/rules/A.kt": 'const val NAME = "R_FLOOR_1"\n'})
        self.assertEqual(result.returncode, 0, result.stdout)

    def test_comments_imports_and_symbols_are_ignored(self):
        body = (
            "package app.ui\n"
            'import a.b.c // "Not text"\n'
            '// Text("comment")\n'
            '/* Text("block") */\n'
            'val sep = ", "\n'
            'val f = "%d"\n'
        )
        result = run({"app/ui/A.kt": body})
        self.assertEqual(result.returncode, 0, result.stdout)

    def test_escape_needs_a_reason(self):
        ok = run({"app/ui/A.kt": 'val tag = "today" // text-ok: test tag\n'})
        self.assertEqual(ok.returncode, 0, ok.stdout)
        bare = run({"app/ui/A.kt": 'val tag = "today" // text-ok:\n'})
        self.assertEqual(bare.returncode, 1)

    def test_real_sources_are_clean(self):
        root = Path(__file__).resolve().parent.parent / "app/src/main/java"
        result = subprocess.run(
            [sys.executable, str(SCRIPT), str(root)], capture_output=True, text=True
        )
        self.assertEqual(result.returncode, 0, result.stdout)


if __name__ == "__main__":
    unittest.main()
