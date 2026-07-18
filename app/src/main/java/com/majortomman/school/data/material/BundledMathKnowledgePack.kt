package com.majortomman.school.data.material

import android.content.Context

/** Compatibility model retained for import code; the APK no longer supplies instances of it. */
internal data class BundledMathKnowledgeBook(
    val sha256: String,
    val title: String,
    val aliases: List<String>,
    val stage: EducationStage,
    val grade: Int,
    val volume: TextbookVolume,
    val pageCount: Int,
    val pageIndexOffset: Int,
    val publisher: String,
    val edition: String,
    val lessons: List<BundledMathLesson>,
) {
    fun validate(slot: TextbookSlot, actualPageCount: Int) {
        require(slot.subjectId == "math") { "教材学科不匹配" }
        require(slot.stage == stage && slot.grade == grade && slot.volume == volume) { "教材槽位不匹配" }
        require(actualPageCount == pageCount) { "教材页数不匹配" }
    }

    fun toScanResult(): DirectPdfScanResult = DirectPdfScanResult(
        title = title,
        pageIndexOffset = pageIndexOffset,
        catalog = TextbookCatalog(
            book = CatalogBook(
                id = "remote-${sha256.take(16)}",
                title = title,
                subject = "数学",
                grade = grade,
                volume = volume,
                publisher = publisher,
                edition = edition,
            ),
            lessons = lessons.map { lesson ->
                CatalogLesson(
                    id = lesson.sourceId,
                    title = lesson.title,
                    pageStart = lesson.pageStart,
                    pageEnd = lesson.pageEnd,
                )
            },
        ),
        scannedPages = 0,
        evidence = "",
    )

    fun install(textbook: InstalledTextbook): InstalledTextbook = textbook
}

internal data class BundledMathLesson(
    val sourceId: String,
    val title: String,
    val pageStart: Int,
    val pageEnd: Int,
)

/** Bundled matching is intentionally disabled. Course and catalogue data must come from remote packs. */
internal object BundledMathKnowledgePack {
    fun find(
        context: Context,
        sha256: String,
        sourceName: String,
    ): BundledMathKnowledgeBook? {
        context.applicationContext
        sha256.length
        sourceName.length
        return null
    }

    fun upgradeIfMatched(
        context: Context,
        textbook: InstalledTextbook,
    ): InstalledTextbook {
        context.applicationContext
        return textbook
    }

    fun count(context: Context): Int {
        context.applicationContext
        return 0
    }
}
