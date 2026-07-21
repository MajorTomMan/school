package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

private data class RoadPoint(
    val position: Float,
    val letter: String,
    val label: String,
    val kind: String,
    val highLabel: Boolean,
)

private val roadPoints = listOf(
    RoadPoint(-4.8f, "E", "电线杆", "pole", true),
    RoadPoint(-3f, "D", "槐树", "tree", false),
    RoadPoint(0f, "O", "汽车站牌", "station", true),
    RoadPoint(3f, "B", "柳树", "tree", false),
    RoadPoint(7.5f, "C", "交通标志杆", "pole", true),
)

/** School 原创的“数轴”教学场景，不复刻教材插图。 */
@Composable
internal fun NumberLineLessonVisual(params: Map<String, String> = emptyMap()) {
    when (params["mode"]) {
        "road" -> RoadPositionVisual(signed = params["signed"] == "true")
        "construction" -> NumberLineConstructionVisual()
        "value" -> NumberLineValueVisual(initial = params["initial"]?.toFloatOrNull() ?: 6.5f)
        "example" -> NumberLineFixedPointsVisual(readingExercise = false)
        "read_points" -> NumberLineFixedPointsVisual(readingExercise = true)
        else -> AdjustableNumberLine(NumberLineMode.VALUE)
    }
}

