package com.majortomman.school.learning.cloud

import android.content.Context
import com.majortomman.school.data.material.CatalogBook
import com.majortomman.school.data.material.CatalogLesson
import com.majortomman.school.data.material.InstalledMaterialPack
import com.majortomman.school.data.material.InstalledTextbook
import com.majortomman.school.data.material.MATERIAL_PACK_SCHEMA_VERSION
import com.majortomman.school.data.material.MaterialLibraryStore
import com.majortomman.school.data.material.MaterialPackManifest
import com.majortomman.school.data.material.MaterialPdfAsset
import com.majortomman.school.data.material.SubjectTemplates
import com.majortomman.school.data.material.TextbookCatalog
import com.majortomman.school.data.material.TextbookCatalogParser
import com.majortomman.school.data.material.TextbookSlot
import com.majortomman.school.data.material.TextbookVolume
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts a verified cloud package into the local navigation model.
 *
 * The course JSON, textbook PDF and every other asset live under the same active course directory.
 * No local PDF binding or user-selected file is consulted.
 */
internal object CloudCourseCatalogInstaller {
    private const val ACTIVE_DIRECTORY = "course-packs/active"
    private const val COURSE_FILE_NAME = "course.json"
    private const val GENERATED_LESSONS_PATH = "generated/lessons.json"
    private const val CLOUD_VERSION_PREFIX = "cloud-course-"

    fun refreshFromCache(context: Context): Int {
        val activeRoot = File(context.filesDir, ACTIVE_DIRECTORY)
        val courseRoots = activeRoot.listFiles().orEmpty().filter(File::isDirectory)
        var installedCount = 0
        courseRoots.forEach { root ->
            val courseFile = File(root, COURSE_FILE_NAME)
            if (!courseFile.isFile) return@forEach
            runCatching { install(context, root, JSONObject(courseFile.readText(Charsets.UTF_8))) }
                .onSuccess { installedCount += 1 }
        }
        return installedCount
    }

    private fun install(context: Context, root: File, course: JSONObject) {
        CloudCourseCodec.validate(course)
        val textbook = course.getJSONObject("textbook")
        val subjectTitle = textbook.getString("subject").trim()
        val subject = SubjectTemplates.findByTitle(subjectTitle)
            ?: error("课程包包含不受支持的学科：$subjectTitle")
        val grade = parseGrade(textbook.opt("grade"))
        val volume = parseVolume(textbook.opt("semester") ?: textbook.opt("volume"))
        val slot = TextbookSlot(subject.id, subject.title, grade, volume)
        val lessons = buildCatalogLessons(course.getJSONArray("chapters"))
        require(lessons.isNotEmpty()) { "课程包不包含可导航的小节" }

        val pdf = textbook.optJSONObject("pdf") ?: error("课程包缺少 textbook.pdf")
        val pdfPath = validateRelativePath(pdf.getString("path"))
        val pdfSha256 = validateSha256(pdf.getString("sha256"))
        val pageCount = pdf.getInt("pageCount")
        val pageIndexOffset = pdf.optInt("pageIndexOffset", 0)
        val pdfFile = File(root, pdfPath)
        require(pdfFile.isFile) { "课程包缺少教材 PDF：$pdfPath" }
        require(CoursePackStore.sha256(pdfFile) == pdfSha256) { "教材 PDF 摘要校验失败" }
        require(pageCount > 0) { "教材 PDF 页数必须大于 0" }

        val title = textbook.getString("title").trim()
        val catalog = TextbookCatalog(
            book = CatalogBook(
                id = textbook.optString("id").ifBlank { root.name },
                title = title,
                subject = subject.title,
                grade = grade,
                volume = volume,
                publisher = textbook.optString("publisher"),
                edition = textbook.optString("edition", "云端课程包"),
            ),
            lessons = lessons,
        )
        val generated = TextbookCatalogParser.generateLessons(slot, catalog)
        val generatedFile = File(root, GENERATED_LESSONS_PATH)
        generatedFile.parentFile?.mkdirs()
        generatedFile.writeText(
            JSONObject().put(
                "lessons",
                JSONArray().apply { generated.forEach { put(it.toJson()) } },
            ).toString(2),
            Charsets.UTF_8,
        )

        val courseFile = File(root, COURSE_FILE_NAME)
        val manifest = MaterialPackManifest(
            schemaVersion = MATERIAL_PACK_SCHEMA_VERSION,
            packId = "cloud-${textbook.optString("id", root.name)}",
            version = "$CLOUD_VERSION_PREFIX${courseFile.lastModified()}",
            title = title,
            subject = subject.title,
            catalogPath = COURSE_FILE_NAME,
            pdf = MaterialPdfAsset(
                path = pdfPath,
                sha256 = pdfSha256,
                pageIndexOffset = pageIndexOffset,
            ),
        )
        val target = InstalledTextbook(
            slot = slot,
            pack = InstalledMaterialPack(
                manifest = manifest,
                rootPath = root.absolutePath,
                installedAt = System.currentTimeMillis(),
                sizeBytes = MaterialLibraryStore.directorySize(root),
            ),
            pageCount = pageCount,
            lessons = generated,
        )
        MaterialLibraryStore.upsert(context, target)
    }

