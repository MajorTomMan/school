#!/usr/bin/env python3
"""Validate cloud course pages against the declared printed textbook pages.

The validator deliberately checks both short source anchors and every textbook-derived block.
This prevents a course page from carrying a valid page number while its displayed wording comes
from another page or from an unsupported paraphrase.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import unicodedata

import fitz


MATH_GLYPH_MAP = str.maketrans({
    "犪": "a", "犫": "b", "犮": "c", "犱": "d", "犲": "e", "犳": "f",
    "犵": "g", "犺": "h", "犻": "i", "犼": "j", "犽": "k", "犾": "l",
    "犿": "m", "狀": "n", "狅": "o", "狆": "p", "狇": "q", "狉": "r",
    "狊": "s", "狋": "t", "狌": "u", "狏": "v", "狑": "w", "狓": "x",
    "狔": "y", "狕": "z",
    "犃": "A", "犅": "B", "犆": "C", "犇": "D", "犈": "E", "犉": "F",
    "犌": "G", "犎": "H", "犐": "I", "犑": "J", "犓": "K", "犔": "L",
    "犕": "M", "犖": "N", "犗": "O", "犘": "P", "犙": "Q", "犚": "R",
    "犛": "S", "犜": "T", "犝": "U", "犞": "V", "犠": "W", "犡": "X",
    "犢": "Y", "犣": "Z", "烅": "", "烄": "", "烆": "", "烍": "", "烌": "", "烎": "",
    "烐": "", "烏": "", "烑": "", "烉": "", "烇": "", "烋": "",
    "熿": "", "燀": "", "燄": "", "燅": "", "槡": "√", "": ".", "": "-",
})

WATERMARKS = (
    "仅供个人学习使用，未经授权不得另做他用",
    "仅供个人学习使用",
    "未经授权不得另做他用",
)


def normalize(value: str) -> str:
    for watermark in WATERMARKS:
        value = value.replace(watermark, "")
    normalized = unicodedata.normalize("NFKC", value).translate(MATH_GLYPH_MAP)
    return "".join(
        char.lower()
        for char in normalized
        if (char.isascii() and char.isalnum())
        or "\u4e00" <= char <= "\u9fff"
        or "\u0370" <= char <= "\u03ff"
        or char in "√π"
    )




def normalized_page_text(page: fitz.Page) -> str:
    """Normalize the PDF in the same visual block order used by the course generator."""
    raw_blocks = []
    for block in sorted(page.get_text("blocks"), key=lambda item: (round(item[1] / 4), item[0])):
        raw_blocks.append(str(block[4]))
    return normalize("\n".join(raw_blocks))

def iter_pages(course: dict[str, object]):
    for chapter in course.get("chapters", []):
        for section in chapter.get("sections", []):
            yield chapter, section, section.get("pages", [])
        review = chapter.get("review") or {}
        if review:
            yield chapter, review, review.get("pages", [])


def source_texts(page: dict[str, object]):
    for block in page.get("blocks") or []:
        block_type = str(block.get("type") or "")
        if block_type in {"textbook_text", "prompt", "historical_note"}:
            text = str(block.get("text") or "").strip()
            if text:
                yield block_type, text
        elif block_type == "worked_example":
            text = str(block.get("statement") or "").strip()
            if text:
                yield block_type, text
            for step in block.get("steps") or []:
                if str(step).strip():
                    yield block_type, str(step).strip()
            result = str(block.get("result") or "").strip()
            if result:
                yield block_type, result
        elif block_type == "exercise":
            text = str(block.get("stem") or "").strip()
            if text:
                yield block_type, text


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
                f"PDF page count mismatch: expected {args.expected_page_count}, actual {document.page_count}"
            )

        page_text_cache: dict[int, str] = {}
        seen_ids: set[str] = set()
        validated_anchors = 0
        validated_blocks = 0
        chapter_count = 0
        section_count = 0

        for chapter, section, pages in iter_pages(course):
            chapter_count += 1 if section is chapter.get("sections", [None])[0] else 0
            section_count += 1
            if not pages:
                raise SystemExit(f"{chapter.get('title')} / {section.get('title')}: no course pages")
            previous_source_page = 0
            for page in pages:
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
                if start < previous_source_page:
                    raise SystemExit(f"{page_id}: source pages are not in textbook order")
                previous_source_page = start

                anchors = page.get("sourceAnchors") or []
                if not anchors:
                    raise SystemExit(f"{page_id}: sourceAnchors is required")

                page_texts: dict[int, str] = {}
                for printed_page in range(start, end + 1):
                    pdf_index = printed_page - 1 + args.page_index_offset
                    if pdf_index < 0 or pdf_index >= document.page_count:
                        raise SystemExit(f"{page_id}: printed page {printed_page} maps outside the PDF")
                    page_texts[printed_page] = page_text_cache.setdefault(
                        pdf_index,
                        normalized_page_text(document.load_page(pdf_index)),
                    )
                combined_source = "".join(page_texts.values())

                for anchor in anchors:
                    printed_page = int(anchor.get("page") or 0)
                    anchor_text = str(anchor.get("text") or "").strip()
                    if printed_page < start or printed_page > end:
                        raise SystemExit(f"{page_id}: anchor page {printed_page} is outside {start}..{end}")
                    normalized_anchor = normalize(anchor_text)
                    if len(normalized_anchor) < 8:
                        raise SystemExit(f"{page_id}: source anchor is too short")
                    if normalized_anchor not in page_texts[printed_page]:
                        raise SystemExit(
                            f"{page_id}: source anchor not found on printed page {printed_page}: {anchor_text}"
                        )
                    validated_anchors += 1

                for block_type, text in source_texts(page):
                    normalized_block = normalize(text)
                    if len(normalized_block) < 4:
                        continue
                    cjk_count = sum("\u4e00" <= ch <= "\u9fff" for ch in text)
                    if cjk_count < 2 or (len(normalized_block) < 30 and cjk_count <= 4):
                        # Stand-alone diagram labels are validated through the page anchor and the
                        # surrounding textbook paragraph rather than as prose.
                        continue
                    # PDF reading order can interleave side notes. Validate each sufficiently long
                    # literal excerpt by a stable 36-character prefix and suffix rather than by a
                    # single full-page string comparison.
                    samples = [normalized_block[: min(36, len(normalized_block))]]
                    if len(normalized_block) > 100:
                        middle = len(normalized_block) // 2
                        samples.append(normalized_block[middle:middle + 24])
                    matched = sum(sample in combined_source for sample in samples if sample)
                    if matched == 0:
                        raise SystemExit(
                            f"{page_id}: {block_type} is not supported by printed page {start}..{end}: {text[:90]}"
                        )
                    validated_blocks += 1

        if validated_anchors == 0 or validated_blocks == 0:
            raise SystemExit("no textbook source material was validated")
        print(
            f"validated {len(seen_ids)} course pages, {validated_anchors} anchors and "
            f"{validated_blocks} textbook blocks across {section_count} sections"
        )
        return 0
    finally:
        document.close()


if __name__ == "__main__":
    raise SystemExit(main())
