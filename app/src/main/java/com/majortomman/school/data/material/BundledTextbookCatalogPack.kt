package com.majortomman.school.data.material

import android.content.Context
import android.util.Base64
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import org.json.JSONArray
import org.json.JSONObject

private const val UNBOUND_PDF_PATH = "binding/textbook.pdf"

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
        require(selectedSlot.subjectId == subjectId) {
            "识别到$title，请从${stage.label} · $subjectTitle导入"
        }
        require(selectedSlot.stage == stage && selectedSlot.grade == grade && selectedSlot.volume == volume) {
            "识别到$title，请从${stage.label} · ${gradeLabel(grade)} · ${volume.labelFor(stage)}导入"
        }
        require(actualPageCount == pageCount) {
            "教材页数与预制目录不一致：预期 $pageCount 页，实际 $actualPageCount 页"
        }
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

    fun installBound(textbook: InstalledTextbook): InstalledTextbook {
        validate(textbook.slot, textbook.pageCount)
        return install(
            root = File(textbook.pack.rootPath),
            pdfPath = textbook.pack.manifest.pdf.path,
            installedAt = textbook.pack.installedAt,
            bound = true,
        )
    }

    fun installUnbound(context: Context): InstalledTextbook {
        val root = File(context.filesDir, "materials/prebuilt/${slot.key}")
        return install(
            root = root,
            pdfPath = UNBOUND_PDF_PATH,
            installedAt = System.currentTimeMillis(),
            bound = false,
        )
    }

    private fun install(
        root: File,
        pdfPath: String,
        installedAt: Long,
        bound: Boolean,
    ): InstalledTextbook {
        root.mkdirs()
        val manifest = MaterialPackManifest(
            schemaVersion = MATERIAL_PACK_SCHEMA_VERSION,
            packId = "prebuilt-${sha256.take(16)}",
            version = if (bound) BundledTextbookCatalogPack.PACK_VERSION else BundledTextbookCatalogPack.UNBOUND_PACK_VERSION,
            title = title,
            subject = subjectTitle,
            catalogPath = "catalog.json",
            pdf = MaterialPdfAsset(
                path = pdfPath,
                sha256 = sha256,
                pageIndexOffset = pageIndexOffset,
            ),
        )
        val generated = TextbookCatalogParser.generateLessons(slot, catalog())
        writeJson(File(root, "manifest.json"), MaterialPackManifestParser.toJson(manifest), "无法保存预制教材信息")
        writeJson(File(root, manifest.catalogPath), catalogToJson(), "无法保存预制教材目录")
        writeJson(
            File(root, "generated/lessons.json"),
            JSONObject().put("lessons", JSONArray().apply { generated.forEach { put(it.toJson()) } }),
            "无法保存预制课程",
        )
        File(root, "generated/analysis").deleteRecursively()
        generated.forEach { lesson ->
            LessonAnalysisStore.write(root, PrebuiltSubjectAnalysisFactory.create(slot, lesson))
        }
        writeJson(
            File(root, "generated/identity.json"),
            JSONObject()
                .put("sourceMode", if (bound) "PREBUILT_CATALOG" else "PREBUILT_CATALOG_UNBOUND")
                .put("title", title)
                .put("subjectId", subjectId)
                .put("subject", subjectTitle)
                .put("stage", stage.id)
                .put("grade", grade)
                .put("volume", volume.id)
                .put("pageIndexOffset", pageIndexOffset)
                .put("lessonCount", generated.size)
                .put("pdfBound", bound),
            "无法保存预制教材识别信息",
        )
        return InstalledTextbook(
            slot = slot,
            pack = InstalledMaterialPack(
                manifest = manifest,
                rootPath = root.absolutePath,
                installedAt = installedAt,
                sizeBytes = MaterialLibraryStore.directorySize(root),
            ),
            pageCount = pageCount,
            lessons = generated,
        )
    }

    private fun catalogToJson(): JSONObject = JSONObject()
        .put(
            "book",
            JSONObject()
                .put("id", id)
                .put("title", title)
                .put("subject", subjectTitle)
                .put("grade", grade)
                .put("volume", volume.id)
                .put("publisher", publisher)
                .put("edition", edition),
        )
        .put(
            "lessons",
            JSONArray().apply {
                lessons.forEach { lesson ->
                    put(
                        JSONObject()
                            .put("id", lesson.id)
                            .put("title", lesson.title)
                            .put("pageStart", lesson.pageStart)
                            .put("pageEnd", lesson.pageEnd)
                            .put("role", lesson.role)
                            .put("orderIndex", lesson.orderIndex)
                            .put("path", JSONArray().apply { lesson.path.forEach { put(it.toJson()) } }),
                    )
                }
            },
        )
}

