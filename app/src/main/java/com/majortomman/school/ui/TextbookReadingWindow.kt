package com.majortomman.school.ui

import com.majortomman.school.data.material.GeneratedLesson
import com.majortomman.school.data.material.InstalledMaterialPack
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal data class TextbookReadingWindow(
    val startPrintedPage: Int,
    val endPrintedPage: Int,
) {
    init {
        require(startPrintedPage > 0)
        require(endPrintedPage >= startPrintedPage)
    }

    fun contains(printedPage: Int): Boolean = printedPage in startPrintedPage..endPrintedPage

    fun clamp(printedPage: Int): Int = printedPage.coerceIn(startPrintedPage, endPrintedPage)

    companion object {
        fun resolve(pack: InstalledMaterialPack, initialPrintedPage: Int): TextbookReadingWindow? {
            val lessonsFile = File(pack.rootPath, "generated/lessons.json")
            if (!lessonsFile.isFile) return null
            return runCatching {
                val lessons = JSONObject(lessonsFile.readText(Charsets.UTF_8))
                    .optJSONArray("lessons") ?: JSONArray()
                for (index in 0 until lessons.length()) {
                    val lesson = GeneratedLesson.fromJson(lessons.getJSONObject(index))
                    if (initialPrintedPage in lesson.pageStart..lesson.pageEnd) {
                        return@runCatching TextbookReadingWindow(lesson.pageStart, lesson.pageEnd)
                    }
                }
                null
            }.getOrNull()
        }
    }
}
