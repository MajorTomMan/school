package com.majortomman.school.learning.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RationalNumbersCourseFactoryTest {
    @Test
    fun followsTextbookChapterOrder() {
        val pages = RationalNumbersCourseFactory.pagesFor("有理数", 1..23)

        val comparisonIndex = pages.indexOfFirst { it.title == "有理数的大小比较" }
        val summaryIndex = pages.indexOfFirst { it.title == "本章知识结构" }
        val exercisesIndex = pages.indexOfFirst { it.section == "第一章 章末练习" }

        assertEquals("具有相反意义的量", pages.first().title)
        assertTrue(pages.indexOfFirst { it.title == "数轴" } < pages.indexOfFirst { it.title == "相反数" })
        assertTrue(pages.indexOfFirst { it.title == "相反数" } < pages.indexOfFirst { it.title == "绝对值" })
        assertTrue(comparisonIndex in 0 until summaryIndex)
        assertTrue(summaryIndex in 0 until exercisesIndex)
    }

    @Test
    fun arithmeticLessonsDoNotUseNumberLineVisualization() {
        val titles = listOf(
            "2.1 有理数的加法与减法",
            "2.2 有理数的乘法与除法",
            "2.3 有理数的乘方",
        )

        titles.flatMap { RationalNumbersCourseFactory.pagesFor(it, 25..58) }.forEach { page ->
            assertFalse("${page.title} 不应使用数轴", page.visualization == RationalVisualizationKind.NUMBER_LINE)
            assertFalse("${page.title} 不应使用相反数镜像", page.visualization == RationalVisualizationKind.OPPOSITE_NUMBERS)
            assertFalse("${page.title} 不应使用绝对值距离", page.visualization == RationalVisualizationKind.ABSOLUTE_VALUE)
        }
    }

    @Test
    fun operationVisualizationsMatchTheirProcesses() {
        val addition = RationalNumbersCourseFactory.pagesFor("有理数的加法与减法", 25..37)
        val multiplication = RationalNumbersCourseFactory.pagesFor("有理数的乘法与除法", 38..50)
        val power = RationalNumbersCourseFactory.pagesFor("有理数的乘方", 51..57)

        assertTrue(addition.any { it.visualization == RationalVisualizationKind.ADDITION_PROCESS })
        assertTrue(addition.any { it.visualization == RationalVisualizationKind.SUBTRACTION_TRANSFORM })
        assertTrue(multiplication.any { it.visualization == RationalVisualizationKind.MULTIPLICATION_SIGN })
        assertTrue(multiplication.any { it.visualization == RationalVisualizationKind.DIVISION_TRANSFORM })
        assertTrue(power.all { it.visualization == RationalVisualizationKind.POWER_PROCESS })
    }

    @Test
    fun courseTextContainsNoDevelopmentConversation() {
        val pages = RationalNumbersCourseFactory.pagesFor("有理数的运算", 24..62)
        val text = pages.joinToString("\n") { page ->
            listOf(page.section, page.title, page.paragraphs.joinToString("\n"), page.formula, page.conclusion)
                .joinToString("\n")
        }
        val forbidden = listOf("用户", "提示词", "本次更新", "此次更新", "当前仓库", "School 解释", "移动端整理")

        forbidden.forEach { phrase -> assertFalse("课程正文不应包含：$phrase", text.contains(phrase)) }
    }

    @Test
    fun sourcePagesStayInsideLessonRange() {
        val range = 7..17
        val pages = RationalNumbersCourseFactory.pagesFor("1.2 有理数及其大小比较", range)

        assertTrue(pages.isNotEmpty())
        assertTrue(pages.all { it.sourcePage in range })
    }

    @Test
    fun recognizesTextbookLessonTitles() {
        assertTrue(RationalNumbersCourseFactory.supports("1.1 正数和负数"))
        assertTrue(RationalNumbersCourseFactory.supports("1.2 有理数及其大小比较"))
        assertTrue(RationalNumbersCourseFactory.supports("有理数的概念"))
        assertTrue(RationalNumbersCourseFactory.supports("2.1 有理数的加法与减法"))
        assertTrue(RationalNumbersCourseFactory.supports("2.2 有理数的乘法与除法"))
        assertTrue(RationalNumbersCourseFactory.supports("2.3 有理数的乘方"))
        assertFalse(RationalNumbersCourseFactory.supports("一次函数"))
    }
}
