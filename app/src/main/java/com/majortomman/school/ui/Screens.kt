package com.majortomman.school.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val progress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(700),
        label = "courseProgress",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    ) {
        item {
            PageHeading(
                eyebrow = "学习路径",
                title = "有理数",
                subtitle = "沿着前置关系往下走。当前节点会轻轻呼吸，告诉你下一步在哪里。",
            )
        }
        item {
            Column(
                modifier = Modifier.padding(top = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("七年级数学上册 · 第一章", fontWeight = FontWeight.SemiBold)
                    Text(
                        "$masteredCount / ${lessons.size}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
        itemsIndexed(lessons, key = { _, lesson -> lesson.id }) { index, lesson ->
            PathLessonNode(
                index = index,
                lesson = lesson,
                onClick = { onOpenLesson(lesson.id) },
            )
            if (index != lessons.lastIndex) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PathConnector(
                        active = lesson.status == MasteryStatus.MASTERED || lesson.status == MasteryStatus.LEARNING,
                    )
                }
            }
        }
    }
}

@Composable
private fun PathLessonNode(
    index: Int,
    lesson: Lesson,
    onClick: () -> Unit,
) {
    val isCurrent = lesson.status == MasteryStatus.LEARNING
    val infiniteTransition = rememberInfiniteTransition(label = "pathNodePulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCurrent) 1.055f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "currentNodeScale",
    )
    val nodeColor = when (lesson.status) {
        MasteryStatus.MASTERED -> MaterialTheme.colorScheme.tertiaryContainer
        MasteryStatus.LEARNING -> MaterialTheme.colorScheme.primary
        MasteryStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.secondaryContainer
        MasteryStatus.NOT_STARTED -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val nodeForeground = when (lesson.status) {
        MasteryStatus.LEARNING -> MaterialTheme.colorScheme.onPrimary
        MasteryStatus.MASTERED -> MaterialTheme.colorScheme.onTertiaryContainer
        MasteryStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.onSecondaryContainer
        MasteryStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val symbol = when (lesson.status) {
        MasteryStatus.MASTERED -> "✓"
        MasteryStatus.LEARNING -> "→"
        MasteryStatus.NEEDS_REVIEW -> "↻"
        MasteryStatus.NOT_STARTED -> (index + 1).toString()
    }
    val content: @Composable () -> Unit = {
        Surface(
            modifier = Modifier
                .size(if (isCurrent) 70.dp else 62.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = nodeColor,
            contentColor = nodeForeground,
            tonalElevation = if (isCurrent) 4.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
    val description: @Composable (Boolean) -> Unit = { alignEnd ->
        Column(
            modifier = Modifier.width(210.dp),
            horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = lesson.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            )
            Text(
                text = lesson.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LabelPill(lesson.status.label)
                LabelPill("${lesson.estimatedMinutes} 分钟")
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index % 2 == 0) {
            content()
            Spacer(Modifier.width(14.dp))
            description(false)
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
            description(true)
            Spacer(Modifier.width(14.dp))
            content()
        }
    }
}
