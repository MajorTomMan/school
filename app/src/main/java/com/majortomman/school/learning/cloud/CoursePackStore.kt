package com.majortomman.school.learning.cloud

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.json.JSONObject

internal class CoursePackStore(context: Context) {
    private val root = File(context.filesDir, "course-packs")
    private val activeRoot = File(root, "active")
    private val stagingRoot = File(root, "staging")
    private val backupRoot = File(root, "backup")
    private val downloadsRoot = File(root, "downloads")

    init {
        activeRoot.mkdirs()
        stagingRoot.mkdirs()
        backupRoot.mkdirs()
        downloadsRoot.mkdirs()
    }

    fun readLocalState(textbookId: String): LocalCourseState? {
        val stateFile = File(File(activeRoot, textbookId), STATE_FILE_NAME)
        if (!stateFile.isFile) return null
        return runCatching {
            CourseManifestCodec.decodeLocalState(stateFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun temporaryDownloadFile(textbookId: String, suffix: String): File {
        downloadsRoot.mkdirs()
        return File(downloadsRoot, "$textbookId-$suffix.part").apply { delete() }
    }

    fun installFull(remote: CourseTextbookManifest, packageFile: File) {
        val staging = prepareStaging(remote.id)
        try {
            unzip(packageFile, staging)
            validateStaging(remote, staging)
            writeState(remote, staging)
            activate(remote.id, staging)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun installIncremental(
        remote: CourseTextbookManifest,
        plan: CourseUpdatePlan.Incremental,
        download: (CourseFileSpec, File) -> Unit,
    ) {
        val active = File(activeRoot, remote.id)
        require(active.isDirectory) { "本地课程不存在，不能执行增量更新" }
        val staging = prepareStaging(remote.id)
        try {
            require(active.copyRecursively(staging, overwrite = true)) { "无法复制本地课程缓存" }
            plan.deletedFiles.forEach { path -> safeResolve(staging, path).deleteRecursively() }
            plan.changedFiles.forEach { file ->
                val destination = safeResolve(staging, file.path)
                val destinationParent = requireNotNull(destination.parentFile) { "课程文件缺少父目录" }
                destinationParent.mkdirs()
                val partial = File(destinationParent, "${destination.name}.part")
                partial.delete()
                download(file, partial)
                verifyFile(partial, file)
                destination.delete()
                require(partial.renameTo(destination)) { "无法保存课程文件 ${file.path}" }
            }
            validateStaging(remote, staging)
            writeState(remote, staging)
            activate(remote.id, staging)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun prepareStaging(textbookId: String): File =
        File(stagingRoot, textbookId).apply {
            deleteRecursively()
            require(mkdirs()) { "无法创建课程暂存目录" }
        }

    private fun validateStaging(remote: CourseTextbookManifest, staging: File) {
        remote.files.forEach { spec -> verifyFile(safeResolve(staging, spec.path), spec) }
        val courseFile = File(staging, COURSE_FILE_NAME)
        require(courseFile.isFile) { "课程包缺少 $COURSE_FILE_NAME" }
        CloudCourseCodec.validate(JSONObject(courseFile.readText(Charsets.UTF_8)))
    }

    private fun writeState(remote: CourseTextbookManifest, staging: File) {
        val target = File(staging, STATE_FILE_NAME)
        val partial = File(staging, "$STATE_FILE_NAME.part")
        partial.writeText(CourseManifestCodec.encodeLocalState(remote), Charsets.UTF_8)
        target.delete()
        require(partial.renameTo(target)) { "无法保存课程缓存状态" }
    }

    private fun activate(textbookId: String, staging: File) {
        val active = File(activeRoot, textbookId)
        val backup = File(backupRoot, textbookId)
        backup.deleteRecursively()
        if (active.exists()) {
            require(active.renameTo(backup)) { "无法备份旧课程缓存" }
        }
        if (!staging.renameTo(active)) {
            active.deleteRecursively()
            if (backup.exists()) backup.renameTo(active)
            error("无法启用新的课程缓存")
        }
    }

    private fun unzip(zipFile: File, destination: File) {
        var totalBytes = 0L
        ZipInputStream(FileInputStream(zipFile).buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val rawEntryPath = entry.name.trimEnd('/')
                if (rawEntryPath.isBlank()) {
                    input.closeEntry()
                    continue
                }
                val entryPath = validateRelativePath(rawEntryPath)
                val target = safeResolve(destination, entryPath)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            totalBytes += count
                            require(totalBytes <= MAX_UNCOMPRESSED_PACKAGE_BYTES) {
                                "课程包解压后超过大小限制"
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                input.closeEntry()
            }
        }
    }

    private fun verifyFile(file: File, spec: CourseFileSpec) {
        require(file.isFile) { "课程文件缺失：${spec.path}" }
        require(file.length() == spec.size) { "课程文件大小校验失败：${spec.path}" }
        require(sha256(file) == spec.sha256) { "课程文件 SHA-256 校验失败：${spec.path}" }
    }

    private fun safeResolve(parent: File, relativePath: String): File {
        val target = File(parent, validateRelativePath(relativePath))
        val parentPath = parent.canonicalFile.toPath()
        val targetPath = target.canonicalFile.toPath()
        require(targetPath.startsWith(parentPath)) { "课程文件路径越界：$relativePath" }
        return target
    }

    companion object {
        private const val COURSE_FILE_NAME = "course.json"
        private const val STATE_FILE_NAME = ".course-state.json"
        private const val MAX_UNCOMPRESSED_PACKAGE_BYTES = 256L * 1024L * 1024L

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
