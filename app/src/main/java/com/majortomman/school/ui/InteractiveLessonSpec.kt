package com.majortomman.school.ui

import com.majortomman.school.data.Lesson
import com.majortomman.school.learning.course.LessonEnrichment

enum class InteractiveLessonKind {
    CLOUD_COURSE,
}

/**
 * Navigation metadata only. All instructional content is decoded from an installed course package.
 */
data class InteractiveLessonSpec(
    val kind: InteractiveLessonKind,
    val badge: String = "",
    val title: String,
    val subtitle: String = "",
    val formula: String = "",
    val sourceSummary: String = "",
    val derivationTitle: String = "",
    val derivationSteps: List<String> = emptyList(),
    val background: List<String> = emptyList(),
    val misconception: String = "",
    val sourcePage: Int,
    val sourcePageEnd: Int,
    val enrichment: LessonEnrichment = LessonEnrichment(),
)

object InteractiveLessonCatalog {
    fun resolve(subjectId: String, lesson: Lesson): InteractiveLessonSpec? {
        if (subjectId.isBlank() || lesson.title.isBlank()) return null
        val firstPage = lesson.textbookPages.first.coerceAtLeast(1)
        val lastPage = lesson.textbookPages.last.coerceAtLeast(firstPage)
        return InteractiveLessonSpec(
            kind = InteractiveLessonKind.CLOUD_COURSE,
            title = lesson.title,
            sourcePage = firstPage,
            sourcePageEnd = lastPage,
        )
    }
}
