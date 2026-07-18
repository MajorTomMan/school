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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.course.AbsoluteValueModel
import com.majortomman.school.learning.course.SignedMovementDirection
import com.majortomman.school.learning.course.SignedMovementModel
import kotlin.math.roundToInt

@Composable
internal fun SignedMovementNumberLineVisual() {
    var start by rememberSaveable { mutableStateOf(-3f) }
    var delta by rememberSaveable { mutableStateOf(-2f) }
    val model = SignedMovementModel(start, delta)
    val directionText = when (model.direction) {
        SignedMovementDirection.LEFT -> "向左"
        SignedMovementDirection.RIGHT -> "向右"
        SignedMovementDirection.STATIONARY -> "不移动"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("起点 ${displayNumber(start)}", color = InteractiveBlue, fontSize = 12.sp)
            Text("移动 ${displaySigned(delta)}（$directionText）", color = InteractiveYellow, fontSize = 12.sp)
            Text("终点 ${displayNumber(model.end)}", color = InteractiveWhite, fontSize = 12.sp)
        }
        Slider(
            value = start,
            onValueChange = { start = it.roundToInt().toFloat() },
            valueRange = -6f..6f,
            steps = 11,
        )
        Slider(
            value = delta,
            onValueChange = { delta = it.roundToInt().toFloat() },
            valueRange = -6f..6f,
            steps = 11,
        )
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val axis = drawSymmetricAxis(model.symmetricAxisBound)
            val startX = axis.xFor(model.start)
            val endX = axis.xFor(model.end)
            val arrowY = axis.centerY - 22f

            drawLine(
                color = InteractiveYellow,
                start = Offset(startX, arrowY),
                end = Offset(endX, arrowY),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
            if (model.direction != SignedMovementDirection.STATIONARY) {
                val sign = if (model.direction == SignedMovementDirection.RIGHT) 1f else -1f
                drawLine(
                    color = InteractiveYellow,
                    start = Offset(endX, arrowY),
                    end = Offset(endX - sign * 13f, arrowY - 9f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = InteractiveYellow,
                    start = Offset(endX, arrowY),
                    end = Offset(endX - sign * 13f, arrowY + 9f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }

            drawCircle(InteractiveBlue, radius = 8f, center = Offset(startX, axis.centerY))
            drawCircle(InteractiveYellow, radius = 8f, center = Offset(endX, axis.centerY))
            drawCanvasLabel("起点 ${displayNumber(model.start)}", startX, axis.centerY - 42f, InteractiveBlue)
            drawCanvasLabel("终点 ${displayNumber(model.end)}", endX, axis.centerY + 58f, InteractiveYellow)
        }
        Text(
            text = "${displayNumber(model.start)} + ${additionTerm(model.delta)} = ${displayNumber(model.end)}",
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AbsoluteValueNumberLineVisual() {
    var value by rememberSaveable { mutableStateOf(-3f) }
    val model = AbsoluteValueModel(value)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("x = ${displayNumber(value)}", color = InteractiveBlue, fontSize = 13.sp)
            Text("|x| = ${displayNumber(model.absoluteValue)}", color = InteractiveYellow, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = { value = (it * 2f).roundToInt() / 2f },
            valueRange = -5f..5f,
        )
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val axis = drawSymmetricAxis(5)
            val zeroX = axis.xFor(0f)
            val valueX = axis.xFor(model.value)
            val absoluteX = axis.xFor(model.absoluteValue)

            drawLine(
                color = InteractiveBlue.copy(alpha = 0.72f),
                start = Offset(zeroX, axis.centerY - 20f),
                end = Offset(valueX, axis.centerY - 20f),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = InteractiveYellow.copy(alpha = 0.72f),
                start = Offset(zeroX, axis.centerY + 20f),
                end = Offset(absoluteX, axis.centerY + 20f),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )

            when {
                model.value < 0f -> {
                    drawCircle(InteractiveBlue, radius = 9f, center = Offset(valueX, axis.centerY))
                    drawCircle(InteractiveYellow, radius = 9f, center = Offset(absoluteX, axis.centerY))
                    drawCanvasLabel("x = ${displayNumber(model.value)}", valueX, axis.centerY - 48f, InteractiveBlue)
                    drawCanvasLabel(
                        "|x| = ${displayNumber(model.absoluteValue)}",
                        absoluteX,
                        axis.centerY + 62f,
                        InteractiveYellow,
                    )
                }
                model.value > 0f -> {
                    drawLine(
                        color = InteractiveWhite.copy(alpha = 0.5f),
                        start = Offset(valueX, axis.centerY - 12f),
                        end = Offset(valueX, axis.centerY + 12f),
                        strokeWidth = 2f,
                    )
                    drawCircle(InteractiveBlue, radius = 8f, center = Offset(valueX, axis.centerY - 7f))
                    drawCircle(InteractiveYellow, radius = 8f, center = Offset(valueX, axis.centerY + 7f))
                    drawCanvasLabel("x = ${displayNumber(model.value)}", valueX, axis.centerY - 48f, InteractiveBlue)
                    drawCanvasLabel(
                        "|x| = ${displayNumber(model.absoluteValue)}",
                        absoluteX,
                        axis.centerY + 62f,
                        InteractiveYellow,
                    )
                }
                else -> {
                    drawCircle(InteractiveWhite, radius = 9f, center = Offset(zeroX, axis.centerY))
                    drawCanvasLabel("x = |x| = 0", zeroX, axis.centerY - 45f, InteractiveWhite)
                }
            }
        }
        Text(
            text = if (model.value < 0f) {
                "x在负半轴，|x|在正半轴，两个点到0的距离相等。"
            } else {
                "x与|x|位于同一坐标。"
            },
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private data class RationalClassificationChoice(
    val id: String,
    val example: String,
    val cellLabel: String,
    val signLabel: String,
    val formLabel: String,
    val color: Color,
)

private val rationalClassificationChoices = listOf(
    RationalClassificationChoice("positive_integer", "3", "正整数", "正有理数", "整数", InteractiveBlue),
    RationalClassificationChoice("positive_fraction", "1/2", "正分数", "正有理数", "分数", InteractiveBlue),
    RationalClassificationChoice("zero", "0", "0", "0", "整数", InteractiveWhite),
    RationalClassificationChoice("negative_integer", "−4", "负整数", "负有理数", "整数", InteractiveYellow),
    RationalClassificationChoice("negative_fraction", "−2/3", "负分数", "负有理数", "分数", InteractiveYellow),
)

@Composable
internal fun ClassificationVisual() {
    var selectedId by rememberSaveable { mutableStateOf("negative_fraction") }
    val selected = rationalClassificationChoices.first { it.id == selectedId }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "有理数的双维分类",
                color = InteractiveWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("点选交叉格", color = InteractiveMuted, fontSize = 10.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "符号 ↓",
                modifier = Modifier.width(48.dp),
                color = InteractiveMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
            ClassificationMatrixHeader("整数", Modifier.weight(1f))
            ClassificationMatrixHeader("分数", Modifier.weight(1f))
        }

        ClassificationMatrixRow(
            sign = "正",
            signColor = InteractiveBlue,
            integerChoice = rationalClassificationChoices[0],
            fractionChoice = rationalClassificationChoices[1],
            selectedId = selectedId,
            onSelect = { selectedId = it },
        )
        ClassificationMatrixRow(
            sign = "0",
            signColor = InteractiveWhite,
            integerChoice = rationalClassificationChoices[2],
            fractionChoice = null,
            selectedId = selectedId,
            onSelect = { selectedId = it },
        )
        ClassificationMatrixRow(
            sign = "负",
            signColor = InteractiveYellow,
            integerChoice = rationalClassificationChoices[3],
            fractionChoice = rationalClassificationChoices[4],
            selectedId = selectedId,
            onSelect = { selectedId = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "例 ${selected.example}",
                modifier = Modifier.width(58.dp),
                color = selected.color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            ClassificationPath(
                prefix = "按符号",
                value = selected.signLabel,
                color = selected.color,
                modifier = Modifier.weight(1f),
            )
            ClassificationPath(
                prefix = "按形式",
                value = selected.formLabel,
                color = selected.color,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ClassificationMatrixHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = "按表示形式 → $label",
        modifier = modifier,
        color = InteractiveMuted,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ClassificationMatrixRow(
    sign: String,
    signColor: Color,
    integerChoice: RationalClassificationChoice?,
    fractionChoice: RationalClassificationChoice?,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(34.dp)
                .border(1.dp, signColor.copy(alpha = 0.58f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(sign, color = signColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        ClassificationMatrixCell(
            choice = integerChoice,
            selected = integerChoice?.id == selectedId,
            modifier = Modifier.weight(1f),
            onSelect = onSelect,
        )
        ClassificationMatrixCell(
            choice = fractionChoice,
            selected = fractionChoice?.id == selectedId,
            modifier = Modifier.weight(1f),
            onSelect = onSelect,
        )
    }
}

@Composable
private fun ClassificationMatrixCell(
    choice: RationalClassificationChoice?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val color = choice?.color ?: InteractiveMuted
    Box(
        modifier = modifier
            .height(34.dp)
            .background(
                if (selected) color.copy(alpha = 0.18f) else InteractivePanel.copy(alpha = 0.2f),
                shape,
            )
            .border(if (selected) 1.5.dp else 1.dp, color.copy(alpha = if (selected) 0.95f else 0.28f), shape)
            .clickable(enabled = choice != null) { choice?.let { onSelect(it.id) } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = choice?.cellLabel ?: "—",
            color = if (choice == null) InteractiveMuted.copy(alpha = 0.35f) else color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ClassificationPath(
    prefix: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(prefix, color = InteractiveMuted, fontSize = 9.sp)
        Text(value, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CloudNode(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, color.copy(alpha = 0.65f)).padding(8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

internal enum class NumberLineMode { VALUE, OPPOSITE }

@Composable
internal fun AdjustableNumberLine(mode: NumberLineMode) {
    var value by rememberSaveable { mutableStateOf(-2f) }
    val points = when (mode) {
        NumberLineMode.VALUE -> listOf(
            LabeledPoint(value, InteractiveYellow, "x=${displayNumber(value)}", true),
        )
        NumberLineMode.OPPOSITE -> listOf(
            LabeledPoint(value, InteractiveBlue, displayNumber(value), true),
            LabeledPoint(-value, InteractiveYellow, displayNumber(-value), false),
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("x", color = InteractiveMuted, fontSize = 12.sp)
            Text(
                if (mode == NumberLineMode.OPPOSITE) {
                    "${displayNumber(value)} 与 ${displayNumber(-value)}"
                } else {
                    displayNumber(value)
                },
                color = InteractiveYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(value = value, onValueChange = { value = (it * 2f).roundToInt() / 2f }, valueRange = -5f..5f)
        Canvas(Modifier.fillMaxWidth().weight(1f)) { drawLabeledNumberLine(points, 5) }
    }
}

@Composable
internal fun ComparisonVisual() {
    var left by rememberSaveable { mutableStateOf(-3f) }
    var right by rememberSaveable { mutableStateOf(2f) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Slider(value = left, onValueChange = { left = it.roundToInt().toFloat() }, valueRange = -5f..5f)
        Slider(value = right, onValueChange = { right = it.roundToInt().toFloat() }, valueRange = -5f..5f)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            drawLabeledNumberLine(
                listOf(
                    LabeledPoint(left, InteractiveYellow, displayNumber(left), true),
                    LabeledPoint(right, InteractiveBlue, displayNumber(right), false),
                ),
                5,
            )
        }
        val relation = when {
            left < right -> "<"
            left > right -> ">"
            else -> "="
        }
        Text(
            "${displayNumber(left)} $relation ${displayNumber(right)}",
            color = InteractiveWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SignedUnitVisual() {
    var positive by rememberSaveable { mutableStateOf(2f) }
    var negative by rememberSaveable { mutableStateOf(3f) }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Slider(value = positive, onValueChange = { positive = it.roundToInt().toFloat() }, valueRange = 0f..6f, steps = 5)
        Slider(value = negative, onValueChange = { negative = it.roundToInt().toFloat() }, valueRange = 0f..6f, steps = 5)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(positive.roundToInt()) { Text("＋", color = InteractiveBlue, fontSize = 24.sp) }
            Spacer(Modifier.width(14.dp))
            repeat(negative.roundToInt()) { Text("−", color = InteractiveYellow, fontSize = 24.sp) }
        }
        Text(
            "结果：${positive.roundToInt() - negative.roundToInt()}",
            color = InteractiveWhite,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun FormulaProcessVisual(formula: String?) {
    Text(
        formula.orEmpty(),
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        color = InteractiveYellow,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun SignRuleVisual() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        listOf("＋ × ＋ = ＋", "＋ × − = −", "− × ＋ = −", "− × − = ＋").forEach { rule ->
            Text(
                rule,
                color = if (rule.endsWith("＋")) InteractiveBlue else InteractiveYellow,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun PowerVisual() {
    var base by rememberSaveable { mutableStateOf(-2f) }
    var exponent by rememberSaveable { mutableStateOf(3f) }
    val baseValue = base.roundToInt()
    val exponentValue = exponent.roundToInt().coerceIn(1, 6)
    val result = (1..exponentValue).fold(1) { current, _ -> current * baseValue }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Slider(value = base, onValueChange = { base = it.roundToInt().toFloat() }, valueRange = -4f..4f, steps = 7)
        Slider(value = exponent, onValueChange = { exponent = it.roundToInt().toFloat() }, valueRange = 1f..6f, steps = 4)
        Text(
            "$baseValue ^ $exponentValue = $result",
            color = InteractiveYellow,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class LabeledPoint(
    val value: Float,
    val color: Color,
    val label: String,
    val labelAbove: Boolean,
)

private data class NumberLineGeometry(
    val left: Float,
    val right: Float,
    val centerY: Float,
    val bound: Int,
) {
    fun xFor(value: Float): Float =
        left + (value.coerceIn(-bound.toFloat(), bound.toFloat()) + bound) / (bound * 2f) * (right - left)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSymmetricAxis(bound: Int): NumberLineGeometry {
    val safeBound = bound.coerceAtLeast(1)
    val left = 28f
    val right = size.width - 28f
    val centerY = size.height * 0.54f
    val geometry = NumberLineGeometry(left, right, centerY, safeBound)

    drawLine(
        InteractiveWhite.copy(alpha = 0.78f),
        Offset(left, centerY),
        Offset(right, centerY),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
    drawLine(
        InteractiveWhite.copy(alpha = 0.78f),
        Offset(right, centerY),
        Offset(right - 11f, centerY - 7f),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
    drawLine(
        InteractiveWhite.copy(alpha = 0.78f),
        Offset(right, centerY),
        Offset(right - 11f, centerY + 7f),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )

    for (tick in -safeBound..safeBound) {
        val x = geometry.xFor(tick.toFloat())
        drawLine(
            InteractiveWhite.copy(alpha = if (tick == 0) 0.85f else 0.42f),
            Offset(x, centerY - if (tick == 0) 11f else 7f),
            Offset(x, centerY + if (tick == 0) 11f else 7f),
            strokeWidth = if (tick == 0) 3f else 2f,
        )
        if (tick == 0 || tick == -safeBound || tick == safeBound || safeBound <= 6) {
            drawCanvasLabel(tick.toString(), x, centerY + 32f, InteractiveMuted, 20f)
        }
    }
    return geometry
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabeledNumberLine(
    points: List<LabeledPoint>,
    bound: Int,
) {
    val axis = drawSymmetricAxis(bound)
    points.forEach { point ->
        val x = axis.xFor(point.value)
        drawCircle(point.color, radius = 8f, center = Offset(x, axis.centerY))
        drawCanvasLabel(
            point.label,
            x,
            if (point.labelAbove) axis.centerY - 34f else axis.centerY + 56f,
            point.color,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCanvasLabel(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    textSize: Float = 24f,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}

private fun displayNumber(value: Float): String =
    if (value == value.roundToInt().toFloat()) value.roundToInt().toString() else "%.1f".format(value)

private fun displaySigned(value: Float): String = when {
    value > 0f -> "+${displayNumber(value)}"
    else -> displayNumber(value)
}

private fun additionTerm(value: Float): String =
    if (value < 0f) "(${displayNumber(value)})" else displayNumber(value)
