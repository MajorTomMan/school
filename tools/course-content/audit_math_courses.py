#!/usr/bin/env python3
"""Audit published mathematics course business data against source PDFs."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import fitz

from course_package import validate_course
from generate_math_courses import BOOKS, normalized_anchor


def iter_pages(course: dict):
    for chapter in course["chapters"]:
        for section in chapter.get("sections", []):
            for page in section["pages"]:
                yield section, page
        if chapter.get("review"):
            for page in chapter["review"]["pages"]:
                yield chapter["review"], page


def manual_section_ids(textbook_id: str) -> set[str]:
    root = Path(__file__).resolve().parent / "manual" / textbook_id
    result = set()
    for path in root.glob("*.json") if root.is_dir() else []:
        result.add(str(json.loads(path.read_text(encoding="utf-8"))["id"]))
    return result


def audit_book(spec, source_root: Path, pdf_root: Path) -> dict[str, int]:
    course_path = source_root / spec.textbook_id / "course.json"
    pdf_path = pdf_root / spec.filename
    course = json.loads(course_path.read_text(encoding="utf-8"))
    validate_course(course)
    manual_ids = manual_section_ids(spec.textbook_id)
    document = fitz.open(pdf_path)
    try:
        pages_count = text_blocks = scenes = 0
        page_text: dict[int, str] = {}
        for section, page in iter_pages(course):
            pages_count += 1
            start = page["sourcePage"]
            end = page.get("sourcePageEnd", start)
            source = ""
            for printed in range(start, end + 1):
                pdf_index = printed - 1 + spec.page_index_offset
                source += page_text.setdefault(pdf_index, normalized_anchor(document[pdf_index].get_text("text")))
            if str(page["title"]).startswith("教材第") and section.get("id") in manual_ids:
                raise SystemExit(f"{spec.textbook_id}: reviewed page {page['id']} still uses a physical-page title")
            for block in page["blocks"]:
                if block["type"] == "text":
                    text_blocks += 1
                    if block["style"] == "textbook":
                        text = normalized_anchor(block["text"])
                        if len(text) >= 12 and text not in source:
                            raise SystemExit(
                                f"{spec.textbook_id}: {page['id']} textbook wording is not found on pages {start}..{end}: "
                                f"{block['text'][:80]}"
                            )
                elif block["type"] == "scene":
                    scenes += 1
        if pages_count < 100:
            raise SystemExit(f"{spec.textbook_id}: suspiciously small course ({pages_count} pages)")
        return {
            "pages": pages_count,
            "textBlocks": text_blocks,
            "scenes": scenes,
            "reviewedSections": len(manual_ids),
        }
    finally:
        document.close()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--pdf-root", type=Path, required=True)
    args = parser.parse_args()
    total = {"pages": 0, "textBlocks": 0, "scenes": 0, "reviewedSections": 0}
    for spec in BOOKS:
        result = audit_book(spec, args.source_root, args.pdf_root)
        print(spec.textbook_id, json.dumps(result, ensure_ascii=False))
        for key, value in result.items():
            total[key] += value
    print("total", json.dumps(total, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
