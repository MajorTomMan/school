package com.majortomman.school.learning.cloud

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

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

    fun installFull(
        remote: CourseTextbookManifest,
        packageFile: File,
        download: (CourseFileSpec, File) -> Unit,
    ) {
        val staging = prepareStaging(remote.id)
        try {
            unzip(packageFile, staging)
            validateBundledFiles(remote, staging)
            materializeRemoteFiles(remote, staging, download)
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
                installDownloadedFile(file, safeResolve(staging, file.path), download)
            }
            validateStaging(remote, staging)
            writeState(remote, staging)
            activate(remote.id, staging)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }


    private fun validateBundledFiles(remote: CourseTextbookManifest, staging: File) {
        val expected = remote.files.filter(CourseFileSpec::bundled).map(CourseFileSpec::path).toSet()
        val actual = staging.walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(staging).invariantSeparatorsPath }
            .toSet()
        require(actual == expected) {
            val unexpected = (actual - expected).sorted()
            val missing = (expected - actual).sorted()
            "课程 ZIP 内容与 bundled 声明不一致：未声明=${unexpected.joinToString()}，缺失=${missing.joinToString()}"
        }
    }

    private fun materializeRemoteFiles(
        remote: CourseTextbookManifest,
        staging: File,
        download: (CourseFileSpec, File) -> Unit,
    ) {
        val active = File(activeRoot, remote.id)
        remote.files.forEach { spec ->
            val target = safeResolve(staging, spec.path)
            if (isVerified(target, spec)) return@forEach

            target.deleteRecursively()
            val previous = active.takeIf(File::isDirectory)?.let { safeResolve(it, spec.path) }
            if (previous != null && isVerified(previous, spec)) {
                target.parentFile?.mkdirs()
                previous.copyTo(target, overwrite = true)
                verifyFile(target, spec)
                return@forEach
            }

            installDownloadedFile(spec, target, download)
        }
    }

    private fun installDownloadedFile(
        spec: CourseFileSpec,
        destination: File,
        download: (CourseFileSpec, File) -> Unit,
    ) {
        require(spec.url.isNotBlank()) { "课程文件 ${spec.path} 缺少下载地址" }
        val destinationParent = requireNotNull(destination.parentFile) { "课程文件缺少父目录" }
        destinationParent.mkdirs()
        val partial = File(destinationParent, "${destination.name}.part")
        partial.delete()
        try {
            download(spec, partial)
            verifyFile(partial, spec)
            destination.deleteRecursively()
            require(partial.renameTo(destination)) { "无法保存课程文件 ${spec.path}" }
        } finally {
            partial.delete()
        }
    }

    private fun isVerified(file: File, spec: CourseFileSpec): Boolean =
        runCatching {
            verifyFile(file, spec)
            true
        }.getOrDefault(false)

    private fun prepareStaging(textbookId: String): File =
        File(stagingRoot, textbookId).apply {
            deleteRecursively()
            require(mkdirs()) { "无法创建课程暂存目录" }
        }

    private fun validateStaging(remote: CourseTextbookManifest, staging: File) {
        remote.files.forEach { spec -> verifyFile(safeResolve(staging, spec.path), spec) }
        val declaredFiles = remote.files.map(CourseFileSpec::path).toSet()
        val actualFiles = staging.walkTopDown()
            .filter(File::isFile)
            .map { it.relativeTo(staging).invariantSeparatorsPath }
            .filterNot { it in INTERNAL_CACHE_FILES || it.endsWith(".part") }
            .toSet()
        require(actualFiles == declaredFiles) {
            val unexpected = (actualFiles - declaredFiles).sorted()
            val missing = (declaredFiles - actualFiles).sorted()
            "课程包文件清单不一致：未声明=${unexpected.joinToString()}，缺失=${missing.joinToString()}"
        }

        val courseFile = File(staging, COURSE_FILE_NAME)
        require(courseFile.isFile) { "课程包缺少 $COURSE_FILE_NAME" }
        val document = CourseDocumentParser.decode(courseFile.readText(Charsets.UTF_8))
        require(document.textbook.id == remote.id) {
            "课程内容教材 ID 与更新清单不一致：${document.textbook.id} != ${remote.id}"
        }
        validatePdfAsset(remote, staging, document.textbook.pdf.path, document.textbook.pdf.pageCount)
    }

    private fun validatePdfAsset(
        remote: CourseTextbookManifest,
        staging: File,
        path: String,
        expectedPageCount: Int,
    ) {
        val spec = remote.files.firstOrNull { it.path == path }
            ?: error("课程清单未声明教材 PDF：$path")
        val file = safeResolve(staging, path)
        verifyFile(file, spec)
        require(file.inputStream().buffered().use { input ->
            val header = ByteArray(5)
            input.read(header) == header.size && header.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))
        }) { "教材文件不是有效 PDF" }

        val actualPageCount = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }
        require(actualPageCount == expectedPageCount) {
            "教材 PDF 页数不一致：课程 $expectedPageCount，实际 $actualPageCount"
        }
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
        private val INTERNAL_CACHE_FILES = setOf(STATE_FILE_NAME, "generated/lessons.json")
        private const val MAX_UNCOMPRESSED_PACKAGE_BYTES = 2L * 1024L * 1024L * 1024L

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
