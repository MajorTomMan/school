package com.majortomman.school.data.material

import com.majortomman.school.data.Lesson
import com.majortomman.school.data.MasteryStatus
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

const val MATERIAL_PACK_SCHEMA_VERSION = 1
const val IMPORT_TUTORIAL_VERSION = 2

enum class EducationStage(
    val id: String,
    val label: String,
    val grades: IntRange,
) {
    PRIMARY("primary", "小学", 1..6),
    JUNIOR_HIGH("junior-high", "初中", 7..9),
    SENIOR_HIGH("senior-high", "高中", 10..12),
    UNIVERSITY("university", "大学", 13..16);

    companion object {
        fun fromId(id: String?): EducationStage? = entries.firstOrNull { it.id == id }

        fun fromGrade(grade: Int): EducationStage = entries.firstOrNull { grade in it.grades }
            ?: when {
                grade <= 6 -> PRIMARY
                grade <= 9 -> JUNIOR_HIGH
                grade <= 12 -> SENIOR_HIGH
                else -> UNIVERSITY
            }
    }
}

enum class TextbookVolume(
    val id: Int,
    val label: String,
) {
    FIRST(1, "上册"),
    SECOND(2, "下册");

    fun labelFor(stage: EducationStage): String = when (stage) {
        EducationStage.PRIMARY, EducationStage.JUNIOR_HIGH -> label
        EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY -> if (this == FIRST) "上学期" else "下学期"
    }

    companion object {
        fun fromId(id: Int): TextbookVolume = entries.firstOrNull { it.id == id } ?: FIRST
    }
}

data class SubjectTemplate(
    val id: String,
    val title: String,
    val stages: Set<EducationStage>,
) {
    fun gradesFor(stage: EducationStage): IntRange = stage.grades
}

