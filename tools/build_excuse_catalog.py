#!/usr/bin/env python3
"""Compile the reviewed editorial Markdown sources into the production excuse catalog.

The script uses only the Python standard library. Run from the repository root:

    python tools/build_excuse_catalog.py
    python tools/build_excuse_catalog.py --check

The default command writes src/main/resources/excuses/catalog.json. --check verifies that
this committed file is byte-for-byte identical to the reviewed editorial sources.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / "content" / "excuses"
OUTPUT = ROOT / "src" / "main" / "resources" / "excuses" / "catalog.json"
CATALOG_VERSION = "2026.08.04.1"
EXPECTED_TEMPLATE_COUNT = 564

SOURCE_FILES = (
    CONTENT / "drafts" / "general.md",
    CONTENT / "drafts" / "very-late-submission.md",
    CONTENT / "drafts" / "gridwords-last-attempt.md",
    CONTENT / "drafts" / "gridwords-very-slow.md",
    CONTENT / "drafts" / "quadwords-very-slow.md",
    CONTENT / "drafts" / "quadwords-single-board-collapse.md",
    CONTENT / "drafts" / "current-daily-outlier.md",
)
TOPIC_MAP_FILE = CONTENT / "review" / "general-topic-map.md"

ENTRY_PATTERN = re.compile(r"^- `(?P<id>[a-z0-9._-]+)` – (?P<text>.+)$")
TOPIC_PATTERN = re.compile(r"^\| `(?P<id>general\.[a-z0-9.-]+)` \| `(?P<topic>[A-Z_]+)` \|$")
PLACEHOLDER_PATTERN = re.compile(r"\{(?P<name>[A-Za-z][A-Za-z0-9]*)}")
SPACE_PATTERN = re.compile(r"\s+")

STYLES = {
    "technical": "TECHNICAL",
    "tactical": "TACTICAL",
    "bureaucratic": "BUREAUCRATIC",
    "dramatic": "DRAMATIC",
    "cosmic": "COSMIC",
    "northern-german": "NORTHERN_GERMAN",
    "sporting": "SPORTING",
    "legal": "LEGAL",
}

ALLOWED_TOPICS = {
    "GENERAL",
    "TECHNICAL_FAILURE",
    "LONG_TERM_PLAN",
    "RESPONSIBILITY",
    "GRID_CONFLICT",
    "LATE_SUBMISSION",
    "SLOW_RESULT",
    "NOT_SOLVED",
    "LAST_ATTEMPT",
    "SINGLE_BOARD_BLAME",
    "DAILY_OUTLIER",
}

ALLOWED_CONDITIONS = {
    "NOT_SOLVED",
    "VERY_LATE_SUBMISSION",
    "GRIDWORDS_LAST_ATTEMPT",
    "GRIDWORDS_VERY_SLOW",
    "QUADWORDS_VERY_SLOW",
    "QUADWORDS_SINGLE_BOARD_COLLAPSE",
    "CLEAR_CURRENT_DAILY_OUTLIER",
    "FOUR_BOARDS_PRESENT",
    "BOARDLESS_SUBMISSION",
    "UNIQUE_WORST_BOARD",
    "SIGNIFICANT_WORST_BOARD_GAP",
    "THREE_BOARDS_SOLVED_ONE_UNSOLVED",
    "ALL_BOARDS_SOLVED",
    "BOARDS_SIMILAR",
    "TOP_LEFT_WORST",
    "TOP_RIGHT_WORST",
    "BOTTOM_LEFT_WORST",
    "BOTTOM_RIGHT_WORST",
}

ALLOWED_PLACEHOLDERS = {"game", "score", "duration", "worstBoard"}

# These replacements are intentionally separate from the original draft to retain review history.
TEXT_OVERRIDES = {
    "general.tactical.09": "Ich habe das Grid heute gezielt zu einer falschen Analyse meiner Muster verleitet.",
    "general.dramatic.15": "Die Tragödie begann in dem Moment, als das Grid vorgab, sie ließe sich noch verhindern.",
    "general.cosmic.17": "Die kosmische Buchhaltung hat dieses Ergebnis offenbar in der falschen Dimension verbucht.",
    "general.sporting.15": "Die heutige Leistung war offenbar Teil einer unangekündigten Regenerationseinheit.",
    "gridwords-very-slow.sporting.04": (
        "Wir haben uns Zeit genommen, weil ein überhasteter Angriff nur noch mehr Ballverluste im Kopf produziert hätte."
    ),
}


@dataclass(frozen=True)
class DraftEntry:
    template_id: str
    text: str
    source: Path
    line_number: int


@dataclass(frozen=True)
class Metadata:
    style: str
    games: tuple[str, ...]
    topic: str
    specificity: int
    requires_all: tuple[str, ...]


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail if the committed catalog differs")
    parser.add_argument("--stdout", action="store_true", help="print the generated catalog instead of writing")
    return parser.parse_args()


def read_entries(path: Path) -> list[DraftEntry]:
    if not path.is_file():
        raise ValueError(f"missing editorial source: {path.relative_to(ROOT)}")
    entries: list[DraftEntry] = []
    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = ENTRY_PATTERN.match(raw_line)
        if match:
            template_id = match.group("id")
            text = TEXT_OVERRIDES.get(template_id, match.group("text").strip())
            entries.append(DraftEntry(template_id, text, path, line_number))
    if not entries:
        raise ValueError(f"no excuse entries found in {path.relative_to(ROOT)}")
    return entries


def read_general_topics() -> dict[str, str]:
    if not TOPIC_MAP_FILE.is_file():
        raise ValueError(f"missing topic map: {TOPIC_MAP_FILE.relative_to(ROOT)}")
    topics: dict[str, str] = {}
    for line_number, raw_line in enumerate(TOPIC_MAP_FILE.read_text(encoding="utf-8").splitlines(), start=1):
        match = TOPIC_PATTERN.match(raw_line)
        if not match:
            continue
        template_id = match.group("id")
        topic = match.group("topic")
        if topic not in ALLOWED_TOPICS:
            raise ValueError(f"unknown topic {topic} at {TOPIC_MAP_FILE.relative_to(ROOT)}:{line_number}")
        if template_id in topics:
            raise ValueError(f"duplicate topic assignment for {template_id}")
        topics[template_id] = topic
    if len(topics) != 144:
        raise ValueError(f"expected 144 general topic assignments, found {len(topics)}")
    return topics


def style_from_id(template_id: str) -> str:
    matches = [style for segment, style in STYLES.items() if f".{segment}." in f".{template_id}."]
    if len(matches) != 1:
        raise ValueError(f"cannot determine exactly one style from id {template_id}")
    return matches[0]


def metadata_for(template_id: str, general_topics: dict[str, str]) -> Metadata:
    style = style_from_id(template_id)
    if template_id.startswith("general."):
        topic = general_topics.get(template_id)
        if topic is None:
            raise ValueError(f"missing topic assignment for {template_id}")
        return Metadata(style, ("GRIDWORDS", "QUADWORDS"), topic, 0, ())
    if template_id.startswith("not-solved."):
        return Metadata(style, ("GRIDWORDS", "QUADWORDS"), "NOT_SOLVED", 20, ("NOT_SOLVED",))
    if template_id.startswith("very-late-submission."):
        return Metadata(
            style,
            ("GRIDWORDS", "QUADWORDS"),
            "LATE_SUBMISSION",
            20,
            ("VERY_LATE_SUBMISSION",),
        )
    if template_id.startswith("gridwords-last-attempt."):
        return Metadata(style, ("GRIDWORDS",), "LAST_ATTEMPT", 20, ("GRIDWORDS_LAST_ATTEMPT",))
    if template_id.startswith("gridwords-very-slow."):
        return Metadata(style, ("GRIDWORDS",), "SLOW_RESULT", 20, ("GRIDWORDS_VERY_SLOW",))
    if template_id.startswith("quadwords-very-slow."):
        return Metadata(style, ("QUADWORDS",), "SLOW_RESULT", 20, ("QUADWORDS_VERY_SLOW",))
    if template_id.startswith("clear-current-daily-outlier."):
        return Metadata(
            style,
            ("GRIDWORDS", "QUADWORDS"),
            "DAILY_OUTLIER",
            20,
            ("CLEAR_CURRENT_DAILY_OUTLIER",),
        )
    if template_id.startswith("quadwords-single-board-collapse."):
        base = (
            "QUADWORDS_SINGLE_BOARD_COLLAPSE",
            "FOUR_BOARDS_PRESENT",
            "UNIQUE_WORST_BOARD",
        )
        if ".general." in template_id:
            return Metadata(style, ("QUADWORDS",), "SINGLE_BOARD_BLAME", 30, base)
        if ".unsolved." in template_id:
            return Metadata(
                style,
                ("QUADWORDS",),
                "SINGLE_BOARD_BLAME",
                40,
                base + ("THREE_BOARDS_SOLVED_ONE_UNSOLVED",),
            )
        if ".solved-outlier." in template_id:
            return Metadata(
                style,
                ("QUADWORDS",),
                "SINGLE_BOARD_BLAME",
                40,
                base + ("ALL_BOARDS_SOLVED", "SIGNIFICANT_WORST_BOARD_GAP"),
            )
        raise ValueError(f"unknown board-collapse variant in id {template_id}")
    raise ValueError(f"unknown template family for id {template_id}")


def normalized_text(text: str) -> str:
    normalized = text.casefold().replace("„", '"').replace("“", '"').replace("’", "'")
    normalized = re.sub(r"[^a-z0-9äöüß{}]+", " ", normalized)
    return SPACE_PATTERN.sub(" ", normalized).strip()


def build_catalog() -> dict[str, object]:
    general_topics = read_general_topics()
    drafts = [entry for source in SOURCE_FILES for entry in read_entries(source)]
    if len(drafts) != EXPECTED_TEMPLATE_COUNT:
        raise ValueError(f"expected {EXPECTED_TEMPLATE_COUNT} templates, found {len(drafts)}")

    templates: list[dict[str, object]] = []
    seen_ids: dict[str, DraftEntry] = {}
    exact_texts: dict[str, DraftEntry] = {}
    normalized_texts: dict[str, DraftEntry] = {}

    for entry in drafts:
        if entry.template_id in seen_ids:
            previous = seen_ids[entry.template_id]
            raise ValueError(
                f"duplicate id {entry.template_id}: {previous.source.name}:{previous.line_number} and "
                f"{entry.source.name}:{entry.line_number}"
            )
        seen_ids[entry.template_id] = entry

        if not entry.text or len(entry.text) > 500:
            raise ValueError(f"invalid text length for {entry.template_id}: {len(entry.text)}")
        if "Raster" in entry.text or "raster" in entry.text:
            raise ValueError(f"forbidden bot terminology in {entry.template_id}: {entry.text}")
        if "@everyone" in entry.text.casefold() or "@here" in entry.text.casefold():
            raise ValueError(f"forbidden mention in {entry.template_id}")

        placeholders = set(PLACEHOLDER_PATTERN.findall(entry.text))
        unknown_placeholders = placeholders - ALLOWED_PLACEHOLDERS
        if unknown_placeholders:
            raise ValueError(f"unknown placeholders for {entry.template_id}: {sorted(unknown_placeholders)}")

        if entry.text in exact_texts:
            previous = exact_texts[entry.text]
            raise ValueError(
                f"exact duplicate text: {entry.template_id} and {previous.template_id}"
            )
        exact_texts[entry.text] = entry

        normalized = normalized_text(entry.text)
        if normalized in normalized_texts:
            previous = normalized_texts[normalized]
            raise ValueError(
                f"normalized duplicate text: {entry.template_id} and {previous.template_id}"
            )
        normalized_texts[normalized] = entry

        metadata = metadata_for(entry.template_id, general_topics)
        if metadata.topic not in ALLOWED_TOPICS:
            raise ValueError(f"unknown topic for {entry.template_id}: {metadata.topic}")
        unknown_conditions = set(metadata.requires_all) - ALLOWED_CONDITIONS
        if unknown_conditions:
            raise ValueError(f"unknown conditions for {entry.template_id}: {sorted(unknown_conditions)}")
        if "worstBoard" in placeholders and "UNIQUE_WORST_BOARD" not in metadata.requires_all:
            raise ValueError(f"{entry.template_id} renders worstBoard without UNIQUE_WORST_BOARD")
        if entry.template_id.startswith("quadwords-single-board-collapse.") and placeholders != {"worstBoard"}:
            raise ValueError(f"board-collapse template must use exactly worstBoard: {entry.template_id}")
        if not entry.template_id.startswith("quadwords-single-board-collapse.") and "worstBoard" in placeholders:
            raise ValueError(f"unexpected worstBoard placeholder in {entry.template_id}")

        templates.append(
            {
                "id": entry.template_id,
                "style": metadata.style,
                "games": list(metadata.games),
                "topic": metadata.topic,
                "specificity": metadata.specificity,
                "weight": 100,
                "requiresAll": list(metadata.requires_all),
                "excludesAny": [],
                "text": entry.text,
                "selectable": True,
            }
        )

    validate_coverage(templates)
    return {"version": CATALOG_VERSION, "templates": templates}


def validate_coverage(templates: Iterable[dict[str, object]]) -> None:
    templates = list(templates)
    by_style = Counter(template["style"] for template in templates)
    general_by_style = Counter(
        template["style"] for template in templates if template["id"].startswith("general.")
    )
    for style in STYLES.values():
        if general_by_style[style] != 18:
            raise ValueError(f"expected 18 general templates for {style}, found {general_by_style[style]}")
        if by_style[style] < 60:
            raise ValueError(f"unexpectedly small full style corpus for {style}: {by_style[style]}")

    expected_families = {
        "general": 144,
        "not-solved": 64,
        "very-late-submission": 58,
        "gridwords-last-attempt": 56,
        "gridwords-very-slow": 56,
        "quadwords-very-slow": 56,
        "quadwords-single-board-collapse": 72,
        "clear-current-daily-outlier": 58,
    }
    for prefix, expected in expected_families.items():
        actual = sum(1 for template in templates if template["id"].startswith(prefix + "."))
        if actual != expected:
            raise ValueError(f"expected {expected} templates for {prefix}, found {actual}")

    specific_reasons = {
        "NOT_SOLVED",
        "VERY_LATE_SUBMISSION",
        "GRIDWORDS_LAST_ATTEMPT",
        "GRIDWORDS_VERY_SLOW",
        "QUADWORDS_VERY_SLOW",
        "QUADWORDS_SINGLE_BOARD_COLLAPSE",
        "CLEAR_CURRENT_DAILY_OUTLIER",
    }
    reason_style_counts: dict[str, Counter[str]] = defaultdict(Counter)
    for template in templates:
        for condition in template["requiresAll"]:
            if condition in specific_reasons:
                reason_style_counts[condition][template["style"]] += 1
    for reason in specific_reasons:
        missing = [style for style in STYLES.values() if reason_style_counts[reason][style] < 6]
        if missing:
            raise ValueError(f"reason {reason} has fewer than six templates for styles: {missing}")


def serialized_catalog(catalog: dict[str, object]) -> str:
    return json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"


def main() -> int:
    args = parse_arguments()
    content = serialized_catalog(build_catalog())
    if args.stdout:
        sys.stdout.write(content)
        return 0
    if args.check:
        if not OUTPUT.is_file():
            print(f"missing generated catalog: {OUTPUT.relative_to(ROOT)}", file=sys.stderr)
            return 1
        current = OUTPUT.read_text(encoding="utf-8")
        if current != content:
            print(
                "committed catalog differs from editorial sources; run "
                "python tools/build_excuse_catalog.py",
                file=sys.stderr,
            )
            return 1
        print(f"catalog is current: {EXPECTED_TEMPLATE_COUNT} templates")
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(content, encoding="utf-8")
    print(f"wrote {OUTPUT.relative_to(ROOT)} with {EXPECTED_TEMPLATE_COUNT} templates")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
