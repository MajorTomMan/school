package com.majortomman.school.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val state = UpdateRepository(applicationContext).check(force = false)
        return if (state is UpdateState.Error) Result.retry() else Result.success()
    }
}

class UpdateDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferences = UpdatePreferences(applicationContext)
        val manifest = preferences.cachedManifest()
            ?: return@withContext Result.failure(workDataOf("error" to "缺少更新清单。"))
        val updatesDir = File(applicationContext.filesDir, "updates").apply { mkdirs() }
        val partial = File(updatesDir, "school-${manifest.versionCode}.apk.part")
        val target = File(updatesDir, "school-${manifest.versionCode}.apk")

        runCatching {
            target.delete()
            partial.delete()
            setForeground(createForegroundInfo(0, manifest.versionName))
            download(manifest, partial)
            require(partial.renameTo(target)) { "无法保存下载完成的更新包。" }
            UpdateSecurity.verifyApk(applicationContext, target, manifest)
            preferences.saveDownloadedApk(manifest.versionCode, target.absolutePath)
            UpdateRuntimeBus.publish(UpdateState.Ready(manifest, target))
            showReadyNotification(manifest.versionName)
            Result.success(workDataOf("apk_path" to target.absolutePath))
        }.getOrElse { throwable ->
            partial.delete()
            target.delete()
            preferences.clearDownloadedApk()
            val message = throwable.message ?: "更新下载失败。"
            UpdateRuntimeBus.publish(UpdateState.Error(message))
            if (runAttemptCount < 2) Result.retry() else Result.failure(workDataOf("error" to message))
        }
    }

    private suspend fun download(manifest: UpdateManifest, destination: File) {
        val connection = (URL(manifest.apk.downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "School-Updater")
        }
        try {
            require(connection.responseCode in 200..299) { "下载服务器返回 ${connection.responseCode}。" }
            val expectedSize = manifest.apk.size
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastProgress = -1
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        require(downloaded <= expectedSize) { "下载文件超过清单声明大小。" }
                        val progress = ((downloaded * 100L) / expectedSize).toInt().coerceIn(0, 100)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            setProgress(workDataOf("progress" to progress, "downloaded" to downloaded))
                            UpdateRuntimeBus.publish(UpdateState.Downloading(manifest, progress, downloaded))
                            setForeground(createForegroundInfo(progress, manifest.versionName))
                        }
                    }
                    output.fd.sync()
                }
            }
            require(downloaded == expectedSize) { "下载文件大小不完整。" }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha == manifest.apk.sha256) { "下载文件 SHA-256 校验失败。" }
        } finally {
            connection.disconnect()
        }
    }

    private fun createForegroundInfo(progress: Int, versionName: String): ForegroundInfo {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, UPDATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载 School $versionName")
            .setContentText("已完成 $progress%")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()
        return ForegroundInfo(UPDATE_NOTIFICATION_ID, notification)
    }

    private fun showReadyNotification(versionName: String) {
        ensureNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, UPDATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("School $versionName 已下载")
            .setContentText("打开应用完成安装")
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(UPDATE_NOTIFICATION_ID + 1, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    UPDATE_NOTIFICATION_CHANNEL,
                    "应用更新",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }
}
