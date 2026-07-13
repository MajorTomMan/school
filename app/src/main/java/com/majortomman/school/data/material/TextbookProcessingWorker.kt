package com.majortomman.school.data.material

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ServiceInfo
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.majortomman.school.MainActivity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal object TextbookProcessingContract {
    const val TAG = "textbook-processing"
    const val TAG_SLOT_PREFIX = "textbook-slot:"
    const val KEY_SOURCE_URI = "source_uri"
    const val KEY_SUBJECT_ID = "subject_id"
    const val KEY_SUBJECT_TITLE = "subject_title"
    const val KEY_GRADE = "grade"
    const val KEY_VOLUME = "volume"
    const val KEY_SLOT_KEY = "slot_key"
    const val KEY_STAGE = "stage"
    const val KEY_PROGRESS = "progress"
    const val KEY_MESSAGE = "message"

    fun uniqueWorkName(slot: TextbookSlot): String = "textbook-processing-${slot.key}"
    fun slotTag(slot: TextbookSlot): String = "$TAG_SLOT_PREFIX${slot.key}"
}

class TextbookProcessingWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val slot = inputSlot() ?: return@withContext Result.failure(
            workDataOf(TextbookProcessingContract.KEY_MESSAGE to "教材年级信息不完整"),
        )
        val source = inputData.getString(TextbookProcessingContract.KEY_SOURCE_URI)
            ?.let(Uri::parse)
            ?: return@withContext Result.failure(
                workDataOf(TextbookProcessingContract.KEY_MESSAGE to "没有可读取的教材文件"),
            )

        createNotificationChannels()
        report(slot, TextbookProcessingStage.PREPARING, 1, "正在准备${slot.displayTitle}")

        val lockFile = File(MaterialLibraryStore.materialRoot(applicationContext), "processing.lock")
        lockFile.parentFile?.mkdirs()
        try {
            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                channel.lock().use {
                    val installed = process(slot, source)
                    showCompletionNotification(
                        slot = slot,
                        title = "教材处理完成",
                        body = "${installed.slot.displayTitle}已生成 ${installed.lessons.size} 个课程",
                        success = true,
                    )
                }
            }
            Result.success(
                workDataOf(
                    TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
                    TextbookProcessingContract.KEY_STAGE to TextbookProcessingStage.COMPLETED.name,
                    TextbookProcessingContract.KEY_PROGRESS to 100,
                    TextbookProcessingContract.KEY_MESSAGE to "教材处理完成",
                ),
            )
        } catch (error: IllegalArgumentException) {
            MaterialLibraryStore.processingRoot(applicationContext, slot).deleteRecursively()
            val message = error.message ?: "教材格式不符合要求"
            showCompletionNotification(slot, "教材处理失败", message, success = false)
            Result.failure(failureData(slot, message))
        } catch (error: IOException) {
            val message = error.message ?: "教材文件读取失败"
            if (runAttemptCount < MAX_RETRY_COUNT) {
                report(slot, TextbookProcessingStage.PREPARING, 1, "$message，稍后自动继续")
                Result.retry()
            } else {
                showCompletionNotification(slot, "教材处理未完成", message, success = false)
                Result.failure(failureData(slot, message))
            }
        } catch (error: Throwable) {
            val message = error.message ?: error::class.java.simpleName
            showCompletionNotification(slot, "教材处理未完成", message, success = false)
            Result.failure(failureData(slot, message))
        }
    }

    private suspend fun process(slot: TextbookSlot, source: Uri): InstalledTextbook {
        val materialRoot = MaterialLibraryStore.materialRoot(applicationContext)
        val packsRoot = MaterialLibraryStore.packsRoot(applicationContext)
        materialRoot.mkdirs()
        packsRoot.mkdirs()
        val staging = MaterialLibraryStore.processingRoot(applicationContext, slot)
        prepareStaging(staging, source)

        val extractedMarker = File(staging, ".extracted")
        if (!extractedMarker.isFile) {
            report(slot, TextbookProcessingStage.EXTRACTING, 3, "正在导入教材文件")
            val sourceSize = querySourceSize(source)
            applicationContext.contentResolver.openInputStream(source)?.use { raw ->
                val counting = CountingInputStream(BufferedInputStream(raw))
                extractArchive(counting, staging) { bytes ->
                    val fraction = if (sourceSize > 0L) bytes.toDouble() / sourceSize else 0.0
                    val progress = (3 + fraction.coerceIn(0.0, 1.0) * 32).toInt()
                    reportBlocking(slot, TextbookProcessingStage.EXTRACTING, progress, "正在导入教材文件")
                }
            } ?: throw IOException("无法读取所选教材")
            extractedMarker.writeText("ok")
        } else {
            report(slot, TextbookProcessingStage.EXTRACTING, 35, "已恢复教材导入进度")
        }

        ensureActive()
        val manifestFile = File(staging, "manifest.json")
        require(manifestFile.isFile) { "教材包根目录缺少 manifest.json" }
        val manifest = MaterialPackManifestParser.parse(manifestFile.readText(Charsets.UTF_8))
        val pdfFile = resolveInside(staging, manifest.pdf.path)
        val catalogFile = resolveInside(staging, manifest.catalogPath)
        require(pdfFile.isFile) { "教材包缺少 PDF：${manifest.pdf.path}" }
        require(catalogFile.isFile) { "教材包缺少目录：${manifest.catalogPath}" }
        val catalog = TextbookCatalogParser.parse(
            catalogFile.readText(Charsets.UTF_8),
            manifest,
            slot,
        )

        val validatedMarker = File(staging, ".validated")
        if (!validatedMarker.isFile) {
            report(slot, TextbookProcessingStage.VALIDATING, 37, "正在校验教材完整性")
            val expectedBytes = pdfFile.length().coerceAtLeast(1L)
            val actualSha256 = sha256(pdfFile) { processed ->
                val progress = 37 + ((processed.toDouble() / expectedBytes) * 18).toInt().coerceIn(0, 18)
                reportBlocking(slot, TextbookProcessingStage.VALIDATING, progress, "正在校验教材完整性")
            }
            require(actualSha256.equals(manifest.pdf.sha256, ignoreCase = true)) {
                "PDF 校验失败，文件可能损坏或版本不一致"
            }
            validatedMarker.writeText(actualSha256)
        } else {
            report(slot, TextbookProcessingStage.VALIDATING, 55, "教材完整性校验已恢复")
        }

        ensureActive()
        val pageCount = readPageCount(pdfFile)
        require(pageCount > 0) { "教材 PDF 没有可读取页面" }
        catalog.lessons.forEach { lesson ->
            val startIndex = lesson.pageStart - 1 + manifest.pdf.pageIndexOffset
            val endIndex = lesson.pageEnd - 1 + manifest.pdf.pageIndexOffset
            require(startIndex in 0 until pageCount && endIndex in 0 until pageCount) {
                "课程 ${lesson.title} 的页码超出 PDF 范围，请检查 pageIndexOffset"
            }
        }

        report(slot, TextbookProcessingStage.INDEXING, 57, "正在建立 $pageCount 页教材索引")
        val generatedDirectory = File(staging, "generated")
        generatedDirectory.mkdirs()
        val pagesFile = File(generatedDirectory, "pages.json")
        val pageArray = JSONArray()
        for (pageIndex in 0 until pageCount) {
            ensureActive()
            pageArray.put(
                JSONObject()
                    .put("pdfIndex", pageIndex)
                    .put("printedPage", pageIndex - manifest.pdf.pageIndexOffset + 1),
            )
            if (pageIndex % PAGE_PROGRESS_INTERVAL == 0 || pageIndex == pageCount - 1) {
                val progress = 57 + (((pageIndex + 1).toDouble() / pageCount) * 16).toInt()
                report(slot, TextbookProcessingStage.INDEXING, progress, "建立页面索引 ${pageIndex + 1} / $pageCount")
            }
        }
        pagesFile.writeText(
            JSONObject().put("pageCount", pageCount).put("pages", pageArray).toString(),
            Charsets.UTF_8,
        )

        val generatedLessons = mutableListOf<GeneratedLesson>()
        val lessonCount = catalog.lessons.size.coerceAtLeast(1)
        catalog.lessons.forEachIndexed { index, sourceLesson ->
            ensureActive()
            val generated = TextbookCatalogParser.generateLessons(
                slot,
                TextbookCatalog(catalog.book, listOf(sourceLesson)),
            ).single()
            generatedLessons += generated
            val progress = 74 + (((index + 1).toDouble() / lessonCount) * 20).toInt()
            report(
                slot,
                TextbookProcessingStage.GENERATING_COURSES,
                progress,
                "生成课程 ${index + 1} / $lessonCount · ${sourceLesson.title}",
            )
            writeGeneratedLessons(File(generatedDirectory, "lessons.json"), generatedLessons)
        }

        report(slot, TextbookProcessingStage.FINALIZING, 96, "正在保存课程和教材")
        File(staging, ".source").delete()
        extractedMarker.delete()
        validatedMarker.delete()

        val finalDirectory = MaterialLibraryStore.finalRoot(applicationContext, slot)
        val backup = File(materialRoot, ".backup-${slot.key}-${UUID.randomUUID()}")
        if (finalDirectory.exists()) {
            require(finalDirectory.renameTo(backup)) { "无法替换旧教材" }
        }
        try {
            require(staging.renameTo(finalDirectory)) { "无法保存教材" }
            backup.deleteRecursively()
        } catch (error: Throwable) {
            if (!finalDirectory.exists() && backup.exists()) backup.renameTo(finalDirectory)
            throw error
        }

        val pack = InstalledMaterialPack(
            manifest = manifest,
            rootPath = finalDirectory.absolutePath,
            installedAt = System.currentTimeMillis(),
            sizeBytes = MaterialLibraryStore.directorySize(finalDirectory),
        )
        val installed = InstalledTextbook(
            slot = slot,
            pack = pack,
            pageCount = pageCount,
            lessons = generatedLessons,
        )
        MaterialLibraryStore.upsert(applicationContext, installed)
        report(slot, TextbookProcessingStage.COMPLETED, 100, "教材处理完成")
        return installed
    }

    private fun prepareStaging(staging: File, source: Uri) {
        val sourceMarker = File(staging, ".source")
        val sourceValue = source.toString()
        if (staging.exists() && sourceMarker.readTextOrNull() != sourceValue) {
            staging.deleteRecursively()
        }
        if (!staging.exists()) require(staging.mkdirs()) { "无法创建教材处理目录" }
        sourceMarker.writeText(sourceValue, Charsets.UTF_8)
    }

    private fun extractArchive(
        input: CountingInputStream,
        destination: File,
        onProgress: (Long) -> Unit,
    ) {
        var fileCount = 0
        var totalBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                fileCount += 1
                require(fileCount <= MAX_FILE_COUNT) { "教材包文件数量过多" }
                val safePath = MaterialPackManifestParser.safeRelativePath(entry.name, "ZIP 条目")
                if (safePath.startsWith(".")) {
                    zip.closeEntry()
                    continue
                }
                val output = resolveInside(destination, safePath)
                if (entry.isDirectory) {
                    require(output.mkdirs() || output.isDirectory) { "无法创建目录：$safePath" }
                } else {
                    output.parentFile?.let { parent ->
                        require(parent.mkdirs() || parent.isDirectory) { "无法创建目录：${parent.name}" }
                    }
                    BufferedOutputStream(FileOutputStream(output)).use { target ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            ensureActive()
                            val read = zip.read(buffer)
                            if (read < 0) break
                            entryBytes += read
                            totalBytes += read
                            require(entryBytes <= MAX_SINGLE_FILE_BYTES) { "教材包内单个文件过大" }
                            require(totalBytes <= MAX_TOTAL_UNCOMPRESSED_BYTES) { "教材包解压后体积过大" }
                            target.write(buffer, 0, read)
                            if (totalBytes % PROGRESS_BYTES_INTERVAL < read) onProgress(input.count)
                        }
                    }
                }
                zip.closeEntry()
                onProgress(input.count)
            }
        }
        require(fileCount > 0) { "教材包是空文件" }
    }

    private fun sha256(file: File, onProgress: (Long) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var processed = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                processed += read
                if (processed % PROGRESS_BYTES_INTERVAL < read) onProgress(processed)
            }
        }
        onProgress(processed)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readPageCount(pdfFile: File): Int {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                return renderer.pageCount
            }
        }
    }

    private fun writeGeneratedLessons(file: File, lessons: List<GeneratedLesson>) {
        val root = JSONObject().put(
            "lessons",
            JSONArray().apply { lessons.forEach { put(it.toJson()) } },
        )
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(root.toString(2), Charsets.UTF_8)
        if (file.exists()) file.delete()
        require(temporary.renameTo(file)) { "无法保存生成课程" }
    }

    private fun resolveInside(root: File, relativePath: String): File {
        val file = File(root, relativePath)
        val rootPath = root.canonicalFile.path + File.separator
        val filePath = file.canonicalFile.path
        require(filePath.startsWith(rootPath)) { "教材包包含越界路径" }
        return file
    }

    private fun querySourceSize(uri: Uri): Long = runCatching {
        applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.longOrZero(OpenableColumns.SIZE) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private suspend fun report(
        slot: TextbookSlot,
        stage: TextbookProcessingStage,
        progress: Int,
        message: String,
    ) {
        val safeProgress = progress.coerceIn(0, 100)
        setProgress(
            workDataOf(
                TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
                TextbookProcessingContract.KEY_STAGE to stage.name,
                TextbookProcessingContract.KEY_PROGRESS to safeProgress,
                TextbookProcessingContract.KEY_MESSAGE to message,
            ),
        )
        setForeground(createForegroundInfo(slot, safeProgress, message))
    }

    private fun reportBlocking(
        slot: TextbookSlot,
        stage: TextbookProcessingStage,
        progress: Int,
        message: String,
    ) {
        setProgressAsync(
            workDataOf(
                TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
                TextbookProcessingContract.KEY_STAGE to stage.name,
                TextbookProcessingContract.KEY_PROGRESS to progress.coerceIn(0, 100),
                TextbookProcessingContract.KEY_MESSAGE to message,
            ),
        )
        setForegroundAsync(createForegroundInfo(slot, progress.coerceIn(0, 100), message))
    }

    private fun createForegroundInfo(
        slot: TextbookSlot,
        progress: Int,
        message: String,
    ): ForegroundInfo {
        val notification = Notification.Builder(applicationContext, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在处理${slot.displayTitle}")
            .setContentText(message)
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openAppIntent(slot))
            .build()
        return ForegroundInfo(
            progressNotificationId(slot),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun showCompletionNotification(
        slot: TextbookSlot,
        title: String,
        body: String,
        success: Boolean,
    ) {
        val notification = Notification.Builder(applicationContext, CHANNEL_RESULT)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error,
            )
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(slot))
            .build()
        notificationManager.notify(resultNotificationId(slot), notification)
    }

    private fun openAppIntent(slot: TextbookSlot): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open_textbook_slot", slot.key)
        return PendingIntent.getActivity(
            applicationContext,
            slot.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "教材处理进度",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示教材导入、校验和课程生成进度"
                setSound(null, null)
            },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                "教材处理结果",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "教材处理完成或失败时发送提醒"
            },
        )
    }

    private fun inputSlot(): TextbookSlot? {
        val subjectId = inputData.getString(TextbookProcessingContract.KEY_SUBJECT_ID) ?: return null
        val subjectTitle = inputData.getString(TextbookProcessingContract.KEY_SUBJECT_TITLE) ?: return null
        val grade = inputData.getInt(TextbookProcessingContract.KEY_GRADE, 0)
        val volume = inputData.getInt(TextbookProcessingContract.KEY_VOLUME, 0)
        if (grade <= 0 || volume <= 0) return null
        return TextbookSlot(subjectId, subjectTitle, grade, TextbookVolume.fromId(volume))
    }

    private fun failureData(slot: TextbookSlot, message: String): Data = workDataOf(
        TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
        TextbookProcessingContract.KEY_STAGE to TextbookProcessingStage.PREPARING.name,
        TextbookProcessingContract.KEY_PROGRESS to 0,
        TextbookProcessingContract.KEY_MESSAGE to message,
    )

    private fun progressNotificationId(slot: TextbookSlot): Int =
        20_000 + (slot.key.hashCode() and 0x0FFF)

    private fun resultNotificationId(slot: TextbookSlot): Int =
        30_000 + (slot.key.hashCode() and 0x0FFF)

    private companion object {
        const val CHANNEL_PROGRESS = "textbook_processing_progress"
        const val CHANNEL_RESULT = "textbook_processing_result"
        const val MAX_RETRY_COUNT = 3
        const val MAX_FILE_COUNT = 10_000
        const val MAX_SINGLE_FILE_BYTES = 1_600L * 1024L * 1024L
        const val MAX_TOTAL_UNCOMPRESSED_BYTES = 2_200L * 1024L * 1024L
        const val PROGRESS_BYTES_INTERVAL = 4L * 1024L * 1024L
        const val PAGE_PROGRESS_INTERVAL = 10
    }
}

private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    var count: Long = 0
        private set

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) count += 1
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val value = super.read(buffer, offset, length)
        if (value > 0) count += value
        return value
    }
}

private fun File.readTextOrNull(): String? = runCatching {
    if (isFile) readText(Charsets.UTF_8) else null
}.getOrNull()

private fun Cursor.longOrZero(columnName: String): Long {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else 0L
}
