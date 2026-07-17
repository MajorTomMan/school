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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.lab.CellPart
import com.majortomman.school.learning.lab.ComplexValue
import com.majortomman.school.learning.lab.OrthographicProjector
import com.majortomman.school.learning.lab.Point3D
import com.majortomman.school.learning.lab.WaterEquationDerivation
import kotlin.math.abs
import kotlin.math.roundToInt

private const val COMPLEX_MIN = -5.0
private const val COMPLEX_MAX = 5.0
private const val MAX_CHEMICAL_COEFFICIENT = 4

@Composable
internal fun ComplexPlaneLabSample() {
    var realText by rememberSaveable { mutableStateOf("3") }
    var imaginaryText by rememberSaveable { mutableStateOf("2") }
    val value = ComplexValue(realText.toDoubleOrNull() ?: 0.0, imaginaryText.toDoubleOrNull() ?: 0.0)

    SectionTitle("复数与复平面", InteractivePurple)
    Spacer(Modifier.height(12.dp))
    Text(
        "当前样板只使用实部、虚部和复平面。输入范围限制为 -5～5，避免点超出坐标图。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ScienceNumberInput("实部 a", realText, "-5～5", Modifier.weight(1f)) {
            realText = filteredBoundedNumber(it, COMPLEX_MIN, COMPLEX_MAX)
        }
        ScienceNumberInput("虚部 b", imaginaryText, "-5～5", Modifier.weight(1f)) {
            imaginaryText = filteredBoundedNumber(it, COMPLEX_MIN, COMPLEX_MAX)
        }
    }
    Spacer(Modifier.height(22.dp))
    ComplexPlaneCanvas(value)
    Spacer(Modifier.height(20.dp))
    ScienceResultBlock(
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
            .background(InteractivePanel.copy(alpha = 0.26f)),
    ) {
        val range = 5f
        val left = 28.dp.toPx()
        val right = size.width - 18.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        fun sx(x: Double): Float = left + ((x.toFloat() + range) / (range * 2f)) * (right - left)
        fun sy(y: Double): Float = bottom - ((y.toFloat() + range) / (range * 2f)) * (bottom - top)

        for (grid in -5..5) {
            drawLine(
                InteractiveLine.copy(alpha = 0.55f),
                Offset(sx(grid.toDouble()), top),
                Offset(sx(grid.toDouble()), bottom),
                1.dp.toPx(),
            )
            drawLine(
                InteractiveLine.copy(alpha = 0.55f),
                Offset(left, sy(grid.toDouble())),
                Offset(right, sy(grid.toDouble())),
                1.dp.toPx(),
            )
        }
        drawLine(
            InteractiveWhite.copy(alpha = 0.72f),
            Offset(left, sy(0.0)),
            Offset(right, sy(0.0)),
            2.dp.toPx(),
            StrokeCap.Round,
        )
        drawLine(
            InteractiveWhite.copy(alpha = 0.72f),
            Offset(sx(0.0), top),
            Offset(sx(0.0), bottom),
            2.dp.toPx(),
            StrokeCap.Round,
        )

        val center = Offset(sx(value.real), sy(value.imaginary))
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
internal fun Coordinate3DLabSample() {
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
    Spacer(Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ScienceNumberInput("x", xText, null, Modifier.weight(1f)) { xText = filteredNumber(it) }
        ScienceNumberInput("y", yText, null, Modifier.weight(1f)) { yText = filteredNumber(it) }
        ScienceNumberInput("z", zText, null, Modifier.weight(1f)) { zText = filteredNumber(it) }
    }
    Spacer(Modifier.height(22.dp))
    Coordinate3DCanvas(point, yaw.toDouble(), pitch.toDouble())
    Spacer(Modifier.height(20.dp))
    ScienceSlider("水平视角", yaw, -70f..70f) { yaw = it }
    Spacer(Modifier.height(14.dp))
    ScienceSlider("俯仰视角", pitch, 5f..65f) { pitch = it }
    Spacer(Modifier.height(20.dp))
    ScienceResultBlock(
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
            .background(InteractivePanel.copy(alpha = 0.26f)),
    ) {
        val center = Offset(size.width * 0.50f, size.height * 0.58f)
        val scale = minOf(size.width, size.height) / 10f
        fun screen(p: Point3D): Offset {
            val projected = OrthographicProjector.project(p, yaw, pitch)
            return Offset(center.x + projected.x.toFloat() * scale, center.y - projected.y.toFloat() * scale)
        }

        val origin = screen(Point3D(0.0, 0.0, 0.0))
        val xAxis = screen(Point3D(4.0, 0.0, 0.0))
        val yAxis = screen(Point3D(0.0, 4.0, 0.0))
        val zAxis = screen(Point3D(0.0, 0.0, 4.0))
        drawLine(InteractiveRed, origin, xAxis, 3.dp.toPx(), StrokeCap.Round)
        drawLine(InteractiveGreen, origin, yAxis, 3.dp.toPx(), StrokeCap.Round)
        drawLine(InteractiveBlue, origin, zAxis, 3.dp.toPx(), StrokeCap.Round)

        for (grid in -4..4) {
            drawLine(
                InteractiveLine.copy(alpha = 0.48f),
                screen(Point3D(grid.toDouble(), -4.0, 0.0)),
                screen(Point3D(grid.toDouble(), 4.0, 0.0)),
                1.dp.toPx(),
            )
            drawLine(
                InteractiveLine.copy(alpha = 0.48f),
                screen(Point3D(-4.0, grid.toDouble(), 0.0)),
                screen(Point3D(4.0, grid.toDouble(), 0.0)),
                1.dp.toPx(),
            )
        }

        val clamped = Point3D(
            point.x.coerceIn(-4.0, 4.0),
            point.y.coerceIn(-4.0, 4.0),
            point.z.coerceIn(-4.0, 4.0),
        )
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
        paint.color = InteractiveRed.toArgb()
        drawContext.canvas.nativeCanvas.drawText("x", xAxis.x, xAxis.y, paint)
        paint.color = InteractiveGreen.toArgb()
        drawContext.canvas.nativeCanvas.drawText("y", yAxis.x, yAxis.y, paint)
        paint.color = InteractiveBlue.toArgb()
        drawContext.canvas.nativeCanvas.drawText("z", zAxis.x, zAxis.y, paint)
        paint.color = InteractivePurple.toArgb()
        drawContext.canvas.nativeCanvas.drawText("P", p.x + 9.dp.toPx(), p.y - 9.dp.toPx(), paint)
    }
}

@Composable
internal fun ChemicalBalanceLabSample() {
    var h2Text by rememberSaveable { mutableStateOf("2") }
    var o2Text by rememberSaveable { mutableStateOf("1") }
    val derivation = WaterEquationDerivation(
        hydrogenCoefficient = h2Text.toIntOrNull() ?: 0,
        oxygenCoefficient = o2Text.toIntOrNull() ?: 0,
    )

    SectionTitle("化学方程式自动推导", InteractiveGreen)
    Spacer(Modifier.height(12.dp))
    Text(
        "只填写方程式左侧的 H₂ 与 O₂ 系数。系统根据氢、氧原子守恒自动生成右侧 H₂O；左侧比例不成立时会说明原因。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ScienceNumberInput("左侧 H₂ 系数", h2Text, "1～4", Modifier.weight(1f)) {
            h2Text = filteredCoefficient(it, MAX_CHEMICAL_COEFFICIENT)
        }
        ScienceNumberInput("左侧 O₂ 系数", o2Text, "1～4", Modifier.weight(1f)) {
            o2Text = filteredCoefficient(it, MAX_CHEMICAL_COEFFICIENT)
        }
    }
    Spacer(Modifier.height(18.dp))
    AutoChemicalProductBlock(derivation)
    Spacer(Modifier.height(22.dp))
    ChemicalEquationCanvas(derivation)
    Spacer(Modifier.height(20.dp))

    val statusColor = if (derivation.isValid) InteractiveGreen else InteractiveYellow
    val leftExpression = "${chemicalTerm(derivation.hydrogenCoefficient, "H₂")} + ${chemicalTerm(derivation.oxygenCoefficient, "O₂")}" 
    val equation = if (derivation.isValid) {
        "$leftExpression → ${chemicalTerm(derivation.waterCoefficient ?: 0, "H₂O")}" 
    } else {
        "$leftExpression → 无合法的纯 H₂O 右侧"
    }
    val detail = derivation.products?.let { products ->
        "反应物 H=${derivation.reactants.hydrogen}、O=${derivation.reactants.oxygen}；生成物 H=${products.hydrogen}、O=${products.oxygen}。"
    } ?: derivation.explanation

    ScienceResultBlock(
        title = if (derivation.isValid) "右侧已自动生成" else "左侧比例需要调整",
        main = equation,
        detail = detail,
        color = statusColor,
    )
}

