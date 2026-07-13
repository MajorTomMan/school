package com.majortomman.school.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "math_mistakes",
    indices = [
        Index(value = ["textbookKey"]),
        Index(value = ["knowledgePointId"]),
        Index(value = ["dueAt"]),
    ],
)
data class MathMistakeEntity(
    @PrimaryKey
    val mistakeKey: String,
    val textbookKey: String,
    val lessonId: String?,
    val questionId: String,
    val templateId: String,
    val knowledgePointId: String,
    val questionJson: String,
    val wrongCount: Int,
    val resolvedStreak: Int,
    val lastAnswer: String,
    val mistakeType: String,
    val dueAt: Long,
    val updatedAt: Long,
)