internal object BundledTextbookCatalogPack {
    const val PACK_VERSION = "prebuilt-catalog-v1"
    const val UNBOUND_PACK_VERSION = "prebuilt-catalog-unbound-v1"
    private val INDEX_ASSETS = listOf(
        "prebuilt/textbooks/index-00.b64",
        "prebuilt/textbooks/index-01.b64",
        "prebuilt/textbooks/index-02.b64",
        "prebuilt/textbooks/index-03a.b64",
        "prebuilt/textbooks/index-03b.b64",
        "prebuilt/textbooks/index-04a.b64",
        "prebuilt/textbooks/index-04b.b64",
    )
    private var cachedBooks: List<BundledCatalogBook>? = null

    fun installMissing(context: Context) {
        val existing = MaterialLibraryStore.read(context).associateBy { it.slot.key }
        books(context).forEach { book ->
            val current = existing[book.slot.key]
            if (current != null && current.pack.pdfFile.isFile) return@forEach
            if (current?.pack?.manifest?.version in setOf(PACK_VERSION, UNBOUND_PACK_VERSION)) return@forEach
            MaterialLibraryStore.upsert(context, book.installUnbound(context))
        }
    }

    fun find(context: Context, sha256: String, sourceName: String): BundledCatalogBook? {
        val normalized = normalizeTitle(sourceName)
        return books(context).firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }
            ?: books(context).firstOrNull { book ->
                book.aliases.any { normalizeTitle(it) == normalized }
            }
    }

    fun upgradeIfMatched(context: Context, textbook: InstalledTextbook): InstalledTextbook {
        if (textbook.pack.manifest.version in setOf(PACK_VERSION, UNBOUND_PACK_VERSION)) return textbook
        val book = find(
            context = context,
            sha256 = textbook.pack.manifest.pdf.sha256,
            sourceName = textbook.pack.manifest.title,
        ) ?: return textbook
        return book.installBound(textbook).also { upgraded ->
            MaterialLibraryStore.upsert(context, upgraded)
        }
    }

    fun count(context: Context): Int = books(context).size

    fun lessonCount(context: Context): Int = books(context).sumOf { it.lessons.size }

    fun books(context: Context): List<BundledCatalogBook> = synchronized(this) {
        cachedBooks ?: buildString {
            INDEX_ASSETS.forEach { path ->
                context.assets.open(path).bufferedReader(Charsets.US_ASCII).use { append(it.readText()) }
            }
        }.let { encoded ->
            val compressed = Base64.decode(encoded, Base64.DEFAULT)
            GZIPInputStream(compressed.inputStream()).bufferedReader(Charsets.UTF_8).use { reader ->
                val root = JSONObject(reader.readText())
                val array = root.optJSONArray("books") ?: JSONArray()
                buildList {
                    for (bookIndex in 0 until array.length()) {
                        val book = array.getJSONObject(bookIndex)
                        val lessons = book.optJSONArray("lessons") ?: JSONArray()
                        add(
                            BundledCatalogBook(
                                id = book.getString("id"),
                                sha256 = book.getString("sha256"),
                                title = book.getString("title"),
                                aliases = book.optJSONArray("aliases").toStringList(),
                                subjectId = book.getString("subjectId"),
                                subjectTitle = book.getString("subjectTitle"),
                                stage = EducationStage.fromId(book.getString("stage"))
                                    ?: throw IllegalArgumentException("预制教材缺少教育阶段"),
                                grade = book.getInt("grade"),
                                volume = TextbookVolume.fromId(book.getInt("volume")),
                                pageCount = book.getInt("pageCount"),
                                pageIndexOffset = book.getInt("pageIndexOffset"),
                                publisher = book.optString("publisher"),
                                edition = book.optString("edition", "预制教材目录 v1"),
                                lessons = buildList {
                                    for (lessonIndex in 0 until lessons.length()) {
                                        val lesson = lessons.getJSONObject(lessonIndex)
                                        add(
                                            CatalogLesson(
                                                id = lesson.getString("id"),
                                                title = lesson.getString("title"),
                                                pageStart = lesson.getInt("pageStart"),
                                                pageEnd = lesson.getInt("pageEnd"),
                                                role = lesson.optString("role", "CORE"),
                                                path = lesson.optJSONArray("path").toPathNodes(),
                                                orderIndex = lesson.optInt("orderIndex", lessonIndex),
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

private fun writeJson(file: File, json: JSONObject, errorMessage: String) {
    val parent = file.parentFile ?: throw IOException(errorMessage)
    require(parent.mkdirs() || parent.isDirectory) { "无法创建 ${parent.absolutePath}" }
    val temporary = File(parent, ".${file.name}.${System.nanoTime()}.tmp")
    try {
        temporary.writeText(json.toString(2), Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } catch (error: Throwable) {
        temporary.delete()
        throw IOException(errorMessage, error)
    }
}

private fun JSONArray?.toStringList(): List<String> = buildList {
    val source = this@toStringList ?: return@buildList
    for (index in 0 until source.length()) {
        source.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
    }
}

private fun JSONArray?.toPathNodes(): List<CatalogPathNode> = buildList {
    val source = this@toPathNodes ?: return@buildList
    for (index in 0 until source.length()) {
        val root = source.getJSONObject(index)
        val node = CatalogPathNode.fromJson(root)
        if (node.id.isNotBlank() && node.title.isNotBlank()) add(node)
    }
}
