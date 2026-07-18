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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.course.ChemistryCourseCategory
import com.majortomman.school.learning.course.ChemistryCourseContentFactory
import com.majortomman.school.learning.course.CourseParameterKind
import com.majortomman.school.learning.course.CourseVisualizationKind
import com.majortomman.school.learning.science.chemistry.ChemistryVerificationMode
import com.majortomman.school.learning.science.chemistry.ChemistryVerificationStatus
import com.majortomman.school.learning.science.chemistry.ChemistryVerifier
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun ChemistryCourseWorkbench(spec: InteractiveLessonSpec) {
    val category = remember(spec.title) { ChemistryCourseContentFactory.classify(spec.title) }
    val visualization = spec.enrichment.visualization
    val parameterValues = remember(spec.title) {
        mutableStateMapOf<String, String>().apply {
            visualization?.parameters.orEmpty().forEach { put(it.id, it.defaultValue) }
        }
    }

    SectionTitle(visualization?.title ?: "化学可视化", InteractiveBlue)
    Spacer(Modifier.height(12.dp))
    Text(
        visualization?.description ?: "在宏观、微观和符号三个层次观察本课内容。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(18.dp))
    visualization?.parameters.orEmpty().forEach { parameter ->
        if (parameter.kind == CourseParameterKind.NUMBER || parameter.kind == CourseParameterKind.INTEGER) {
            val minimum = parameter.minimum ?: 0.0
            val maximum = parameter.maximum ?: 10.0
            val numeric = parameterValues[parameter.id]?.toDoubleOrNull()?.coerceIn(minimum, maximum) ?: minimum
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(parameter.label, color = InteractiveMuted, fontSize = 13.sp)
                Text("${formatChemistry(numeric)} ${parameter.unit}", color = InteractiveBlue, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = numeric.toFloat(),
                onValueChange = { changed ->
                    val step = parameter.step ?: 1.0
                    val snapped = kotlin.math.round(changed / step) * step
                    parameterValues[parameter.id] = if (parameter.kind == CourseParameterKind.INTEGER) snapped.toInt().toString() else formatChemistry(snapped)
                },
                valueRange = minimum.toFloat()..maximum.toFloat(),
            )
        }
    }
    ChemistryVisualization(
        kind = visualization?.kind ?: CourseVisualizationKind.PROCESS,
        values = parameterValues.mapValues { it.value.toDoubleOrNull() ?: 0.0 },
    )

    Spacer(Modifier.height(34.dp))
    SectionTitle("化学结构与守恒验证", InteractiveYellow)
    Spacer(Modifier.height(12.dp))
    val modes = modesFor(category)
    var modeName by rememberSaveable(spec.title) { mutableStateOf(modes.first().name) }
    val mode = modes.firstOrNull { it.name == modeName } ?: modes.first()
    var primary by rememberSaveable(spec.title, mode.name) { mutableStateOf(defaultPrimary(mode)) }
    var secondary by rememberSaveable(spec.title, mode.name) { mutableStateOf(defaultSecondary(mode)) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { option ->
            Text(
                modeLabel(option),
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        modeName = option.name
                        primary = defaultPrimary(option)
                        secondary = defaultSecondary(option)
                    }
                    .padding(vertical = 10.dp),
                color = if (option == mode) InteractiveYellow else InteractiveMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (option == mode) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        if (mode == ChemistryVerificationMode.EQUATION_BALANCE || mode == ChemistryVerificationMode.EQUATION_CHECK) {
            "必须同时给出反应物和生成物；系统不会根据反应物猜测未知产物。"
        } else if (mode == ChemistryVerificationMode.ORGANIC_STRUCTURE || mode == ChemistryVerificationMode.ORGANIC_ISOMER) {
            "有机输入使用受限结构简式语法，例如 CCO、COC、CC(=O)O、c1ccccc1。"
        } else {
            "支持元素、下标、多层括号、结晶水和离子电荷。"
        },
        color = InteractiveMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(12.dp))
    ChemistryTextInput("主要输入", primary) { primary = it.take(512) }
    if (mode == ChemistryVerificationMode.ORGANIC_ISOMER) {
        Spacer(Modifier.height(12.dp))
        ChemistryTextInput("第二个结构", secondary) { secondary = it.take(256) }
    }
    val result = remember(mode, primary, secondary) { ChemistryVerifier.verify(mode, primary, secondary) }
    Spacer(Modifier.height(18.dp))
    ChemistryResultBlock(result)
}

@Composable
private fun ChemistryTextInput(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = InteractiveMuted, fontSize = 12.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            textStyle = TextStyle(color = InteractiveWhite, fontSize = 20.sp, lineHeight = 28.sp),
            cursorBrush = SolidColor(InteractiveYellow),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
    }
}

@Composable
private fun ChemistryResultBlock(result: com.majortomman.school.learning.science.chemistry.ChemistryVerificationResult) {
    val color = when (result.status) {
        ChemistryVerificationStatus.VALID,
        ChemistryVerificationStatus.BALANCED,
        ChemistryVerificationStatus.BALANCED_RESULT,
        -> InteractiveGreen
        ChemistryVerificationStatus.UNBALANCED -> InteractiveYellow
        else -> InteractiveRed
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(color))
        Text(result.title, color = color, fontWeight = FontWeight.Bold)
        result.normalized?.let { Text(it, color = InteractiveWhite, fontSize = 19.sp, lineHeight = 27.sp) }
        result.rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = InteractiveMuted, fontSize = 13.sp)
                Text(value, color = InteractiveWhite, fontSize = 13.sp, textAlign = TextAlign.End)
            }
        }
        Text(result.message, color = InteractiveWhite.copy(alpha = 0.78f), fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun ChemistryVisualization(kind: CourseVisualizationKind, values: Map<String, Double>) {
    Canvas(Modifier.fillMaxWidth().height(310.dp).background(InteractivePanel.copy(alpha = 0.25f))) {
        when (kind) {
            CourseVisualizationKind.PARTICLE_MODEL -> drawChemicalParticles(values)
            CourseVisualizationKind.CHEMICAL_EQUATION -> drawChemicalRearrangement(values)
            CourseVisualizationKind.MOLECULE -> drawMoleculeSketch(values)
            CourseVisualizationKind.DATA_TABLE -> drawChemicalBars(values)
            else -> drawChemicalProcess(values)
        }
    }
}

private fun DrawScope.drawChemicalParticles(values: Map<String, Double>) {
    val count = ((values["count"] ?: values["amount"] ?: 12.0).toInt()).coerceIn(4, 32)
    repeat(count) { index ->
        val columns = 8
        val x = size.width * ((index % columns) + 1f) / (columns + 1f)
        val y = size.height * ((index / columns) + 1f) / ((count + columns - 1) / columns + 1f)
        drawCircle(if (index % 3 == 0) InteractiveRed else if (index % 2 == 0) InteractiveBlue else InteractiveYellow, 10.dp.toPx(), Offset(x, y))
    }
}

private fun DrawScope.drawChemicalRearrangement(values: Map<String, Double>) {
    val mid = size.width / 2f
    repeat(3) { index ->
        val y = size.height * (index + 1f) / 4f
        drawCircle(InteractiveBlue, 11.dp.toPx(), Offset(size.width * 0.18f, y))
        drawCircle(InteractiveBlue, 11.dp.toPx(), Offset(size.width * 0.25f, y))
        drawCircle(InteractiveRed, 13.dp.toPx(), Offset(size.width * 0.34f, y))
    }
    drawLine(InteractiveYellow, Offset(mid - 30.dp.toPx(), size.height / 2f), Offset(mid + 30.dp.toPx(), size.height / 2f), 4.dp.toPx(), StrokeCap.Round)
    repeat(3) { index ->
        val y = size.height * (index + 1f) / 4f
        val center = Offset(size.width * 0.73f, y)
        drawLine(InteractiveWhite.copy(alpha = 0.6f), center, Offset(center.x - 22.dp.toPx(), center.y + 14.dp.toPx()), 3.dp.toPx())
        drawLine(InteractiveWhite.copy(alpha = 0.6f), center, Offset(center.x + 22.dp.toPx(), center.y + 14.dp.toPx()), 3.dp.toPx())
        drawCircle(InteractiveRed, 13.dp.toPx(), center)
        drawCircle(InteractiveBlue, 10.dp.toPx(), Offset(center.x - 22.dp.toPx(), center.y + 14.dp.toPx()))
        drawCircle(InteractiveBlue, 10.dp.toPx(), Offset(center.x + 22.dp.toPx(), center.y + 14.dp.toPx()))
    }
}

private fun DrawScope.drawMoleculeSketch(values: Map<String, Double>) {
    val atoms = ((values["layout"] ?: 60.0) / 10.0).toInt().coerceIn(4, 12)
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = minOf(size.width, size.height) * 0.3f
    val points = (0 until atoms).map { index ->
        val angle = 2.0 * Math.PI * index / atoms
        Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    }
    points.indices.forEach { index ->
        drawLine(InteractiveWhite.copy(alpha = 0.55f), points[index], points[(index + 1) % points.size], 3.dp.toPx())
    }
    points.forEachIndexed { index, point -> drawCircle(if (index % 3 == 0) InteractiveRed else InteractiveBlue, 12.dp.toPx(), point) }
}

private fun DrawScope.drawChemicalBars(values: Map<String, Double>) {
    val entries = values.entries.ifEmpty { setOf(mapOf("mass" to 10.0).entries.first()) }.take(5)
    val max = entries.maxOf { abs(it.value) }.coerceAtLeast(1.0)
    entries.forEachIndexed { index, entry ->
        val width = size.width / entries.size
        val height = (abs(entry.value) / max).toFloat() * size.height * 0.68f
        drawRect(if (index % 2 == 0) InteractiveGreen else InteractiveBlue, Offset(index * width + width * 0.18f, size.height * 0.82f - height), androidx.compose.ui.geometry.Size(width * 0.64f, height))
    }
}

private fun DrawScope.drawChemicalProcess(values: Map<String, Double>) {
    val count = (values.size + 4).coerceIn(4, 8)
    repeat(count) { index ->
        val x = size.width * (index + 1f) / (count + 1f)
        val y = size.height * (0.5f + 0.12f * sin(index.toDouble()).toFloat())
        if (index > 0) drawLine(InteractiveLine, Offset(size.width * index / (count + 1f), size.height * (0.5f + 0.12f * sin(index - 1.0).toFloat())), Offset(x, y), 3.dp.toPx())
        drawCircle(if (index == count - 1) InteractiveYellow else InteractiveGreen, 11.dp.toPx(), Offset(x, y))
    }
    val paint = Paint().apply { color = InteractiveMuted.toArgb(); textSize = 12.sp.toPx(); textAlign = Paint.Align.CENTER }
    drawContext.canvas.nativeCanvas.drawText("宏观现象 → 微观结构 → 符号表达", size.width / 2f, size.height - 18.dp.toPx(), paint)
}

private fun modesFor(category: ChemistryCourseCategory): List<ChemistryVerificationMode> = when (category) {
    ChemistryCourseCategory.ORGANIC -> listOf(ChemistryVerificationMode.ORGANIC_STRUCTURE, ChemistryVerificationMode.ORGANIC_ISOMER)
    ChemistryCourseCategory.FORMULA,
    ChemistryCourseCategory.ELEMENT,
    ChemistryCourseCategory.PARTICLE,
    -> listOf(ChemistryVerificationMode.FORMULA, ChemistryVerificationMode.EQUATION_CHECK)
    else -> listOf(ChemistryVerificationMode.FORMULA, ChemistryVerificationMode.EQUATION_CHECK, ChemistryVerificationMode.EQUATION_BALANCE)
}

private fun modeLabel(mode: ChemistryVerificationMode): String = when (mode) {
    ChemistryVerificationMode.FORMULA -> "化学式"
    ChemistryVerificationMode.EQUATION_CHECK -> "守恒检查"
    ChemistryVerificationMode.EQUATION_BALANCE -> "自动配平"
    ChemistryVerificationMode.ORGANIC_STRUCTURE -> "有机结构"
    ChemistryVerificationMode.ORGANIC_ISOMER -> "异构比较"
}

private fun defaultPrimary(mode: ChemistryVerificationMode): String = when (mode) {
    ChemistryVerificationMode.FORMULA -> "Al2(SO4)3"
    ChemistryVerificationMode.EQUATION_CHECK -> "2H2+O2->2H2O"
    ChemistryVerificationMode.EQUATION_BALANCE -> "Fe+O2->Fe2O3"
    ChemistryVerificationMode.ORGANIC_STRUCTURE -> "CCO"
    ChemistryVerificationMode.ORGANIC_ISOMER -> "CCO"
}

private fun defaultSecondary(mode: ChemistryVerificationMode): String = if (mode == ChemistryVerificationMode.ORGANIC_ISOMER) "COC" else ""

private fun formatChemistry(value: Double): String {
    val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
    return if (abs(rounded - rounded.toLong()) < 1e-9) rounded.toLong().toString() else rounded.toString().trimEnd('0').trimEnd('.')
}
