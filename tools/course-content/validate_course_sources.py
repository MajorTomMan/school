#!/usr/bin/env python3
"""Verify that every cloud course page points at the declared printed textbook page."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import unicodedata

import fitz


MATH_GLYPH_MAP = str.maketrans({
    "犪": "a",
    "犫": "b",
    "犮": "c",
    "犱": "d",
    "狓": "x",
    "犿": "m",
})


def normalize(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).translate(MATH_GLYPH_MAP)
    return "".join(
        char.lower()
        for char in normalized
        if char.isalnum() or "\u4e00" <= char <= "\u9fff"
    )


def iter_pages(course: dict[str, object]):
    for chapter in course.get("chapters", []):
        for section in chapter.get("sections", []):
            yield from section.get("pages", [])
        review = chapter.get("review") or {}
        yield from review.get("pages", [])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--pdf", type=Path, required=True)
    parser.add_argument("--page-index-offset", type=int, required=True)
    parser.add_argument("--expected-page-count", type=int, required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    course = json.loads(args.source.read_text(encoding="utf-8"))
    document = fitz.open(args.pdf)
    try:
        if document.page_count != args.expected_page_count:
            raise SystemExit(
                f"PDF page count mismatch: expected {args.expected_page_count}, "
                f"actual {document.page_count}"
            )

        page_text_cache: dict[int, str] = {}
        seen_ids: set[str] = set()
        validated_anchors = 0

        for page in iter_pages(course):
            page_id = str(page.get("id") or "").strip()
            if not page_id:
                raise SystemExit("course page id is empty")
            if page_id in seen_ids:
                raise SystemExit(f"duplicate course page id: {page_id}")
            seen_ids.add(page_id)

            start = int(page.get("sourcePage") or 0)
            end = int(page.get("sourcePageEnd") or start)
            if start <= 0 or end < start:
                raise SystemExit(f"{page_id}: invalid printed page range {start}..{end}")

            anchors = page.get("sourceAnchors") or []
            if not anchors:
                raise SystemExit(f"{page_id}: sourceAnchors is required")

            for anchor in anchors:
                printed_page = int(anchor.get("page") or 0)
                anchor_text = str(anchor.get("text") or "").strip()
                if printed_page < start or printed_page > end:
                    raise SystemExit(
                        f"{page_id}: anchor page {printed_page} is outside {start}..{end}"
                    )
                normalized_anchor = normalize(anchor_text)
                if len(normalized_anchor) < 8:
                    raise SystemExit(f"{page_id}: source anchor is too short")

                pdf_index = printed_page - 1 + args.page_index_offset
                if pdf_index < 0 or pdf_index >= document.page_count:
                    raise SystemExit(
                        f"{page_id}: printed page {printed_page} maps outside the PDF"
                    )
                page_text = page_text_cache.setdefault(
                    pdf_index,
                    normalize(document.load_page(pdf_index).get_text("text")),
                )
                if normalized_anchor not in page_text:
                    raise SystemExit(
                        f"{page_id}: source anchor not found on printed page "
                        f"{printed_page}: {anchor_text}"
                    )
                validated_anchors += 1

        if validated_anchors == 0:
            raise SystemExit("no textbook source anchors were validated")
        print(
            f"validated {len(seen_ids)} course pages and "
            f"{validated_anchors} textbook source anchors"
        )
        return 0
    finally:
        document.close()


if __name__ == "__main__":
    raise SystemExit(main())