@Composable
private fun RoadPositionVisual(signed: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (signed) "同一位置，同时写出方向和距离" else "以汽车站牌为基准观察左右与距离",
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val left = 22f
            val right = size.width - 22f
            val roadY = size.height * 0.58f
            fun xFor(position: Float): Float = left + (position + 8f) / 16f * (right - left)

            drawLine(
                color = InteractiveWhite.copy(alpha = 0.68f),
                start = Offset(left, roadY),
                end = Offset(right, roadY),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = InteractiveBlue,
                start = Offset(right, roadY),
                end = Offset(right - 13f, roadY - 8f),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = InteractiveBlue,
                start = Offset(right, roadY),
                end = Offset(right - 13f, roadY + 8f),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
            drawNumberLineLabel("西", left, roadY + 70f, InteractiveYellow, 22f, Paint.Align.LEFT)
            drawNumberLineLabel("东", right, roadY + 70f, InteractiveBlue, 22f, Paint.Align.RIGHT)

            roadPoints.forEach { point ->
                val x = xFor(point.position)
                val accent = when {
                    point.position < 0f -> InteractiveYellow
                    point.position > 0f -> InteractiveBlue
                    else -> InteractiveWhite
                }
                drawLine(
                    color = accent.copy(alpha = 0.8f),
                    start = Offset(x, roadY - 12f),
                    end = Offset(x, roadY + 12f),
                    strokeWidth = 3f,
                )
                when (point.kind) {
                    "tree" -> {
                        drawLine(accent, Offset(x, roadY - 16f), Offset(x, roadY - 55f), 5f)
                        drawCircle(accent.copy(alpha = 0.82f), 16f, Offset(x, roadY - 65f))
                    }
                    "station" -> {
                        drawLine(accent, Offset(x, roadY - 15f), Offset(x, roadY - 70f), 5f)
                        drawRect(
                            color = InteractivePanel,
                            topLeft = Offset(x - 19f, roadY - 94f),
                            size = androidx.compose.ui.geometry.Size(38f, 26f),
                        )
                        drawRect(
                            color = InteractiveWhite.copy(alpha = 0.78f),
                            topLeft = Offset(x - 18f, roadY - 93f),
                            size = androidx.compose.ui.geometry.Size(36f, 24f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                        )
                    }
                    else -> {
                        drawLine(accent, Offset(x, roadY - 16f), Offset(x, roadY - 72f), 4f)
                        drawCircle(accent, 8f, Offset(x, roadY - 78f))
                    }
                }
                val labelY = roadY - if (point.highLabel) 112f else 91f
                drawNumberLineLabel(point.label, x, labelY, InteractiveWhite, 21f)
                drawNumberLineLabel(point.letter, x, roadY - 18f, accent, 20f)
                val value = if (signed) signedNumber(point.position) else {
                    if (point.position == 0f) "基准" else "${plainNumber(abs(point.position))} m"
                }
                drawNumberLineLabel(value, x, roadY + 38f, accent, 20f)
            }
        }
        Text(
            text = if (signed) {
                "负号表示西侧，正号表示东侧；数值表示离站牌的距离。"
            } else {
                "左右是两个相反方向；标出的长度是各物体到站牌的距离。"
            },
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite.copy(alpha = 0.82f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NumberLineConstructionVisual() {
    val labels = listOf("原点", "正方向", "单位长度")
    var step by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Column(
                    modifier = Modifier.weight(1f).clickable { step = index }.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = label,
                        color = if (step == index) InteractiveWhite else InteractiveMuted,
                        fontSize = 13.sp,
                        fontWeight = if (step == index) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth().height(2.dp)
                            .background(if (step == index) InteractiveBlue else Color.Transparent),
                    )
                }
            }
        }

        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val left = 25f
            val right = size.width - 25f
            val center = size.width / 2f
            val y = size.height * 0.55f
            drawLine(
                color = InteractiveWhite.copy(alpha = 0.7f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
            drawLine(InteractiveWhite, Offset(center, y - 14f), Offset(center, y + 14f), 3f)
            drawCircle(InteractiveWhite, 6f, Offset(center, y))
            drawNumberLineLabel("O", center, y - 26f, InteractiveWhite, 22f)
            drawNumberLineLabel("0", center, y + 42f, InteractiveWhite, 21f)

            if (step >= 1) {
                drawLine(InteractiveBlue, Offset(right, y), Offset(right - 14f, y - 9f), 4f)
                drawLine(InteractiveBlue, Offset(right, y), Offset(right - 14f, y + 9f), 4f)
                drawNumberLineLabel("正方向", right, y - 30f, InteractiveBlue, 21f, Paint.Align.RIGHT)
                drawNumberLineLabel("负方向", left, y - 30f, InteractiveYellow, 21f, Paint.Align.LEFT)
            }

            if (step >= 2) {
                val unit = (right - left) / 8f
                for (value in -4..4) {
                    val x = center + value * unit
                    drawLine(
                        color = if (value == 0) InteractiveWhite else InteractiveMuted,
                        start = Offset(x, y - 10f),
                        end = Offset(x, y + 10f),
                        strokeWidth = if (value == 0) 3f else 2f,
                    )
                    drawNumberLineLabel(value.toString(), x, y + 42f, InteractiveWhite.copy(alpha = 0.82f), 19f)
                }
                drawLine(
                    color = InteractiveYellow,
                    start = Offset(center, y + 68f),
                    end = Offset(center + unit, y + 68f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
                drawNumberLineLabel("1 个单位长度", center + unit / 2f, y + 94f, InteractiveYellow, 19f)
            }
        }

        Text(
            text = when (step) {
                0 -> "原点是数轴的基准点，用它表示0。"
                1 -> "正方向规定数增大的方向，反方向为负方向。"
                else -> "单位长度决定相邻刻度之间代表的数量。"
            },
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite.copy(alpha = 0.84f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NumberLineValueVisual(initial: Float) {
    var value by rememberSaveable { mutableFloatStateOf(initial.coerceIn(-7f, 7f)) }
    val snapped = round(value * 2f) / 2f

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val left = 24f
            val right = size.width - 24f
            val center = (left + right) / 2f
            val y = size.height * 0.57f
            fun xFor(number: Float): Float = left + (number + 7f) / 14f * (right - left)
            val pointX = xFor(snapped)
            val accent = when {
                snapped > 0f -> InteractiveBlue
                snapped < 0f -> InteractiveYellow
                else -> InteractiveWhite
            }

            drawLine(InteractiveYellow.copy(alpha = 0.26f), Offset(left, y), Offset(center, y), 10f)
            drawLine(InteractiveBlue.copy(alpha = 0.26f), Offset(center, y), Offset(right, y), 10f)
            drawLine(InteractiveWhite.copy(alpha = 0.76f), Offset(left, y), Offset(right, y), 3f)
            drawLine(InteractiveBlue, Offset(right, y), Offset(right - 13f, y - 8f), 3f)
            drawLine(InteractiveBlue, Offset(right, y), Offset(right - 13f, y + 8f), 3f)

            for (tick in -7..7) {
                val x = xFor(tick.toFloat())
                drawLine(
                    color = if (tick == 0) InteractiveWhite else InteractiveMuted,
                    start = Offset(x, y - 9f),
                    end = Offset(x, y + 9f),
                    strokeWidth = if (tick == 0) 3f else 2f,
                )
                if (tick % 2 != 0 || tick == 0) {
                    drawNumberLineLabel(tick.toString(), x, y + 38f, InteractiveMuted, 18f)
                }
            }

            drawLine(accent, Offset(center, y), Offset(pointX, y), 9f, cap = StrokeCap.Round)
            drawCircle(accent, 10f, Offset(pointX, y))
            drawNumberLineLabel(plainNumber(snapped), pointX, y - 28f, accent, 25f)
            drawNumberLineLabel("负半轴", left, y - 48f, InteractiveYellow, 20f, Paint.Align.LEFT)
            drawNumberLineLabel("正半轴", right, y - 48f, InteractiveBlue, 20f, Paint.Align.RIGHT)
        }

        Slider(
            value = snapped,
            onValueChange = { value = round(it * 2f) / 2f },
            valueRange = -7f..7f,
            steps = 27,
        )
        Text(
            text = when {
                snapped > 0f -> "${plainNumber(snapped)}在正半轴上，与原点的距离是${plainNumber(snapped)}。"
                snapped < 0f -> "${plainNumber(snapped)}在负半轴上，与原点的距离是${plainNumber(abs(snapped))}。"
                else -> "0由原点表示，它是正半轴和负半轴的分界。"
            },
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite.copy(alpha = 0.84f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NumberLineFixedPointsVisual(readingExercise: Boolean) {
    Canvas(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
        val left = 24f
        val right = size.width - 24f
        val y = size.height * 0.55f
        val min = if (readingExercise) -3.5f else -5f
        val max = if (readingExercise) 3.5f else 5f
        fun xFor(number: Float): Float = left + (number - min) / (max - min) * (right - left)

        drawLine(InteractiveWhite.copy(alpha = 0.76f), Offset(left, y), Offset(right, y), 3f)
        drawLine(InteractiveBlue, Offset(right, y), Offset(right - 13f, y - 8f), 3f)
        drawLine(InteractiveBlue, Offset(right, y), Offset(right - 13f, y + 8f), 3f)

        if (readingExercise) {
            val ticks = listOf(-3f, -2f, -1f, 0f, 1f, 2f, 2.5f, 3f)
            ticks.forEach { value ->
                val x = xFor(value)
                drawLine(InteractiveMuted, Offset(x, y - 9f), Offset(x, y + 9f), 2f)
                drawNumberLineLabel(plainNumber(value), x, y + 39f, InteractiveMuted, 18f)
            }
            listOf(
                Triple("E", -3f, InteractiveYellow),
                Triple("B", -2f, InteractiveYellow),
                Triple("A", 0f, InteractiveWhite),
                Triple("C", 1f, InteractiveBlue),
                Triple("D", 2.5f, InteractiveBlue),
            ).forEach { (letter, value, color) ->
                val x = xFor(value)
                drawCircle(color, 8f, Offset(x, y))
                drawNumberLineLabel(letter, x, y - 28f, color, 23f)
            }
        } else {
            for (tick in -5..5) {
                val x = xFor(tick.toFloat())
                drawLine(InteractiveMuted, Offset(x, y - 8f), Offset(x, y + 8f), 2f)
                drawNumberLineLabel(tick.toString(), x, y + 40f, InteractiveMuted, 17f)
            }
            val values = listOf(-4f, -2.5f, -1f, 0f, 0.5f, 3f, 4f)
            val labels = listOf("−4", "−5/2", "−1", "0", "0.5", "3", "4")
            values.forEachIndexed { index, value ->
                val x = xFor(value)
                val color = when {
                    value < 0f -> InteractiveYellow
                    value > 0f -> InteractiveBlue
                    else -> InteractiveWhite
                }
                drawCircle(color, 8f, Offset(x, y))
                drawNumberLineLabel(
                    text = labels[index],
                    x = x,
                    y = y - if (index % 2 == 0) 30f else 57f,
                    color = color,
                    textSize = 20f,
                )
            }
        }
    }
}

private fun signedNumber(value: Float): String = when {
    value > 0f -> "+${plainNumber(value)}"
    value < 0f -> plainNumber(value)
    else -> "0"
}

private fun plainNumber(value: Float): String {
    val integer = value.roundToInt()
    return if (abs(value - integer) < 0.0001f) integer.toString() else {
        String.format(java.util.Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
    }
}

private fun DrawScope.drawNumberLineLabel(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    textSize: Float,
    align: Paint.Align = Paint.Align.CENTER,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        this.textSize = textSize
        textAlign = align
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
