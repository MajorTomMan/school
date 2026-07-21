#!/usr/bin/env python3
"""Generate complete textbook-faithful junior-high mathematics course packages.

The PDF is the authority. Course pages preserve textbook wording and order, retain source anchors,
and use faithful PDF excerpts whenever mathematical typography or diagrams cannot be transcribed
without changing meaning.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import re
import unicodedata
from typing import Any, Iterable

import fitz


@dataclass(frozen=True)
class BookSpec:
    textbook_id: str
    filename: str
    title: str
    grade: str
    semester: str
    drive_id: str
    page_count: int
    page_index_offset: int
    sha256: str


BOOKS = (
    BookSpec("pep-math-7-1", "义务教育教科书·数学七年级上册.pdf", "义务教育教科书·数学七年级上册", "七年级", "上册", "1zPJIoh7Ora3AOMLXfDll8YbAZ8u1v78N", 202, 7, "11b6f1fbfa46eee4158953ef745ae1e6fbe6b9527a1423d55cbe75729e8210b9"),
    BookSpec("pep-math-7-2", "义务教育教科书·数学七年级下册.pdf", "义务教育教科书·数学七年级下册", "七年级", "下册", "15pn2jQjlWO6g4SCK5w2ys3SO8aTE-Kjh", 202, 7, "a58d058dd4f6a0855558a90a24640d9cf3ff8430112fb4d1f434bd881f836210"),
    BookSpec("pep-math-8-1", "义务教育教科书·数学八年级上册.pdf", "义务教育教科书·数学八年级上册", "八年级", "上册", "1spnH7HoU9wSX_J3HqdClU8an4wsWNTB6", 182, 7, "e21e7094a470f74a5be947e5fa8a9eee57bd6a4deaaaba3dbd09b811590838ed"),
    BookSpec("pep-math-8-2", "义务教育教科书·数学八年级下册.pdf", "义务教育教科书·数学八年级下册", "八年级", "下册", "1lJJzJupEt6FThZDB6WMH8AI09W2fT20D", 206, 7, "4979e490f518912473519aafc2b7e016aa804741ac7efbaa4156d230d9dc8b3c"),
    BookSpec("pep-math-9-1", "义务教育教科书·数学九年级上册.pdf", "义务教育教科书·数学九年级上册", "九年级", "上册", "1ArtD3pcWtIeawWPNSAVudUVk1X0hqWKx", 163, 7, "b6d43c0601c07a6a6f68d3be065c863b41529745e739b4496154c90ecbe7140d"),
    BookSpec("pep-math-9-2", "义务教育教科书·数学九年级下册.pdf", "义务教育教科书·数学九年级下册", "九年级", "下册", "12p2aGGugc_EvrO-Kqb17MHzMAqXvHTBn", 122, 7, "a71d30eb32b0e43737c88ada99a76ae6b5959050dc53c491b5206bae2d31b0ce"),
)

GLYPH_MAP = str.maketrans({
    "犃": "A", "犅": "B", "犆": "C", "犇": "D", "犈": "E", "犉": "F", "犌": "G", "犎": "H",
    "犐": "I", "犑": "J", "犓": "K", "犔": "L", "犕": "M", "犖": "N", "犗": "O", "犘": "P",
    "犙": "Q", "犚": "R", "犛": "S", "犜": "T", "犝": "U", "犞": "V", "犠": "W", "犡": "X",
    "犢": "Y", "犣": "Z",
    "犪": "a", "犫": "b", "犮": "c", "犱": "d", "犲": "e", "犳": "f", "犵": "g", "犺": "h",
    "犻": "i", "犼": "j", "犽": "k", "犾": "l", "犿": "m", "狀": "n", "狅": "o", "狆": "p",
    "狇": "q", "狉": "r", "狊": "s", "狋": "t", "狌": "u", "狏": "v", "狑": "w", "狓": "x",
    "狔": "y", "狕": "z", "槡": "√", "": "", "": "-", "": "", "": "",
    "": "", "": "", "": "", "": "", "": "", "": "",
    "": "", "": "", "": "", "": "", "": "", "": "",
    "": "", "": "-", "": "", "": "",
    "": "", "": "", "": "", "": "", "": "", "": "",
    "": "", "": "", "": "", "": "", "": "", "": "",
    "": "", "": "-",
    "": "",
})

# Real private-use separators used by the PDFs.
PRIVATE_RE = re.compile(r"[\ue000-\uf8ff]")
WEIRD_SHORT_RE = re.compile(r"^[^0-9A-Za-z\u4e00-\u9fff]+$")
SECTION_NUMBER_RE = re.compile(r"^\*?\d+(?:[.．]\d+){1,3}")
CHAPTER_RE = re.compile(r"^第[一二三四五六七八九十百]+章")
EXAMPLE_RE = re.compile(r"^(例\s*\d+|问题\s*\d*|练习|思考|探究|观察与猜想|实验与探究|信息技术应用)")
CAPTION_RE = re.compile(r"^(图|表)\s*\d")
MATH_OPERATOR_RE = re.compile(r"[-=＋+－−×÷√∠△∥⊥≤≥<>≈%²³^]|[A-Za-z]\s*[-+×÷=]")


def file_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def display_text(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).translate(GLYPH_MAP)
    value = PRIVATE_RE.sub("·", value)
    value = value.replace("\u200b", "").replace("\ufeff", "")
    value = value.replace("﹣", "−").replace("–", "−")
    value = re.sub(r"[ \t]+", "", value)
    value = re.sub(r"\n+", "", value)
    value = re.sub(r"·{3,}", "…", value)
    value = re.sub(r"([。！？；：,.!?;:])\1+", r"\1", value)
    return value.strip()


def normalized_anchor(value: str) -> str:
    text = display_text(value)
    return "".join(ch for ch in text if ch.isalnum() or "\u4e00" <= ch <= "\u9fff")


def clean_title(value: str) -> str:
    text = display_text(value).replace("·", ".")
    text = re.sub(r"\s+", "", text)
    return text.strip(".· ")


def printed_page(spec: BookSpec, pdf_page_1based: int) -> int:
    return pdf_page_1based - spec.page_index_offset


def extract_text_blocks(page: fitz.Page, chapter_title: str, section_title: str) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    height = page.rect.height
    dictionary = page.get_text("dict")
    for raw in dictionary.get("blocks", []):
        if raw.get("type") != 0:
            continue
        lines = raw.get("lines") or []
        spans = [span for line in lines for span in line.get("spans", [])]
        text = display_text("".join(span.get("text", "") for span in spans))
        if not text:
            continue
        x0, y0, x1, y1 = map(float, raw["bbox"])
        max_size = max((float(span.get("size", 0)) for span in spans), default=0)
        # Running heads and printed page numbers.
        if y0 > height - 52:
            continue
        if y0 > height - 78 and (clean_title(chapter_title) in clean_title(text) or re.fullmatch(r"[0-9０-９]+", text)):
            continue
        if len(text) <= 5 and WEIRD_SHORT_RE.fullmatch(text):
            continue
        compact = clean_title(text)
        known_titles = {clean_title(chapter_title), clean_title(section_title)}
        if compact in known_titles or any(compact.startswith(title) and len(compact) <= len(title) + 3 for title in known_titles if title):
            continue
        if CHAPTER_RE.match(compact) and len(compact) < 30:
            continue
        result.append({"text": text, "bbox": [round(x0, 2), round(y0, 2), round(x1, 2), round(y1, 2)], "fontSize": max_size})
    result.sort(key=lambda item: (item["bbox"][1], item["bbox"][0]))
    return result


def split_long_text(text: str, target: int = 260, hard: int = 430) -> list[str]:
    if len(text) <= hard:
        return [text]
    pieces = re.split(r"(?<=[。！？；])", text)
    out: list[str] = []
    current = ""
    for piece in pieces:
        if not piece:
            continue
        if current and len(current) + len(piece) > hard:
            out.append(current)
            current = piece
        else:
            current += piece
        if len(current) >= target:
            out.append(current)
            current = ""
    if current:
        out.append(current)
    return [item for item in out if item]


def needs_source_excerpt(text: str) -> bool:
    operators = len(MATH_OPERATOR_RE.findall(text))
    digit_count = sum(ch.isdigit() for ch in text)
    # Long prose remains native. Formula-rich regions use the exact PDF crop.
    return operators >= 2 or (operators >= 1 and digit_count >= 8 and len(text) < 360)


def block_to_course(raw: dict[str, Any], printed: int) -> list[dict[str, Any]]:
    text = raw["text"]
    if needs_source_excerpt(text):
        x0, y0, x1, y1 = raw["bbox"]
        pad_x, pad_y = 7.0, 6.0
        return [{
            "type": "source_excerpt",
            "sourcePage": printed,
            "bbox": [max(0, x0 - pad_x), max(0, y0 - pad_y), x1 + pad_x, y1 + pad_y],
            "fallbackText": text,
            "altText": "教材原式与图示",
        }]
    role = "textbook_text"
    if EXAMPLE_RE.match(text):
        # Keep textbook wording intact while making its instructional stage visible.
        stage = EXAMPLE_RE.match(text).group(1)
        return [{"type": "heading", "text": stage}] + [
            {"type": "textbook_text", "text": part} for part in split_long_text(text)
        ]
    if CAPTION_RE.match(text):
        role = "caption"
    elif text.endswith("?") or text.endswith("？"):
        role = "prompt"
    return [{"type": role, "text": part} for part in split_long_text(text)]


def visual_for(title: str) -> tuple[str, dict[str, str]] | None:
    t = clean_title(title)
    mappings: list[tuple[Iterable[str], str, dict[str, str]]] = [
        (("有理数的概念",), "integer_to_fraction", {"title": "整数写成分数形式"}),
        (("正数和负数",), "opposite_quantities", {"title": "具有相反意义的量"}),
        (("数轴",), "number_line", {"title": "原点、正方向和单位长度"}),
        (("相反数",), "opposite_numbers", {"title": "关于原点对称"}),
        (("绝对值",), "absolute_value", {"title": "到原点的距离"}),
        (("大小比较",), "number_comparison", {"title": "数轴上的左右顺序"}),
        (("加法",), "addition_process", {"title": "正负单位的合并"}),
        (("减法",), "subtraction_transform", {"title": "减法转化为加法"}),
        (("乘法",), "multiplication_sign", {"title": "积的符号"}),
        (("除法",), "division_transform", {"title": "除法转化"}),
        (("乘方", "幂的运算"), "power_process", {"title": "重复相乘"}),
        (("代数式", "整式", "因式分解", "分式"), "algebra_process", {"title": "式的结构与变形"}),
        (("方程", "消元", "不等式"), "equation_balance", {"title": "等式两边的同一变形"}),
        (("平方根", "立方根", "二次根式"), "root_number_line", {"title": "根式与数的位置"}),
        (("坐标", "平面直角坐标系"), "cartesian_plane", {"title": "点与坐标"}),
        (("二次函数",), "function_graph", {"title": "二次函数图象", "function": "quadratic"}),
        (("反比例函数",), "function_graph", {"title": "反比例函数图象", "function": "inverse"}),
        (("函数", "一次函数"), "function_graph", {"title": "变量关系与函数图象", "function": "linear"}),
        (("平移", "旋转", "轴对称", "中心对称", "相似", "位似"), "transformation", {"title": "图形变换"}),
        (("勾股", "锐角三角函数", "解直角三角形"), "right_triangle", {"title": "直角三角形中的数量关系"}),
        (("统计", "数据", "直方图", "趋势图"), "data_chart", {"title": "数据的整理与描述"}),
        (("概率", "随机事件"), "probability", {"title": "事件与可能结果"}),
        (("投影", "三视图"), "projection", {"title": "立体图形与平面表示"}),
        (("三角形", "四边形", "平行线", "垂直", "角", "圆", "多边形", "直线", "线段"), "geometry", {"title": "几何关系"}),
    ]
    for needles, renderer, params in mappings:
        if any(needle in t for needle in needles):
            shape = ""
            if renderer == "geometry":
                if "圆" in t: shape = "circle"
                elif "平行" in t or "垂直" in t: shape = "parallel"
                else: shape = "triangle"
            return renderer, ({**params, **({"shape": shape} if shape else {})})
    return None


def page_title(section_title: str, printed: int, blocks: list[dict[str, Any]], part: int, total: int) -> str:
    if part == 0:
        for raw in blocks:
            text = raw["text"]
            match = EXAMPLE_RE.match(text)
            if match and len(match.group(1)) > 1:
                return match.group(1)
        return section_title
    suffix = f"（{part + 1}）" if total > 1 else ""
    return f"教材第{printed}页{suffix}"


def source_anchor_for(blocks: list[dict[str, Any]], printed: int, page_blocks: list[dict[str, Any]] | None = None) -> list[dict[str, Any]]:
    candidates = [raw["text"] for raw in blocks if len(normalized_anchor(raw["text"])) >= 12]
    if not candidates and page_blocks:
        candidates = [raw["text"] for raw in page_blocks if len(normalized_anchor(raw["text"])) >= 12]
    if not candidates:
        candidates = ["教材第" + str(printed) + "页"]
    best = candidates[0]
    normalized = display_text(best)
    # Keep a contiguous phrase and avoid cutting through a mathematical token where possible.
    if len(normalized) > 48:
        end = 48
        for punct in "。！？；，":
            pos = normalized.find(punct, 20, 55)
            if pos >= 0:
                end = pos + 1
                break
        normalized = normalized[:end]
    return [{"page": printed, "text": normalized}]


def group_blocks_for_pages(raw_blocks: list[dict[str, Any]]) -> list[list[dict[str, Any]]]:
    if not raw_blocks:
        return []
    groups: list[list[dict[str, Any]]] = []
    current: list[dict[str, Any]] = []
    score = 0
    for raw in raw_blocks:
        text = raw["text"]
        item_score = min(len(text), 500) + (350 if needs_source_excerpt(text) else 0)
        stage_break = bool(EXAMPLE_RE.match(text)) and current
        if current and (score + item_score > 1250 or len(current) >= 5 or stage_break):
            groups.append(current)
            current = []
            score = 0
        current.append(raw)
        score += item_score
    if current:
        groups.append(current)
    return groups


def section_boundaries(spec: BookSpec, doc: fitz.Document) -> list[dict[str, Any]]:
    toc = doc.get_toc()
    chapters: list[dict[str, Any]] = []
    for idx, (level, title, pdf_page) in enumerate(toc):
        if level != 1 or not CHAPTER_RE.match(clean_title(title)):
            continue
        chapter_end_pdf = next((row[2] - 1 for row in toc[idx + 1:] if row[0] == 1 and CHAPTER_RE.match(clean_title(row[1]))), doc.page_count)
        children = [row for row in toc[idx + 1:] if row[2] <= chapter_end_pdf and row[0] >= 2]
        candidates: dict[int, tuple[int, str]] = {}
        for child_level, child_title, child_pdf in children:
            page = printed_page(spec, child_pdf)
            if page <= 0:
                continue
            existing = candidates.get(page)
            special = any(key in clean_title(child_title) for key in ("小结", "复习题", "数学活动", "综合与实践", "课题学习"))
            rank = 100 - child_level if special else child_level
            if existing is None or rank > existing[0]:
                candidates[page] = (rank, child_title)
        chapter_start = printed_page(spec, pdf_page)
        chapter_end = printed_page(spec, chapter_end_pdf)
        starts = sorted((page, clean_title(value[1])) for page, value in candidates.items() if chapter_start <= page <= chapter_end)
        sections: list[dict[str, Any]] = []
        if starts and chapter_start < starts[0][0]:
            sections.append({"title": clean_title(title), "start": chapter_start, "end": starts[0][0] - 1})
        for pos, (start, section_title) in enumerate(starts):
            end = (starts[pos + 1][0] - 1) if pos + 1 < len(starts) else chapter_end
            if end >= start:
                sections.append({"title": section_title, "start": start, "end": end})
        if not sections:
            sections.append({"title": clean_title(title), "start": chapter_start, "end": chapter_end})
        chapters.append({
            "number": clean_title(title).split("章", 1)[0] + "章",
            "title": clean_title(title).split("章", 1)[1] if "章" in clean_title(title) else clean_title(title),
            "start": chapter_start,
            "end": chapter_end,
            "sections": sections,
        })
    return chapters


def manual_rational_pages() -> list[dict[str, Any]]:
    # This is the chapter currently being reviewed. Wording and order follow printed pages 7-8.
    return [
        {
            "id": "1.2.1-p07-a",
            "type": "lesson",
            "title": "整数和分数",
            "sourcePage": 7,
            "sourceAnchors": [{"page": 7, "text": "正整数、0、负整数统称为整数"}],
            "blocks": [
                {"type": "textbook_text", "text": "在小学阶段和上一节中，我们认识了很多数。回想一下，到目前为止，我们认识了哪些数？"},
                {"type": "textbook_text", "text": "我们学习过正整数，如1，2，3，…；0；负整数，如−1，−2，−3，…。正整数、0、负整数统称为整数。"},
                {"type": "source_excerpt", "sourcePage": 7, "bbox": [86.0, 327.0, 482.0, 493.0], "fallbackText": "正分数、负分数以及有限小数、无限循环小数的教材示例。", "altText": "教材中的分数与小数示例"},
                {"type": "textbook_text", "text": "事实上，有限小数和无限循环小数都可以化为分数，因此它们也可以看成分数。"},
            ],
        },
        {
            "id": "1.2.1-p07-b",
            "type": "lesson",
            "title": "整数写成分数形式",
            "sourcePage": 7,
            "sourceAnchors": [{"page": 7, "text": "这样，整数可以写成分数的形式"}],
            "blocks": [
                {"type": "textbook_text", "text": "进一步地，正整数可以写成正分数的形式，例如2=2/1；负整数可以写成负分数的形式，例如−3=−3/1；0也可以写成分数的形式0/1。这样，整数可以写成分数的形式。"},
                {"type": "visualization", "renderer": "integer_to_fraction", "params": {"title": "整数写成分数形式"}},
            ],
        },
        {
            "id": "1.2.1-p07-c",
            "type": "lesson",
            "title": "有理数的概念",
            "aliases": ["有理数概念"],
            "sourcePage": 7,
            "sourceAnchors": [{"page": 7, "text": "可以写成分数形式的数称为有理数"}],
            "blocks": [
                {"type": "textbook_text", "text": "可以写成分数形式的数称为有理数（rational number）。其中，可以写成正分数形式的数为正有理数，可以写成负分数形式的数为负有理数。"},
                {"type": "textbook_text", "text": "这样，引入负数后，我们对数的认识就扩大到了有理数范围。"},
            ],
        },
        {
            "id": "1.2.1-p07-d",
            "type": "lesson",
            "title": "例1 辨认有理数",
            "sourcePage": 7,
            "sourcePageEnd": 8,
            "sourceAnchors": [{"page": 7, "text": "指出下列各数中的正有理数、负有理数"}, {"page": 8, "text": "其中正整数有13，20"}],
            "blocks": [
                {"type": "heading", "text": "例1"},
                {"type": "source_excerpt", "sourcePage": 7, "bbox": [62.0, 573.0, 486.0, 681.0], "fallbackText": "指出下列各数中的正有理数、负有理数，并分别指出其中的正整数、负整数。", "altText": "教材例1题目"},
                {"type": "source_excerpt", "sourcePage": 8, "bbox": [60.0, 42.0, 465.0, 126.0], "fallbackText": "教材例1的解答。", "altText": "教材例1解答"},
            ],
        },
    ]


def generate_book(spec: BookSpec, pdf_root: Path, output_root: Path) -> dict[str, Any]:
    pdf_path = pdf_root / spec.filename
    if not pdf_path.is_file():
        raise SystemExit(f"missing PDF: {pdf_path}")
    if pdf_path.stat().st_size <= 0 or file_sha256(pdf_path) != spec.sha256:
        raise SystemExit(f"PDF digest mismatch: {pdf_path.name}")
    doc = fitz.open(pdf_path)
    try:
        if doc.page_count != spec.page_count:
            raise SystemExit(f"page count mismatch for {spec.filename}: {doc.page_count}")
        chapter_specs = section_boundaries(spec, doc)
        chapters: list[dict[str, Any]] = []
        for chapter_index, chapter in enumerate(chapter_specs, 1):
            chapter_id = f"chapter-{chapter_index:02d}"
            sections: list[dict[str, Any]] = []
            for section_index, section in enumerate(chapter["sections"], 1):
                section_id = re.sub(r"[^0-9A-Za-z._-]+", "-", section["title"]).strip("-") or f"section-{section_index:02d}"
                pages: list[dict[str, Any]] = []
                if spec.textbook_id == "pep-math-7-1" and "有理数的概念" in section["title"]:
                    pages.extend(manual_rational_pages())
                    page_start = max(section["start"], 8)
                else:
                    page_start = section["start"]
                visual_added = False
                visual = visual_for(section["title"])
                for printed in range(page_start, section["end"] + 1):
                    pdf_index = printed - 1 + spec.page_index_offset
                    if pdf_index < 0 or pdf_index >= doc.page_count:
                        continue
                    raw_blocks = extract_text_blocks(doc[pdf_index], chapter["title"], section["title"])
                    groups = group_blocks_for_pages(raw_blocks)
                    if not groups:
                        continue
                    for part_index, group in enumerate(groups):
                        course_blocks: list[dict[str, Any]] = []
                        if visual and not visual_added and part_index == 0:
                            # Textbook wording comes first. The visualization follows the first instructional unit.
                            pass
                        for raw in group:
                            course_blocks.extend(block_to_course(raw, printed))
                        if visual and not visual_added:
                            course_blocks.append({"type": "visualization", "renderer": visual[0], "params": visual[1]})
                            visual_added = True
                        pages.append({
                            "id": f"{spec.textbook_id}-{chapter_index:02d}-{section_index:02d}-p{printed:03d}-{part_index + 1}",
                            "type": "lesson",
                            "title": page_title(section["title"], printed, group, part_index, len(groups)),
                            "sourcePage": printed,
                            "sourceAnchors": source_anchor_for(group, printed, raw_blocks),
                            "blocks": course_blocks,
                        })
                if pages:
                    sections.append({
                        "id": section_id,
                        "number": re.match(r"\*?\d+(?:\.\d+)*", section["title"]).group(0) if re.match(r"\*?\d+(?:\.\d+)*", section["title"]) else "",
                        "title": section["title"],
                        "aliases": [section["title"].replace(" ", "")],
                        "pages": pages,
                    })
            chapters.append({
                "id": chapter_id,
                "number": chapter["number"],
                "title": chapter["title"],
                "aliases": [f"{chapter['number']}{chapter['title']}", f"{chapter['number']} {chapter['title']}"],
                "sections": sections,
            })
        payload = {
            "schemaVersion": 1,
            "textbook": {
                "id": spec.textbook_id,
                "title": spec.title,
                "publisher": "人民教育出版社",
                "edition": "人教版",
                "grade": spec.grade,
                "semester": spec.semester,
                "subject": "数学",
                "pdf": {
                    "path": "assets/textbook.pdf",
                    "url": f"https://drive.google.com/file/d/{spec.drive_id}/view",
                    "size": pdf_path.stat().st_size,
                    "sha256": spec.sha256,
                    "pageCount": spec.page_count,
                    "pageIndexOffset": spec.page_index_offset,
                },
            },
            "chapters": chapters,
        }
        destination = output_root / spec.textbook_id / "course.json"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return payload
    finally:
        doc.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pdf-root", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--book", action="append", default=[], help="optional textbook id filter")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    selected = [spec for spec in BOOKS if not args.book or spec.textbook_id in set(args.book)]
    totals = {"books": 0, "chapters": 0, "sections": 0, "pages": 0, "blocks": 0}
    for spec in selected:
        payload = generate_book(spec, args.pdf_root.resolve(), args.output_root.resolve())
        chapters = payload["chapters"]
        sections = [section for chapter in chapters for section in chapter["sections"]]
        pages = [page for section in sections for page in section["pages"]]
        blocks = [block for page in pages for block in page["blocks"]]
        totals["books"] += 1
        totals["chapters"] += len(chapters)
        totals["sections"] += len(sections)
        totals["pages"] += len(pages)
        totals["blocks"] += len(blocks)
        print(f"{spec.textbook_id}: {len(chapters)} chapters, {len(sections)} sections, {len(pages)} pages")
    print(json.dumps(totals, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
