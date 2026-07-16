package com.majortomman.school.data.material

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal object MaterialLibraryStore {
    private const val ROOT_DIRECTORY = "material-packs"
    private const val PACKS_DIRECTORY = "packs"
    private const val LIBRARY_FILE = "library.json"
    private const val LEGACY_INDEX_FILE = "installed.json"

    fun materialRoot(context: Context): File = File(context.filesDir, ROOT_DIRECTORY)

    fun packsRoot(context: Context): File = File(materialRoot(context), PACKS_DIRECTORY)

    fun processingRoot(context: Context, slot: TextbookSlot): File =
        File(materialRoot(context), ".processing-${slot.key}")

    fun finalRoot(context: Context, slot: TextbookSlot): File =
        File(packsRoot(context), slot.key)

    @Synchronized
    fun read(context: Context): List<InstalledTextbook> {
        val libraryFile = File(materialRoot(context), LIBRARY_FILE)
        if (libraryFile.isFile) {
            return runCatching { parseLibrary(libraryFile.readText(Charsets.UTF_8)) }
                .getOrDefault(emptyList())
                .filter(::isAvailable)
        }

        val migrated = migrateLegacy(context)
        if (migrated.isNotEmpty()) write(context, migrated)
        return migrated
    }

    @Synchronized
    fun upsert(context: Context, textbook: InstalledTextbook) {
        val updated = read(context)
            .filterNot { it.slot.key == textbook.slot.key }
            .plus(textbook)
            .sortedWith(compareBy({ it.slot.subjectTitle }, { it.slot.grade }, { it.slot.volume.id }))
        write(context, updated)
    }

    @Synchronized
    fun remove(context: Context, slotKey: String): InstalledTextbook? {
        val current = read(context)
        val removed = current.firstOrNull { it.slot.key == slotKey }
        write(context, current.filterNot { it.slot.key == slotKey })
        return removed
    }

    @Synchronized
    fun write(context: Context, textbooks: List<InstalledTextbook>) {
        val rootDirectory = materialRoot(context)
        rootDirectory.mkdirs()
        val target = File(rootDirectory, LIBRARY_FILE)
        val temporary = File(rootDirectory, "$LIBRARY_FILE.tmp")
        val root = JSONObject().put(
            "items",
            JSONArray().apply {
                textbooks.forEach { put(it.toJson()) }
            },
        )
        temporary.writeText(root.toString(2), Charsets.UTF_8)
        if (target.exists()) target.delete()
        require(temporary.renameTo(target)) { "无法保存教材索引" }
    }

    fun directorySize(directory: File): Long =
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun isAvailable(textbook: InstalledTextbook): Boolean =
        textbook.pack.pdfFile.isFile ||
            (textbook.lessons.isNotEmpty() && textbook.pack.manifest.version.startsWith("prebuilt-"))

    private fun parseLibrary(json: String): List<InstalledTextbook> {
        val root = JSONObject(json)
        val items = root.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                add(installedFromJson(items.getJSONObject(index)))
            }
        }
    }

    private fun installedFromJson(root: JSONObject): InstalledTextbook {
        val slot = TextbookSlot.fromJson(root.getJSONObject("slot"))
        val manifest = MaterialPackManifestParser.parse(root.getJSONObject("manifest").toString())
        val pack = InstalledMaterialPack(
            manifest = manifest,
            rootPath = root.getString("rootPath"),
            installedAt = root.getLong("installedAt"),
            sizeBytes = root.optLong("sizeBytes", 0L),
        )
        val lessonArray = root.optJSONArray("lessons") ?: JSONArray()
        val lessons = buildList {
            for (index in 0 until lessonArray.length()) {
                add(GeneratedLesson.fromJson(lessonArray.getJSONObject(index)))
            }
        }.ifEmpty {
            loadGeneratedLessons(slot, pack)
        }
        return InstalledTextbook(
            slot = slot,
            pack = pack,
            pageCount = root.optInt("pageCount", 0),
            lessons = lessons,
        )
    }

    private fun InstalledTextbook.toJson(): JSONObject = JSONObject()
        .put("slot", slot.toJson())
        .put("manifest", MaterialPackManifestParser.toJson(pack.manifest))
        .put("rootPath", pack.rootPath)
        .put("installedAt", pack.installedAt)
        .put("sizeBytes", pack.sizeBytes)
        .put("pageCount", pageCount)
        .put(
            "lessons",
            JSONArray().apply { lessons.forEach { put(it.toJson()) } },
        )

    private fun migrateLegacy(context: Context): List<InstalledTextbook> = runCatching {
        val legacy = File(materialRoot(context), LEGACY_INDEX_FILE)
        if (!legacy.isFile) return@runCatching emptyList()
        val root = JSONObject(legacy.readText(Charsets.UTF_8))
        val manifest = MaterialPackManifestParser.parse(root.getJSONObject("manifest").toString())
        val pack = InstalledMaterialPack(
            manifest = manifest,
            rootPath = root.getString("rootPath"),
            installedAt = root.getLong("installedAt"),
            sizeBytes = root.optLong("sizeBytes", 0L),
        )
        if (!pack.pdfFile.isFile) return@runCatching emptyList()
        val slot = detectSlot(pack)
        val lessons = loadGeneratedLessons(slot, pack)
        listOf(
            InstalledTextbook(
                slot = slot,
                pack = pack,
                pageCount = 0,
                lessons = lessons,
            ),
        )
    }.getOrDefault(emptyList())

    private fun detectSlot(pack: InstalledMaterialPack): TextbookSlot {
        val catalogRoot = runCatching { JSONObject(pack.catalogFile.readText(Charsets.UTF_8)) }.getOrNull()
        val book = catalogRoot?.optJSONObject("book")
        val subjectTitle = book?.optString("subject")?.takeIf { it.isNotBlank() }
            ?: pack.manifest.subject
        val subject = SubjectTemplates.findByTitle(subjectTitle)
            ?: SubjectTemplates.all.first()
        val grade = book?.optInt("grade", 7) ?: 7
        val volume = TextbookVolume.fromId(book?.optInt("volume", 1) ?: 1)
        return TextbookSlot(subject.id, subject.title, grade, volume)
    }

    private fun loadGeneratedLessons(
        slot: TextbookSlot,
        pack: InstalledMaterialPack,
    ): List<GeneratedLesson> {
        val generated = File(pack.rootPath, "generated/lessons.json")
        if (generated.isFile) {
            return runCatching {
                val array = JSONObject(generated.readText(Charsets.UTF_8)).getJSONArray("lessons")
                buildList {
                    for (index in 0 until array.length()) {
                        add(GeneratedLesson.fromJson(array.getJSONObject(index)))
                    }
                }
            }.getOrDefault(emptyList())
        }
        return runCatching {
            val catalog = TextbookCatalogParser.parse(
                pack.catalogFile.readText(Charsets.UTF_8),
                pack.manifest,
                slot,
            )
            TextbookCatalogParser.generateLessons(slot, catalog)
        }.getOrDefault(emptyList())
    }
}
