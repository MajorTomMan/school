package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

private data class OppositeQuantityScene(
    val id: String,
    val label: String,
    val unit: String,
    val positiveMeaning: String,
    val negativeMeaning: String,
    val bound: Float,
    val step: Float,
    val initialValue: Float,
)

private val oppositeQuantityScenes = listOf(
    OppositeQuantityScene("temperature", "温度", "℃", "零上", "零下", 10f, 1f, 3f),
    OppositeQuantityScene("account", "收支", "万元", "盈利", "亏损", 50f, 10f, 50f),
    OppositeQuantityScene("change", "变化", "%", "增长", "减少", 10f, 0.1f, -0.7f),
    OppositeQuantityScene("deviation", "质量偏差", "g", "超过标准", "低于标准", 100f, 5f, -30f),
    OppositeQuantityScene("elevation", "海拔", "m", "高于海平面", "低于海平面", 300f, 10f, 120f),
    OppositeQuantityScene("tolerance", "允许偏差", "mm", "偏大", "偏小", 0.08f, 0.01f, 0.03f),
)

/**
 * School 自有的“相反意义的量”交互场景。
 *
 * 教材文字负责给出定义和例题；这里把基准、方向和变化量变成可操作的模型。
 * 场景由课程页参数选择，不复刻教材图片。
 */
