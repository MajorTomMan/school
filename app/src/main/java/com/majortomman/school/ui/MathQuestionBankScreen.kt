package com.majortomman.school.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.math.MathKnowledgeCatalog
import com.majortomman.school.data.math.MathPracticeMode
import com.majortomman.school.data.math.MathQuestion
import com.majortomman.school.data.math.MathQuestionBankRepository
import com.majortomman.school.data.math.MathQuestionType
import com.majortomman.school.data.math.MathSubmissionResult
import com.majortomman.school.data.material.InstalledTextbook
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

private val BankBlack = Color(0xFF050608)
private val BankWhite = Color(0xFFF5F7FA)
private val BankBlue = Color(0xFF2D7BFF)
private val BankRed = Color(0xFFFF453A)
private val BankYellow = Color(0xFFFFCC00)
private val BankMuted = BankWhite.copy(alpha = 0.47f)
private val BankLine = BankWhite.copy(alpha = 0.14f)

@Composable
fun MathQuestionBankScreen(
    repository: MathQuestionBankRepository,
    textbook: InstalledTextbook?,
    onOpenSubjects: () -> Unit,
    onOpenTextbook: (Int) -> Unit,
) {
    if (textbook == null || textbook.slot.subjectId != "math") {
        MathBankEmptyScreen(onOpenSubjects)
        return
    }

    val scope = rememberCoroutineScope()
    val masteryFlow = remember(textbook.key) { repository.observeMastery(textbook) }
    val mistakesFlow = remember(textbook.key) { repository.observeMistakes(textbook) }
    val attemptsFlow = remember(textbook.key) { repository.observeRecentAttempts(textbook) }
    val mastery by masteryFlow.collectAsState(initial = emptyList())
    val mistakes by mistakesFlow.collectAsState(initial = emptyList())
    val attempts by attemptsFlow.collectAsState(initial = emptyList())

    var selectedModeName by rememberSaveable(textbook.key) { mutableStateOf<String?>(null) }
    var questionJson by rememberSaveable(textbook.key) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val selectedMode = selectedModeName?.let { runCatching { MathPracticeMode.valueOf(it) }.getOrNull() }
    val question = questionJson?.let { raw -> runCatching { MathQuestion.fromJson(JSONObject(raw)) }.getOrNull() }

    fun loadQuestion(mode: MathPracticeMode) {
        selectedModeName = mode.name
        loading = true
        errorMessage = null
        scope.launch {
            runCatching { repository.nextQuestion(textbook, mode) }
                .onSuccess { generated -> questionJson = generated.toJson().toString() }
                .onFailure { error -> errorMessage = error.message ?: "暂时无法生成题目" }
            loading = false
        }
    }

    AnimatedContent(
        targetState = question,
        modifier = Modifier.fillMaxSize().background(BankBlack),
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(150)) },
        label = "mathBankNavigation",
    ) { currentQuestion ->
        if (currentQuestion == null) {
            MathBankOverview(
                textbook = textbook,
                mastery = mastery,
                mistakeCount = mistakes.size,
                recentAccuracy = attempts.take(10).let { recent ->
                    if (recent.isEmpty()) 0 else recent.count { it.correct } * 100 / recent.size
                },
                loading = loading,
                errorMessage = errorMessage,
                onStart = ::loadQuestion,
            )
        } else {
            MathQuestionPracticePage(
                question = currentQuestion,
                mode = selectedMode ?: MathPracticeMode.MIXED,
                loadingNext = loading,
                onOpenTextbook = onOpenTextbook,
                onSubmit = { answer, usedHint, duration, onResult ->
                    scope.launch {
                        runCatching {
                            repository.submit(currentQuestion, answer, usedHint, duration)
                        }.onSuccess(onResult)
                            .onFailure { error ->
                                onResult(
                                    MathSubmissionResult(
                                        question = currentQuestion,
                                        evaluation = com.majortomman.school.data.math.MathAnswerEvaluation(
                                            correct = false,
                                            canonicalAnswer = currentQuestion.canonicalAnswer,
                                            feedback = error.message ?: "保存作答失败",
                                            mistakeType = "系统错误",
                                            normalizedAnswer = answer,
                                        ),
                                        masteryScore = 0.0,
                                    ),
                                )
                            }
                    }
                },
                onNext = { loadQuestion(selectedMode ?: MathPracticeMode.MIXED) },
                onBack = {
                    questionJson = null
                    errorMessage = null
                },
            )
        }
    }
}

