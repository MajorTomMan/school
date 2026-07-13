package com.majortomman.school.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningDao {
    @Insert
    suspend fun insertAttempt(attempt: PracticeAttemptEntity): Long

    @Query("SELECT * FROM practice_attempts ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentAttempts(limit: Int = 20): Flow<List<PracticeAttemptEntity>>

    @Query(
        """
        SELECT COUNT(*) AS attempts,
               COALESCE(SUM(CASE WHEN correct = 1 THEN 1 ELSE 0 END), 0) AS correctAttempts
        FROM practice_attempts
        """,
    )
    fun observeAttemptStats(): Flow<AttemptStatsRow>

    @Query("SELECT * FROM review_schedules ORDER BY dueAt ASC")
    fun observeReviewSchedules(): Flow<List<ReviewScheduleEntity>>

    @Query("SELECT * FROM review_schedules WHERE lessonId = :lessonId LIMIT 1")
    suspend fun getReviewSchedule(lessonId: String): ReviewScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewSchedule(schedule: ReviewScheduleEntity)

    @Insert
    suspend fun insertMathAttempt(attempt: MathPracticeAttemptEntity): Long

    @Query(
        """
        SELECT * FROM math_practice_attempts
        WHERE textbookKey = :textbookKey
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    fun observeMathAttempts(textbookKey: String, limit: Int = 30): Flow<List<MathPracticeAttemptEntity>>

    @Query(
        """
        SELECT templateId FROM math_practice_attempts
        WHERE textbookKey = :textbookKey
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun recentMathTemplateIds(textbookKey: String, limit: Int = 8): List<String>

    @Query(
        """
        SELECT * FROM math_knowledge_mastery
        WHERE textbookKey = :textbookKey
        ORDER BY score ASC, dueAt ASC
        """,
    )
    fun observeMathMastery(textbookKey: String): Flow<List<MathKnowledgeMasteryEntity>>

    @Query(
        """
        SELECT * FROM math_knowledge_mastery
        WHERE textbookKey = :textbookKey AND knowledgePointId = :knowledgePointId
        LIMIT 1
        """,
    )
    suspend fun getMathMastery(textbookKey: String, knowledgePointId: String): MathKnowledgeMasteryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMathMastery(mastery: MathKnowledgeMasteryEntity)

    @Query(
        """
        SELECT * FROM math_mistakes
        WHERE textbookKey = :textbookKey
        ORDER BY dueAt ASC, wrongCount DESC, updatedAt DESC
        """,
    )
    fun observeMathMistakes(textbookKey: String): Flow<List<MathMistakeEntity>>

    @Query("SELECT * FROM math_mistakes WHERE mistakeKey = :mistakeKey LIMIT 1")
    suspend fun getMathMistake(mistakeKey: String): MathMistakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMathMistake(mistake: MathMistakeEntity)

    @Query("DELETE FROM math_mistakes WHERE mistakeKey = :mistakeKey")
    suspend fun deleteMathMistake(mistakeKey: String)

    @Query("DELETE FROM practice_attempts")
    suspend fun clearAttempts()

    @Query("DELETE FROM review_schedules")
    suspend fun clearReviewSchedules()

    @Query("DELETE FROM math_practice_attempts")
    suspend fun clearMathAttempts()

    @Query("DELETE FROM math_knowledge_mastery")
    suspend fun clearMathMastery()

    @Query("DELETE FROM math_mistakes")
    suspend fun clearMathMistakes()
}

data class AttemptStatsRow(
    val attempts: Int,
    val correctAttempts: Int,
)
