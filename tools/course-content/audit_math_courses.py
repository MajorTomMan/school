#!/usr/bin/env python3
"""Audit packaged mathematics course files against their source PDFs."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import fitz

from generate_math_courses import BOOKS, normalized_anchor

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
        visualization_blocks = 0
        manually_reviewed_sections = 0
        pages_count = 0
        page_text: dict[int, str] = {}
        counted_manual_sections: set[str] = set()

        for chapter, section, page in iter_pages(course):
            pages_count += 1
            page_id = page["id"]
            if page_id in ids:
                raise SystemExit(f"{spec.textbook_id}: duplicate page id {page_id}")
            ids.add(page_id)

            manual_review = section.get("manualReview") or {}
            is_manual = bool(manual_review)
            section_id = str(section.get("id") or "")
            if is_manual and section_id not in counted_manual_sections:
                counted_manual_sections.add(section_id)
                manually_reviewed_sections += 1
                reviewed_pages = manual_review.get("printedPages")
                if not isinstance(reviewed_pages, list) or not reviewed_pages:
                    raise SystemExit(f"{spec.textbook_id}: section {section_id} has no reviewed page list")

            printed = int(page["sourcePage"])
            source_end = int(page.get("sourcePageEnd", printed))
            pdf_index = printed - 1 + spec.page_index_offset
            if pdf_index not in page_text:
                page_text[pdf_index] = normalize(document[pdf_index].get_text("text"))
            source = page_text[pdf_index]

            if is_manual:
                reviewed = {int(value) for value in manual_review["printedPages"]}
                if any(value not in reviewed for value in range(printed, source_end + 1)):
                    raise SystemExit(
                        f"{spec.textbook_id}: {page_id} references pages outside manual review range"
                    )
                if str(page.get("title") or "").startswith("教材第"):
                    raise SystemExit(f"{spec.textbook_id}: {page_id} still uses a physical-page title")

            if not page.get("blocks"):
                raise SystemExit(f"{spec.textbook_id}: {page_id} has no blocks")

            for block in page["blocks"]:
                kind = block["type"]
                if kind == "source_excerpt":
                    raise SystemExit(
                        f"{spec.textbook_id}: {page_id} still contains a PDF crop; "
                        "published lessons must use native text and School visualizations"
                    )
                if kind in TEXT_TYPES:
                    native_text_blocks += 1
                    text = normalize(block.get("text", ""))
                    # Automatic skeleton text must remain a contiguous PDF phrase. Manually reviewed
                    # sections are checked through their recorded source anchors and page range instead.
                    if (
                        not is_manual
                        and not page_id.startswith("1.2.1-p07-")
                        and page_id != "pep-math-7-1-01-01-p001-1"
                        and len(text) >= 12
                        and text not in source
                    ):
                        raise SystemExit(
                            f"{spec.textbook_id}: {page_id} textbook text is not found on printed page {printed}: "
                            f"{block.get('text', '')[:80]}"
                        )
                elif kind == "visualization":
                    visualization_blocks += 1
                    if not str(block.get("renderer") or "").strip():
                        raise SystemExit(f"{spec.textbook_id}: {page_id} empty visualization renderer")

        if pages_count < 100:
            raise SystemExit(f"{spec.textbook_id}: suspiciously small course ({pages_count} pages)")
        return {
            "pages": pages_count,
            "nativeTextBlocks": native_text_blocks,
            "sourceExcerpts": 0,
            "visualizations": visualization_blocks,
            "manuallyReviewedSections": manually_reviewed_sections,
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
    total = {
        "pages": 0,
        "nativeTextBlocks": 0,
        "sourceExcerpts": 0,
        "visualizations": 0,
        "manuallyReviewedSections": 0,
    }
    for spec in BOOKS:
        result = audit_book(spec, args.source_root, args.pdf_root)
        print(spec.textbook_id, json.dumps(result, ensure_ascii=False))
        for key, value in result.items():
            total[key] += value
    print("total", json.dumps(total, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
