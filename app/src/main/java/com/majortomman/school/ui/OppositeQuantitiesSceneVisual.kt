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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    OppositeQuantityScene("deviation", "偏差", "g", "超过", "不足", 100f, 5f, -30f),
)

/**
 * School 自有的“相反意义的量”交互场景。
 *
 * 教材文字负责给出正数和负数的定义；这里让学习者改变同一个量，观察它相对 0
 * 这个基准向两个相反方向变化。可视化不复刻教材图片，也不依赖 PDF 裁剪。
 */
@Composable
internal fun OppositeQuantitiesSceneVisual(params: Map<String, String> = emptyMap()) {
    val requestedScene = oppositeQuantityScenes.firstOrNull { it.id == params["scene"] }
        ?: oppositeQuantityScenes.first()
    var selectedId by rememberSaveable { mutableStateOf(requestedScene.id) }
    var value by rememberSaveable { mutableFloatStateOf(requestedScene.initialValue) }
    val scene = oppositeQuantityScenes.firstOrNull { it.id == selectedId } ?: requestedScene
    val animatedValue by animateFloatAsState(targetValue = value, label = "opposite-quantity-value")
    val isZero = abs(animatedValue) < 0.0001f
    val signedValue = displaySignedQuantity(animatedValue, scene.unit)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            oppositeQuantityScenes.forEach { option ->
                val selected = option.id == scene.id
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("以 0 为基准", color = InteractiveMuted, fontSize = 12.sp)
            Text(
                signedValue,
                color = valueColor(animatedValue),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (scene.id == "temperature") {
                drawTemperatureScene(animatedValue)
            } else {
                drawHorizontalOppositeScene(
                    value = animatedValue,
                    bound = scene.bound,
                    negativeLabel = scene.negativeMeaning,
                    positiveLabel = scene.positiveMeaning,
                    unit = scene.unit,
                )
            }
        }

        Slider(
            value = value.coerceIn(-scene.bound, scene.bound),
            onValueChange = { raw -> value = snapToStep(raw, scene.step) },
            valueRange = -scene.bound..scene.bound,
            steps = ((scene.bound * 2f / scene.step).roundToInt() - 1).coerceAtLeast(0),
        )
        Text(
            text = when {
                !isZero && animatedValue > 0f -> "+ 表示相对基准向“${scene.positiveMeaning}”的方向变化。"
                !isZero && animatedValue < 0f -> "− 表示相对基准向“${scene.negativeMeaning}”的方向变化。"
                else -> "0 是正、负两个方向共同的基准，不表示任何一边。"
            },
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite.copy(alpha = 0.82f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
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
        String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
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
    val top = 22f
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
    drawSceneLabel("0℃ 基准", centerX - 110f, zeroY + 7f, InteractiveWhite, 23f, Paint.Align.RIGHT)
    val meaning = when {
        value > 0f -> "零上 ${displayQuantity(abs(value))}℃"
        value < 0f -> "零下 ${displayQuantity(abs(value))}℃"
        else -> "0℃"
    }
    drawSceneLabel(meaning, centerX, 32f, color, 27f)
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
    drawSceneLabel(displaySignedQuantity(value, unit), endX, y - 26f, color, 28f)

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
