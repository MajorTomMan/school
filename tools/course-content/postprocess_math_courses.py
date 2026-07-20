#!/usr/bin/env python3
"""Turn textbook-derived course data into native School lessons.

The PDF remains the authority for wording, page numbers and source anchors. Published course JSON never
contains PDF crop instructions: verified fallback wording becomes native text, while School-owned
explanations and visualizations provide the learning experience.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

GENERIC_EXCERPT_TEXTS = (
    "教材原式",
    "教材原图",
    "教材图示",
    "教材示例",
    "教材例",
)

APP_EXPLANATION = "先把0看成共同基准：数值落在基准的哪一边，由正负号表示；离基准有多远，由数的大小表示。"
APP_CONCLUSION = "正负号表示相反方向，数值大小表示相对0这个基准的变化量；0既不是正数，也不是负数。"


def iter_pages(course: dict[str, Any]):
    for chapter in course.get("chapters", []):
        for section in chapter.get("sections", []):
            for page in section.get("pages", []):
                yield chapter, section, page
        review = chapter.get("review") or {}
        for page in review.get("pages", []):
            yield chapter, review, page


def meaningful_fallback(value: str) -> bool:
    text = value.strip()
    if not text:
        return False
    if len(text) < 32 and any(text.startswith(prefix) for prefix in GENERIC_EXCERPT_TEXTS):
        return False
    return True


def textbook_text(value: str) -> dict[str, str]:
    return {"type": "textbook_text", "text": value.strip()}


def convert_excerpts(page: dict[str, Any]) -> int:
    converted = 0
    blocks: list[dict[str, Any]] = []
    for block in page.get("blocks", []):
        if block.get("type") != "source_excerpt":
            blocks.append(block)
            continue
        fallback = str(block.get("fallbackText") or "").strip()
        if meaningful_fallback(fallback):
            blocks.append(textbook_text(fallback))
        converted += 1
    page["blocks"] = blocks
    return converted


def replace_page_blocks(page: dict[str, Any], blocks: list[dict[str, Any]]) -> None:
    page["blocks"] = blocks


def curate_pep_7_1(course: dict[str, Any]) -> None:
    pages = {page["id"]: page for _, _, page in iter_pages(course)}

    intro = pages.get("pep-math-7-1-01-01-p001-1")
    if intro:
        replace_page_blocks(intro, [
            textbook_text("在小学，我们从日常生活中的实例出发，学习了自然数、小数、分数及其运算。在日常生活、生产和科研中，还会遇到另外一些数的表示问题。例如："),
            {"type": "prompt", "text": "（1）北京冬季某一天的最高气温为零上3摄氏度，最低气温为零下3摄氏度。如何用数区分“零上3摄氏度”和“零下3摄氏度”？"},
            {"type": "prompt", "text": "（2）某公司今年7月份盈利50万元，8月份亏损10万元。该公司在记账时如何用数分别表示“盈利50万元”和“亏损10万元”？"},
            {"type": "prompt", "text": "（3）某年，我国棉花产量比上年增长7.8%，玉米产量比上年减少0.7%。统计这两种农作物产量的变化情况时，如何用数分别表示“增长7.8%”和“减少0.7%”？"},
            textbook_text("上面的问题都涉及意义相反的两个量，为了能用数表示像这样具有相反意义的两个量，需要引入负数。本章我们将认识负数的意义，把数的范围扩大到有理数，并在有理数范围内学习数的表示和大小比较等。"),
        ])

    positive_negative = pages.get("pep-math-7-1-01-02-p002-1")
    if positive_negative:
        replace_page_blocks(positive_negative, [
            textbook_text("数的产生和发展离不开生活和生产的需要。人们对于数的认识就是伴随着记数、测量、运算等方面的需求不断拓展的。在小学，我们学过自然数、小数和分数，它们都是大于或等于0的数，但是在日常生活和生产实践中，为了表达和运算的需要，还有必要引入一类新的数。"),
            textbook_text("在本章引言的问题中，温度比0℃高，称为零上温度；温度比0℃低，称为零下温度。零上温度和零下温度是以0℃为分界点的具有相反意义的量。零上3摄氏度用3℃表示，零下3摄氏度用−3℃表示。类似地，如果用50万元表示盈利50万元，就可以用−10万元表示亏损10万元；如果用7.8%表示增长7.8%，就可以用−0.7%表示减少0.7%。"),
            textbook_text("在数学中，像3，50，7.8%这样大于0的数叫作正数，像−3，−10，−0.7%这样在正数前加上符号“−”的数叫作负数。一个数前面的“+”“−”号叫作这个数的符号。0既不是正数，也不是负数。"),
            {"type": "explanation", "text": APP_EXPLANATION},
            {"type": "visualization", "renderer": "opposite_quantities", "params": {"title": "以0为基准观察相反方向", "scene": "temperature"}},
            {"type": "conclusion", "text": APP_CONCLUSION},
        ])

    for chapter in course.get("chapters", []):
        for section in chapter.get("sections", []):
            if section.get("title") == "1.1正数和负数":
                section["pages"] = [
                    page for page in section.get("pages", [])
                    if page.get("id") != "pep-math-7-1-01-02-p002-2"
                ]

    example = pages.get("pep-math-7-1-01-02-p003-1")
    if example:
        replace_page_blocks(example, [
            {"type": "historical_note", "text": "我国是历史上最早认识和使用负数的国家。至迟成书于东汉早期（约1世纪）的我国古代数学著作《九章算术》，在“方程”一章中提出了正数、负数的概念及其加减运算法则。魏晋时期的数学家刘徽在为《九章算术》作注时，用不同颜色的算筹分别表示正数和负数，红色为正，黑色为负。"},
            textbook_text("如果一个问题中出现具有相反意义的量，就可以用正数和负数分别表示它们。"),
            {
                "type": "worked_example",
                "label": "例1",
                "statement": "一箱橘子的标准质量为2.5 kg。如果用正数表示超过标准质量的克数，比标准质量多65 g和比标准质量少30 g各怎么表示？50 g，−27 g各表示什么意思？",
                "steps": [
                    "比标准质量多65 g用+65 g表示，比标准质量少30 g用−30 g表示。",
                    "50 g表示这箱橘子的质量比标准质量多50 g；−27 g表示比标准质量少27 g。",
                ],
                "result": "先确定标准，再用正负号表示相对标准的两个方向。",
            },
            {"type": "visualization", "renderer": "opposite_quantities", "params": {"title": "相对标准质量的偏差", "scene": "deviation"}},
        ])

    rational_intro = pages.get("1.2.1-p07-a")
    if rational_intro:
        replace_page_blocks(rational_intro, [
            textbook_text("在小学阶段和上一节中，我们认识了很多数。回想一下，到目前为止，我们认识了哪些数？"),
            textbook_text("我们学习过正整数，如1，2，3，…；0；负整数，如−1，−2，−3，…。正整数、0、负整数统称为整数。"),
            textbook_text("我们还学习过正分数，如1/2，2/3，1又5/7，0.1，5.32，0.3循环，…；负分数，如−5/2，−2/3，−1/7，−0.5，−150.5，…。它们都是分数。"),
            textbook_text("事实上，有限小数和无限循环小数都可以化为分数，因此它们也可以看成分数。"),
        ])

    integer_fraction = pages.get("1.2.1-p07-b")
    if integer_fraction:
        replace_page_blocks(integer_fraction, [
            textbook_text("进一步地，正整数可以写成正分数的形式，例如2=2/1；负整数可以写成负分数的形式，例如−3=−3/1；0也可以写成分数的形式0/1。这样，整数可以写成分数的形式。"),
            {"type": "explanation", "text": "给任何整数补上分母1，它的大小没有改变，却显露出统一的分数形式。"},
            {"type": "visualization", "renderer": "integer_to_fraction", "params": {"title": "整数写成分数形式"}},
            {"type": "conclusion", "text": "整数不是分数之外的另一套数；它们都能写成分母不为0的分数形式。"},
        ])

    concept = pages.get("1.2.1-p07-c")
    if concept:
        replace_page_blocks(concept, [
            textbook_text("可以写成分数形式的数称为有理数（rational number）。其中，可以写成正分数形式的数为正有理数，可以写成负分数形式的数为负有理数。"),
            textbook_text("这样，引入负数后，我们对数的认识就扩大到了有理数范围。"),
            {"type": "explanation", "text": "判断一个数是否为有理数，关键不是它眼前写成整数、小数还是分数，而是它能否写成两个整数之比，且分母不为0。"},
        ])

    rational_example = pages.get("1.2.1-p07-d")
    if rational_example:
        replace_page_blocks(rational_example, [
            {
                "type": "worked_example",
                "label": "例1",
                "statement": "指出下列各数中的正有理数、负有理数，并分别指出其中的正整数、负整数：13，4.3，−3/8，8.5%，−30，−12%，1/9，−7.5，20，−60，1.2循环。",
                "steps": [
                    "正有理数：13，4.3，8.5%，1/9，20，1.2循环；其中正整数有13，20。",
                    "负有理数：−3/8，−30，−12%，−7.5，−60；其中负整数有−30，−60。",
                ],
                "result": "先按符号辨认正、负，再判断其中哪些本身是整数。",
            },
        ])


def process_course(path: Path) -> tuple[int, int]:
    course = json.loads(path.read_text(encoding="utf-8"))
    converted = 0
    for _, _, page in iter_pages(course):
        converted += convert_excerpts(page)
    if course.get("textbook", {}).get("id") == "pep-math-7-1":
        curate_pep_7_1(course)
    remaining = sum(
        block.get("type") == "source_excerpt"
        for _, _, page in iter_pages(course)
        for block in page.get("blocks", [])
    )
    if remaining:
        raise SystemExit(f"{path}: {remaining} source_excerpt blocks remain")
    path.write_text(json.dumps(course, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return converted, remaining


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    args = parser.parse_args()
    total = 0
    for path in sorted(args.source_root.glob("pep-math-*/course.json")):
        converted, _ = process_course(path)
        total += converted
        print(f"{path.parent.name}: converted {converted} textbook excerpts to native content")
    print(f"converted total: {total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
