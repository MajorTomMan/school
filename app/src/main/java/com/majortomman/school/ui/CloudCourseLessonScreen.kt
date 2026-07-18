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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.Lesson
import com.majortomman.school.data.material.InstalledMaterialPack
import com.majortomman.school.learning.cloud.CloudCourseRepository
import com.majortomman.school.learning.cloud.CourseSyncManager
import com.majortomman.school.learning.cloud.CourseSyncResult
import com.majortomman.school.learning.course.RationalLessonPage
import com.majortomman.school.learning.course.RationalVisualizationKind
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun CloudCourseLessonScreen(
    lesson: Lesson,
    installedMaterial: InstalledMaterialPack,
    nextLessonTitle: String?,
    onOpenTextbook: (Int) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val revision by CloudCourseRepository.revision.collectAsState()
    val pages = remember(lesson.title, lesson.textbookPages, revision) {
        CloudCourseRepository.pagesFor(lesson.title, lesson.textbookPages)
    }
    if (pages.isEmpty()) {
        CourseDataUnavailableScreen(
            lessonTitle = lesson.title,
            onBack = onBack,
        )
        return
    }

    CloudCoursePager(
        pages = pages,
        installedMaterial = installedMaterial,
        nextLessonTitle = nextLessonTitle,
        onOpenTextbook = onOpenTextbook,
        onBack = onBack,
        onComplete = onComplete,
    )
}

@Composable
private fun CourseDataUnavailableScreen(
    lessonTitle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var syncing by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InteractiveBlack)
            .systemBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        CloudTextAction("返回", InteractiveMuted, onBack)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (syncing) {
                CircularProgressIndicator(color = InteractiveBlue)
            }
            Text(
                "课程数据尚未下载",
                color = InteractiveWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                lessonTitle,
                color = InteractiveMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            status?.let {
                Text(
                    it,
                    color = InteractiveMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
            InteractiveAction(
                label = if (syncing) "正在同步" else "重新同步课程",
                color = InteractiveBlue,
                modifier = Modifier.fillMaxWidth(),
                enabled = !syncing,
            ) {
                syncing = true
                status = null
                scope.launch {
                    status = when (val result = CourseSyncManager.syncOnStartup(context)) {
                        CourseSyncResult.Disabled -> "尚未配置课程清单地址"
                        is CourseSyncResult.Success -> if (result.updatedTextbooks > 0) {
                            "课程数据已更新"
                        } else {
                            "云端没有可用于当前课程的新数据"
                        }
                        is CourseSyncResult.Failed -> result.message
                    }
                    syncing = false
                }
            }
        }
        Spacer(Modifier.height(48.dp))
    }
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
            Text(
                currentPage.section,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                color = InteractiveWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
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
        val hasVisualization = page.visualization !in setOf(
            RationalVisualizationKind.NONE,
            RationalVisualizationKind.HISTORY,
        )
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
            if (hasVisualization) {
                Spacer(Modifier.height(12.dp))
                CloudVisualization(page, Modifier.fillMaxWidth().height(visualHeight))
            }
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
            RationalVisualizationKind.NONE,
            RationalVisualizationKind.HISTORY,
            -> Unit
            RationalVisualizationKind.OPPOSITE_QUANTITIES -> OppositeDirectionVisual()
            RationalVisualizationKind.RATIONAL_CLASSIFICATION -> ClassificationVisual()
            RationalVisualizationKind.NUMBER_LINE -> AdjustableNumberLine(NumberLineMode.VALUE)
            RationalVisualizationKind.OPPOSITE_NUMBERS -> AdjustableNumberLine(NumberLineMode.OPPOSITE)
            RationalVisualizationKind.ABSOLUTE_VALUE -> AdjustableNumberLine(NumberLineMode.ABSOLUTE)
            RationalVisualizationKind.NUMBER_COMPARISON -> ComparisonVisual()
            RationalVisualizationKind.ADDITION_PROCESS -> SignedUnitVisual()
            RationalVisualizationKind.SUBTRACTION_TRANSFORM,
            RationalVisualizationKind.DIVISION_TRANSFORM,
            -> FormulaProcessVisual(page.formula)
            RationalVisualizationKind.MULTIPLICATION_SIGN -> SignRuleVisual()
            RationalVisualizationKind.POWER_PROCESS -> PowerVisual()
        }
    }
}