object SubjectTemplates {
    val all = listOf(
        SubjectTemplate("chinese", "语文", setOf(EducationStage.PRIMARY, EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH)),
        SubjectTemplate("math", "数学", EducationStage.entries.toSet()),
        SubjectTemplate("english", "英语", setOf(EducationStage.PRIMARY, EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("science", "科学", setOf(EducationStage.PRIMARY)),
        SubjectTemplate("physics", "物理", setOf(EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("chemistry", "化学", setOf(EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("biology", "生物", setOf(EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("history", "历史", setOf(EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("geography", "地理", setOf(EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("politics", "思想政治", setOf(EducationStage.JUNIOR_HIGH, EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("computer", "计算机", setOf(EducationStage.SENIOR_HIGH, EducationStage.UNIVERSITY)),
        SubjectTemplate("economics", "经济学", setOf(EducationStage.UNIVERSITY)),
        SubjectTemplate("law", "法学", setOf(EducationStage.UNIVERSITY)),
    )

    fun forStage(stage: EducationStage): List<SubjectTemplate> = all.filter { stage in it.stages }

    fun find(id: String): SubjectTemplate? = all.firstOrNull { it.id == id }

    fun findByTitle(title: String): SubjectTemplate? = all.firstOrNull { it.title == title.trim() }
}

data class TextbookSlot(
    val subjectId: String,
    val subjectTitle: String,
    val grade: Int,
    val volume: TextbookVolume,
    val stage: EducationStage = EducationStage.fromGrade(grade),
) {
    val key: String
        get() = "$subjectId-$grade-${volume.id}"

    val levelLabel: String
        get() = gradeLabel(grade)

    val volumeLabel: String
        get() = volume.labelFor(stage)

    val displayTitle: String
        get() = "$levelLabel$subjectTitle$volumeLabel"

    fun toJson(): JSONObject = JSONObject()
        .put("subjectId", subjectId)
        .put("subjectTitle", subjectTitle)
        .put("grade", grade)
        .put("volume", volume.id)
        .put("stage", stage.id)

    companion object {
        fun fromJson(root: JSONObject): TextbookSlot {
            val grade = root.getInt("grade")
            return TextbookSlot(
                subjectId = root.getString("subjectId"),
                subjectTitle = root.getString("subjectTitle"),
                grade = grade,
                volume = TextbookVolume.fromId(root.getInt("volume")),
                stage = EducationStage.fromId(root.optString("stage")) ?: EducationStage.fromGrade(grade),
            )
        }

        fun fromKey(key: String): TextbookSlot? {
            val parts = key.split('-')
            if (parts.size < 3) return null
            val subjectId = parts.dropLast(2).joinToString("-")
            val grade = parts[parts.lastIndex - 1].toIntOrNull() ?: return null
            val volume = parts.last().toIntOrNull()?.let(TextbookVolume::fromId) ?: return null
            val subject = SubjectTemplates.find(subjectId) ?: return null
            return TextbookSlot(
                subjectId = subject.id,
                subjectTitle = subject.title,
                grade = grade,
                volume = volume,
                stage = EducationStage.fromGrade(grade),
            )
        }
    }
}

fun gradeLabel(grade: Int): String = when (grade) {
    1 -> "一年级"
    2 -> "二年级"
    3 -> "三年级"
    4 -> "四年级"
    5 -> "五年级"
    6 -> "六年级"
    7 -> "七年级"
    8 -> "八年级"
    9 -> "九年级"
    10 -> "高一"
    11 -> "高二"
    12 -> "高三"
    13 -> "大一"
    14 -> "大二"
    15 -> "大三"
    16 -> "大四"
    else -> "第${grade}学年"
}

data class MaterialPdfAsset(
    val path: String,
    val sha256: String,
    val pageIndexOffset: Int,
)

data class MaterialPackManifest(
    val schemaVersion: Int,
    val packId: String,
    val version: String,
    val title: String,
    val subject: String,
    val catalogPath: String,
    val pdf: MaterialPdfAsset,
)

data class CatalogBook(
    val id: String,
    val title: String,
    val subject: String,
    val grade: Int,
    val volume: TextbookVolume,
    val publisher: String,
    val edition: String,
)

data class CatalogLesson(
    val id: String,
    val title: String,
    val pageStart: Int,
    val pageEnd: Int,
)

data class TextbookCatalog(
    val book: CatalogBook,
    val lessons: List<CatalogLesson>,
)

data class GeneratedLesson(
    val id: String,
    val sourceId: String,
    val title: String,
    val subtitle: String,
    val estimatedMinutes: Int,
    val pageStart: Int,
    val pageEnd: Int,
    val objectives: List<String>,
    val explanation: String,
    val commonMistake: String,
) {
    fun toLesson(status: MasteryStatus): Lesson = Lesson(
        id = id,
        title = title,
        subtitle = subtitle,
        estimatedMinutes = estimatedMinutes,
        textbookPages = pageStart..pageEnd,
        status = status,
        objectives = objectives,
        explanation = explanation,
        commonMistake = commonMistake,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("sourceId", sourceId)
        .put("title", title)
        .put("subtitle", subtitle)
        .put("estimatedMinutes", estimatedMinutes)
        .put("pageStart", pageStart)
        .put("pageEnd", pageEnd)
        .put("objectives", JSONArray(objectives))
        .put("explanation", explanation)
        .put("commonMistake", commonMistake)

    companion object {
        fun fromJson(root: JSONObject): GeneratedLesson = GeneratedLesson(
            id = root.getString("id"),
            sourceId = root.optString("sourceId", root.getString("id")),
            title = root.getString("title"),
            subtitle = root.getString("subtitle"),
            estimatedMinutes = root.optInt("estimatedMinutes", 18),
            pageStart = root.getInt("pageStart"),
            pageEnd = root.getInt("pageEnd"),
            objectives = root.getJSONArray("objectives").toStringList(),
            explanation = root.getString("explanation"),
            commonMistake = root.getString("commonMistake"),
        )
    }
}

data class InstalledMaterialPack(
    val manifest: MaterialPackManifest,
    val rootPath: String,
    val installedAt: Long,
    val sizeBytes: Long,
) {
    val pdfFile: File
        get() = File(rootPath, manifest.pdf.path)

    val catalogFile: File
        get() = File(rootPath, manifest.catalogPath)

    fun printedPageToPdfIndex(printedPage: Int): Int =
        (printedPage - 1 + manifest.pdf.pageIndexOffset).coerceAtLeast(0)

    fun pdfIndexToPrintedPage(pdfIndex: Int): Int =
        (pdfIndex - manifest.pdf.pageIndexOffset + 1).coerceAtLeast(1)
}

data class InstalledTextbook(
    val slot: TextbookSlot,
    val pack: InstalledMaterialPack,
    val pageCount: Int,
    val lessons: List<GeneratedLesson>,
) {
    val key: String
        get() = slot.key
}

enum class TextbookProcessingStage(val label: String) {
    PREPARING("准备教材"),
    EXTRACTING("复制 PDF"),
    VALIDATING("校验 PDF"),
    IDENTIFYING("识别教材信息"),
    INDEXING("建立页面索引"),
    GENERATING_COURSES("生成课程"),
    FINALIZING("完成安装"),
    COMPLETED("处理完成"),
}

enum class TextbookProcessingStatus {
    QUEUED,
    RUNNING,
    FAILED,
}

data class TextbookProcessingState(
    val slot: TextbookSlot,
    val status: TextbookProcessingStatus,
    val stage: TextbookProcessingStage,
    val progress: Int,
    val message: String,
)

data class MaterialLibraryState(
    val installedTextbooks: List<InstalledTextbook> = emptyList(),
    val processing: Map<String, TextbookProcessingState> = emptyMap(),
    val message: String? = null,
) {
    fun installed(slot: TextbookSlot): InstalledTextbook? =
        installedTextbooks.firstOrNull { it.slot.key == slot.key }

    fun processing(slot: TextbookSlot): TextbookProcessingState? = processing[slot.key]
}

object TextbookCatalogParser {
    fun parse(
        json: String,
        manifest: MaterialPackManifest,
        selectedSlot: TextbookSlot,
    ): TextbookCatalog {
        val root = JSONObject(json)
        val bookObject = root.optJSONObject("book")
        val subject = bookObject?.optString("subject")?.takeIf { it.isNotBlank() }
            ?: manifest.subject
        val grade = bookObject?.optInt("grade", selectedSlot.grade) ?: selectedSlot.grade
        val volume = TextbookVolume.fromId(bookObject?.optInt("volume", selectedSlot.volume.id) ?: selectedSlot.volume.id)
        val book = CatalogBook(
            id = bookObject?.optString("id")?.takeIf { it.isNotBlank() }
                ?: root.optString("bookId").takeIf { it.isNotBlank() }
                ?: manifest.packId,
            title = bookObject?.optString("title")?.takeIf { it.isNotBlank() } ?: manifest.title,
            subject = subject,
            grade = grade,
            volume = volume,
            publisher = bookObject?.optString("publisher").orEmpty(),
            edition = bookObject?.optString("edition").orEmpty(),
        )

        require(book.grade == selectedSlot.grade) {
            "所选教材属于${gradeLabel(book.grade)}，与当前${gradeLabel(selectedSlot.grade)}不一致"
        }
        require(book.volume == selectedSlot.volume) {
            "所选教材为${book.volume.labelFor(selectedSlot.stage)}，与当前${selectedSlot.volumeLabel}不一致"
        }
        require(book.subject == selectedSlot.subjectTitle || manifest.subject == selectedSlot.subjectTitle) {
            "所选教材科目为${book.subject}，与当前${selectedSlot.subjectTitle}不一致"
        }

        val chapters = root.optJSONArray("chapters") ?: JSONArray()
        val lessons = buildList {
            for (chapterIndex in 0 until chapters.length()) {
                val lessonArray = chapters.getJSONObject(chapterIndex).optJSONArray("lessons") ?: continue
                for (lessonIndex in 0 until lessonArray.length()) {
                    val lesson = lessonArray.getJSONObject(lessonIndex)
                    val id = lesson.optString("id").trim()
                    val title = lesson.optString("title").trim()
                    require(id.isNotBlank() && title.isNotBlank()) { "catalog.json 中课程缺少 id 或 title" }
                    val pages = lesson.optJSONArray("pages") ?: throw IllegalArgumentException("课程 $title 缺少 pages")
                    require(pages.length() >= 1) { "课程 $title 的 pages 不能为空" }
                    val start = pages.getInt(0)
                    val end = if (pages.length() >= 2) pages.getInt(1) else start
                    require(start > 0 && end >= start) { "课程 $title 的页码范围无效" }
                    add(CatalogLesson(id, title, start, end))
                }
            }
        }
        require(lessons.isNotEmpty()) { "catalog.json 中没有可生成的课程" }
        return TextbookCatalog(book, lessons)
    }

    fun generateLessons(slot: TextbookSlot, catalog: TextbookCatalog): List<GeneratedLesson> =
        catalog.lessons.map { source ->
            val pageLabel = if (source.pageStart == source.pageEnd) {
                "教材第 ${source.pageStart} 页"
            } else {
                "教材第 ${source.pageStart}—${source.pageEnd} 页"
            }
            GeneratedLesson(
                id = "${slot.key}:${source.id}",
                sourceId = source.id,
                title = source.title,
                subtitle = "$pageLabel · 来自教材扫描结果",
                estimatedMinutes = ((source.pageEnd - source.pageStart + 1) * 3).coerceIn(12, 36),
                pageStart = source.pageStart,
                pageEnd = source.pageEnd,
                objectives = listOf(
                    "理解${source.title}的核心概念",
                    "能说明教材中的定义与例题依据",
                    "完成${source.title}的独立练习",
                ),
                explanation = "本节课程依据《${catalog.book.title}》$pageLabel 的目录与页面范围生成。学习时可以随时回到教材原页核对定义、图形和例题。",
                commonMistake = "不要只记结论。遇到不确定的条件时，先返回$pageLabel，确认教材原文和例题中的适用范围。",
            )
        }
}

object MaterialPackManifestParser {
    private val packIdPattern = Regex("[a-z0-9][a-z0-9._-]{2,63}")
    private val sha256Pattern = Regex("[0-9a-fA-F]{64}")

    fun parse(json: String): MaterialPackManifest {
        val root = JSONObject(json)
        val schemaVersion = root.requireInt("schemaVersion")
        require(schemaVersion == MATERIAL_PACK_SCHEMA_VERSION) {
            "不支持的教材包格式版本：$schemaVersion"
        }

        val packId = root.requireString("packId")
        require(packIdPattern.matches(packId)) {
            "packId 只能包含小写字母、数字、点、下划线和短横线，长度为 3—64"
        }

        val version = root.requireString("version")
        require(version.length <= 64) { "教材包版本号过长" }
        val title = root.requireString("title")
        require(title.length <= 120) { "教材标题过长" }
        val subject = root.requireString("subject")
        require(subject.length <= 40) { "科目名称过长" }
        val catalogPath = safeRelativePath(root.optString("catalog", "catalog.json"), "catalog")

        val pdfObject = root.optJSONObject("pdf")
            ?: throw IllegalArgumentException("manifest.json 缺少 pdf 对象")
        val pdfPath = safeRelativePath(pdfObject.requireString("path"), "pdf.path")
        require(pdfPath.endsWith(".pdf", ignoreCase = true)) { "pdf.path 必须指向 PDF 文件" }
        val sha256 = pdfObject.requireString("sha256").lowercase()
        require(sha256Pattern.matches(sha256)) { "pdf.sha256 必须是 64 位十六进制摘要" }
        val pageIndexOffset = pdfObject.optInt("pageIndexOffset", 0)
        require(pageIndexOffset in -10_000..10_000) { "pdf.pageIndexOffset 超出允许范围" }

        return MaterialPackManifest(
            schemaVersion = schemaVersion,
            packId = packId,
            version = version,
            title = title,
            subject = subject,
            catalogPath = catalogPath,
            pdf = MaterialPdfAsset(
                path = pdfPath,
                sha256 = sha256,
                pageIndexOffset = pageIndexOffset,
            ),
        )
    }

    fun toJson(manifest: MaterialPackManifest): JSONObject = JSONObject()
        .put("schemaVersion", manifest.schemaVersion)
        .put("packId", manifest.packId)
        .put("version", manifest.version)
        .put("title", manifest.title)
        .put("subject", manifest.subject)
        .put("catalog", manifest.catalogPath)
        .put(
            "pdf",
            JSONObject()
                .put("path", manifest.pdf.path)
                .put("sha256", manifest.pdf.sha256)
                .put("pageIndexOffset", manifest.pdf.pageIndexOffset),
        )

    fun safeRelativePath(raw: String, field: String): String {
        val normalized = raw.trim().replace('\\', '/')
        require(normalized.isNotEmpty()) { "$field 不能为空" }
        require(!normalized.startsWith('/')) { "$field 不能是绝对路径" }
        require(!Regex("^[A-Za-z]:").containsMatchIn(normalized)) { "$field 不能是绝对路径" }
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        require(parts.isNotEmpty() && parts.none { it == ".." }) { "$field 包含不安全路径" }
        return parts.joinToString("/")
    }

    private fun JSONObject.requireString(name: String): String {
        val value = optString(name).trim()
        require(value.isNotEmpty()) { "manifest.json 缺少 $name" }
        return value
    }

    private fun JSONObject.requireInt(name: String): Int {
        require(has(name)) { "manifest.json 缺少 $name" }
        return getInt(name)
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) add(getString(index))
}
