package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.round

private data class CoordinateValidationExample(
    val label: String,
    val formula: String,
    val coefficient: Float,
    val constant: Float,
    val xRange: ClosedFloatingPointRange<Float>,
    val yRange: ClosedFloatingPointRange<Float>,
    val initialX: Float,
) {
    fun valueAt(x: Float): Float = coefficient * x + constant
}

private val coordinateValidationExamples = listOf(
    CoordinateValidationExample(
        label = "y=2x",
        formula = "y = 2x",
        coefficient = 2f,
        constant = 0f,
        xRange = -3f..3f,
        yRange = -6f..6f,
        initialX = 1f,
    ),
    CoordinateValidationExample(
        label = "y=-1.5x",
        formula = "y = -1.5x",
        coefficient = -1.5f,
        constant = 0f,
        xRange = -4f..4f,
        yRange = -6f..6f,
        initialX = 2f,
    ),
    CoordinateValidationExample(
        label = "气温问题",
        formula = "y = -6x + 5",
        coefficient = -6f,
        constant = 5f,
        xRange = 0f..1.5f,
        yRange = -4f..6f,
        initialX = 0.5f,
    ),
)

@Composable
internal fun LinearCoordinateValidationLab(lessonId: String) {
    var exampleIndex by rememberSaveable(lessonId, "xy-example") { mutableIntStateOf(0) }
    val example = coordinateValidationExamples[exampleIndex.coerceIn(coordinateValidationExamples.indices)]
    var x by rememberSaveable(lessonId, "xy-x") { mutableFloatStateOf(example.initialX) }
    var y by rememberSaveable(lessonId, "xy-y") { mutableFloatStateOf(example.valueAt(example.initialX)) }

    LaunchedEffect(exampleIndex) {
        x = example.initialX
        y = example.valueAt(example.initialX)
    }

    val expectedY = example.valueAt(x)
    val error = y - expectedY
    val matches = abs(error) < 0.051f

    SectionTitle("验证 x 与 y 的对应关系", InteractivePurple)
    Spacer(Modifier.height(12.dp))
    Text(
        "分别调整 x 和 y，观察点 (x, y) 怎样移动。只有当这组数满足所选函数时，这个点才会落在函数图像上。",
        color = InteractiveWhite.copy(alpha = 0.75f),
        fontSize = 16.sp,
        lineHeight = 25.sp,
    )
    Spacer(Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        coordinateValidationExamples.forEachIndexed { index, item ->
            ValidationChoice(
                label = item.label,
                selected = index == exampleIndex,
                modifier = Modifier.weight(1f),
            ) { exampleIndex = index }
        }
    }
    Spacer(Modifier.height(18.dp))

    CoordinateValidationGraph(
        example = example,
        x = x,
        y = y,
        expectedY = expectedY,
        matches = matches,
    )
    Spacer(Modifier.height(18.dp))

    CoordinateSlider(
        label = "调整 x",
        value = x,
        range = example.xRange,
        color = InteractiveBlue,
        onValueChange = { x = snapCoordinate(it) },
    )
    Spacer(Modifier.height(12.dp))
    CoordinateSlider(
        label = "调整 y",
        value = y,
        range = example.yRange,
        color = if (matches) InteractiveGreen else InteractiveRed,
        onValueChange = { y = snapCoordinate(it) },
    )
    Spacer(Modifier.height(16.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (matches) InteractiveGreen.copy(alpha = 0.10f) else InteractiveRed.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (matches) InteractiveGreen.copy(alpha = 0.65f) else InteractiveRed.copy(alpha = 0.55f),
                RoundedCornerShape(14.dp),
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "当前点：(${coordinateNumber(x)}, ${coordinateNumber(y)})",
            color = InteractiveWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "当 x=${coordinateNumber(x)} 时，${example.formula} 给出的对应值是 y=${coordinateNumber(expectedY)}。",
            color = InteractiveMuted,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        )
        Text(
            when {
                matches -> "验证通过：当前点在函数图像上。"
                error > 0f -> "还不符合：当前 y 偏高 ${coordinateNumber(abs(error))}。"
                else -> "还不符合：当前 y 偏低 ${coordinateNumber(abs(error))}。"
            },
            color = if (matches) InteractiveGreen else InteractiveRed,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        if (!matches) {
            Spacer(Modifier.height(4.dp))
            InteractiveAction(
                label = "把 y 调到正确对应值",
                color = InteractiveYellow,
            ) { y = snapCoordinate(expectedY) }
        }
    }
}

@Composable
private fun CoordinateValidationGraph(
    example: CoordinateValidationExample,
    x: Float,
    y: Float,
    expectedY: Float,
    matches: Boolean,
) {
    val xMin = example.xRange.start
    val xMax = example.xRange.endInclusive
    val yMin = example.yRange.start
    val yMax = example.yRange.endInclusive

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(InteractivePanel, RoundedCornerShape(18.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(18.dp))
            .padding(10.dp),
    ) {
        val left = 34.dp.toPx()
        val right = size.width - 18.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 34.dp.toPx()
        fun sx(worldX: Float): Float = left + (worldX - xMin) / (xMax - xMin) * (right - left)
        fun sy(worldY: Float): Float = bottom - (worldY - yMin) / (yMax - yMin) * (bottom - top)

        repeat(9) { index ->
            val ratio = index / 8f
            val gx = left + ratio * (right - left)
            val gy = top + ratio * (bottom - top)
            drawLine(InteractiveLine.copy(alpha = 0.62f), Offset(gx, top), Offset(gx, bottom), 1.dp.toPx())
            drawLine(InteractiveLine.copy(alpha = 0.62f), Offset(left, gy), Offset(right, gy), 1.dp.toPx())
        }

        if (0f in xMin..xMax) {
            drawLine(
                InteractiveWhite.copy(alpha = 0.68f),
                Offset(sx(0f), top),
                Offset(sx(0f), bottom),
                2.dp.toPx(),
                StrokeCap.Round,
            )
        }
        if (0f in yMin..yMax) {
            drawLine(
                InteractiveWhite.copy(alpha = 0.68f),
                Offset(left, sy(0f)),
                Offset(right, sy(0f)),
                2.dp.toPx(),
                StrokeCap.Round,
            )
        }

        clipRect(left = left, top = top, right = right, bottom = bottom) {
            drawLine(
                color = InteractiveBlue,
                start = Offset(sx(xMin), sy(example.valueAt(xMin))),
                end = Offset(sx(xMax), sy(example.valueAt(xMax))),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )

            val correctCenter = Offset(sx(x), sy(expectedY))
            if (!matches) {
                drawLine(
                    color = InteractiveYellow.copy(alpha = 0.45f),
                    start = Offset(sx(x), sy(y)),
                    end = correctCenter,
                    strokeWidth = 2.dp.toPx(),
                )
                drawCircle(
                    color = InteractiveYellow.copy(alpha = 0.82f),
                    radius = 8.dp.toPx(),
                    center = correctCenter,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            val candidateCenter = Offset(sx(x), sy(y))
            drawLine(
                color = (if (matches) InteractiveGreen else InteractiveRed).copy(alpha = 0.34f),
                start = Offset(sx(x), if (0f in yMin..yMax) sy(0f) else bottom),
                end = candidateCenter,
                strokeWidth = 2.dp.toPx(),
            )
            drawLine(
                color = (if (matches) InteractiveGreen else InteractiveRed).copy(alpha = 0.34f),
                start = Offset(if (0f in xMin..xMax) sx(0f) else left, sy(y)),
                end = candidateCenter,
                strokeWidth = 2.dp.toPx(),
            )
            drawCircle(
                color = if (matches) InteractiveGreen else InteractiveRed,
                radius = 8.dp.toPx(),
                center = candidateCenter,
            )
            drawCircle(
                color = InteractiveWhite.copy(alpha = 0.85f),
                radius = 3.dp.toPx(),
                center = candidateCenter,
            )
        }

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 12.sp.toPx()
            color = InteractiveMuted.toArgb()
        }
        drawContext.canvas.nativeCanvas.drawText(example.formula, left, 17.dp.toPx(), paint)
        paint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText("x", right, bottom + 25.dp.toPx(), paint)
        paint.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("y", left - 18.dp.toPx(), top + 4.dp.toPx(), paint)
    }
}

@Composable
private fun CoordinateSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    color: Color,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractivePanel, RoundedCornerShape(14.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = InteractiveMuted, fontSize = 14.sp)
            Text(
                coordinateNumber(value),
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Composable
private fun ValidationChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .background(
                if (selected) InteractivePurple.copy(alpha = 0.17f) else InteractivePanel,
                RoundedCornerShape(12.dp),
            )
            .border(
                1.dp,
                if (selected) InteractivePurple else InteractiveLine,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) InteractivePurple else InteractiveMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun snapCoordinate(value: Float): Float = round(value * 10f) / 10f

private fun coordinateNumber(value: Float): String {
    val rounded = snapCoordinate(value)
    return if (abs(rounded - rounded.toInt()) < 0.001f) {
        rounded.toInt().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
