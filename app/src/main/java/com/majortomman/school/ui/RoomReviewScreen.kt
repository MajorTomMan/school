package com.majortomman.school.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.majortomman.school.data.AttemptRecord
import com.majortomman.school.data.LearningProgress
import com.majortomman.school.data.ReviewItem
import com.majortomman.school.data.ScheduledReview

@Composable
fun RoomReviewScreen(
    fallbackItems: List<ReviewItem>,
    progress: LearningProgress,
    scheduledReviews: List<ScheduledReview>,
    recentAttempts: List<AttemptRecord>,
    onOpenLesson: (String) -> Unit,
) {
    val currentReview = scheduledReviews.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeading(
                eyebrow = "复习",
                title = if (currentReview == null) "今天没有必须完成的复习" else "先完成眼前这一项",
                subtitle = if (currentReview == null) {
                    "新的作答会自动进入复习计划。现在可以回课程路径继续学习。"
                } else {
                    "复习页一次只突出一个任务。完成后，再看下一项。"
                },
            )
        }

        if (currentReview != null) {
            item {
                AnimatedCardItem(index = 0) {
                    FocusSurface(onClick = { onOpenLesson(currentReview.lessonId) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LabelPill("1 / ${scheduledReviews.size}")
                            LabelPill(currentReview.dueLabel)
                        }
                        Text(
                            currentReview.lessonTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (currentReview.lastCorrect) {
                                "上次答对了，这次只需要快速确认是否还记得。"
                            } else {
                                "上次没有通过，这次先重新定位错误发生在哪一步。"
                            },
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LabelPill("间隔 ${currentReview.intervalDays} 天")
                            LabelPill("连续通过 ${currentReview.repetitions} 次")
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenLesson(currentReview.lessonId) },
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                "开始复习",
                                modifier = Modifier.padding(vertical = 13.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        } else if (fallbackItems.isNotEmpty()) {
            item {
                FocusSurface {
                    LabelPill("建议复习")
                    Text(fallbackItems.first().title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(fallbackItems.first().reason)
                }
            }
        }

        if (scheduledReviews.size > 1) {
            item { SectionTitle("接下来") }
            itemsIndexed(scheduledReviews.drop(1), key = { _, item -> item.lessonId }) { index, item ->
                AnimatedCardItem(index = index + 1) {
                    UpcomingReviewRow(item = item, onClick = { onOpenLesson(item.lessonId) })
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "最近作答",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "正确率 ${progress.accuracyPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (recentAttempts.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("还没有详细记录", fontWeight = FontWeight.Bold)
                        Text("完成下一道练习后，这里会显示答案、反馈和错误类型。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            itemsIndexed(recentAttempts, key = { _, item -> item.id }) { index, item ->
                AnimatedCardItem(index = index + scheduledReviews.size + 1) {
                    AttemptHistoryRow(item)
                }
            }
        }
    }
}

@Composable
private fun UpcomingReviewRow(
    item: ScheduledReview,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(if (item.lastCorrect) "✓" else "!")
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.lessonTitle, fontWeight = FontWeight.Bold)
                Text(
                    if (item.lastCorrect) "快速确认" else "短间隔巩固",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LabelPill(item.dueLabel)
        }
    }
}

@Composable
private fun AttemptHistoryRow(item: AttemptRecord) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        color = if (item.correct) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBubble(if (item.correct) "✓" else "×")
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.lessonTitle, fontWeight = FontWeight.Bold)
                        Text(item.createdLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LabelPill(if (item.correct) "正确" else "需要复习")
            }
            Text(item.feedback, maxLines = if (expanded) Int.MAX_VALUE else 2)
            item.mistakeType?.let { LabelPill("错误类型 · $it") }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("题目", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.questionText)
                    Text("你的答案", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(item.answer.ifBlank { "未填写" })
                }
            }
            Text(
                if (expanded) "点击收起" else "点击查看当时的答案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
