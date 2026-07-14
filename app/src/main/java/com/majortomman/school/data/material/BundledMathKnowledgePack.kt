package com.majortomman.school.data.material

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

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
        require(slot.subjectId == "math") { "预制知识包只适用于数学教材" }
        require(slot.stage == stage && slot.grade == grade && slot.volume == volume) {
            "识别到$title，请从${stage.label} · ${gradeLabel(grade)} · ${volume.labelFor(stage)}导入"
        }
        require(actualPageCount == pageCount) {
            "教材页数与预制知识包不一致：预期 $pageCount 页，实际 $actualPageCount 页"
        }
    }

    fun toScanResult(): DirectPdfScanResult = DirectPdfScanResult(
        title = title,
        pageIndexOffset = pageIndexOffset,
        catalog = TextbookCatalog(
            book = CatalogBook(
                id = "prebuilt-${sha256.take(16)}",
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
        evidence = "内置数学知识包 · SHA-256 精确匹配 · ${lessons.size} 个知识点",
    )

    fun install(textbook: InstalledTextbook): InstalledTextbook {
        validate(textbook.slot, textbook.pageCount)
        val root = File(textbook.pack.rootPath)
        val scan = toScanResult()
        val manifest = textbook.pack.manifest.copy(
            version = PACK_VERSION,
            title = title,
            subject = "数学",
            pdf = textbook.pack.manifest.pdf.copy(pageIndexOffset = pageIndexOffset),
        )
        File(root, "manifest.json").writeText(
            MaterialPackManifestParser.toJson(manifest).toString(2),
            Charsets.UTF_8,
        )
        File(root, manifest.catalogPath).writeText(
            DirectPdfImportScanner.catalogToJson(scan.catalog).toString(2),
            Charsets.UTF_8,
        )

        val generatedLessons = TextbookCatalogParser.generateLessons(textbook.slot, scan.catalog)
        writeGeneratedLessons(File(root, "generated/lessons.json"), generatedLessons)
        File(root, "generated/analysis").deleteRecursively()
        generatedLessons.forEach { generated ->
            LessonAnalysisStore.write(
                root,
                PrebuiltMathAnalysisFactory.create(textbook.slot, generated),
            )
        }
        File(root, "generated/identity.json").apply {
            parentFile?.mkdirs()
            writeText(
                JSONObject()
                    .put("sourceMode", "PREBUILT_MATH")
                    .put("title", title)
                    .put("subject", "数学")
                    .put("stage", stage.id)
                    .put("grade", grade)
                    .put("volume", volume.id)
                    .put("pageIndexOffset", pageIndexOffset)
                    .put("knowledgePointCount", generatedLessons.size)
                    .put("evidence", scan.evidence)
                    .toString(2),
                Charsets.UTF_8,
            )
        }

        return InstalledTextbook(
            slot = textbook.slot,
            pack = textbook.pack.copy(
                manifest = manifest,
                sizeBytes = MaterialLibraryStore.directorySize(root),
            ),
            pageCount = textbook.pageCount,
            lessons = generatedLessons,
        )
    }

    private fun writeGeneratedLessons(file: File, generatedLessons: List<GeneratedLesson>) {
        file.parentFile?.let { parent ->
            require(parent.mkdirs() || parent.isDirectory) { "无法创建预制课程目录" }
        }
        val root = JSONObject().put(
            "lessons",
            JSONArray().apply { generatedLessons.forEach { put(it.toJson()) } },
        )
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(root.toString(2), Charsets.UTF_8)
        if (file.exists()) file.delete()
        require(temporary.renameTo(file)) { "无法保存预制数学课程" }
    }

    private companion object {
        const val PACK_VERSION = "prebuilt-math-v1"
    }
}

internal data class BundledMathLesson(
    val sourceId: String,
    val title: String,
    val pageStart: Int,
    val pageEnd: Int,
)

internal object BundledMathKnowledgePack {
    private const val INDEX_ASSET = "prebuilt/math/index.json"
    private var cachedBooks: List<BundledMathKnowledgeBook>? = null

    fun find(
        context: Context,
        sha256: String,
        sourceName: String,
    ): BundledMathKnowledgeBook? {
        val books = loadBooks(context)
        val normalizedName = normalizeTitle(sourceName)
        return books.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
            ?: books.firstOrNull { book ->
                book.aliases.any { alias -> normalizeTitle(alias) == normalizedName }
            }
    }

    fun upgradeIfMatched(
        context: Context,
        textbook: InstalledTextbook,
    ): InstalledTextbook {
        if (textbook.pack.manifest.version == "prebuilt-math-v1") return textbook
        val book = find(
            context = context,
            sha256 = textbook.pack.manifest.pdf.sha256,
            sourceName = textbook.pack.manifest.title,
        ) ?: return textbook
        return book.install(textbook).also { upgraded ->
            MaterialLibraryStore.upsert(context, upgraded)
        }
    }

    fun count(context: Context): Int = loadBooks(context).size

    private fun loadBooks(context: Context): List<BundledMathKnowledgeBook> = synchronized(this) {
        cachedBooks ?: readAsset(context, INDEX_ASSET).let { root ->
            val books = root.optJSONArray("books") ?: JSONArray()
            buildList {
                for (bookIndex in 0 until books.length()) {
                    val book = books.getJSONObject(bookIndex)
                    val lessons = book.optJSONArray("lessons") ?: JSONArray()
                    add(
                        BundledMathKnowledgeBook(
                            sha256 = book.getString("sha256"),
                            title = book.getString("title"),
                            aliases = book.optJSONArray("aliases").toStringList(),
                            stage = EducationStage.fromId(book.getString("stage"))
                                ?: throw IllegalArgumentException("预制教材缺少教育阶段"),
                            grade = book.getInt("grade"),
                            volume = TextbookVolume.fromId(book.getInt("volume")),
                            pageCount = book.getInt("pageCount"),
                            pageIndexOffset = book.getInt("pageIndexOffset"),
                            publisher = book.optString("publisher", "人民教育出版社"),
                            edition = book.optString("edition", "预制数学知识包"),
                            lessons = buildList {
                                for (lessonIndex in 0 until lessons.length()) {
                                    val lesson = lessons.getJSONArray(lessonIndex)
                                    add(
                                        BundledMathLesson(
                                            sourceId = lesson.getString(0),
                                            title = lesson.getString(1),
                                            pageStart = lesson.getInt(2),
                                            pageEnd = lesson.getInt(3),
                                        ),
                                    )
                                }
                            },
                        ),
                    )
                }
            }.also { cachedBooks = it }
        }
    }

    private fun readAsset(context: Context, path: String): JSONObject =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { reader ->
            JSONObject(reader.readText())
        }

    private fun normalizeTitle(value: String): String = value
        .substringAfterLast('/')
        .removeSuffix(".pdf")
        .removeSuffix(".PDF")
        .replace("·", "")
        .replace("（", "(")
        .replace("）", ")")
        .replace(Regex("[\\s_\\-]+"), "")
        .lowercase()
}

private fun JSONArray?.toStringList(): List<String> = buildList {
    val source = this@toStringList ?: return@buildList
    for (index in 0 until source.length()) {
        source.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
    }
}
