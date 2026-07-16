package com.majortomman.school.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.curriculum.MasteryTrendEventType
import kotlin.math.roundToInt

internal data class MasteryTrendPoint(
    val x: Long,
    val score: Double,
    val eventType: MasteryTrendEventType? = null,
)

private val ChartWhite = Color(0xFFF5F7FA)
private val ChartBlue = Color(0xFF2D7BFF)
private val ChartYellow = Color(0xFFFFCC00)
private val ChartRed = Color(0xFFFF453A)
private val ChartLine = ChartWhite.copy(alpha = 0.12f)
private val ChartMuted = ChartWhite.copy(alpha = 0.42f)

@Composable
internal fun MasteryTrendChart(
    title: String,
    subtitle: String,
    points: List<MasteryTrendPoint>,
    xLabel: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val ordered = points.sortedBy(MasteryTrendPoint::x)
    val latest = ordered.lastOrNull()?.score

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    color = ChartWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$subtitle · Y 掌握度 / X 日期",
                    color = ChartMuted,
                    fontSize = 11.sp,
                )
            }
            if (latest != null) {
                Text(
                    text = "${(latest.coerceIn(0.0, 1.0) * 100).roundToInt()}%",
                    color = ChartYellow,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (ordered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .background(ChartWhite.copy(alpha = 0.035f))
                    .padding(18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "完成一次练习后开始记录趋势。",
                    color = ChartMuted,
                    fontSize = 13.sp,
                )
            }
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(38.dp).height(154.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text("100%", color = ChartMuted, fontSize = 9.sp)
                Text("50%", color = ChartMuted, fontSize = 9.sp)
                Text("0%", color = ChartMuted, fontSize = 9.sp)
            }
            Spacer(Modifier.width(8.dp))
            Canvas(modifier = Modifier.weight(1f).height(154.dp)) {
                val top = 6.dp.toPx()
                val bottom = size.height - 6.dp.toPx()
                val height = bottom - top
                val minX = ordered.first().x
                val maxX = ordered.last().x
                val xRange = (maxX - minX).coerceAtLeast(1L).toFloat()

                listOf(0f, 0.5f, 1f).forEach { ratio ->
                    val y = bottom - ratio * height
                    drawLine(
                        color = ChartLine,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                fun offset(point: MasteryTrendPoint): Offset {
                    val xRatio = if (ordered.size == 1) 0.5f else (point.x - minX).toFloat() / xRange
                    val yRatio = point.score.coerceIn(0.0, 1.0).toFloat()
                    return Offset(xRatio * size.width, bottom - yRatio * height)
                }

                if (ordered.size > 1) {
                    val path = Path()
                    ordered.forEachIndexed { index, point ->
                        val position = offset(point)
                        if (index == 0) path.moveTo(position.x, position.y)
                        else path.lineTo(position.x, position.y)
                    }
                    drawPath(
                        path = path,
                        color = ChartBlue,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                ordered.forEach { point ->
                    val position = offset(point)
                    val pointColor = when (point.eventType) {
                        MasteryTrendEventType.CORRECT -> ChartYellow
                        MasteryTrendEventType.INCORRECT -> ChartRed
                        else -> ChartBlue
                    }
                    drawCircle(pointColor, radius = 4.5.dp.toPx(), center = position)
                    drawCircle(ChartWhite.copy(alpha = 0.8f), radius = 1.5.dp.toPx(), center = position)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(46.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(xLabel(ordered.first().x), color = ChartMuted, fontSize = 9.sp)
                if (ordered.size > 1) {
                    Text(xLabel(ordered.last().x), color = ChartMuted, fontSize = 9.sp)
                }
            }
        }
    }
}
