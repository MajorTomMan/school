package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
internal fun IntegerToFractionTextbookVisual() {
    var selected by remember { mutableIntStateOf(0) }
    val conversions = listOf(
        Triple("2", "2/1", "正整数可以写成正分数的形式"),
        Triple("−3", "−3/1", "负整数可以写成负分数的形式"),
        Triple("0", "0/1", "0也可以写成分数的形式"),
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            "整数也可以写成分数的形式",
            color = InteractiveWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            conversions.forEachIndexed { index, item ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selected = index }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        item.first,
                        color = if (selected == index) InteractiveBlue else InteractiveWhite,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("↓", color = InteractiveMuted, fontSize = 18.sp)
                    Text(
                        item.second,
                        color = if (selected == index) InteractiveYellow else InteractiveWhite.copy(alpha = 0.82f),
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Text(
            conversions[selected].third,
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(1.dp).then(Modifier),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawLine(
                    InteractiveBlue.copy(alpha = 0.55f),
                    Offset(0f, size.height / 2),
                    Offset(size.width, size.height / 2),
                    strokeWidth = 2f,
                )
            }
        }
        Text(
            "可以写成分数形式的数称为有理数。",
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun TextbookMathVisual(
    kind: RationalVisualizationKind,
    params: Map<String, String>,
) {
    val title = params["title"].orEmpty()
    Column(Modifier.fillMaxSize()) {
        if (title.isNotBlank()) {
            Text(
                title,
                color = InteractiveWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            when (kind) {
                RationalVisualizationKind.ALGEBRA_PROCESS -> drawAlgebraProcess(params)
                RationalVisualizationKind.EQUATION_BALANCE -> drawEquationBalance(params)
                RationalVisualizationKind.ROOT_NUMBER_LINE -> drawRootNumberLine()
                RationalVisualizationKind.CARTESIAN_PLANE -> drawCartesianPlane(null)
                RationalVisualizationKind.FUNCTION_GRAPH -> drawCartesianPlane(params["function"] ?: "linear")
                RationalVisualizationKind.GEOMETRY -> drawGeometry(params["shape"] ?: "triangle")
                RationalVisualizationKind.TRANSFORMATION -> drawTransformation(params["mode"] ?: "translation")
                RationalVisualizationKind.RIGHT_TRIANGLE -> drawRightTriangle(params)
                RationalVisualizationKind.DATA_CHART -> drawDataChart(params["mode"] ?: "bar")
                RationalVisualizationKind.PROBABILITY -> drawProbabilityTree()
                RationalVisualizationKind.PROJECTION -> drawProjection()
                else -> Unit
            }
        }
        params["note"]?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                color = InteractiveMuted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAlgebraProcess(params: Map<String, String>) {
    val left = params["left"] ?: "3x + 2x"
    val right = params["right"] ?: "5x"
    drawCenteredText(left, size.width * 0.25f, size.height * 0.48f, InteractiveWhite, 38f)
    drawArrow(size.width * 0.42f, size.height * 0.48f, size.width * 0.58f, size.height * 0.48f, InteractiveBlue)
    drawCenteredText(right, size.width * 0.76f, size.height * 0.48f, InteractiveYellow, 42f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEquationBalance(params: Map<String, String>) {
    val centerX = size.width / 2
    val beamY = size.height * 0.38f
    drawLine(InteractiveWhite, Offset(size.width * .18f, beamY), Offset(size.width * .82f, beamY), 4f, StrokeCap.Round)
    drawLine(InteractiveMuted, Offset(centerX, beamY), Offset(centerX, size.height * .72f), 4f)
    val base = Path().apply {
        moveTo(centerX, size.height * .65f)
        lineTo(centerX - 44f, size.height * .82f)
        lineTo(centerX + 44f, size.height * .82f)
        close()
    }
    drawPath(base, InteractiveMuted.copy(alpha = .5f), style = Stroke(3f))
    drawLine(InteractiveBlue, Offset(size.width * .25f, beamY), Offset(size.width * .25f, beamY + 62f), 2f)
    drawLine(InteractiveYellow, Offset(size.width * .75f, beamY), Offset(size.width * .75f, beamY + 62f), 2f)
    drawCenteredText(params["left"] ?: "x + 3", size.width * .25f, beamY + 92f, InteractiveBlue, 30f)
    drawCenteredText(params["right"] ?: "7", size.width * .75f, beamY + 92f, InteractiveYellow, 30f)
    drawCenteredText("等式两边进行相同的运算，等式仍成立", centerX, size.height * .94f, InteractiveMuted, 21f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRootNumberLine() {
    val y = size.height * .58f
    val left = 36f
    val right = size.width - 36f
    drawLine(InteractiveWhite.copy(alpha = .75f), Offset(left, y), Offset(right, y), 3f)
    for (i in 0..4) {
        val x = left + (right - left) * i / 4f
        drawLine(InteractiveMuted, Offset(x, y - 8f), Offset(x, y + 8f), 2f)
        drawCenteredText(i.toString(), x, y + 34f, InteractiveMuted, 20f)
    }
    val root2X = left + (right - left) * 1.4142f / 4f
    drawCircle(InteractiveYellow, 8f, Offset(root2X, y))
    drawCenteredText("√2", root2X, y - 28f, InteractiveYellow, 26f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCartesianPlane(function: String?) {
    val origin = Offset(size.width * .5f, size.height * .55f)
    val sx = size.width / 10f
    val sy = size.height / 8f
    drawLine(InteractiveWhite.copy(alpha = .65f), Offset(25f, origin.y), Offset(size.width - 20f, origin.y), 2.5f)
    drawLine(InteractiveWhite.copy(alpha = .65f), Offset(origin.x, size.height - 18f), Offset(origin.x, 18f), 2.5f)
    (-4..4).forEach { n ->
        val x = origin.x + n * sx
        val y = origin.y - n * sy
        drawLine(InteractiveMuted.copy(alpha = .5f), Offset(x, origin.y - 5f), Offset(x, origin.y + 5f), 1.5f)
        drawLine(InteractiveMuted.copy(alpha = .5f), Offset(origin.x - 5f, y), Offset(origin.x + 5f, y), 1.5f)
    }
    if (function == null) {
        val p = Offset(origin.x + 2 * sx, origin.y - 2 * sy)
        drawCircle(InteractiveYellow, 8f, p)
        drawCenteredText("P(2, 2)", p.x + 35f, p.y - 18f, InteractiveYellow, 21f)
        return
    }
    val path = Path()
    var first = true
    for (pixel in 0..size.width.toInt()) {
        val x = (pixel - origin.x) / sx
        val value = when {
            function.contains("quadratic", true) -> .38f * x * x - 1.2f
            function.contains("inverse", true) -> if (kotlin.math.abs(x) < .18f) Float.NaN else 1.7f / x
            else -> .72f * x + .5f
        }
        if (!value.isFinite()) {
            first = true
            continue
        }
        val point = Offset(pixel.toFloat(), origin.y - value * sy)
        if (point.y !in -20f..size.height + 20f) {
            first = true
        } else if (first) {
            path.moveTo(point.x, point.y)
            first = false
        } else path.lineTo(point.x, point.y)
    }
    drawPath(path, InteractiveBlue, style = Stroke(4f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGeometry(shape: String) {
    when {
        shape.contains("circle", true) -> {
            val c = Offset(size.width / 2, size.height / 2)
            val r = minOf(size.width, size.height) * .32f
            drawCircle(InteractiveBlue, r, c, style = Stroke(4f))
            drawLine(InteractiveYellow, c, Offset(c.x + r, c.y), 3f)
            drawCenteredText("O", c.x - 18f, c.y + 8f, InteractiveWhite, 22f)
        }
        shape.contains("parallel", true) -> {
            drawLine(InteractiveBlue, Offset(45f, size.height * .32f), Offset(size.width - 40f, size.height * .22f), 4f)
            drawLine(InteractiveBlue, Offset(45f, size.height * .72f), Offset(size.width - 40f, size.height * .62f), 4f)
            drawLine(InteractiveYellow, Offset(size.width * .36f, 30f), Offset(size.width * .62f, size.height - 25f), 4f)
        }
        else -> {
            val a = Offset(size.width * .5f, size.height * .14f)
            val b = Offset(size.width * .17f, size.height * .82f)
            val c = Offset(size.width * .83f, size.height * .82f)
            drawLine(InteractiveBlue, a, b, 4f)
            drawLine(InteractiveBlue, b, c, 4f)
            drawLine(InteractiveBlue, c, a, 4f)
            drawCenteredText("A", a.x, a.y - 14f, InteractiveWhite, 22f)
            drawCenteredText("B", b.x - 12f, b.y + 28f, InteractiveWhite, 22f)
            drawCenteredText("C", c.x + 12f, c.y + 28f, InteractiveWhite, 22f)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTransformation(mode: String) {
    val original = listOf(
        Offset(size.width * .18f, size.height * .62f),
        Offset(size.width * .34f, size.height * .28f),
        Offset(size.width * .43f, size.height * .68f),
    )
    val transformed = original.map { point ->
        when {
            mode.contains("rotation", true) -> Offset(size.width - point.y * .55f, point.x * .75f)
            mode.contains("symmetry", true) -> Offset(size.width - point.x, point.y)
            else -> Offset(point.x + size.width * .38f, point.y - size.height * .08f)
        }
    }
    drawPolygon(original, InteractiveBlue)
    drawPolygon(transformed, InteractiveYellow)
    original.zip(transformed).forEach { (a, b) -> drawLine(InteractiveMuted.copy(alpha = .4f), a, b, 2f) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRightTriangle(params: Map<String, String>) {
    val a = Offset(size.width * .2f, size.height * .78f)
    val b = Offset(size.width * .78f, size.height * .78f)
    val c = Offset(size.width * .2f, size.height * .2f)
    drawLine(InteractiveBlue, a, b, 4f)
    drawLine(InteractiveBlue, a, c, 4f)
    drawLine(InteractiveYellow, c, b, 4f)
    drawLine(InteractiveMuted, Offset(a.x + 20f, a.y), Offset(a.x + 20f, a.y - 20f), 2f)
    drawLine(InteractiveMuted, Offset(a.x, a.y - 20f), Offset(a.x + 20f, a.y - 20f), 2f)
    drawCenteredText(params["formula"] ?: "a² + b² = c²", size.width * .55f, size.height * .15f, InteractiveYellow, 28f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDataChart(mode: String) {
    val values = listOf(.38f, .72f, .52f, .88f, .64f)
    val base = size.height * .82f
    if (mode.contains("line", true)) {
        val pts = values.mapIndexed { i, v -> Offset(size.width * (.12f + i * .19f), base - v * size.height * .65f) }
        pts.zipWithNext().forEach { (a, b) -> drawLine(InteractiveBlue, a, b, 4f, StrokeCap.Round) }
        pts.forEach { drawCircle(InteractiveYellow, 7f, it) }
    } else {
        values.forEachIndexed { i, v ->
            val x = size.width * (.1f + i * .18f)
            drawRect(InteractiveBlue.copy(alpha = .78f), Offset(x, base - v * size.height * .65f), androidx.compose.ui.geometry.Size(size.width * .1f, v * size.height * .65f))
        }
    }
    drawLine(InteractiveWhite.copy(alpha = .7f), Offset(28f, base), Offset(size.width - 22f, base), 2f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProbabilityTree() {
    val root = Offset(size.width * .12f, size.height * .5f)
    val mid1 = Offset(size.width * .47f, size.height * .27f)
    val mid2 = Offset(size.width * .47f, size.height * .73f)
    val leaves = listOf(
        Offset(size.width * .85f, size.height * .14f),
        Offset(size.width * .85f, size.height * .38f),
        Offset(size.width * .85f, size.height * .62f),
        Offset(size.width * .85f, size.height * .86f),
    )
    drawLine(InteractiveBlue, root, mid1, 3f)
    drawLine(InteractiveYellow, root, mid2, 3f)
    drawLine(InteractiveBlue, mid1, leaves[0], 3f)
    drawLine(InteractiveYellow, mid1, leaves[1], 3f)
    drawLine(InteractiveBlue, mid2, leaves[2], 3f)
    drawLine(InteractiveYellow, mid2, leaves[3], 3f)
    listOf(root, mid1, mid2).plus(leaves).forEach { drawCircle(InteractiveWhite, 6f, it) }
    drawCenteredText("第一次", mid1.x, size.height * .08f, InteractiveMuted, 20f)
    drawCenteredText("第二次", leaves[0].x, size.height * .06f, InteractiveMuted, 20f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawProjection() {
    val x = size.width * .28f
    val y = size.height * .25f
    val w = size.width * .38f
    val h = size.height * .42f
    val d = size.width * .13f
    val points = listOf(
        Offset(x, y), Offset(x + w, y), Offset(x + w, y + h), Offset(x, y + h),
        Offset(x + d, y - d), Offset(x + w + d, y - d), Offset(x + w + d, y + h - d), Offset(x + d, y + h - d),
    )
    val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
    edges.forEach { (a, b) -> drawLine(InteractiveBlue, points[a], points[b], 3f) }
    drawArrow(size.width * .73f, size.height * .48f, size.width * .9f, size.height * .48f, InteractiveYellow)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygon(points: List<Offset>, color: Color) {
    points.zip(points.drop(1) + points.first()).forEach { (a, b) -> drawLine(color, a, b, 4f) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(x1: Float, y1: Float, x2: Float, y2: Float, color: Color) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), 4f, StrokeCap.Round)
    val angle = kotlin.math.atan2(y2 - y1, x2 - x1)
    val l = 13f
    drawLine(color, Offset(x2, y2), Offset(x2 - l * cos((angle - PI.toFloat() / 6).toDouble()).toFloat(), y2 - l * sin((angle - PI.toFloat() / 6).toDouble()).toFloat()), 4f)
    drawLine(color, Offset(x2, y2), Offset(x2 - l * cos((angle + PI.toFloat() / 6).toDouble()).toFloat(), y2 - l * sin((angle + PI.toFloat() / 6).toDouble()).toFloat()), 4f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCenteredText(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    textSize: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText(text, x, y, paint)
}
