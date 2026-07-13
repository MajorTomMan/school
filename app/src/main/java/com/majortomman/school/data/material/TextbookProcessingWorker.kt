package com.majortomman.school.data.material

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
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
                workDataOf(TextbookProcessingContract.KEY_MESSAGE to "没有可读取的教材 PDF"),
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
                        title = "教材 PDF 处理完成",
                        body = "${installed.slot.displayTitle}已扫描 ${installed.pageCount} 页并生成 ${installed.lessons.size} 个课程",
                        success = true,
                    )
                }
            }
            Result.success(
                workDataOf(
                    TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
                    TextbookProcessingContract.KEY_STAGE to TextbookProcessingStage.COMPLETED.name,
                    TextbookProcessingContract.KEY_PROGRESS to 100,
                    TextbookProcessingContract.KEY_MESSAGE to "教材 PDF 处理完成",
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            MaterialLibraryStore.processingRoot(applicationContext, slot).deleteRecursively()
            val message = error.message ?: "所选文件不是可用的教材 PDF"
            showCompletionNotification(slot, "教材处理失败", message, false)
            Result.failure(failureData(slot, message))
        } catch (error: IOException) {
            val message = error.message ?: "教材 PDF 读取失败"
            if (runAttemptCount < MAX_RETRY_COUNT) {
                report(slot, TextbookProcessingStage.PREPARING, 1, "$message，稍后自动继续")
                Result.retry()
            } else {
                showCompletionNotification(slot, "教材处理未完成", message, false)
                Result.failure(failureData(slot, message))
            }
        } catch (error: Throwable) {
            val message = error.message ?: error::class.java.simpleName
            showCompletionNotification(slot, "教材处理未完成", message, false)
            Result.failure(failureData(slot, message))
        }
    }

    private suspend fun process(slot: TextbookSlot, source: Uri): InstalledTextbook {
        val materialRoot = MaterialLibraryStore.materialRoot(applicationContext)
        MaterialLibraryStore.packsRoot(applicationContext).mkdirs()
        materialRoot.mkdirs()
        val staging = MaterialLibraryStore.processingRoot(applicationContext, slot)
        prepareStaging(staging, source)
        val sourceName = querySourceName(source).ifBlank { "${slot.displayTitle}.pdf" }
        val pdfDirectory = File(staging, "books")
        val pdfFile = File(pdfDirectory, "textbook.pdf")
        val copiedMarker = File(staging, ".pdf-copied")

        if (!copiedMarker.isFile || !pdfFile.isFile) {
            report(slot, TextbookProcessingStage.EXTRACTING, 3, "正在复制教材 PDF")
            require(pdfDirectory.mkdirs() || pdfDirectory.isDirectory) { "无法创建 PDF 目录" }
            val sourceSize = querySourceSize(source)
            applicationContext.contentResolver.openInputStream(source)?.use { raw ->
                BufferedInputStream(raw).use { input ->
                    BufferedOutputStream(FileOutputStream(pdfFile)).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            checkStopped()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            require(copied <= MAX_PDF_BYTES) { "PDF 文件过大，当前最多支持 2 GB" }
                            if (copied % PROGRESS_BYTES_INTERVAL < read) {
                                val fraction = if (sourceSize > 0L) copied.toDouble() / sourceSize else 0.0
                                val progress = 3 + (fraction.coerceIn(0.0, 1.0) * 24).toInt()
                                reportBlocking(slot, TextbookProcessingStage.EXTRACTING, progress, "复制 PDF ${formatBytes(copied)}")
                            }
                        }
                    }
                }
            } ?: throw IOException("无法读取所选 PDF")
            copiedMarker.writeText(source.toString(), Charsets.UTF_8)
        } else {
            report(slot, TextbookProcessingStage.EXTRACTING, 27, "已恢复 PDF 复制进度")
        }

        checkStopped()
        require(pdfFile.length() > 0L) { "所选 PDF 是空文件" }
        require(hasPdfHeader(pdfFile)) { "所选文件不是 PDF：文件头缺少 %PDF-" }
        val pageCount = readPageCount(pdfFile)
        require(pageCount > 0) { "教材 PDF 没有可读取页面" }

        report(slot, TextbookProcessingStage.VALIDATING, 29, "正在校验 PDF 完整性")
        val expectedBytes = pdfFile.length().coerceAtLeast(1L)
        val sha256 = sha256(pdfFile) { processed ->
            val progress = 29 + ((processed.toDouble() / expectedBytes) * 12).toInt().coerceIn(0, 12)
            reportBlocking(slot, TextbookProcessingStage.VALIDATING, progress, "校验 PDF ${formatBytes(processed)}")
        }

        checkStopped()
        report(slot, TextbookProcessingStage.IDENTIFYING, 42, "正在识别文件名、封面和目录")
        val scanResult = DirectPdfImportScanner.scan(
            pdfFile = pdfFile,
            displayName = sourceName,
            slot = slot,
            cacheRoot = staging,
        ) { completed, total, message ->
            val fraction = completed.toDouble() / total.coerceAtLeast(1)
            val progress = 42 + (fraction.coerceIn(0.0, 1.0) * 20).toInt()
            report(slot, TextbookProcessingStage.IDENTIFYING, progress, message)
        }

        val manifest = MaterialPackManifest(
            schemaVersion = MATERIAL_PACK_SCHEMA_VERSION,
            packId = "pdf-${sha256.take(24)}",
            version = "direct-pdf-1",
            title = scanResult.title,
            subject = slot.subjectTitle,
            catalogPath = "catalog.json",
            pdf = MaterialPdfAsset(
                path = "books/textbook.pdf",
                sha256 = sha256,
                pageIndexOffset = scanResult.pageIndexOffset,
            ),
        )
        File(staging, "manifest.json").writeText(
            MaterialPackManifestParser.toJson(manifest).toString(2),
            Charsets.UTF_8,
        )
        File(staging, manifest.catalogPath).writeText(
            DirectPdfImportScanner.catalogToJson(scanResult.catalog).toString(2),
            Charsets.UTF_8,
        )
        File(staging, "generated/identity.json").apply {
            parentFile?.mkdirs()
            writeText(
                JSONObject()
                    .put("sourceName", sourceName)
                    .put("stage", slot.stage.id)
                    .put("subject", slot.subjectTitle)
                    .put("grade", slot.grade)
                    .put("volume", slot.volume.id)
                    .put("title", scanResult.title)
                    .put("pageIndexOffset", scanResult.pageIndexOffset)
                    .put("scannedPages", scanResult.scannedPages)
                    .put("evidence", scanResult.evidence)
                    .toString(2),
                Charsets.UTF_8,
            )
        }

        val catalog = TextbookCatalogParser.parse(
            File(staging, manifest.catalogPath).readText(Charsets.UTF_8),
            manifest,
            slot,
        )
        catalog.lessons.forEach { lesson ->
            val startIndex = lesson.pageStart - 1 + manifest.pdf.pageIndexOffset
            val endIndex = lesson.pageEnd - 1 + manifest.pdf.pageIndexOffset
            require(startIndex in 0 until pageCount && endIndex in 0 until pageCount) {
                "课程 ${lesson.title} 的页码超出 PDF 范围"
            }
        }

        report(slot, TextbookProcessingStage.INDEXING, 64, "正在建立 $pageCount 页 PDF 索引")
        val generatedDirectory = File(staging, "generated")
        generatedDirectory.mkdirs()
        val pageArray = JSONArray()
        for (pageIndex in 0 until pageCount) {
            checkStopped()
            pageArray.put(
                JSONObject()
                    .put("pdfIndex", pageIndex)
                    .put("printedPage", pageIndex - manifest.pdf.pageIndexOffset + 1),
            )
            if (pageIndex % PAGE_PROGRESS_INTERVAL == 0 || pageIndex == pageCount - 1) {
                val progress = 64 + (((pageIndex + 1).toDouble() / pageCount) * 12).toInt()
                report(slot, TextbookProcessingStage.INDEXING, progress, "建立页面索引 ${pageIndex + 1} / $pageCount")
            }
        }
        File(generatedDirectory, "pages.json").writeText(
            JSONObject().put("pageCount", pageCount).put("pages", pageArray).toString(),
            Charsets.UTF_8,
        )

        val generatedLessons = mutableListOf<GeneratedLesson>()
        val lessonCount = catalog.lessons.size.coerceAtLeast(1)
        catalog.lessons.forEachIndexed { index, sourceLesson ->
            checkStopped()
            generatedLessons += TextbookCatalogParser.generateLessons(
                slot,
                TextbookCatalog(catalog.book, listOf(sourceLesson)),
            ).single()
            val progress = 77 + (((index + 1).toDouble() / lessonCount) * 18).toInt()
            report(
                slot,
                TextbookProcessingStage.GENERATING_COURSES,
                progress,
                "生成课程 ${index + 1} / $lessonCount · ${sourceLesson.title}",
            )
            writeGeneratedLessons(File(generatedDirectory, "lessons.json"), generatedLessons)
        }

        report(slot, TextbookProcessingStage.FINALIZING, 96, "正在保存 PDF、课程和识别结果")
        copiedMarker.delete()
        File(staging, ".source").delete()

        val finalDirectory = MaterialLibraryStore.finalRoot(applicationContext, slot)
        val backup = File(materialRoot, ".backup-${slot.key}-${UUID.randomUUID()}")
        if (finalDirectory.exists()) require(finalDirectory.renameTo(backup)) { "无法替换旧教材" }
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
        return InstalledTextbook(slot, pack, pageCount, generatedLessons).also {
            MaterialLibraryStore.upsert(applicationContext, it)
            report(slot, TextbookProcessingStage.COMPLETED, 100, "教材 PDF 处理完成")
        }
    }

    private fun prepareStaging(staging: File, source: Uri) {
        val sourceMarker = File(staging, ".source")
        val sourceValue = source.toString()
        if (staging.exists() && sourceMarker.readTextOrNull() != sourceValue) staging.deleteRecursively()
        if (!staging.exists()) require(staging.mkdirs()) { "无法创建教材处理目录" }
        sourceMarker.writeText(sourceValue, Charsets.UTF_8)
    }

    private fun hasPdfHeader(file: File): Boolean = FileInputStream(file).use { input ->
        val header = ByteArray(5)
        input.read(header) == header.size && header.toString(Charsets.US_ASCII) == "%PDF-"
    }

    private fun sha256(file: File, onProgress: (Long) -> Unit): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var processed = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                checkStopped()
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

    private fun checkStopped() {
        if (isStopped) throw CancellationException("教材处理已取消")
    }

    private fun readPageCount(pdfFile: File): Int {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> return renderer.pageCount }
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

    private fun querySourceSize(uri: Uri): Long = queryOpenable(uri, OpenableColumns.SIZE) { cursor, index ->
        if (cursor.isNull(index)) 0L else cursor.getLong(index)
    } ?: 0L

    private fun querySourceName(uri: Uri): String = queryOpenable(uri, OpenableColumns.DISPLAY_NAME) { cursor, index ->
        cursor.getString(index)
    }.orEmpty()

    private fun <T> queryOpenable(uri: Uri, column: String, read: (Cursor, Int) -> T): T? = runCatching {
        applicationContext.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(column)
            if (index < 0) null else read(cursor, index)
        }
    }.getOrNull()

    private suspend fun report(
        slot: TextbookSlot,
        stage: TextbookProcessingStage,
        progress: Int,
        message: String,
    ) {
        val safeProgress = progress.coerceIn(0, 100)
        setProgress(progressData(slot, stage, safeProgress, message))
        setForeground(createForegroundInfo(slot, safeProgress, message))
    }

    private fun reportBlocking(
        slot: TextbookSlot,
        stage: TextbookProcessingStage,
        progress: Int,
        message: String,
    ) {
        val safeProgress = progress.coerceIn(0, 100)
        setProgressAsync(progressData(slot, stage, safeProgress, message))
        setForegroundAsync(createForegroundInfo(slot, safeProgress, message))
    }

    private fun progressData(
        slot: TextbookSlot,
        stage: TextbookProcessingStage,
        progress: Int,
        message: String,
    ): Data = workDataOf(
        TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
        TextbookProcessingContract.KEY_STAGE to stage.name,
        TextbookProcessingContract.KEY_PROGRESS to progress,
        TextbookProcessingContract.KEY_MESSAGE to message,
    )

    private fun createForegroundInfo(slot: TextbookSlot, progress: Int, message: String): ForegroundInfo {
        val notification = Notification.Builder(applicationContext, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在扫描${slot.displayTitle}")
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
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
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
            NotificationChannel(CHANNEL_PROGRESS, "教材处理进度", NotificationManager.IMPORTANCE_LOW).apply {
                description = "显示 PDF 复制、校验、身份识别、目录扫描和课程生成进度"
                setSound(null, null)
            },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "教材处理结果", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "教材 PDF 处理完成或失败时发送提醒"
            },
        )
    }

    private fun inputSlot(): TextbookSlot? {
        val subjectId = inputData.getString(TextbookProcessingContract.KEY_SUBJECT_ID) ?: return null
        val subjectTitle = inputData.getString(TextbookProcessingContract.KEY_SUBJECT_TITLE) ?: return null
        val grade = inputData.getInt(TextbookProcessingContract.KEY_GRADE, 0)
        val volume = inputData.getInt(TextbookProcessingContract.KEY_VOLUME, 0)
        if (grade <= 0 || volume <= 0) return null
        return TextbookSlot(
            subjectId = subjectId,
            subjectTitle = subjectTitle,
            grade = grade,
            volume = TextbookVolume.fromId(volume),
            stage = EducationStage.fromGrade(grade),
        )
    }

    private fun failureData(slot: TextbookSlot, message: String): Data = workDataOf(
        TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
        TextbookProcessingContract.KEY_STAGE to TextbookProcessingStage.PREPARING.name,
        TextbookProcessingContract.KEY_PROGRESS to 0,
        TextbookProcessingContract.KEY_MESSAGE to message,
    )

    private fun progressNotificationId(slot: TextbookSlot): Int = 20_000 + (slot.key.hashCode() and 0x0FFF)
    private fun resultNotificationId(slot: TextbookSlot): Int = 30_000 + (slot.key.hashCode() and 0x0FFF)

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f KB".format(bytes / 1024.0)
    }

    private companion object {
        const val CHANNEL_PROGRESS = "textbook_processing_progress"
        const val CHANNEL_RESULT = "textbook_processing_result"
        const val MAX_RETRY_COUNT = 3
        const val MAX_PDF_BYTES = 2_000L * 1024L * 1024L
        const val PROGRESS_BYTES_INTERVAL = 4L * 1024L * 1024L
        const val PAGE_PROGRESS_INTERVAL = 20
    }
}

private fun File.readTextOrNull(): String? = runCatching {
    if (isFile) readText(Charsets.UTF_8) else null
}.getOrNull()
