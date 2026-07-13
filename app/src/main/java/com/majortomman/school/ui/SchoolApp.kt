package com.majortomman.school.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.majortomman.school.data.AiSettings
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.data.SampleContent
import kotlinx.coroutines.launch

private enum class MainTab(
    val label: String,
    val emoji: String,
) {
    TODAY("今日", "☀️"),
    PATH("课程", "🧭"),
    REVIEW("复习", "📝"),
    SETTINGS("设置", "⚙️"),
}

@Composable
fun SchoolApp(repository: PreferencesRepository) {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.TODAY.name) }
    var openedLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val progress by repository.learningProgress.collectAsState(initial = LearningProgress())
    val aiSettings by repository.aiSettings.collectAsState(initial = AiSettings())

    val lessons = SampleContent.lessons.map { lesson ->
        lesson.copy(status = progress.lessonStatuses[lesson.id] ?: lesson.status)
    }
    val selectedTab = MainTab.valueOf(selectedTabName)
    val openedLesson = lessons.firstOrNull { it.id == openedLessonId }

    if (openedLesson != null) {
        LearningScreen(
            lesson = openedLesson,
            aiSettings = aiSettings,
            progress = progress,
            onBack = { openedLessonId = null },
            onRecordAttempt = { answer, correct, feedback ->
                scope.launch {
                    repository.recordAttempt(openedLesson.id, answer, correct, feedback)
                }
            },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTabName = tab.name },
                        icon = { Text(tab.emoji) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                MainTab.TODAY -> TodayScreen(
                    plan = SampleContent.dailyPlan,
                    lessons = lessons,
                    progress = progress,
                    onStartLesson = { openedLessonId = it },
                    onOpenPath = { selectedTabName = MainTab.PATH.name },
                )

                MainTab.PATH -> CoursePathScreen(
                    lessons = lessons,
                    onOpenLesson = { openedLessonId = it },
                )

                MainTab.REVIEW -> ReviewScreen(items = SampleContent.reviews, progress = progress)
                MainTab.SETTINGS -> SettingsScreen(
                    settings = aiSettings,
                    onSave = { updated -> scope.launch { repository.saveAiSettings(updated) } },
                    onClearProgress = { scope.launch { repository.clearLearningProgress() } },
                )
            }
        }
    }
}
