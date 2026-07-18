package com.majortomman.school.ui

import com.majortomman.school.data.Lesson
import com.majortomman.school.data.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InteractiveLessonCatalogTest {
    @Test
    fun resolvesRationalNumbersLesson() {
        val spec = InteractiveLessonCatalog.resolve("math", lesson("1.2 有理数及其大小比较", 7..17))

        assertEquals(InteractiveLessonKind.RATIONAL_NUMBERS, spec?.kind)
        assertEquals(7, spec?.sourcePage)
        assertEquals(17, spec?.sourcePageEnd)
    }

    @Test
    fun resolvesRationalOperationsLesson() {
        val spec = InteractiveLessonCatalog.resolve("math", lesson("2.1 有理数的加法与减法", 25..37))

        assertEquals(InteractiveLessonKind.RATIONAL_NUMBERS, spec?.kind)
    }

    @Test
    fun resolvesLinearFunctionLesson() {
        val spec = InteractiveLessonCatalog.resolve("math", lesson("一次函数", 114..126))
        assertEquals(InteractiveLessonKind.LINEAR_FUNCTION, spec?.kind)
        assertEquals(114, spec?.sourcePage)
        assertEquals(126, spec?.sourcePageEnd)
    }

    @Test
    fun resolvesNewtonFirstLawLesson() {
        val spec = InteractiveLessonCatalog.resolve("physics", lesson("1.牛顿第一定律", 82..86))
        assertEquals(InteractiveLessonKind.NEWTON_FIRST_LAW, spec?.kind)
    }

    @Test
    fun routesOtherMathLessonsToGeneralMathCourse() {
        val spec = InteractiveLessonCatalog.resolve("math", lesson("二次函数", 1..5))
        assertEquals(InteractiveLessonKind.MATH_GENERAL, spec?.kind)
        assertEquals(1, spec?.sourcePage)
        assertEquals(5, spec?.sourcePageEnd)
    }

    @Test
    fun doesNotHijackUnrelatedSubjects() {
        assertNull(InteractiveLessonCatalog.resolve("english", lesson("Unit 1", 1..5)))
    }

    private fun lesson(title: String, pages: IntRange) = Lesson(
        id = "test:$title",
        title = title,
        subtitle = "",
        estimatedMinutes = 15,
        textbookPages = pages,
        status = MasteryStatus.NOT_STARTED,
        objectives = emptyList(),
        explanation = "",
        commonMistake = "",
    )
}
