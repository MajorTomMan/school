@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.majortomman.school.ai.OpenAiCompatibleClient
import com.majortomman.school.data.AiSettings
import com.majortomman.school.data.DailyPlan
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.Lesson
import com.majortomman.school.data.MasteryStatus
import com.majortomman.school.data.ReviewItem
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    plan: DailyPlan,
    lessons: List<Lesson>,
    progress: LearningProgress,
    onStartLesson: (String) -> Unit,
    onOpenPath: () -> Unit,
) {
    val lesson = lessons.first { it.id == plan.newLessonId }
    val lessonIndex = lessons.indexOfFirst { it.id == lesson.id }.coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            PageHeading(
                eyebrow = "今日学习",
                title = "继续上次的一小步",
                subtitle = "不用挑内容。先把当前知识点完整走完，再决定下一步。",
            )
        }
        item {
            AnimatedCardItem(index = 0) {
                FocusSurface(onClick = { onStartLesson(lesson.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LabelPill("预计 ${plan.estimatedMinutes} 分钟")
                        IconBubble("→")
                    }
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = lesson.subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                    StepProgressBar(
                        currentStep = lessonIndex,
                        totalSteps = lessons.size.coerceAtLeast(1),
                    )
                    Text(
                        text = "第 ${lessonIndex + 1} / ${lessons.size} 个知识点 · 教材 ${lesson.textbookPages.first}-${lesson.textbookPages.last} 页",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                    Button(
                        onClick = { onStartLesson(lesson.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(if (lesson.status == MasteryStatus.MASTERED) "重新走一遍" else "继续学习")
                    }
                }
            }
        }
        item { SectionTitle("今天的节奏") }
        item {
            AnimatedCardItem(index = 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodayStepRow("1", "先理解", "数轴为什么能把抽象的数变成位置", active = true)
                    TodayStepRow("2", "再判断", "用左右位置比较两个有理数", active = false)
                    TodayStepRow("3", "最后练习", "独立写出判断和理由", active = false)
                }
            }
        }
        item {
            SectionTitle(
                title = "到期复习",
                action = { TextButton(onClick = onOpenPath) { Text("查看路径") } },
            )
        }
        itemsIndexed(plan.reviewItems, key = { _, item -> item.id }) { index, item ->
            AnimatedCardItem(index = index + 2) {
                CompactReviewRow(item)
            }
        }
        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("本机已记录 ${progress.attempts} 次作答", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("正确率 ${progress.accuracyPercent}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TodayStepRow(
    number: String,
    title: String,
    description: String,
    active: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, fontWeight = FontWeight.Bold)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactReviewRow(item: ReviewItem) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble("↻", background = MaterialTheme.colorScheme.primaryContainer)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold)
                Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LabelPill(item.dueLabel)
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AiSettings,
    onSave: (AiSettings) -> Unit,
    onClearProgress: () -> Unit,
) {
    var endpoint by rememberSaveable { mutableStateOf(settings.endpoint) }
    var model by rememberSaveable { mutableStateOf(settings.model) }
    var apiKey by rememberSaveable { mutableStateOf(settings.apiKey) }
    var connectionStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var isTesting by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(settings) {
        endpoint = settings.endpoint
        model = settings.model
        apiKey = settings.apiKey
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageHeading(
            eyebrow = "设置",
            title = "把工具调成你的样子",
            subtitle = "AI、教材和学习记录都保持本地优先。",
        )
        MotionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("AI 服务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("OpenAI compatible", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconBubble("AI")
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = endpoint,
                onValueChange = { endpoint = it; connectionStatus = null },
                label = { Text("接口地址") },
                supportingText = { Text("例如 http://192.168.1.2:7777/v1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(18.dp),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = model,
                onValueChange = { model = it; connectionStatus = null },
                label = { Text("模型名称") },
                supportingText = { Text("需要与 /v1/models 返回的 ID 一致") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key（局域网服务可留空）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(18.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = endpoint.isNotBlank() && model.isNotBlank(),
                    onClick = {
                        val updated = AiSettings(endpoint.trim(), model.trim(), apiKey.trim())
                        onSave(updated)
                        connectionStatus = "已保存到本机"
                    },
                    shape = RoundedCornerShape(16.dp),
                ) { Text("保存") }
                OutlinedButton(
                    enabled = !isTesting && endpoint.isNotBlank(),
                    onClick = {
                        isTesting = true
                        connectionStatus = "正在连接……"
                        val updated = AiSettings(endpoint.trim(), model.trim(), apiKey.trim())
                        scope.launch {
                            connectionStatus = OpenAiCompatibleClient(updated).testConnection().fold(
                                onSuccess = { it },
                                onFailure = { "连接失败：${it.message ?: it::class.java.simpleName}" },
                            )
                            isTesting = false
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (isTesting) "测试中" else "测试连接") }
            }
            AnimatedVisibility(
                visible = connectionStatus != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(connectionStatus.orEmpty(), modifier = Modifier.padding(14.dp))
                }
            }
        }
        MotionCard(tone = CardTone.SOFT) {
            Text("教材资源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("七年级数学上册 · 示例目录")
            Text("下一阶段从手机目录导入独立教材包，并直接打开真实 PDF 对应页。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = {}, shape = RoundedCornerShape(16.dp)) { Text("导入功能待接入") }
        }
        MotionCard(tone = CardTone.WARNING) {
            Text("学习数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("练习记录、答案、反馈和掌握状态都保存在本机。")
            AnimatedContent(
                targetState = confirmClear,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "clearConfirmation",
            ) { confirming ->
                if (!confirming) {
                    TextButton(onClick = { confirmClear = true }) { Text("清空学习记录") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onClearProgress(); confirmClear = false }) { Text("确认清空") }
                        TextButton(onClick = { confirmClear = false }) { Text("取消") }
                    }
                }
            }
        }
    }
}

private enum class GuidedLearningStep(
    val label: String,
    val title: String,
) {
    INTUITION("第 1 步", "先建立直觉"),
    EXAMPLE("第 2 步", "看一个例子"),
    CHECK("第 3 步", "自己判断一下"),
    TEXTBOOK("第 4 步", "回到教材"),
    PRACTICE("第 5 步", "独立练习"),
    SUMMARY("完成", "把这一节收好"),
}

@Composable
fun LearningScreen(
    lesson: Lesson,
    aiSettings: AiSettings,
    progress: LearningProgress,
    onBack: () -> Unit,
    onRecordAttempt: (answer: String, correct: Boolean, feedback: String) -> Unit,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var helpVisible by rememberSaveable { mutableStateOf(false) }
    val steps = GuidedLearningStep.entries
    val step = steps[stepIndex]

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(lesson.title, fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(
                        visible = helpVisible,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                text = helpTextFor(step),
                                modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { helpVisible = !helpVisible },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (helpVisible) "收起帮助" else "我没看懂")
                        }
                        Button(
                            onClick = {
                                helpVisible = false
                                if (step == GuidedLearningStep.SUMMARY) {
                                    onBack()
                                } else {
                                    stepIndex = (stepIndex + 1).coerceAtMost(steps.lastIndex)
                                }
                            },
                            modifier = Modifier.weight(1.4f),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(if (step == GuidedLearningStep.SUMMARY) "返回课程" else "继续")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(step.label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("${stepIndex + 1} / ${steps.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StepProgressBar(currentStep = stepIndex, totalSteps = steps.size)
                Text(step.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn(tween(230)) + slideInHorizontally(tween(280)) { it / 7 }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { -it / 8 })
                },
                label = "guidedLessonStep",
            ) { current ->
                when (current) {
                    GuidedLearningStep.INTUITION -> IntuitionStep(lesson)
                    GuidedLearningStep.EXAMPLE -> ExampleStep()
                    GuidedLearningStep.CHECK -> QuickCheckStep()
                    GuidedLearningStep.TEXTBOOK -> TextbookStep(lesson)
                    GuidedLearningStep.PRACTICE -> PracticeStep(
                        lesson = lesson,
                        settings = aiSettings,
                        progress = progress,
                        onRecordAttempt = onRecordAttempt,
                    )
                    GuidedLearningStep.SUMMARY -> SummaryStep(lesson, progress)
                }
            }
        }
    }
}

@Composable
private fun IntuitionStep(lesson: Lesson) = GuidedScrollColumn {
    FocusSurface {
        LabelPill("核心直觉")
        Text(lesson.explanation, style = MaterialTheme.typography.bodyLarge)
    }
    Text("这一节你只需要抓住三件事", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    lesson.objectives.forEachIndexed { index, objective ->
        TodayStepRow((index + 1).toString(), "目标 ${index + 1}", objective, active = index == 0)
    }
    MotionCard(tone = CardTone.WARNING) {
        Text("容易踩的坑", fontWeight = FontWeight.Bold)
        Text(lesson.commonMistake)
    }
}

@Composable
private fun ExampleStep() = GuidedScrollColumn {
    Text("在数轴上放入 -3、0 和 2", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    NumberLineDiagram()
    MotionCard {
        Text("第一步：找位置", fontWeight = FontWeight.Bold)
        Text("-3 在原点左侧 3 个单位；2 在原点右侧 2 个单位。")
    }
    MotionCard(tone = CardTone.SUCCESS) {
        Text("第二步：比较左右", fontWeight = FontWeight.Bold)
        Text("数轴上越靠右的数越大，所以 2 > -3。")
    }
}

@Composable
private fun NumberLineDiagram() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NumberPoint("-3", "左侧")
                NumberPoint("0", "原点")
                NumberPoint("2", "右侧")
            }
        }
    }
}

@Composable
private fun NumberPoint(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
            Text(value, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickCheckStep() {
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    val correct = choice == "2"

    GuidedScrollColumn {
        FocusSurface {
            LabelPill("先别计算")
            Text("只看数轴位置：-3 和 2，哪个数更大？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceButton("-3", selected = choice == "-3", modifier = Modifier.weight(1f)) { choice = "-3" }
            ChoiceButton("2", selected = choice == "2", modifier = Modifier.weight(1f)) { choice = "2" }
        }
        AnimatedVisibility(
            visible = choice != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            MotionCard(tone = if (correct) CardTone.SUCCESS else CardTone.WARNING) {
                Text(if (correct) "判断正确" else "再看一次左右位置", fontWeight = FontWeight.Bold)
                Text(if (correct) "2 位于 -3 的右侧，所以 2 更大。" else "-3 在原点左边，2 在原点右边。数轴上右边的数更大。")
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 0.dp else 1.dp,
        shape = RoundedCornerShape(22.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TextbookStep(lesson: Lesson) = GuidedScrollColumn {
    FocusSurface {
        LabelPill("教材定位")
        Text(
            "第 ${lesson.textbookPages.first}-${lesson.textbookPages.last} 页",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("真实教材包接入后，这里会直接打开对应页，不需要在整本 PDF 里翻找。")
    }
    MotionCard {
        Text("为什么现在才看教材", fontWeight = FontWeight.Bold)
        Text("先建立直觉，再回看定义和例题，会比一开始就啃原文更容易把上下文接起来。")
    }
}

@Composable
private fun PracticeStep(
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
        "先想：数轴上越靠左的数更大还是更小？",
        "-3 在原点左边，2 在原点右边。",
        "数轴右边的数更大，因此 2 > -3。",
    )

    LaunchedEffect(progress.lastLessonId, progress.lastAnswer) {
        if (progress.lastLessonId == lesson.id && answer.isBlank()) {
            answer = progress.lastAnswer
            result = progress.lastFeedback.takeIf { it.isNotBlank() }
        }
    }

    GuidedScrollColumn {
        FocusSurface {
            LabelPill("独立作答")
            Text(question, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = answer,
            onValueChange = { answer = it; result = null; mistakeType = null },
            label = { Text("写下你的判断和理由") },
            minLines = 4,
            shape = RoundedCornerShape(20.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { hint = (hint + 1).coerceAtMost(hints.size) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) { Text("提示") }
            Button(
                enabled = answer.isNotBlank(),
                onClick = {
                    val text = answer.replace(" ", "")
                    val correct = text.contains("2") && (text.contains("大") || text.contains(">"))
                    val feedback = if (correct) {
                        "判断正确。更完整的理由是：2 位于 -3 的右侧。"
                    } else {
                        "再检查两个数在数轴上的左右位置。这里先不直接公布答案。"
                    }
                    result = feedback
                    mistakeType = if (correct) null else "步骤或表达需要检查"
                    onRecordAttempt(answer, correct, feedback)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) { Text("本地检查") }
        }
        OutlinedButton(
            enabled = answer.isNotBlank() && !isEvaluating && settings.endpoint.isNotBlank() && settings.model.isNotBlank(),
            onClick = {
                isEvaluating = true
                result = "AI 正在批改……"
                scope.launch {
                    val evaluation = runCatching {
                        OpenAiCompatibleClient(settings).evaluateAnswer(question, answer)
                    }.getOrElse { error ->
                        result = "AI 批改失败：${error.message ?: error::class.java.simpleName}"
                        isEvaluating = false
                        return@launch
                    }
                    result = evaluation.feedback
                    mistakeType = evaluation.mistakeType
                    onRecordAttempt(answer, evaluation.correct, evaluation.feedback)
                    isEvaluating = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) { Text(if (isEvaluating) "批改中……" else "使用 AI 批改") }
        AnimatedVisibility(
            visible = hint > 0,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            MotionCard(tone = CardTone.SOFT) {
                LabelPill("提示 $hint")
                Text(hints[(hint - 1).coerceAtLeast(0)])
            }
        }
        AnimatedVisibility(
            visible = result != null,
            enter = fadeIn(tween(260)) + expandVertically(tween(300)),
            exit = fadeOut() + shrinkVertically(),
        ) {
            MotionCard(tone = if (mistakeType == null) CardTone.SUCCESS else CardTone.WARNING) {
                Text("反馈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                mistakeType?.let { LabelPill("可能原因 · $it") }
                Text(result.orEmpty())
            }
        }
    }
}

@Composable
private fun SummaryStep(
    lesson: Lesson,
    progress: LearningProgress,
) = GuidedScrollColumn {
    FocusSurface {
        LabelPill("完成一个学习回合")
        Text(lesson.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("你已经依次经过直觉、例题、判断、教材和练习。下一次打开时，可以直接从薄弱点继续。")
    }
    MotionCard(tone = CardTone.SUCCESS) {
        Text("这一节应该留下什么", fontWeight = FontWeight.Bold)
        lesson.objectives.forEach { Text("• $it") }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("累计作答 ${progress.attempts} 次")
            Text("正确率 ${progress.accuracyPercent}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

private fun helpTextFor(step: GuidedLearningStep): String = when (step) {
    GuidedLearningStep.INTUITION -> "先不要记定义。把数轴想成一条有方向的尺子：右边更大，左边更小。"
    GuidedLearningStep.EXAMPLE -> "只跟住两个动作：先找位置，再比较谁更靠右。"
    GuidedLearningStep.CHECK -> "不用计算绝对值，也不用背规则。只看 -3 和 2 在原点的哪一边。"
    GuidedLearningStep.TEXTBOOK -> "先看页码范围和例题标题，不必一次读完整页。遇到不懂的定义再停下来。"
    GuidedLearningStep.PRACTICE -> "先写结论，再补一句理由。哪怕理由不完整，也比空着更容易定位卡点。"
    GuidedLearningStep.SUMMARY -> "不用强迫自己记住全部内容。能说出‘数轴右边的数更大’，这一轮就有价值。"
}

@Composable
private fun GuidedScrollColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}
