package com.majortomman.school.data.material

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.majortomman.school.MainActivity
import com.majortomman.school.ai.OpenAiCompatibleClient
import com.majortomman.school.data.PreferencesRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class TextbookAnalysisWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val slot = inputSlot() ?: return@withContext Result.failure(
            workDataOf(TextbookProcessingContract.KEY_MESSAGE to "教材年级信息不完整"),
        )
        createNotificationChannels()
        notificationManager.cancel(resultNotificationId(slot))

        try {
            val textbook = MaterialLibraryStore.read(applicationContext)
                .firstOrNull { it.slot.key == slot.key }
                ?: return@withContext Result.retry()
            val root = File(textbook.pack.rootPath)
            val lessons = textbook.lessons
            require(lessons.isNotEmpty()) { "教材没有可分析的课程" }

            report(slot, 1, "正在准备本地文字识别")
            val settings = PreferencesRepository(applicationContext).aiSettings.first()
            val client = OpenAiCompatibleClient(settings)
            var aiEnabled = client.testConnection().isSuccess
            var textAiCount = 0
            var visionAiCount = 0
            var ocrFallbackCount = 0
            var packCount = 0
            var catalogFallbackCount = 0
            var ocrPageCount = 0

            TextbookOcrEngine().use { ocrEngine ->
                lessons.forEachIndexed { index, lesson ->
                    checkStopped()
                    val existing = LessonAnalysisStore.read(root, lesson.sourceId)
                    if (existing != null) {
                        reportLessonProgress(slot, index, lessons.size, "已恢复 ${lesson.title} 的课程结果")
                        when (existing.source) {
                            LessonAnalysisSource.AI_TEXT -> textAiCount += 1
                            LessonAnalysisSource.AI_VISION -> visionAiCount += 1
                            LessonAnalysisSource.OCR_FALLBACK -> ocrFallbackCount += 1
                            LessonAnalysisSource.PACK -> packCount += 1
                            LessonAnalysisSource.CATALOG_FALLBACK -> catalogFallbackCount += 1
                        }
                        return@forEachIndexed
                    }

                    val provided = LessonAnalysisStore.readPackProvided(root, lesson)
                    val analysis = if (provided != null) {
                        packCount += 1
                        provided
                    } else {
                        reportLessonProgress(slot, index, lessons.size, "本地 OCR · ${lesson.title}")
                        val ocrPages = recognizeRepresentativePages(textbook, lesson, ocrEngine)
                        ocrPageCount += ocrPages.count { it.isUsable }
                        val usablePages = ocrPages.filter { it.isUsable }

                        val generatedByText = if (aiEnabled && usablePages.isNotEmpty()) {
                            runCatching {
                                val raw = client.analyzeTextbookLessonFromText(
                                    subject = slot.subjectTitle,
                                    lessonTitle = lesson.title,
                                    pageStart = lesson.pageStart,
                                    pageEnd = lesson.pageEnd,
                                    pageTexts = usablePages.map { it.printedPage to it.text },
                                )
                                LessonAnalysis.fromModelResponse(
                                    raw = raw,
                                    lesson = lesson,
                                    source = LessonAnalysisSource.AI_TEXT,
                                )
                            }
                        } else {
                            null
                        }
                        val textAnalysis = generatedByText?.getOrNull()

                        if (textAnalysis != null) {
                            textAiCount += 1
                            textAnalysis
                        } else {
                            val generatedByVision = if (aiEnabled) {
                                runCatching {
                                    val images = renderRepresentativePages(textbook, lesson)
                                    val raw = client.analyzeTextbookLesson(
                                        subject = slot.subjectTitle,
                                        lessonTitle = lesson.title,
                                        pageStart = lesson.pageStart,
                                        pageEnd = lesson.pageEnd,
                                        pageImages = images,
                                    )
                                    LessonAnalysis.fromModelResponse(
                                        raw = raw,
                                        lesson = lesson,
                                        source = LessonAnalysisSource.AI_VISION,
                                    )
                                }
                            } else {
                                null
                            }
                            val visionAnalysis = generatedByVision?.getOrNull()

                            if (visionAnalysis != null) {
                                visionAiCount += 1
                                visionAnalysis
                            } else {
                                if (generatedByText?.isFailure == true && generatedByVision?.isFailure == true) {
                                    aiEnabled = false
                                }
                                val offline = LessonAnalysisFallback.generateFromOcr(slot, lesson, ocrPages)
                                if (offline.source == LessonAnalysisSource.OCR_FALLBACK) {
                                    ocrFallbackCount += 1
                                } else {
                                    catalogFallbackCount += 1
                                }
                                offline
                            }
                        }
                    }

                    LessonAnalysisStore.write(root, analysis)
                    reportLessonProgress(
                        slot,
                        index + 1,
                        lessons.size,
                        "生成动态课程 ${index + 1} / ${lessons.size} · ${lesson.title}",
                    )
                }
            }

            val body = when {
                textAiCount > 0 -> {
                    "${slot.displayTitle}已本地识别 $ocrPageCount 个页面，并由文本模型生成 $textAiCount 个动态课程"
                }
                ocrFallbackCount > 0 -> {
                    "${slot.displayTitle}已在设备上完成文字识别；连接文本模型后可继续生成更完整的动画和练习"
                }
                visionAiCount > 0 -> {
                    "${slot.displayTitle}的本地 OCR 结果不足，已对必要页面使用视觉分析"
                }
                packCount > 0 -> "${slot.displayTitle}已读取教材包扫描结果并生成动态课程"
                else -> "${slot.displayTitle}已生成 ${lessons.size} 个离线课程模板"
            }
            report(slot, 100, "教材分析完成")
            showResult(slot, "教材分析完成", body, success = true)
            Result.success(
                workDataOf(
                    TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
                    TextbookProcessingContract.KEY_STAGE to TextbookProcessingStage.COMPLETED.name,
                    TextbookProcessingContract.KEY_PROGRESS to 100,
                    TextbookProcessingContract.KEY_MESSAGE to "教材分析完成",
                    KEY_TEXT_AI_COUNT to textAiCount,
                    KEY_VISION_AI_COUNT to visionAiCount,
                    KEY_OCR_FALLBACK_COUNT to ocrFallbackCount,
                    KEY_PACK_COUNT to packCount,
                    KEY_CATALOG_FALLBACK_COUNT to catalogFallbackCount,
                    KEY_OCR_PAGE_COUNT to ocrPageCount,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: error::class.java.simpleName
            showResult(slot, "教材分析未完成", message, success = false)
            Result.failure(failureData(slot, message))
        }
    }

    private suspend fun recognizeRepresentativePages(
        textbook: InstalledTextbook,
        lesson: GeneratedLesson,
        ocrEngine: TextbookOcrEngine,
    ): List<OcrPageResult> {
        val pack = textbook.pack
        val root = File(pack.rootPath)
        val printedPages = representativePages(lesson)
        val results = mutableListOf<OcrPageResult>()
        ParcelFileDescriptor.open(pack.pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (printedPage in printedPages) {
                    checkStopped()
                    val cached = TextbookOcrStore.read(root, printedPage)
                    if (cached != null) {
                        results += cached
                    } else {
                        val index = pack.printedPageToPdfIndex(printedPage)
                        if (index !in 0 until renderer.pageCount) continue
                        val result = renderer.openPage(index).use { page ->
                            val widthScale = MAX_OCR_WIDTH.toFloat() / page.width.coerceAtLeast(1)
                            val heightScale = MAX_OCR_HEIGHT.toFloat() / page.height.coerceAtLeast(1)
                            val scale = minOf(widthScale, heightScale, MAX_OCR_SCALE)
                                .coerceAtLeast(MIN_OCR_SCALE)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                ocrEngine.recognize(bitmap, printedPage, index)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        TextbookOcrStore.write(root, result)
                        results += result
                    }
                }
            }
        }
        return results
    }

    private fun renderRepresentativePages(
        textbook: InstalledTextbook,
        lesson: GeneratedLesson,
    ): List<ByteArray> {
        val pack = textbook.pack
        val printedPages = representativePages(lesson)
        ParcelFileDescriptor.open(pack.pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                return printedPages.mapNotNull { printedPage ->
                    checkStopped()
                    val index = pack.printedPageToPdfIndex(printedPage)
                    if (index !in 0 until renderer.pageCount) return@mapNotNull null
                    renderer.openPage(index).use { page ->
                        val scale = (MAX_IMAGE_WIDTH.toFloat() / page.width.toFloat()).coerceAtMost(1f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            ByteArrayOutputStream().use { output ->
                                require(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                                    "无法生成教材页面预览"
                                }
                                output.toByteArray()
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }.also { require(it.isNotEmpty()) { "教材课程没有可读取页面" } }
            }
        }
    }

    private fun representativePages(lesson: GeneratedLesson): Set<Int> = linkedSetOf(
        lesson.pageStart,
        (lesson.pageStart + lesson.pageEnd) / 2,
        lesson.pageEnd,
    )

    private suspend fun reportLessonProgress(
        slot: TextbookSlot,
        completed: Int,
        total: Int,
        message: String,
    ) {
        val fraction = completed.toDouble() / total.coerceAtLeast(1)
        val progress = (5 + fraction.coerceIn(0.0, 1.0) * 94).toInt()
        report(slot, progress, message)
    }

    private suspend fun report(
        slot: TextbookSlot,
        progress: Int,
        message: String,
    ) {
        val safeProgress = progress.coerceIn(0, 100)
        val data = workDataOf(
            TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
            TextbookProcessingContract.KEY_STAGE to if (safeProgress >= 100) {
                TextbookProcessingStage.COMPLETED.name
            } else {
                TextbookProcessingStage.GENERATING_COURSES.name
            },
            TextbookProcessingContract.KEY_PROGRESS to safeProgress,
            TextbookProcessingContract.KEY_MESSAGE to message,
        )
        setProgress(data)
        setForeground(createForegroundInfo(slot, safeProgress, message))
    }

    private fun createForegroundInfo(
        slot: TextbookSlot,
        progress: Int,
        message: String,
    ): ForegroundInfo {
        val notification = Notification.Builder(applicationContext, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在分析${slot.displayTitle}")
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

    private fun showResult(
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
                description = "显示教材导入、本地 OCR 和课程生成进度"
                setSound(null, null)
            },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "教材处理结果", NotificationManager.IMPORTANCE_DEFAULT).apply {
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
        TextbookProcessingContract.KEY_STAGE to TextbookProcessingStage.GENERATING_COURSES.name,
        TextbookProcessingContract.KEY_PROGRESS to 0,
        TextbookProcessingContract.KEY_MESSAGE to message,
    )

    private fun checkStopped() {
        if (isStopped) throw CancellationException("教材分析已取消")
    }

    private fun progressNotificationId(slot: TextbookSlot): Int = 20_000 + (slot.key.hashCode() and 0x0FFF)
    private fun resultNotificationId(slot: TextbookSlot): Int = 30_000 + (slot.key.hashCode() and 0x0FFF)

    private companion object {
        const val CHANNEL_PROGRESS = "textbook_processing_progress"
        const val CHANNEL_RESULT = "textbook_processing_result"
        const val MAX_OCR_WIDTH = 1_600
        const val MAX_OCR_HEIGHT = 2_400
        const val MAX_OCR_SCALE = 3f
        const val MIN_OCR_SCALE = 0.35f
        const val MAX_IMAGE_WIDTH = 900
        const val JPEG_QUALITY = 72
        const val KEY_TEXT_AI_COUNT = "text_ai_analysis_count"
        const val KEY_VISION_AI_COUNT = "vision_ai_analysis_count"
        const val KEY_OCR_FALLBACK_COUNT = "ocr_fallback_count"
        const val KEY_PACK_COUNT = "pack_analysis_count"
        const val KEY_CATALOG_FALLBACK_COUNT = "catalog_fallback_count"
        const val KEY_OCR_PAGE_COUNT = "ocr_page_count"
    }
}
