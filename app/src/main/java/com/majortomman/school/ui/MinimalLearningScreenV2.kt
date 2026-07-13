package com.majortomman.school.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.ai.OpenAiCompatibleClient
import com.majortomman.school.data.AiSettings
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.Lesson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LessonBlack = Color(0xFF050608)
private val LessonWhite = Color(0xFFF5F7FA)
private val LessonBlue = Color(0xFF2D7BFF)
private val LessonRed = Color(0xFFFF453A)
private val LessonYellow = Color(0xFFFFCC00)
private val LessonMuted = LessonWhite.copy(alpha = 0.46f)
private val LessonLine = LessonWhite.copy(alpha = 0.13f)

private enum class LessonPage(val title: String) {
    INTUITION("核心直觉"),
    PITFALL("容易踩坑"),
    TEXTBOOK("教材定位"),
    PRACTICE("独立练习"),
    SUMMARY("完成"),
}

@Composable
fun MinimalLearningScreenV2(
    lesson: Lesson,
    aiSettings: AiSettings,
    progress: LearningProgress,
    onBack: () -> Unit,
    onRecordAttempt: (answer: String, correct: Boolean, feedback: String) -> Unit,
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var helpVisible by rememberSaveable { mutableStateOf(false) }
    val pages = LessonPage.entries
    val page = pages[pageIndex]
    val animatedProgress by animateFloatAsState(
        targetValue = (pageIndex + 1f) / pages.size,
        animationSpec = tween(420),
        label = "lessonPageProgress",
    )

    fun goBack() {
        helpVisible = false
        if (pageIndex == 0) {
            onBack()
        } else {
            pageIndex -= 1
        }
    }

    fun goForward() {
        helpVisible = false
        if (pageIndex == pages.lastIndex) {
            onBack()
        } else {
            pageIndex += 1
        }
    }

    Scaffold(
        containerColor = LessonBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LessonBlack)
                    .systemBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = pageIndex,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        },
                        label = "lessonCounter",
                    ) { index ->
                        Text(
                            "${index + 1} / ${pages.size}",
                            color = LessonMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                LessonProgress(progress = animatedProgress)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LessonBlack)
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = helpVisible,
                    enter = fadeIn(tween(220)) + expandVertically(tween(280)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(220)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(LessonYellow))
                        Text(
                            helpFor(page),
                            color = LessonWhite.copy(alpha = 0.72f),
                            lineHeight = 23.sp,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LessonOutlinedAction(
                        label = "返回",
                        color = LessonWhite.copy(alpha = 0.78f),
                        modifier = Modifier.weight(1f),
                        onClick = ::goBack,
                    )
                    LessonOutlinedAction(
                        label = "我没看懂",
                        color = LessonYellow,
                        modifier = Modifier.weight(1f),
                    ) {
                        helpVisible = !helpVisible
                    }
                    LessonOutlinedAction(
                        label = "继续",
                        color = LessonBlue,
                        modifier = Modifier.weight(1f),
                        onClick = ::goForward,
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = pageIndex,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                if (targetState > initialState) {
                    (fadeIn(tween(340)) + slideInHorizontally(tween(430)) { width -> width / 4 }) togetherWith
                        (fadeOut(tween(190)) + slideOutHorizontally(tween(310)) { width -> -width / 5 })
                } else {
                    (fadeIn(tween(340)) + slideInHorizontally(tween(430)) { width -> -width / 4 }) togetherWith
                        (fadeOut(tween(190)) + slideOutHorizontally(tween(310)) { width -> width / 5 })
                }
            },
            label = "lessonPageTransition",
        ) { index ->
            val currentPage = pages[index]
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    currentPage.title,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    color = LessonWhite,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                when (currentPage) {
                    LessonPage.INTUITION -> LessonIntuition(lesson)
                    LessonPage.PITFALL -> LessonPitfall(lesson)
                    LessonPage.TEXTBOOK -> LessonTextbook(lesson)
                    LessonPage.PRACTICE -> LessonPractice(
                        lesson = lesson,
                        settings = aiSettings,
                        progress = progress,
                        onRecordAttempt = onRecordAttempt,
                    )
                    LessonPage.SUMMARY -> LessonSummary(lesson, progress)
                }
            }
        }
    }
}