@Composable
private fun MathBankEmptyScreen(onOpenSubjects: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BankBlack)
            .systemBarsPadding()
            .padding(horizontal = 26.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("数学题库", color = BankWhite, fontSize = 46.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(18.dp))
        Text(
            "先在学科页选择并进入一本数学教材。题库会根据教材章节、薄弱知识点和错题记录生成练习。",
            color = BankMuted,
            fontSize = 17.sp,
            lineHeight = 27.sp,
        )
        Spacer(Modifier.height(30.dp))
        BankAction("前往学科", BankBlue, onOpenSubjects)
    }
}

@Composable
private fun MathBankOverview(
    textbook: InstalledTextbook,
    mastery: List<com.majortomman.school.data.math.MathMasterySnapshot>,
    mistakeCount: Int,
    recentAccuracy: Int,
    loading: Boolean,
    errorMessage: String?,
    onStart: (MathPracticeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 28.dp),
    ) {
        Text("数学题库", color = BankWhite, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(textbook.slot.displayTitle, color = BankMuted, fontSize = 15.sp)
        Spacer(Modifier.height(34.dp))

        MathPracticeMode.entries.forEachIndexed { index, mode ->
            val suffix = when (mode) {
                MathPracticeMode.MISTAKES -> "$mistakeCount 道待巩固"
                MathPracticeMode.WEAKNESS -> mastery.firstOrNull()?.let { "${it.title} · ${it.percent}%" } ?: "建立掌握度"
                MathPracticeMode.TEXTBOOK -> "依据教材原题线索"
                MathPracticeMode.MIXED -> if (recentAccuracy == 0) "开始综合练习" else "最近正确率 $recentAccuracy%"
            }
            ModeRow(mode, suffix, loading) { onStart(mode) }
            if (index != MathPracticeMode.entries.lastIndex) BankDivider()
        }

        errorMessage?.let {
            Spacer(Modifier.height(18.dp))
            Text(it, color = BankRed, fontSize = 14.sp, lineHeight = 21.sp)
        }

        Spacer(Modifier.height(42.dp))
        Text("知识掌握", color = BankWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(17.dp))
        if (mastery.isEmpty()) {
            Text("完成几道题后，这里会显示每个知识点的真实掌握情况。", color = BankMuted, fontSize = 15.sp)
        } else {
            mastery.forEach { point ->
                MasteryLine(point.title, point.percent, point.attempts)
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun ModeRow(
    mode: MathPracticeMode,
    suffix: String,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(vertical = 21.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.72f)) {
            Text(mode.label, color = BankWhite, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(mode.description, color = BankMuted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        Text(if (disabled) "准备中" else suffix, color = BankYellow, fontSize = 12.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun MasteryLine(title: String, percent: Int, attempts: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = BankWhite, fontSize = 16.sp)
            Text("$percent% · $attempts 次", color = BankMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(9.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(BankLine)) {
            Box(
                Modifier
                    .fillMaxWidth((percent / 100f).coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(when {
                        percent < 45 -> BankRed
                        percent < 75 -> BankYellow
                        else -> BankBlue
                    }),
            )
        }
    }
}

@Composable
private fun MathQuestionPracticePage(
    question: MathQuestion,
    mode: MathPracticeMode,
    loadingNext: Boolean,
    onOpenTextbook: (Int) -> Unit,
    onSubmit: (String, Boolean, Long, (MathSubmissionResult) -> Unit) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    var answer by remember(question.id) { mutableStateOf("") }
    var selectedOrder by remember(question.id) { mutableStateOf(emptyList<String>()) }
    var selectedPoint by remember(question.id) { mutableStateOf<Double?>(null) }
    var hintLevel by remember(question.id) { mutableIntStateOf(0) }
    var result by remember(question.id) { mutableStateOf<MathSubmissionResult?>(null) }
    var submitting by remember(question.id) { mutableStateOf(false) }
    var startedAt by remember(question.id) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(question.id) {
        startedAt = System.currentTimeMillis()
    }

    val finalAnswer = when (question.type) {
        MathQuestionType.ORDERING -> selectedOrder.joinToString(",")
        MathQuestionType.NUMBER_LINE_POINT -> selectedPoint?.toString().orEmpty()
        else -> answer
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("返回题库", color = BankMuted, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onBack))
            Text(mode.label, color = BankYellow, fontSize = 12.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            MathKnowledgeCatalog.find(question.knowledgePointId).title,
            color = BankBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${question.difficulty.label} · ${question.type.label} · ${question.source.label}",
            color = BankMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(28.dp))
        Text(question.prompt, color = BankWhite, fontSize = 30.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium)

        question.sourceExcerpt?.let { excerpt ->
            Spacer(Modifier.height(25.dp))
            BankDivider()
            Spacer(Modifier.height(15.dp))
            Text("教材原题线索", color = BankYellow, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(excerpt, color = BankMuted, fontSize = 14.sp, lineHeight = 22.sp)
            question.sourcePage?.let { page ->
                Spacer(Modifier.height(9.dp))
                Text(
                    "查看教材第 $page 页",
                    color = BankBlue,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onOpenTextbook(page) },
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        when (question.type) {
            MathQuestionType.SINGLE_CHOICE -> ChoiceAnswer(question, answer, result == null) { answer = it }
            MathQuestionType.ORDERING -> OrderingAnswer(question, selectedOrder, result == null) { selectedOrder = it }
            MathQuestionType.NUMBER_LINE_POINT -> NumberLineAnswer(question, selectedPoint, result == null) { selectedPoint = it }
            else -> TextAnswer(question, answer, result == null) { answer = it }
        }

        if (hintLevel > 0) {
            Spacer(Modifier.height(18.dp))
            val hints = question.hints.take(hintLevel)
            hints.forEachIndexed { index, hint ->
                Text("${index + 1}. $hint", color = BankYellow, fontSize = 14.sp, lineHeight = 22.sp)
                if (index != hints.lastIndex) Spacer(Modifier.height(7.dp))
            }
        }

        Spacer(Modifier.height(26.dp))
        val currentResult = result
        if (currentResult == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BankAction(
                    text = if (hintLevel < question.hints.size) "提示 ${hintLevel + 1}" else "提示已展开",
                    accent = BankYellow,
                    enabled = hintLevel < question.hints.size,
                ) { hintLevel += 1 }
                BankAction(
                    text = if (submitting) "检查中" else "提交答案",
                    accent = BankBlue,
                    enabled = !submitting && finalAnswer.isNotBlank(),
                ) {
                    submitting = true
                    onSubmit(
                        finalAnswer,
                        hintLevel > 0,
                        System.currentTimeMillis() - startedAt,
                    ) { submission ->
                        result = submission
                        submitting = false
                    }
                }
            }
        } else {
            ResultBlock(currentResult)
            Spacer(Modifier.height(25.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BankAction("返回题库", BankWhite, onClick = onBack)
                BankAction(
                    if (loadingNext) "生成中" else "下一题",
                    BankBlue,
                    enabled = !loadingNext,
                    onClick = onNext,
                )
            }
        }
        Spacer(Modifier.height(42.dp))
    }
}

@Composable
private fun ChoiceAnswer(
    question: MathQuestion,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        question.options.forEach { option ->
            val active = selected == option.id
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (active) BankBlue else BankLine, RoundedCornerShape(5.dp))
                    .clickable(enabled = enabled) { onSelect(option.id) }
                    .padding(horizontal = 17.dp, vertical = 16.dp),
            ) {
                Text(option.text, color = if (active) BankBlue else BankWhite, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun TextAnswer(
    question: MathQuestion,
    answer: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val placeholder = when (question.type) {
        MathQuestionType.STEP_BY_STEP -> "每一步单独写一行\n例如：2x=6\nx=3"
        MathQuestionType.EXPRESSION_INPUT -> "输入表达式、解集或等价形式"
        else -> "输入答案"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BankLine, RoundedCornerShape(5.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        if (answer.isBlank()) {
            Text(placeholder, color = BankMuted, fontSize = 16.sp, lineHeight = 23.sp)
        }
        BasicTextField(
            value = answer,
            onValueChange = { if (enabled) onChange(it.take(2_000)) },
            modifier = Modifier.fillMaxWidth().height(if (question.type == MathQuestionType.STEP_BY_STEP) 150.dp else 56.dp),
            textStyle = TextStyle(color = BankWhite, fontSize = 19.sp, lineHeight = 27.sp),
            cursorBrush = SolidColor(BankBlue),
            enabled = enabled,
        )
    }
}

@Composable
private fun OrderingAnswer(
    question: MathQuestion,
    selected: List<String>,
    enabled: Boolean,
    onChange: (List<String>) -> Unit,
) {
    Text("当前顺序", color = BankMuted, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Text(
        if (selected.isEmpty()) "依次点击下方数字" else selected.joinToString("  <  "),
        color = if (selected.isEmpty()) BankMuted else BankWhite,
        fontSize = 22.sp,
        lineHeight = 31.sp,
    )
    Spacer(Modifier.height(17.dp))
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        question.orderingItems.filterNot { it in selected }.forEach { item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BankLine, RoundedCornerShape(5.dp))
                    .clickable(enabled = enabled) { onChange(selected + item) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Text(item, color = BankWhite, fontSize = 18.sp)
            }
        }
    }
    if (selected.isNotEmpty() && enabled) {
        Spacer(Modifier.height(12.dp))
        Text("重新排序", color = BankYellow, fontSize = 13.sp, modifier = Modifier.clickable { onChange(emptyList()) })
    }
}

@Composable
private fun NumberLineAnswer(
    question: MathQuestion,
    selected: Double?,
    enabled: Boolean,
    onSelect: (Double) -> Unit,
) {
    val min = question.numberLineMin
    val max = question.numberLineMax
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(question.id, enabled) {
                    detectTapGestures { offset ->
                        if (!enabled) return@detectTapGestures
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val raw = min + fraction * (max - min)
                        onSelect(raw.roundToInt().toDouble())
                    }
                },
        ) {
            val left = 12f
            val right = size.width - 12f
            val centerY = size.height * 0.52f
            drawLine(BankWhite, Offset(left, centerY), Offset(right, centerY), strokeWidth = 3f, cap = StrokeCap.Round)
            val span = (max - min).coerceAtLeast(1)
            for (value in min..max) {
                val x = left + (value - min).toFloat() / span * (right - left)
                val tick = if (value == 0) 18f else 10f
                drawLine(BankMuted, Offset(x, centerY - tick), Offset(x, centerY + tick), strokeWidth = if (value == 0) 3f else 1.5f)
            }
            selected?.let { value ->
                val x = left + ((value - min) / span).toFloat() * (right - left)
                drawCircle(BankBlue, radius = 10f, center = Offset(x, centerY))
                drawLine(BankBlue, Offset(x, centerY - 34f), Offset(x, centerY - 12f), strokeWidth = 3f)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(min.toString(), color = BankMuted, fontSize = 12.sp)
            Text(selected?.roundToInt()?.toString() ?: "点击数轴选择位置", color = if (selected == null) BankMuted else BankBlue, fontSize = 14.sp)
            Text(max.toString(), color = BankMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ResultBlock(result: MathSubmissionResult) {
    val evaluation = result.evaluation
    BankDivider()
    Spacer(Modifier.height(18.dp))
    Text(
        if (evaluation.correct) "回答正确" else "需要再看一步",
        color = if (evaluation.correct) BankBlue else BankRed,
        fontSize = 26.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(10.dp))
    Text(evaluation.feedback, color = BankWhite, fontSize = 16.sp, lineHeight = 25.sp)
    if (!evaluation.correct) {
        Spacer(Modifier.height(13.dp))
        Text("参考答案 · ${evaluation.canonicalAnswer}", color = BankYellow, fontSize = 14.sp)
        evaluation.mistakeType?.let {
            Spacer(Modifier.height(7.dp))
            Text("错误类型 · $it", color = BankRed, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("当前掌握度 ${(result.masteryScore * 100).roundToInt()}%", color = BankMuted, fontSize = 13.sp)
}

@Composable
private fun BankAction(
    text: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .border(1.dp, if (enabled) accent else BankLine, RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) accent else BankMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BankDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BankLine))
}
