package com.majortomman.school.learning.cloud

import org.json.JSONArray
import org.json.JSONObject

internal const val DEFAULT_FULL_DOWNLOAD_THRESHOLD = 0.60

/** Download and integrity metadata shared by archives and individual files. */
internal sealed interface CourseDownloadSpec {
    val path: String
    val url: String
    val size: Long
    val sha256: String
}

internal data class CourseArchiveSpec(
    override val path: String,
    override val url: String,
    override val size: Long,
    override val sha256: String,
) : CourseDownloadSpec

internal data class CourseFileSpec(
    override val path: String,
    override val url: String,
    override val size: Long,
    override val sha256: String,
    val bundled: Boolean,
) : CourseDownloadSpec

internal data class CourseTextbookManifest(
    val id: String,
    val packageFile: CourseArchiveSpec,
    val files: List<CourseFileSpec>,
)

internal data class CourseManifest(
    val textbooks: List<CourseTextbookManifest>,
)

internal data class LocalCourseState(
    val files: Map<String, LocalCourseFileState>,
)

internal data class LocalCourseFileState(
    val size: Long,
    val sha256: String,
)

internal sealed interface CourseUpdatePlan {
    data object None : CourseUpdatePlan

    data class Full(val reason: String) : CourseUpdatePlan

    data class Incremental(
        val changedFiles: List<CourseFileSpec>,
        val deletedFiles: List<String>,
    ) : CourseUpdatePlan
}

internal object CourseUpdatePlanner {
    fun plan(
        remote: CourseTextbookManifest,
        local: LocalCourseState?,
        fullDownloadThreshold: Double = DEFAULT_FULL_DOWNLOAD_THRESHOLD,
    ): CourseUpdatePlan {
        require(fullDownloadThreshold in 0.0..1.0) { "全量下载阈值必须在 0 到 1 之间" }
        if (local == null) return CourseUpdatePlan.Full("本地没有课程包")

        val remoteFiles = remote.files.associateBy(CourseFileSpec::path)
        val changed = remote.files.filter { file ->
            local.files[file.path]?.let { it.size == file.size && it.sha256 == file.sha256 } != true
        }
        val deleted = (local.files.keys - remoteFiles.keys).sorted()
        if (changed.isEmpty() && deleted.isEmpty()) return CourseUpdatePlan.None
        if (changed.any { it.url.isBlank() }) return CourseUpdatePlan.Full("增量文件缺少下载地址")

        val incrementalSize = changed.sumOf(CourseFileSpec::size)
        val changedExternalSize = changed.filterNot(CourseFileSpec::bundled).sumOf(CourseFileSpec::size)
        val fullTransferSize = remote.packageFile.size + changedExternalSize
        return if (incrementalSize.toDouble() < fullTransferSize.toDouble() * fullDownloadThreshold) {
            CourseUpdatePlan.Incremental(changed, deleted)
        } else {
            CourseUpdatePlan.Full("增量下载体积接近完整课程包")
        }
    }
}

internal object CourseManifestCodec {
    fun decode(raw: String): CourseManifest {
        val root = JSONObject(raw)
        root.requireManifestKeys("textbooks")
        val textbooksJson = root.optJSONArray("textbooks") ?: error("课程清单缺少 textbooks")
        require(textbooksJson.length() > 0) { "课程清单不包含教材" }
        val textbooks = textbooksJson.manifestObjects().map { textbook ->
            textbook.requireManifestKeys("id", "package", "files")
            val id = textbook.getString("id").validateIdentifier("教材 ID")
            val files = textbook.getJSONArray("files").manifestObjects().map(::decodeFile)
            require(files.isNotEmpty()) { "课程 $id 不包含文件" }
            require(files.map(CourseFileSpec::path).distinct().size == files.size) { "课程 $id 包含重复文件路径" }
            require(files.any { it.path == COURSE_FILE_NAME && it.bundled }) {
                "课程 $id 必须在完整包中包含 $COURSE_FILE_NAME"
            }
            require(files.filterNot(CourseFileSpec::bundled).all { it.url.isNotBlank() }) {
                "课程 $id 的外部文件缺少下载地址"
            }
            CourseTextbookManifest(
                id = id,
                packageFile = decodeArchive(textbook.getJSONObject("package")).also {
                    require(it.path.endsWith(".zip", ignoreCase = true)) { "课程 $id 的完整包必须是 ZIP" }
                    require(it.url.isNotBlank()) { "课程 $id 缺少完整包下载地址" }
                },
                files = files,
            )
        }
        require(textbooks.map(CourseTextbookManifest::id).distinct().size == textbooks.size) {
            "课程清单包含重复教材 ID"
        }
        return CourseManifest(textbooks)
    }

