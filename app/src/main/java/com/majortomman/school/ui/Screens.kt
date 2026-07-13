package com.majortomman.school.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.majortomman.school.data.Lesson
import com.majortomman.school.data.MasteryStatus

@Composable
fun CoursePathScreen(
    lessons: List<Lesson>,
    onOpenLesson: (String) -> Unit,
) {
    val masteredCount = lessons.count { it.status == MasteryStatus.MASTERED }
    val activeCount = lessons.count { it.status == MasteryStatus.LEARNING }
    val rawProgress = if (lessons.isEmpty()) 0f else (masteredCount + activeCount * 0.45f) / lessons.size
    val progress = animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(700),
        label = "courseProgress",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeading(
                eyebrow = "Learning path",
                title = "有理数",
                subtitle = "按前置关系一步步走，不需要在整本教材里迷路。",
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
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("七年级数学上册", style = MaterialTheme.typography.labelLarge)
                            Text("第一章", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        IconBubble("∑")
                    }
                    LinearProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    )
                    MetricRow {
                        MetricTile(masteredCount.toString(), "已掌握")
                        MetricTile(activeCount.toString(), "学习中")
                        MetricTile(lessons.size.toString(), "知识点")
                    }
                }
            }
        }
        item {
            SectionTitle("知识路径")
        }
        itemsIndexed(lessons, key = { _, lesson -> lesson.id }) { index, lesson ->
            AnimatedCardItem(index = index + 1) {
                LessonPathCard(
                    index = index,
                    lesson = lesson,
                    onClick = { onOpenLesson(lesson.id) },
                )
            }
        }
    }
}

@Composable
private fun LessonPathCard(
    index: Int,
    lesson: Lesson,
    onClick: () -> Unit,
) {
    val tone = when (lesson.status) {
        MasteryStatus.MASTERED -> CardTone.SUCCESS
        MasteryStatus.LEARNING -> CardTone.ACCENT
        MasteryStatus.NEEDS_REVIEW -> CardTone.WARNING
        MasteryStatus.NOT_STARTED -> CardTone.SURFACE
    }
    val symbol = when (lesson.status) {
        MasteryStatus.MASTERED -> "✓"
        MasteryStatus.LEARNING -> "→"
        MasteryStatus.NEEDS_REVIEW -> "↻"
        MasteryStatus.NOT_STARTED -> (index + 1).toString()
    }

    MotionCard(tone = tone, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(symbol)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    lesson.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LabelPill("${lesson.estimatedMinutes} 分钟")
                    LabelPill("${lesson.textbookPages.first}-${lesson.textbookPages.last} 页")
                }
            }
            Text(
                text = lesson.status.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
