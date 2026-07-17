package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

private data class FunctionExample(
    val label: String,
    val formula: String,
    val coefficient: Float,
    val constant: Float,
    val note: String,
)

private val linearExamples = listOf(
    FunctionExample("正比例 ①", "y = 2x", 2f, 0f, "x 每增加 1，y 增加 2。"),
    FunctionExample("正比例 ②", "y = -1.5x", -1.5f, 0f, "x 增加时，y 按固定规律减小。"),
    FunctionExample("气温问题", "y = -6x + 5", -6f, 5f, "从 5 ℃开始，登高 1 km，气温下降 6 ℃。"),
)

@Composable
internal fun LinearFunctionLab(lessonId: String) {
    var exampleIndex by rememberSaveable(lessonId, "linear-example") { mutableIntStateOf(0) }
    var xText by rememberSaveable(lessonId, "linear-x") { mutableStateOf("2") }
    val example = linearExamples[exampleIndex.coerceIn(linearExamples.indices)]
    val animatedA by animateFloatAsState(example.coefficient, tween(320), label = "linearCoefficient")
    val animatedB by animateFloatAsState(example.constant, tween(320), label = "linearConstant")
    val xValue = xText.toFloatOrNull() ?: 0f
    val animatedX by animateFloatAsState(xValue, tween(240), label = "linearX")
    val yValue = animatedA * animatedX + animatedB

    SectionTitle("列表、描点、连线", InteractiveBlue)
    Spacer(Modifier.height(12.dp))
    Text(
        "先选择教材中的一个函数，再输入 x。应用会计算对应的 y，把这一组对应值画成点。这里暂时不使用斜率、截距等后续语言。",
        color = InteractiveWhite.copy(alpha = 0.75f),
        fontSize = 16.sp,
        lineHeight = 25.sp,
    )
    Spacer(Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        linearExamples.forEachIndexed { index, item ->
            ChoicePill(
                label = item.label,
                selected = index == exampleIndex,
                modifier = Modifier.weight(1f),
            ) { exampleIndex = index }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(example.note, color = InteractiveMuted, fontSize = 14.sp, lineHeight = 21.sp)
    Spacer(Modifier.height(18.dp))

    LinearFunctionGraph(
        coefficient = animatedA,
        constant = animatedB,
        selectedX = animatedX,
    )
    Spacer(Modifier.height(18.dp))

    FunctionValueTable(coefficient = animatedA, constant = animatedB)
    Spacer(Modifier.height(18.dp))

    ValueReadout(
        expression = "把 x=${formatNumber(animatedX)} 代入 ${example.formula}",
        result = "得到 y=${formatNumber(yValue)}，对应点为 (${formatNumber(animatedX)}, ${formatNumber(yValue)})",
    )
    Spacer(Modifier.height(18.dp))

    NumericInput(
        label = "输入 x（坐标图显示范围：-5 到 5）",
        value = xText,
        onValueChange = { next ->
            if (next.length <= 6 && next.matches(Regex("-?\\d*(\\.\\d*)?"))) {
                val parsed = next.toFloatOrNull()
                if (parsed == null || parsed in -5f..5f) xText = next
            }
        },
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "观察重点：表格中的每一列都是一组 x、y 对应值；把这些点描在坐标系中并连接起来，就得到函数图像。",
        color = InteractiveGreen,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
}

@Composable
private fun FunctionValueTable(coefficient: Float, constant: Float) {
    val xs = listOf(-2f, -1f, 0f, 1f, 2f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractivePanel, RoundedCornerShape(14.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("对应值表", color = InteractiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        ValueTableRow("x", xs.map(::formatNumber), InteractiveWhite)
        ValueTableRow("y", xs.map { formatNumber(coefficient * it + constant) }, InteractiveYellow)
    }
}

@Composable
private fun ValueTableRow(label: String, values: List<String>, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, modifier = Modifier.weight(0.55f), color = color, fontWeight = FontWeight.Bold)
        values.forEach { value ->
            Text(value, modifier = Modifier.weight(1f), color = color, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LinearFunctionGraph(coefficient: Float, constant: Float, selectedX: Float) {
    val xMin = -5f
    val xMax = 5f
    val yMin = -6f
    val yMax = 6f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(InteractivePanel, RoundedCornerShape(18.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(18.dp))
            .padding(10.dp),
    ) {
        val left = 30.dp.toPx()
        val right = size.width - 18.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        fun sx(worldX: Float): Float = left + (worldX - xMin) / (xMax - xMin) * (right - left)
        fun sy(worldY: Float): Float = bottom - (worldY - yMin) / (yMax - yMin) * (bottom - top)

        for (grid in -5..5) {
            val gx = sx(grid.toFloat())
            drawLine(InteractiveLine.copy(alpha = 0.65f), Offset(gx, top), Offset(gx, bottom), 1.dp.toPx())
        }
        for (grid in -6..6) {
            val gy = sy(grid.toFloat())
            drawLine(InteractiveLine.copy(alpha = 0.65f), Offset(left, gy), Offset(right, gy), 1.dp.toPx())
        }

        drawLine(InteractiveWhite.copy(alpha = 0.66f), Offset(left, sy(0f)), Offset(right, sy(0f)), 2.dp.toPx(), StrokeCap.Round)
        drawLine(InteractiveWhite.copy(alpha = 0.66f), Offset(sx(0f), top), Offset(sx(0f), bottom), 2.dp.toPx(), StrokeCap.Round)

        clipRect(left = left, top = top, right = right, bottom = bottom) {
            val yAtMin = coefficient * xMin + constant
            val yAtMax = coefficient * xMax + constant
            drawLine(
                color = InteractiveBlue,
                start = Offset(sx(xMin), sy(yAtMin)),
                end = Offset(sx(xMax), sy(yAtMax)),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )

            val pointY = coefficient * selectedX + constant
            if (selectedX in xMin..xMax && pointY in yMin..yMax) {
                drawLine(InteractiveYellow.copy(alpha = 0.48f), Offset(sx(selectedX), sy(0f)), Offset(sx(selectedX), sy(pointY)), 2.dp.toPx())
                drawLine(InteractiveYellow.copy(alpha = 0.48f), Offset(sx(0f), sy(pointY)), Offset(sx(selectedX), sy(pointY)), 2.dp.toPx())
                drawCircle(InteractiveYellow, 7.dp.toPx(), Offset(sx(selectedX), sy(pointY)))
            }
        }

        val labelPaint = Paint().apply {
            color = InteractiveMuted.toArgb()
            textSize = 12.sp.toPx()
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText("x", right - 3.dp.toPx(), sy(0f) - 8.dp.toPx(), labelPaint)
        drawContext.canvas.nativeCanvas.drawText("y", sx(0f) + 10.dp.toPx(), top + 8.dp.toPx(), labelPaint)
    }
}

private enum class ResistanceScene(
    val title: String,
    val surface: String,
    val deceleration: Float,
    val conclusion: String,
) {
    NORMAL("有明显阻力", "较粗糙的水平面", 1.5f, "小球逐渐变慢，最后停下。"),
    SMALL("阻力较小", "更光滑的水平面", 0.75f, "小球运动得更远、更久。"),
    NONE("理想无阻力", "想象中的理想水平面", 0f, "小球没有停下来的理由。"),
}

@Composable
internal fun NewtonFirstLawLab(lessonId: String) {
    var sceneIndex by rememberSaveable(lessonId, "newton-scene") { mutableIntStateOf(0) }
    var elapsed by rememberSaveable(lessonId, "newton-t") { mutableFloatStateOf(0f) }
    var running by rememberSaveable(lessonId, "newton-running") { mutableStateOf(false) }
    val scene = ResistanceScene.entries[sceneIndex.coerceIn(ResistanceScene.entries.indices)]
    val initialVelocity = 6f
    val maxDuration = 8f
    val stopTime = if (scene.deceleration > 0f) initialVelocity / scene.deceleration else Float.POSITIVE_INFINITY
    val effectiveTime = min(elapsed, stopTime)
    val velocity = max(0f, initialVelocity - scene.deceleration * elapsed)
    val position = initialVelocity * effectiveTime - 0.5f * scene.deceleration * effectiveTime * effectiveTime
    val stopped = scene.deceleration > 0f && elapsed >= stopTime

    LaunchedEffect(running, sceneIndex) {
        if (!running) return@LaunchedEffect
        val offset = elapsed
        val start = withFrameNanos { it }
        while (isActive && running) {
            withFrameNanos { now -> elapsed = offset + (now - start) / 1_000_000_000f }
            val finished = (scene.deceleration > 0f && elapsed >= stopTime + 0.55f) || elapsed >= maxDuration
            if (finished) running = false
        }
    }

    fun reset() {
        running = false
        elapsed = 0f
    }

    SectionTitle("把阻力逐渐减小", InteractiveBlue)
    Spacer(Modifier.height(12.dp))
    Text(
        "教材没有先引入摩擦系数，而是比较真实运动与理想运动。选择不同情形，观察同一个小球是否停下，以及它能运动多远。",
        color = InteractiveWhite.copy(alpha = 0.75f),
        fontSize = 16.sp,
        lineHeight = 25.sp,
    )
    Spacer(Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResistanceScene.entries.forEachIndexed { index, item ->
            ChoicePill(
                label = item.title,
                selected = index == sceneIndex,
                modifier = Modifier.weight(1f),
            ) {
                sceneIndex = index
                reset()
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(scene.surface, color = InteractiveMuted, fontSize = 14.sp)
    Spacer(Modifier.height(18.dp))

    NewtonBallCanvas(
        scene = scene,
        elapsed = elapsed,
        position = position,
        velocity = velocity,
        stopped = stopped,
        maxDuration = maxDuration,
        initialVelocity = initialVelocity,
    )
    Spacer(Modifier.height(18.dp))

    ValueReadout(
        expression = if (stopped) "小球已经停下" else if (scene == ResistanceScene.NONE) "理想情况下，小球仍在运动" else "小球正在逐渐变慢",
        result = "已运动 ${formatNumber(position)} m · ${scene.conclusion}",
    )
    Spacer(Modifier.height(18.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InteractiveAction(
            label = if (running) "暂停" else if (elapsed > 0f) "继续" else "播放",
            color = InteractiveGreen,
            modifier = Modifier.weight(1f),
        ) { running = !running }
        InteractiveAction(
            label = "重置",
            color = InteractiveMuted,
            modifier = Modifier.weight(1f),
            onClick = ::reset,
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        when (scene) {
            ResistanceScene.NORMAL -> "现实中小球停下，是因为阻力一直在改变它的运动。"
            ResistanceScene.SMALL -> "阻力变小后，小球运动得更远。这正是伽利略继续推理的依据。"
            ResistanceScene.NONE -> "把阻力完全去掉只是理想化想象；在这个理想条件下，小球将保持运动。"
        },
        color = if (scene == ResistanceScene.NONE) InteractiveGreen else InteractiveYellow,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
}

@Composable
private fun NewtonBallCanvas(
    scene: ResistanceScene,
    elapsed: Float,
    position: Float,
    velocity: Float,
    stopped: Boolean,
    maxDuration: Float,
    initialVelocity: Float,
) {
    val worldMax = initialVelocity * maxDuration
    val fraction = (position / worldMax).coerceIn(0f, 1f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(InteractivePanel, RoundedCornerShape(18.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        val left = 28.dp.toPx()
        val right = size.width - 28.dp.toPx()
        val roadY = size.height * 0.70f
        val ballRadius = 20.dp.toPx()

        drawLine(
            color = InteractiveWhite.copy(alpha = 0.45f),
            start = Offset(left, roadY),
            end = Offset(right, roadY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        val textureCount = when (scene) {
            ResistanceScene.NORMAL -> 34
            ResistanceScene.SMALL -> 15
            ResistanceScene.NONE -> 0
        }
        repeat(textureCount) { index ->
            val x = left + (right - left) * (index + 0.5f) / max(1, textureCount)
            val height = if (index % 2 == 0) 7.dp.toPx() else 4.dp.toPx()
            drawLine(
                color = InteractiveMuted.copy(alpha = 0.30f),
                start = Offset(x - 3.dp.toPx(), roadY + height),
                end = Offset(x + 3.dp.toPx(), roadY),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val ballX = left + fraction * (right - left)
        val ballCenter = Offset(ballX, roadY - ballRadius)

        for (trail in 1..5) {
            val pastTime = max(0f, elapsed - trail * 0.12f)
            val pastEffective = if (scene.deceleration > 0f) min(pastTime, initialVelocity / scene.deceleration) else pastTime
            val pastPosition = initialVelocity * pastEffective - 0.5f * scene.deceleration * pastEffective * pastEffective
            val pastX = left + (pastPosition / worldMax).coerceIn(0f, 1f) * (right - left)
            drawCircle(
                color = InteractiveBlue.copy(alpha = 0.10f * (6 - trail)),
                radius = ballRadius * (1f - trail * 0.06f),
                center = Offset(pastX, ballCenter.y),
            )
        }

        drawOval(
            color = Color.Black.copy(alpha = 0.34f),
            topLeft = Offset(ballX - ballRadius * 1.15f, roadY - 5.dp.toPx()),
            size = Size(ballRadius * 2.3f, 10.dp.toPx()),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFBCEAF4), InteractiveBlue, Color(0xFF147C97)),
                center = Offset(ballCenter.x - ballRadius * 0.35f, ballCenter.y - ballRadius * 0.35f),
                radius = ballRadius * 1.35f,
            ),
            radius = ballRadius,
            center = ballCenter,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.65f),
            radius = 4.dp.toPx(),
            center = Offset(ballCenter.x - 7.dp.toPx(), ballCenter.y - 8.dp.toPx()),
        )
        rotate(degrees = position * 85f, pivot = ballCenter) {
            drawLine(
                color = InteractiveWhite.copy(alpha = 0.62f),
                start = Offset(ballCenter.x - ballRadius * 0.72f, ballCenter.y),
                end = Offset(ballCenter.x + ballRadius * 0.72f, ballCenter.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val angle = 0.85
            val diagonalX = cos(angle).toFloat() * ballRadius * 0.62f
            val diagonalY = sin(angle).toFloat() * ballRadius * 0.62f
            drawLine(
                color = InteractiveWhite.copy(alpha = 0.35f),
                start = Offset(ballCenter.x - diagonalX, ballCenter.y - diagonalY),
                end = Offset(ballCenter.x + diagonalX, ballCenter.y + diagonalY),
                strokeWidth = 1.5.dp.toPx(),
            )
        }

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 13.sp.toPx()
            color = InteractiveMuted.toArgb()
        }
        drawContext.canvas.nativeCanvas.drawText("起点", left, roadY + 30.dp.toPx(), paint)
        paint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText("更远", right, roadY + 30.dp.toPx(), paint)
        paint.textAlign = Paint.Align.LEFT
        paint.color = if (stopped) InteractiveRed.toArgb() else InteractiveGreen.toArgb()
        val stateText = when {
            stopped -> "停下"
            velocity < initialVelocity * 0.55f -> "正在变慢"
            scene == ResistanceScene.NONE -> "保持运动"
            else -> "正在滚动"
        }
        drawContext.canvas.nativeCanvas.drawText(stateText, left, 28.dp.toPx(), paint)

        if (stopped) {
            val pulse = ((elapsed - (initialVelocity / scene.deceleration)) / 0.55f).coerceIn(0f, 1f)
            drawCircle(
                color = InteractiveRed.copy(alpha = 0.45f * (1f - pulse)),
                radius = ballRadius + pulse * 18.dp.toPx(),
                center = ballCenter,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(if (selected) InteractiveBlue.copy(alpha = 0.15f) else InteractivePanel, RoundedCornerShape(12.dp))
            .border(1.dp, if (selected) InteractiveBlue else InteractiveLine, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) InteractiveBlue else InteractiveMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun NumericInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractivePanel, RoundedCornerShape(14.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(label, color = InteractiveMuted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = InteractiveWhite, fontSize = 26.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(InteractiveBlue),
            singleLine = true,
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text("0", color = InteractiveMuted, fontSize = 26.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ValueReadout(expression: String, result: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractivePanel, RoundedCornerShape(14.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(expression, color = InteractiveMuted, fontSize = 15.sp)
        Text(result, color = InteractiveYellow, fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatNumber(value: Float): String {
    if (!value.isFinite()) return "∞"
    val rounded = round(value * 100f) / 100f
    return if (abs(rounded - rounded.toInt()) < 0.001f) rounded.toInt().toString() else rounded.toString().trimEnd('0').trimEnd('.')
}
