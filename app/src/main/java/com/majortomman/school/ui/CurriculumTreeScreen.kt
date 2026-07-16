package com.majortomman.school.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.curriculum.CurriculumNodeProgress
import com.majortomman.school.data.curriculum.CurriculumNodeProgressStatus
import com.majortomman.school.data.curriculum.CurriculumNodeType
import com.majortomman.school.data.curriculum.CurriculumSnapshot
import com.majortomman.school.data.curriculum.CurriculumTreeRow
import com.majortomman.school.data.curriculum.MasteryTrendRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TreeBlack = Color(0xFF050608)
private val TreeWhite = Color(0xFFF5F7FA)
private val TreeBlue = Color(0xFF2D7BFF)
private val TreeYellow = Color(0xFFFFCC00)
private val TreeRed = Color(0xFFFF453A)
private val TreeMuted = TreeWhite.copy(alpha = 0.44f)
private val TreeLine = TreeWhite.copy(alpha = 0.12f)
private val TrendDateFormatter = DateTimeFormatter.ofPattern("MM-dd")

@Composable
internal fun CurriculumTreeScreen(
    snapshot: CurriculumSnapshot,
    curriculumId: String,
    progress: Map<String, CurriculumNodeProgress>,
    activeLegacyLessonId: String?,
    onOpenLesson: (String) -> Unit,
) {
    val curriculum = snapshot.curriculumById[curriculumId]
    val subject = curriculum?.subjectId?.let(snapshot.subjectById::get)
    val subjectId = curriculum?.subjectId.orEmpty()
    val activeNode = activeLegacyLessonId?.let(snapshot::nodeForLegacyLesson)
    val activeKnowledge = activeNode?.let { node -> snapshot.knowledgeForNode(node.id).firstOrNull() }
    val appContext = LocalContext.current.applicationContext
    val trendRepository = remember(appContext) { MasteryTrendRepository.getInstance(appContext) }
    val subjectTrend by remember(trendRepository, subjectId) {
        trendRepository.observeSubjectTrend(subjectId, days = 30)
    }.collectAsState(initial = emptyList())
    val knowledgeTrend by remember(trendRepository, subjectId, activeKnowledge?.id) {
        trendRepository.observeKnowledgeTrend(
            subjectId = subjectId,
            knowledgePointId = activeKnowledge?.id.orEmpty(),
            days = 30,
        )
    }.collectAsState(initial = emptyList())

    val rows = snapshot.flattenedTree(curriculumId).filterNot { it.node.type == CurriculumNodeType.ROOT }
    val lessonRows = rows.filter { it.node.legacyLessonId != null }
    val mastered = lessonRows.count { progress[it.node.id]?.status == CurriculumNodeProgressStatus.MASTERED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(TreeBlack)
            .systemBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 26.dp),
    ) {
        item {
            Text(
                text = curriculum?.title ?: "课程路径",
                color = TreeWhite,
                fontSize = 42.sp,
                lineHeight = 47.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "$mastered / ${lessonRows.size} 个课程节点已掌握",
                color = TreeMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(28.dp))

            MasteryTrendChart(
                title = "${subject?.title ?: "学科"}掌握度趋势",
                subtitle = "近 30 天 · 基于练习结果的估计",
                points = subjectTrend.map { point ->
                    MasteryTrendPoint(
                        x = point.epochDay,
                        score = point.averageScore,
                    )
                },
                xLabel = ::epochDayLabel,
            )

            if (activeKnowledge != null) {
                Spacer(Modifier.height(30.dp))
                MasteryTrendChart(
                    title = "${activeKnowledge.title}趋势",
                    subtitle = "近 30 天 · 每次掌握度变化",
                    points = knowledgeTrend.map { point ->
                        MasteryTrendPoint(
                            x = point.recordedAt,
                            score = point.score,
                            eventType = point.eventType,
                        )
                    },
                    xLabel = ::timestampLabel,
                )
            }

            Spacer(Modifier.height(26.dp))
            if (activeNode != null) {
                val prerequisites = snapshot.knowledgeForNode(activeNode.id)
                    .flatMap { snapshot.prerequisites(it.id) }
                    .distinctBy { it.id }
                if (prerequisites.isNotEmpty()) {
                    Text("当前课程的前置知识", color = TreeYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        prerequisites.joinToString(" · ") { it.title },
                        color = TreeWhite.copy(alpha = 0.68f),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(TreeLine))
            Spacer(Modifier.height(12.dp))
        }

        items(rows, key = { it.node.id }) { row ->
            CurriculumTreeRowItem(
                row = row,
                snapshot = snapshot,
                progress = progress[row.node.id],
                active = row.node.legacyLessonId == activeLegacyLessonId,
                onClick = row.node.legacyLessonId?.let { lessonId -> { onOpenLesson(lessonId) } },
            )
        }

        item { Spacer(Modifier.height(50.dp)) }
    }
}

@Composable
private fun CurriculumTreeRowItem(
    row: CurriculumTreeRow,
    snapshot: CurriculumSnapshot,
    progress: CurriculumNodeProgress?,
    active: Boolean,
    onClick: (() -> Unit)?,
) {
    val node = row.node
    val isLesson = node.legacyLessonId != null
    val status = progress?.status ?: if (active) CurriculumNodeProgressStatus.LEARNING else CurriculumNodeProgressStatus.NOT_STARTED
    val statusColor = when (status) {
        CurriculumNodeProgressStatus.MASTERED -> TreeYellow
        CurriculumNodeProgressStatus.LEARNING -> TreeBlue
        CurriculumNodeProgressStatus.NEEDS_REVIEW -> TreeRed
        CurriculumNodeProgressStatus.NOT_STARTED -> TreeMuted
    }
    val knowledge = if (isLesson) snapshot.knowledgeForNode(node.id).joinToString(" · ") { it.title } else ""
    val resources = if (isLesson) snapshot.resourcesForNode(node.id) else emptyList()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(vertical = if (isLesson) 13.dp else 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Spacer(Modifier.width((row.depth * 17).coerceAtMost(68).dp))
        if (isLesson) {
            Box(
                Modifier
                    .padding(top = 7.dp)
                    .width(9.dp)
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(Modifier.width(14.dp))
        } else {
            Box(
                Modifier
                    .padding(top = 11.dp)
                    .width(18.dp)
                    .height(1.dp)
                    .background(TreeLine),
            )
            Spacer(Modifier.width(9.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = node.title,
                color = when {
                    active -> TreeWhite
                    isLesson -> TreeWhite.copy(alpha = if (status == CurriculumNodeProgressStatus.NOT_STARTED) 0.55f else 0.84f)
                    else -> TreeWhite.copy(alpha = 0.72f)
                },
                fontSize = when (node.type) {
                    CurriculumNodeType.LEVEL -> 25.sp
                    CurriculumNodeType.TERM -> 20.sp
                    CurriculumNodeType.COURSE -> 18.sp
                    else -> if (isLesson) 17.sp else 16.sp
                },
                lineHeight = 24.sp,
                fontWeight = when {
                    active -> FontWeight.SemiBold
                    node.type in setOf(CurriculumNodeType.LEVEL, CurriculumNodeType.TERM) -> FontWeight.Medium
                    else -> FontWeight.Normal
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isLesson && knowledge.isNotBlank()) {
                Text(
                    text = buildString {
                        append(knowledge)
                        if (resources.isNotEmpty()) {
                            append(" · ")
                            append(if (resources.any { it.first.type.name == "TEXTBOOK_PDF" }) "教材已绑定" else "预制课程")
                        }
                    },
                    color = if (active) TreeBlue else TreeMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (active) {
            Text("继续", color = TreeBlue, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
        }
    }
}

private fun epochDayLabel(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(TrendDateFormatter)

private fun timestampLabel(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(TrendDateFormatter)