@Composable
private fun LessonIntuition(lesson: Lesson) = LessonScroll {
    Spacer(Modifier.height(34.dp))
    Text(
        lesson.explanation,
        color = LessonWhite,
        fontSize = 25.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(42.dp))
    lesson.objectives.forEachIndexed { index, objective ->
        var visible by rememberSaveable(index) { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(index * 130L + 120L)
            visible = true
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) + expandVertically(tween(320)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "%02d".format(index + 1),
                    color = LessonBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    objective,
                    modifier = Modifier.weight(1f),
                    color = LessonWhite.copy(alpha = 0.78f),
                    fontSize = 18.sp,
                    lineHeight = 27.sp,
                )
            }
        }
        if (index != lesson.objectives.lastIndex) LessonDivider()
    }
}

@Composable
private fun LessonPitfall(lesson: Lesson) = LessonScroll {
    Spacer(Modifier.height(80.dp))
    Text(
        "先停一下。",
        color = LessonRed,
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(28.dp))
    Box(Modifier.fillMaxWidth().height(2.dp).background(LessonRed))
    Spacer(Modifier.height(28.dp))
    Text(
        lesson.commonMistake,
        color = LessonWhite,
        fontSize = 23.sp,
        lineHeight = 34.sp,
    )
    Spacer(Modifier.height(30.dp))
    Text(
        "看到负号时，先回到位置关系。",
        color = LessonMuted,
        lineHeight = 23.sp,
    )
}

@Composable
private fun LessonTextbook(lesson: Lesson) = LessonScroll {
    Spacer(Modifier.height(90.dp))
    Text("教材", color = LessonMuted, fontSize = 15.sp)
    Spacer(Modifier.height(10.dp))
    Text(
        "${lesson.textbookPages.first}—${lesson.textbookPages.last}",
        color = LessonYellow,
        fontSize = 72.sp,
        fontWeight = FontWeight.Bold,
    )
    Text("页", color = LessonWhite, fontSize = 25.sp)
    Spacer(Modifier.height(40.dp))
    LessonDivider()
    Spacer(Modifier.height(24.dp))
    Text(
        "真实教材导入后，这里直接打开对应页。\n不再让你从整本书里寻找上下文。",
        color = LessonWhite.copy(alpha = 0.67f),
        fontSize = 18.sp,
        lineHeight = 28.sp,
    )
}

