package com.majortomman.school.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.Lesson
import com.majortomman.school.data.material.InstalledMaterialPack
import com.majortomman.school.learning.course.RationalLessonPage
import com.majortomman.school.learning.course.RationalNumbersCourseFactory
import com.majortomman.school.learning.course.RationalVisualizationKind
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun RationalNumbersLessonScreen(
    lesson: Lesson,
    installedMaterial: InstalledMaterialPack,
    nextLessonTitle: String?,
    onOpenTextbook: (Int) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val pages = remember(lesson.title, lesson.textbookPages) {
        RationalNumbersCourseFactory.pagesFor(lesson.title, lesson.textbookPages)
    }
    if (pages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val currentPage = pages[pagerState.currentPage]
    val canGoBack = pagerState.currentPage > 0
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InteractiveBlack)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagerTextAction("返回", InteractiveMuted, onBack)
            Text("有理数", color = InteractiveWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            PagerTextAction("教材第 ${currentPage.sourcePage} 页", InteractiveYellow) {
                if (installedMaterial.pdfFile.isFile) onOpenTextbook(currentPage.sourcePage)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { index ->
            RationalLessonPageContent(
                page = pages[index],
                pageNumber = index + 1,
                pageCount = pages.size,
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagerNavigationAction(
                label = "上一页",
                enabled = canGoBack,
                alignment = TextAlign.Start,
            ) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
            Text(
                "${pagerState.currentPage + 1} / ${pages.size}",
                modifier = Modifier.width(72.dp),
                color = InteractiveMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            PagerNavigationAction(
                label = if (isLastPage) nextLessonTitle?.let { "完成并继续" } ?: "完成" else "下一页",
                enabled = true,
                alignment = TextAlign.End,
            ) {
                if (isLastPage) {
                    onComplete()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            }
        }
    }
}

@Composable
private fun RationalLessonPageContent(
    page: RationalLessonPage,
    pageNumber: Int,
    pageCount: Int,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        val visualHeight = when {
            maxHeight < 520.dp -> 150.dp
            maxHeight < 640.dp -> 190.dp
            maxHeight < 760.dp -> 230.dp
            else -> 270.dp
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Text(page.section, color = InteractiveBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                page.title,
                color = InteractiveWhite,
                fontSize = if (maxHeight < 560.dp) 27.sp else 32.sp,
                lineHeight = if (maxHeight < 560.dp) 33.sp else 39.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            page.paragraphs.forEachIndexed { index, paragraph ->
                Text(
                    paragraph,
                    color = InteractiveWhite.copy(alpha = 0.84f),
                    fontSize = if (maxHeight < 560.dp) 14.sp else 16.sp,
                    lineHeight = if (maxHeight < 560.dp) 21.sp else 25.sp,
                )
                if (index != page.paragraphs.lastIndex) Spacer(Modifier.height(7.dp))
            }
            if (!page.formula.isNullOrBlank()) {
                Spacer(Modifier.height(13.dp))
                Text(
                    page.formula,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    color = InteractiveYellow,
                    fontSize = if (maxHeight < 560.dp) 18.sp else 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            RationalVisualization(
                page = page,
                modifier = Modifier.fillMaxWidth().height(visualHeight),
            )
            if (!page.conclusion.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(InteractiveBlue.copy(alpha = 0.68f)))
                Spacer(Modifier.height(8.dp))
                Text(
                    page.conclusion,
                    color = InteractiveWhite,
                    fontSize = if (maxHeight < 560.dp) 14.sp else 16.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "第 $pageNumber 页，共 $pageCount 页",
                modifier = Modifier.fillMaxWidth(),
                color = InteractiveMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun RationalVisualization(page: RationalLessonPage, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(InteractivePanel.copy(alpha = 0.52f))
            .border(1.dp, InteractiveLine),
        contentAlignment = Alignment.Center,
    ) {
        when (page.visualization) {
            RationalVisualizationKind.OPPOSITE_QUANTITIES -> OppositeQuantitiesVisual()
            RationalVisualizationKind.RATIONAL_CLASSIFICATION -> RationalClassificationVisual()
            RationalVisualizationKind.NUMBER_LINE -> NumberLineVisual()
            RationalVisualizationKind.OPPOSITE_NUMBERS -> OppositeNumbersVisual()
            RationalVisualizationKind.ABSOLUTE_VALUE -> AbsoluteValueVisual()
            RationalVisualizationKind.NUMBER_COMPARISON -> NumberComparisonVisual()
            RationalVisualizationKind.ADDITION_PROCESS -> AdditionProcessVisual()
            RationalVisualizationKind.SUBTRACTION_TRANSFORM -> ExpressionStepsVisual(
                listOf("9 − (−8)", "9 + 8", "17"),
                listOf("原式", "加上相反数", "结果"),
            )
            RationalVisualizationKind.MULTIPLICATION_SIGN -> MultiplicationSignVisual()
            RationalVisualizationKind.DIVISION_TRANSFORM -> ExpressionStepsVisual(
                listOf("(−12) ÷ 3", "(−12) × 1/3", "−4"),
                listOf("原式", "乘除数的倒数", "结果"),
            )
            RationalVisualizationKind.POWER_PROCESS -> PowerProcessVisual()
            RationalVisualizationKind.HISTORY -> HistoryVisual()
        }
    }
}

@Composable
private fun OppositeQuantitiesVisual() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        OppositeRow("零上 5 ℃", "零下 5 ℃")
        OppositeRow("增加 12", "减少 12")
        OppositeRow("收入 300 元", "支出 300 元")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("规定为正", color = InteractiveBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("基准 0", color = InteractiveWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("规定为负", color = InteractiveYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OppositeRow(positive: String, negative: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(positive, modifier = Modifier.weight(1f), color = InteractiveBlue, textAlign = TextAlign.End, fontSize = 14.sp)
        Text("↔", color = InteractiveMuted, fontSize = 18.sp)
        Text(negative, modifier = Modifier.weight(1f), color = InteractiveYellow, fontSize = 14.sp)
    }
}

@Composable
private fun RationalClassificationVisual() {
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        ClassificationNode("有理数", InteractiveWhite)
        Text("按符号分类", color = InteractiveMuted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClassificationNode("正有理数", InteractiveBlue, Modifier.weight(1f))
            ClassificationNode("0", InteractiveWhite, Modifier.weight(0.55f))
            ClassificationNode("负有理数", InteractiveYellow, Modifier.weight(1f))
        }
        Text("按表示形式分类", color = InteractiveMuted, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ClassificationNode("整数", InteractiveBlue, Modifier.weight(1f))
            ClassificationNode("分数", InteractiveYellow, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ClassificationNode(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.border(1.dp, color.copy(alpha = 0.65f)).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NumberLineVisual() {
    var value by rememberSaveable { mutableStateOf(-2f) }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("数 x", color = InteractiveMuted, fontSize = 12.sp)
            Text(formatNumber(value), color = InteractiveYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = { value = (it * 2f).roundToInt() / 2f }, valueRange = -5f..5f)
        Canvas(Modifier.fillMaxWidth().weight(1f)) { drawNumberLine(listOf(value to InteractiveYellow)) }
    }
}

@Composable
private fun OppositeNumbersVisual() {
    var magnitude by rememberSaveable { mutableStateOf(3f) }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("a", color = InteractiveMuted, fontSize = 12.sp)
            Text("±${formatNumber(magnitude)}", color = InteractiveYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = magnitude, onValueChange = { magnitude = it.roundToInt().toFloat() }, valueRange = 0f..5f)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            drawNumberLine(listOf(magnitude to InteractiveBlue, -magnitude to InteractiveYellow), showSymmetry = true)
        }
    }
}

@Composable
private fun AbsoluteValueVisual() {
    var value by rememberSaveable { mutableStateOf(-4f) }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("x = ${formatNumber(value)}", color = InteractiveMuted, fontSize = 12.sp)
            Text("|x| = ${formatNumber(abs(value))}", color = InteractiveYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = { value = it.roundToInt().toFloat() }, valueRange = -5f..5f)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            drawNumberLine(listOf(value to InteractiveYellow), showDistance = true)
        }
    }
}

@Composable
private fun NumberComparisonVisual() {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text("−4 在 −2 的左边，因此 −4<−2", color = InteractiveWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            drawNumberLine(listOf(-4f to InteractiveYellow, -2f to InteractiveBlue))
        }
        Text("两个负数比较大小，绝对值大的数反而小。", color = InteractiveMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AdditionProcessVisual() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text("3+(−5)", color = InteractiveWhite, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(3) { SignedChip("+", InteractiveBlue) }
            Spacer(Modifier.width(10.dp))
            repeat(5) { SignedChip("−", InteractiveYellow) }
        }
        Text("3 对正负单位相互抵消", color = InteractiveMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(2) { SignedChip("−", InteractiveYellow) }
        }
        Text("剩下 2 个负单位：3+(−5)=−2", color = InteractiveYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SignedChip(symbol: String, color: Color) {
    Box(
        modifier = Modifier.width(28.dp).height(28.dp).border(1.dp, color),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExpressionStepsVisual(steps: List<String>, labels: List<String>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        steps.forEachIndexed { index, expression ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(labels[index], modifier = Modifier.width(92.dp), color = InteractiveMuted, fontSize = 12.sp)
                Text(
                    expression,
                    modifier = Modifier.weight(1f),
                    color = if (index == steps.lastIndex) InteractiveYellow else InteractiveWhite,
                    fontSize = 20.sp,
                    fontWeight = if (index == steps.lastIndex) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            if (index != steps.lastIndex) {
                Text("↓", modifier = Modifier.fillMaxWidth(), color = InteractiveBlue, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun MultiplicationSignVisual() {
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text("乘法符号表", color = InteractiveWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignRuleNode("(+)×(+)", "+", Modifier.weight(1f))
            SignRuleNode("(−)×(−)", "+", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignRuleNode("(+)×(−)", "−", Modifier.weight(1f))
            SignRuleNode("(−)×(+)", "−", Modifier.weight(1f))
        }
        Text("先定符号，再计算绝对值的积。", color = InteractiveMuted, fontSize = 12.sp)
    }
}

@Composable
private fun SignRuleNode(expression: String, result: String, modifier: Modifier) {
    Row(
        modifier = modifier.border(1.dp, InteractiveLine).padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(expression, color = InteractiveWhite, fontSize = 13.sp)
        Text(result, color = if (result == "+") InteractiveBlue else InteractiveYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PowerProcessVisual() {
    var exponent by rememberSaveable { mutableStateOf(3f) }
    val exponentInt = exponent.roundToInt().coerceIn(1, 6)
    val factors = List(exponentInt) { "(−2)" }.joinToString("×")
    val result = (-2.0).let { base ->
        var value = 1.0
        repeat(exponentInt) { value *= base }
        value.toInt()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("指数 n", color = InteractiveMuted, fontSize = 12.sp)
            Text(exponentInt.toString(), color = InteractiveYellow, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Slider(value = exponent, onValueChange = { exponent = it.roundToInt().toFloat() }, valueRange = 1f..6f, steps = 4)
        Text("(−2)$exponentInt", modifier = Modifier.fillMaxWidth(), color = InteractiveWhite, fontSize = 22.sp, textAlign = TextAlign.Center)
        Text(factors, modifier = Modifier.fillMaxWidth(), color = InteractiveBlue, fontSize = 15.sp, textAlign = TextAlign.Center)
        Text(
            "= $result（${if (exponentInt % 2 == 0) "偶数个负因数，结果为正" else "奇数个负因数，结果为负"}）",
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveYellow,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistoryVisual() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        HistoryStep("实际问题", "盈亏、收支和方向需要表示相反的量")
        HistoryStep("筹算表示", "用不同颜色的算筹区别正数和负数")
        HistoryStep("运算法则", "正负数进入统一的加减乘除体系")
        HistoryStep("数的概念", "负数逐渐被作为数接受")
    }
}

@Composable
private fun HistoryStep(title: String, body: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(8.dp).background(InteractiveBlue))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = InteractiveWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(body, color = InteractiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

private fun DrawScope.drawNumberLine(
    points: List<Pair<Float, Color>>,
    showSymmetry: Boolean = false,
    showDistance: Boolean = false,
) {
    val left = 24.dp.toPx()
    val right = size.width - 24.dp.toPx()
    val centerY = size.height * 0.58f
    val zeroX = mapNumberToX(0f, left, right)
    drawLine(InteractiveWhite.copy(alpha = 0.72f), Offset(left, centerY), Offset(right, centerY), 2.dp.toPx(), StrokeCap.Round)
    drawLine(InteractiveWhite.copy(alpha = 0.72f), Offset(right, centerY), Offset(right - 9.dp.toPx(), centerY - 5.dp.toPx()), 2.dp.toPx())
    drawLine(InteractiveWhite.copy(alpha = 0.72f), Offset(right, centerY), Offset(right - 9.dp.toPx(), centerY + 5.dp.toPx()), 2.dp.toPx())
    val paint = Paint().apply {
        textSize = 11.sp.toPx()
        textAlign = Paint.Align.CENTER
    }
    for (number in -5..5) {
        val x = mapNumberToX(number.toFloat(), left, right)
        drawLine(InteractiveLine.copy(alpha = 0.9f), Offset(x, centerY - 7.dp.toPx()), Offset(x, centerY + 7.dp.toPx()), 1.dp.toPx())
        paint.color = InteractiveMuted.toArgb()
        drawContext.canvas.nativeCanvas.drawText(number.toString(), x, centerY + 22.dp.toPx(), paint)
    }
    points.forEach { (number, color) ->
        val x = mapNumberToX(number, left, right)
        if (showDistance) {
            drawLine(color.copy(alpha = 0.55f), Offset(minOf(x, zeroX), centerY - 24.dp.toPx()), Offset(maxOf(x, zeroX), centerY - 24.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(color, 7.dp.toPx(), Offset(x, centerY))
        paint.color = color.toArgb()
        paint.textSize = 14.sp.toPx()
        drawContext.canvas.nativeCanvas.drawText(formatNumber(number), x, centerY - 14.dp.toPx(), paint)
    }
    if (showSymmetry && points.size >= 2) {
        val first = mapNumberToX(points[0].first, left, right)
        val second = mapNumberToX(points[1].first, left, right)
        drawLine(InteractiveBlue.copy(alpha = 0.42f), Offset(minOf(first, second), centerY - 38.dp.toPx()), Offset(maxOf(first, second), centerY - 38.dp.toPx()), 2.dp.toPx())
        drawLine(InteractiveBlue.copy(alpha = 0.42f), Offset(zeroX, centerY - 46.dp.toPx()), Offset(zeroX, centerY - 30.dp.toPx()), 2.dp.toPx())
    }
}

private fun DrawScope.mapNumberToX(number: Float, left: Float, right: Float): Float =
    left + ((number.coerceIn(-5f, 5f) + 5f) / 10f) * (right - left)

private fun formatNumber(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

@Composable
private fun PagerTextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(label, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp), color = color, fontSize = 13.sp)
}

@Composable
private fun PagerNavigationAction(
    label: String,
    enabled: Boolean,
    alignment: TextAlign,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier = Modifier.width(112.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp),
        color = if (enabled) InteractiveBlue else InteractiveMuted.copy(alpha = 0.35f),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = alignment,
    )
}