@Composable
internal fun OppositeQuantitiesSceneVisual(data: CourseSceneData) {
    val requestedScene = oppositeQuantityScenes.firstOrNull { it.id == data.string("scene") }
        ?: oppositeQuantityScenes.first()
    val allowedIds = data.strings("scenes")
    val availableScenes = allowedIds
        .mapNotNull { id -> oppositeQuantityScenes.firstOrNull { it.id == id } }
        .distinctBy { it.id }
        .ifEmpty { listOf(requestedScene) }

    var selectedId by rememberSaveable(data.string("scene"), allowedIds.joinToString("|")) {
        mutableStateOf(requestedScene.id)
    }
    val selectedScene = availableScenes.firstOrNull { it.id == selectedId }
        ?: availableScenes.first()
    var value by rememberSaveable(selectedScene.id) {
        mutableFloatStateOf(selectedScene.initialValue)
    }
    val animatedValue by animateFloatAsState(
        targetValue = value,
        label = "opposite-quantity-value",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (availableScenes.size > 1) {
            Row(modifier = Modifier.fillMaxWidth()) {
                availableScenes.forEach { option ->
                    val selected = option.id == selectedScene.id
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedId = option.id
                                value = option.initialValue
                            }
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = option.label,
                            color = if (selected) InteractiveWhite else InteractiveMuted,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (selected) InteractiveBlue else Color.Transparent),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (selectedScene.id) {
                    "tolerance" -> "标准直径 40.00 mm"
                    "elevation" -> "海平面为 0 m"
                    else -> "以 0 为基准"
                },
                color = InteractiveMuted,
                fontSize = 12.sp,
            )
            Text(
                text = displaySignedQuantity(animatedValue, selectedScene.unit),
                color = valueColor(animatedValue),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            when (selectedScene.id) {
                "temperature" -> drawTemperatureScene(animatedValue)
                "elevation" -> drawElevationScene(animatedValue, selectedScene.bound)
                "tolerance" -> drawToleranceScene(animatedValue)
                else -> drawHorizontalOppositeScene(
                    value = animatedValue,
                    bound = selectedScene.bound,
                    negativeLabel = selectedScene.negativeMeaning,
                    positiveLabel = selectedScene.positiveMeaning,
                    unit = selectedScene.unit,
                )
            }
        }

        Slider(
            value = value.coerceIn(-selectedScene.bound, selectedScene.bound),
            onValueChange = { raw -> value = snapToStep(raw, selectedScene.step) },
            valueRange = -selectedScene.bound..selectedScene.bound,
            steps = ((selectedScene.bound * 2f / selectedScene.step).roundToInt() - 1)
                .coerceAtLeast(0),
        )
        Text(
            text = observationText(selectedScene, animatedValue),
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite.copy(alpha = 0.82f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun observationText(scene: OppositeQuantityScene, value: Float): String = when {
    scene.id == "tolerance" && abs(value) <= 0.0501f ->
        "实际直径是 ${displayQuantity(40f + value)} mm，位于 39.95～40.05 mm 的合格范围内。"
    scene.id == "tolerance" ->
        "实际直径是 ${displayQuantity(40f + value)} mm，已经超出 ±0.05 mm 的允许偏差。"
    abs(value) < 0.0001f ->
        "0 是两个相反方向共同的基准，不表示任何一边。"
    value > 0f ->
        "+ 表示相对基准向“${scene.positiveMeaning}”的方向变化。"
    else ->
        "− 表示相对基准向“${scene.negativeMeaning}”的方向变化。"
}

private fun snapToStep(value: Float, step: Float): Float = round(value / step) * step

private fun valueColor(value: Float): Color = when {
    value > 0.0001f -> InteractiveBlue
    value < -0.0001f -> InteractiveYellow
    else -> InteractiveWhite
}

private fun displayQuantity(value: Float): String {
    val rounded = value.roundToInt()
    return if (abs(value - rounded) < 0.0001f) {
        rounded.toString()
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

private fun displaySignedQuantity(value: Float, unit: String): String = when {
    value > 0.0001f -> "+${displayQuantity(value)}$unit"
    value < -0.0001f -> "${displayQuantity(value)}$unit"
    else -> "0$unit"
}

private fun DrawScope.drawTemperatureScene(value: Float) {
    val minValue = -10f
    val maxValue = 10f
    val top = 24f
    val bottom = size.height - 34f
    val centerX = size.width * 0.48f
    val tubeWidth = 32f
    val bulbRadius = 24f
    val zeroY = bottom - (0f - minValue) / (maxValue - minValue) * (bottom - top)
    val valueY = bottom - (value - minValue) / (maxValue - minValue) * (bottom - top)
    val color = valueColor(value)

    drawLine(
        color = InteractiveWhite.copy(alpha = 0.45f),
        start = Offset(centerX, top),
        end = Offset(centerX, bottom),
        strokeWidth = tubeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = InteractivePanel,
        start = Offset(centerX, top + 2f),
        end = Offset(centerX, bottom),
        strokeWidth = tubeWidth - 8f,
        cap = StrokeCap.Round,
    )
    drawCircle(InteractiveWhite.copy(alpha = 0.45f), bulbRadius + 4f, Offset(centerX, bottom + 5f))
    drawCircle(color, bulbRadius, Offset(centerX, bottom + 5f))
    drawLine(
        color = color,
        start = Offset(centerX, bottom),
        end = Offset(centerX, valueY),
        strokeWidth = tubeWidth - 12f,
        cap = StrokeCap.Round,
    )

    (-10..10 step 5).forEach { tick ->
        val y = bottom - (tick - minValue) / (maxValue - minValue) * (bottom - top)
        val strong = tick == 0
        drawLine(
            color = if (strong) InteractiveWhite else InteractiveMuted,
            start = Offset(centerX + 26f, y),
            end = Offset(centerX + if (strong) 58f else 48f, y),
            strokeWidth = if (strong) 3f else 2f,
        )
        drawSceneLabel(
            text = if (tick > 0) "+$tick" else tick.toString(),
            x = centerX + 82f,
            y = y + 7f,
            color = if (strong) InteractiveWhite else InteractiveMuted,
            textSize = 22f,
        )
    }

    drawLine(
        color = InteractiveWhite.copy(alpha = 0.65f),
        start = Offset(centerX - 88f, zeroY),
        end = Offset(centerX + 62f, zeroY),
        strokeWidth = 2f,
    )
    drawSceneLabel("零上", centerX - 58f, top + 10f, InteractiveBlue, 23f, Paint.Align.RIGHT)
    drawSceneLabel("0℃ 基准", centerX - 110f, zeroY + 7f, InteractiveWhite, 23f, Paint.Align.RIGHT)
    drawSceneLabel("零下", centerX - 58f, bottom - 2f, InteractiveYellow, 23f, Paint.Align.RIGHT)
}

private fun DrawScope.drawElevationScene(value: Float, bound: Float) {
    val left = 24f
    val right = size.width - 24f
    val top = 24f
    val bottom = size.height - 26f
    val seaY = size.height * 0.55f
    val axisX = size.width * 0.55f
    val halfRange = minOf(seaY - top, bottom - seaY)
    val pointY = seaY - (value / bound).coerceIn(-1f, 1f) * halfRange
    val color = valueColor(value)

    drawLine(
        color = InteractiveBlue.copy(alpha = 0.7f),
        start = Offset(left, seaY),
        end = Offset(right, seaY),
        strokeWidth = 4f,
    )
    drawLine(
        color = InteractiveWhite.copy(alpha = 0.5f),
        start = Offset(axisX, top),
        end = Offset(axisX, bottom),
        strokeWidth = 2f,
    )

    val mountain = Path().apply {
        moveTo(right - 150f, seaY)
        lineTo(right - 92f, seaY - 94f)
        lineTo(right - 45f, seaY - 28f)
        lineTo(right, seaY)
    }
    drawPath(mountain, InteractiveBlue.copy(alpha = 0.14f))
    drawPath(mountain, InteractiveBlue.copy(alpha = 0.55f), style = Stroke(width = 3f))

    val basin = Path().apply {
        moveTo(left, seaY)
        lineTo(left + 52f, seaY + 58f)
        lineTo(left + 118f, seaY + 24f)
        lineTo(left + 150f, seaY)
    }
    drawPath(basin, InteractiveYellow.copy(alpha = 0.12f))
    drawPath(basin, InteractiveYellow.copy(alpha = 0.5f), style = Stroke(width = 3f))

    drawCircle(color, radius = 10f, center = Offset(axisX, pointY))
    drawLine(
        color = color.copy(alpha = 0.65f),
        start = Offset(axisX, pointY),
        end = Offset(axisX + 72f, pointY),
        strokeWidth = 3f,
    )

    drawSceneLabel("高于海平面", left, top + 12f, InteractiveBlue, 22f, Paint.Align.LEFT)
    drawSceneLabel("海平面 0 m", left, seaY - 12f, InteractiveWhite, 23f, Paint.Align.LEFT)
    drawSceneLabel("低于海平面", left, bottom, InteractiveYellow, 22f, Paint.Align.LEFT)
    drawSceneLabel(
        displaySignedQuantity(value, "m"),
        (axisX + 82f).coerceAtMost(right - 5f),
        pointY + 7f,
        color,
        26f,
        Paint.Align.LEFT,
    )
}

private fun DrawScope.drawToleranceScene(deviation: Float) {
    val standard = 40f
    val allowed = 0.05f
    val minValue = standard - 0.08f
    val maxValue = standard + 0.08f
    val actual = standard + deviation
    val left = 24f
    val right = size.width - 24f
    val y = size.height * 0.58f
    fun xFor(value: Float): Float =
        left + (value - minValue) / (maxValue - minValue) * (right - left)

    val allowedLeft = xFor(standard - allowed)
    val allowedRight = xFor(standard + allowed)
    val actualX = xFor(actual.coerceIn(minValue, maxValue))
    val accepted = abs(deviation) <= allowed + 0.0001f
    val actualColor = if (accepted) InteractiveGreen else InteractiveRed

    drawRect(
        color = InteractiveGreen.copy(alpha = 0.12f),
        topLeft = Offset(allowedLeft, y - 22f),
        size = Size(allowedRight - allowedLeft, 44f),
    )
    drawLine(
        color = InteractiveWhite.copy(alpha = 0.5f),
        start = Offset(left, y),
        end = Offset(right, y),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )

    listOf(
        standard - allowed to "39.95",
        standard to "40.00",
        standard + allowed to "40.05",
    ).forEach { (tick, label) ->
        val x = xFor(tick)
        drawLine(
            color = if (tick == standard) InteractiveWhite else InteractiveGreen,
            start = Offset(x, y - 25f),
            end = Offset(x, y + 25f),
            strokeWidth = if (tick == standard) 3f else 2f,
        )
        drawSceneLabel(label, x, y + 55f, InteractiveMuted, 21f)
    }

    drawCircle(actualColor, radius = 11f, center = Offset(actualX, y))
    drawLine(
        color = actualColor,
        start = Offset(actualX, y - 10f),
        end = Offset(actualX, y - 68f),
        strokeWidth = 3f,
    )
    drawSceneLabel(
        "${displayQuantity(actual)} mm",
        actualX.coerceIn(left + 52f, right - 52f),
        y - 80f,
        actualColor,
        26f,
    )
    drawSceneLabel(
        if (accepted) "合格范围" else "超出允许范围",
        size.width / 2f,
        28f,
        actualColor,
        25f,
    )
    drawSceneLabel("−0.05", allowedLeft, y - 35f, InteractiveGreen, 20f)
    drawSceneLabel("+0.05", allowedRight, y - 35f, InteractiveGreen, 20f)
}

private fun DrawScope.drawHorizontalOppositeScene(
    value: Float,
    bound: Float,
    negativeLabel: String,
    positiveLabel: String,
    unit: String,
) {
    val left = 28f
    val right = size.width - 28f
    val centerX = size.width / 2f
    val y = size.height * 0.55f
    val endX = centerX + value / bound * (right - centerX)
    val color = valueColor(value)

    drawLine(
        color = InteractiveWhite.copy(alpha = 0.52f),
        start = Offset(left, y),
        end = Offset(right, y),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = InteractiveWhite,
        start = Offset(centerX, y - 25f),
        end = Offset(centerX, y + 25f),
        strokeWidth = 3f,
    )
    drawLine(
        color = color,
        start = Offset(centerX, y),
        end = Offset(endX, y),
        strokeWidth = 12f,
        cap = StrokeCap.Round,
    )
    drawCircle(color, radius = 10f, center = Offset(endX, y))

    drawSceneLabel(negativeLabel, left, y - 42f, InteractiveYellow, 25f, Paint.Align.LEFT)
    drawSceneLabel(positiveLabel, right, y - 42f, InteractiveBlue, 25f, Paint.Align.RIGHT)
    drawSceneLabel("0", centerX, y + 54f, InteractiveWhite, 23f)
    drawSceneLabel(
        displaySignedQuantity(value, unit),
        endX.coerceIn(left + 58f, right - 58f),
        y - 26f,
        color,
        28f,
    )

    if (abs(value) > 0.0001f) {
        val arrowDirection = if (value > 0f) 1f else -1f
        drawLine(
            color = color,
            start = Offset(endX, y),
            end = Offset(endX - arrowDirection * 14f, y - 9f),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(endX, y),
            end = Offset(endX - arrowDirection * 14f, y + 9f),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
    }

    val distanceWidth = abs(endX - centerX)
    if (distanceWidth > 1f) {
        drawRect(
            color = color.copy(alpha = 0.08f),
            topLeft = Offset(minOf(centerX, endX), y + 18f),
            size = Size(distanceWidth, 32f),
        )
        drawSceneLabel(
            "离基准 ${displayQuantity(abs(value))}$unit",
            (centerX + endX) / 2f,
            y + 41f,
            InteractiveMuted,
            20f,
        )
    }
}

private fun DrawScope.drawSceneLabel(
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
