package com.majortomman.school.data.material

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

internal data class DirectPdfScanResult(
    val title: String,
    val pageIndexOffset: Int,
    val catalog: TextbookCatalog,
    val scannedPages: Int,
    val evidence: String,
)

internal object DirectPdfImportScanner {
    private const val MAX_IDENTITY_PAGES = 24
    private const val MAX_SCAN_WIDTH = 1_300
    private const val MAX_SCAN_HEIGHT = 1_900

    suspend fun scan(
        pdfFile: File,
        displayName: String,
        slot: TextbookSlot,
        cacheRoot: File,
        onProgress: suspend (completed: Int, total: Int, message: String) -> Unit,
    ): DirectPdfScanResult {
        val pages = mutableListOf<OcrPageResult>()
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val total = minOf(renderer.pageCount, MAX_IDENTITY_PAGES)
                TextbookOcrEngine().use { engine ->
                    for (index in 0 until total) {
                        onProgress(index, total, "识别封面与目录 ${index + 1} / $total")
                        val result = renderer.openPage(index).use { page ->
                            val scale = minOf(
                                MAX_SCAN_WIDTH.toFloat() / page.width.coerceAtLeast(1),
                                MAX_SCAN_HEIGHT.toFloat() / page.height.coerceAtLeast(1),
                                2.2f,
                            ).coerceAtLeast(0.45f)
                            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                engine.recognize(
                                    bitmap = bitmap,
                                    printedPage = index + 1,
                                    pdfIndex = index,
                                )
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        pages += result
                    }
                }
                onProgress(total, total, "正在整理教材信息")
                val offset = inferPageIndexOffset(pages)
                val correctedPages = pages.map { page ->
                    page.copy(printedPage = (page.pdfIndex - offset + 1).coerceAtLeast(1))
                }
                correctedPages.forEach { TextbookOcrStore.write(cacheRoot, it) }
                val evidence = buildString {
                    append(displayName)
                    append('\n')
                    correctedPages.take(8).forEach { page ->
                        append(page.compactText.take(1_200))
                        append('\n')
                    }
                }
                validateIdentity(slot, evidence)
                val title = titleFrom(displayName, correctedPages, slot)
                val lessons = extractCatalogLessons(
                    pages = correctedPages,
                    pageCount = renderer.pageCount,
                    offset = offset,
                    stage = slot.stage,
                )
                val catalog = TextbookCatalog(
                    book = CatalogBook(
                        id = stableSlug("${slot.key}-$title"),
                        title = title,
                        subject = slot.subjectTitle,
                        grade = slot.grade,
                        volume = slot.volume,
                        publisher = inferPublisher(evidence),
                        edition = "PDF 自动扫描",
                    ),
                    lessons = lessons,
                )
                return DirectPdfScanResult(
                    title = title,
                    pageIndexOffset = offset,
                    catalog = catalog,
                    scannedPages = correctedPages.size,
                    evidence = evidence.take(8_000),
                )
            }
        }
    }

    fun catalogToJson(catalog: TextbookCatalog): JSONObject = JSONObject()
        .put(
            "book",
            JSONObject()
                .put("id", catalog.book.id)
                .put("title", catalog.book.title)
                .put("subject", catalog.book.subject)
                .put("grade", catalog.book.grade)
                .put("volume", catalog.book.volume.id)
                .put("publisher", catalog.book.publisher)
                .put("edition", catalog.book.edition),
        )
        .put(
            "chapters",
            JSONArray().put(
                JSONObject()
                    .put("id", "auto-scan")
                    .put("title", "PDF 自动扫描目录")
                    .put(
                        "lessons",
                        JSONArray().apply {
                            catalog.lessons.forEach { lesson ->
                                put(
                                    JSONObject()
                                        .put("id", lesson.id)
                                        .put("title", lesson.title)
                                        .put("pages", JSONArray().put(lesson.pageStart).put(lesson.pageEnd)),
                                )
                            }
                        },
                    ),
            ),
        )

    private fun inferPageIndexOffset(pages: List<OcrPageResult>): Int {
        val candidates = mutableListOf<Int>()
        pages.forEach { page ->
            page.lines.forEach { line ->
                val text = line.text.trim()
                val number = text.toIntOrNull() ?: return@forEach
                if (line.top < 0.78f || number !in 1..2_000) return@forEach
                val offset = page.pdfIndex - (number - 1)
                if (offset in -20..80) candidates += offset
            }
        }
        if (candidates.isEmpty()) return 0
        return candidates.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
    }

    private fun validateIdentity(slot: TextbookSlot, evidence: String) {
        val compact = evidence.replace(" ", "")
        val subjectHits = SubjectTemplates.all.filter { it.title in compact }
        if (slot.subjectTitle !in compact && subjectHits.isNotEmpty() && subjectHits.none { it.id == slot.subjectId }) {
            throw IllegalArgumentException(
                "PDF 中识别到${subjectHits.first().title}，与当前选择的${slot.subjectTitle}不一致",
            )
        }

        val gradeTokens = mapOf(
            1 to listOf("一年级"), 2 to listOf("二年级"), 3 to listOf("三年级"),
            4 to listOf("四年级"), 5 to listOf("五年级"), 6 to listOf("六年级"),
            7 to listOf("七年级", "初一"), 8 to listOf("八年级", "初二"), 9 to listOf("九年级", "初三"),
            10 to listOf("高一", "高中一年级"), 11 to listOf("高二", "高中二年级"),
            12 to listOf("高三", "高中三年级"), 13 to listOf("大一"), 14 to listOf("大二"),
            15 to listOf("大三"), 16 to listOf("大四"),
        )
        val foundGrades = gradeTokens.filterValues { tokens -> tokens.any { it in compact } }.keys
        if (foundGrades.isNotEmpty() && slot.grade !in foundGrades) {
            throw IllegalArgumentException(
                "PDF 中识别到${gradeLabel(foundGrades.first())}，与当前选择的${slot.levelLabel}不一致",
            )
        }

        val hasFirst = listOf("上册", "第一册", "上学期").any { it in compact }
        val hasSecond = listOf("下册", "第二册", "下学期").any { it in compact }
        if (hasFirst && !hasSecond && slot.volume == TextbookVolume.SECOND) {
            throw IllegalArgumentException("PDF 中识别到上册或第一册，与当前选择的${slot.volumeLabel}不一致")
        }
        if (hasSecond && !hasFirst && slot.volume == TextbookVolume.FIRST) {
            throw IllegalArgumentException("PDF 中识别到下册或第二册，与当前选择的${slot.volumeLabel}不一致")
        }
    }

    private fun titleFrom(
        displayName: String,
        pages: List<OcrPageResult>,
        slot: TextbookSlot,
    ): String {
        val filename = displayName
            .substringAfterLast('/')
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .trim()
        if (filename.length in 4..120 && filename !in genericNames) return filename
        val coverLines = pages.take(4)
            .flatMap { it.lines }
            .map { it.text.trim() }
            .filter { it.length in 4..50 }
        return coverLines.firstOrNull {
            slot.subjectTitle in it || "教科书" in it || "教材" in it
        }?.take(120) ?: slot.displayTitle
    }

    private fun extractCatalogLessons(
        pages: List<OcrPageResult>,
        pageCount: Int,
        offset: Int,
        stage: EducationStage,
    ): List<CatalogLesson> {
        val candidates = mutableListOf<Pair<String, Int>>()
        pages.forEach { page ->
            val isCatalogPage = page.text.contains("目录") || page.pdfIndex in 1..15
            if (!isCatalogPage) return@forEach
            page.lines.forEach { line ->
                parseCatalogLine(line.text)?.let(candidates::add)
            }
        }
        val maxPrintedPage = (pageCount - offset).coerceAtLeast(1)
        val normalized = candidates
            .map { (title, page) -> cleanTitle(title) to page }
            .filter { (title, page) -> title.length in 2..80 && page in 1..maxPrintedPage }
            .distinctBy { it.first to it.second }
            .sortedBy { it.second }

        if (normalized.size >= 2) {
            return normalized.mapIndexed { index, (title, start) ->
                val nextStart = normalized.getOrNull(index + 1)?.second
                val end = ((nextStart ?: (maxPrintedPage + 1)) - 1).coerceAtLeast(start)
                CatalogLesson(
                    id = "scan-${index + 1}-${stableSlug(title).take(24)}",
                    title = title,
                    pageStart = start,
                    pageEnd = end.coerceAtMost(maxPrintedPage),
                )
            }
        }

        val chunkSize = when (stage) {
            EducationStage.PRIMARY -> 8
            EducationStage.JUNIOR_HIGH -> 10
            EducationStage.SENIOR_HIGH -> 12
            EducationStage.UNIVERSITY -> 16
        }
        val firstPage = 1
        return buildList {
            var start = firstPage
            var index = 1
            while (start <= maxPrintedPage) {
                val end = minOf(maxPrintedPage, start + chunkSize - 1)
                add(
                    CatalogLesson(
                        id = "scan-section-$index",
                        title = "教材内容 $index",
                        pageStart = start,
                        pageEnd = end,
                    ),
                )
                start = end + 1
                index += 1
            }
        }
    }

    private fun parseCatalogLine(raw: String): Pair<String, Int>? {
        val text = raw
            .replace('…', '.')
            .replace('·', '.')
            .replace(Regex("\\.{2,}"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val match = catalogLinePattern.matchEntire(text) ?: return null
        val title = match.groupValues[1].trim(' ', '.', '．', '。', '、', '-')
        val page = match.groupValues[2].toIntOrNull() ?: return null
        return title to page
    }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("^(第[一二三四五六七八九十百0-9]+[章节单元课]\\s*)"), "")
        .replace(Regex("^[一二三四五六七八九十0-9]+[、.．]\\s*"), "")
        .trim()
        .ifBlank { raw.trim() }
        .take(80)

    private fun inferPublisher(text: String): String = publisherNames.firstOrNull { it in text }.orEmpty()

    private fun stableSlug(raw: String): String {
        val ascii = raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        if (ascii.length >= 3) return ascii.take(48)
        val hash = raw.hashCode().toUInt().toString(16)
        return "pdf-$hash"
    }

    private val catalogLinePattern = Regex("^(.{2,100}?)\\s+(\\d{1,4})$")
    private val genericNames = setOf("document", "教材", "课本", "textbook", "book")
    private val publisherNames = listOf(
        "人民教育出版社", "北京师范大学出版社", "华东师范大学出版社", "苏教版",
        "教育科学出版社", "高等教育出版社", "外语教学与研究出版社", "清华大学出版社",
    )
}
