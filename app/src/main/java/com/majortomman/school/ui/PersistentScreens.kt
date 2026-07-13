@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.majortomman.school.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
    val accuracy = animateFloatAsState(
        targetValue = progress.accuracyPercent / 100f,
        animationSpec = tween(700),
        label = "accuracy",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeading(
                eyebrow = "Today",
                title = "今天，只学一小步",
                subtitle = "先把一个知识点真正弄懂，再去碰下一个。",
            )
        }
        item {
            AnimatedCardItem(index = 0) {
                MotionCard(tone = CardTone.ACCENT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            LabelPill(
                                text = "今日学习 · ${plan.estimatedMinutes} 分钟",
                                background = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                            )
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
                        }
                        IconBubble("→")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabelPill("教材 ${lesson.textbookPages.first}-${lesson.textbookPages.last} 页")
                        LabelPill(lesson.status.label)
                    }
                    Button(
                        onClick = { onStartLesson(lesson.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(if (lesson.status == MasteryStatus.MASTERED) "重新练习" else "开始今天的学习")
                    }
                }
            }
        }
        item {
            AnimatedCardItem(index = 1) {
                MotionCard(tone = CardTone.SOFT) {
                    SectionTitle("学习节奏")
                    MetricRow {
                        MetricTile(progress.attempts.toString(), "累计作答")
                        MetricTile(progress.correctAttempts.toString(), "正确次数")
                        MetricTile("${progress.accuracyPercent}%", "当前正确率")
                    }
                    LinearProgressIndicator(
                        progress = { accuracy.value },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text(
                        text = if (progress.attempts == 0) "完成第一道练习后，这里会开始记录你的真实学习轨迹。"
                        else "记录不会用来排名，只用于判断下一步该学什么。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SectionTitle(
                title = "今天到期的复习",
                action = { TextButton(onClick = onOpenPath) { Text("查看课程") } },
            )
        }
        itemsIndexed(plan.reviewItems, key = { _, item -> item.id }) { index, item ->
            AnimatedCardItem(index = index + 2) {
                ReviewTaskCard(item)
            }
        }
    }
}

@Composable
fun ReviewScreen(
    items: List<ReviewItem>,
    progress: LearningProgress,
) {
    val accuracy = animateFloatAsState(
        targetValue = progress.accuracyPercent / 100f,
        animationSpec = tween(700),
        label = "reviewAccuracy",
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeading(
                eyebrow = "Review",
                title = "该忘的，轻轻碰一下",
                subtitle = "复习不是重学整章，只把快要松动的部分重新接上。",
            )
        }
        item {
            AnimatedCardItem(index = 0) {
                MotionCard(tone = CardTone.SOFT) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("练习概况", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("累计 ${progress.attempts} 次作答", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${progress.accuracyPercent}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { accuracy.value },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (progress.lastFeedback.isNotBlank()) {
                        Text(
                            text = "最近反馈：${progress.lastFeedback}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { SectionTitle("待复习") }
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            AnimatedCardItem(index = index + 1) { ReviewTaskCard(item) }
        }
        item {
            AnimatedCardItem(index = items.size + 1) {
                MotionCard(tone = CardTone.WARNING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBubble("!")
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("错因诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "作答与反馈已经保存在本机。下一步会按错误类型自动安排复习。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeading(
            eyebrow = "Settings",
            title = "把工具调成你的样子",
            subtitle = "AI、教材和学习记录都保持本地优先。",
        )
        AnimatedCardItem(index = 0) {
            MotionCard(tone = CardTone.SURFACE) {
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(14.dp),
                    ) {
                        Text(connectionStatus.orEmpty())
                    }
                }
            }
        }
        AnimatedCardItem(index = 1) {
            MotionCard(tone = CardTone.SOFT) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBubble("⌁")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("局域网连接", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "测试版允许连接 HTTP llama.cpp。请只在可信局域网中使用，不要把未鉴权接口暴露到公网。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        AnimatedCardItem(index = 2) {
            MotionCard(tone = CardTone.SURFACE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("教材资源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("七年级数学上册 · 示例目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconBubble("书")
                }
                Text("下一阶段从手机目录导入独立教材包，并直接打开真实 PDF 对应页。")
                OutlinedButton(onClick = {}, shape = RoundedCornerShape(16.dp)) { Text("导入功能待接入") }
            }
        }
        AnimatedCardItem(index = 3) {
            MotionCard(tone = CardTone.WARNING) {
                Text("学习数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("练习次数、最近答案、反馈和知识点掌握状态都保存在本机。")
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
}

private enum class PersistentLearningTab(val label: String) {
    EXPLAIN("讲解"),
    TEXTBOOK("教材"),
    EXAMPLE("例题"),
    PRACTICE("练习"),
}

@Composable
fun LearningScreen(
    lesson: Lesson,
    aiSettings: AiSettings,
    progress: LearningProgress,
    onBack: () -> Unit,
    onRecordAttempt: (answer: String, correct: Boolean, feedback: String) -> Unit,
) {
    var tabName by rememberSaveable { mutableStateOf(PersistentLearningTab.EXPLAIN.name) }
    val tab = PersistentLearningTab.valueOf(tabName)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(lesson.title, fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MotionCard(
                modifier = Modifier.padding(horizontal = 18.dp),
                tone = CardTone.ACCENT,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(lesson.subtitle, style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            LabelPill("${lesson.estimatedMinutes} 分钟")
                            LabelPill("教材 ${lesson.textbookPages.first}-${lesson.textbookPages.last} 页")
                        }
                    }
                    IconBubble("∴")
                }
            }
            LearningTabStrip(
                selected = tab,
                onSelected = { tabName = it.name },
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 8 }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -it / 8 })
                },
                label = "lessonTabContent",
            ) { currentTab ->
                when (currentTab) {
                    PersistentLearningTab.EXPLAIN -> ExplainLessonCards(lesson)
                    PersistentLearningTab.TEXTBOOK -> TextbookLessonCards(lesson)
                    PersistentLearningTab.EXAMPLE -> ExampleLessonCards()
                    PersistentLearningTab.PRACTICE -> PracticeLessonCards(
                        lesson = lesson,
                        settings = aiSettings,
                        progress = progress,
                        onRecordAttempt = onRecordAttempt,
                    )
                }
            }
        }
    }
}

@Composable
private fun LearningTabStrip(
    selected: PersistentLearningTab,
    onSelected: (PersistentLearningTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PersistentLearningTab.entries.forEach { tab ->
            val background by animateColorAsState(
                targetValue = if (tab == selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
                animationSpec = tween(180),
                label = "tabBackground",
            )
            val foreground by animateColorAsState(
                targetValue = if (tab == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "tabForeground",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(background)
                    .clickable { onSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tab.label, color = foreground, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ExplainLessonCards(lesson: Lesson) = LearningCardColumn {
    MotionCard(tone = CardTone.SOFT) {
        LabelPill("先建立直觉")
        Text(lesson.explanation, style = MaterialTheme.typography.bodyLarge)
    }
    MotionCard {
        SectionTitle("本节目标")
        lesson.objectives.forEachIndexed { index, text ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                LabelPill((index + 1).toString(), background = MaterialTheme.colorScheme.primaryContainer)
                Text(text, modifier = Modifier.weight(1f))
            }
        }
    }
    MotionCard(tone = CardTone.WARNING) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble("!")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("容易踩的坑", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(lesson.commonMistake)
            }
        }
    }
    MotionCard(tone = CardTone.ACCENT) {
        Text("AI 会怎么帮你", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("先定位你卡在概念、步骤还是前置知识，再逐层给提示，不直接甩出完整答案。")
    }
}

@Composable
private fun TextbookLessonCards(lesson: Lesson) = LearningCardColumn {
    MotionCard(tone = CardTone.SOFT) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelPill("教材定位")
                Text(
                    "第 ${lesson.textbookPages.first}-${lesson.textbookPages.last} 页",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("接入教材资源包后，这里会直接显示对应 PDF 页面。")
            }
            IconBubble("书")
        }
    }
    MotionCard {
        Text("为什么保留原教材", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("AI 讲解负责帮你跨过卡点，教材原页负责保留定义、例题、图形和上下文。两者不会互相替代。")
    }
}

@Composable
private fun ExampleLessonCards() = LearningCardColumn {
    MotionCard(tone = CardTone.ACCENT) {
        LabelPill("例题")
        Text("在数轴上表示 -3、0、2，并比较 -3 和 2 的大小。", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
    MotionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            IconBubble("1")
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("确定位置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("-3 在原点左侧 3 个单位；2 在原点右侧 2 个单位。")
            }
        }
    }
    MotionCard(tone = CardTone.SUCCESS) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            IconBubble("2")
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("利用数轴比较", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("数轴上右边的数更大，所以 2 > -3。")
            }
        }
    }
}

@Composable
private fun PracticeLessonCards(
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

    LearningCardColumn {
        MotionCard(tone = CardTone.ACCENT) {
            LabelPill("独立作答")
            Text(question, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        MotionCard {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = answer,
                onValueChange = { answer = it; result = null; mistakeType = null },
                label = { Text("写下你的判断和理由") },
                minLines = 4,
                shape = RoundedCornerShape(18.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { hint = (hint + 1).coerceAtMost(hints.size) },
                    shape = RoundedCornerShape(16.dp),
                ) { Text("给一点提示") }
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
                    shape = RoundedCornerShape(16.dp),
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
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (isEvaluating) "批改中……" else "使用 AI 批改") }
        }
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
        if (progress.attempts > 0) {
            MotionCard(tone = CardTone.SOFT) {
                MetricRow {
                    MetricTile(progress.attempts.toString(), "累计作答")
                    MetricTile("${progress.accuracyPercent}%", "正确率")
                }
            }
        }
    }
}

@Composable
private fun ReviewTaskCard(item: ReviewItem) {
    MotionCard(onClick = {}) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble("↻", background = MaterialTheme.colorScheme.primaryContainer)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LabelPill(item.dueLabel)
        }
    }
}

@Composable
private fun LearningCardColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}
