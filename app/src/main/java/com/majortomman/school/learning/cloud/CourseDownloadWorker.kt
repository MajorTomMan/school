package com.majortomman.school.learning.cloud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.majortomman.school.MainActivity
import com.majortomman.school.data.material.MaterialLibraryStore
import kotlin.math.roundToInt

class CourseDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannels()
        return foregroundInfo(0, "正在准备课程下载", indeterminate = true)
    }

    override suspend fun doWork(): Result {
        createNotificationChannels()
        setForeground(foregroundInfo(0, "正在检查课程资源", indeterminate = true))
        CourseDownloadCoordinator.reportRunning(
            CourseSyncProgress(0L, 0L, "课程清单", "正在检查更新"),
        )

        return when (
            val result = CourseSyncManager.syncAfterConfirmation(applicationContext) { progress ->
                publishProgress(progress)
            }
        ) {
            CourseSyncResult.Disabled -> {
                val message = "课程下载地址尚未配置"
                CourseDownloadCoordinator.reportFailure(message)
                showResultNotification(success = false, message = message)
                Result.failure(workDataOf(KEY_ERROR to message))
            }
            is CourseSyncResult.Failed -> {
                val message = userFacingFailure(result.message)
                CourseDownloadCoordinator.reportFailure(message)
                showResultNotification(success = false, message = message)
                Result.failure(workDataOf(KEY_ERROR to message))
            }
            is CourseSyncResult.Success -> {
                CloudCourseCatalogInstaller.refreshFromCache(applicationContext)
                MaterialLibraryStore.read(applicationContext)
                CourseDownloadCoordinator.reportSuccess(result.updatedTextbooks)
                val message = if (result.updatedTextbooks > 0) {
                    "课程内容已下载完成，可以离线学习"
                } else {
                    "课程内容已经是最新版本"
                }
                showResultNotification(success = true, message = message)
                Result.success(workDataOf(KEY_UPDATED_TEXTBOOKS to result.updatedTextbooks))
            }
        }
    }

    private fun publishProgress(progress: CourseSyncProgress) {
        CourseDownloadCoordinator.reportRunning(progress)
        val percent = progress.percent
        setProgressAsync(
            workDataOf(
                KEY_DOWNLOADED_BYTES to progress.downloadedBytes,
                KEY_TOTAL_BYTES to progress.totalBytes,
                KEY_CURRENT_ITEM to progress.currentItem,
                KEY_STAGE to progress.stage,
                KEY_PERCENT to percent,
            ),
        )
        notifySafely(
            PROGRESS_NOTIFICATION_ID,
            progressNotification(
                percent = percent,
                text = progress.currentItem.ifBlank { progress.stage },
                indeterminate = progress.totalBytes <= 0L,
            ),
        )
    }

    private fun foregroundInfo(percent: Int, text: String, indeterminate: Boolean): ForegroundInfo {
        val notification = progressNotification(percent, text, indeterminate)
        return ForegroundInfo(
            PROGRESS_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun progressNotification(percent: Int, text: String, indeterminate: Boolean): android.app.Notification {
        val displayedPercent = percent.coerceIn(0, 100)
        val title = if (indeterminate) {
            "正在下载课程内容"
        } else {
            "正在下载课程内容 · $displayedPercent%"
        }
        val detail = if (indeterminate) text else "$text · $displayedPercent%"
        return NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, displayedPercent, indeterminate)
            .build()
    }

    private fun showResultNotification(success: Boolean, message: String) {
        val notification = NotificationCompat.Builder(applicationContext, RESULT_CHANNEL_ID)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_notify_error,
            )
            .setContentTitle(if (success) "课程下载完成" else "课程下载失败")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notifySafely(RESULT_NOTIFICATION_ID, notification)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "课程下载进度",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示课程包和教材的后台下载进度"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RESULT_CHANNEL_ID,
                "课程下载结果",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "通知课程下载完成或失败"
            },
        )
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        runCatching { NotificationManagerCompat.from(applicationContext).notify(id, notification) }
    }

    private fun userFacingFailure(message: String): String = when {
        message.contains("课程文件下载不完整") && message.contains("textbook.pdf") ->
            "教材 PDF 下载地址返回的内容不完整。Google Drive 文件可能未开放公开访问；" +
                "请将共享权限设置为“知道链接的任何人可查看”后重试。"
        else -> message
    }

    private val CourseSyncProgress.percent: Int
        get() = if (totalBytes <= 0L) 0 else {
            (downloadedBytes.toDouble() * 100.0 / totalBytes.toDouble()).roundToInt().coerceIn(0, 100)
        }

    companion object {
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_CURRENT_ITEM = "current_item"
        const val KEY_STAGE = "stage"
        const val KEY_PERCENT = "percent"
        const val KEY_ERROR = "error"
        const val KEY_UPDATED_TEXTBOOKS = "updated_textbooks"

        private const val DOWNLOAD_CHANNEL_ID = "school_course_downloads"
        private const val RESULT_CHANNEL_ID = "school_course_download_results"
        private const val PROGRESS_NOTIFICATION_ID = 42021
        private const val RESULT_NOTIFICATION_ID = 42022
    }
}
