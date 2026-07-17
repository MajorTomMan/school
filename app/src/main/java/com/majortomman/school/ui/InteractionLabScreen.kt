package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.lab.CellPart
import com.majortomman.school.learning.lab.ComplexValue
import com.majortomman.school.learning.lab.OrthographicProjector
import com.majortomman.school.learning.lab.Point3D
import com.majortomman.school.learning.lab.WaterEquationBalance
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private enum class LabSample(val label: String, val subtitle: String) {
    COMPLEX("复平面", "z=a+bi"),
    SPACE("三维坐标", "P(x,y,z)"),
    CHEMISTRY("化学配平", "2H₂+O₂→2H₂O"),
    BIOLOGY("细胞标注", "植物细胞示意图"),
}

@Composable
internal fun InteractionLabScreen() {
    var selectedName by rememberSaveable { mutableStateOf(LabSample.COMPLEX.name) }
    val selected = LabSample.valueOf(selectedName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InteractiveBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text("交互能力实验室", color = InteractiveWhite, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "独立技术样板，不属于任何教材正文。课程只有在章节权限允许时才会调用对应能力。",
            color = InteractiveMuted,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        )
        Spacer(Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LabSample.entries.chunked(2).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowItems.forEach { item ->
                        LabChoice(
                            item = item,
                            selected = item == selected,
                            modifier = Modifier.weight(1f),
                        ) { selectedName = item.name }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(28.dp))

        when (selected) {
            LabSample.COMPLEX -> ComplexPlaneSample()
            LabSample.SPACE -> Coordinate3DSample()
            LabSample.CHEMISTRY -> ChemicalBalanceSample()
            LabSample.BIOLOGY -> BiologyCellSample()
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun LabChoice(
    item: LabSample,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(if (selected) InteractiveBlue.copy(alpha = 0.13f) else InteractivePanel, RoundedCornerShape(14.dp))
            .border(1.dp, if (selected) InteractiveBlue else InteractiveLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(item.label, color = if (selected) InteractiveBlue else InteractiveWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(item.subtitle, color = InteractiveMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ComplexPlaneSample() {
    var realText by rememberSaveable { mutableStateOf("3") }
    var imaginaryText by rememberSaveable { mutableStateOf("2") }
    val value = ComplexValue(realText.toDoubleOrNull() ?: 0.0, imaginaryText.toDoubleOrNull() ?: 0.0)

    SectionTitle("复数与复平面", InteractivePurple)
    Spacer(Modifier.height(12.dp))
    Text(
        "当前样板只使用实部、虚部和复平面。没有启用模、辐角或极形式。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LabNumberInput("实部 a", realText, Modifier.weight(1f)) { realText = filteredNumber(it) }
        LabNumberInput("虚部 b", imaginaryText, Modifier.weight(1f)) { imaginaryText = filteredNumber(it) }
    }
    Spacer(Modifier.height(16.dp))
    ComplexPlaneCanvas(value)
    Spacer(Modifier.height(14.dp))
    LabResultCard(
        title = "当前复数",
        main = "z = ${formatLabNumber(value.real)} ${signedImaginary(value.imaginary)}",
        detail = "复平面上的点为 (${formatLabNumber(value.real)}, ${formatLabNumber(value.imaginary)})。",
        color = InteractivePurple,
    )
}

@Composable
private fun ComplexPlaneCanvas(value: ComplexValue) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .background(InteractivePanel, RoundedCornerShape(18.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        val range = 5f
        val left = 28.dp.toPx()
        val right = size.width - 18.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        fun sx(x: Double): Float = left + ((x.toFloat() + range) / (range * 2f)) * (right - left)
        fun sy(y: Double): Float = bottom - ((y.toFloat() + range) / (range * 2f)) * (bottom - top)

        for (grid in -5..5) {
            drawLine(InteractiveLine.copy(alpha = 0.55f), Offset(sx(grid.toDouble()), top), Offset(sx(grid.toDouble()), bottom), 1.dp.toPx())
            drawLine(InteractiveLine.copy(alpha = 0.55f), Offset(left, sy(grid.toDouble())), Offset(right, sy(grid.toDouble())), 1.dp.toPx())
        }
        drawLine(InteractiveWhite.copy(alpha = 0.72f), Offset(left, sy(0.0)), Offset(right, sy(0.0)), 2.dp.toPx(), StrokeCap.Round)
        drawLine(InteractiveWhite.copy(alpha = 0.72f), Offset(sx(0.0), top), Offset(sx(0.0), bottom), 2.dp.toPx(), StrokeCap.Round)

        val clampedReal = value.real.coerceIn(-5.0, 5.0)
        val clampedImaginary = value.imaginary.coerceIn(-5.0, 5.0)
        val center = Offset(sx(clampedReal), sy(clampedImaginary))
        val origin = Offset(sx(0.0), sy(0.0))
        drawLine(InteractivePurple, origin, center, 4.dp.toPx(), StrokeCap.Round)
        drawLine(InteractiveYellow.copy(alpha = 0.48f), Offset(center.x, sy(0.0)), center, 2.dp.toPx())
        drawLine(InteractiveYellow.copy(alpha = 0.48f), Offset(sx(0.0), center.y), center, 2.dp.toPx())
        drawCircle(InteractivePurple, 9.dp.toPx(), center)
        drawCircle(InteractiveWhite, 3.dp.toPx(), center)

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 12.sp.toPx()
            color = InteractiveMuted.toArgb()
        }
        drawContext.canvas.nativeCanvas.drawText("实轴", right - 26.dp.toPx(), sy(0.0) - 8.dp.toPx(), paint)
        drawContext.canvas.nativeCanvas.drawText("虚轴", sx(0.0) + 8.dp.toPx(), top + 12.dp.toPx(), paint)
        paint.color = InteractivePurple.toArgb()
        drawContext.canvas.nativeCanvas.drawText("z", center.x + 10.dp.toPx(), center.y - 10.dp.toPx(), paint)
    }
}

@Composable
private fun Coordinate3DSample() {
    var xText by rememberSaveable { mutableStateOf("2") }
    var yText by rememberSaveable { mutableStateOf("3") }
    var zText by rememberSaveable { mutableStateOf("2") }
    var yaw by rememberSaveable { mutableFloatStateOf(35f) }
    var pitch by rememberSaveable { mutableFloatStateOf(28f) }
    val point = Point3D(
        x = xText.toDoubleOrNull() ?: 0.0,
        y = yText.toDoubleOrNull() ?: 0.0,
        z = zText.toDoubleOrNull() ?: 0.0,
    )

    SectionTitle("空间点与正交投影", InteractiveBlue)
    Spacer(Modifier.height(12.dp))
    Text(
        "首版先验证坐标、投影和视角交互。它使用正交投影，不把透视造成的远近变化当成真实长度变化。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabNumberInput("x", xText, Modifier.weight(1f)) { xText = filteredNumber(it) }
        LabNumberInput("y", yText, Modifier.weight(1f)) { yText = filteredNumber(it) }
        LabNumberInput("z", zText, Modifier.weight(1f)) { zText = filteredNumber(it) }
    }
    Spacer(Modifier.height(16.dp))
    Coordinate3DCanvas(point = point, yaw = yaw.toDouble(), pitch = pitch.toDouble())
    Spacer(Modifier.height(14.dp))
    LabSlider("水平视角", yaw, -70f..70f) { yaw = it }
    Spacer(Modifier.height(10.dp))
    LabSlider("俯仰视角", pitch, 5f..65f) { pitch = it }
    Spacer(Modifier.height(14.dp))
    LabResultCard(
        title = "当前空间点",
        main = "P(${formatLabNumber(point.x)}, ${formatLabNumber(point.y)}, ${formatLabNumber(point.z)})",
        detail = "在 xy 平面上的投影为 (${formatLabNumber(point.x)}, ${formatLabNumber(point.y)}, 0)。",
        color = InteractiveBlue,
    )
}

@Composable
private fun Coordinate3DCanvas(point: Point3D, yaw: Double, pitch: Double) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .background(InteractivePanel, RoundedCornerShape(18.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        val center = Offset(size.width * 0.50f, size.height * 0.58f)
        val scale = minOf(size.width, size.height) / 10f
        fun screen(p: Point3D): Offset {
            val projected = OrthographicProjector.project(p, yaw, pitch)
            return Offset(center.x + projected.x.toFloat() * scale, center.y - projected.y.toFloat() * scale)
        }

        val origin = screen(Point3D(0.0, 0.0, 0.0))
        val axisLength = 4.0
        val xAxis = screen(Point3D(axisLength, 0.0, 0.0))
        val yAxis = screen(Point3D(0.0, axisLength, 0.0))
        val zAxis = screen(Point3D(0.0, 0.0, axisLength))
        drawLine(InteractiveRed, origin, xAxis, 3.dp.toPx(), StrokeCap.Round)
        drawLine(InteractiveGreen, origin, yAxis, 3.dp.toPx(), StrokeCap.Round)
        drawLine(InteractiveBlue, origin, zAxis, 3.dp.toPx(), StrokeCap.Round)

        for (grid in -4..4) {
            val a = screen(Point3D(grid.toDouble(), -4.0, 0.0))
            val b = screen(Point3D(grid.toDouble(), 4.0, 0.0))
            val c = screen(Point3D(-4.0, grid.toDouble(), 0.0))
            val d = screen(Point3D(4.0, grid.toDouble(), 0.0))
            drawLine(InteractiveLine.copy(alpha = 0.48f), a, b, 1.dp.toPx())
            drawLine(InteractiveLine.copy(alpha = 0.48f), c, d, 1.dp.toPx())
        }

        val clamped = Point3D(point.x.coerceIn(-4.0, 4.0), point.y.coerceIn(-4.0, 4.0), point.z.coerceIn(-4.0, 4.0))
        val px = screen(Point3D(clamped.x, 0.0, 0.0))
        val pxy = screen(Point3D(clamped.x, clamped.y, 0.0))
        val p = screen(clamped)
        drawLine(InteractiveYellow.copy(alpha = 0.64f), origin, px, 2.dp.toPx())
        drawLine(InteractiveYellow.copy(alpha = 0.64f), px, pxy, 2.dp.toPx())
        drawLine(InteractiveYellow.copy(alpha = 0.64f), pxy, p, 2.dp.toPx())
        drawCircle(InteractiveYellow.copy(alpha = 0.45f), 7.dp.toPx(), pxy, style = Stroke(2.dp.toPx()))
        drawCircle(InteractivePurple, 9.dp.toPx(), p)
        drawCircle(InteractiveWhite, 3.dp.toPx(), p)

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 13.sp.toPx()
            color = InteractiveMuted.toArgb()
        }
        paint.color = InteractiveRed.toArgb(); drawContext.canvas.nativeCanvas.drawText("x", xAxis.x, xAxis.y, paint)
        paint.color = InteractiveGreen.toArgb(); drawContext.canvas.nativeCanvas.drawText("y", yAxis.x, yAxis.y, paint)
        paint.color = InteractiveBlue.toArgb(); drawContext.canvas.nativeCanvas.drawText("z", zAxis.x, zAxis.y, paint)
        paint.color = InteractivePurple.toArgb(); drawContext.canvas.nativeCanvas.drawText("P", p.x + 9.dp.toPx(), p.y - 9.dp.toPx(), paint)
    }
}

@Composable
private fun ChemicalBalanceSample() {
    var h2Text by rememberSaveable { mutableStateOf("2") }
    var o2Text by rememberSaveable { mutableStateOf("1") }
    var h2oText by rememberSaveable { mutableStateOf("2") }
    val balance = WaterEquationBalance(
        hydrogenCoefficient = h2Text.toIntOrNull() ?: 0,
        oxygenCoefficient = o2Text.toIntOrNull() ?: 0,
        waterCoefficient = h2oText.toIntOrNull() ?: 0,
    )

    SectionTitle("化学方程式配平", InteractiveGreen)
    Spacer(Modifier.height(12.dp))
    Text(
        "这个样板只比较反应前后氢、氧原子的数目，不使用化合价、物质的量或氧化还原概念。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(16.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabIntegerInput("H₂ 系数", h2Text, Modifier.weight(1f)) { h2Text = filteredCoefficient(it) }
        LabIntegerInput("O₂ 系数", o2Text, Modifier.weight(1f)) { o2Text = filteredCoefficient(it) }
        LabIntegerInput("H₂O 系数", h2oText, Modifier.weight(1f)) { h2oText = filteredCoefficient(it) }
    }
    Spacer(Modifier.height(16.dp))
    ChemicalEquationCanvas(balance)
    Spacer(Modifier.height(14.dp))
    val statusColor = if (balance.isBalanced) InteractiveGreen else InteractiveRed
    LabResultCard(
        title = if (balance.isBalanced) "原子数相等" else "还没有配平",
        main = "${balance.hydrogenCoefficient}H₂ + ${balance.oxygenCoefficient}O₂ → ${balance.waterCoefficient}H₂O",
        detail = "反应物 H=${balance.reactants.hydrogen}、O=${balance.reactants.oxygen}；生成物 H=${balance.products.hydrogen}、O=${balance.products.oxygen}。",
        color = statusColor,
    )
}

@Composable
private fun ChemicalEquationCanvas(balance: WaterEquationBalance) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(InteractivePanel, RoundedCornerShape(18.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        val mid = size.width / 2f
        val top = 52.dp.toPx()
        val rowGap = 34.dp.toPx()
        val maxAtoms = maxOf(1, balance.reactants.hydrogen, balance.reactants.oxygen, balance.products.hydrogen, balance.products.oxygen)
        fun barWidth(value: Int): Float = (size.width * 0.34f) * value / maxAtoms
        fun drawAtomBar(label: String, value: Int, y: Float, leftSide: Boolean, color: Color) {
            val width = barWidth(value)
            val x = if (leftSide) mid - 22.dp.toPx() - width else mid + 22.dp.toPx()
            drawRoundRect(color.copy(alpha = 0.78f), Offset(x, y), Size(width, 18.dp.toPx()), 9.dp.toPx())
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = 13.sp.toPx()
                this.color = InteractiveWhite.toArgb()
                textAlign = if (leftSide) Paint.Align.RIGHT else Paint.Align.LEFT
            }
            val textX = if (leftSide) x - 7.dp.toPx() else x + width + 7.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText("$label=$value", textX, y + 14.dp.toPx(), paint)
        }
        drawAtomBar("H", balance.reactants.hydrogen, top, true, InteractiveBlue)
        drawAtomBar("H", balance.products.hydrogen, top, false, InteractiveBlue)
        drawAtomBar("O", balance.reactants.oxygen, top + rowGap, true, InteractiveRed)
        drawAtomBar("O", balance.products.oxygen, top + rowGap, false, InteractiveRed)

        val paint = Paint().apply { isAntiAlias = true; textSize = 13.sp.toPx(); color = InteractiveMuted.toArgb(); textAlign = Paint.Align.CENTER }
        drawContext.canvas.nativeCanvas.drawText("反应物", mid - size.width * 0.24f, 24.dp.toPx(), paint)
        drawContext.canvas.nativeCanvas.drawText("生成物", mid + size.width * 0.24f, 24.dp.toPx(), paint)
        paint.textSize = 24.sp.toPx(); paint.color = (if (balance.isBalanced) InteractiveGreen else InteractiveYellow).toArgb()
        drawContext.canvas.nativeCanvas.drawText("→", mid, top + 28.dp.toPx(), paint)

        val moleculeY = size.height - 54.dp.toPx()
        repeat(balance.hydrogenCoefficient.coerceIn(0, 5)) { index ->
            drawDiatomicMolecule(Offset(28.dp.toPx() + index * 31.dp.toPx(), moleculeY), InteractiveBlue)
        }
        repeat(balance.oxygenCoefficient.coerceIn(0, 4)) { index ->
            drawDiatomicMolecule(Offset(size.width * 0.35f + index * 31.dp.toPx(), moleculeY), InteractiveRed)
        }
        repeat(balance.waterCoefficient.coerceIn(0, 5)) { index ->
            val x = mid + 34.dp.toPx() + index * 38.dp.toPx()
            drawCircle(InteractiveRed, 8.dp.toPx(), Offset(x, moleculeY))
            drawCircle(InteractiveBlue, 6.dp.toPx(), Offset(x - 9.dp.toPx(), moleculeY + 9.dp.toPx()))
            drawCircle(InteractiveBlue, 6.dp.toPx(), Offset(x + 9.dp.toPx(), moleculeY + 9.dp.toPx()))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiatomicMolecule(center: Offset, color: Color) {
    drawCircle(color, 7.dp.toPx(), Offset(center.x - 6.dp.toPx(), center.y))
    drawCircle(color, 7.dp.toPx(), Offset(center.x + 6.dp.toPx(), center.y))
}

@Composable
private fun BiologyCellSample() {
    var selectedName by rememberSaveable { mutableStateOf(CellPart.NUCLEUS.name) }
    var canvasSize by rememberSaveable { mutableStateOf(IntSize.Zero) }
    val selected = CellPart.valueOf(selectedName)

    SectionTitle("植物细胞结构标注", InteractiveYellow)
    Spacer(Modifier.height(12.dp))
    Text(
        "教学示意图，非真实比例；颜色只用于区分结构。点击图中的标记或下方名称查看说明。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(16.dp))

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .background(InteractivePanel, RoundedCornerShape(18.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(18.dp))
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                detectTapGestures { offset ->
                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                        val nearest = CellPart.entries.minByOrNull { part ->
                            val dx = offset.x - part.normalizedX * canvasSize.width
                            val dy = offset.y - part.normalizedY * canvasSize.height
                            dx * dx + dy * dy
                        }
                        if (nearest != null) {
                            val dx = offset.x - nearest.normalizedX * canvasSize.width
                            val dy = offset.y - nearest.normalizedY * canvasSize.height
                            if (sqrt(dx * dx + dy * dy) < 58.dp.toPx()) selectedName = nearest.name
                        }
                    }
                }
            }
            .padding(12.dp),
    ) {
        val outerLeft = size.width * 0.12f
        val outerTop = size.height * 0.12f
        val outerSize = Size(size.width * 0.76f, size.height * 0.74f)
        drawRoundRect(InteractiveGreen.copy(alpha = 0.34f), Offset(outerLeft, outerTop), outerSize, 28.dp.toPx(), style = Stroke(8.dp.toPx()))
        drawRoundRect(InteractiveBlue.copy(alpha = 0.18f), Offset(outerLeft + 8.dp.toPx(), outerTop + 8.dp.toPx()), Size(outerSize.width - 16.dp.toPx(), outerSize.height - 16.dp.toPx()), 23.dp.toPx())
        drawOval(InteractivePurple.copy(alpha = 0.26f), Offset(size.width * 0.34f, size.height * 0.30f), Size(size.width * 0.44f, size.height * 0.39f))
        drawCircle(InteractiveYellow.copy(alpha = 0.82f), size.width * 0.09f, Offset(size.width * 0.47f, size.height * 0.43f))
        repeat(5) { index ->
            val x = size.width * (0.28f + index * 0.11f)
            val y = size.height * if (index % 2 == 0) 0.67f else 0.74f
            drawOval(InteractiveGreen, Offset(x, y), Size(26.dp.toPx(), 12.dp.toPx()))
        }

        CellPart.entries.forEach { part ->
            val center = Offset(part.normalizedX * size.width, part.normalizedY * size.height)
            val isSelected = part == selected
            drawCircle(if (isSelected) InteractiveRed else InteractiveWhite, if (isSelected) 8.dp.toPx() else 5.dp.toPx(), center)
            if (isSelected) drawCircle(InteractiveRed.copy(alpha = 0.42f), 15.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }

        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 12.sp.toPx()
            color = InteractiveMuted.toArgb()
        }
        drawContext.canvas.nativeCanvas.drawText("教学示意图 · 非真实比例", 14.dp.toPx(), 20.dp.toPx(), paint)
    }
    Spacer(Modifier.height(14.dp))

    CellPart.entries.chunked(2).forEach { parts ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { part ->
                LabTextChoice(part.label, part == selected, Modifier.weight(1f)) { selectedName = part.name }
            }
            if (parts.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
    }
    Spacer(Modifier.height(6.dp))
    LabResultCard(selected.label, selected.description, "当前只展示教材常见结构名称，不展开更细的细胞器机制。", InteractiveYellow)
}

@Composable
private fun LabNumberInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .background(InteractivePanel, RoundedCornerShape(13.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(13.dp))
            .padding(13.dp),
    ) {
        Text(label, color = InteractiveMuted, fontSize = 12.sp)
        Spacer(Modifier.height(7.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = InteractiveWhite, fontSize = 23.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(InteractiveBlue),
            singleLine = true,
        )
    }
}

@Composable
private fun LabIntegerInput(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    LabNumberInput(label, value, modifier, onValueChange)
}

@Composable
private fun LabSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractivePanel, RoundedCornerShape(13.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(13.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = InteractiveMuted, fontSize = 13.sp)
            Text("${value.roundToInt()}°", color = InteractiveBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun LabTextChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .background(if (selected) InteractiveYellow.copy(alpha = 0.12f) else InteractivePanel, RoundedCornerShape(11.dp))
            .border(1.dp, if (selected) InteractiveYellow else InteractiveLine, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) InteractiveYellow else InteractiveMuted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun LabResultCard(title: String, main: String, detail: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(15.dp))
            .border(1.dp, color.copy(alpha = 0.48f), RoundedCornerShape(15.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(main, color = InteractiveWhite, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium)
        Text(detail, color = InteractiveMuted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

private fun filteredNumber(input: String): String {
    val normalized = input.replace('−', '-')
    if (normalized.length > 8) return normalized.take(8)
    return if (normalized.matches(Regex("-?\\d*(\\.\\d*)?"))) normalized else normalized.dropLast(1)
}

private fun filteredCoefficient(input: String): String = input.filter(Char::isDigit).take(1)

private fun signedImaginary(value: Double): String = when {
    value > 0 -> "+ ${formatLabNumber(value)}i"
    value < 0 -> "- ${formatLabNumber(abs(value))}i"
    else -> "+ 0i"
}

private fun formatLabNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 1000.0) / 1000.0
    return if (abs(rounded - rounded.toLong()) < 1e-9) rounded.toLong().toString() else rounded.toString().trimEnd('0').trimEnd('.')
}