@Composable
private fun AutoChemicalProductBlock(derivation: WaterEquationDerivation) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("自动生成右侧", color = InteractiveMuted, fontSize = 13.sp)
            Text(
                if (derivation.isValid) "已通过守恒检查" else "等待合法左侧比例",
                color = if (derivation.isValid) InteractiveGreen else InteractiveYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = derivation.waterCoefficient?.let { coefficient -> chemicalTerm(coefficient, "H₂O") } ?: "—",
            color = InteractiveWhite,
            fontSize = 25.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(10.dp))
        ScienceDivider()
    }
}

@Composable
private fun ChemicalEquationCanvas(derivation: WaterEquationDerivation) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .background(InteractivePanel.copy(alpha = 0.26f)),
    ) {
        val mid = size.width / 2f
        val sidePadding = 18.dp.toPx()
        val halfWidth = mid - sidePadding * 2f
        val leftStart = sidePadding
        val rightStart = mid + sidePadding

        val headingPaint = Paint().apply {
            isAntiAlias = true
            textSize = 14.sp.toPx()
            color = InteractiveMuted.toArgb()
            textAlign = Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText("反应物", leftStart + halfWidth / 2f, 28.dp.toPx(), headingPaint)
        drawContext.canvas.nativeCanvas.drawText("自动生成物", rightStart + halfWidth / 2f, 28.dp.toPx(), headingPaint)

        val formulaPaint = Paint().apply {
            isAntiAlias = true
            textSize = 13.sp.toPx()
            color = InteractiveWhite.toArgb()
        }
        drawContext.canvas.nativeCanvas.drawText("H₂ × ${derivation.hydrogenCoefficient}", leftStart, 58.dp.toPx(), formulaPaint)
        drawContext.canvas.nativeCanvas.drawText("O₂ × ${derivation.oxygenCoefficient}", leftStart, 178.dp.toPx(), formulaPaint)
        drawContext.canvas.nativeCanvas.drawText(
            derivation.waterCoefficient?.let { "H₂O × $it" } ?: "H₂O × ?",
            rightStart,
            58.dp.toPx(),
            formulaPaint,
        )

        fun gridPoint(index: Int, startX: Float, startY: Float, areaWidth: Float, rowGap: Float): Offset {
            val column = index % 2
            val row = index / 2
            return Offset(
                x = startX + areaWidth * if (column == 0) 0.28f else 0.72f,
                y = startY + row * rowGap,
            )
        }

        repeat(derivation.hydrogenCoefficient.coerceIn(0, MAX_CHEMICAL_COEFFICIENT)) { index ->
            drawDiatomicMolecule(
                center = gridPoint(index, leftStart, 92.dp.toPx(), halfWidth, 42.dp.toPx()),
                atomColor = InteractiveBlue,
                label = "H",
            )
        }
        repeat(derivation.oxygenCoefficient.coerceIn(0, MAX_CHEMICAL_COEFFICIENT)) { index ->
            drawDiatomicMolecule(
                center = gridPoint(index, leftStart, 212.dp.toPx(), halfWidth, 42.dp.toPx()),
                atomColor = InteractiveRed,
                label = "O",
            )
        }
        repeat((derivation.waterCoefficient ?: 0).coerceIn(0, MAX_CHEMICAL_COEFFICIENT)) { index ->
            drawWaterMolecule(
                center = gridPoint(index, rightStart, 108.dp.toPx(), halfWidth, 72.dp.toPx()),
            )
        }

        if (!derivation.isValid) {
            val invalidPaint = Paint().apply {
                isAntiAlias = true
                textSize = 18.sp.toPx()
                color = InteractiveYellow.toArgb()
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawContext.canvas.nativeCanvas.drawText("比例需为 2:1", rightStart + halfWidth / 2f, 155.dp.toPx(), invalidPaint)
        }

        val arrowPaint = Paint().apply {
            isAntiAlias = true
            textSize = 30.sp.toPx()
            color = (if (derivation.isValid) InteractiveGreen else InteractiveYellow).toArgb()
            textAlign = Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText("→", mid, 154.dp.toPx(), arrowPaint)

        val countPaint = Paint().apply {
            isAntiAlias = true
            textSize = 13.sp.toPx()
            color = InteractiveMuted.toArgb()
            textAlign = Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(
            "H ${derivation.reactants.hydrogen}  ·  O ${derivation.reactants.oxygen}",
            leftStart + halfWidth / 2f,
            size.height - 18.dp.toPx(),
            countPaint,
        )
        val productCountText = derivation.products?.let { products ->
            "H ${products.hydrogen}  ·  O ${products.oxygen}"
        } ?: "H —  ·  O —"
        drawContext.canvas.nativeCanvas.drawText(
            productCountText,
            rightStart + halfWidth / 2f,
            size.height - 18.dp.toPx(),
            countPaint,
        )
    }
}

private fun DrawScope.drawDiatomicMolecule(center: Offset, atomColor: Color, label: String) {
    val radius = 11.dp.toPx()
    val left = Offset(center.x - 10.dp.toPx(), center.y)
    val right = Offset(center.x + 10.dp.toPx(), center.y)
    drawLine(InteractiveWhite.copy(alpha = 0.55f), left, right, 3.dp.toPx(), StrokeCap.Round)
    drawAtom(left, radius, atomColor, label)
    drawAtom(right, radius, atomColor, label)
}

private fun DrawScope.drawWaterMolecule(center: Offset) {
    val oxygen = Offset(center.x, center.y - 5.dp.toPx())
    val leftHydrogen = Offset(center.x - 14.dp.toPx(), center.y + 13.dp.toPx())
    val rightHydrogen = Offset(center.x + 14.dp.toPx(), center.y + 13.dp.toPx())
    drawLine(InteractiveWhite.copy(alpha = 0.55f), oxygen, leftHydrogen, 3.dp.toPx(), StrokeCap.Round)
    drawLine(InteractiveWhite.copy(alpha = 0.55f), oxygen, rightHydrogen, 3.dp.toPx(), StrokeCap.Round)
    drawAtom(oxygen, 12.dp.toPx(), InteractiveRed, "O")
    drawAtom(leftHydrogen, 9.dp.toPx(), InteractiveBlue, "H")
    drawAtom(rightHydrogen, 9.dp.toPx(), InteractiveBlue, "H")
}

private fun DrawScope.drawAtom(center: Offset, radius: Float, color: Color, label: String) {
    drawCircle(color.copy(alpha = 0.95f), radius, center)
    drawCircle(InteractiveWhite.copy(alpha = 0.34f), radius, center, style = Stroke(1.dp.toPx()))
    val paint = Paint().apply {
        isAntiAlias = true
        textSize = 9.sp.toPx()
        this.color = InteractiveBlack.toArgb()
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(label, center.x, baseline, paint)
}

@Composable
internal fun BiologyCellLabSample() {
    var selectedName by rememberSaveable { mutableStateOf(CellPart.NUCLEUS.name) }
    val selected = CellPart.valueOf(selectedName)

    SectionTitle("植物细胞结构标注", InteractiveYellow)
    Spacer(Modifier.height(12.dp))
    Text(
        "选中结构后，整个结构会高亮，并用引线标出名称。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "从外到内：细胞壁 → 细胞膜 → 细胞质；细胞核、液泡和叶绿体位于细胞内部。",
        color = InteractiveWhite.copy(alpha = 0.72f),
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(20.dp))

    PlantCellCanvas(selected)
    Spacer(Modifier.height(20.dp))

    CellPart.entries.chunked(2).forEach { parts ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            parts.forEach { part ->
                ScienceTextChoice(part.label, part == selected, Modifier.weight(1f)) { selectedName = part.name }
            }
            if (parts.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(18.dp))
    ScienceResultBlock(
        title = selected.label,
        main = selected.description,
        detail = "教学示意图，非真实比例；颜色只用于区分结构。",
        color = InteractiveYellow,
    )
}

@Composable
private fun PlantCellCanvas(selected: CellPart) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp)
            .background(InteractivePanel.copy(alpha = 0.26f)),
    ) {
        val cellLeft = size.width * 0.13f
        val cellTop = size.height * 0.16f
        val cellSize = Size(size.width * 0.72f, size.height * 0.70f)
        val wallRadius = 28.dp.toPx()
        val membraneInset = 12.dp.toPx()
        val membraneLeft = cellLeft + membraneInset
        val membraneTop = cellTop + membraneInset
        val membraneSize = Size(cellSize.width - membraneInset * 2f, cellSize.height - membraneInset * 2f)

        drawRoundRect(
            color = InteractiveBlue.copy(alpha = 0.15f),
            topLeft = Offset(membraneLeft, membraneTop),
            size = membraneSize,
            cornerRadius = CornerRadius(20.dp.toPx()),
        )
        drawRoundRect(
            color = InteractiveGreen.copy(alpha = 0.56f),
            topLeft = Offset(cellLeft, cellTop),
            size = cellSize,
            cornerRadius = CornerRadius(wallRadius),
            style = Stroke(10.dp.toPx()),
        )
        drawRoundRect(
            color = InteractiveWhite.copy(alpha = 0.72f),
            topLeft = Offset(membraneLeft, membraneTop),
            size = membraneSize,
            cornerRadius = CornerRadius(20.dp.toPx()),
            style = Stroke(2.dp.toPx()),
        )

        val vacuoleTopLeft = Offset(size.width * 0.42f, size.height * 0.30f)
        val vacuoleSize = Size(size.width * 0.31f, size.height * 0.35f)
        drawRoundRect(
            color = InteractivePurple.copy(alpha = 0.24f),
            topLeft = vacuoleTopLeft,
            size = vacuoleSize,
            cornerRadius = CornerRadius(34.dp.toPx()),
        )
        drawRoundRect(
            color = InteractivePurple.copy(alpha = 0.55f),
            topLeft = vacuoleTopLeft,
            size = vacuoleSize,
            cornerRadius = CornerRadius(34.dp.toPx()),
            style = Stroke(2.dp.toPx()),
        )

        val nucleusCenter = Offset(size.width * 0.34f, size.height * 0.42f)
        val nucleusRadius = size.width * 0.085f
        drawCircle(InteractiveYellow.copy(alpha = 0.82f), nucleusRadius, nucleusCenter)
        drawCircle(InteractiveRed.copy(alpha = 0.72f), nucleusRadius * 0.34f, nucleusCenter)
        drawCircle(InteractiveWhite.copy(alpha = 0.42f), nucleusRadius, nucleusCenter, style = Stroke(2.dp.toPx()))

        val chloroplasts = listOf(
            Offset(size.width * 0.31f, size.height * 0.69f),
            Offset(size.width * 0.45f, size.height * 0.74f),
            Offset(size.width * 0.59f, size.height * 0.70f),
            Offset(size.width * 0.70f, size.height * 0.75f),
        )
        chloroplasts.forEach { center ->
            val topLeft = Offset(center.x - 15.dp.toPx(), center.y - 7.dp.toPx())
            val chloroplastSize = Size(30.dp.toPx(), 14.dp.toPx())
            drawOval(InteractiveGreen, topLeft, chloroplastSize)
            drawLine(
                InteractiveBlack.copy(alpha = 0.48f),
                Offset(center.x - 8.dp.toPx(), center.y),
                Offset(center.x + 8.dp.toPx(), center.y),
                1.dp.toPx(),
            )
        }

        when (selected) {
            CellPart.CELL_WALL -> drawRoundRect(
                color = InteractiveYellow,
                topLeft = Offset(cellLeft, cellTop),
                size = cellSize,
                cornerRadius = CornerRadius(wallRadius),
                style = Stroke(4.dp.toPx()),
            )
            CellPart.CELL_MEMBRANE -> drawRoundRect(
                color = InteractiveYellow,
                topLeft = Offset(membraneLeft, membraneTop),
                size = membraneSize,
                cornerRadius = CornerRadius(20.dp.toPx()),
                style = Stroke(4.dp.toPx()),
            )
            CellPart.CYTOPLASM -> drawRoundRect(
                color = InteractiveYellow.copy(alpha = 0.16f),
                topLeft = Offset(membraneLeft + 3.dp.toPx(), membraneTop + 3.dp.toPx()),
                size = Size(membraneSize.width - 6.dp.toPx(), membraneSize.height - 6.dp.toPx()),
                cornerRadius = CornerRadius(18.dp.toPx()),
            )
            CellPart.NUCLEUS -> drawCircle(
                color = InteractiveYellow,
                radius = nucleusRadius + 5.dp.toPx(),
                center = nucleusCenter,
                style = Stroke(4.dp.toPx()),
            )
            CellPart.VACUOLE -> drawRoundRect(
                color = InteractiveYellow,
                topLeft = vacuoleTopLeft,
                size = vacuoleSize,
                cornerRadius = CornerRadius(34.dp.toPx()),
                style = Stroke(4.dp.toPx()),
            )
            CellPart.CHLOROPLAST -> chloroplasts.forEach { center ->
                drawOval(
                    color = InteractiveYellow,
                    topLeft = Offset(center.x - 18.dp.toPx(), center.y - 10.dp.toPx()),
                    size = Size(36.dp.toPx(), 20.dp.toPx()),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }

        val anchor = Offset(selected.normalizedX * size.width, selected.normalizedY * size.height)
        val labelEnd = Offset(size.width * 0.78f, 54.dp.toPx())
        val elbow = Offset(labelEnd.x - 38.dp.toPx(), labelEnd.y)
        drawLine(InteractiveYellow, anchor, elbow, 2.dp.toPx(), StrokeCap.Round)
        drawLine(InteractiveYellow, elbow, labelEnd, 2.dp.toPx(), StrokeCap.Round)

        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = 14.sp.toPx()
            color = InteractiveYellow.toArgb()
            textAlign = Paint.Align.RIGHT
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText(selected.label, labelEnd.x, labelEnd.y - 7.dp.toPx(), labelPaint)

        val notePaint = Paint().apply {
            isAntiAlias = true
            textSize = 11.sp.toPx()
            color = InteractiveMuted.toArgb()
        }
        drawContext.canvas.nativeCanvas.drawText("教学示意图 · 非真实比例", 12.dp.toPx(), 22.dp.toPx(), notePaint)
    }
}

@Composable
private fun ScienceNumberInput(
    label: String,
    value: String,
    rangeHint: String?,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = InteractiveMuted, fontSize = 12.sp)
            if (rangeHint != null) {
                Text(rangeHint, color = InteractiveMuted.copy(alpha = 0.72f), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = InteractiveWhite, fontSize = 23.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(InteractiveBlue),
            singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        ScienceDivider()
    }
}

@Composable
private fun ScienceSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = InteractiveMuted, fontSize = 13.sp)
            Text("${value.roundToInt()}°", color = InteractiveBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
        ScienceDivider()
    }
}

@Composable
private fun ScienceTextChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) InteractiveYellow else InteractiveMuted,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(9.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (selected) 2.dp else 1.dp)
                .background(if (selected) InteractiveYellow else InteractiveLine),
        )
    }
}

