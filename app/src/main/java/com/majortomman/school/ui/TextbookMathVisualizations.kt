package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.majortomman.school.learning.course.RationalVisualizationKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun TextbookMathVisualization(
    kind: RationalVisualizationKind,
    params: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        RationalVisualizationKind.RATIONAL_DEFINITION_FLOW -> RationalDefinitionFlowVisual(modifier)
        RationalVisualizationKind.RATIONAL_CLASSIFICATION -> RationalClassificationTextbookVisual(modifier)
        RationalVisualizationKind.SUBTRACTION_TRANSFORM -> FormulaTransformationVisual(
            title = "减法转化为加法",
            formula = "a − b = a + (−b)",
            explanation = "减去一个数，等于加上这个数的相反数。",
            modifier = modifier,
        )
        RationalVisualizationKind.DIVISION_TRANSFORM -> FormulaTransformationVisual(
            title = "除法转化为乘法",
            formula = "a ÷ b = a × 1/b（b ≠ 0）",
            explanation = "除以一个不等于0的数，等于乘这个数的倒数。",
            modifier = modifier,
        )
        RationalVisualizationKind.FUNCTION_GRAPH -> FunctionGraphVisual(params["mode"].orEmpty(), modifier)
        else -> GenericTextbookCanvas(kind, modifier)
    }
}

