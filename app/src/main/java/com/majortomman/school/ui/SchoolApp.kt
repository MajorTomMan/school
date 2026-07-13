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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.AiSettings
import com.majortomman.school.data.AttemptRecord
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.data.SampleContent
import com.majortomman.school.data.ScheduledReview
import com.majortomman.school.data.recordAttempt
import kotlinx.coroutines.launch

private val NavigationBlack = Color.Black
private val NavigationWhite = Color(0xFFF5F5F7)
private val NavigationBlue = Color(0xFF0A84FF)

private enum class MainTab(val label: String) {
    TODAY("今天"),
    PATH("路径"),
    REVIEW("复习"),
    SETTINGS("设置"),
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
                (fadeIn(tween(300)) + slideInHorizontally(tween(420)) { it / 7 }) togetherWith
                    (fadeOut(tween(170)) + slideOutHorizontally(tween(280)) { -it / 9 })
            } else {
                (fadeIn(tween(280)) + slideInHorizontally(tween(400)) { -it / 8 }) togetherWith
                    (fadeOut(tween(170)) + slideOutHorizontally(tween(280)) { it / 9 })
            }
        },
        label = "appNavigation",
    ) { lesson ->
        if (lesson != null) {
            SceneLearningScreen(
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
                containerColor = NavigationBlack,
                bottomBar = {
                    MinimalBottomBar(
                        selected = selectedTab,
                        onSelect = { selectedTabName = it.name },
                    )
                },
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (fadeIn(tween(260)) + slideInHorizontally(tween(360)) { it / 14 }) togetherWith
                                (fadeOut(tween(150)) + slideOutHorizontally(tween(260)) { -it / 14 })
                        },
                        label = "mainTabs",
                    ) { tab ->
                        when (tab) {
                            MainTab.TODAY -> SceneTodayScreen(
                                plan = SampleContent.dailyPlan,
                                lessons = lessons,
                                progress = progress,
                                onStartLesson = { openedLessonId = it },
                                onOpenPath = { selectedTabName = MainTab.PATH.name },
                            )

                            MainTab.PATH -> SceneCoursePathScreen(
                                lessons = lessons,
                                onOpenLesson = { openedLessonId = it },
                            )

                            MainTab.REVIEW -> MinimalRoomReviewScreen(
                                fallbackItems = SampleContent.reviews,
                                progress = progress,
                                scheduledReviews = reviewQueue,
                                recentAttempts = recentAttempts,
                                onOpenLesson = { openedLessonId = it },
                            )

                            MainTab.SETTINGS -> MinimalSettingsScreen(
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
private fun MinimalBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavigationBlack)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) NavigationWhite else NavigationWhite.copy(alpha = 0.32f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) NavigationBlue else Color.Transparent),
                )
            }
        }
    }
}
