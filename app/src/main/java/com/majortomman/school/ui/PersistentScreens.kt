@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.majortomman.school.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.majortomman.school.ai.OpenAiCompatibleClient
import com.majortomman.school.data.AiSettings
import com.majortomman.school.data.DailyPlan
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.Lesson
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PersistentHeading("今天，学一点真的会的", "只安排最重要的一小步，预计 ${plan.estimatedMinutes} 分钟。")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("今日新知识", style = MaterialTheme.typography.labelLarge)
                    Text(lesson.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(lesson.subtitle)
                    Text("${lesson.estimatedMinutes} 分钟 · 教材 ${lesson.textbookPages.first}-${lesson.textbookPages.last} 页")
                    Button(onClick = { onStartLesson(lesson.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (lesson.status.label == "已掌握") "重新练习" else "继续学习")
                    }
                }
            }
        }
        item {
            PersistentInfoCard("本机学习记录") {
                Text("已作答 ${progress.attempts} 次 · 正确 ${progress.correctAttempts} 次")
                Text("当前正确率 ${progress.accuracyPercent}%")
                LinearProgressIndicator(
                    progress = { progress.accuracyPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今天到期的复习", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenPath) { Text("查看课程") }
            }
        }
        items(plan.reviewItems) { PersistentReviewCard(it) }
    }
}

@Composable
fun ReviewScreen(items: List<ReviewItem>, progress: LearningProgress) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PersistentHeading("到期复习", "不是重看整章，只重新碰一下快要忘掉的地方。") }
        item {
            PersistentInfoCard("练习概况") {
                Text("累计 ${progress.attempts} 次，正确率 ${progress.accuracyPercent}%")
                if (progress.lastFeedback.isNotBlank()) {
                    Text("最近反馈：${progress.lastFeedback}")
                }
            }
        }
        items(items) { PersistentReviewCard(it) }
        item {
            PersistentInfoCard("错题诊断") {
                Text("作答和反馈现在会保存在本机。下一步会按错误类型自动生成复习任务。")
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PersistentHeading("设置", "AI 与学习进度现在会在本机持久保存。")
        Text("OpenAI 兼容接口", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = endpoint,
            onValueChange = { endpoint = it; connectionStatus = null },
            label = { Text("接口地址") },
            supportingText = { Text("例如 http://192.168.1.2:7777/v1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = model,
            onValueChange = { model = it; connectionStatus = null },
            label = { Text("模型名称") },
            supportingText = { Text("需要与 /v1/models 返回的模型 ID 一致") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key（局域网服务可留空）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = endpoint.isNotBlank() && model.isNotBlank(),
                onClick = {
                    val updated = AiSettings(endpoint.trim(), model.trim(), apiKey.trim())
                    onSave(updated)
                    connectionStatus = "已保存到本机"
                },
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
            ) { Text(if (isTesting) "测试中" else "测试连接") }
        }
        connectionStatus?.let { status ->
            PersistentInfoCard("连接状态") { Text(status) }
        }
        PersistentInfoCard("局域网说明") {
            Text("为了连接你电脑上的 HTTP llama.cpp，当前测试版允许明文局域网请求。不要把接口直接暴露到公网。")
        }
        PersistentInfoCard("教材资源") {
            Text("七年级数学上册 · 示例目录")
            Text("下一阶段从手机目录导入独立教材包，并打开真实 PDF 对应页。")
            OutlinedButton(onClick = {}) { Text("导入功能待接入") }
        }
        PersistentInfoCard("学习数据") {
            Text("练习次数、最近答案、反馈和知识点掌握状态保存在本机。")
            if (!confirmClear) {
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
                title = { Text(lesson.title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PersistentLearningTab.entries.forEach {
                    FilterChip(
                        selected = tab == it,
                        onClick = { tabName = it.name },
                        label = { Text(it.label) },
                    )
                }
            }
            when (tab) {
                PersistentLearningTab.EXPLAIN -> PersistentExplainLesson(lesson)
                PersistentLearningTab.TEXTBOOK -> PersistentTextbookLesson(lesson)
                PersistentLearningTab.EXAMPLE -> PersistentExampleLesson()
                PersistentLearningTab.PRACTICE -> PersistentPracticeLesson(
                    lesson = lesson,
                    settings = aiSettings,
                    progress = progress,
                    onRecordAttempt = onRecordAttempt,
                )
            }
        }
    }
}

@Composable
private fun PersistentExplainLesson(lesson: Lesson) = PersistentScrollColumn {
    Text("先建立直觉", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(lesson.explanation, style = MaterialTheme.typography.bodyLarge)
    PersistentInfoCard("本节目标") {
        lesson.objectives.forEachIndexed { index, text -> Text("${index + 1}. $text") }
    }
    PersistentInfoCard("容易踩的坑") { Text(lesson.commonMistake) }
    PersistentInfoCard("AI 辅导策略") {
        Text("先定位卡在概念、步骤还是前置知识，再逐层给提示，不直接甩出完整答案。")
    }
}

@Composable
private fun PersistentTextbookLesson(lesson: Lesson) = PersistentScrollColumn {
    Text("教材定位", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📖", style = MaterialTheme.typography.displaySmall)
            Text("教材第 ${lesson.textbookPages.first}-${lesson.textbookPages.last} 页")
            Text("接入教材资源包后，这里会显示对应 PDF 页面。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersistentExampleLesson() = PersistentScrollColumn {
    Text("例题", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("在数轴上表示 -3、0、2，并比较 -3 和 2 的大小。")
    PersistentInfoCard("第一步：确定位置") {
        Text("-3 在原点左侧 3 个单位；2 在原点右侧 2 个单位。")
    }
    PersistentInfoCard("第二步：利用数轴比较") {
        Text("数轴上右边的数更大，所以 2 > -3。")
    }
}

@Composable
private fun PersistentPracticeLesson(
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

    PersistentScrollColumn {
        Text("独立作答", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(question)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = answer,
            onValueChange = { answer = it; result = null; mistakeType = null },
            label = { Text("你的答案") },
            minLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { hint = (hint + 1).coerceAtMost(hints.size) }) {
                Text("给一点提示")
            }
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
        ) { Text(if (isEvaluating) "批改中……" else "使用 AI 批改") }
        if (hint > 0) PersistentInfoCard("提示 $hint") { Text(hints[hint - 1]) }
        result?.let { feedback ->
            PersistentInfoCard("反馈") {
                mistakeType?.let { Text("可能的错误类型：$it", fontWeight = FontWeight.SemiBold) }
                Text(feedback)
            }
        }
        if (progress.attempts > 0) {
            Text("本机累计：${progress.attempts} 次作答，正确率 ${progress.accuracyPercent}%", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PersistentHeading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PersistentReviewCard(item: ReviewItem) {
    OutlinedCard {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("↻", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.dueLabel, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PersistentInfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun PersistentScrollColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}
