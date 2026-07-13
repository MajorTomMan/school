package com.majortomman.school.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import com.majortomman.school.data.AttemptRecord
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.data.SampleContent
import com.majortomman.school.data.ScheduledReview
import kotlinx.coroutines.launch

private enum class MainTab(
    val label: String,
    val symbol: String,
) {
    TODAY("今日", "●"),
    PATH("课程", "◇"),
    REVIEW("复习", "↻"),
    SETTINGS("设置", "⌁"),
}

@Composable
fun SchoolApp(repository: PreferencesRepository) {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.TODAY.name) }
    var openedLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val progress by repository.learningProgress.collectAsState(initial = LearningProgress())
    val aiSettings by repository.aiSettings.collectAsState(initial = AiSettings())
    val recentAttempts by repository.recentAttempts.collectAsState(initial = emptyList<AttemptRecord>())
    val reviewQueue by repository.reviewQueue.collectAsState(initial = emptyList<ScheduledReview>())

    val lessons = SampleContent.lessons.map { lesson ->
        lesson.copy(status = progress.lessonStatuses[lesson.id] ?: lesson.status)
    }
    val selectedTab = MainTab.valueOf(selectedTabName)
    val openedLesson = lessons.firstOrNull { it.id == openedLessonId }

    AnimatedContent(
        targetState = openedLesson,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(240)) + slideInHorizontally(tween(320)) { it / 5 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { -it / 8 })
            } else {
                (fadeIn(tween(240)) + slideInHorizontally(tween(320)) { -it / 6 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 8 })
            }
        },
        label = "appNavigation",
    ) { lesson ->
        if (lesson != null) {
            LearningScreen(
                lesson = lesson,
                aiSettings = aiSettings,
                progress = progress,
                onBack = { openedLessonId = null },
                onRecordAttempt = { draft ->
                    scope.launch {
                        repository.recordAttempt(lesson.id, draft)
                    }
                },
            )
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == selectedTab,
                                onClick = { selectedTabName = tab.name },
                                icon = { Text(tab.symbol) },
                                label = { Text(tab.label) },
                                alwaysShowLabel = true,
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 10 }) togetherWith
                                (fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -it / 12 })
                        },
                        label = "mainTabs",
                    ) { tab ->
                        when (tab) {
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

                            MainTab.REVIEW -> ReviewScreen(
                                fallbackItems = SampleContent.reviews,
                                progress = progress,
                                scheduledReviews = reviewQueue,
                                recentAttempts = recentAttempts,
                                onOpenLesson = { openedLessonId = it },
                            )

                            MainTab.SETTINGS -> SettingsScreen(
                                settings = aiSettings,
                                onSave = { updated -> scope.launch { repository.saveAiSettings(updated) } },
                                onClearProgress = { scope.launch { repository.clearLearningProgress() } },
                            )
                        }
                    }
                }
            }
        }
    }
}
