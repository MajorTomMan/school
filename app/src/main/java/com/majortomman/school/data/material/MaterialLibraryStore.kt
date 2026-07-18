package com.majortomman.school.data.material

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal object MaterialLibraryStore {
    private const val ROOT_DIRECTORY = "material-packs"
    private const val LIBRARY_FILE = "library.json"
    private const val LEGACY_INDEX_FILE = "installed.json"
    private const val CLOUD_COURSE_VERSION_PREFIX = "cloud-course-"

    fun materialRoot(context: Context): File = File(context.filesDir, ROOT_DIRECTORY)

    @Synchronized
    fun read(context: Context): List<InstalledTextbook> {
        val libraryFile = File(materialRoot(context), LIBRARY_FILE)
        if (!libraryFile.isFile) return emptyList()
        return runCatching { parseLibrary(libraryFile.readText(Charsets.UTF_8)) }
            .getOrDefault(emptyList())
            .filter(::isAvailableCloudTextbook)
    }

    @Synchronized
    fun upsert(context: Context, textbook: InstalledTextbook) {
        require(isAvailableCloudTextbook(textbook)) { "只能登记已校验的云端课程与教材 PDF" }
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

    /** Removes every local-import or bundled textbook left by older APK versions. */
    @Synchronized
    fun purgeLegacyBundledContent(context: Context): Int {
        val root = materialRoot(context)
        val libraryFile = File(root, LIBRARY_FILE)
        val all = if (libraryFile.isFile) {
            runCatching { parseLibrary(libraryFile.readText(Charsets.UTF_8)) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val cloudOnly = all.filter(::isAvailableCloudTextbook)
        val obsolete = all - cloudOnly.toSet()
        obsolete.forEach { textbook ->
            val path = File(textbook.pack.rootPath)
            if (!path.absolutePath.contains("${File.separator}course-packs${File.separator}active${File.separator}")) {
                path.deleteRecursively()
            }
        }
        write(context, cloudOnly)

        File(root, LEGACY_INDEX_FILE).delete()
        File(root, "packs").deleteRecursively()
        root.listFiles().orEmpty()
            .filter { it.name.startsWith(".processing-") || it.name.startsWith(".backup-") }
            .forEach(File::deleteRecursively)
        File(context.filesDir, "materials/prebuilt").deleteRecursively()
        context.getSharedPreferences("school_pdf_directories", Context.MODE_PRIVATE).edit().clear().apply()
        return obsolete.size
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
                textbooks.filter(::isAvailableCloudTextbook).forEach { put(it.toJson()) }
            },
        )
        temporary.writeText(root.toString(2), Charsets.UTF_8)
        if (target.exists()) target.delete()
        require(temporary.renameTo(target)) { "无法保存教材索引" }
    }

    fun directorySize(directory: File): Long =
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun isAvailableCloudTextbook(textbook: InstalledTextbook): Boolean =
        textbook.pack.manifest.version.startsWith(CLOUD_COURSE_VERSION_PREFIX) &&
            textbook.pack.pdfFile.isFile &&
            textbook.lessons.isNotEmpty()

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
