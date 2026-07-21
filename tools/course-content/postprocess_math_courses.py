#!/usr/bin/env python3
"""Finalize generated mathematics courses.

Generated PDF crop placeholders are converted to native textbook text and manually reviewed
sections replace generated sections. The output is the exact business-only course package consumed
by the APK; no authoring metadata survives this step.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from course_package import validate_course

MANUAL_ROOT = Path(__file__).resolve().parent / "manual"
GENERIC_EXCERPT_TEXTS = ("教材原式", "教材原图", "教材图示", "教材示例", "教材例")


def iter_pages(course: dict[str, Any]):
    for chapter in course.get("chapters", []):
        for section in chapter.get("sections", []):
            yield section, section.get("pages", [])
        if chapter.get("review"):
            yield chapter["review"], chapter["review"].get("pages", [])


def meaningful_fallback(value: str) -> bool:
    text = value.strip()
    return bool(text) and not (len(text) < 32 and any(text.startswith(prefix) for prefix in GENERIC_EXCERPT_TEXTS))


def convert_excerpts(course: dict[str, Any]) -> int:
    converted = 0
    for _, pages in iter_pages(course):
        for page in pages:
            output: list[dict[str, Any]] = []
            for block in page.get("blocks", []):
                if block.get("type") != "source_excerpt":
                    output.append(block)
                    continue
                fallback = str(block.get("fallbackText") or "").strip()
                if meaningful_fallback(fallback):
                    output.append({"type": "text", "style": "textbook", "text": fallback})
                converted += 1
            page["blocks"] = output
    return converted


def apply_manual_sections(course: dict[str, Any]) -> int:
    textbook_id = str(course.get("textbook", {}).get("id") or "").strip()
    directory = MANUAL_ROOT / textbook_id
    if not directory.is_dir():
        return 0

    targets = {
        str(section.get("id")): (chapter, index)
        for chapter in course.get("chapters", [])
        for index, section in enumerate(chapter.get("sections", []))
    }
    applied = 0
    for path in sorted(directory.glob("*.json")):
        override = json.loads(path.read_text(encoding="utf-8"))
        section_id = str(override.get("id") or "").strip()
        target = targets.get(section_id)
        if target is None:
            raise SystemExit(f"{path}: section {section_id!r} was not found in generated course")
        chapter, index = target
        chapter["sections"][index] = override
        applied += 1
    return applied


def process_course(path: Path) -> tuple[int, int]:
    course = json.loads(path.read_text(encoding="utf-8"))
    converted = convert_excerpts(course)
    manual_sections = apply_manual_sections(course)
    remaining = sum(
        block.get("type") == "source_excerpt"
        for _, pages in iter_pages(course)
        for page in pages
        for block in page.get("blocks", [])
    )
    if remaining:
        raise SystemExit(f"{path}: {remaining} source_excerpt blocks remain")
    validate_course(course)
    path.write_text(json.dumps(course, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return converted, manual_sections


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    args = parser.parse_args()

    total_converted = 0
    total_manual = 0
    for path in sorted(args.source_root.glob("pep-math-*/course.json")):
        converted, manual_sections = process_course(path)
        total_converted += converted
        total_manual += manual_sections
        print(f"{path.parent.name}: converted {converted} excerpts, applied {manual_sections} reviewed sections")
    print(f"converted total: {total_converted}; manual sections total: {total_manual}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
