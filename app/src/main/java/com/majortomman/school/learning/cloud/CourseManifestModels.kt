package com.majortomman.school.learning.cloud

import org.json.JSONArray
import org.json.JSONObject

internal const val SUPPORTED_COURSE_SCHEMA_VERSION = 1
internal const val DEFAULT_FULL_DOWNLOAD_THRESHOLD = 0.60

internal data class CourseFileSpec(
    val path: String,
    val url: String,
    val size: Long,
    val sha256: String,
    val inFullPackage: Boolean = true,
)

internal data class CourseTextbookManifest(
    val schemaVersion: Int,
    val contentVersion: Long,
    val id: String,
    val title: String,
    val version: Long,
    val minimumAppVersion: Int,
    val fullPackage: CourseFileSpec,
    val files: List<CourseFileSpec>,
    val deletedFiles: List<String>,
)

internal data class CourseManifest(
    val schemaVersion: Int,
    val contentVersion: Long,
    val generatedAt: String,
    val textbooks: List<CourseTextbookManifest>,
)

internal data class LocalCourseState(
    val schemaVersion: Int,
    val contentVersion: Long,
    val textbookVersion: Long,
    val files: Map<String, LocalCourseFileState>,
)

internal data class LocalCourseFileState(
    val size: Long,
    val sha256: String,
)

internal sealed interface CourseUpdatePlan {
    data object None : CourseUpdatePlan

    data class Full(
        val reason: String,
    ) : CourseUpdatePlan

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
        if (local.schemaVersion != remote.schemaVersion) {
            return CourseUpdatePlan.Full("课程数据结构版本发生变化")
        }
        if (local.textbookVersion > remote.version) return CourseUpdatePlan.None

        val remoteFiles = remote.files.associateBy(CourseFileSpec::path)
        val changed = remote.files.filter { file ->
            local.files[file.path]?.sha256 != file.sha256 || local.files[file.path]?.size != file.size
        }
        val deleted = (
            remote.deletedFiles + (local.files.keys - remoteFiles.keys)
        ).distinct().filterNot(remoteFiles::containsKey)

        if (changed.isEmpty() && deleted.isEmpty()) return CourseUpdatePlan.None
        if (changed.any { it.url.isBlank() }) {
            return CourseUpdatePlan.Full("增量文件缺少下载地址")
        }

        val incrementalSize = changed.sumOf(CourseFileSpec::size)
        val fullSize = remote.fullPackage.size
        if (fullSize <= 0L || remote.fullPackage.url.isBlank()) {
            return CourseUpdatePlan.Incremental(changed, deleted)
        }

        // 完整 ZIP 可以不包含教材 PDF 等外部资源。执行全量安装时，这些发生变化的
        // 外部文件仍然需要单独下载，因此必须计入全量传输成本。
        val changedExternalSize = changed.filterNot(CourseFileSpec::inFullPackage).sumOf(CourseFileSpec::size)
        val fullTransferSize = fullSize + changedExternalSize
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
        val schemaVersion = root.getInt("schemaVersion")
        require(schemaVersion == SUPPORTED_COURSE_SCHEMA_VERSION) {
            "不支持课程数据结构版本 $schemaVersion"
        }
        val contentVersion = root.getLong("contentVersion")
        val textbooksJson = root.getJSONArray("textbooks")
        val textbooks = buildList {
            for (index in 0 until textbooksJson.length()) {
                val textbook = textbooksJson.getJSONObject(index)
                val files = textbook.getJSONArray("files").mapObjects(::decodeFile)
                require(files.map(CourseFileSpec::path).distinct().size == files.size) {
                    "课程 ${textbook.getString("id")} 包含重复文件路径"
                }
                require(files.filterNot(CourseFileSpec::inFullPackage).all { it.url.isNotBlank() }) {
                    "课程 ${textbook.getString("id")} 的外部文件缺少下载地址"
                }
                add(
                    CourseTextbookManifest(
                        schemaVersion = schemaVersion,
                        contentVersion = contentVersion,
                        id = textbook.getString("id").validateIdentifier("教材 ID"),
                        title = textbook.getString("title").trim(),
                        version = textbook.getLong("version"),
                        minimumAppVersion = textbook.optInt("minimumAppVersion", 1),
                        fullPackage = decodeFile(textbook.getJSONObject("fullPackage")),
                        files = files,
                        deletedFiles = textbook.optJSONArray("deletedFiles")?.mapStrings(::validateRelativePath).orEmpty(),
                    ),
                )
            }
        }
        return CourseManifest(
            schemaVersion = schemaVersion,
            contentVersion = contentVersion,
            generatedAt = root.optString("generatedAt"),
            textbooks = textbooks,
        )
    }

    fun decodeLocalState(raw: String): LocalCourseState {
        val root = JSONObject(raw)
        val filesObject = root.getJSONObject("files")
        val files = buildMap {
            filesObject.keys().forEach { path ->
                val item = filesObject.getJSONObject(path)
                put(
                    validateRelativePath(path),
                    LocalCourseFileState(
                        size = item.getLong("size"),
                        sha256 = validateSha256(item.getString("sha256")),
                    ),
                )
            }
        }
        return LocalCourseState(
            schemaVersion = root.getInt("schemaVersion"),
            contentVersion = root.getLong("contentVersion"),
            textbookVersion = root.getLong("textbookVersion"),
            files = files,
        )
    }

    fun encodeLocalState(remote: CourseTextbookManifest): String {
        val files = JSONObject()
        remote.files.forEach { file ->
            files.put(
                file.path,
                JSONObject()
                    .put("size", file.size)
                    .put("sha256", file.sha256),
            )
        }
        return JSONObject()
            .put("schemaVersion", remote.schemaVersion)
            .put("contentVersion", remote.contentVersion)
            .put("textbookVersion", remote.version)
            .put("files", files)
            .toString(2)
    }

    private fun decodeFile(json: JSONObject): CourseFileSpec = CourseFileSpec(
        path = validateRelativePath(json.getString("path")),
        url = json.optString("url").trim(),
        size = json.getLong("size").also { require(it >= 0L) { "课程文件大小不能为负数" } },
        sha256 = validateSha256(json.getString("sha256")),
        inFullPackage = json.optBoolean("inFullPackage", true),
    )

    private fun JSONArray.mapObjects(transform: (JSONObject) -> CourseFileSpec): List<CourseFileSpec> =
        buildList {
            for (index in 0 until length()) add(transform(getJSONObject(index)))
        }

    private fun JSONArray.mapStrings(transform: (String) -> String): List<String> =
        buildList {
            for (index in 0 until length()) add(transform(getString(index)))
        }
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
