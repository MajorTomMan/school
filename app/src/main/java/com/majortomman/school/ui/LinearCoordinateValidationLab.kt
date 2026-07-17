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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private data class RelationVariable(
    val symbol: String,
    val label: String,
    val range: ClosedFloatingPointRange<Double>,
)

private data class RelationDefinition(
    val formula: String,
    val variables: List<RelationVariable>,
    val solve: (target: String, known: Map<String, Double>) -> Double?,
) {
    fun requiredInputs(target: String): List<RelationVariable> = variables.filterNot { it.symbol == target }
}

private data class LinearRelationExample(
    val label: String,
    val coefficient: Double,
    val constant: Double,
    val xRange: ClosedFloatingPointRange<Double>,
    val yRange: ClosedFloatingPointRange<Double>,
    val note: String,
) {
    val formula: String = buildString {
        append("y = ")
        when {
            coefficient == 1.0 -> append("x")
            coefficient == -1.0 -> append("-x")
            else -> append(formatEquationNumber(coefficient)).append("x")
        }
        when {
            constant > 0.0 -> append(" + ").append(formatEquationNumber(constant))
            constant < 0.0 -> append(" - ").append(formatEquationNumber(abs(constant)))
        }
    }

    val relation = RelationDefinition(
        formula = formula,
        variables = listOf(
            RelationVariable("x", "x", xRange),
            RelationVariable("y", "y", yRange),
        ),
        solve = { target, known ->
            when (target) {
                "y" -> known["x"]?.let { coefficient * it + constant }
                "x" -> known["y"]?.let { if (abs(coefficient) < 1e-9) null else (it - constant) / coefficient }
                else -> null
            }
        },
    )
}

private val linearRelationExamples = listOf(
    LinearRelationExample(
        label = "y=2x",
        coefficient = 2.0,
        constant = 0.0,
        xRange = -3.0..3.0,
        yRange = -6.0..6.0,
        note = "输入一个 x，就能得到与它对应的 y。",
    ),
    LinearRelationExample(
        label = "y=-1.5x",
        coefficient = -1.5,
        constant = 0.0,
        xRange = -4.0..4.0,
        yRange = -6.0..6.0,
        note = "也可以给出 y，再求与它对应的 x。",
    ),
    LinearRelationExample(
        label = "气温问题",
        coefficient = -6.0,
        constant = 5.0,
        xRange = 0.0..1.5,
        yRange = -4.0..6.0,
        note = "x 表示登高的千米数，y 表示气温。",
    ),
)

