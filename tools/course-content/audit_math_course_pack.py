#!/usr/bin/env python3
"""Structural and editorial audit for the complete junior-high mathematics course pack."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys

import fitz

from generate_all_math_courses import BOOKS, clean_text

KNOWN_RENDERERS = {
    "opposite_quantities", "rational_classification", "rational_definition_flow",
    "number_line", "opposite_numbers", "absolute_value", "number_comparison",
    "addition_process", "subtraction_transform", "multiplication_sign",
    "division_transform", "power_process", "equation_balance", "equation_system",
    "inequality_number_line", "coordinate_plane", "intersecting_lines",
    "parallel_lines", "translation", "triangle", "congruent_triangles",
    "axis_symmetry", "pythagorean", "quadrilateral", "function_relation",
    "function_graph", "statistics", "probability", "rotation", "circle",
    "similarity", "right_triangle", "projection", "algebra_process", "history",
}

FORBIDDEN_TEXT = (
    "仅供个人学习使用", "未经授权不得另做他用",
    "本页以教材原页为准", "提示词", "AI生成",
)


def iter_sections(course: dict):
    for chapter in course["chapters"]:
        for section in chapter.get("sections", []):
            yield chapter, section
        review = chapter.get("review")
        if review:
            yield chapter, review


def all_pages(course: dict):
    for chapter, section in iter_sections(course):
        for page in section.get("pages", []):
            yield chapter, section, page


def expected_page_ranges(book):
    for index, chapter in enumerate(book.chapters):
        end = book.chapters[index + 1].start - 1 if index + 1 < len(book.chapters) else book.page_count - book.offset
        yield chapter.start, end


def substantive_printed_pages(book, pdf_path: Path) -> set[int]:
    document = fitz.open(pdf_path)
    try:
        pages: set[int] = set()
        for start, end in expected_page_ranges(book):
            for printed in range(start, end + 1):
                text = clean_text(document.load_page(printed - 1 + book.offset).get_text("text"))
                cjk_count = sum("\u4e00" <= ch <= "\u9fff" for ch in text)
                has_sentence = any(ch in text for ch in "。！？；：,.!?;:")
                if cjk_count >= 12 and (has_sentence or cjk_count >= 80):
                    pages.add(printed)
        return pages
    finally:
        document.close()


def audit_book(book, source_root: Path, pdf_dir: Path) -> tuple[int, int]:
    source = source_root / book.id / "course.json"
    pdf = pdf_dir / book.pdf_name
    course = json.loads(source.read_text(encoding="utf-8"))
    textbook = course.get("textbook", {})
    if textbook.get("id") != book.id or textbook.get("title") != book.title:
        raise SystemExit(f"{book.id}: textbook metadata mismatch")
    if len(course.get("chapters", [])) != len(book.chapters):
        raise SystemExit(f"{book.id}: chapter count mismatch")

    expected_chapters = [(chapter.number, chapter.title) for chapter in book.chapters]
    actual_chapters = [(chapter.get("number"), chapter.get("title")) for chapter in course["chapters"]]
    if actual_chapters != expected_chapters:
        raise SystemExit(f"{book.id}: chapter order/title mismatch")

    expected_entries = {(chapter.number, entry.number, entry.title) for chapter in book.chapters for entry in chapter.entries}
    actual_entries = set()
    represented_pages: set[int] = set()
    seen_ids: set[str] = set()
    renderers: set[str] = set()
    page_count = 0
    block_count = 0

    for chapter, section, page in all_pages(course):
        page_count += 1
        page_id = str(page.get("id") or "")
        if not page_id or page_id in seen_ids:
            raise SystemExit(f"{book.id}: missing or duplicate page id {page_id!r}")
        seen_ids.add(page_id)
        start = int(page.get("sourcePage", 0))
        end = int(page.get("sourcePageEnd", start))
        represented_pages.update(range(start, end + 1))
        if not page.get("sourceAnchors"):
            raise SystemExit(f"{page_id}: sourceAnchors missing")
        blocks = page.get("blocks") or []
        if not blocks:
            raise SystemExit(f"{page_id}: empty blocks")
        visible_chars = 0
        for block in blocks:
            block_count += 1
            kind = block.get("type")
            text = str(block.get("text") or block.get("statement") or block.get("stem") or block.get("expression") or "")
            visible_chars += len(text)
            if any(forbidden in text for forbidden in FORBIDDEN_TEXT):
                raise SystemExit(f"{page_id}: forbidden/generated wording found: {text[:80]}")
            if len(text) > 850:
                raise SystemExit(f"{page_id}: block too long ({len(text)} chars)")
            if kind == "visualization":
                renderer = str(block.get("renderer") or "")
                if renderer not in KNOWN_RENDERERS:
                    raise SystemExit(f"{page_id}: unknown renderer {renderer}")
                renderers.add(renderer)
        if visible_chars < 8 and not any(block.get("type") == "visualization" for block in blocks):
            raise SystemExit(f"{page_id}: page has no meaningful visible content")

    for chapter in course["chapters"]:
        for section in chapter.get("sections", []):
            number = section.get("number")
            if number != "章引言":
                actual_entries.add((chapter.get("number"), number, section.get("title")))
        review = chapter.get("review") or {}
        # Review pages aggregate multiple textbook entries, so verify their source numbers by title aliases below.
        review_titles = " ".join(str(item) for item in review.get("aliases", [])) + " " + str(review.get("title", ""))
        for expected_chapter, number, title in expected_entries:
            if expected_chapter == chapter.get("number") and number in {"小结"} and title in review_titles:
                actual_entries.add((expected_chapter, number, title))

    # Every ordinary/special entry must exist as a section; review entries are aggregated by design.
    for chapter in book.chapters:
        chapter_json = next(item for item in course["chapters"] if item["number"] == chapter.number)
        section_pairs = {(item["number"], item["title"]) for item in chapter_json.get("sections", [])}
        review = chapter_json.get("review") or {}
        review_pages = review.get("pages", [])
        for entry in chapter.entries:
            if entry.kind == "review":
                if not review_pages:
                    raise SystemExit(f"{book.id}: {chapter.number} review pages missing")
            elif (entry.number, entry.title) not in section_pairs:
                raise SystemExit(f"{book.id}: missing section {chapter.number} / {entry.number} {entry.title}")

    substantive = substantive_printed_pages(book, pdf)
    missing = sorted(substantive - represented_pages)
    if missing:
        raise SystemExit(f"{book.id}: substantive printed pages not represented: {missing}")
    if page_count < 100:
        raise SystemExit(f"{book.id}: implausibly small course ({page_count} pages)")

    if book.id == "pep-math-7-1":
        rational_pages = [page for _, section, page in all_pages(course) if section.get("number") == "1.2" and page.get("sourcePage") == 7]
        titles = [page.get("title") for page in rational_pages]
        expected_titles = ["回顾整数", "回顾分数", "整数写成分数形式", "有理数", "例1 辨认有理数"]
        if titles != expected_titles:
            raise SystemExit(f"pep-math-7-1: rational concept stages mismatch: {titles}")
        renderers_on_page7 = {
            block.get("renderer")
            for page in rational_pages
            for block in page.get("blocks", [])
            if block.get("type") == "visualization"
        }
        if renderers_on_page7 != {"rational_definition_flow"}:
            raise SystemExit(f"pep-math-7-1: wrong page-7 visualizations: {renderers_on_page7}")
        if not any(
            block.get("renderer") == "number_line"
            for _, section, page in all_pages(course)
            if section.get("number") == "1.2" and int(page.get("sourcePage", 0)) >= 8
            for block in page.get("blocks", [])
        ):
            raise SystemExit("pep-math-7-1: number-line section has no number-line visualization")

    print(f"{book.id}: audited {page_count} app pages, {block_count} blocks, {len(renderers)} renderer types")
    return page_count, block_count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, default=Path("tools/course-content"))
    parser.add_argument("--pdf-dir", type=Path, required=True)
    args = parser.parse_args()
    pages = blocks = 0
    for book in BOOKS:
        p, b = audit_book(book, args.source_root, args.pdf_dir)
        pages += p
        blocks += b
    print(f"complete mathematics pack: {len(BOOKS)} textbooks, {pages} app pages, {blocks} blocks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
