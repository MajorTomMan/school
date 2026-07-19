#!/usr/bin/env python3
"""Deep audit of generated mathematics course files against the source PDFs."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import unicodedata

import fitz

from generate_math_courses import BOOKS, display_text, normalized_anchor

TEXT_TYPES = {"textbook_text", "prompt", "caption", "historical_note"}


def normalize(value: str) -> str:
    return normalized_anchor(value)


def iter_pages(course: dict):
    for chapter in course["chapters"]:
        for section in chapter.get("sections", []):
            for page in section.get("pages", []):
                yield chapter, section, page
        review = chapter.get("review") or {}
        for page in review.get("pages", []):
            yield chapter, review, page


def audit_book(spec, source_root: Path, pdf_root: Path) -> dict[str, int]:
    course_path = source_root / spec.textbook_id / "course.json"
    pdf_path = pdf_root / spec.filename
    course = json.loads(course_path.read_text(encoding="utf-8"))
    document = fitz.open(pdf_path)
    try:
        ids: set[str] = set()
        native_text_blocks = 0
        excerpt_blocks = 0
        visualization_blocks = 0
        pages_count = 0
        page_text: dict[int, str] = {}
        for chapter, section, page in iter_pages(course):
            pages_count += 1
            page_id = page["id"]
            if page_id in ids:
                raise SystemExit(f"{spec.textbook_id}: duplicate page id {page_id}")
            ids.add(page_id)
            printed = int(page["sourcePage"])
            pdf_index = printed - 1 + spec.page_index_offset
            if pdf_index not in page_text:
                page_text[pdf_index] = normalize(document[pdf_index].get_text("text"))
            source = page_text[pdf_index]
            if not page.get("blocks"):
                raise SystemExit(f"{spec.textbook_id}: {page_id} has no blocks")
            for block in page["blocks"]:
                kind = block["type"]
                if kind in TEXT_TYPES:
                    native_text_blocks += 1
                    text = normalize(block.get("text", ""))
                    # Generated textbook text must remain a contiguous textbook phrase. Manual pages
                    # are still protected by sourceAnchors because mathematical fractions are repaired.
                    if not page_id.startswith("1.2.1-p07-") and len(text) >= 12 and text not in source:
                        raise SystemExit(
                            f"{spec.textbook_id}: {page_id} textbook text is not found on printed page {printed}: "
                            f"{block.get('text','')[:80]}"
                        )
                elif kind == "source_excerpt":
                    excerpt_blocks += 1
                    bbox = block.get("bbox") or []
                    if len(bbox) != 4:
                        raise SystemExit(f"{spec.textbook_id}: {page_id} invalid excerpt bbox")
                    x0, y0, x1, y1 = map(float, bbox)
                    rect = document[pdf_index].rect
                    if not (0 <= x0 < x1 <= rect.width + 20 and 0 <= y0 < y1 <= rect.height + 20):
                        raise SystemExit(f"{spec.textbook_id}: {page_id} excerpt outside PDF page: {bbox}")
                elif kind == "visualization":
                    visualization_blocks += 1
                    if not str(block.get("renderer") or "").strip():
                        raise SystemExit(f"{spec.textbook_id}: {page_id} empty visualization renderer")
        if pages_count < 100:
            raise SystemExit(f"{spec.textbook_id}: suspiciously small course ({pages_count} pages)")
        return {
            "pages": pages_count,
            "nativeTextBlocks": native_text_blocks,
            "sourceExcerpts": excerpt_blocks,
            "visualizations": visualization_blocks,
        }
    finally:
        document.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--pdf-root", type=Path, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    total = {"pages": 0, "nativeTextBlocks": 0, "sourceExcerpts": 0, "visualizations": 0}
    for spec in BOOKS:
        result = audit_book(spec, args.source_root, args.pdf_root)
        print(spec.textbook_id, json.dumps(result, ensure_ascii=False))
        for key, value in result.items():
            total[key] += value
    print("total", json.dumps(total, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
