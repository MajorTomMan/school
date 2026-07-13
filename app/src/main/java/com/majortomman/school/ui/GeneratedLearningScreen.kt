package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.ai.OpenAiCompatibleClient
import com.majortomman.school.data.AiSettings
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.Lesson
import com.majortomman.school.data.material.GeneratedExercise
import com.majortomman.school.data.material.InstalledMaterialPack
import com.majortomman.school.data.material.LessonAnalysis
import com.majortomman.school.data.material.LessonSceneSpec
import com.majortomman.school.data.material.LessonSceneType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GeneratedBlack = Color(0xFF050608)
private val GeneratedWhite = Color(0xFFF5F7FA)
private val GeneratedBlue = Color(0xFF2D7BFF)
private val GeneratedRed = Color(0xFFFF453A)
private val GeneratedYellow = Color(0xFFFFCC00)
private val GeneratedMuted = GeneratedWhite.copy(alpha = 0.46f)
private val GeneratedLine = GeneratedWhite.copy(alpha = 0.13f)

private enum class GeneratedPage(val title: String) {
    QUESTION("先想一想"),
    VISUAL("动态观察"),
    INTUITION("核心直觉"),
    PITFALL("容易踩坑"),
    TEXTBOOK("教材定位"),
    PRACTICE("独立练习"),
    SUMMARY("完成"),
}

@Composable
fun GeneratedLearningScreen(
    lesson: Lesson,
    analysis: LessonAnalysis,
    aiSettings: AiSettings,
    progress: LearningProgress,
    installedMaterial: InstalledMaterialPack,
    onOpenTextbook: (Int) -> Unit,
    onBack: () -> Unit,
    onRecordAttempt: (answer: String, correct: Boolean, feedback: String) -> Unit,
) {
    var pageIndex by rememberSaveable(lesson.id) { mutableIntStateOf(0) }
    var helpVisible by rememberSaveable(lesson.id) { mutableStateOf(false) }
    val pages = GeneratedPage.entries
    val currentPage = pages[pageIndex]
    val progressValue by animateFloatAsState(
        targetValue = (pageIndex + 1f) / pages.size,
        animationSpec = tween(420),
        label = "generatedLessonProgress",
    )

    fun goBack() {
        helpVisible = false
        if (pageIndex == 0) onBack() else pageIndex -= 1
    }

    fun goForward() {
        helpVisible = false
        if (pageIndex == pages.lastIndex) onBack() else pageIndex += 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeneratedBlack)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    analysis.source.label,
                    color = GeneratedMuted,
                    fontSize = 12.sp,
                )
                AnimatedContent(
                    targetState = pageIndex,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                    label = "generatedPageCounter",
                ) { index ->
                    Text("${index + 1} / ${pages.size}", color = GeneratedMuted, fontSize = 12.sp)
                }
            }
            GeneratedProgress(progressValue)
        }

        AnimatedContent(
            targetState = pageIndex,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                if (targetState > initialState) {
                    (fadeIn(tween(330)) + slideInHorizontally(tween(430)) { it / 4 }) togetherWith
                        (fadeOut(tween(180)) + slideOutHorizontally(tween(300)) { -it / 5 })
                } else {
                    (fadeIn(tween(330)) + slideInHorizontally(tween(430)) { -it / 4 }) togetherWith
                        (fadeOut(tween(180)) + slideOutHorizontally(tween(300)) { it / 5 })
                }
            },
            label = "generatedLessonPages",
        ) { index ->
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    pages[index].title,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    color = GeneratedWhite,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                when (pages[index]) {
                    GeneratedPage.QUESTION -> GeneratedQuestion(analysis.scene)
                    GeneratedPage.VISUAL -> GeneratedVisual(analysis.scene)
                    GeneratedPage.INTUITION -> GeneratedIntuition(analysis)
                    GeneratedPage.PITFALL -> GeneratedPitfall(analysis)
                    GeneratedPage.TEXTBOOK -> GeneratedTextbook(analysis, installedMaterial, onOpenTextbook)
                    GeneratedPage.PRACTICE -> GeneratedPractice(
                        lesson = lesson,
                        exercise = analysis.exercise,
                        settings = aiSettings,
                        progress = progress,
                        onRecordAttempt = onRecordAttempt,
                    )
                    GeneratedPage.SUMMARY -> GeneratedSummary(lesson, analysis, progress)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GeneratedBlack)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = helpVisible,
                enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(210)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(GeneratedYellow))
                    Text(
                        generatedHelp(currentPage, analysis),
                        color = GeneratedWhite.copy(alpha = 0.72f),
                        lineHeight = 23.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GeneratedOutlinedAction(
                    label = "返回",
                    color = GeneratedWhite.copy(alpha = 0.78f),
                    modifier = Modifier.weight(1f),
                    onClick = ::goBack,
                )
                GeneratedOutlinedAction(
                    label = "我没看懂",
                    color = GeneratedYellow,
                    modifier = Modifier.weight(1f),
                ) { helpVisible = !helpVisible }
                GeneratedOutlinedAction(
                    label = if (currentPage == GeneratedPage.SUMMARY) "完成" else "继续",
                    color = GeneratedBlue,
                    modifier = Modifier.weight(1f),
                    onClick = ::goForward,
                )
            }
        }
    }
}

