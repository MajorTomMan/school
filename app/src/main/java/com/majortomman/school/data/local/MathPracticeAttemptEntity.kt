package com.majortomman.school.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "math_practice_attempts",
    indices = [
        Index(value = ["textbookKey"]),
        Index(value = ["knowledgePointId"]),
        Index(value = ["questionId"]),
        Index(value = ["createdAt"]),
    ],
)
data class MathPracticeAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val textbookKey: String,
    val lessonId: String?,
    val questionId: String,
    val templateId: String,
    val knowledgePointId: String,
    val questionJson: String,
    val answer: String,
    val canonicalAnswer: String,
    val correct: Boolean,
    val mistakeType: String?,
    val usedHint: Boolean,
    val durationMillis: Long,
    val createdAt: Long,
)
