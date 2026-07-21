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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.course.CourseSceneData
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

private data class RoadObject(
    val position: Float,
    val letter: String,
    val name: String,
    val type: RoadObjectType,
    val upperRow: Boolean,
)

private enum class RoadObjectType { TREE, STATION, POLE }

private val roadObjects = listOf(
    RoadObject(-4.8f, "E", "电线杆", RoadObjectType.POLE, true),
    RoadObject(-3f, "D", "槐树", RoadObjectType.TREE, false),
    RoadObject(0f, "O", "汽车站牌", RoadObjectType.STATION, true),
    RoadObject(3f, "B", "柳树", RoadObjectType.TREE, false),
    RoadObject(7.5f, "C", "标志杆", RoadObjectType.POLE, true),
)

/** School 原创的数轴教学场景，不复刻教材插图。 */
@Composable
internal fun NumberLineLessonVisual(data: CourseSceneData) {
    when (data.string("mode")) {
        "road" -> RoadScene(signed = data.boolean("signed"))
        "construction" -> NumberLineConstruction()
        "value" -> NumberLineValue(initial = data.number("initial", 6.5).toFloat())
        "example" -> FixedPointsScene(readingExercise = false)
        "read_points" -> FixedPointsScene(readingExercise = true)
        else -> AdjustableNumberLine(NumberLineMode.VALUE)
    }
}

