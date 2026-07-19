#!/usr/bin/env python3
"""Generate six PEP junior-high mathematics course packs from the textbook PDFs.

The generator treats the textbook as the source of truth:
- printed-page order is preserved;
- every lesson paragraph comes from the mapped PDF page;
- every generated page carries a verbatim source anchor;
- long printed pages are split into consecutive app pages rather than compressed;
- visualizations are declared only where they support the current textbook topic.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import re
import unicodedata
from typing import Iterable

import fitz


MATH_GLYPH_MAP = str.maketrans({
    # Lower-case mathematical italic glyphs used by the PDFs.
    "犪": "a", "犫": "b", "犮": "c", "犱": "d", "犲": "e", "犳": "f",
    "犵": "g", "犺": "h", "犻": "i", "犼": "j", "犽": "k", "犾": "l",
    "犿": "m", "狀": "n", "狅": "o", "狆": "p", "狇": "q", "狉": "r",
    "狊": "s", "狋": "t", "狌": "u", "狏": "v", "狑": "w", "狓": "x",
    "狔": "y", "狕": "z",
    # Upper-case mathematical italic glyphs.
    "犃": "A", "犅": "B", "犆": "C", "犇": "D", "犈": "E", "犉": "F",
    "犌": "G", "犎": "H", "犐": "I", "犑": "J", "犓": "K", "犔": "L",
    "犕": "M", "犖": "N", "犗": "O", "犘": "P", "犙": "Q", "犚": "R",
    "犛": "S", "犜": "T", "犝": "U", "犞": "V", "犠": "W", "犡": "X",
    "犢": "Y", "犣": "Z", "烅": "", "烄": "", "烍": "", "烌": "", "烎": "",
    "烐": "", "烏": "", "烑": "", "烉": "", "烇": "", "烋": "",
    "熿": "", "燀": "", "燄": "", "燅": "",
    "槡": "√", "Ą": "′", "Ă": "′", "č": "(", "Ď": ")", "烆": "",
    "": ".", "": "-", "": "-", "": "-", "": "-",
    "": "", "": "", "": "", "": "",
    "": "", "": "", "": "", "": "",
    "": "", "": "", "": "", "": "",
    "": "", "": "", "": "", "": "",
    "": "", "": "", "": "",
})

WATERMARKS = (
    "仅供个人学习使用，未经授权不得另做他用",
    "仅供个人学习使用",
    "未经授权不得另做他用",
)

PROMPT_PREFIXES = (
    "思考", "探究", "问题", "观察", "猜想", "讨论", "归纳", "想一想",
    "做一做", "试一试", "议一议", "填空", "说一说", "看下面的问题",
)

EXERCISE_PREFIXES = ("练习", "习题", "复习题", "数学活动")


@dataclass(frozen=True)
class Entry:
    number: str
    title: str
    start: int
    kind: str = "section"  # section, special, review
    aliases: tuple[str, ...] = ()


@dataclass(frozen=True)
class Chapter:
    number: str
    title: str
    start: int
    entries: tuple[Entry, ...]


@dataclass(frozen=True)
class Book:
    id: str
    title: str
    grade: str
    semester: str
    pdf_name: str
    drive_id: str
    page_count: int
    size: int
    sha256: str
    offset: int
    chapters: tuple[Chapter, ...]


def e(number: str, title: str, start: int, kind: str = "section", *aliases: str) -> Entry:
    return Entry(number, title, start, kind, tuple(aliases))


def c(number: str, title: str, start: int, *entries: Entry) -> Chapter:
    return Chapter(number, title, start, tuple(entries))


BOOKS: tuple[Book, ...] = (
    Book(
        "pep-math-7-1", "义务教育教科书·数学七年级上册", "七年级", "上册",
        "义务教育教科书·数学七年级上册.pdf", "1zPJIoh7Ora3AOMLXfDll8YbAZ8u1v78N",
        202, 12915486, "11b6f1fbfa46eee4158953ef745ae1e6fbe6b9527a1423d55cbe75729e8210b9", 7,
        (
            c("第一章", "有理数", 1,
              e("1.1", "正数和负数", 2),
              e("阅读与思考", "用正负数表示允许偏差", 6, "special"),
              e("1.2", "有理数及其大小比较", 7, "section", "有理数的概念", "数轴", "相反数", "绝对值", "有理数的大小比较"),
              e("图说数学史", "漫漫长路识负数", 18, "special"),
              e("数学活动", "第一章数学活动", 20, "special"),
              e("小结", "第一章小结", 21, "review"),
              e("复习题", "第一章复习题", 22, "review")),
            c("第二章", "有理数的运算", 24,
              e("2.1", "有理数的加法与减法", 25, "section", "有理数的加法", "有理数的减法"),
              e("阅读与思考", "我国古代的正负数加减运算法则——正负术", 37, "special"),
              e("2.2", "有理数的乘法与除法", 38, "section", "有理数的乘法", "有理数的除法"),
              e("探究与发现", "从数系扩充看有理数乘法法则", 50, "special"),
              e("2.3", "有理数的乘方", 51),
              e("数学活动", "第二章数学活动", 58, "special"),
              e("小结", "第二章小结", 59, "review"),
              e("复习题", "第二章复习题", 61, "review")),
            c("第三章", "代数式", 68,
              e("3.1", "列代数式表示数量关系", 69),
              e("阅读与思考", "数字1与字母X的对话", 78, "special"),
              e("3.2", "代数式的值", 79),
              e("数学活动", "第三章数学活动", 83, "special"),
              e("小结", "第三章小结", 85, "review"),
              e("复习题", "第三章复习题", 86, "review")),
            c("第四章", "整式的加减", 88,
              e("4.1", "整式", 89),
              e("4.2", "整式的加法与减法", 95),
              e("信息技术应用", "用电子表格进行数据计算", 104, "special"),
              e("数学活动", "第四章数学活动", 105, "special"),
              e("小结", "第四章小结", 107, "review"),
              e("复习题", "第四章复习题", 108, "review")),
            c("第五章", "一元一次方程", 110,
              e("5.1", "方程", 111, "section", "从算式到方程", "等式的性质"),
              e("5.2", "解一元一次方程", 120),
              e("探究与发现", "无限循环小数化分数", 132, "special"),
              e("5.3", "实际问题与一元一次方程", 133),
              e("阅读与思考", "初步认识数学模型", 142, "special"),
              e("数学活动", "第五章数学活动", 143, "special"),
              e("小结", "第五章小结", 145, "review"),
              e("复习题", "第五章复习题", 146, "review")),
            c("第六章", "几何图形初步", 149,
              e("6.1", "几何图形", 150),
              e("图说数学史", "几何的起源", 160, "special"),
              e("6.2", "直线、射线、线段", 162),
              e("阅读与思考", "长度的测量", 168, "special"),
              e("6.3", "角", 170),
              e("阅读与思考", "角的度量", 180, "special"),
              e("数学活动", "第六章数学活动", 182, "special"),
              e("小结", "第六章小结", 184, "review"),
              e("复习题", "第六章复习题", 185, "review"),
              e("综合与实践", "设计学校田径运动会比赛场地", 189, "special")),
        ),
    ),
    Book(
        "pep-math-7-2", "义务教育教科书·数学七年级下册", "七年级", "下册",
        "义务教育教科书·数学七年级下册.pdf", "15pn2jQjlWO6g4SCK5w2ys3SO8aTE-Kjh",
        202, 29820133, "a58d058dd4f6a0855558a90a24640d9cf3ff8430112fb4d1f434bd881f836210", 7,
        (
            c("第七章", "相交线与平行线", 1,
              e("7.1", "相交线", 2), e("观察与猜想", "看图时的错觉", 10, "special"),
              e("7.2", "平行线", 11), e("7.3", "定义、命题、定理", 22),
              e("7.4", "平移", 26), e("探究与发现", "利用平移设计图案", 30, "special"),
              e("数学活动", "第七章数学活动", 32, "special"), e("小结", "第七章小结", 34, "review"),
              e("复习题", "第七章复习题", 35, "review")),
            c("第八章", "实数", 39,
              e("8.1", "平方根", 40), e("8.2", "立方根", 48), e("8.3", "实数及其简单运算", 52),
              e("阅读与思考", "为什么√2不是有理数", 58, "special"),
              e("数学活动", "第八章数学活动", 59, "special"), e("小结", "第八章小结", 60, "review"),
              e("复习题", "第八章复习题", 61, "review")),
            c("第九章", "平面直角坐标系", 63,
              e("9.1", "用坐标描述平面内点的位置", 64), e("阅读与思考", "用经纬度表示地理位置", 71, "special"),
              e("9.2", "坐标方法的简单应用", 72), e("数学活动", "第九章数学活动", 82, "special"),
              e("小结", "第九章小结", 83, "review"), e("复习题", "第九章复习题", 84, "review")),
            c("第十章", "二元一次方程组", 87,
              e("10.1", "二元一次方程组的概念", 88), e("10.2", "消元——解二元一次方程组", 91),
              e("10.3", "实际问题与二元一次方程组", 101), e("10.4", "三元一次方程组的解法", 107),
              e("图说数学史", "中国古代数学的光辉成就——解多元一次方程组", 112, "special"),
              e("阅读与思考", "中国古代著名的一次不定方程组问题", 114, "special"),
              e("数学活动", "第十章数学活动", 115, "special"), e("小结", "第十章小结", 117, "review"),
              e("复习题", "第十章复习题", 118, "review")),
            c("第十一章", "不等式与不等式组", 120,
              e("11.1", "不等式", 121), e("阅读与思考", "用求差法比较大小", 130, "special"),
              e("11.2", "一元一次不等式", 131), e("11.3", "一元一次不等式组", 138),
              e("数学活动", "第十一章数学活动", 142, "special"), e("小结", "第十一章小结", 143, "review"),
              e("复习题", "第十一章复习题", 144, "review"), e("综合与实践", "低碳生活", 146, "special")),
            c("第十二章", "数据的收集、整理与描述", 150,
              e("12.1", "统计调查", 151), e("探究与发现", "瓶子中有多少粒豆子", 159, "special"),
              e("12.2", "用统计图描述数据", 160), e("信息技术应用", "利用信息技术工具画统计图", 178, "special"),
              e("图说数学史", "统计学点滴", 180, "special"), e("数学活动", "第十二章数学活动", 182, "special"),
              e("小结", "第十二章小结", 183, "review"), e("复习题", "第十二章复习题", 184, "review"),
              e("综合与实践", "白昼时长规律的探究", 188, "special")),
        ),
    ),
    Book(
        "pep-math-8-1", "义务教育教科书·数学八年级上册", "八年级", "上册",
        "义务教育教科书·数学八年级上册.pdf", "1spnH7HoU9wSX_J3HqdClU8an4wsWNTB6",
        182, 10148104, "e21e7094a470f74a5be947e5fa8a9eee57bd6a4deaaaba3dbd09b811590838ed", 7,
        (
            c("第十三章", "三角形", 1,
              e("13.1", "三角形的概念", 2), e("13.2", "与三角形有关的线段", 5),
              e("13.3", "三角形的内角与外角", 11), e("阅读与思考", "为什么要证明", 18, "special"),
              e("数学活动", "第十三章数学活动", 19, "special"), e("小结", "第十三章小结", 20, "review"),
              e("复习题", "第十三章复习题", 21, "review"), e("综合与实践", "确定匀质薄板的重心位置", 23, "special")),
            c("第十四章", "全等三角形", 28,
              e("14.1", "全等三角形及其性质", 29), e("14.2", "三角形全等的判定", 32),
              e("信息技术应用", "探究三角形全等的条件", 46, "special"), e("14.3", "角的平分线", 48),
              e("图说数学史", "公理化方法", 54, "special"), e("数学活动", "第十四章数学活动", 56, "special"),
              e("小结", "第十四章小结", 57, "review"), e("复习题", "第十四章复习题", 58, "review")),
            c("第十五章", "轴对称", 61,
              e("15.1", "图形的轴对称", 62), e("15.2", "画轴对称的图形", 72),
              e("15.3", "等腰三角形", 78), e("探究与发现", "三角形中边与角之间的不等关系", 86, "special"),
              e("数学活动", "第十五章数学活动", 88, "special"), e("小结", "第十五章小结", 90, "review"),
              e("复习题", "第十五章复习题", 91, "review"), e("综合与实践", "最短路径问题", 94, "special")),
            c("第十六章", "整式的乘法", 97,
              e("16.1", "幂的运算", 98), e("16.2", "整式的乘法", 103), e("16.3", "乘法公式", 112),
              e("阅读与思考", "杨辉三角", 118, "special"), e("数学活动", "第十六章数学活动", 119, "special"),
              e("小结", "第十六章小结", 120, "review"), e("复习题", "第十六章复习题", 121, "review")),
            c("第十七章", "因式分解", 123,
              e("17.1", "用提公因式法分解因式", 124), e("17.2", "用公式法分解因式", 128),
              e("阅读与思考", "x²+(p+q)x+pq型式子的因式分解", 133, "special"),
              e("数学活动", "第十七章数学活动", 134, "special"), e("小结", "第十七章小结", 135, "review"),
              e("复习题", "第十七章复习题", 136, "review")),
            c("第十八章", "分式", 137,
              e("18.1", "分式及其基本性质", 138), e("18.2", "分式的乘法与除法", 146),
              e("18.3", "分式的加法与减法", 152), e("阅读与思考", "容器中的水能倒完吗", 157, "special"),
              e("18.4", "整数指数幂", 158), e("18.5", "分式方程", 164),
              e("数学活动", "第十八章数学活动", 170, "special"), e("小结", "第十八章小结", 171, "review"),
              e("复习题", "第十八章复习题", 172, "review")),
        ),
    ),
    Book(
        "pep-math-8-2", "义务教育教科书·数学八年级下册", "八年级", "下册",
        "义务教育教科书·数学八年级下册.pdf", "1lJJzJupEt6FThZDB6WMH8AI09W2fT20D",
        206, 20284130, "4979e490f518912473519aafc2b7e016aa804741ac7efbaa4156d230d9dc8b3c", 7,
        (
            c("第十九章", "二次根式", 1,
              e("19.1", "二次根式及其性质", 2), e("19.2", "二次根式的乘法与除法", 6),
              e("19.3", "二次根式的加法与减法", 13), e("阅读与思考", "海伦-秦九韶公式", 17, "special"),
              e("数学活动", "第十九章数学活动", 18, "special"), e("小结", "第十九章小结", 19, "review"),
              e("复习题", "第十九章复习题", 20, "review")),
            c("第二十章", "勾股定理", 22,
              e("20.1", "勾股定理及其应用", 23), e("阅读与思考", "勾股定理的证明", 32, "special"),
              e("20.2", "勾股定理的逆定理及其应用", 34), e("图说数学史", "数学瑰宝——勾股定理", 39, "special"),
              e("数学活动", "第二十章数学活动", 41, "special"), e("小结", "第二十章小结", 42, "review"),
              e("复习题", "第二十章复习题", 43, "review")),
            c("第二十一章", "四边形", 45,
              e("21.1", "四边形及多边形", 46), e("探究与发现", "用多边形镶嵌平面", 54, "special"),
              e("21.2", "平行四边形", 55), e("21.3", "特殊的平行四边形", 68),
              e("探究与发现", "利用菱形的性质和判定尺规作图", 82, "special"),
              e("数学活动", "第二十一章数学活动", 83, "special"), e("小结", "第二十一章小结", 85, "review"),
              e("复习题", "第二十一章复习题", 86, "review")),
            c("第二十二章", "函数", 89,
              e("22.1", "函数的概念", 90), e("图说数学史", "函数概念的探索之路", 98, "special"),
              e("22.2", "函数的表示", 100), e("数学活动", "第二十二章数学活动", 110, "special"),
              e("小结", "第二十二章小结", 111, "review"), e("复习题", "第二十二章复习题", 112, "review")),
            c("第二十三章", "一次函数", 113,
              e("23.1", "一次函数的概念", 114), e("23.2", "一次函数的图象和性质", 117),
              e("信息技术应用", "探究函数的图象和性质", 125, "special"),
              e("23.3", "一次函数与方程（组）、不等式", 127), e("23.4", "实际问题与一次函数", 131),
              e("数学活动", "第二十三章数学活动", 138, "special"), e("小结", "第二十三章小结", 139, "review"),
              e("复习题", "第二十三章复习题", 140, "review"), e("综合与实践", "音乐与数学", 143, "special")),
            c("第二十四章", "数据的分析", 148,
              e("24.1", "数据的集中趋势", 149), e("信息技术应用", "利用统计软件求统计量", 166, "special"),
              e("24.2", "数据的离散程度", 168), e("24.3", "数据的四分位数", 176),
              e("24.4", "数据的分组", 182), e("阅读与思考", "大数据及其应用", 186, "special"),
              e("数学活动", "第二十四章数学活动", 188, "special"), e("小结", "第二十四章小结", 189, "review"),
              e("复习题", "第二十四章复习题", 190, "review"), e("综合与实践", "学生体质健康调查与分析", 193, "special")),
        ),
    ),
    Book(
        "pep-math-9-1", "义务教育教科书·数学九年级上册", "九年级", "上册",
        "义务教育教科书·数学九年级上册.pdf", "1ArtD3pcWtIeawWPNSAVudUVk1X0hqWKx",
        163, 9609936, "b6d43c0601c07a6a6f68d3be065c863b41529745e739b4496154c90ecbe7140d", 7,
        (
            c("第二十一章", "一元二次方程", 1,
              e("21.1", "一元二次方程", 2), e("21.2", "解一元二次方程", 5),
              e("阅读与思考", "黄金分割数", 18, "special"), e("21.3", "实际问题与一元二次方程", 19),
              e("数学活动", "第二十一章数学活动", 23, "special"), e("小结", "第二十一章小结", 24, "review"),
              e("复习题21", "第二十一章复习题", 25, "review")),
            c("第二十二章", "二次函数", 27,
              e("22.1", "二次函数的图象和性质", 28), e("22.2", "二次函数与一元二次方程", 43),
              e("信息技术应用", "探索二次函数的性质", 48, "special"), e("22.3", "实际问题与二次函数", 49),
              e("阅读与思考", "推测滑行距离与滑行时间的关系", 52, "special"),
              e("数学活动", "第二十二章数学活动", 54, "special"), e("小结", "第二十二章小结", 55, "review"),
              e("复习题22", "第二十二章复习题", 56, "review")),
            c("第二十三章", "旋转", 58,
              e("23.1", "图形的旋转", 59), e("23.2", "中心对称", 64),
              e("信息技术应用", "探索旋转的性质", 71, "special"), e("23.3", "课题学习 图案设计", 72),
              e("阅读与思考", "旋转对称", 73, "special"), e("数学活动", "第二十三章数学活动", 74, "special"),
              e("小结", "第二十三章小结", 75, "review"), e("复习题23", "第二十三章复习题", 76, "review")),
            c("第二十四章", "圆", 78,
              e("24.1", "圆的有关性质", 79), e("24.2", "点和圆、直线和圆的位置关系", 92),
              e("实验与探究", "圆和圆的位置关系", 103, "special"), e("24.3", "正多边形和圆", 105),
              e("阅读与思考", "圆周率π", 109, "special"), e("24.4", "弧长和扇形面积", 111),
              e("实验与探究", "设计跑道", 117, "special"), e("数学活动", "第二十四章数学活动", 118, "special"),
              e("小结", "第二十四章小结", 121, "review"), e("复习题24", "第二十四章复习题", 122, "review")),
            c("第二十五章", "概率初步", 126,
              e("25.1", "随机事件与概率", 127), e("25.2", "用列举法求概率", 136),
              e("阅读与思考", "概率与中奖", 141, "special"), e("25.3", "用频率估计概率", 142),
              e("实验与探究", "π的估计", 149, "special"), e("数学活动", "第二十五章数学活动", 150, "special"),
              e("小结", "第二十五章小结", 151, "review"), e("复习题25", "第二十五章复习题", 152, "review")),
        ),
    ),
    Book(
        "pep-math-9-2", "义务教育教科书·数学九年级下册", "九年级", "下册",
        "义务教育教科书·数学九年级下册.pdf", "12p2aGGugc_EvrO-Kqb17MHzMAqXvHTBn",
        122, 11303872, "a71d30eb32b0e43737c88ada99a76ae6b5959050dc53c491b5206bae2d31b0ce", 7,
        (
            c("第二十六章", "反比例函数", 1,
              e("26.1", "反比例函数", 2), e("信息技术应用", "探索反比例函数的性质", 10, "special"),
              e("26.2", "实际问题与反比例函数", 12), e("阅读与思考", "生活中的反比例关系", 17, "special"),
              e("数学活动", "第二十六章数学活动", 19, "special"), e("小结", "第二十六章小结", 20, "review"),
              e("复习题26", "第二十六章复习题", 21, "review")),
            c("第二十七章", "相似", 23,
              e("27.1", "图形的相似", 24), e("27.2", "相似三角形", 29),
              e("观察与猜想", "奇妙的分形图形", 45, "special"), e("27.3", "位似", 47),
              e("信息技术应用", "探索位似的性质", 53, "special"), e("数学活动", "第二十七章数学活动", 54, "special"),
              e("小结", "第二十七章小结", 56, "review"), e("复习题27", "第二十七章复习题", 57, "review")),
            c("第二十八章", "锐角三角函数", 60,
              e("28.1", "锐角三角函数", 61), e("阅读与思考", "一张古老的三角函数表", 70, "special"),
              e("28.2", "解直角三角形及其应用", 72), e("阅读与思考", "山坡的高度", 80, "special"),
              e("数学活动", "第二十八章数学活动", 81, "special"), e("小结", "第二十八章小结", 83, "review"),
              e("复习题28", "第二十八章复习题", 84, "review")),
            c("第二十九章", "投影与视图", 86,
              e("29.1", "投影", 87), e("29.2", "三视图", 94),
              e("阅读与思考", "视图的产生与应用", 104, "special"), e("29.3", "课题学习 制作立体模型", 105),
              e("数学活动", "第二十九章数学活动", 107, "special"), e("小结", "第二十九章小结", 108, "review"),
              e("复习题29", "第二十九章复习题", 109, "review")),
        ),
    ),
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def clean_text(value: str) -> str:
    value = value.translate(MATH_GLYPH_MAP)
    for watermark in WATERMARKS:
        value = value.replace(watermark, "")
    for garbage in (
        "᫞឴ˁ Ꮶ", "᫞឴ˁᏦ", "ܬ˷ ڌ", "ܬ˷ڌ", "፬Ռᤂၹ",
        "wnj ቦᅼគፇ ڏ", "̄njڀᮏˁ Ꮶ", "ቃˁԧဘ",
    ):
        value = value.replace(garbage, "")
    value = unicodedata.normalize("NFKC", value)
    value = value.replace("\ue010", ".").replace("\ue011", "-")
    value = value.replace("\u00ad", "").replace("\ufeff", "")
    # Remove private-use/control glyph debris and unrelated decorative-font scripts. The
    # textbooks use Chinese, ASCII variables and a small set of Greek mathematical letters.
    cleaned_chars: list[str] = []
    for ch in value:
        category = unicodedata.category(ch)
        if category in {"Cc", "Co", "Mn"}:
            continue
        if category.startswith("L"):
            if ch.isascii() or "\u4e00" <= ch <= "\u9fff" or "\u0370" <= ch <= "\u03ff":
                cleaned_chars.append(ch)
            continue
        cleaned_chars.append(ch)
    value = "".join(cleaned_chars)
    allowed_non_ascii = set("，。；：、？！“”‘’（）《》【】…·—–−×÷√παβγθΔΩρ∠△≠≤≥≈％°²³₁₂₃₄₅₆₇₈₉₀→←↓∥⊥∵∴∞∈∉∪∩∅⊙⌒′∶±∽≌")
    value = "".join(
        ch for ch in value
        if ch.isascii() or "\u4e00" <= ch <= "\u9fff" or "\u0370" <= ch <= "\u03ff" or ch in allowed_non_ascii
    )
    lines = []
    for raw_line in value.splitlines():
        line = re.sub(r"[ \t]+", " ", raw_line).strip()
        if not line or line in WATERMARKS:
            continue
        # Known PDF extraction debris, usually decorative column markers.
        if re.fullmatch(r"[\W_]{1,8}", line) and not any(ch in line for ch in "=+-×÷√∠△π"):
            continue
        # Short decorative glyph runs from embedded textbook fonts are not lesson text.
        if len(line) <= 8 and not re.search(r"[\u4e00-\u9fffA-Za-z=+\-×÷√∠△π]", line):
            continue
        line = re.sub(r"^[^\u4e00-\u9fffA-Za-z0-9=+\-×÷√∠△π]+(?=\d)", "", line)
        line = re.sub(r"^[^\u4e00-\u9fffA-Za-z0-9]+(?=[\u4e00-\u9fff])", "", line)
        line = re.sub(r"^[αβγδθρΩπ]+[?？˸:：]*", "", line)
        if not re.search(r"[\u4e00-\u9fff0-9]", line) and not re.search(r"[=+\-×÷√∠△π<>]", line):
            # Stand-alone Latin glyph runs are generally vector-diagram labels or corrupted
            # decorative fonts. The surrounding textbook paragraph still carries the meaning.
            continue
        cjk_count = sum("\u4e00" <= ch <= "\u9fff" for ch in line)
        if cjk_count <= 1 and "图" in line and len(line) < 100:
            continue
        if cjk_count == 0 and len(line) > 60:
            continue
        # Vector-drawing export placeholders such as ``3D=`` are not textbook prose or
        # mathematical formulas. They otherwise survive because the equals sign is meaningful.
        if re.fullmatch(r"\d+[A-Za-z]=", line):
            continue
        lines.append(line)

    # Join ordinary wrapped lines while preserving explicit textbook stage headings.
    joined: list[str] = []
    heading_pattern = re.compile(
        r"^(?:\d+[.．]\d+(?:[.．]\d+)?|例\s*\d*|问题\s*\d*|练习|习题|复习题|"
        r"数学活动|小结|阅读与思考|探究与发现|观察与猜想|信息技术应用|图说数学史|实验与探究|综合与实践)"
    )
    for line in lines:
        if not joined:
            joined.append(line)
            continue
        previous = joined[-1]
        if heading_pattern.match(line) or previous.endswith(("。", "!", "?", ";", ":", "．", "！", "？", "；", "：")):
            joined.append(line)
        else:
            joined[-1] = previous + line
    text = "\n".join(joined)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def is_running_header_or_footer(text: str, y0: float, y1: float, page_height: float) -> bool:
    compact = re.sub(r"\s+", "", clean_text(text))
    if not compact:
        return True
    if y0 > page_height - 52:
        return True
    if re.fullmatch(r"[0-9一二三四五六七八九十百]+", compact):
        return True
    if re.fullmatch(r"第[一二三四五六七八九十百]+章.+", compact) and y0 > page_height * 0.85:
        return True
    return False


def is_diagram_label_block(text: str, width: float, height: float) -> bool:
    """Discard diagram labels that PDF extraction flattens into fake prose.

    These blocks are still represented by a dedicated visualization and by the preceding
    textbook sentence. Keeping the flattened labels (for example a classification tree read
    as one string) would both distort the textbook sequence and create crowded app pages.
    """
    compact = re.sub(r"\s+", "", text)
    if not compact:
        return True
    cjk_count = sum("\u4e00" <= ch <= "\u9fff" for ch in compact)
    has_sentence_punctuation = any(ch in compact for ch in "。！？；：,.!?;:")
    if width < 250 and height > 24 and cjk_count >= 4 and not has_sentence_punctuation:
        return True
    if height < 18 and width < 260 and cjk_count <= 2 and len(compact) < 32:
        return True
    return False


def extract_page_blocks(document: fitz.Document, printed_page: int, offset: int) -> list[str]:
    pdf_index = printed_page - 1 + offset
    page = document.load_page(pdf_index)
    blocks = []
    for block in sorted(page.get_text("blocks"), key=lambda item: (round(item[1] / 4), item[0])):
        x0, y0, x1, y1, raw = block[:5]
        if is_running_header_or_footer(raw, y0, y1, page.rect.height):
            continue
        text = clean_text(raw)
        if text and is_diagram_label_block(text, x1 - x0, y1 - y0):
            continue
        if y1 > page.rect.height - 18:
            lines = text.splitlines()
            if lines and re.fullmatch(r"[0-9０-９]+", lines[-1].strip()):
                text = "\n".join(lines[:-1]).strip()
        if text:
            blocks.append(text)
    return blocks


def split_sentences(text: str, max_chars: int = 640) -> list[str]:
    if len(text) <= max_chars:
        return [text]
    pieces = re.split(r"(?<=[。！？；])", text)
    chunks: list[str] = []
    current = ""
    for piece in pieces:
        piece = piece.strip()
        if not piece:
            continue
        if current and len(current) + len(piece) > max_chars:
            chunks.append(current)
            current = piece
        elif len(piece) > max_chars:
            if current:
                chunks.append(current)
                current = ""
            for index in range(0, len(piece), max_chars):
                chunks.append(piece[index:index + max_chars])
        else:
            current += piece
    if current:
        chunks.append(current)
    return chunks


def split_blocks_for_app(blocks: list[str], max_chars: int = 720) -> list[list[str]]:
    expanded: list[str] = []
    for block in blocks:
        expanded.extend(split_sentences(block, max_chars=max_chars))
    pages: list[list[str]] = []
    current: list[str] = []
    current_len = 0
    for block in expanded:
        # Headings/prompts begin a new app page when the current one already has substantial text.
        heading = detect_heading(block)
        if current and ((heading and current_len > 260) or current_len + len(block) > max_chars):
            pages.append(current)
            current = []
            current_len = 0
        current.append(block)
        current_len += len(block)
    if current:
        pages.append(current)
    # A formula fragment at the bottom of a printed page must stay with the preceding explanation;
    # otherwise the app would create a nearly empty swipe page.
    merged: list[list[str]] = []
    for page in pages:
        substance = sum(sum(ch.isascii() and ch.isalnum() or "\u4e00" <= ch <= "\u9fff" for ch in block) for block in page)
        if merged and substance < 70 and not any(detect_heading(block) for block in page):
            merged[-1].extend(page)
        else:
            merged.append(page)
    return merged or [["本页以教材原页为准。"]]


def detect_heading(text: str) -> str | None:
    first = text.splitlines()[0].strip()
    numbered = re.match(r"^(\d+[.．]\d+(?:[.．]\d+)?)\s*(.*)$", first)
    if numbered:
        rest = numbered.group(2).strip()
        if rest:
            # Text extraction may glue the following paragraph to the heading.
            for marker in ("在小学", "在实际", "我们", "一般地", "先来", "从古", "数的", "方程", "函数"):
                position = rest.find(marker)
                if position >= 2:
                    rest = rest[:position]
                    break
            rest = re.split(r"[。！？；]", rest, maxsplit=1)[0].strip()
        return rest[:36] or numbered.group(1)
    stage = re.match(
        r"^(例\s*\d*|问题\s*\d*|练习|习题\s*\d*|复习题\s*\d*|数学活动|小结|阅读与思考|"
        r"探究与发现|观察与猜想|信息技术应用|图说数学史|实验与探究|综合与实践)(.*)$",
        first,
    )
    if stage:
        tail = re.split(r"[。！？；]", stage.group(2), maxsplit=1)[0].strip(" :：")
        return (stage.group(1) + (" " + tail[:28] if tail else "")).strip()
    return None


def block_type(text: str) -> str:
    first = text.splitlines()[0].strip()
    if first.startswith(EXERCISE_PREFIXES) or re.match(r"^\d+[.、](?!\d)\s*", first):
        return "exercise"
    if first.startswith(PROMPT_PREFIXES):
        return "prompt"
    if re.match(r"^例\s*\d*", first):
        return "worked_example"
    return "textbook_text"


def anchor_for(texts: Iterable[str]) -> str:
    # Anchors must come from one original PDF text block. Merging adjacent blocks can create a
    # phrase that never appears contiguously in the PDF reading order.
    def substance(value: str) -> int:
        return sum(ch.isascii() and ch.isalnum() or "\u4e00" <= ch <= "\u9fff" for ch in value)

    fallback = ""
    for original in texts:
        compact = re.sub(r"\s+", "", original)
        if substance(compact) > substance(fallback):
            fallback = compact
        for sentence in re.split(r"[。！？；]", compact):
            sentence = sentence.strip()
            if substance(sentence) >= 12:
                return sentence[:72]
    return fallback[:72]


def slug(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).lower()
    value = re.sub(r"[^a-z0-9\u4e00-\u9fff]+", "-", value).strip("-")
    return value or "page"


def visualization_for(book: Book, chapter: Chapter, entry: Entry, page_no: int, chunk_index: int, text: str) -> tuple[str | None, dict[str, object]]:
    if chunk_index != 0:
        return None, {}
    current = text[:900]
    topic = f"{chapter.title} {entry.title}"
    if book.id == "pep-math-7-1" and entry.number == "1.2" and page_no == 7:
        return "rational_definition_flow", {}

    # The textbook chapter/section heading is the strongest semantic signal. It must win over
    # incidental comparison words in an introduction (for example, the axis-symmetry chapter
    # mentions translation, and the quadratic-function introduction mentions equations).
    topic_mappings: list[tuple[tuple[str, ...], str, dict[str, object]]] = [
        (("反比例函数",), "function_graph", {"mode": "inverse"}),
        (("二次函数",), "function_graph", {"mode": "quadratic"}),
        (("一次函数",), "function_graph", {"mode": "linear"}),
        (("轴对称",), "axis_symmetry", {}),
        (("旋转",), "rotation", {}),
        (("相似", "位似"), "similarity", {}),
        (("勾股定理",), "pythagorean", {}),
        (("锐角三角函数",), "right_triangle", {}),
        (("投影与视图", "投影", "三视图"), "projection", {}),
        (("圆",), "circle", {}),
        (("平行四边形", "四边形", "矩形", "菱形", "正方形"), "quadrilateral", {}),
        (("全等三角形",), "congruent_triangles", {}),
        (("平移",), "translation", {}),
        (("平面直角坐标系",), "coordinate_plane", {}),
        (("相交线",), "intersecting_lines", {}),
        (("平行线",), "parallel_lines", {}),
        (("概率",), "probability", {}),
        (("数据", "统计"), "statistics", {}),
        (("二元一次方程组",), "equation_system", {}),
        (("不等式",), "inequality_number_line", {}),
        (("一元一次方程", "一元二次方程"), "equation_balance", {}),
        (("三角形",), "triangle", {}),
        (("整式", "因式分解", "乘法公式", "分式", "二次根式", "代数式"), "algebra_process", {}),
    ]
    for needles, renderer, params in topic_mappings:
        if any(needle in topic for needle in needles):
            return renderer, params

    mappings: list[tuple[tuple[str, ...], str, dict[str, object]]] = [
        (("数轴", "原点、正方向和单位长度"), "number_line", {"min": -5, "max": 5, "draggable": True}),
        (("相反数", "互为相反数"), "opposite_numbers", {}), (("绝对值", "到原点的距离"), "absolute_value", {}),
        (("两个负数", "左边的数小于右边的数"), "number_comparison", {}),
        (("具有相反意义", "正数和负数"), "opposite_quantities", {}),
        (("有理数的加法", "加法法则"), "addition_process", {}), (("有理数的减法", "减去一个数"), "subtraction_transform", {}),
        (("有理数的乘法", "乘法法则"), "multiplication_sign", {}), (("有理数的除法", "除以一个数"), "division_transform", {}),
        (("有理数的乘方", "幂的运算", "负数的奇次幂"), "power_process", {}),
        (("二元一次方程组", "消元"), "equation_system", {}), (("不等式", "解集"), "inequality_number_line", {}),
        (("平面直角坐标系", "横轴", "纵轴"), "coordinate_plane", {}),
        (("轴对称", "对称轴"), "axis_symmetry", {}), (("旋转", "中心对称"), "rotation", {}),
        (("相似", "位似"), "similarity", {}), (("勾股定理",), "pythagorean", {}),
        (("锐角三角函数", "正弦", "余弦", "正切"), "right_triangle", {}),
        (("投影", "三视图"), "projection", {}), (("圆", "弧长", "扇形"), "circle", {}),
        (("平行四边形", "矩形", "菱形", "正方形", "四边形"), "quadrilateral", {}),
        (("全等三角形", "全等"), "congruent_triangles", {}), (("平移",), "translation", {}),
        (("对顶角", "邻补角", "相交线"), "intersecting_lines", {}), (("平行线", "同位角"), "parallel_lines", {}),
        (("一次函数",), "function_graph", {"mode": "linear"}), (("二次函数",), "function_graph", {"mode": "quadratic"}),
        (("反比例函数",), "function_graph", {"mode": "inverse"}), (("函数", "变量"), "function_relation", {}),
        (("统计", "数据", "频数分布"), "statistics", {}), (("概率", "随机事件"), "probability", {}),
        (("三角形", "内角", "外角"), "triangle", {}),
        (("方程", "等式的性质"), "equation_balance", {}),
        (("整式", "因式分解", "乘法公式", "分式", "二次根式", "代数式"), "algebra_process", {}),
    ]
    for needles, renderer, params in mappings:
        if any(needle in current for needle in needles):
            return renderer, params
    return None, {}


def page_blocks_json(texts: list[str], renderer: str | None, params: dict[str, object]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for text in texts:
        kind = block_type(text)
        if kind == "worked_example":
            result.append({"type": "worked_example", "statement": text, "steps": []})
        elif kind == "exercise":
            result.append({"type": "exercise", "number": "", "stem": text, "answerMode": "text"})
        elif kind == "prompt":
            result.append({"type": "prompt", "text": text})
        else:
            result.append({"type": "textbook_text", "text": text})
    if renderer:
        result.append({"type": "visualization", "renderer": renderer, "params": params})
    return result



def rational_concept_pages(book: Book, chapter: Chapter, entry: Entry, printed_page: int) -> list[dict[str, object]] | None:
    """Curated page split for textbook page 7, preserving the textbook's actual stages."""
    if not (book.id == "pep-math-7-1" and chapter.number == "第一章" and entry.number == "1.2" and printed_page == 7):
        return None
    prefix = f"{book.id}-{slug(chapter.number)}-{slug(entry.number)}-p007"
    return [
        {
            "id": f"{prefix}-01",
            "type": "lesson",
            "title": "回顾整数",
            "sourcePage": 7,
            "sourceAnchors": [{"page": 7, "text": "在小学阶段和上一节中，我们认识了很多数。回想一下，到目前为止，我们认识了哪些数"}],
            "blocks": [
                {"type": "textbook_text", "text": "引入负数后，数的范围就扩大了。与小学对数的学习类似，我们进一步在这个范围内学习数的表示以及大小比较等问题。"},
                {"type": "prompt", "text": "在小学阶段和上一节中，我们认识了很多数。回想一下，到目前为止，我们认识了哪些数？"},
                {"type": "textbook_text", "text": "我们学习过正整数，如1，2，3，…；0；负整数，如−1，−2，−3，…。正整数、0、负整数统称为整数。"},
            ],
        },
        {
            "id": f"{prefix}-02",
            "type": "lesson",
            "title": "回顾分数",
            "sourcePage": 7,
            "sourceAnchors": [{"page": 7, "text": "事实上有限小数和无限循环小数都可以化为分数因此它们也可以看成分数"}],
            "blocks": [
                {"type": "textbook_text", "text": "我们还学习过正分数，如"},
                {"type": "textbook_text", "text": "负分数，如"},
                {"type": "textbook_text", "text": "它们都是分数。"},
                {"type": "formula", "expression": "1/2，2/3，15/7，0.1，5.32，0.3̇；−5/2，−2/3，−1/7，−0.5，−150.5"},
                {"type": "textbook_text", "text": "事实上，有限小数和无限循环小数都可以化为分数，因此它们也可以看成分数。"},
            ],
        },
        {
            "id": f"{prefix}-03",
            "type": "lesson",
            "title": "整数写成分数形式",
            "sourcePage": 7,
            "sourceAnchors": [{"page": 7, "text": "进一步地正整数可以写成正分数的形式"}],
            "blocks": [
                {"type": "textbook_text", "text": "进一步地，正整数可以写成正分数的形式，例如"},
                {"type": "formula", "expression": "2 = 2/1"},
                {"type": "textbook_text", "text": "负整数可以写成负分数的形式，例如"},
                {"type": "formula", "expression": "−3 = −3/1"},
                {"type": "textbook_text", "text": "0也可以写成分数的形式"},
                {"type": "formula", "expression": "0 = 0/1"},
                {"type": "textbook_text", "text": "这样，整数可以写成分数的形式。"},
                {"type": "visualization", "renderer": "rational_definition_flow", "params": {}},
            ],
        },
        {
            "id": f"{prefix}-04",
            "type": "lesson",
            "title": "有理数",
            "sourcePage": 7,
            "sourceAnchors": [{"page": 7, "text": "可以写成分数形式的数称为有理数"}],
            "blocks": [
                {"type": "textbook_text", "text": "可以写成分数形式的数称为有理数（rational number）。其中，可以写成正分数形式的数为正有理数，可以写成负分数形式的数为负有理数。"},
                {"type": "textbook_text", "text": "这样，引入负数后，我们对数的认识就扩大到了有理数范围。"},
            ],
        },
        {
            "id": f"{prefix}-05",
            "type": "lesson",
            "title": "例1 辨认有理数",
            "sourcePage": 7,
            "sourceAnchors": [{"page": 7, "text": "指出下列各数中的正有理数负有理数并分别指出其中的正整数负整数"}],
            "blocks": [
                {"type": "worked_example", "statement": "例1 指出下列各数中的正有理数、负有理数，并分别指出其中的正整数、负整数：", "steps": []},
                {"type": "formula", "expression": "13，4.3，−3/8，8.5%，−30，−12%，1/9，−7.5，20，−60，1.2̇"},
            ],
        },
    ]