@Composable
internal fun LinearCoordinateValidationLab(lessonId: String) {
    var exampleIndex by rememberSaveable(lessonId, "relation-example") { mutableIntStateOf(0) }
    var targetSymbol by rememberSaveable(lessonId, "relation-target") { mutableStateOf("y") }
    var inputTexts by remember(exampleIndex, targetSymbol) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val example = linearRelationExamples[exampleIndex.coerceIn(linearRelationExamples.indices)]
    val relation = example.relation
    val requiredInputs = relation.requiredInputs(targetSymbol)

    LaunchedEffect(exampleIndex, targetSymbol) {
        inputTexts = requiredInputs.associate { variable ->
            variable.symbol to when (variable.symbol) {
                "x" -> if (example.xRange.start >= 0.0) "0.5" else "1"
                "y" -> formatEquationNumber(example.coefficient + example.constant)
                else -> ""
            }
        }
    }

    val knownValues = requiredInputs.mapNotNull { variable ->
        inputTexts[variable.symbol]?.toDoubleOrNull()?.let { variable.symbol to it }
    }.toMap()
    val allInputsReady = knownValues.size == requiredInputs.size
    val result = if (allInputsReady) relation.solve(targetSymbol, knownValues) else null
    val pointX = when (targetSymbol) {
        "x" -> result
        else -> knownValues["x"]
    }
    val pointY = when (targetSymbol) {
        "y" -> result
        else -> knownValues["y"]
    }

    SectionTitle("根据一个量求另一个量", InteractivePurple)
    Spacer(Modifier.height(12.dp))
    Text(
        "先选择要求的量。页面只显示计算时需要填写的已知量，输入后图像中的点会同步移动。",
        color = InteractiveWhite.copy(alpha = 0.75f),
        fontSize = 16.sp,
        lineHeight = 25.sp,
    )
    Spacer(Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        linearRelationExamples.forEachIndexed { index, item ->
            RelationChoice(
                label = item.label,
                selected = index == exampleIndex,
                modifier = Modifier.weight(1f),
            ) { exampleIndex = index }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(example.note, color = InteractiveMuted, fontSize = 14.sp, lineHeight = 21.sp)
    Spacer(Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RelationChoice(
            label = "已知 x，求 y",
            selected = targetSymbol == "y",
            modifier = Modifier.weight(1f),
        ) { targetSymbol = "y" }
        RelationChoice(
            label = "已知 y，求 x",
            selected = targetSymbol == "x",
            modifier = Modifier.weight(1f),
        ) { targetSymbol = "x" }
    }
    Spacer(Modifier.height(18.dp))

    RelationGraph(
        example = example,
        pointX = pointX,
        pointY = pointY,
    )
    Spacer(Modifier.height(18.dp))

    requiredInputs.forEachIndexed { index, variable ->
        RelationNumberInput(
            label = "输入 ${variable.label}",
            value = inputTexts[variable.symbol].orEmpty(),
            color = if (variable.symbol == "x") InteractiveBlue else InteractiveYellow,
            onValueChange = { next ->
                if (isNumberDraft(next)) {
                    val number = next.toDoubleOrNull()
                    if (number == null || number in variable.range) {
                        inputTexts = inputTexts + (variable.symbol to next)
                    }
                }
            },
        )
        if (index != requiredInputs.lastIndex) Spacer(Modifier.height(12.dp))
    }

    Spacer(Modifier.height(16.dp))
    RelationResultCard(
        formula = relation.formula,
        targetSymbol = targetSymbol,
        knownValues = knownValues,
        result = result,
        allInputsReady = allInputsReady,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "当一个关系中有三个或更多量时，要求其中一个量，页面会同时显示其余所有已知量的输入框。",
        color = InteractiveMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun RelationResultCard(
    formula: String,
    targetSymbol: String,
    knownValues: Map<String, Double>,
    result: Double?,
    allInputsReady: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractivePanel, RoundedCornerShape(14.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(formula, color = InteractiveYellow, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        when {
            !allInputsReady -> Text("请先填写已知量。", color = InteractiveMuted, fontSize = 15.sp)
            result == null || !result.isFinite() -> Text("当前输入无法得到有效结果。", color = InteractiveRed, fontSize = 15.sp)
            else -> {
                val knownText = knownValues.entries.joinToString("，") { "${it.key}=${formatEquationNumber(it.value)}" }
                Text("已知 $knownText", color = InteractiveMuted, fontSize = 15.sp)
                Text(
                    "$targetSymbol=${formatEquationNumber(result)}",
                    color = InteractiveGreen,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RelationGraph(
    example: LinearRelationExample,
    pointX: Double?,
    pointY: Double?,
) {
    val xMin = example.xRange.start.toFloat()
    val xMax = example.xRange.endInclusive.toFloat()
    val yMin = example.yRange.start.toFloat()
    val yMax = example.yRange.endInclusive.toFloat()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .background(InteractivePanel, RoundedCornerShape(18.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(18.dp))
            .padding(10.dp),
    ) {
        val left = 34.dp.toPx()
        val right = size.width - 18.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 34.dp.toPx()
        fun sx(value: Float): Float = left + (value - xMin) / (xMax - xMin) * (right - left)
        fun sy(value: Float): Float = bottom - (value - yMin) / (yMax - yMin) * (bottom - top)

        repeat(9) { index ->
            val ratio = index / 8f
            val gridX = left + ratio * (right - left)
            val gridY = top + ratio * (bottom - top)
            drawLine(InteractiveLine.copy(alpha = 0.62f), Offset(gridX, top), Offset(gridX, bottom), 1.dp.toPx())
            drawLine(InteractiveLine.copy(alpha = 0.62f), Offset(left, gridY), Offset(right, gridY), 1.dp.toPx())
        }
        if (0f in xMin..xMax) {
            drawLine(InteractiveWhite.copy(alpha = 0.68f), Offset(sx(0f), top), Offset(sx(0f), bottom), 2.dp.toPx())
        }
        if (0f in yMin..yMax) {
            drawLine(InteractiveWhite.copy(alpha = 0.68f), Offset(left, sy(0f)), Offset(right, sy(0f)), 2.dp.toPx())
        }

        clipRect(left = left, top = top, right = right, bottom = bottom) {
            drawLine(
                color = InteractiveBlue,
                start = Offset(sx(xMin), sy((example.coefficient * xMin + example.constant).toFloat())),
                end = Offset(sx(xMax), sy((example.coefficient * xMax + example.constant).toFloat())),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            if (pointX != null && pointY != null && pointX.isFinite() && pointY.isFinite()) {
                val x = pointX.toFloat()
                val y = pointY.toFloat()
                if (x in xMin..xMax && y in yMin..yMax) {
                    val center = Offset(sx(x), sy(y))
                    drawLine(
                        InteractiveYellow.copy(alpha = 0.4f),
                        Offset(sx(x), if (0f in yMin..yMax) sy(0f) else bottom),
                        center,
                        2.dp.toPx(),
                    )
                    drawLine(
                        InteractiveYellow.copy(alpha = 0.4f),
                        Offset(if (0f in xMin..xMax) sx(0f) else left, sy(y)),
                        center,
                        2.dp.toPx(),
                    )
                    drawCircle(InteractiveGreen, 8.dp.toPx(), center)
                    drawCircle(InteractiveWhite, 3.dp.toPx(), center)
                }
            }
        }

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 12.sp.toPx()
            color = InteractiveMuted.toArgb()
        }
        drawContext.canvas.nativeCanvas.drawText(example.formula, left, 16.dp.toPx(), paint)
        paint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText("x", right, bottom + 25.dp.toPx(), paint)
        paint.textAlign = Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText("y", left - 18.dp.toPx(), top + 4.dp.toPx(), paint)
    }
}

@Composable
private fun RelationNumberInput(
    label: String,
    value: String,
    color: Color,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractivePanel, RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(label, color = InteractiveMuted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = InteractiveWhite, fontSize = 25.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(color),
            singleLine = true,
        )
    }
}

@Composable
private fun RelationChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(
                if (selected) InteractivePurple.copy(alpha = 0.17f) else InteractivePanel,
                RoundedCornerShape(12.dp),
            )
            .border(1.dp, if (selected) InteractivePurple else InteractiveLine, RoundedCornerShape(12.dp))
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

private fun isNumberDraft(value: String): Boolean =
    value.length <= 12 && value.matches(Regex("-?\\d*(\\.\\d*)?"))
