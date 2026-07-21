#!/usr/bin/env python3
"""Verify native textbook wording against the declared printed textbook page range."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import unicodedata

import fitz

from course_package import validate_course

MATH_GLYPH_MAP = str.maketrans({
    "犃": "A", "犅": "B", "犆": "C", "犇": "D", "犈": "E", "犉": "F", "犌": "G", "犎": "H",
    "犐": "I", "犑": "J", "犓": "K", "犔": "L", "犕": "M", "犖": "N", "犗": "O", "犘": "P",
    "犙": "Q", "犚": "R", "犛": "S", "犜": "T", "犝": "U", "犞": "V", "犠": "W", "犡": "X",
    "犢": "Y", "犣": "Z", "犪": "a", "犫": "b", "犮": "c", "犱": "d", "犲": "e", "犳": "f",
    "犵": "g", "犺": "h", "犻": "i", "犼": "j", "犽": "k", "犾": "l", "犿": "m", "狀": "n",
    "狅": "o", "狆": "p", "狇": "q", "狉": "r", "狊": "s", "狋": "t", "狌": "u", "狏": "v",
    "狑": "w", "狓": "x", "狔": "y", "狕": "z", "槡": "√",
})


def normalize(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).translate(MATH_GLYPH_MAP)
    normalized = "".join("·" if "\ue000" <= char <= "\uf8ff" else char for char in normalized)
    return "".join(char.lower() for char in normalized if char.isalnum() or "\u4e00" <= char <= "\u9fff")


def iter_pages(course: dict):
    for chapter in course["chapters"]:
        for section in chapter.get("sections", []):
            yield from section["pages"]
        if chapter.get("review"):
            yield from chapter["review"]["pages"]


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
    validate_course(course)
    document = fitz.open(args.pdf)
    try:
        if document.page_count != args.expected_page_count:
            raise SystemExit(f"PDF page count mismatch: expected {args.expected_page_count}, actual {document.page_count}")
        cache: dict[int, str] = {}
        checked = 0
        for page in iter_pages(course):
            start = page["sourcePage"]
            end = page.get("sourcePageEnd", start)
            source_parts = []
            for printed in range(start, end + 1):
                pdf_index = printed - 1 + args.page_index_offset
                if not 0 <= pdf_index < document.page_count:
                    raise SystemExit(f"{page['id']}: printed page {printed} maps outside PDF")
                source_parts.append(cache.setdefault(pdf_index, normalize(document[pdf_index].get_text("text"))))
            source = "".join(source_parts)
            for block in page["blocks"]:
                if block.get("type") != "text" or block.get("style") != "textbook":
                    continue
                text = normalize(block["text"])
                if len(text) >= 12 and text not in source:
                    raise SystemExit(
                        f"{page['id']}: textbook wording not found in printed pages {start}..{end}: "
                        f"{block['text'][:100]}"
                    )
                if len(text) >= 12:
                    checked += 1
        if checked == 0:
            raise SystemExit("no textbook wording was validated")
        print(f"validated {checked} textbook passages")
        return 0
    finally:
        document.close()


if __name__ == "__main__":
    raise SystemExit(main())
