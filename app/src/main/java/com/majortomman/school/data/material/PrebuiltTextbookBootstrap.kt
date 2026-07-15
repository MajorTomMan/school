package com.majortomman.school.data.material

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 在应用进程启动时把内置数学知识包安装为“未绑定 PDF”的本地课程。
 *
 * 课程、题目和学习记录可以独立使用；用户后续绑定匹配 PDF 后，原有导入流程会
 * 覆盖同一教材槽位并解锁教材原页。这里不会启动 OCR，也不会复制任何教材正文。
 */
class PrebuiltTextbookBootstrapProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.applicationContext?.let { appContext ->
            runCatching { PrebuiltTextbookBootstrap.installMissing(appContext) }
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

internal object PrebuiltTextbookBootstrap {
    private const val INDEX_ASSET = "prebuilt/math/index.json"
    private const val PACK_VERSION = "prebuilt-math-unbound-v1"
    private const val UNBOUND_PDF_PATH = "binding/textbook.pdf"

    fun installMissing(context: android.content.Context) {
        val existing = MaterialLibraryStore.read(context).associateBy { it.slot.key }
        loadBooks(context).forEach { book ->
            val current = existing[book.slot.key]
            if (current != null && current.pack.pdfFile.isFile) return@forEach
            if (current?.pack?.manifest?.version == PACK_VERSION) return@forEach
            MaterialLibraryStore.upsert(context, book.install(context))
        }
    }

    private fun loadBooks(context: android.content.Context): List<BootstrapBook> =
        context.assets.open(INDEX_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
            val books = JSONObject(reader.readText()).optJSONArray("books") ?: JSONArray()
            buildList {
                for (bookIndex in 0 until books.length()) {
                    val root = books.getJSONObject(bookIndex)
                    val stage = EducationStage.fromId(root.getString("stage")) ?: continue
                    val slot = TextbookSlot(
                        subjectId = "math",
                        subjectTitle = "数学",
                        grade = root.getInt("grade"),
                        volume = TextbookVolume.fromId(root.getInt("volume")),
                        stage = stage,
                    )
                    val lessonArray = root.optJSONArray("lessons") ?: JSONArray()
                    val lessons = buildList {
                        for (index in 0 until lessonArray.length()) {
                            val lesson = lessonArray.getJSONArray(index)
                            add(
                                CatalogLesson(
                                    id = lesson.getString(0),
                                    title = lesson.getString(1),
                                    pageStart = lesson.getInt(2),
                                    pageEnd = lesson.getInt(3),
                                ),
                            )
                        }
                    }
                    add(
                        BootstrapBook(
                            slot = slot,
                            sha256 = root.getString("sha256"),
                            title = root.getString("title"),
                            pageCount = root.getInt("pageCount"),
                            pageIndexOffset = root.getInt("pageIndexOffset"),
                            publisher = root.optString("publisher", "人民教育出版社"),
                            edition = root.optString("edition", "预制数学知识包"),
                            lessons = lessons,
                        ),
                    )
                }
            }
        }

    private data class BootstrapBook(
        val slot: TextbookSlot,
        val sha256: String,
        val title: String,
        val pageCount: Int,
        val pageIndexOffset: Int,
        val publisher: String,
        val edition: String,
        val lessons: List<CatalogLesson>,
    ) {
        fun install(context: android.content.Context): InstalledTextbook {
            val root = File(context.filesDir, "materials/prebuilt/${slot.key}")
            root.mkdirs()
            val manifest = MaterialPackManifest(
                schemaVersion = MATERIAL_PACK_SCHEMA_VERSION,
                packId = "prebuilt-${sha256.take(16)}",
                version = PACK_VERSION,
                title = title,
                subject = "数学",
                catalogPath = "catalog.json",
                pdf = MaterialPdfAsset(
                    path = UNBOUND_PDF_PATH,
                    sha256 = sha256,
                    pageIndexOffset = pageIndexOffset,
                ),
            )
            val catalog = TextbookCatalog(
                book = CatalogBook(
                    id = manifest.packId,
                    title = title,
                    subject = "数学",
                    grade = slot.grade,
                    volume = slot.volume,
                    publisher = publisher,
                    edition = edition,
                ),
                lessons = lessons,
            )
            val generated = TextbookCatalogParser.generateLessons(slot, catalog)

            writeJson(File(root, "manifest.json"), MaterialPackManifestParser.toJson(manifest))
            writeJson(File(root, manifest.catalogPath), DirectPdfImportScanner.catalogToJson(catalog))
            writeJson(
                File(root, "generated/lessons.json"),
                JSONObject().put("lessons", JSONArray().apply { generated.forEach { put(it.toJson()) } }),
            )
            generated.forEach { lesson ->
                LessonAnalysisStore.write(root, PrebuiltMathAnalysisFactory.create(slot, lesson))
            }
            writeJson(
                File(root, "generated/identity.json"),
                JSONObject()
                    .put("sourceMode", "PREBUILT_MATH_UNBOUND")
                    .put("title", title)
                    .put("stage", slot.stage.id)
                    .put("grade", slot.grade)
                    .put("volume", slot.volume.id)
                    .put("knowledgePointCount", generated.size)
                    .put("pdfBound", false),
            )

            return InstalledTextbook(
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
        }
    }

    private fun writeJson(file: File, json: JSONObject) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".${file.name}.tmp")
        temporary.writeText(json.toString(2), Charsets.UTF_8)
        if (file.exists()) file.delete()
        require(temporary.renameTo(file)) { "无法保存预制教材数据：${file.name}" }
    }
}
