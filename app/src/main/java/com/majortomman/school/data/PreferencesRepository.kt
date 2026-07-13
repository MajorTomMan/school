package com.majortomman.school.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    private object Keys {
        val aiEndpoint = stringPreferencesKey("ai_endpoint")
        val aiModel = stringPreferencesKey("ai_model")
        val aiApiKey = stringPreferencesKey("ai_api_key")
        val attempts = intPreferencesKey("practice_attempts")
        val correctAttempts = intPreferencesKey("practice_correct_attempts")
        val lastLessonId = stringPreferencesKey("last_lesson_id")
        val lastAnswer = stringPreferencesKey("last_answer")
        val lastFeedback = stringPreferencesKey("last_feedback")
    }

    val aiSettings: Flow<AiSettings> = context.schoolDataStore.data
        .safeData()
        .map { preferences ->
            AiSettings(
                endpoint = preferences[Keys.aiEndpoint] ?: AiSettings().endpoint,
                model = preferences[Keys.aiModel] ?: AiSettings().model,
                apiKey = preferences[Keys.aiApiKey].orEmpty(),
            )
        }

    val learningProgress: Flow<LearningProgress> = context.schoolDataStore.data
        .safeData()
        .map { preferences ->
            val statuses = SampleContent.lessons.mapNotNull { lesson ->
                val stored = preferences[lessonStatusKey(lesson.id)] ?: return@mapNotNull null
                val status = runCatching { MasteryStatus.valueOf(stored) }.getOrNull()
                    ?: return@mapNotNull null
                lesson.id to status
            }.toMap()

            LearningProgress(
                lessonStatuses = statuses,
                attempts = preferences[Keys.attempts] ?: 0,
                correctAttempts = preferences[Keys.correctAttempts] ?: 0,
                lastLessonId = preferences[Keys.lastLessonId],
                lastAnswer = preferences[Keys.lastAnswer].orEmpty(),
                lastFeedback = preferences[Keys.lastFeedback].orEmpty(),
            )
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
        answer: String,
        correct: Boolean,
        feedback: String,
    ) {
        context.schoolDataStore.edit { preferences ->
            preferences[Keys.attempts] = (preferences[Keys.attempts] ?: 0) + 1
            if (correct) {
                preferences[Keys.correctAttempts] = (preferences[Keys.correctAttempts] ?: 0) + 1
                preferences[lessonStatusKey(lessonId)] = MasteryStatus.MASTERED.name
            } else if (preferences[lessonStatusKey(lessonId)] == null) {
                preferences[lessonStatusKey(lessonId)] = MasteryStatus.LEARNING.name
            }
            preferences[Keys.lastLessonId] = lessonId
            preferences[Keys.lastAnswer] = answer.take(2_000)
            preferences[Keys.lastFeedback] = feedback.take(2_000)
        }
    }

    suspend fun clearLearningProgress() {
        context.schoolDataStore.edit { preferences ->
            SampleContent.lessons.forEach { lesson -> preferences.remove(lessonStatusKey(lesson.id)) }
            preferences.remove(Keys.attempts)
            preferences.remove(Keys.correctAttempts)
            preferences.remove(Keys.lastLessonId)
            preferences.remove(Keys.lastAnswer)
            preferences.remove(Keys.lastFeedback)
        }
    }

    private fun lessonStatusKey(lessonId: String) = stringPreferencesKey("lesson_status_$lessonId")

    private fun Flow<Preferences>.safeData(): Flow<Preferences> = catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
}
