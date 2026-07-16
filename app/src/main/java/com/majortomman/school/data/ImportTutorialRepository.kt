package com.majortomman.school.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.majortomman.school.data.material.IMPORT_TUTORIAL_VERSION
import com.majortomman.school.data.material.SubjectTemplates
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.importTutorialDataStore by preferencesDataStore(name = "import_tutorial_preferences")

class ImportTutorialRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    private object Keys {
        val completed = stringSetPreferencesKey("completed_subject_tutorials")
    }

    /**
     * 导入教程不再阻塞第一次教材选择。
     *
     * UI 仍沿用原有“已完成教程”判断，因此把当前支持学科视为已完成即可直接进入
     * 教材浏览器；历史完成记录继续保留，避免升级时破坏已有 DataStore 数据。
     */
    private val optionalTutorialTokens = SubjectTemplates.all
        .mapTo(mutableSetOf()) { subject -> token(subject.id, IMPORT_TUTORIAL_VERSION) }

    val completedTutorials: Flow<Set<String>> = appContext.importTutorialDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            preferences[Keys.completed].orEmpty() + optionalTutorialTokens
        }

    suspend fun markCompleted(subjectId: String, version: Int = IMPORT_TUTORIAL_VERSION) {
        val token = token(subjectId, version)
        appContext.importTutorialDataStore.edit { preferences ->
            preferences[Keys.completed] = preferences[Keys.completed].orEmpty() + token
        }
    }

    fun isCompleted(
        completed: Set<String>,
        subjectId: String,
        version: Int = IMPORT_TUTORIAL_VERSION,
    ): Boolean = token(subjectId, version) in completed

    private fun token(subjectId: String, version: Int): String = "$subjectId:$version"
}