@Composable
private fun LessonPractice(
    lesson: Lesson,
    settings: AiSettings,
    progress: LearningProgress,
    onRecordAttempt: (answer: String, correct: Boolean, feedback: String) -> Unit,
) {
    var answer by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var mistakeType by rememberSaveable { mutableStateOf<String?>(null) }
    var hint by rememberSaveable { mutableIntStateOf(0) }
    var isEvaluating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val question = "在数轴上，-3 与 2 哪个数更大？请简单说明理由。"
    val hints = listOf(
        "先看左右，不要先计算。",
        "-3 在原点左边，2 在原点右边。",
        "数轴右边的数更大。",
    )

    LaunchedEffect(progress.lastLessonId, progress.lastAnswer) {
        if (progress.lastLessonId == lesson.id && answer.isBlank()) {
            answer = progress.lastAnswer
            result = progress.lastFeedback.takeIf { it.isNotBlank() }
        }
    }

    LessonScroll {
        Spacer(Modifier.height(20.dp))
        Text(
            question,
            color = LessonWhite,
            fontSize = 27.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(40.dp))
        LessonInput(
            value = answer,
            onValueChange = {
                answer = it
                result = null
                mistakeType = null
            },
        )
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LessonOutlinedAction(
                label = "提示",
                color = LessonYellow,
                modifier = Modifier.weight(1f),
            ) {
                hint = (hint + 1).coerceAtMost(hints.size)
            }
            LessonOutlinedAction(
                label = "本地检查",
                color = LessonBlue,
                enabled = answer.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                val compact = answer.replace(" ", "")
                val correct = compact.contains("2") && (compact.contains("大") || compact.contains(">"))
                val feedback = if (correct) {
                    "判断正确。2 位于 -3 的右侧。"
                } else {
                    "再检查两个数在数轴上的左右位置。"
                }
                result = feedback
                mistakeType = if (correct) null else "位置关系"
                onRecordAttempt(answer, correct, feedback)
            }
            LessonOutlinedAction(
                label = if (isEvaluating) "批改中…" else "AI 批改",
                color = LessonRed,
                enabled = answer.isNotBlank() &&
                    !isEvaluating &&
                    settings.endpoint.isNotBlank() &&
                    settings.model.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                isEvaluating = true
                result = "正在理解你的答案…"
                scope.launch {
                    val evaluation = runCatching {
                        OpenAiCompatibleClient(settings).evaluateAnswer(question, answer)
                    }.getOrElse { error ->
                        result = "AI 批改失败：${error.message ?: error::class.java.simpleName}"
                        mistakeType = "连接失败"
                        isEvaluating = false
                        return@launch
                    }
                    result = evaluation.feedback
                    mistakeType = evaluation.mistakeType
                    onRecordAttempt(answer, evaluation.correct, evaluation.feedback)
                    isEvaluating = false
                }
            }
        }
        AnimatedVisibility(
            visible = hint > 0,
            enter = fadeIn(tween(220)) + expandVertically(tween(260)),
            exit = fadeOut() + shrinkVertically(),
        ) {
            LessonInlineNotice(
                color = LessonYellow,
                label = "提示 $hint",
                body = hints[(hint - 1).coerceAtLeast(0)],
            )
        }
        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn(tween(240)) + expandVertically(tween(300)),
            exit = fadeOut() + shrinkVertically(),
        ) {
            LessonInlineNotice(
                color = if (mistakeType == null) LessonBlue else LessonRed,
                label = mistakeType?.let { "需要检查 · $it" } ?: "反馈",
                body = result.orEmpty(),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LessonSummary(lesson: Lesson, progress: LearningProgress) = LessonScroll {
    Spacer(Modifier.height(80.dp))
    Text("完成", color = LessonBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Text(
        lesson.title,
        color = LessonWhite,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(34.dp))
    LessonDivider()
    Spacer(Modifier.height(26.dp))
    lesson.objectives.forEach { objective ->
        Text(
            "—  $objective",
            modifier = Modifier.padding(vertical = 7.dp),
            color = LessonWhite.copy(alpha = 0.7f),
            fontSize = 17.sp,
            lineHeight = 25.sp,
        )
    }
    Spacer(Modifier.height(28.dp))
    Text(
        "累计 ${progress.attempts} 次作答 · 正确率 ${progress.accuracyPercent}%",
        color = LessonYellow,
    )
}

@Composable
private fun LessonOutlinedAction(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) color else color.copy(alpha = 0.25f)
    val borderColor = if (enabled) color.copy(alpha = 0.82f) else color.copy(alpha = 0.18f)
    Box(
        modifier = modifier
            .height(48.dp)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun LessonInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("你的答案", color = LessonMuted, style = MaterialTheme.typography.labelMedium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            textStyle = TextStyle(color = LessonWhite, fontSize = 18.sp, lineHeight = 26.sp),
            cursorBrush = SolidColor(LessonBlue),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (value.isEmpty()) {
                        Text("输入…", color = LessonWhite.copy(alpha = 0.2f), fontSize = 18.sp)
                    }
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(LessonLine))
    }
}

@Composable
private fun LessonInlineNotice(color: Color, label: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(color))
        Text(label, color = color, fontWeight = FontWeight.Bold)
        Text(body, color = LessonWhite.copy(alpha = 0.74f), lineHeight = 23.sp)
    }
}

@Composable
private fun LessonProgress(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(LessonLine),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(LessonBlue),
        )
    }
}

@Composable
private fun LessonDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LessonLine))
}

@Composable
private fun LessonScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        content = content,
    )
}

private fun helpFor(page: LessonPage): String = when (page) {
    LessonPage.INTUITION -> "先不要背定义。只抓住：数轴右边更大，左边更小。"
    LessonPage.PITFALL -> "看到负号时，先回到位置关系，不要只比较数字表面大小。"
    LessonPage.TEXTBOOK -> "只看对应页的定义和例题，不必一次读完整章。"
    LessonPage.PRACTICE -> "先写结论，再补一句理由。理由不完整也比空白更容易诊断。"
    LessonPage.SUMMARY -> "能说出这节课最重要的一句话，就已经完成了一次有效学习。"
}