    fun decodeLocalState(raw: String): LocalCourseState {
        val root = JSONObject(raw)
        root.requireManifestKeys("files")
        val filesObject = root.getJSONObject("files")
        val files = buildMap {
            filesObject.keys().forEach { path ->
                val item = filesObject.getJSONObject(path)
                item.requireManifestKeys("size", "sha256")
                put(
                    validateRelativePath(path),
                    LocalCourseFileState(
                        size = item.getLong("size").also { require(it >= 0L) { "课程文件大小不能为负数" } },
                        sha256 = validateSha256(item.getString("sha256")),
                    ),
                )
            }
        }
        return LocalCourseState(files)
    }

    fun encodeLocalState(remote: CourseTextbookManifest): String {
        val files = JSONObject()
        remote.files.forEach { file ->
            files.put(file.path, JSONObject().put("size", file.size).put("sha256", file.sha256))
        }
        return JSONObject().put("files", files).toString(2)
    }


    private fun decodeArchive(json: JSONObject): CourseArchiveSpec {
        json.requireManifestKeys("path", "url", "size", "sha256")
        return CourseArchiveSpec(
            path = validateRelativePath(json.getString("path")),
            url = json.optString("url").trim(),
            size = json.getLong("size").also { require(it > 0L) { "课程完整包大小必须大于 0" } },
            sha256 = validateSha256(json.getString("sha256")),
        )
    }

    private fun decodeFile(json: JSONObject): CourseFileSpec {
        json.requireManifestKeys("path", "url", "size", "sha256", "bundled")
        val path = validateRelativePath(json.getString("path"))
        require(path !in RESERVED_COURSE_PATHS && RESERVED_COURSE_PREFIXES.none(path::startsWith)) {
            "课程文件占用了 APK 保留路径：$path"
        }
        return CourseFileSpec(
            path = path,
            url = json.optString("url").trim(),
            size = json.getLong("size").also { require(it > 0L) { "课程文件大小必须大于 0" } },
            sha256 = validateSha256(json.getString("sha256")),
            bundled = json.getBoolean("bundled"),
        )
    }

    private const val COURSE_FILE_NAME = "course.json"
    private val RESERVED_COURSE_PATHS = setOf(".course-state.json")
    private val RESERVED_COURSE_PREFIXES = setOf("generated/")
}

internal fun validateRelativePath(value: String): String {
    val normalized = value.trim().replace('\\', '/')
    require(normalized.isNotBlank()) { "课程文件路径不能为空" }
    require(!normalized.startsWith('/')) { "课程文件路径不能是绝对路径：$normalized" }
    require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
        "课程文件路径不安全：$normalized"
    }
    return normalized
}

private fun String.validateIdentifier(label: String): String {
    val normalized = trim()
    require(normalized.matches(Regex("[A-Za-z0-9._-]+"))) { "$label 格式无效：$normalized" }
    return normalized
}

internal fun validateSha256(value: String): String {
    val normalized = value.lowercase().filterNot(Char::isWhitespace)
    require(normalized.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 格式无效" }
    return normalized
}

private fun JSONObject.requireManifestKeys(vararg allowed: String) {
    val allowedSet = allowed.toSet()
    val unknown = keys().asSequence().filterNot(allowedSet::contains).toList()
    require(unknown.isEmpty()) { "课程清单包含未知字段：${unknown.joinToString()}" }
}

private fun JSONArray.manifestObjects(): List<JSONObject> = buildList {
    for (index in 0 until length()) add(getJSONObject(index))
}