@Composable
private fun OppositeDirectionVisual() {
    var value by rememberSaveable { mutableStateOf(3f) }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Slider(value = value, onValueChange = { value = it.roundToInt().toFloat() }, valueRange = 1f..10f, steps = 8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("+${value.roundToInt()}", color = InteractiveBlue, fontSize = 24.sp)
            Text("0", color = InteractiveWhite, fontSize = 20.sp)
            Text("−${value.roundToInt()}", color = InteractiveYellow, fontSize = 24.sp)
        }
    }
}

@Composable
private fun ClassificationVisual() {
    Column(
        Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CloudNode("数", InteractiveWhite)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CloudNode("正", InteractiveBlue, Modifier.weight(1f))
            CloudNode("0", InteractiveWhite, Modifier.weight(0.6f))
            CloudNode("负", InteractiveYellow, Modifier.weight(1f))
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
private fun AdjustableNumberLine(mode: NumberLineMode) {
    var value by rememberSaveable { mutableStateOf(-2f) }
    val points = when (mode) {
        NumberLineMode.VALUE -> listOf(value to InteractiveYellow)
        NumberLineMode.OPPOSITE -> listOf(value to InteractiveBlue, -value to InteractiveYellow)
        NumberLineMode.ABSOLUTE -> listOf(value to InteractiveYellow, 0f to InteractiveWhite)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("x", color = InteractiveMuted, fontSize = 12.sp)
            Text(
                when (mode) {
                    NumberLineMode.ABSOLUTE -> "|${displayNumber(value)}| = ${displayNumber(abs(value))}"
                    NumberLineMode.OPPOSITE -> "${displayNumber(value)}，${displayNumber(-value)}"
                    NumberLineMode.VALUE -> displayNumber(value)
                },
                color = InteractiveYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(value = value, onValueChange = { value = (it * 2f).roundToInt() / 2f }, valueRange = -5f..5f)
        Canvas(Modifier.fillMaxWidth().weight(1f)) { drawNumberLine(points) }
    }
}

@Composable
private fun ComparisonVisual() {
    var left by rememberSaveable { mutableStateOf(-3f) }
    var right by rememberSaveable { mutableStateOf(2f) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Slider(value = left, onValueChange = { left = it.roundToInt().toFloat() }, valueRange = -5f..5f)
        Slider(value = right, onValueChange = { right = it.roundToInt().toFloat() }, valueRange = -5f..5f)
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            drawNumberLine(listOf(left to InteractiveYellow, right to InteractiveBlue))
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
private fun SignedUnitVisual() {
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
private fun FormulaProcessVisual(formula: String?) {
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
private fun SignRuleVisual() {
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
private fun PowerVisual() {
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNumberLine(points: List<Pair<Float, Color>>) {
    val left = 22f
    val right = size.width - 22f
    val centerY = size.height * 0.58f
    drawLine(
        InteractiveWhite.copy(alpha = 0.72f),
        Offset(left, centerY),
        Offset(right, centerY),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
    )
    for (tick in -5..5) {
        val x = left + (tick + 5) / 10f * (right - left)
        drawLine(
            InteractiveWhite.copy(alpha = 0.45f),
            Offset(x, centerY - 8f),
            Offset(x, centerY + 8f),
            strokeWidth = 2f,
        )
    }
    points.forEach { (value, color) ->
        val x = left + (value.coerceIn(-5f, 5f) + 5f) / 10f * (right - left)
        drawCircle(color, radius = 8f, center = Offset(x, centerY))
    }
}

private fun displayNumber(value: Float): String =
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