@Composable
private fun ScienceResultBlock(title: String, main: String, detail: String, color: Color) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.48f)))
        Spacer(Modifier.height(6.dp))
        Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(main, color = InteractiveWhite, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
        Text(detail, color = InteractiveMuted, fontSize = 14.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun ScienceDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
}

private fun filteredNumber(input: String): String {
    val normalized = input.replace('−', '-')
    if (normalized.length > 8) return normalized.take(8)
    return if (normalized.matches(Regex("-?\\d*(\\.\\d*)?"))) normalized else normalized.dropLast(1)
}

private fun filteredBoundedNumber(input: String, min: Double, max: Double): String {
    val normalized = input.replace('−', '-').take(7)
    if (!normalized.matches(Regex("-?\\d*(\\.\\d*)?"))) return normalized.dropLast(1)
    if (normalized.isEmpty() || normalized == "-" || normalized == "." || normalized == "-.") return normalized
    val parsed = normalized.toDoubleOrNull() ?: return normalized
    return when {
        parsed < min -> formatLabNumber(min)
        parsed > max -> formatLabNumber(max)
        else -> normalized
    }
}

private fun filteredCoefficient(input: String, max: Int): String {
    val digits = input.filter(Char::isDigit)
    if (digits.isEmpty()) return ""
    return (digits.toIntOrNull() ?: 1).coerceIn(1, max).toString()
}

private fun chemicalTerm(coefficient: Int, formula: String): String = when {
    coefficient <= 0 -> "—$formula"
    coefficient == 1 -> formula
    else -> "$coefficient$formula"
}

private fun signedImaginary(value: Double): String = when {
    value > 0 -> "+ ${formatLabNumber(value)}i"
    value < 0 -> "- ${formatLabNumber(abs(value))}i"
    else -> "+ 0i"
}

private fun formatLabNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 1000.0) / 1000.0
    return if (abs(rounded - rounded.toLong()) < 1e-9) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
