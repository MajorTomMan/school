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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.majortomman.school.data.DailyPlan
import com.majortomman.school.data.ImportTutorialRepository
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.MasteryStatus
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.data.ScheduledReview
import com.majortomman.school.data.curriculum.CurriculumRepository
import com.majortomman.school.data.material.LessonAnalysis
import com.majortomman.school.data.material.MaterialPackRepository
import com.majortomman.school.data.math.MathQuestionBankRepository
import com.majortomman.school.data.recordAttempt
import kotlinx.coroutines.launch

private val NavigationBlack = Color.Black
private val NavigationWhite = Color(0xFFF5F5F7)
private val NavigationBlue = Color(0xFF0A84FF)

private enum class MainTab(val label: String) {
    SUBJECTS("学科"),
    TODAY("今天"),
    PATH("路径"),
    BANK("题库"),
    REVIEW("复习"),
    SETTINGS("设置"),
}

@Composable
fun SchoolApp(
    repository: PreferencesRepository,
    materialRepository: MaterialPackRepository,
    curriculumRepository: CurriculumRepository,
    tutorialRepository: ImportTutorialRepository,
    mathQuestionRepository: MathQuestionBankRepository,
    initialTextbookKey: String? = null,
) {
    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.SUBJECTS.name) }
    var activeTextbookKey by rememberSaveable { mutableStateOf(initialTextbookKey) }
    var openedLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedTextbookKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedTextbookPage by rememberSaveable { mutableStateOf<Int?>(null) }
    var openedAnalysis by remember { mutableStateOf<LessonAnalysis?>(null) }
    var resolvedAnalysisKey by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val progress by repository.learningProgress.collectAsState(initial = LearningProgress())
    val aiSettings by repository.aiSettings.collectAsState(initial = AiSettings())
    val recentAttempts by repository.recentAttempts.collectAsState(initial = emptyList<AttemptRecord>())
    val reviewQueue by repository.reviewQueue.collectAsState(initial = emptyList<ScheduledReview>())
    val libraryState by materialRepository.state.collectAsState()
    val curriculumState by curriculumRepository.state.collectAsState()
    val curriculumProgress by curriculumRepository.nodeProgress.collectAsState()
    val completedTutorials by tutorialRepository.completedTutorials.collectAsState(initial = emptySet())

    val activeTextbook = libraryState.installedTextbooks.firstOrNull { it.key == activeTextbookKey }
    val activeCurriculumId = activeTextbook?.let(curriculumRepository::curriculumIdFor)
    val lessons = activeTextbook?.lessons.orEmpty().mapIndexed { index, generated ->
        val stored = progress.lessonStatuses[generated.id]
        val nodeStatus = curriculumState.nodeForLegacyLesson(generated.id)
            ?.let { curriculumProgress[it.id]?.status }
            ?.let { runCatching { MasteryStatus.valueOf(it.name) }.getOrNull() }
        val fallback = if (index == 0) MasteryStatus.LEARNING else MasteryStatus.NOT_STARTED
        generated.toLesson(stored ?: nodeStatus ?: fallback)
    }
    val currentLesson = lessons.firstOrNull { it.status == MasteryStatus.LEARNING }
        ?: lessons.firstOrNull { it.status == MasteryStatus.NEEDS_REVIEW }
        ?: lessons.firstOrNull { it.status == MasteryStatus.NOT_STARTED }
        ?: lessons.firstOrNull()
    val dailyPlan = currentLesson?.let {
        DailyPlan(
            newLessonId = it.id,
            reviewItems = emptyList(),
            estimatedMinutes = it.estimatedMinutes,
        )
    }
    val selectedTab = MainTab.valueOf(selectedTabName)
    val openedLesson = lessons.firstOrNull { it.id == openedLessonId }
    val openedLessonIndex = lessons.indexOfFirst { it.id == openedLessonId }
    val nextLesson = openedLessonIndex.takeIf { it >= 0 }?.let { lessons.getOrNull(it + 1) }
    val openedGeneratedLesson = activeTextbook?.lessons?.firstOrNull { it.id == openedLessonId }
    val analysisRequestKey = activeTextbook?.key?.let { textbookKey ->
        openedGeneratedLesson?.sourceId?.let { sourceId -> "$textbookKey:$sourceId" }
    }
    val openedTextbook = libraryState.installedTextbooks.firstOrNull { it.key == openedTextbookKey }

    LaunchedEffect(libraryState.installedTextbooks.map { textbook ->
        "${textbook.key}:${textbook.pack.manifest.version}:${textbook.pack.pdfFile.isFile}:${textbook.lessons.size}"
    }) {
        runCatching { curriculumRepository.synchronizeInstalledTextbooks(libraryState.installedTextbooks) }
    }

    LaunchedEffect(analysisRequestKey) {
        openedAnalysis = null
        resolvedAnalysisKey = null
        if (analysisRequestKey != null && activeTextbook != null && openedGeneratedLesson != null) {
            openedAnalysis = materialRepository.loadLessonAnalysis(
                activeTextbook,
                openedGeneratedLesson.sourceId,
            )
            resolvedAnalysisKey = analysisRequestKey
        }
    }
    val openedAnalysisLoading = analysisRequestKey != null && resolvedAnalysisKey != analysisRequestKey

    LaunchedEffect(libraryState.installedTextbooks.map { it.key }) {
        if (activeTextbookKey != null && activeTextbook == null) {
            activeTextbookKey = null
            openedLessonId = null
        }
        if (openedTextbookKey != null && openedTextbook == null) {
            openedTextbookKey = null
            openedTextbookPage = null
        }
    }

    val textbookPage = openedTextbookPage
    if (textbookPage != null && openedTextbook != null) {
        PdfTextbookScreen(
            pack = openedTextbook.pack,
            initialPrintedPage = textbookPage,
            onBack = {
                openedTextbookKey = null
                openedTextbookPage = null
            },
        )
        return
    }

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
        if (lesson != null && activeTextbook != null) {
            val openTextbook: (Int) -> Unit = { printedPage ->
                openedTextbookKey = activeTextbook.key
                openedTextbookPage = printedPage
            }
            val recordAttempt: (String, Boolean, String) -> Unit = { answer, correct, feedback ->
                scope.launch { repository.recordAttempt(lesson.id, answer, correct, feedback) }
            }
            val completeLesson: () -> Unit = {
                val nextId = nextLesson?.id
                scope.launch {
                    repository.finishLessonAndStartNext(lesson.id, nextId)
                }
                if (nextLesson != null) {
                    openedLessonId = nextLesson.id
                } else {
                    openedLessonId = null
                    selectedTabName = MainTab.PATH.name
                }
            }
            when {
                openedAnalysisLoading -> LessonAnalysisLoadingScreen(
                    lessonTitle = lesson.title,
                    onBack = { openedLessonId = null },
                )

                openedAnalysis != null -> GeneratedLearningScreen(
                    lesson = lesson,
                    analysis = openedAnalysis!!,
                    aiSettings = aiSettings,
                    progress = progress,
                    installedMaterial = activeTextbook.pack,
                    nextLessonTitle = nextLesson?.title,
                    onOpenTextbook = openTextbook,
                    onBack = { openedLessonId = null },
                    onComplete = completeLesson,
                    onRecordAttempt = recordAttempt,
                )

                else -> SceneLearningScreen(
                    lesson = lesson,
                    aiSettings = aiSettings,
                    progress = progress,
                    installedMaterial = activeTextbook.pack,
                    onOpenTextbook = openTextbook,
                    onBack = { openedLessonId = null },
                    onRecordAttempt = recordAttempt,
                )
            }
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
                            MainTab.SUBJECTS -> SubjectTextbookCenterScreen(
                                libraryState = libraryState,
                                completedTutorials = completedTutorials,
                                onTutorialCompleted = { subjectId, version ->
                                    scope.launch { tutorialRepository.markCompleted(subjectId, version) }
                                },
                                onImport = { slot, uri -> materialRepository.enqueueImport(slot, uri) },
                                onCancelProcessing = materialRepository::cancelProcessing,
                                onRemove = { slot -> scope.launch { materialRepository.removeInstalled(slot) } },
                                onEnterCourse = { textbook ->
                                    activeTextbookKey = textbook.key
                                    openedLessonId = null
                                    selectedTabName = MainTab.TODAY.name
                                },
                                onOpenTextbook = { textbook, page ->
                                    openedTextbookKey = textbook.key
                                    openedTextbookPage = page
                                },
                            )

                            MainTab.TODAY -> {
                                if (activeTextbook == null || dailyPlan == null || lessons.isEmpty()) {
                                    NoActiveTextbookScreen { selectedTabName = MainTab.SUBJECTS.name }
                                } else {
                                    SceneTodayScreen(
                                        plan = dailyPlan,
                                        lessons = lessons,
                                        progress = progress,
                                        onStartLesson = { openedLessonId = it },
                                        onOpenPath = { selectedTabName = MainTab.PATH.name },
                                    )
                                }
                            }

                            MainTab.PATH -> {
                                when {
                                    activeTextbook == null || lessons.isEmpty() -> {
                                        NoActiveTextbookScreen { selectedTabName = MainTab.SUBJECTS.name }
                                    }
                                    activeCurriculumId != null && activeCurriculumId in curriculumState.curriculumById -> {
                                        CurriculumTreeScreen(
                                            snapshot = curriculumState,
                                            curriculumId = activeCurriculumId,
                                            progress = curriculumProgress,
                                            activeLegacyLessonId = currentLesson?.id,
                                            onOpenLesson = { openedLessonId = it },
                                        )
                                    }
                                    else -> {
                                        SceneCoursePathScreen(
                                            lessons = lessons,
                                            onOpenLesson = { openedLessonId = it },
                                        )
                                    }
                                }
                            }

                            MainTab.BANK -> MathQuestionBankScreen(
                                repository = mathQuestionRepository,
                                textbook = activeTextbook,
                                onOpenSubjects = { selectedTabName = MainTab.SUBJECTS.name },
                                onOpenTextbook = { page ->
                                    activeTextbook?.let { textbook ->
                                        openedTextbookKey = textbook.key
                                        openedTextbookPage = page
                                    }
                                },
                            )

                            MainTab.REVIEW -> MinimalRoomReviewScreen(
                                fallbackItems = emptyList(),
                                progress = progress,
                                scheduledReviews = reviewQueue,
                                recentAttempts = recentAttempts,
                                onOpenLesson = { lessonId ->
                                    if (lessons.any { it.id == lessonId }) openedLessonId = lessonId
                                },
                            )

                            MainTab.SETTINGS -> MaterialSettingsScreen(
                                settings = aiSettings,
                                onSave = { updated -> scope.launch { repository.saveAiSettings(updated) } },
                                onOpenSubjects = { selectedTabName = MainTab.SUBJECTS.name },
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
private fun LessonAnalysisLoadingScreen(
    lessonTitle: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavigationBlack)
            .padding(horizontal = 28.dp, vertical = 44.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "返回",
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
            color = NavigationWhite.copy(alpha = 0.56f),
            fontSize = 14.sp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = lessonTitle,
                color = NavigationWhite,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "正在读取课程数据",
                color = NavigationBlue,
                fontSize = 15.sp,
            )
        }
        Text(
            text = "课程树、教材与学习记录保持在本机。",
            color = NavigationWhite.copy(alpha = 0.34f),
            fontSize = 13.sp,
        )
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
            .padding(horizontal = 7.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier = Modifier
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 3.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) NavigationWhite else NavigationWhite.copy(alpha = 0.32f),
                    fontSize = 11.sp,
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
