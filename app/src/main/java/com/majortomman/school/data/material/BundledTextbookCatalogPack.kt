package com.majortomman.school.data.material

import android.content.Context

/** Compatibility model retained for PDF import code. The runtime does not bundle any catalog rows. */
internal data class BundledCatalogBook(
    val id: String,
    val sha256: String,
    val title: String,
    val aliases: List<String>,
    val subjectId: String,
    val subjectTitle: String,
    val stage: EducationStage,
    val grade: Int,
    val volume: TextbookVolume,
    val pageCount: Int,
    val pageIndexOffset: Int,
    val publisher: String,
    val edition: String,
    val lessons: List<CatalogLesson>,
) {
    val slot: TextbookSlot
        get() = TextbookSlot(subjectId, subjectTitle, grade, volume, stage)

    fun validate(selectedSlot: TextbookSlot, actualPageCount: Int) {
        require(selectedSlot.subjectId == subjectId) { "教材学科不匹配" }
        require(selectedSlot.stage == stage && selectedSlot.grade == grade && selectedSlot.volume == volume) {
            "教材槽位不匹配"
        }
        require(actualPageCount == pageCount) { "教材页数不匹配" }
    }

    fun catalog(): TextbookCatalog = TextbookCatalog(
        book = CatalogBook(
            id = id,
            title = title,
            subject = subjectTitle,
            grade = grade,
            volume = volume,
            publisher = publisher,
            edition = edition,
        ),
        lessons = lessons,
    )

    fun installBound(textbook: InstalledTextbook): InstalledTextbook = textbook

    fun installUnbound(context: Context): InstalledTextbook {
        context.applicationContext
        error("APK 不包含预制教材目录")
    }
}

/** All catalogue content must be supplied by an installed cloud course package. */
internal object BundledTextbookCatalogPack {
    const val PACK_VERSION = "bundled-catalog-disabled"
    const val UNBOUND_PACK_VERSION = "bundled-catalog-disabled"

    fun installMissing(context: Context) {
        context.applicationContext
    }

    fun find(context: Context, sha256: String, sourceName: String): BundledCatalogBook? {
        context.applicationContext
        sha256.length
        sourceName.length
        return null
    }

    fun upgradeIfMatched(context: Context, textbook: InstalledTextbook): InstalledTextbook {
        context.applicationContext
        return textbook
    }

    fun count(context: Context): Int {
        context.applicationContext
        return 0
    }

    fun lessonCount(context: Context): Int {
        context.applicationContext
        return 0
    }

    fun books(context: Context): List<BundledCatalogBook> {
        context.applicationContext
        return emptyList()
    }
}
