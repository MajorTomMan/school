package com.majortomman.school.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        is CourseSourceExcerptBlock -> TextbookPdfExcerpt(block, installedMaterial)
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
    val height = visualizationHeight(block.kind, compact)
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        when (block.kind) {
            RationalVisualizationKind.NONE,
            RationalVisualizationKind.HISTORY,
            -> Unit
            RationalVisualizationKind.OPPOSITE_QUANTITIES -> SignedMovementNumberLineVisual()
            RationalVisualizationKind.RATIONAL_CLASSIFICATION,
            RationalVisualizationKind.INTEGER_TO_FRACTION,
            -> IntegerToFractionTextbookVisual()
            RationalVisualizationKind.NUMBER_LINE -> AdjustableNumberLine(NumberLineMode.VALUE)
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

private fun visualizationHeight(kind: RationalVisualizationKind, compact: Boolean) = when (kind) {
    RationalVisualizationKind.INTEGER_TO_FRACTION,
    RationalVisualizationKind.RATIONAL_CLASSIFICATION,
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

@Composable
private fun TextbookPdfExcerpt(
    block: CourseSourceExcerptBlock,
    installedMaterial: InstalledMaterialPack,
) {
    val pdf = installedMaterial.pdfFile
    val pdfIndex = installedMaterial.printedPageToPdfIndex(block.sourcePage)
    val key = "${pdf.absolutePath}:${pdf.lastModified()}:$pdfIndex:${block.left}:${block.top}:${block.right}:${block.bottom}"
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = key) {
        value = withContext(Dispatchers.IO) { renderPdfExcerpt(pdf, pdfIndex, block) }
    }
    val image = bitmap
    if (image != null) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                block.altText,
                color = InteractiveMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 7.dp),
            )
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = block.altText,
                modifier = Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height.toFloat()),
                contentScale = ContentScale.Fit,
            )
        }
    } else if (block.fallbackText.isNotBlank()) {
        Text(
            block.fallbackText,
            color = InteractiveWhite.copy(alpha = 0.84f),
            fontSize = 15.sp,
            lineHeight = 24.sp,
        )
    }
}

private fun renderPdfExcerpt(
    file: File,
    pageIndex: Int,
    block: CourseSourceExcerptBlock,
): Bitmap? = runCatching {
    if (!file.isFile) return@runCatching null
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use descriptorUse@ { descriptor ->
        PdfRenderer(descriptor).use rendererUse@ { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return@rendererUse null
            renderer.openPage(pageIndex).use pageUse@ { page ->
                val scale = 2.2f
                val full = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                full.eraseColor(android.graphics.Color.WHITE)
                page.render(
                    full,
                    null,
                    Matrix().apply { postScale(scale, scale) },
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                )
                val x = (block.left * scale).toInt().coerceIn(0, full.width - 1)
                val y = (block.top * scale).toInt().coerceIn(0, full.height - 1)
                val right = (block.right * scale).toInt().coerceIn(x + 1, full.width)
                val bottom = (block.bottom * scale).toInt().coerceIn(y + 1, full.height)
                val crop = Bitmap.createBitmap(full, x, y, right - x, bottom - y)
                if (crop !== full) full.recycle()
                crop.toDarkCourseExcerpt()
            }
        }
    }
}.getOrNull()


private fun Bitmap.toDarkCourseExcerpt(): Bitmap {
    val output = copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(width * height)
    output.getPixels(pixels, 0, width, 0, 0, width, height)
    for (index in pixels.indices) {
        val color = pixels[index]
        val red = android.graphics.Color.red(color)
        val green = android.graphics.Color.green(color)
        val blue = android.graphics.Color.blue(color)
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        pixels[index] = if (minimum >= 246) {
            android.graphics.Color.TRANSPARENT
        } else if (maximum - minimum <= 18) {
            val value = (255 - ((red + green + blue) / 3)).coerceIn(36, 245)
            android.graphics.Color.argb(255, value, value, value)
        } else {
            val brighten = 1.18f
            android.graphics.Color.argb(
                255,
                (red * brighten).toInt().coerceIn(0, 255),
                (green * brighten).toInt().coerceIn(0, 255),
                (blue * brighten).toInt().coerceIn(0, 255),
            )
        }
    }
    output.setPixels(pixels, 0, width, 0, 0, width, height)
    if (output !== this) recycle()
    return output
}