@Composable
private fun RationalDefinitionFlowVisual(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "教材中的推导顺序",
            color = InteractiveMuted,
            fontSize = 11.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("正整数", color = InteractiveBlue, fontSize = 14.sp)
            Text("0", color = InteractiveWhite, fontSize = 14.sp)
            Text("负整数", color = InteractiveYellow, fontSize = 14.sp)
        }
        Text(
            "正整数、0、负整数统称为整数。",
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            "↓  整数也可以写成分数形式",
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("2 = 2/1", "−3 = −3/1", "0 = 0/1").forEach { formula ->
                Text(formula, color = InteractiveYellow, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text(
            "可以写成分数形式的数称为有理数。",
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RationalClassificationTextbookVisual(modifier: Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("教材中的辨认顺序", color = InteractiveMuted, fontSize = 11.sp)
        Text("先指出正有理数、负有理数", color = InteractiveWhite, fontSize = 16.sp)
        Text("↓", modifier = Modifier.fillMaxWidth(), color = InteractiveBlue, fontSize = 18.sp, textAlign = TextAlign.Center)
        Text("再分别指出其中的正整数、负整数", color = InteractiveWhite, fontSize = 16.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("正有理数  →  正整数", color = InteractiveBlue, fontSize = 14.sp)
            Text("负有理数  →  负整数", color = InteractiveYellow, fontSize = 14.sp)
        }
        Text("0既不是正数，也不是负数。", color = InteractiveMuted, fontSize = 13.sp)
    }
}

@Composable
private fun FormulaTransformationVisual(
    title: String,
    formula: String,
    explanation: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = InteractiveMuted, fontSize = 12.sp)
        Text(
            formula,
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveYellow,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(explanation, color = InteractiveWhite, fontSize = 14.sp)
    }
}

@Composable
private fun FunctionGraphVisual(mode: String, modifier: Modifier) {
    Canvas(modifier.fillMaxSize().padding(10.dp)) {
        val origin = Offset(size.width * 0.5f, size.height * 0.56f)
        drawAxes(origin)
        val path = Path()
        when (mode.lowercase()) {
            "quadratic" -> {
                var started = false
                for (step in 0..160) {
                    val x = -2.5f + step / 160f * 5f
                    val y = x * x - 1f
                    val point = Offset(origin.x + x * size.width / 7f, origin.y - y * size.height / 8f)
                    if (!started) { path.moveTo(point.x, point.y); started = true } else path.lineTo(point.x, point.y)
                }
            }
            "inverse" -> {
                for (branch in listOf(-1, 1)) {
                    val branchPath = Path()
                    var started = false
                    for (step in 1..100) {
                        val x = branch * (0.35f + step / 100f * 2.7f)
                        val y = 1.4f / x
                        val point = Offset(origin.x + x * size.width / 7f, origin.y - y * size.height / 5.5f)
                        if (!started) { branchPath.moveTo(point.x, point.y); started = true } else branchPath.lineTo(point.x, point.y)
                    }
                    drawPath(branchPath, InteractiveBlue, style = Stroke(width = 3.5f))
                }
                drawCanvasText("y = k/x", size.width * 0.74f, 26f, InteractiveYellow, 25f)
                return@Canvas
            }
            else -> {
                path.moveTo(size.width * 0.12f, size.height * 0.84f)
                path.lineTo(size.width * 0.88f, size.height * 0.2f)
            }
        }
        drawPath(path, InteractiveBlue, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
        drawCircle(InteractiveYellow, 6f, origin)
        drawCanvasText(
            if (mode == "quadratic") "二次函数" else "一次函数",
            size.width * 0.76f,
            26f,
            InteractiveYellow,
            25f,
        )
    }
}

@Composable
private fun GenericTextbookCanvas(kind: RationalVisualizationKind, modifier: Modifier) {
    Canvas(modifier.fillMaxSize().padding(10.dp)) {
        when (kind) {
            RationalVisualizationKind.EQUATION_BALANCE -> drawEquationBalance()
            RationalVisualizationKind.EQUATION_SYSTEM -> drawEquationSystem()
            RationalVisualizationKind.INEQUALITY_NUMBER_LINE -> drawInequalityRay()
            RationalVisualizationKind.COORDINATE_PLANE -> drawCoordinatePlane()
            RationalVisualizationKind.INTERSECTING_LINES -> drawIntersectingLines()
            RationalVisualizationKind.PARALLEL_LINES -> drawParallelLines()
            RationalVisualizationKind.TRANSLATION -> drawTranslation()
            RationalVisualizationKind.TRIANGLE -> drawTriangleVisual()
            RationalVisualizationKind.CONGRUENT_TRIANGLES -> drawCongruentTriangles()
            RationalVisualizationKind.AXIS_SYMMETRY -> drawAxisSymmetry()
            RationalVisualizationKind.PYTHAGOREAN -> drawPythagorean()
            RationalVisualizationKind.QUADRILATERAL -> drawQuadrilateral()
            RationalVisualizationKind.FUNCTION_RELATION -> drawFunctionRelation()
            RationalVisualizationKind.STATISTICS -> drawStatistics()
            RationalVisualizationKind.PROBABILITY -> drawProbability()
            RationalVisualizationKind.ROTATION -> drawRotation()
            RationalVisualizationKind.CIRCLE -> drawCircleVisual()
            RationalVisualizationKind.SIMILARITY -> drawSimilarity()
            RationalVisualizationKind.RIGHT_TRIANGLE -> drawRightTriangle()
            RationalVisualizationKind.PROJECTION -> drawProjection()
            RationalVisualizationKind.ALGEBRA_PROCESS -> drawAlgebraProcess()
            else -> drawCanvasText("观察教材中的数量与图形关系", size.width / 2f, size.height / 2f, InteractiveMuted, 24f)
        }
    }
}

private fun DrawScope.drawAxes(origin: Offset) {
    drawLine(InteractiveWhite.copy(alpha = 0.62f), Offset(18f, origin.y), Offset(size.width - 18f, origin.y), 2f)
    drawLine(InteractiveWhite.copy(alpha = 0.62f), Offset(origin.x, size.height - 15f), Offset(origin.x, 15f), 2f)
    drawLine(InteractiveWhite.copy(alpha = 0.62f), Offset(size.width - 18f, origin.y), Offset(size.width - 30f, origin.y - 7f), 2f)
    drawLine(InteractiveWhite.copy(alpha = 0.62f), Offset(size.width - 18f, origin.y), Offset(size.width - 30f, origin.y + 7f), 2f)
    drawLine(InteractiveWhite.copy(alpha = 0.62f), Offset(origin.x, 15f), Offset(origin.x - 7f, 27f), 2f)
    drawLine(InteractiveWhite.copy(alpha = 0.62f), Offset(origin.x, 15f), Offset(origin.x + 7f, 27f), 2f)
}

private fun DrawScope.drawEquationBalance() {
    val centerX = size.width / 2f
    val beamY = size.height * 0.38f
    drawLine(InteractiveWhite, Offset(centerX - 120f, beamY), Offset(centerX + 120f, beamY), 4f, StrokeCap.Round)
    drawLine(InteractiveMuted, Offset(centerX, beamY), Offset(centerX, size.height * 0.73f), 4f)
    drawLine(InteractiveMuted, Offset(centerX - 55f, size.height * 0.73f), Offset(centerX + 55f, size.height * 0.73f), 4f)
    listOf(centerX - 95f, centerX + 95f).forEach { x ->
        drawLine(InteractiveMuted, Offset(x, beamY), Offset(x, beamY + 62f), 2f)
        drawLine(InteractiveBlue.copy(alpha = 0.8f), Offset(x - 48f, beamY + 62f), Offset(x + 48f, beamY + 62f), 3f)
    }
    drawCanvasText("x + a", centerX - 95f, beamY + 50f, InteractiveBlue, 24f)
    drawCanvasText("b", centerX + 95f, beamY + 50f, InteractiveYellow, 24f)
    drawCanvasText("等式两边进行相同变形，平衡保持不变", centerX, size.height - 20f, InteractiveMuted, 21f)
}

private fun DrawScope.drawEquationSystem() {
    val o = Offset(size.width / 2f, size.height / 2f)
    drawAxes(o)
    drawLine(InteractiveBlue, Offset(35f, size.height * 0.78f), Offset(size.width - 35f, size.height * 0.24f), 3f)
    drawLine(InteractiveYellow, Offset(35f, size.height * 0.25f), Offset(size.width - 35f, size.height * 0.68f), 3f)
    drawCircle(InteractiveWhite, 7f, Offset(size.width * 0.54f, size.height * 0.47f))
    drawCanvasText("公共解", size.width * 0.65f, size.height * 0.42f, InteractiveWhite, 22f)
}

private fun DrawScope.drawInequalityRay() {
    val y = size.height * 0.55f
    drawLine(InteractiveWhite.copy(alpha = 0.7f), Offset(25f, y), Offset(size.width - 25f, y), 3f)
    val x = size.width * 0.44f
    drawCircle(InteractiveBlack, 10f, Offset(x, y))
    drawCircle(InteractiveYellow, 10f, Offset(x, y), style = Stroke(3f))
    drawLine(InteractiveBlue, Offset(x + 12f, y), Offset(size.width - 40f, y), 7f, StrokeCap.Round)
    drawLine(InteractiveBlue, Offset(size.width - 40f, y), Offset(size.width - 55f, y - 10f), 4f)
    drawLine(InteractiveBlue, Offset(size.width - 40f, y), Offset(size.width - 55f, y + 10f), 4f)
    drawCanvasText("a", x, y + 38f, InteractiveYellow, 23f)
    drawCanvasText("x > a", size.width / 2f, 32f, InteractiveWhite, 28f)
}

private fun DrawScope.drawCoordinatePlane() {
    val o = Offset(size.width * 0.46f, size.height * 0.58f)
    drawAxes(o)
    val p = Offset(size.width * 0.72f, size.height * 0.3f)
    drawLine(InteractiveBlue.copy(alpha = 0.5f), Offset(p.x, o.y), p, 2f)
    drawLine(InteractiveYellow.copy(alpha = 0.5f), Offset(o.x, p.y), p, 2f)
    drawCircle(InteractiveWhite, 7f, p)
    drawCanvasText("P(x, y)", p.x + 30f, p.y - 12f, InteractiveWhite, 22f)
}

private fun DrawScope.drawIntersectingLines() {
    val o = Offset(size.width / 2f, size.height / 2f)
    drawLine(InteractiveBlue, Offset(30f, size.height * 0.78f), Offset(size.width - 30f, size.height * 0.22f), 4f)
    drawLine(InteractiveYellow, Offset(30f, size.height * 0.22f), Offset(size.width - 30f, size.height * 0.78f), 4f)
    drawCanvasText("1", o.x, o.y - 40f, InteractiveWhite, 22f)
    drawCanvasText("3", o.x, o.y + 58f, InteractiveWhite, 22f)
    drawCanvasText("对顶角相等", o.x, 28f, InteractiveMuted, 22f)
}

private fun DrawScope.drawParallelLines() {
    val top = size.height * 0.34f
    val bottom = size.height * 0.68f
    drawLine(InteractiveBlue, Offset(25f, top), Offset(size.width - 25f, top), 4f)
    drawLine(InteractiveBlue, Offset(25f, bottom), Offset(size.width - 25f, bottom), 4f)
    drawLine(InteractiveYellow, Offset(size.width * 0.3f, 22f), Offset(size.width * 0.68f, size.height - 22f), 4f)
    drawCanvasText("l₁ ∥ l₂", size.width / 2f, 28f, InteractiveWhite, 24f)
}

private fun DrawScope.drawTranslation() {
    val first = listOf(Offset(50f, size.height * 0.7f), Offset(110f, size.height * 0.7f), Offset(80f, size.height * 0.3f))
    val dx = size.width * 0.5f
    drawPolygon(first, InteractiveBlue)
    drawPolygon(first.map { Offset(it.x + dx, it.y - 18f) }, InteractiveYellow)
    drawLine(InteractiveWhite.copy(alpha = 0.65f), Offset(115f, size.height * 0.5f), Offset(dx + 35f, size.height * 0.45f), 3f)
    drawCanvasText("平移方向与距离保持一致", size.width / 2f, size.height - 18f, InteractiveMuted, 21f)
}

private fun DrawScope.drawTriangleVisual() {
    val a = Offset(size.width / 2f, 28f)
    val b = Offset(45f, size.height - 35f)
    val c = Offset(size.width - 45f, size.height - 35f)
    drawPolygon(listOf(a, b, c), InteractiveBlue)
    drawCanvasText("A", a.x, a.y - 8f, InteractiveWhite, 22f)
    drawCanvasText("B", b.x - 12f, b.y + 18f, InteractiveWhite, 22f)
    drawCanvasText("C", c.x + 12f, c.y + 18f, InteractiveWhite, 22f)
}

private fun DrawScope.drawCongruentTriangles() {
    val t1 = listOf(Offset(35f, size.height * 0.72f), Offset(size.width * 0.38f, size.height * 0.72f), Offset(size.width * 0.22f, size.height * 0.27f))
    val t2 = t1.map { Offset(size.width - it.x, it.y) }
    drawPolygon(t1, InteractiveBlue)
    drawPolygon(t2, InteractiveYellow)
    drawCanvasText("对应边、对应角分别相等", size.width / 2f, size.height - 15f, InteractiveMuted, 20f)
}

private fun DrawScope.drawAxisSymmetry() {
    val axisX = size.width / 2f
    drawLine(InteractiveWhite.copy(alpha = 0.55f), Offset(axisX, 18f), Offset(axisX, size.height - 18f), 3f)
    val left = listOf(Offset(axisX - 120f, size.height * 0.72f), Offset(axisX - 55f, size.height * 0.68f), Offset(axisX - 90f, size.height * 0.28f))
    drawPolygon(left, InteractiveBlue)
    drawPolygon(left.map { Offset(axisX + (axisX - it.x), it.y) }, InteractiveYellow)
    drawCanvasText("对称轴", axisX, 28f, InteractiveMuted, 20f)
}

private fun DrawScope.drawPythagorean() {
    val a = Offset(size.width * 0.25f, size.height * 0.75f)
    val b = Offset(size.width * 0.25f, size.height * 0.25f)
    val c = Offset(size.width * 0.78f, size.height * 0.75f)
    drawPolygon(listOf(a, b, c), InteractiveBlue)
    drawLine(InteractiveYellow, Offset(a.x, a.y - 18f), Offset(a.x + 18f, a.y - 18f), 3f)
    drawLine(InteractiveYellow, Offset(a.x + 18f, a.y - 18f), Offset(a.x + 18f, a.y), 3f)
    drawCanvasText("a² + b² = c²", size.width / 2f, 30f, InteractiveYellow, 27f)
}

private fun DrawScope.drawQuadrilateral() {
    val points = listOf(Offset(55f, size.height * 0.7f), Offset(size.width * 0.32f, size.height * 0.28f), Offset(size.width * 0.78f, size.height * 0.28f), Offset(size.width - 55f, size.height * 0.7f))
    drawPolygon(points, InteractiveBlue)
    drawLine(InteractiveYellow.copy(alpha = 0.7f), points[0], points[2], 2f)
    drawLine(InteractiveYellow.copy(alpha = 0.7f), points[1], points[3], 2f)
}

private fun DrawScope.drawFunctionRelation() {
    val xs = listOf("x", "1", "2", "3")
    val ys = listOf("y", "2", "4", "6")
    xs.forEachIndexed { index, value -> drawCanvasText(value, 55f + index * (size.width - 110f) / 3f, size.height * 0.3f, if (index == 0) InteractiveBlue else InteractiveWhite, 23f) }
    ys.forEachIndexed { index, value -> drawCanvasText(value, 55f + index * (size.width - 110f) / 3f, size.height * 0.62f, if (index == 0) InteractiveYellow else InteractiveWhite, 23f) }
    drawCanvasText("一个确定的 x 对应唯一确定的 y", size.width / 2f, size.height - 18f, InteractiveMuted, 20f)
}

private fun DrawScope.drawStatistics() {
    val base = size.height * 0.82f
    val values = listOf(0.36f, 0.62f, 0.45f, 0.76f, 0.55f)
    values.forEachIndexed { index, value ->
        val x = 35f + index * (size.width - 70f) / values.lastIndex
        drawLine(if (index % 2 == 0) InteractiveBlue else InteractiveYellow, Offset(x, base), Offset(x, base - size.height * value), 18f, StrokeCap.Round)
    }
    drawLine(InteractiveWhite.copy(alpha = 0.55f), Offset(20f, base), Offset(size.width - 20f, base), 2f)
    drawCanvasText("用图表描述数据", size.width / 2f, 28f, InteractiveWhite, 24f)
}

private fun DrawScope.drawProbability() {
    val center = Offset(size.width / 2f, size.height / 2f)
    drawCircle(InteractiveBlue.copy(alpha = 0.18f), size.minDimension * 0.25f, center)
    drawCircle(InteractiveBlue, size.minDimension * 0.25f, center, style = Stroke(3f))
    drawLine(InteractiveYellow, Offset(center.x, center.y - size.minDimension * 0.25f), Offset(center.x, center.y + size.minDimension * 0.25f), 3f)
    drawCanvasText("正面", center.x - 52f, center.y + 7f, InteractiveBlue, 22f)
    drawCanvasText("反面", center.x + 52f, center.y + 7f, InteractiveYellow, 22f)
    drawCanvasText("大量重复试验中，频率趋于稳定", center.x, size.height - 15f, InteractiveMuted, 19f)
}

private fun DrawScope.drawRotation() {
    val o = Offset(size.width / 2f, size.height / 2f)
    val p = Offset(o.x + 110f, o.y)
    val q = Offset(o.x, o.y - 110f)
    drawLine(InteractiveBlue, o, p, 4f)
    drawLine(InteractiveYellow, o, q, 4f)
    drawCircle(InteractiveWhite, 7f, o)
    drawCircle(InteractiveBlue, 7f, p)
    drawCircle(InteractiveYellow, 7f, q)
    val arc = Path().apply {
        moveTo(p.x, p.y)
        cubicTo(o.x + 110f, o.y - 62f, o.x + 62f, o.y - 110f, q.x, q.y)
    }
    drawPath(arc, InteractiveMuted, style = Stroke(3f))
    drawCanvasText("旋转中心 O", o.x, size.height - 16f, InteractiveMuted, 20f)
}

private fun DrawScope.drawCircleVisual() {
    val o = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.3f
    drawCircle(InteractiveBlue, r, o, style = Stroke(4f))
    drawLine(InteractiveYellow, o, Offset(o.x + r, o.y), 3f)
    drawLine(InteractiveWhite.copy(alpha = 0.65f), Offset(o.x - r * 0.72f, o.y - r * 0.7f), Offset(o.x + r * 0.8f, o.y + r * 0.58f), 3f)
    drawCircle(InteractiveWhite, 5f, o)
    drawCanvasText("O", o.x - 12f, o.y + 22f, InteractiveWhite, 20f)
    drawCanvasText("r", o.x + r / 2f, o.y - 10f, InteractiveYellow, 21f)
}

private fun DrawScope.drawSimilarity() {
    val t1 = listOf(Offset(40f, size.height * 0.72f), Offset(size.width * 0.35f, size.height * 0.72f), Offset(size.width * 0.2f, size.height * 0.35f))
    val t2 = listOf(Offset(size.width * 0.53f, size.height * 0.76f), Offset(size.width - 35f, size.height * 0.76f), Offset(size.width * 0.72f, size.height * 0.2f))
    drawPolygon(t1, InteractiveBlue)
    drawPolygon(t2, InteractiveYellow)
    drawCanvasText("对应角相等，对应边成比例", size.width / 2f, size.height - 12f, InteractiveMuted, 20f)
}

private fun DrawScope.drawRightTriangle() {
    val a = Offset(size.width * 0.23f, size.height * 0.75f)
    val b = Offset(size.width * 0.23f, size.height * 0.25f)
    val c = Offset(size.width * 0.8f, size.height * 0.75f)
    drawPolygon(listOf(a, b, c), InteractiveBlue)
    drawCanvasText("θ", c.x - 38f, c.y - 18f, InteractiveYellow, 24f)
    drawCanvasText("对边", a.x - 25f, size.height * 0.5f, InteractiveWhite, 19f)
    drawCanvasText("邻边", size.width * 0.5f, a.y + 25f, InteractiveWhite, 19f)
    drawCanvasText("斜边", size.width * 0.56f, size.height * 0.43f, InteractiveYellow, 19f)
}

private fun DrawScope.drawProjection() {
    val front = listOf(Offset(40f, size.height * 0.68f), Offset(135f, size.height * 0.68f), Offset(135f, size.height * 0.3f), Offset(40f, size.height * 0.3f))
    val shift = Offset(48f, -35f)
    drawPolygon(front, InteractiveBlue)
    drawPolygon(front.map { it + shift }, InteractiveYellow)
    for (index in front.indices) drawLine(InteractiveWhite.copy(alpha = 0.55f), front[index], front[index] + shift, 2f)
    drawLine(InteractiveMuted, Offset(size.width * 0.58f, 28f), Offset(size.width * 0.58f, size.height - 25f), 2f)
    drawCanvasText("主视图", size.width * 0.77f, size.height * 0.3f, InteractiveWhite, 21f)
    drawRect(InteractiveBlue, Offset(size.width * 0.67f, size.height * 0.38f), androidx.compose.ui.geometry.Size(size.width * 0.2f, size.height * 0.28f), style = Stroke(3f))
}

private fun DrawScope.drawAlgebraProcess() {
    drawCanvasText("具体数量", size.width * 0.17f, size.height * 0.45f, InteractiveBlue, 24f)
    drawLine(InteractiveMuted, Offset(size.width * 0.3f, size.height * 0.42f), Offset(size.width * 0.48f, size.height * 0.42f), 3f)
    drawLine(InteractiveMuted, Offset(size.width * 0.48f, size.height * 0.42f), Offset(size.width * 0.44f, size.height * 0.38f), 3f)
    drawLine(InteractiveMuted, Offset(size.width * 0.48f, size.height * 0.42f), Offset(size.width * 0.44f, size.height * 0.46f), 3f)
    drawCanvasText("字母表示", size.width * 0.62f, size.height * 0.45f, InteractiveYellow, 24f)
    drawCanvasText("运算法则保持一致", size.width / 2f, size.height * 0.75f, InteractiveWhite, 22f)
}

private fun DrawScope.drawPolygon(points: List<Offset>, color: Color) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, color, style = Stroke(4f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCanvasText(text: String, x: Float, y: Float, color: Color, size: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = size
        textAlign = Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
