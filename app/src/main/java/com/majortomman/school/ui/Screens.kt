@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.majortomman.school.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.majortomman.school.data.DailyPlan
import com.majortomman.school.data.Lesson
import com.majortomman.school.data.MasteryStatus
import com.majortomman.school.data.ReviewItem

@Composable
fun TodayScreen(
    plan: DailyPlan,
    lessons: List<Lesson>,
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
            Heading("今天，学一点真的会的", "只安排最重要的一小步，预计 ${plan.estimatedMinutes} 分钟。")
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("今日新知识", style = MaterialTheme.typography.labelLarge)
                    Text(lesson.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(lesson.subtitle)
                    Text("${lesson.estimatedMinutes} 分钟 · 教材 ${lesson.textbookPages.first}-${lesson.textbookPages.last} 页")
                    Button(onClick = { onStartLesson(lesson.id) }, modifier = Modifier.fillMaxWidth()) { Text("继续学习") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("今天到期的复习", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenPath) { Text("查看课程") }
            }
        }
        items(plan.reviewItems) { ReviewCard(it) }
    }
}

@Composable
fun CoursePathScreen(lessons: List<Lesson>, onOpenLesson: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Heading("七年级数学上册", "第一章 · 有理数")
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { 0.3f }, modifier = Modifier.fillMaxWidth())
        }
        items(lessons) { lesson ->
            OutlinedCard(onClick = { onOpenLesson(lesson.id) }) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusMarker(lesson.status)
                    Column(Modifier.weight(1f)) {
                        Text(lesson.title, fontWeight = FontWeight.SemiBold)
                        Text(lesson.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(lesson.status.label, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun ReviewScreen(items: List<ReviewItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Heading("到期复习", "不是重看整章，只重新碰一下快要忘掉的地方。") }
        items(items) { ReviewCard(it) }
        item {
            InfoCard("错题诊断") {
                Text("下一阶段会记录错误步骤、错误类型、再次作答结果和复习时间。")
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var provider by rememberSaveable { mutableStateOf("局域网 llama.cpp") }
    var endpoint by rememberSaveable { mutableStateOf("http://192.168.1.2:7777/v1") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Heading("设置", "AI 与教材资源都以本地优先方式配置。")
        Text("AI 服务", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("局域网 llama.cpp", "OpenAI 兼容接口").forEach {
                FilterChip(selected = provider == it, onClick = { provider = it }, label = { Text(it) })
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("接口地址") },
            supportingText = { Text("网络请求会在下一阶段接通。") },
            singleLine = true,
        )
        InfoCard("教材资源") {
            Text("七年级数学上册 · 示例目录")
            Text("后续从手机目录导入独立教材包，不把 1GB 教材塞进 APK。")
            OutlinedButton(onClick = {}) { Text("导入功能待接入") }
        }
    }
}

private enum class LearningTab(val label: String) {
    EXPLAIN("讲解"), TEXTBOOK("教材"), EXAMPLE("例题"), PRACTICE("练习")
}

@Composable
fun LearningScreen(lesson: Lesson, onBack: () -> Unit) {
    var tabName by rememberSaveable { mutableStateOf(LearningTab.EXPLAIN.name) }
    val tab = LearningTab.valueOf(tabName)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(lesson.title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LearningTab.entries.forEach {
                    FilterChip(selected = tab == it, onClick = { tabName = it.name }, label = { Text(it.label) })
                }
            }
            when (tab) {
                LearningTab.EXPLAIN -> ExplainLesson(lesson)
                LearningTab.TEXTBOOK -> TextbookLesson(lesson)
                LearningTab.EXAMPLE -> ExampleLesson()
                LearningTab.PRACTICE -> PracticeLesson()
            }
        }
    }
}

@Composable
private fun ExplainLesson(lesson: Lesson) = ScrollColumn {
    Text("先建立直觉", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(lesson.explanation, style = MaterialTheme.typography.bodyLarge)
    InfoCard("本节目标") { lesson.objectives.forEachIndexed { i, text -> Text("${i + 1}. $text") } }
    InfoCard("容易踩的坑") { Text(lesson.commonMistake) }
    InfoCard("AI 辅导策略") { Text("先定位卡在概念、步骤还是前置知识，再逐层给提示，不直接甩出完整答案。") }
}

@Composable
private fun TextbookLesson(lesson: Lesson) = ScrollColumn {
    Text("教材定位", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📖", style = MaterialTheme.typography.displaySmall)
            Text("教材第 ${lesson.textbookPages.first}-${lesson.textbookPages.last} 页")
            Text("接入教材资源包后，这里会显示对应 PDF 页面。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExampleLesson() = ScrollColumn {
    Text("例题", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("在数轴上表示 -3、0、2，并比较 -3 和 2 的大小。")
    InfoCard("第一步：确定位置") { Text("-3 在原点左侧 3 个单位；2 在原点右侧 2 个单位。") }
    InfoCard("第二步：利用数轴比较") { Text("数轴上右边的数更大，所以 2 > -3。") }
}

@Composable
private fun PracticeLesson() {
    var answer by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var hint by rememberSaveable { mutableIntStateOf(0) }
    val hints = listOf(
        "先想：数轴上越靠左的数更大还是更小？",
        "-3 在原点左边，2 在原点右边。",
        "数轴右边的数更大，因此 2 > -3。",
    )
    ScrollColumn {
        Text("独立作答", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("在数轴上，-3 与 2 哪个数更大？请简单说明理由。")
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = answer,
            onValueChange = { answer = it; result = null },
            label = { Text("你的答案") },
            minLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { hint = (hint + 1).coerceAtMost(hints.size) }) { Text("给一点提示") }
            Button(enabled = answer.isNotBlank(), onClick = {
                val text = answer.replace(" ", "")
                result = if (text.contains("2") && (text.contains("大") || text.contains(">")))
                    "判断正确。更完整的理由是：2 位于 -3 的右侧。"
                else "再检查两个数在数轴上的左右位置。这里先不直接公布答案。"
            }) { Text("检查答案") }
        }
        if (hint > 0) InfoCard("提示 $hint") { Text(hints[hint - 1]) }
        result?.let { InfoCard("反馈") { Text(it) } }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReviewCard(item: ReviewItem) {
    OutlinedCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun StatusMarker(status: MasteryStatus) {
    val symbol = when (status) {
        MasteryStatus.MASTERED -> "✓"
        MasteryStatus.LEARNING -> "●"
        MasteryStatus.NOT_STARTED -> "○"
        MasteryStatus.NEEDS_REVIEW -> "!"
    }
    Text(
        symbol,
        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ScrollColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}
