package com.majortomman.school.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.majortomman.school.data.local.PracticeAttemptEntity
import com.majortomman.school.data.local.SchoolDatabase
import com.majortomman.school.data.review.ReviewScheduler
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.schoolDataStore by preferencesDataStore(name = "school_preferences")

data class AiSettings(
    val endpoint: String = "http://192.168.1.2:7777/v1",
    val model: String = "gemma-4",
    val apiKey: String = "",
)

data class LearningProgress(
    val lessonStatuses: Map<String, MasteryStatus> = emptyMap(),
    val attempts: Int = 0,
    val correctAttempts: Int = 0,
    val lastLessonId: String? = null,
    val lastAnswer: String = "",
    val lastFeedback: String = "",
) {
    val accuracyPercent: Int
        get() = if (attempts == 0) 0 else (correctAttempts * 100 / attempts)
}

class PreferencesRepository(
    private val context: Context,
) {
    private val learningDao = SchoolDatabase.getInstance(context).learningDao()
    private val preferencesFlow = context.schoolDataStore.data.safeData()

    private object Keys {
        val aiEndpoint = stringPreferencesKey("ai_endpoint")
        val aiModel = stringPreferencesKey("ai_model")
        val aiApiKey = stringPreferencesKey("ai_api_key")

        // 0.2/0.3 used counters in DataStore. They remain as a legacy baseline so upgrades
        // keep the visible totals while all new attempt details are stored in Room.
        val attempts = intPreferencesKey("practice_attempts")
        val correctAttempts = intPreferencesKey("practice_correct_attempts")
        val lastLessonId = stringPreferencesKey("last_lesson_id")
        val lastAnswer = stringPreferencesKey("last_answer")
        val lastFeedback = stringPreferencesKey("last_feedback")
    }

    val aiSettings: Flow<AiSettings> = preferencesFlow.map { preferences ->
        AiSettings(
            endpoint = preferences[Keys.aiEndpoint] ?: AiSettings().endpoint,
            model = preferences[Keys.aiModel] ?: AiSettings().model,
            apiKey = preferences[Keys.aiApiKey].orEmpty(),
        )
    }

    val learningProgress: Flow<LearningProgress> = combine(
        preferencesFlow,
        learningDao.observeAttemptStats(),
    ) { preferences, roomStats ->
        val statuses = SampleContent.lessons.mapNotNull { lesson ->
            val stored = preferences[lessonStatusKey(lesson.id)] ?: return@mapNotNull null
            val status = runCatching { MasteryStatus.valueOf(stored) }.getOrNull()
                ?: return@mapNotNull null
            lesson.id to status
        }.toMap()

        LearningProgress(
            lessonStatuses = statuses,
            attempts = (preferences[Keys.attempts] ?: 0) + roomStats.attempts,
            correctAttempts = (preferences[Keys.correctAttempts] ?: 0) + roomStats.correctAttempts,
            lastLessonId = preferences[Keys.lastLessonId],
            lastAnswer = preferences[Keys.lastAnswer].orEmpty(),
            lastFeedback = preferences[Keys.lastFeedback].orEmpty(),
        )
    }

    val recentAttempts: Flow<List<AttemptRecord>> = learningDao.observeRecentAttempts().map { attempts ->
        val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        val zoneId = ZoneId.systemDefault()
        attempts.map { attempt ->
            AttemptRecord(
                id = attempt.id,
                lessonId = attempt.lessonId,
                lessonTitle = lessonTitle(attempt.lessonId),
                questionText = attempt.questionText,
                answer = attempt.answer,
                correct = attempt.correct,
                feedback = attempt.feedback,
                mistakeType = attempt.mistakeType,
                createdLabel = Instant.ofEpochMilli(attempt.createdAt)
                    .atZone(zoneId)
                    .format(formatter),
            )
        }
    }

    val reviewQueue: Flow<List<ScheduledReview>> = learningDao.observeReviewSchedules().map { schedules ->
        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        schedules.map { schedule ->
            val dueDate = Instant.ofEpochMilli(schedule.dueAt).atZone(zoneId).toLocalDate()
            val days = ChronoUnit.DAYS.between(today, dueDate).toInt()
            ScheduledReview(
                lessonId = schedule.lessonId,
                lessonTitle = lessonTitle(schedule.lessonId),
                dueLabel = when {
                    days < 0 -> "已到期"
                    days == 0 -> "今天"
                    days == 1 -> "明天"
                    else -> "${days}天后"
                },
                intervalDays = schedule.intervalDays,
                repetitions = schedule.repetitions,
                lastCorrect = schedule.lastCorrect,
            )
        }
    }

    suspend fun saveAiSettings(settings: AiSettings) {
        context.schoolDataStore.edit { preferences ->
            preferences[Keys.aiEndpoint] = settings.endpoint.trim().trimEnd('/')
            preferences[Keys.aiModel] = settings.model.trim()
            preferences[Keys.aiApiKey] = settings.apiKey.trim()
        }
    }

    suspend fun markLessonStatus(lessonId: String, status: MasteryStatus) {
        context.schoolDataStore.edit { preferences ->
            preferences[lessonStatusKey(lessonId)] = status.name
        }
    }

    suspend fun recordAttempt(
        lessonId: String,
        draft: AttemptDraft,
    ) {
        val now = System.currentTimeMillis()
        learningDao.insertAttempt(
            PracticeAttemptEntity(
                lessonId = lessonId,
                questionId = draft.questionId,
                questionText = draft.questionText.take(4_000),
                answer = draft.answer.take(4_000),
                correct = draft.correct,
                feedback = draft.feedback.take(4_000),
                mistakeType = draft.mistakeType?.take(120),
                createdAt = now,
            ),
        )

        val previousSchedule = learningDao.getReviewSchedule(lessonId)
        learningDao.upsertReviewSchedule(
            ReviewScheduler.next(
                lessonId = lessonId,
                previous = previousSchedule,
                correct = draft.correct,
                now = now,
            ),
        )

        context.schoolDataStore.edit { preferences ->
            preferences[lessonStatusKey(lessonId)] = if (draft.correct) {
                MasteryStatus.MASTERED.name
            } else {
                MasteryStatus.NEEDS_REVIEW.name
            }
            preferences[Keys.lastLessonId] = lessonId
            preferences[Keys.lastAnswer] = draft.answer.take(2_000)
            preferences[Keys.lastFeedback] = draft.feedback.take(2_000)
        }
    }

    suspend fun clearLearningProgress() {
        learningDao.clearAttempts()
        learningDao.clearReviewSchedules()
        context.schoolDataStore.edit { preferences ->
            SampleContent.lessons.forEach { lesson -> preferences.remove(lessonStatusKey(lesson.id)) }
            preferences.remove(Keys.attempts)
            preferences.remove(Keys.correctAttempts)
            preferences.remove(Keys.lastLessonId)
            preferences.remove(Keys.lastAnswer)
            preferences.remove(Keys.lastFeedback)
        }
    }

    private fun lessonTitle(lessonId: String): String =
        SampleContent.lessons.firstOrNull { it.id == lessonId }?.title ?: lessonId

    private fun lessonStatusKey(lessonId: String) = stringPreferencesKey("lesson_status_$lessonId")

    private fun Flow<Preferences>.safeData(): Flow<Preferences> = catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
}
