#!/usr/bin/env python3
"""Fail when a repository Markdown file contains a missing local link target."""

from __future__ import annotations

import re
import sys
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]
INLINE_LINK = re.compile(r"!?\[[^\]]*\]\((?P<target><[^>]+>|[^)\s]+)(?:\s+['\"][^'\"]*['\"])?\)")
REFERENCE_LINK = re.compile(r"^\s*\[[^\]]+\]:\s*(?P<target><[^>]+>|\S+)", re.MULTILINE)
IGNORED_SCHEMES = ("http://", "https://", "mailto:", "tel:", "data:", "discord:")


def without_fenced_code(text: str) -> str:
    lines: list[str] = []
    fence: str | None = None
    for line in text.splitlines(keepends=True):
        stripped = line.lstrip()
        marker = "```" if stripped.startswith("```") else "~~~" if stripped.startswith("~~~") else None
        if marker:
            fence = None if fence == marker else marker if fence is None else fence
            lines.append("\n")
        elif fence is None:
            lines.append(line)
        else:
            lines.append("\n")
    return "".join(lines)


def local_target(markdown_file: Path, raw_target: str) -> Path | None:
    target = raw_target.strip("<>")
    if not target or target.startswith("#") or target.lower().startswith(IGNORED_SCHEMES):
        return None
    target = unquote(target.split("#", 1)[0].split("?", 1)[0])
    if not target:
        return None
    if target.startswith("/"):
        return ROOT / target.lstrip("/")
    return markdown_file.parent / target


def main() -> int:
    failures: list[str] = []
    files = sorted(path for path in ROOT.rglob("*.md") if ".git" not in path.parts and ".codex-worktrees" not in path.parts)
    checked = 0
    for markdown_file in files:
        try:
            source = markdown_file.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            # A small number of early ADRs predate the repository-wide UTF-8 convention.
            source = markdown_file.read_text(encoding="cp1252")
        text = without_fenced_code(source)
        matches = list(INLINE_LINK.finditer(text)) + list(REFERENCE_LINK.finditer(text))
        for match in matches:
            target = local_target(markdown_file, match.group("target"))
            if target is None:
                continue
            checked += 1
            if not target.exists():
                line = text.count("\n", 0, match.start()) + 1
                failures.append(
                    f"{markdown_file.relative_to(ROOT).as_posix()}:{line}: "
                    f"missing {match.group('target')}"
                )

    if failures:
        print("Broken local Markdown links:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1
    print(f"Checked {checked} local links in {len(files)} Markdown files; all targets exist.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
