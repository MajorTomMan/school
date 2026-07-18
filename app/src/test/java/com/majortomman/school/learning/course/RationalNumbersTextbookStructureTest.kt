package com.majortomman.school.learning.course

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RationalNumbersTextbookStructureTest {
    @Test
    fun rationalConceptAliasUsesPagedTextbookCourse() {
        assertTrue(RationalNumbersCourseFactory.supports("有理数的概念"))

        val pages = RationalNumbersCourseFactory.pagesFor("有理数的概念", 7..17)

        assertTrue(pages.isNotEmpty())
        assertTrue(pages.all { it.section == "1.2 有理数及其大小比较" })
        assertTrue(pages.any { it.title == "有理数" })
        assertTrue(pages.any { it.title == "有理数的分类" })
    }

    @Test
    fun firstChapterContainsSummaryAndAllExerciseGroups() {
        val pages = RationalNumbersCourseFactory.pagesFor("有理数", 1..23)

        assertTrue(pages.any { it.section == "第一章 小结" && it.title == "本章知识结构" })
        assertTrue(pages.any { it.id == "chapter-one-exercises-1-2" })
        assertTrue(pages.any { it.id == "chapter-one-exercises-3-4" })
        assertTrue(pages.any { it.id == "chapter-one-exercises-5-8" })
        assertTrue(pages.any { it.id == "chapter-one-exercises-9-11" })
    }

    @Test
    fun secondChapterContainsSummaryAndAllExerciseGroups() {
        val pages = RationalNumbersCourseFactory.pagesFor("有理数的运算", 24..62)

        assertTrue(pages.any { it.section == "第二章 小结" && it.title == "本章知识结构" })
        assertTrue(pages.any { it.id == "chapter-two-exercises-1-3" })
        assertTrue(pages.any { it.id == "chapter-two-exercises-4-6" })
        assertTrue(pages.any { it.id == "chapter-two-exercises-7-8" })
        assertTrue(pages.any { it.id == "chapter-two-exercises-9-12" })
        assertTrue(pages.any { it.id == "chapter-two-exercises-13-15" })
    }

    @Test
    fun summaryAndExercisesReferenceTextbookPages() {
        val first = RationalNumbersCourseFactory.pagesFor("有理数", 1..23)
            .filter { it.section.contains("小结") || it.section.contains("章末练习") }
        val second = RationalNumbersCourseFactory.pagesFor("有理数的运算", 24..62)
            .filter { it.section.contains("小结") || it.section.contains("章末练习") }

        assertTrue(first.isNotEmpty())
        assertTrue(second.isNotEmpty())
        assertTrue(first.all { it.sourcePage in 21..23 })
        assertTrue(second.all { it.sourcePage in 59..62 })
    }

    @Test
    fun textbookPagesContainNoGenericFormulaWorkbenchLanguage() {
        val text = (
            RationalNumbersCourseFactory.pagesFor("有理数", 1..23) +
                RationalNumbersCourseFactory.pagesFor("有理数的运算", 24..62)
            ).joinToString("\n") { page ->
                listOf(page.section, page.title, page.paragraphs.joinToString("\n"), page.formula, page.conclusion)
                    .joinToString("\n")
            }

        listOf("自定义数学公式验证", "按教材顺序理解", "非本课必会", "参数 · 可视化 · 验证").forEach {
            assertFalse(text.contains(it))
        }
    }
}
