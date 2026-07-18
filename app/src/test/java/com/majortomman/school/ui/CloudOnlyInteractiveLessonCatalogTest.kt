package com.majortomman.school.ui

import com.majortomman.school.data.Lesson
import com.majortomman.school.data.MasteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudOnlyInteractiveLessonCatalogTest {
    @Test
    fun routesEverySupportedSubjectThroughCloudCourseRuntime() {
        listOf(
            "math" to "有理数的大小比较",
            "physics" to "牛顿第一定律",
            "chemistry" to "化学方程式",
            "biology" to "细胞结构",
            "english" to "Unit 1",
        ).forEach { (subject, title) ->
            val spec = InteractiveLessonCatalog.resolve(subject, lesson(title, 3..8))
            assertEquals(InteractiveLessonKind.CLOUD_COURSE, spec?.kind)
            assertEquals(3, spec?.sourcePage)
            assertEquals(8, spec?.sourcePageEnd)
        }
    }

    @Test
    fun doesNotGenerateInstructionalContentInsideNavigationSpec() {
        val spec = InteractiveLessonCatalog.resolve("math", lesson("一次函数", 114..126))!!

        assertTrue(spec.badge.isEmpty())
        assertTrue(spec.subtitle.isEmpty())
        assertTrue(spec.formula.isEmpty())
        assertTrue(spec.sourceSummary.isEmpty())
        assertTrue(spec.derivationSteps.isEmpty())
        assertTrue(spec.background.isEmpty())
        assertTrue(spec.misconception.isEmpty())
    }

    @Test
    fun rejectsIncompleteNavigationMetadata() {
        assertNull(InteractiveLessonCatalog.resolve("", lesson("有理数", 1..2)))
        assertNull(InteractiveLessonCatalog.resolve("math", lesson("", 1..2)))
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
