package com.majortomman.school.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.material.InstalledMaterialPack
import com.majortomman.school.learning.course.CourseConclusionBlock
import com.majortomman.school.learning.course.CourseExerciseBlock
import com.majortomman.school.learning.course.CourseFormulaBlock
import com.majortomman.school.learning.course.CourseHeadingBlock
import com.majortomman.school.learning.course.CoursePageBlock
import com.majortomman.school.learning.course.CourseSourceExcerptBlock
import com.majortomman.school.learning.course.CourseSummaryBlock
import com.majortomman.school.learning.course.CourseTextBlock
import com.majortomman.school.learning.course.CourseTextRole
import com.majortomman.school.learning.course.CourseVisualizationBlock
import com.majortomman.school.learning.course.CourseWorkedExampleBlock
import com.majortomman.school.learning.course.RationalLessonPage
import com.majortomman.school.learning.course.RationalVisualizationKind

@Composable
internal fun CloudCourseOrderedBlocks(
    page: RationalLessonPage,
    installedMaterial: InstalledMaterialPack,
    compact: Boolean,
) {
    page.blocks.forEachIndexed { index, block ->
        if (index > 0) Spacer(Modifier.height(blockSpacing(block)))
        CloudCourseBlock(block, page, installedMaterial, compact)
    }
}

private fun blockSpacing(block: CoursePageBlock) = when (block) {
    is CourseHeadingBlock -> 22.dp
    is CourseVisualizationBlock,
    is CourseSourceExcerptBlock,
    -> 18.dp
    is CourseConclusionBlock -> 20.dp
    else -> 12.dp
}

@Composable
private fun CloudCourseBlock(
    block: CoursePageBlock,
    page: RationalLessonPage,
    installedMaterial: InstalledMaterialPack,
    compact: Boolean,
) {
    when (block) {
        is CourseHeadingBlock -> TextbookSubheading(block.text)
        is CourseTextBlock -> TextbookParagraph(block, compact)
        is CourseFormulaBlock -> TextbookFormula(block, compact)
        is CourseSummaryBlock -> TextbookSummary(block, compact)
        is CourseWorkedExampleBlock -> TextbookWorkedExample(block, compact)
        is CourseExerciseBlock -> TextbookExercise(block, compact)
        is CourseConclusionBlock -> TextbookConclusion(block.text, compact)
        is CourseVisualizationBlock -> CloudVisualizationBlock(block, page, compact)
        is CourseSourceExcerptBlock -> LegacySourceText(block, installedMaterial, compact)
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
private fun TextbookParagraph(block: CourseTextBlock, compact: Boolean) {
    val color = when (block.role) {
        CourseTextRole.PROMPT -> InteractiveBlue
        CourseTextRole.CAPTION -> InteractiveMuted
        CourseTextRole.HISTORY -> InteractiveWhite.copy(alpha = 0.76f)
        CourseTextRole.EXPLANATION -> InteractiveWhite.copy(alpha = 0.78f)
        CourseTextRole.TEXTBOOK -> InteractiveWhite.copy(alpha = 0.88f)
    }
    Text(
        text = block.text,
        color = color,
        fontSize = if (compact) 15.sp else 16.sp,
        lineHeight = if (compact) 24.sp else 27.sp,
        fontStyle = if (block.role == CourseTextRole.CAPTION) FontStyle.Italic else FontStyle.Normal,
    )
}

@Composable
private fun TextbookFormula(block: CourseFormulaBlock, compact: Boolean) {
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
private fun TextbookSummary(block: CourseSummaryBlock, compact: Boolean) {
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
private fun TextbookWorkedExample(block: CourseWorkedExampleBlock, compact: Boolean) {
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
private fun TextbookExercise(block: CourseExerciseBlock, compact: Boolean) {
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
private fun CloudVisualizationBlock(
    block: CourseVisualizationBlock,
    page: RationalLessonPage,
    compact: Boolean,
) {
    val isNumberLineLesson = block.renderer.equals("number_line_lesson", ignoreCase = true)
    val height = if (isNumberLineLesson) {
        if (compact) 360.dp else 420.dp
    } else {
        visualizationHeight(block.kind, compact)
    }
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        if (isNumberLineLesson) {
            NumberLineLessonVisual(block.params)
        } else {
            when (block.kind) {
                RationalVisualizationKind.NONE,
                RationalVisualizationKind.HISTORY,
                -> Unit
                RationalVisualizationKind.OPPOSITE_QUANTITIES -> OppositeQuantitiesSceneVisual(block.params)
                RationalVisualizationKind.RATIONAL_CLASSIFICATION -> RationalConceptFlowVisual(block.params)
                RationalVisualizationKind.INTEGER_TO_FRACTION -> IntegerToFractionTextbookVisual()
                RationalVisualizationKind.NUMBER_LINE -> NumberLineLessonVisual(block.params)
                RationalVisualizationKind.OPPOSITE_NUMBERS -> AdjustableNumberLine(NumberLineMode.OPPOSITE)
                RationalVisualizationKind.ABSOLUTE_VALUE -> AbsoluteValueNumberLineVisual()
                RationalVisualizationKind.NUMBER_COMPARISON -> ComparisonVisual()
                RationalVisualizationKind.ADDITION_PROCESS -> SignedUnitVisual()
                RationalVisualizationKind.SUBTRACTION_TRANSFORM,
                RationalVisualizationKind.DIVISION_TRANSFORM,
                -> FormulaProcessVisual(page.formula)
                RationalVisualizationKind.MULTIPLICATION_SIGN -> SignRuleVisual()
                RationalVisualizationKind.POWER_PROCESS -> PowerVisual()
                else -> TextbookMathVisual(block.kind, block.params)
            }
        }
    }
}

private fun visualizationHeight(kind: RationalVisualizationKind, compact: Boolean) = when (kind) {
    RationalVisualizationKind.OPPOSITE_QUANTITIES,
    RationalVisualizationKind.INTEGER_TO_FRACTION,
    RationalVisualizationKind.RATIONAL_CLASSIFICATION,
    RationalVisualizationKind.NUMBER_LINE,
    -> if (compact) 360.dp else 420.dp
    RationalVisualizationKind.FUNCTION_GRAPH,
    RationalVisualizationKind.CARTESIAN_PLANE,
    RationalVisualizationKind.GEOMETRY,
    RationalVisualizationKind.TRANSFORMATION,
    RationalVisualizationKind.RIGHT_TRIANGLE,
    RationalVisualizationKind.DATA_CHART,
    RationalVisualizationKind.PROBABILITY,
    RationalVisualizationKind.PROJECTION,
    -> if (compact) 300.dp else 360.dp
    else -> if (compact) 220.dp else 280.dp
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun LegacySourceText(
    block: CourseSourceExcerptBlock,
    installedMaterial: InstalledMaterialPack,
    compact: Boolean,
) {
    if (block.fallbackText.isNotBlank()) {
        Text(
            text = block.fallbackText,
            color = InteractiveWhite.copy(alpha = 0.88f),
            fontSize = if (compact) 15.sp else 16.sp,
            lineHeight = if (compact) 24.sp else 27.sp,
        )
    }
}
