package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.course.CourseBlock
import com.majortomman.school.learning.course.CourseConclusion
import com.majortomman.school.learning.course.CourseExample
import com.majortomman.school.learning.course.CourseExercise
import com.majortomman.school.learning.course.CourseFormula
import com.majortomman.school.learning.course.CourseHeading
import com.majortomman.school.learning.course.CourseList
import com.majortomman.school.learning.course.CoursePage
import com.majortomman.school.learning.course.CourseScene
import com.majortomman.school.learning.course.CourseSceneBlock
import com.majortomman.school.learning.course.CourseSceneData
import com.majortomman.school.learning.course.CourseSceneTemplate
import com.majortomman.school.learning.course.CourseText
import com.majortomman.school.learning.course.CourseTextStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun CloudCourseOrderedBlocks(
    page: CoursePage,
    compact: Boolean,
) {
    page.blocks.forEachIndexed { index, block ->
        if (index > 0) Spacer(Modifier.height(blockSpacing(block)))
        CloudCourseBlock(block, page, compact)
    }
}

private fun blockSpacing(block: CourseBlock) = when (block) {
    is CourseHeading -> 22.dp
    is CourseSceneBlock -> 18.dp
    is CourseConclusion -> 20.dp
    else -> 12.dp
}

@Composable
private fun CloudCourseBlock(
    block: CourseBlock,
    page: CoursePage,
    compact: Boolean,
) {
    when (block) {
        is CourseHeading -> TextbookSubheading(block.text)
        is CourseText -> TextbookParagraph(block, compact)
        is CourseFormula -> TextbookFormula(block, compact)
        is CourseList -> TextbookList(block, compact)
        is CourseExample -> TextbookExample(block, compact)
        is CourseExercise -> TextbookExercise(block, compact)
        is CourseConclusion -> TextbookConclusion(block.text, compact)
        is CourseSceneBlock -> CourseSceneView(block.scene, page, compact)
    }
}

@Composable
private fun TextbookSubheading(text: String) {
    Text(
        text = text,
        color = InteractiveWhite,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun TextbookParagraph(block: CourseText, compact: Boolean) {
    val color = when (block.style) {
        CourseTextStyle.PROMPT -> InteractiveBlue
        CourseTextStyle.CAPTION -> InteractiveMuted
        CourseTextStyle.HISTORY -> InteractiveWhite.copy(alpha = 0.76f)
        CourseTextStyle.EXPLANATION -> InteractiveWhite.copy(alpha = 0.78f)
        CourseTextStyle.TEXTBOOK -> InteractiveWhite.copy(alpha = 0.88f)
    }
    Text(
        text = block.text,
        color = color,
        fontSize = if (compact) 15.sp else 16.sp,
        lineHeight = if (compact) 24.sp else 27.sp,
        fontStyle = if (block.style == CourseTextStyle.CAPTION) FontStyle.Italic else FontStyle.Normal,
    )
}

@Composable
private fun TextbookFormula(block: CourseFormula, compact: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveYellow.copy(alpha = 0.24f)))
        Text(
            text = block.expression,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 15.dp),
            color = InteractiveYellow,
            fontSize = if (compact) 20.sp else 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (block.conditions.isNotEmpty()) {
            Text(
                text = block.conditions.joinToString("，"),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                color = InteractiveMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveYellow.copy(alpha = 0.24f)))
    }
}

@Composable
private fun TextbookList(block: CourseList, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        block.items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Text("—", color = InteractiveBlue, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item,
                    modifier = Modifier.weight(1f),
                    color = InteractiveWhite.copy(alpha = 0.88f),
                    fontSize = if (compact) 15.sp else 16.sp,
                    lineHeight = 25.sp,
                )
            }
        }
    }
}