def generate_book(book: Book, pdf_path: Path, output_dir: Path) -> dict[str, object]:
    if not pdf_path.is_file():
        raise SystemExit(f"missing PDF: {pdf_path}")
    if pdf_path.stat().st_size != book.size:
        raise SystemExit(f"{book.id}: PDF size mismatch")
    if sha256(pdf_path) != book.sha256:
        raise SystemExit(f"{book.id}: PDF SHA-256 mismatch")

    document = fitz.open(pdf_path)
    try:
        if document.page_count != book.page_count:
            raise SystemExit(f"{book.id}: page count mismatch")
        chapters_json: list[dict[str, object]] = []
        total_pages = 0
        for chapter_index, chapter in enumerate(book.chapters):
            next_chapter_start = book.chapters[chapter_index + 1].start if chapter_index + 1 < len(book.chapters) else document.page_count - book.offset + 1
            entries = list(chapter.entries)
            sections_json: list[dict[str, object]] = []
            review_pages: list[dict[str, object]] = []

            # Preserve the chapter introduction as its own section.
            intro_end = entries[0].start - 1 if entries else next_chapter_start - 1
            all_entries: list[tuple[Entry, int]] = []
            if intro_end >= chapter.start:
                all_entries.append((Entry("章引言", f"{chapter.title}章引言", chapter.start, "special"), intro_end))
            for entry_index, entry in enumerate(entries):
                end = (entries[entry_index + 1].start - 1) if entry_index + 1 < len(entries) else (next_chapter_start - 1)
                all_entries.append((entry, max(entry.start, end)))

            for entry, end in all_entries:
                pages_json: list[dict[str, object]] = []
                sequence = 1
                for printed_page in range(entry.start, end + 1):
                    curated_pages = rational_concept_pages(book, chapter, entry, printed_page)
                    if curated_pages is not None:
                        pages_json.extend(curated_pages)
                        sequence += len(curated_pages)
                        total_pages += len(curated_pages)
                        continue
                    blocks = extract_page_blocks(document, printed_page, book.offset)
                    if not blocks:
                        continue
                    app_chunks = split_blocks_for_app(blocks)
                    for chunk_index, texts in enumerate(app_chunks):
                        if max((sum(ch.isascii() and ch.isalnum() or "\u4e00" <= ch <= "\u9fff" for ch in text) for text in texts), default=0) < 8:
                            continue
                        heading = next((detect_heading(text) for text in texts if detect_heading(text)), None)
                        if heading:
                            title = heading
                        elif len(app_chunks) == 1:
                            title = entry.title
                        else:
                            title = f"{entry.title}（{sequence}）"
                        renderer, params = visualization_for(book, chapter, entry, printed_page, chunk_index, "\n".join(texts))
                        page_id = f"{book.id}-{slug(chapter.number)}-{slug(entry.number)}-p{printed_page:03d}-{chunk_index + 1:02d}"
                        anchor = anchor_for(texts)
                        if len(re.sub(r"\s+", "", anchor)) < 8:
                            continue
                        page_json = {
                            "id": page_id,
                            "type": "lesson" if entry.kind not in {"review"} else "summary",
                            "title": title,
                            "sourcePage": printed_page,
                            "sourceAnchors": [{"page": printed_page, "text": anchor}],
                            "blocks": page_blocks_json(texts, renderer, params),
                        }
                        pages_json.append(page_json)
                        sequence += 1
                        total_pages += 1
                section_json = {
                    "id": f"{book.id}-{slug(chapter.number)}-{slug(entry.number)}",
                    "number": entry.number,
                    "title": entry.title,
                    "aliases": list(entry.aliases),
                    "pages": pages_json,
                }
                if entry.kind == "review":
                    review_pages.extend(pages_json)
                else:
                    sections_json.append(section_json)

            chapter_json: dict[str, object] = {
                "id": f"{book.id}-{slug(chapter.number)}",
                "number": chapter.number,
                "title": chapter.title,
                "aliases": [f"{chapter.number} {chapter.title}"],
                "sections": sections_json,
            }
            if review_pages:
                chapter_json["review"] = {
                    "id": f"{book.id}-{slug(chapter.number)}-review",
                    "title": f"{chapter.title}小结与复习",
                    "aliases": [f"{chapter.title}小结", f"{chapter.title}复习题"],
                    "pages": review_pages,
                }
            chapters_json.append(chapter_json)

        payload = {
            "schemaVersion": 1,
            "textbook": {
                "id": book.id,
                "title": book.title,
                "publisher": "人民教育出版社",
                "edition": "人教版",
                "grade": book.grade,
                "semester": book.semester,
                "subject": "数学",
                "pdf": {
                    "path": "assets/textbook.pdf",
                    "url": f"https://drive.google.com/file/d/{book.drive_id}/view",
                    "size": book.size,
                    "sha256": book.sha256,
                    "pageCount": book.page_count,
                    "pageIndexOffset": book.offset,
                },
            },
            "chapters": chapters_json,
        }
        book_dir = output_dir / book.id
        book_dir.mkdir(parents=True, exist_ok=True)
        target = book_dir / "course.json"
        target.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{book.id}: {len(chapters_json)} chapters, {total_pages} app pages -> {target}")
        return payload
    finally:
        document.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pdf-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=Path("tools/course-content"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    summary = []
    for book in BOOKS:
        payload = generate_book(book, args.pdf_dir / book.pdf_name, args.output_dir)
        summary.append({
            "id": book.id,
            "title": book.title,
            "chapters": len(payload["chapters"]),
            "coursePath": f"{book.id}/course.json",
            "pdf": {
                "fileId": book.drive_id,
                "size": book.size,
                "sha256": book.sha256,
                "pageCount": book.page_count,
                "pageIndexOffset": book.offset,
            },
        })
    (args.output_dir / "math-textbooks.json").write_text(
        json.dumps({"schemaVersion": 1, "textbooks": summary}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
