package com.majortomman.school.data

enum class MasteryStatus(val label: String) {
    MASTERED("已掌握"),
    LEARNING("学习中"),
    NOT_STARTED("未开始"),
    NEEDS_REVIEW("需要复习"),
}

data class Lesson(
    val id: String,
    val title: String,
    val subtitle: String,
    val estimatedMinutes: Int,
    val textbookPages: IntRange,
    val status: MasteryStatus,
    val objectives: List<String>,
    val explanation: String,
    val commonMistake: String,
)

data class ReviewItem(
    val id: String,
    val title: String,
    val reason: String,
    val dueLabel: String,
)

data class DailyPlan(
    val newLessonId: String,
    val reviewItems: List<ReviewItem>,
    val estimatedMinutes: Int,
)

data class AttemptDraft(
    val questionId: String,
    val questionText: String,
    val answer: String,
    val correct: Boolean,
    val feedback: String,
    val mistakeType: String? = null,
)

data class AttemptRecord(
    val id: Long,
    val lessonId: String,
    val lessonTitle: String,
    val questionText: String,
    val answer: String,
    val correct: Boolean,
    val feedback: String,
    val mistakeType: String?,
    val createdLabel: String,
)

data class ScheduledReview(
    val lessonId: String,
    val lessonTitle: String,
    val dueLabel: String,
    val intervalDays: Int,
    val repetitions: Int,
    val lastCorrect: Boolean,
)