@Composable
private fun TextbookExample(block: CourseExample, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(block.label, color = InteractiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            block.statement,
            color = InteractiveWhite,
            fontSize = if (compact) 15.sp else 16.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Medium,
        )
        block.steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Text("${index + 1}", color = InteractiveYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    step,
                    modifier = Modifier.weight(1f),
                    color = InteractiveWhite.copy(alpha = 0.84f),
                    fontSize = if (compact) 14.sp else 15.sp,
                    lineHeight = 24.sp,
                )
            }
        }
        block.result?.let {
            Text(
                it,
                color = InteractiveYellow,
                fontSize = if (compact) 15.sp else 16.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TextbookExercise(block: CourseExercise, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = listOf(block.number, block.stem).filter(String::isNotBlank).joinToString("  "),
            color = InteractiveWhite,
            fontSize = if (compact) 15.sp else 16.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Medium,
        )
        block.choices.forEach { choice ->
            Text(choice, color = InteractiveWhite.copy(alpha = 0.82f), fontSize = 15.sp, lineHeight = 23.sp)
        }
        block.hints.forEachIndexed { index, hint ->
            Text(
                "提示 ${index + 1}：$hint",
                color = InteractiveMuted,
                fontSize = 13.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun TextbookConclusion(text: String, compact: Boolean) {
    Column {
        Box(Modifier.fillMaxWidth().height(2.dp).background(InteractiveBlue.copy(alpha = 0.72f)))
        Spacer(Modifier.height(10.dp))
        Text(
            text,
            color = InteractiveWhite,
            fontSize = if (compact) 15.sp else 16.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CourseSceneView(
    scene: CourseScene,
    page: CoursePage,
    compact: Boolean,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(sceneHeight(scene, compact)),
        contentAlignment = Alignment.Center,
    ) {
        CourseSceneRegistry.Render(scene, page)
    }
}

private fun sceneHeight(scene: CourseScene, compact: Boolean): Dp {
    if (scene.template == CourseSceneTemplate.DECLARATIVE_DIAGRAM) {
        return scene.data.number("height", if (compact) 260.0 else 320.0).toFloat().dp
    }
    if (scene.template == CourseSceneTemplate.NUMBER_LINE) {
        return when (scene.data.string("mode")) {
            "read_points" -> if (compact) 230.dp else 280.dp
            "example" -> if (compact) 290.dp else 340.dp
            else -> if (compact) 360.dp else 420.dp
        }
    }
    return when (scene.template) {
        CourseSceneTemplate.OPPOSITE_QUANTITIES,
        CourseSceneTemplate.INTEGER_TO_FRACTION,
        CourseSceneTemplate.RATIONAL_CLASSIFICATION,
        -> if (compact) 360.dp else 420.dp

        CourseSceneTemplate.FUNCTION_GRAPH,
        CourseSceneTemplate.CARTESIAN_PLANE,
        CourseSceneTemplate.GEOMETRY,
        CourseSceneTemplate.TRANSFORMATION,
        CourseSceneTemplate.RIGHT_TRIANGLE,
        CourseSceneTemplate.DATA_CHART,
        CourseSceneTemplate.PROBABILITY,
        CourseSceneTemplate.PROJECTION,
        -> if (compact) 300.dp else 360.dp

        else -> if (compact) 220.dp else 280.dp
    }
}

private object CourseSceneRegistry {
    @Composable
    fun Render(scene: CourseScene, page: CoursePage) {
        when (scene.template) {
            CourseSceneTemplate.OPPOSITE_QUANTITIES -> OppositeQuantitiesSceneVisual(scene.data)
            CourseSceneTemplate.RATIONAL_CLASSIFICATION -> RationalConceptFlowVisual(scene.data)
            CourseSceneTemplate.INTEGER_TO_FRACTION -> IntegerToFractionTextbookVisual()
            CourseSceneTemplate.NUMBER_LINE -> NumberLineLessonVisual(scene.data)
            CourseSceneTemplate.OPPOSITE_NUMBERS -> AdjustableNumberLine(NumberLineMode.OPPOSITE)
            CourseSceneTemplate.ABSOLUTE_VALUE -> AbsoluteValueNumberLineVisual()
            CourseSceneTemplate.NUMBER_COMPARISON -> ComparisonVisual()
            CourseSceneTemplate.ADDITION_PROCESS -> SignedUnitVisual()
            CourseSceneTemplate.SUBTRACTION_TRANSFORM,
            CourseSceneTemplate.DIVISION_TRANSFORM,
            -> FormulaProcessVisual(
                scene.data.string("expression").takeIf(String::isNotBlank) ?: page.firstFormula(),
            )

            CourseSceneTemplate.MULTIPLICATION_SIGN -> SignRuleVisual()
            CourseSceneTemplate.POWER_PROCESS -> PowerVisual()
            CourseSceneTemplate.DECLARATIVE_DIAGRAM -> DeclarativeDiagram(scene.data)
            else -> TextbookMathVisual(scene.template, scene.data)
        }
    }
}

private fun CoursePage.firstFormula(): String? =
    blocks.filterIsInstance<CourseFormula>().firstOrNull()?.expression

@Composable
private fun DeclarativeDiagram(data: CourseSceneData) {
    val elements = data.objects("elements")
    Canvas(Modifier.fillMaxSize()) {
        elements.forEach { element -> drawDiagramElement(element) }
    }
}

private fun DrawScope.drawDiagramElement(element: Map<String, Any?>) {
    val type = element.stringValue("type")
    val color = when (element.stringValue("color")) {
        "yellow" -> InteractiveYellow
        "muted" -> InteractiveMuted
        "white" -> InteractiveWhite
        else -> InteractiveBlue
    }
    val stroke = element.numberValue("stroke", 3.0).toFloat()
    fun x(key: String): Float = size.width * element.numberValue(key).toFloat()
    fun y(key: String): Float = size.height * element.numberValue(key).toFloat()

    when (type) {
        "line", "arrow" -> {
            val start = Offset(x("x1"), y("y1"))
            val end = Offset(x("x2"), y("y2"))
            drawLine(color, start, end, stroke, StrokeCap.Round)
            if (type == "arrow") drawDiagramArrowHead(start, end, color, stroke)
        }
        "point" -> drawCircle(color, element.numberValue("radius", 0.015).toFloat() * size.minDimension, Offset(x("x"), y("y")))
        "circle" -> drawCircle(
            color = color,
            radius = element.numberValue("radius").toFloat() * size.minDimension,
            center = Offset(x("x"), y("y")),
            style = Stroke(stroke),
        )
        "rectangle" -> drawRect(
            color = color,
            topLeft = Offset(x("x"), y("y")),
            size = Size(size.width * element.numberValue("width").toFloat(), size.height * element.numberValue("height").toFloat()),
            style = Stroke(stroke),
        )
        "polyline" -> {
            val points = element.objectList("points").map { Offset(size.width * it.numberValue("x").toFloat(), size.height * it.numberValue("y").toFloat()) }
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, color, style = Stroke(stroke))
            }
        }
        "text" -> drawDiagramText(element.stringValue("text"), Offset(x("x"), y("y")), color, element.numberValue("size", 18.0).toFloat())
        "number_line" -> drawDiagramNumberLine(element, color)
    }
}

private fun DrawScope.drawDiagramNumberLine(element: Map<String, Any?>, color: Color) {
    val left = size.width * element.numberValue("x1", 0.08).toFloat()
    val right = size.width * element.numberValue("x2", 0.92).toFloat()
    val y = size.height * element.numberValue("y", 0.55).toFloat()
    val minimum = element.numberValue("min", -5.0)
    val maximum = element.numberValue("max", 5.0)
    val step = element.numberValue("step", 1.0)
    drawLine(color, Offset(left, y), Offset(right, y), 3f, StrokeCap.Round)
    var value = minimum
    var guard = 0
    while (value <= maximum + step / 1000.0 && guard++ < 1000) {
        val ratio = ((value - minimum) / (maximum - minimum)).toFloat()
        val px = left + (right - left) * ratio
        drawLine(InteractiveMuted, Offset(px, y - 7f), Offset(px, y + 7f), 2f)
        drawDiagramText(formatDiagramNumber(value), Offset(px, y + 28f), InteractiveMuted, 15f)
        value += step
    }
}

private fun DrawScope.drawDiagramArrowHead(start: Offset, end: Offset, color: Color, width: Float) {
    val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
    val length = 13f
    listOf(-PI.toFloat() / 6f, PI.toFloat() / 6f).forEach { delta ->
        drawLine(
            color,
            end,
            Offset(
                end.x - length * cos((angle + delta).toDouble()).toFloat(),
                end.y - length * sin((angle + delta).toDouble()).toFloat(),
            ),
            width,
            StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawDiagramText(text: String, point: Offset, color: Color, textSize: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(text, point.x, point.y, paint)
}

private fun Map<String, Any?>.stringValue(key: String): String = this[key] as? String ?: ""
private fun Map<String, Any?>.numberValue(key: String, default: Double = 0.0): Double = (this[key] as? Number)?.toDouble() ?: default
@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.objectList(key: String): List<Map<String, Any?>> = this[key] as? List<Map<String, Any?>> ?: emptyList()
private fun formatDiagramNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
