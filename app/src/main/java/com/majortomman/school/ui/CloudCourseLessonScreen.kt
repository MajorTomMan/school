package com.majortomman.school.ui

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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.Lesson
import com.majortomman.school.data.material.InstalledMaterialPack
import com.majortomman.school.learning.cloud.CloudCourseRepository
import com.majortomman.school.learning.course.RationalLessonPage
import com.majortomman.school.learning.course.RationalVisualizationKind
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun CloudAwareRationalNumbersLessonScreen(
    lesson: Lesson,
    installedMaterial: InstalledMaterialPack,
    nextLessonTitle: String?,
    onOpenTextbook: (Int) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val cloudRevision by CloudCourseRepository.revision.collectAsState()
    val cloudPages = remember(lesson.title, lesson.textbookPages, cloudRevision) {
        CloudCourseRepository.pagesFor(lesson.title, lesson.textbookPages)
    }
    if (cloudPages.isEmpty()) {
        RationalNumbersLessonScreen(
            lesson = lesson,
            installedMaterial = installedMaterial,
            nextLessonTitle = nextLessonTitle,
            onOpenTextbook = onOpenTextbook,
            onBack = onBack,
            onComplete = onComplete,
        )
        return
    }

    CloudCoursePager(
        pages = cloudPages,
        installedMaterial = installedMaterial,
        nextLessonTitle = nextLessonTitle,
        onOpenTextbook = onOpenTextbook,
        onBack = onBack,
        onComplete = onComplete,
    )
}

