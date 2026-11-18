#!/usr/bin/env python3
"""Text-literal rule (PRD NF-3, Design §13): no user-visible text hardcoded in Kotlin UI code.

Any string literal that contains a letter, inside a `ui` package, is an error. UI text must come
from `res/values/strings.xml` through `stringResource(...)`.

Escape hatch, for literals that are not text (a route id, a test tag): put `// text-ok: <reason>`
on the same line. The reason is required.

Usage: check_text_literals.py [DIR ...]   (default: app/src/main/java)
Exit code 0 = clean, 1 = violations, 2 = bad usage.
"""
import re
import sys
from pathlib import Path

LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
ESCAPE = re.compile(r"//\s*text-ok:\s*\S")
LETTER = re.compile(r"[^\W\d_]")
FORMAT = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]")


def strip_block_comments(source: str) -> str:
    """Blank out /* ... */ and KDoc while keeping line numbers."""
    return re.sub(r"/\*.*?\*/", lambda m: re.sub(r"[^\n]", " ", m.group(0)), source, flags=re.S)


def code_part(line: str) -> str:
    """Drop a trailing // comment, ignoring // inside a string."""
    in_str = False
    i = 0
    while i < len(line):
        c = line[i]
        if c == "\\" and in_str:
            i += 2
            continue
        if c == '"':
            in_str = not in_str
        elif c == "/" and not in_str and line[i : i + 2] == "//":
            return line[:i]
        i += 1
    return line


def violations_in(path: Path):
    text = strip_block_comments(path.read_text(encoding="utf-8"))
    for number, raw in enumerate(text.splitlines(), start=1):
        if ESCAPE.search(raw):
            continue
        line = code_part(raw)
        if line.lstrip().startswith(("import ", "package ")):
            continue
        for match in LITERAL.finditer(line):
            if LETTER.search(FORMAT.sub("", match.group(1))):
                yield number, match.group(0)


def is_ui_file(path: Path) -> bool:
    return path.suffix == ".kt" and "ui" in path.parts[:-1]


def main(argv):
    roots = [Path(a) for a in argv] or [Path("app/src/main/java")]
    bad = 0
    for root in roots:
        if not root.exists():
            print(f"not found: {root}", file=sys.stderr)
            return 2
        for path in sorted(root.rglob("*.kt")):
            if not is_ui_file(path):
                continue
            for number, literal in violations_in(path):
                print(f"{path}:{number}: hardcoded UI text {literal}; use a string resource (NF-3)")
                bad += 1
    if bad:
        print(f"{bad} violation(s)", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
