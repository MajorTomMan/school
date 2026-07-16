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
     * 导入教程不再作为首次使用的阻塞步骤。这里保留历史完成记录，并把当前学科教程
     * 默认视为可跳过，兼容旧页面路由和未来重新开放的帮助入口。
     */
    val completedTutorials: Flow<Set<String>> = appContext.importTutorialDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            preferences[Keys.completed].orEmpty() + SubjectTemplates.all.map { subject ->
                token(subject.id, IMPORT_TUTORIAL_VERSION)
            }
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