    private fun buildCatalogLessons(chapters: JSONArray): List<CatalogLesson> = buildList {
        for (chapterIndex in 0 until chapters.length()) {
            val chapter = chapters.getJSONObject(chapterIndex)
            val chapterId = chapter.getString("id")
            val sections = chapter.optJSONArray("sections") ?: JSONArray()
            for (sectionIndex in 0 until sections.length()) {
                val section = sections.getJSONObject(sectionIndex)
                val range = pageRange(section.optJSONArray("pages")) ?: continue
                add(
                    CatalogLesson(
                        id = "$chapterId-${section.getString("id")}",
                        title = section.getString("title"),
                        pageStart = range.first,
                        pageEnd = range.last,
                    ),
                )
            }
            val review = chapter.optJSONObject("review")
            val reviewRange = pageRange(review?.optJSONArray("pages"))
            if (review != null && reviewRange != null) {
                add(
                    CatalogLesson(
                        id = "$chapterId-${review.optString("id", "review")}",
                        title = review.optString("title", "${chapter.getString("title")}复习"),
                        pageStart = reviewRange.first,
                        pageEnd = reviewRange.last,
                    ),
                )
            }
        }
    }

    private fun pageRange(pages: JSONArray?): IntRange? {
        if (pages == null || pages.length() == 0) return null
        var start = Int.MAX_VALUE
        var end = Int.MIN_VALUE
        for (index in 0 until pages.length()) {
            val page = pages.getJSONObject(index)
            val pageStart = page.optInt("sourcePage", 1).coerceAtLeast(1)
            val pageEnd = page.optInt("sourcePageEnd", pageStart).coerceAtLeast(pageStart)
            start = minOf(start, pageStart)
            end = maxOf(end, pageEnd)
        }
        return start..end
    }

    private fun parseGrade(value: Any?): Int {
        if (value is Number) return value.toInt().coerceIn(1, 16)
        val text = value?.toString().orEmpty().trim()
        text.toIntOrNull()?.let { return it.coerceIn(1, 16) }
        val names = listOf(
            "一", "二", "三", "四", "五", "六", "七", "八", "九", "十",
            "十一", "十二", "十三", "十四", "十五", "十六",
        )
        val index = names.indexOfFirst { text.contains(it) }
        require(index >= 0) { "课程包缺少有效年级" }
        return index + 1
    }

    private fun parseVolume(value: Any?): TextbookVolume {
        val text = value?.toString().orEmpty().trim()
        val id = when {
            text == "2" || text.contains("下") || text.contains("第二") -> 2
            else -> 1
        }
        return TextbookVolume.fromId(id)
    }
}
