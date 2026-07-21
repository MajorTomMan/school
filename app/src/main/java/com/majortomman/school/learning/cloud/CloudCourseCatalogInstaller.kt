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
import com.majortomman.school.learning.course.CourseChapter
import com.majortomman.school.learning.course.CourseDocument
import com.majortomman.school.learning.course.CourseSection
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Converts an APK-validated course document into the local textbook navigation model. */
internal object CloudCourseCatalogInstaller {
    private const val ACTIVE_DIRECTORY = "course-packs/active"
    private const val COURSE_FILE_NAME = "course.json"
    private const val GENERATED_LESSONS_PATH = "generated/lessons.json"
    private const val CLOUD_VERSION_PREFIX = "cloud-course-"

    fun refreshFromCache(context: Context): Int {
        val activeRoot = File(context.filesDir, ACTIVE_DIRECTORY)
        var installedCount = 0
        activeRoot.listFiles().orEmpty().filter(File::isDirectory).forEach { root ->
            val courseFile = File(root, COURSE_FILE_NAME)
            if (!courseFile.isFile) return@forEach
            runCatching {
                install(context, root, courseFile, CourseDocumentParser.decode(courseFile.readText(Charsets.UTF_8)))
            }.onSuccess { installedCount += 1 }
        }
        return installedCount
    }

    private fun install(context: Context, root: File, courseFile: File, course: CourseDocument) {
        val textbook = course.textbook
        val subject = SubjectTemplates.findByTitle(textbook.subject)
            ?: error("课程包包含不受支持的学科：${textbook.subject}")
        val grade = parseGrade(textbook.grade)
        val volume = parseVolume(textbook.semester)
        val slot = TextbookSlot(subject.id, subject.title, grade, volume)
        val lessons = buildCatalogLessons(course.chapters)
        require(lessons.isNotEmpty()) { "课程包不包含可导航的小节" }

        val pdfFile = File(root, textbook.pdf.path)
        require(pdfFile.isFile) { "课程包缺少教材 PDF：${textbook.pdf.path}" }
        val pdfSha256 = CoursePackStore.sha256(pdfFile)

        val catalog = TextbookCatalog(
            book = CatalogBook(
                id = textbook.id,
                title = textbook.title,
                subject = subject.title,
                grade = grade,
                volume = volume,
                publisher = textbook.publisher,
                edition = textbook.edition,
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

        val manifest = MaterialPackManifest(
            schemaVersion = MATERIAL_PACK_SCHEMA_VERSION,
            packId = "cloud-${textbook.id}",
            version = "$CLOUD_VERSION_PREFIX${courseFile.lastModified()}",
            title = textbook.title,
            subject = subject.title,
            catalogPath = COURSE_FILE_NAME,
            pdf = MaterialPdfAsset(
                path = textbook.pdf.path,
                sha256 = pdfSha256,
                pageIndexOffset = textbook.pdf.pageIndexOffset,
            ),
        )
        MaterialLibraryStore.upsert(
            context,
            InstalledTextbook(
                slot = slot,
                pack = InstalledMaterialPack(
                    manifest = manifest,
                    rootPath = root.absolutePath,
                    installedAt = System.currentTimeMillis(),
                    sizeBytes = MaterialLibraryStore.directorySize(root),
                ),
                pageCount = textbook.pdf.pageCount,
                lessons = generated,
            ),
        )
    }

    private fun buildCatalogLessons(chapters: List<CourseChapter>): List<CatalogLesson> = buildList {
        chapters.forEach { chapter ->
            chapter.sections.forEach { section -> add(section.toCatalogLesson(chapter.id)) }
            chapter.review?.let { add(it.toCatalogLesson(chapter.id)) }
        }
    }

    private fun CourseSection.toCatalogLesson(chapterId: String): CatalogLesson {
        val start = pages.minOf { it.sourcePage }
        val end = pages.maxOf { it.sourcePageEnd }
        return CatalogLesson(
            id = "$chapterId-$id",
            title = title,
            pageStart = start,
            pageEnd = end,
        )
    }

    private fun parseGrade(value: String): Int {
        value.toIntOrNull()?.let { return it.coerceIn(1, 16) }
        val names = listOf(
            "十六" to 16, "十五" to 15, "十四" to 14, "十三" to 13,
            "十二" to 12, "十一" to 11, "十" to 10, "九" to 9,
            "八" to 8, "七" to 7, "六" to 6, "五" to 5,
            "四" to 4, "三" to 3, "二" to 2, "一" to 1,
        )
        return names.firstOrNull { (name, _) -> value.contains(name) }?.second
            ?: error("课程包缺少有效年级")
    }

    private fun parseVolume(value: String): TextbookVolume {
        val id = if (value == "2" || value.contains("下") || value.contains("第二")) 2 else 1
        return TextbookVolume.fromId(id)
    }
}