@Composable
private fun GeneratedQuestion(scene: LessonSceneSpec) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            scene.prompt,
            color = GeneratedWhite,
            fontSize = 42.sp,
            lineHeight = 50.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "先不要背结论。观察对象、位置或变化。",
            color = GeneratedMuted,
            fontSize = 17.sp,
            lineHeight = 26.sp,
        )
    }
}

@Composable
private fun GeneratedVisual(scene: LessonSceneSpec) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            scene.title,
            modifier = Modifier.padding(horizontal = 8.dp),
            color = GeneratedWhite,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(18.dp))
        when (scene.type) {
            LessonSceneType.NUMBER_LINE,
            LessonSceneType.MIRROR,
            LessonSceneType.DISTANCE,
            LessonSceneType.COMPARISON,
            -> GeneratedNumberLine(scene)
            LessonSceneType.PROCESS -> GeneratedProcess(scene)
            LessonSceneType.TEXT -> GeneratedTextRelation(scene)
        }
        if (scene.expression.isNotBlank()) {
            Text(
                scene.expression,
                modifier = Modifier.fillMaxWidth(),
                color = GeneratedYellow,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GeneratedNumberLine(scene: LessonSceneSpec) {
    val axis = remember(scene) { Animatable(0f) }
    val points = remember(scene) { Animatable(0f) }
    LaunchedEffect(scene) {
        axis.snapTo(0f)
        points.snapTo(0f)
        axis.animateTo(1f, animationSpec = tween(800))
        points.animateTo(1f, animationSpec = tween(650))
    }
    val values = scene.values.ifEmpty { listOf(-3.0, 2.0) }
    val minValue = minOf(values.minOrNull() ?: -3.0, 0.0)
    val maxValue = maxOf(values.maxOrNull() ?: 3.0, 0.0)
    val padding = ((maxValue - minValue) * 0.22).coerceAtLeast(1.0)
    val domainMin = minValue - padding
    val domainMax = maxValue + padding

    Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
        val left = 28.dp.toPx()
        val right = size.width - 28.dp.toPx()
        val centerY = size.height * 0.55f
        val axisEnd = left + (right - left) * axis.value
        drawLine(
            color = GeneratedWhite.copy(alpha = 0.72f),
            start = Offset(left, centerY),
            end = Offset(axisEnd, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val zeroX = left + ((0.0 - domainMin) / (domainMax - domainMin)).toFloat() * (right - left)
        if (zeroX <= axisEnd) {
            drawLine(
                color = GeneratedWhite.copy(alpha = 0.65f),
                start = Offset(zeroX, centerY - 9.dp.toPx()),
                end = Offset(zeroX, centerY + 9.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            val paint = Paint().apply {
                color = GeneratedMuted.toArgb()
                textSize = 13.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText("0", zeroX, centerY + 31.dp.toPx(), paint)
        }

        values.take(4).forEachIndexed { index, value ->
            val fraction = ((value - domainMin) / (domainMax - domainMin)).toFloat().coerceIn(0f, 1f)
            val x = left + fraction * (right - left)
            val color = when (index) {
                0 -> GeneratedRed
                1 -> GeneratedBlue
                2 -> GeneratedYellow
                else -> GeneratedWhite
            }
            val landingY = centerY - 34.dp.toPx() * (1f - points.value)
            drawCircle(color.copy(alpha = points.value), 8.dp.toPx() * points.value, Offset(x, landingY))
            if (scene.type == LessonSceneType.DISTANCE && points.value > 0.5f) {
                drawLine(
                    color = color.copy(alpha = 0.42f * points.value),
                    start = Offset(minOf(x, zeroX), centerY + 42.dp.toPx() + index * 13.dp.toPx()),
                    end = Offset(maxOf(x, zeroX), centerY + 42.dp.toPx() + index * 13.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val label = scene.labels.getOrNull(index) ?: formatSceneNumber(value)
            val paint = Paint().apply {
                this.color = color.copy(alpha = points.value).toArgb()
                textSize = 17.sp.toPx()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(label, x, centerY - 20.dp.toPx(), paint)
        }
    }
}

@Composable
private fun GeneratedProcess(scene: LessonSceneSpec) {
    val steps = scene.steps.ifEmpty { listOf("找到对象", "确认条件", "建立关系", "得到结论") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        steps.forEachIndexed { index, step ->
            var visible by rememberSaveable(scene.title, index) { mutableStateOf(false) }
            LaunchedEffect(scene.title, index) {
                delay(130L * index + 100L)
                visible = true
            }
            AnimatedVisibility(visible, enter = fadeIn(tween(300)) + expandVertically(tween(280))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%02d".format(index + 1), color = GeneratedBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(step, modifier = Modifier.weight(1f), color = GeneratedWhite, fontSize = 20.sp, lineHeight = 28.sp)
                }
            }
            if (index != steps.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(GeneratedLine))
        }
    }
}

@Composable
private fun GeneratedTextRelation(scene: LessonSceneSpec) {
    val labels = scene.labels.ifEmpty { scene.steps.ifEmpty { listOf(scene.title, scene.conclusion) } }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        labels.take(5).forEachIndexed { index, label ->
            Text(
                label,
                color = when (index % 3) {
                    0 -> GeneratedBlue
                    1 -> GeneratedYellow
                    else -> GeneratedWhite
                },
                fontSize = if (index == 0) 30.sp else 22.sp,
                lineHeight = 32.sp,
                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun GeneratedIntuition(analysis: LessonAnalysis) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            analysis.summary,
            color = GeneratedWhite,
            fontSize = 25.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(38.dp))
        analysis.objectives.forEachIndexed { index, objective ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("%02d".format(index + 1), color = GeneratedBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    objective,
                    modifier = Modifier.weight(1f),
                    color = GeneratedWhite.copy(alpha = 0.78f),
                    fontSize = 18.sp,
                    lineHeight = 27.sp,
                )
            }
            if (index != analysis.objectives.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(GeneratedLine))
        }
        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun GeneratedPitfall(analysis: LessonAnalysis) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(72.dp))
        Text("先停一下。", color = GeneratedRed, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(GeneratedRed))
        Spacer(Modifier.height(28.dp))
        Text(analysis.misconception, color = GeneratedWhite, fontSize = 23.sp, lineHeight = 34.sp)
        Spacer(Modifier.height(28.dp))
        Text("不确定时，回到教材第 ${analysis.scene.sourcePage} 页核对。", color = GeneratedMuted, lineHeight = 23.sp)
    }
}

@Composable
private fun GeneratedTextbook(
    analysis: LessonAnalysis,
    installedMaterial: InstalledMaterialPack,
    onOpenTextbook: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(68.dp))
        Text("来源", color = GeneratedMuted, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "${analysis.sourcePages.first}—${analysis.sourcePages.last}",
            color = GeneratedYellow,
            fontSize = 68.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("页", color = GeneratedWhite, fontSize = 24.sp)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(GeneratedLine))
        Spacer(Modifier.height(22.dp))
        Text(
            installedMaterial.manifest.title + " · " + installedMaterial.manifest.version,
            color = GeneratedWhite.copy(alpha = 0.67f),
            fontSize = 17.sp,
        )
        Spacer(Modifier.height(24.dp))
        GeneratedOutlinedAction(
            label = "打开第 ${analysis.scene.sourcePage} 页",
            color = GeneratedYellow,
            modifier = Modifier.fillMaxWidth(),
        ) { onOpenTextbook(analysis.scene.sourcePage) }
    }
}

@Composable
private fun GeneratedPractice(
    lesson: Lesson,
    exercise: GeneratedExercise,
    settings: AiSettings,
    progress: LearningProgress,
    onRecordAttempt: (answer: String, correct: Boolean, feedback: String) -> Unit,
) {
    var answer by rememberSaveable(lesson.id) { mutableStateOf("") }
    var hintIndex by rememberSaveable(lesson.id) { mutableIntStateOf(0) }
    var feedback by rememberSaveable(lesson.id) { mutableStateOf<String?>(null) }
    var evaluating by rememberSaveable(lesson.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(exercise.question, color = GeneratedWhite, fontSize = 25.sp, lineHeight = 35.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(32.dp))
        Text("你的答案", color = GeneratedMuted, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = answer,
            onValueChange = { answer = it; feedback = null },
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .border(1.dp, GeneratedWhite.copy(alpha = 0.24f), RoundedCornerShape(7.dp))
                .padding(15.dp),
            textStyle = TextStyle(color = GeneratedWhite, fontSize = 18.sp, lineHeight = 25.sp),
            cursorBrush = SolidColor(GeneratedBlue),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.TopStart) {
                    if (answer.isBlank()) Text("输入你的判断和理由…", color = GeneratedWhite.copy(alpha = 0.2f), fontSize = 17.sp)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GeneratedOutlinedAction(
                label = "提示",
                color = GeneratedYellow,
                modifier = Modifier.weight(1f),
            ) {
                hintIndex = (hintIndex + 1).coerceAtMost(exercise.hints.size)
            }
            GeneratedOutlinedAction(
                label = "本地检查",
                color = GeneratedBlue,
                modifier = Modifier.weight(1f),
                enabled = answer.isNotBlank(),
            ) {
                val correct = locallyMatches(answer, exercise.acceptedAnswers)
                feedback = when {
                    exercise.acceptedAnswers.isEmpty() -> "这道题需要判断解释是否完整，请使用 AI 批改。"
                    correct -> "方向正确。${exercise.explanation}"
                    else -> "暂时没有命中关键结论。先检查条件、对象和最终关系。"
                }
                if (exercise.acceptedAnswers.isNotEmpty()) onRecordAttempt(answer, correct, feedback.orEmpty())
            }
            GeneratedOutlinedAction(
                label = if (evaluating) "批改中" else "AI 批改",
                color = GeneratedRed,
                modifier = Modifier.weight(1f),
                enabled = answer.isNotBlank() && !evaluating,
            ) {
                evaluating = true
                scope.launch {
                    val evaluation = runCatching {
                        OpenAiCompatibleClient(settings).evaluateAnswer(exercise.question, answer)
                    }
                    evaluation.onSuccess { result ->
                        feedback = result.feedback
                        onRecordAttempt(answer, result.correct, result.feedback)
                    }.onFailure { error ->
                        feedback = "AI 批改失败：${error.message ?: "无法连接模型"}"
                    }
                    evaluating = false
                }
            }
        }
        AnimatedVisibility(hintIndex > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(GeneratedYellow))
                Text(
                    exercise.hints.take(hintIndex).joinToString("\n\n"),
                    color = GeneratedWhite.copy(alpha = 0.76f),
                    lineHeight = 24.sp,
                )
            }
        }
        AnimatedVisibility(feedback != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(modifier = Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(2.dp).background(GeneratedBlue))
                Text(feedback.orEmpty(), color = GeneratedWhite.copy(alpha = 0.8f), lineHeight = 24.sp)
            }
        }
        if (progress.lastLessonId == lesson.id && progress.lastFeedback.isNotBlank()) {
            Spacer(Modifier.height(22.dp))
            Text("上次反馈 · ${progress.lastFeedback}", color = GeneratedMuted, fontSize = 13.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(38.dp))
    }
}

@Composable
private fun GeneratedSummary(
    lesson: Lesson,
    analysis: LessonAnalysis,
    progress: LearningProgress,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("完成", color = GeneratedBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text(lesson.title, color = GeneratedWhite, fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        Text(analysis.scene.conclusion, color = GeneratedWhite.copy(alpha = 0.68f), fontSize = 18.sp, lineHeight = 28.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Text("累计完成 ${progress.attempts} 次练习", color = GeneratedMuted, fontSize = 13.sp)
    }
}

@Composable
private fun GeneratedOutlinedAction(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .border(1.dp, if (enabled) color.copy(alpha = 0.8f) else GeneratedLine, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) color else GeneratedMuted.copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun GeneratedProgress(progress: Float) {
    Canvas(modifier = Modifier.fillMaxWidth().height(3.dp)) {
        drawLine(
            color = GeneratedWhite.copy(alpha = 0.12f),
            start = Offset.Zero,
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = GeneratedBlue,
            start = Offset.Zero,
            end = Offset(size.width * progress.coerceIn(0f, 1f), 0f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private fun generatedHelp(page: GeneratedPage, analysis: LessonAnalysis): String = when (page) {
    GeneratedPage.QUESTION -> "先指出题目中的对象，再说它们之间可能存在的位置、数量或变化关系。"
    GeneratedPage.VISUAL -> analysis.scene.steps.firstOrNull() ?: "跟着动画逐步观察，不需要一次理解全部结论。"
    GeneratedPage.INTUITION -> analysis.objectives.firstOrNull() ?: analysis.summary
    GeneratedPage.PITFALL -> "把错误说法与教材第 ${analysis.scene.sourcePage} 页逐句对照，找出缺少的条件。"
    GeneratedPage.TEXTBOOK -> "打开教材原页，确认课程结论确实来自原文、图形或例题。"
    GeneratedPage.PRACTICE -> analysis.exercise.hints.firstOrNull() ?: "先写出你能确定的一步，再继续补充理由。"
    GeneratedPage.SUMMARY -> "试着不看屏幕，用自己的话复述动画揭示的关系。"
}

private fun locallyMatches(answer: String, acceptedAnswers: List<String>): Boolean {
    if (acceptedAnswers.isEmpty()) return false
    val normalizedAnswer = normalizeAnswer(answer)
    return acceptedAnswers.any { accepted ->
        val normalizedAccepted = normalizeAnswer(accepted)
        normalizedAccepted.isNotEmpty() &&
            (normalizedAnswer.contains(normalizedAccepted) || normalizedAccepted.contains(normalizedAnswer))
    }
}

private fun normalizeAnswer(value: String): String = value
    .lowercase()
    .replace(Regex("[\\s，。；：、,.!?！？=]"), "")

private fun formatSceneNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')
