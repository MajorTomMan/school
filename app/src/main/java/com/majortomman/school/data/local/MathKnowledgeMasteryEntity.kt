package com.majortomman.school.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "math_knowledge_mastery",
    primaryKeys = ["textbookKey", "knowledgePointId"],
    indices = [Index(value = ["dueAt"])],
)
data class MathKnowledgeMasteryEntity(
    val textbookKey: String,
    val knowledgePointId: String,
    val score: Double,
    val attempts: Int,
    val correctStreak: Int,
    val wrongStreak: Int,
    val lastCorrect: Boolean,
    val dueAt: Long,
    val updatedAt: Long,
)
