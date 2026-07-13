package com.majortomman.school.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.majortomman.school.data.AiSettings
import com.majortomman.school.data.AttemptRecord
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.data.SampleContent
import com.majortomman.school.data.ScheduledReview
import com.majortomman.school.data.recordAttempt
import kotlinx.coroutines.launch

private enum class MainTab(
    val label: String,
    val symbol: String,
) {
    TODAY("今天", "●"),
    PATH("路径", "◇"),
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
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -it / 8 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { -it / 6 }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 8 })
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
                onRecordAttempt = { answer, correct, feedback ->
                    scope.launch {
                        repository.recordAttempt(lesson.id, answer, correct, feedback)
                    }
                },
            )
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    GuidedBottomBar(
                        selected = selectedTab,
                        onSelect = { selectedTabName = it.name },
                    )
                },
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (fadeIn(tween(210)) + slideInHorizontally(tween(260)) { it / 12 }) togetherWith
                                (fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { -it / 12 })
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

                            MainTab.REVIEW -> RoomReviewScreen(
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

@Composable
private fun GuidedBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        )
                        .clickable { onSelect(tab) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = tab.symbol,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
