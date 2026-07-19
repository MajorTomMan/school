package com.majortomman.school.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
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
import com.majortomman.school.learning.course.CoursePageBlock
import com.majortomman.school.learning.course.CoursePageBlockKind
import com.majortomman.school.learning.course.RationalLessonPage
import com.majortomman.school.learning.course.RationalVisualizationKind
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
internal fun CloudCoursePreviewScreen(page: RationalLessonPage) {
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
            Text("返回", color = InteractiveMuted, fontSize = 13.sp)
            Text(
                page.section,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                color = InteractiveWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text("教材第 ${page.sourcePage} 页", color = InteractiveYellow, fontSize = 13.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
        Box(Modifier.weight(1f)) {
            CloudCoursePageContent(page, 1, 5)
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
            Text("上一页", modifier = Modifier.width(112.dp), color = InteractiveMuted.copy(alpha = 0.35f), fontSize = 14.sp)
            Text("1 / 5", modifier = Modifier.width(72.dp), color = InteractiveMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Text("下一页", modifier = Modifier.width(112.dp), color = InteractiveBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
        }
    }
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
    val sourceLabel = if (currentPage.sourcePageEnd > currentPage.sourcePage) {
        "教材第 ${currentPage.sourcePage}—${currentPage.sourcePageEnd} 页"
    } else {
        "教材第 ${currentPage.sourcePage} 页"
    }

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
                sourceLabel,
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 620.dp
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Text(page.section, color = InteractiveBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                page.title,
                color = InteractiveWhite,
                fontSize = if (compact) 27.sp else 32.sp,
                lineHeight = if (compact) 33.sp else 39.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            if (page.blocks.isNotEmpty()) {
                page.blocks.forEach { block ->
                    CourseBlockContent(block, compact)
                }
            } else {
                LegacyPageContent(page, compact)
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "第 $pageNumber 页，共 $pageCount 页",
                modifier = Modifier.fillMaxWidth(),
                color = InteractiveMuted.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.End,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CourseBlockContent(block: CoursePageBlock, compact: Boolean) {
    val bodySize = if (compact) 14.sp else 16.sp
    val bodyLineHeight = if (compact) 22.sp else 25.sp
    when (block.kind) {
        CoursePageBlockKind.TEXTBOOK_TEXT -> {
            Text(
                block.text,
                color = InteractiveWhite.copy(alpha = 0.86f),
                fontSize = bodySize,
                lineHeight = bodyLineHeight,
            )
            Spacer(Modifier.height(11.dp))
        }
        CoursePageBlockKind.PROMPT -> {
            Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveBlue.copy(alpha = 0.55f)))
            Spacer(Modifier.height(7.dp))
            Text("思考", color = InteractiveBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(block.text, color = InteractiveWhite, fontSize = bodySize, lineHeight = bodyLineHeight)
            Spacer(Modifier.height(13.dp))
        }
        CoursePageBlockKind.FORMULA -> {
            Spacer(Modifier.height(2.dp))
            Text(
                block.text,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                color = InteractiveYellow,
                fontSize = if (compact) 18.sp else 22.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
        }
        CoursePageBlockKind.WORKED_EXAMPLE -> {
            Text("例题", color = InteractiveYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            if (block.text.isNotBlank()) {
                Text(block.text, color = InteractiveWhite, fontSize = bodySize, lineHeight = bodyLineHeight)
            }
            block.items.forEach { item ->
                Spacer(Modifier.height(6.dp))
                Text(item, color = InteractiveWhite.copy(alpha = 0.86f), fontSize = bodySize, lineHeight = bodyLineHeight)
            }
            Spacer(Modifier.height(13.dp))
        }
        CoursePageBlockKind.EXERCISE -> {
            Text(
                listOfNotNull("练习", block.label).joinToString(" "),
                color = InteractiveBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            if (block.text.isNotBlank()) {
                Text(block.text, color = InteractiveWhite, fontSize = bodySize, lineHeight = bodyLineHeight)
            }
            block.items.forEach { item ->
                Spacer(Modifier.height(6.dp))
                Text(item, color = InteractiveWhite.copy(alpha = 0.78f), fontSize = bodySize, lineHeight = bodyLineHeight)
            }
            Spacer(Modifier.height(13.dp))
        }
        CoursePageBlockKind.SUMMARY -> {
            block.items.forEach { item ->
                Text("• $item", color = InteractiveWhite, fontSize = bodySize, lineHeight = bodyLineHeight)
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(7.dp))
        }
        CoursePageBlockKind.CONCLUSION -> {
            Box(Modifier.fillMaxWidth().height(2.dp).background(InteractiveBlue.copy(alpha = 0.68f)))
            Spacer(Modifier.height(9.dp))
            Text(
                block.text,
                color = InteractiveWhite,
                fontSize = bodySize,
                lineHeight = bodyLineHeight,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(14.dp))
        }
        CoursePageBlockKind.VISUALIZATION -> {
            if (block.visualization !in setOf(RationalVisualizationKind.NONE, RationalVisualizationKind.HISTORY)) {
                val height = visualizationHeight(block.visualization, compact)
                CloudVisualization(
                    kind = block.visualization,
                    params = block.visualizationParams,
                    modifier = Modifier.fillMaxWidth().height(height),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LegacyPageContent(page: RationalLessonPage, compact: Boolean) {
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            color = InteractiveYellow,
            fontSize = if (compact) 18.sp else 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
    if (page.visualization !in setOf(RationalVisualizationKind.NONE, RationalVisualizationKind.HISTORY)) {
        Spacer(Modifier.height(14.dp))
        CloudVisualization(
            kind = page.visualization,
            params = emptyMap(),
            modifier = Modifier.fillMaxWidth().height(visualizationHeight(page.visualization, compact)),
        )
    }
    if (!page.conclusion.isNullOrBlank()) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(InteractiveBlue.copy(alpha = 0.68f)))
        Spacer(Modifier.height(10.dp))
        Text(
            page.conclusion,
            color = InteractiveWhite,
            fontSize = if (compact) 14.sp else 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun visualizationHeight(kind: RationalVisualizationKind, compact: Boolean) = when (kind) {
    RationalVisualizationKind.FUNCTION_GRAPH,
    RationalVisualizationKind.COORDINATE_PLANE,
    RationalVisualizationKind.PROJECTION,
    RationalVisualizationKind.CIRCLE,
    RationalVisualizationKind.ROTATION,
    RationalVisualizationKind.SIMILARITY,
    RationalVisualizationKind.RIGHT_TRIANGLE,
    RationalVisualizationKind.PYTHAGOREAN,
    -> if (compact) 260.dp else 320.dp
    RationalVisualizationKind.RATIONAL_DEFINITION_FLOW -> if (compact) 250.dp else 285.dp
    else -> if (compact) 215.dp else 260.dp
}

@Composable
private fun CloudVisualization(
    kind: RationalVisualizationKind,
    params: Map<String, String>,
    modifier: Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (kind) {
            RationalVisualizationKind.NONE,
            RationalVisualizationKind.HISTORY,
            -> Unit
            RationalVisualizationKind.OPPOSITE_QUANTITIES -> SignedMovementNumberLineVisual()
            RationalVisualizationKind.RATIONAL_CLASSIFICATION -> TextbookMathVisualization(kind, params, Modifier.fillMaxSize())
            RationalVisualizationKind.NUMBER_LINE -> AdjustableNumberLine(NumberLineMode.VALUE)
            RationalVisualizationKind.OPPOSITE_NUMBERS -> AdjustableNumberLine(NumberLineMode.OPPOSITE)
            RationalVisualizationKind.ABSOLUTE_VALUE -> AbsoluteValueNumberLineVisual()
            RationalVisualizationKind.NUMBER_COMPARISON -> ComparisonVisual()
            RationalVisualizationKind.ADDITION_PROCESS -> SignedUnitVisual()
            RationalVisualizationKind.SUBTRACTION_TRANSFORM,
            RationalVisualizationKind.DIVISION_TRANSFORM,
            -> TextbookMathVisualization(kind, params, Modifier.fillMaxSize())
            RationalVisualizationKind.MULTIPLICATION_SIGN -> SignRuleVisual()
            RationalVisualizationKind.POWER_PROCESS -> PowerVisual()
            else -> TextbookMathVisualization(kind, params, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CloudTextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        color = color,
        fontSize = 13.sp,
    )
}

@Composable
private fun CloudNavigationAction(
    label: String,
    enabled: Boolean,
    alignment: TextAlign,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier = Modifier
            .width(112.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        color = if (enabled) InteractiveBlue else InteractiveMuted.copy(alpha = 0.35f),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = alignment,
    )
}