@Composable
private fun RoadScene(signed: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (signed) "一个数同时记录方向和距离" else "以汽车站牌为基准观察相对位置",
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val left = 24f
            val right = size.width - 24f
            val roadY = size.height * 0.58f
            val minimum = -5.5f
            val maximum = 8f
            fun xFor(value: Float): Float =
                left + (value - minimum) / (maximum - minimum) * (right - left)

            drawLine(
                color = InteractiveWhite.copy(alpha = 0.72f),
                start = Offset(left, roadY),
                end = Offset(right, roadY),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            drawArrowHead(right, roadY, InteractiveBlue)
            label("西", left, roadY + 72f, InteractiveYellow, 20f, Paint.Align.LEFT)
            label("东", right, roadY + 72f, InteractiveBlue, 20f, Paint.Align.RIGHT)

            roadObjects.forEach { item ->
                val x = xFor(item.position)
                val accent = signColor(item.position)
                drawLine(accent, Offset(x, roadY - 10f), Offset(x, roadY + 10f), 3f)
                drawRoadObject(item.type, x, roadY, accent)
                val titleY = roadY - if (item.upperRow) 112f else 88f
                label(item.name, x, titleY, InteractiveWhite, 17f)
                label(item.letter, x, roadY - 18f, accent, 18f)
                val positionText = when {
                    item.position == 0f -> if (signed) "0" else "基准"
                    signed -> signedNumber(item.position)
                    else -> "${numberText(abs(item.position))} m"
                }
                label(positionText, x, roadY + 38f, accent, 18f)
            }

            val originX = xFor(0f)
            val unitX = xFor(1f)
            drawLine(InteractiveMuted, Offset(unitX, roadY - 9f), Offset(unitX, roadY + 9f), 2f)
            label("A", unitX, roadY - 18f, InteractiveMuted, 17f)
            if (signed) {
                label("+1", unitX, roadY + 38f, InteractiveMuted, 17f)
            } else {
                val unitY = roadY + 66f
                drawLine(
                    color = InteractiveYellow,
                    start = Offset(originX, unitY),
                    end = Offset(unitX, unitY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
                drawLine(InteractiveYellow, Offset(originX, unitY - 5f), Offset(originX, unitY + 5f), 2f)
                drawLine(InteractiveYellow, Offset(unitX, unitY - 5f), Offset(unitX, unitY + 5f), 2f)
                label("OA = 1 m", (originX + unitX) / 2f, unitY + 24f, InteractiveYellow, 17f)
            }
        }
        Text(
            text = if (signed) {
                "符号说明位于站牌哪一侧，数值说明离站牌有多远。"
            } else {
                "线段OA表示1 m；先确定共同基准，再观察方向和距离。"
            },
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite.copy(alpha = 0.84f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun DrawScope.drawRoadObject(type: RoadObjectType, x: Float, roadY: Float, color: Color) {
    when (type) {
        RoadObjectType.TREE -> {
            drawLine(color, Offset(x, roadY - 14f), Offset(x, roadY - 52f), 5f)
            drawCircle(color.copy(alpha = 0.82f), 15f, Offset(x, roadY - 62f))
        }
        RoadObjectType.STATION -> {
            drawLine(color, Offset(x, roadY - 14f), Offset(x, roadY - 66f), 5f)
            drawRect(InteractivePanel, Offset(x - 18f, roadY - 91f), Size(36f, 24f))
            drawRect(
                color = InteractiveWhite.copy(alpha = 0.8f),
                topLeft = Offset(x - 18f, roadY - 91f),
                size = Size(36f, 24f),
                style = Stroke(3f),
            )
        }
        RoadObjectType.POLE -> {
            drawLine(color, Offset(x, roadY - 14f), Offset(x, roadY - 66f), 4f)
            drawCircle(color, 7f, Offset(x, roadY - 73f))
        }
    }
}

@Composable
private fun NumberLineConstruction() {
    val stages = listOf("原点", "正方向", "单位长度")
    var stage by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            stages.forEachIndexed { index, title ->
                Column(
                    modifier = Modifier.weight(1f).clickable { stage = index }.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        color = if (stage == index) InteractiveWhite else InteractiveMuted,
                        fontSize = 13.sp,
                        fontWeight = if (stage == index) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth().height(2.dp)
                            .background(if (stage == index) InteractiveBlue else Color.Transparent),
                    )
                }
            }
        }

        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val left = 26f
            val right = size.width - 26f
            val center = size.width / 2f
            val y = size.height * 0.54f
            val unit = (right - left) / 8f

            drawLine(InteractiveWhite.copy(alpha = 0.72f), Offset(left, y), Offset(right, y), 3f)
            drawLine(InteractiveWhite, Offset(center, y - 13f), Offset(center, y + 13f), 3f)
            drawCircle(InteractiveWhite, 6f, Offset(center, y))
            label("O", center, y - 27f, InteractiveWhite, 21f)
            label("0", center, y + 40f, InteractiveWhite, 19f)

            if (stage >= 1) {
                drawArrowHead(right, y, InteractiveBlue)
                label("正方向", right, y - 31f, InteractiveBlue, 19f, Paint.Align.RIGHT)
                label("负方向", left, y - 31f, InteractiveYellow, 19f, Paint.Align.LEFT)
            }

            if (stage >= 2) {
                for (value in -4..4) {
                    val x = center + value * unit
                    drawLine(
                        color = if (value == 0) InteractiveWhite else InteractiveMuted,
                        start = Offset(x, y - 9f),
                        end = Offset(x, y + 9f),
                        strokeWidth = if (value == 0) 3f else 2f,
                    )
                    label(numberText(value.toFloat()), x, y + 40f, InteractiveWhite.copy(alpha = 0.82f), 17f)
                }
                drawLine(
                    color = InteractiveYellow,
                    start = Offset(center, y + 66f),
                    end = Offset(center + unit, y + 66f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
                label("1 个单位长度", center + unit / 2f, y + 91f, InteractiveYellow, 17f)
            }
        }

        Text(
            text = when (stage) {
                0 -> "原点确定基准，用它表示0。"
                1 -> "正方向规定数增大的方向。"
                else -> "单位长度决定相邻刻度代表多少。"
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
private fun NumberLineValue(initial: Float) {
    var rawValue by rememberSaveable(initial) {
        mutableFloatStateOf(initial.coerceIn(-7f, 7f))
    }
    val value = round(rawValue * 2f) / 2f

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
            val pointX = xFor(value)
            val accent = signColor(value)

            drawLine(InteractiveYellow.copy(alpha = 0.25f), Offset(left, y), Offset(center, y), 10f)
            drawLine(InteractiveBlue.copy(alpha = 0.25f), Offset(center, y), Offset(right, y), 10f)
            drawLine(InteractiveWhite.copy(alpha = 0.76f), Offset(left, y), Offset(right, y), 3f)
            drawArrowHead(right, y, InteractiveBlue)

            for (tick in -7..7) {
                val x = xFor(tick.toFloat())
                drawLine(
                    color = if (tick == 0) InteractiveWhite else InteractiveMuted,
                    start = Offset(x, y - 9f),
                    end = Offset(x, y + 9f),
                    strokeWidth = if (tick == 0) 3f else 2f,
                )
                if (tick % 2 != 0 || tick == 0) {
                    label(numberText(tick.toFloat()), x, y + 38f, InteractiveMuted, 17f)
                }
            }

            drawLine(accent, Offset(center, y), Offset(pointX, y), 9f, cap = StrokeCap.Round)
            drawCircle(accent, 10f, Offset(pointX, y))
            label(numberText(value), pointX, y - 29f, accent, 24f)
            label("负半轴", left, y - 49f, InteractiveYellow, 18f, Paint.Align.LEFT)
            label("正半轴", right, y - 49f, InteractiveBlue, 18f, Paint.Align.RIGHT)
        }

        Slider(
            value = value,
            onValueChange = { rawValue = round(it * 2f) / 2f },
            valueRange = -7f..7f,
            steps = 27,
        )
        Text(
            text = when {
                value > 0f -> "${numberText(value)}在正半轴上，与原点的距离是${numberText(value)}。"
                value < 0f -> "${numberText(value)}在负半轴上，与原点的距离是${numberText(abs(value))}。"
                else -> "0由原点表示，是正半轴与负半轴的分界。"
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
private fun FixedPointsScene(readingExercise: Boolean) {
    Canvas(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
        val left = 24f
        val right = size.width - 24f
        val y = size.height * 0.55f
        val min = if (readingExercise) -3.5f else -5f
        val max = if (readingExercise) 3.5f else 5f
        fun xFor(number: Float): Float = left + (number - min) / (max - min) * (right - left)

        drawLine(InteractiveWhite.copy(alpha = 0.76f), Offset(left, y), Offset(right, y), 3f)
        drawArrowHead(right, y, InteractiveBlue)

        val integerTicks = if (readingExercise) -3..3 else -5..5
        integerTicks.forEach { tick ->
            val x = xFor(tick.toFloat())
            drawLine(InteractiveMuted, Offset(x, y - 9f), Offset(x, y + 9f), 2f)
            label(numberText(tick.toFloat()), x, y + 39f, InteractiveMuted, 17f)
        }

        if (readingExercise) {
            listOf(
                Triple("E", -3f, InteractiveYellow),
                Triple("B", -2f, InteractiveYellow),
                Triple("A", 0f, InteractiveWhite),
                Triple("C", 1f, InteractiveBlue),
                Triple("D", 2.5f, InteractiveBlue),
            ).forEach { (name, value, color) ->
                val x = xFor(value)
                drawCircle(color, 8f, Offset(x, y))
                label(name, x, y - 28f, color, 22f)
            }
            label("观察点 D 在 2 与 3 的什么位置", size.width / 2f, y + 82f, InteractiveMuted, 17f)
        } else {
            val points = listOf(
                -4f to "−4",
                -2.5f to "−5/2",
                -1f to "−1",
                0f to "0",
                0.5f to "0.5",
                3f to "3",
                4f to "4",
            )
            points.forEachIndexed { index, (value, text) ->
                val x = xFor(value)
                val color = signColor(value)
                drawCircle(color, 8f, Offset(x, y))
                label(text, x, y - if (index % 2 == 0) 30f else 56f, color, 18f)
            }
        }
    }
}

private fun DrawScope.drawArrowHead(x: Float, y: Float, color: Color) {
    drawLine(color, Offset(x, y), Offset(x - 13f, y - 8f), 3f, StrokeCap.Round)
    drawLine(color, Offset(x, y), Offset(x - 13f, y + 8f), 3f, StrokeCap.Round)
}

private fun signColor(value: Float): Color = when {
    value < 0f -> InteractiveYellow
    value > 0f -> InteractiveBlue
    else -> InteractiveWhite
}

private fun signedNumber(value: Float): String = when {
    value > 0f -> "+${numberText(value)}"
    value < 0f -> numberText(value)
    else -> "0"
}

private fun numberText(value: Float): String {
    val integer = value.roundToInt()
    val text = if (abs(value - integer) < 0.0001f) {
        integer.toString()
    } else {
        String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
    }
    return text.replace('-', '−')
}

private fun DrawScope.label(
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