@Composable
private fun CloudCoursePager(
    pages: List<RationalLessonPage>,
    installedMaterial: InstalledMaterialPack,
    nextLessonTitle: String?,
    onOpenTextbook: (Int) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val currentPage = pages[pagerState.currentPage]
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
            CloudTextAction("返回", InteractiveMuted, onBack)
            Text(currentPage.section, color = InteractiveWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            CloudTextAction(
                "教材第 ${currentPage.sourcePage} 页",
                if (installedMaterial.pdfFile.isFile) InteractiveYellow else InteractiveMuted,
            ) {
                if (installedMaterial.pdfFile.isFile) onOpenTextbook(currentPage.sourcePage)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            CloudCoursePageContent(pages[index], index + 1, pages.size)
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
            CloudNavigationAction("上一页", pagerState.currentPage > 0, TextAlign.Start) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
            Text(
                "${pagerState.currentPage + 1} / ${pages.size}",
                modifier = Modifier.width(72.dp),
                color = InteractiveMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            CloudNavigationAction(
                if (isLastPage) nextLessonTitle?.let { "完成并继续" } ?: "完成" else "下一页",
                true,
                TextAlign.End,
            ) {
                if (isLastPage) onComplete()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun CloudCoursePageContent(page: RationalLessonPage, pageNumber: Int, pageCount: Int) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
        val compact = maxHeight < 620.dp
        val visualHeight = if (compact) 180.dp else 245.dp
        Column(Modifier.fillMaxSize()) {
            Text(page.section, color = InteractiveBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                page.title,
                color = InteractiveWhite,
                fontSize = if (compact) 27.sp else 32.sp,
                lineHeight = if (compact) 33.sp else 39.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            page.paragraphs.forEachIndexed { index, paragraph ->
                Text(
                    paragraph,
                    color = InteractiveWhite.copy(alpha = 0.84f),
                    fontSize = if (compact) 14.sp else 16.sp,
                    lineHeight = if (compact) 21.sp else 25.sp,
                )
                if (index != page.paragraphs.lastIndex) Spacer(Modifier.height(7.dp))
            }
            if (!page.formula.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    page.formula,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    color = InteractiveYellow,
                    fontSize = if (compact) 18.sp else 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            CloudVisualization(page, Modifier.fillMaxWidth().height(visualHeight))
            if (!page.conclusion.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(InteractiveBlue.copy(alpha = 0.68f)))
                Spacer(Modifier.height(8.dp))
                Text(
                    page.conclusion,
                    color = InteractiveWhite,
                    fontSize = if (compact) 14.sp else 16.sp,
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
private fun CloudVisualization(page: RationalLessonPage, modifier: Modifier) {
    Box(
        modifier = modifier.background(InteractivePanel.copy(alpha = 0.52f)).border(1.dp, InteractiveLine),
        contentAlignment = Alignment.Center,
    ) {
        when (page.visualization) {
            RationalVisualizationKind.OPPOSITE_QUANTITIES -> CloudOppositeQuantities()
            RationalVisualizationKind.RATIONAL_CLASSIFICATION -> CloudClassification()
            RationalVisualizationKind.NUMBER_LINE -> CloudAdjustableNumberLine(NumberLineMode.VALUE)
            RationalVisualizationKind.OPPOSITE_NUMBERS -> CloudAdjustableNumberLine(NumberLineMode.OPPOSITE)
            RationalVisualizationKind.ABSOLUTE_VALUE -> CloudAdjustableNumberLine(NumberLineMode.ABSOLUTE)
            RationalVisualizationKind.NUMBER_COMPARISON -> CloudComparison()
            RationalVisualizationKind.ADDITION_PROCESS -> CloudSignedChips()
            RationalVisualizationKind.SUBTRACTION_TRANSFORM -> CloudExpressionSteps(
                listOf(page.formula ?: "9 − (−8)", "9 + 8", "17"),
            )
            RationalVisualizationKind.MULTIPLICATION_SIGN -> CloudSignRule()
            RationalVisualizationKind.DIVISION_TRANSFORM -> CloudExpressionSteps(
                listOf(page.formula ?: "(−12) ÷ 3", "(−12) × 1/3", "−4"),
            )
            RationalVisualizationKind.POWER_PROCESS -> CloudPower()
            RationalVisualizationKind.HISTORY -> Text(
                "教材内容与练习按原有顺序编排",
                color = InteractiveMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CloudOppositeQuantities() {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        listOf("零上 5 ℃" to "零下 5 ℃", "增加 12" to "减少 12", "收入 300 元" to "支出 300 元")
            .forEach { (positive, negative) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(positive, Modifier.weight(1f), color = InteractiveBlue, textAlign = TextAlign.End)
                    Text("↔", color = InteractiveMuted)
                    Text(negative, Modifier.weight(1f), color = InteractiveYellow)
                }
            }
    }
}

@Composable
private fun CloudClassification() {
    Column(
        Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CloudNode("有理数", InteractiveWhite)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudNode("正有理数", InteractiveBlue, Modifier.weight(1f))
            CloudNode("0", InteractiveWhite, Modifier.weight(0.6f))
            CloudNode("负有理数", InteractiveYellow, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CloudNode("整数", InteractiveBlue, Modifier.weight(1f))
            CloudNode("分数", InteractiveYellow, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CloudNode(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, color.copy(alpha = 0.65f)).padding(9.dp), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

private enum class NumberLineMode { VALUE, OPPOSITE, ABSOLUTE }

@Composable
private fun CloudAdjustableNumberLine(mode: NumberLineMode) {
    var value by rememberSaveable { mutableStateOf(-2f) }
    val points = when (mode) {
        NumberLineMode.VALUE -> listOf(value to InteractiveYellow)
        NumberLineMode.OPPOSITE -> listOf(value to InteractiveBlue, -value to InteractiveYellow)
        NumberLineMode.ABSOLUTE -> listOf(value to InteractiveYellow, 0f to InteractiveWhite)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("数 x", color = InteractiveMuted, fontSize = 12.sp)
            Text(
                when (mode) {
                    NumberLineMode.ABSOLUTE -> "|${cloudNumber(value)}| = ${cloudNumber(abs(value))}"
                    NumberLineMode.OPPOSITE -> "${cloudNumber(value)} 与 ${cloudNumber(-value)}"
                    NumberLineMode.VALUE -> cloudNumber(value)
                },
                color = InteractiveYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(value = value, onValueChange = { value = (it * 2f).roundToInt() / 2f }, valueRange = -5f..5f)
        Canvas(Modifier.fillMaxWidth().weight(1f)) { drawCloudNumberLine(points) }
    }
}

@Composable
private fun CloudComparison() {
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            drawCloudNumberLine(listOf(-4f to InteractiveYellow, -2f to InteractiveBlue))
        }
        Text("−4 在 −2 的左边，因此 −4 < −2", color = InteractiveWhite, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CloudSignedChips() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Text("3 个正单位与 5 个负单位", color = InteractiveWhite, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(3) { Text("＋", color = InteractiveBlue, fontSize = 26.sp) }
            Spacer(Modifier.width(16.dp))
            repeat(5) { Text("−", color = InteractiveYellow, fontSize = 26.sp) }
        }
        Text("抵消 3 对后，剩余 2 个负单位：−2", color = InteractiveYellow, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CloudExpressionSteps(steps: List<String>) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
        steps.distinct().forEachIndexed { index, step ->
            Text(step, color = if (index == steps.lastIndex) InteractiveYellow else InteractiveWhite, fontSize = 21.sp, fontWeight = FontWeight.Medium)
            if (index != steps.lastIndex) Text("↓", color = InteractiveMuted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun CloudSignRule() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        listOf("＋ × ＋ = ＋", "＋ × − = −", "− × ＋ = −", "− × − = ＋").forEach { rule ->
            Text(rule, color = if (rule.endsWith("＋")) InteractiveBlue else InteractiveYellow, fontSize = 18.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CloudPower() {
    var exponent by rememberSaveable { mutableStateOf(3f) }
    val n = exponent.roundToInt().coerceIn(1, 6)
    val value = (1..n).fold(1) { result, _ -> result * -2 }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Slider(value = exponent, onValueChange = { exponent = it.roundToInt().toFloat() }, valueRange = 1f..6f, steps = 4)
        Text("(−2)$n = ${List(n) { "(−2)" }.joinToString(" × ")}", color = InteractiveWhite, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("结果：$value", color = InteractiveYellow, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloudNumberLine(points: List<Pair<Float, Color>>) {
    val left = 22f
    val right = size.width - 22f
    val centerY = size.height * 0.58f
    drawLine(InteractiveWhite.copy(alpha = 0.72f), Offset(left, centerY), Offset(right, centerY), strokeWidth = 3f, cap = StrokeCap.Round)
    for (tick in -5..5) {
        val x = left + (tick + 5) / 10f * (right - left)
        drawLine(InteractiveWhite.copy(alpha = 0.45f), Offset(x, centerY - 8f), Offset(x, centerY + 8f), strokeWidth = 2f)
    }
    points.forEach { (value, color) ->
        val x = left + (value.coerceIn(-5f, 5f) + 5f) / 10f * (right - left)
        drawCircle(color, radius = 8f, center = Offset(x, centerY))
    }
}

private fun cloudNumber(value: Float): String =
    if (value == value.roundToInt().toFloat()) value.roundToInt().toString() else "%.1f".format(value)

@Composable
private fun CloudTextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(label, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp), color = color, fontSize = 13.sp)
}

@Composable
private fun CloudNavigationAction(label: String, enabled: Boolean, alignment: TextAlign, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.width(112.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        color = if (enabled) InteractiveBlue else InteractiveMuted.copy(alpha = 0.35f),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = alignment,
    )
}
