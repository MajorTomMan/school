package com.majortomman.school.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.majortomman.school.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val reason = inputData.getString(UPDATE_WORK_REASON_KEY)
        val state = UpdateRepository(applicationContext).check(force = false)
        if (reason == UPDATE_WORK_REASON_PUSH) {
            when (state) {
                is UpdateState.Available -> announceAvailable(state.manifest)
                is UpdateState.Ready -> announceAvailable(state.manifest)
                else -> Unit
            }
        }
        return if (state is UpdateState.Error) Result.retry() else Result.success()
    }

    private fun announceAvailable(manifest: UpdateManifest) {
        if (UpdateRuntimeBus.isAppForeground()) {
            UpdateRuntimeBus.showDialog()
            return
        }
        ensureAvailableNotificationChannel()
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_update_dialog", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            manifest.versionCode.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val changeCount = manifest.changes.size + manifest.fixes.size
        val text = if (changeCount > 0) {
            "包含 $changeCount 项修改与修复，点击查看并升级"
        } else {
            "点击查看版本说明并升级"
        }
        val notification = NotificationCompat.Builder(applicationContext, UPDATE_AVAILABLE_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("School ${manifest.versionName} 已发布")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            applicationContext.getSystemService(NotificationManager::class.java)
                .notify(UPDATE_AVAILABLE_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureAvailableNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        UPDATE_AVAILABLE_NOTIFICATION_CHANNEL,
                        "新版本提醒",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ),
                )
        }
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
            if (throwable is CancellationException) throw throwable
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
                        currentCoroutineContext().ensureActive()
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
        ensureDownloadNotificationChannel()
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
        ensureDownloadNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, UPDATE_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("School $versionName 已下载")
            .setContentText("打开应用完成安装")
            .setAutoCancel(true)
            .build()
        runCatching {
            applicationContext.getSystemService(NotificationManager::class.java)
                .notify(UPDATE_NOTIFICATION_ID + 1, notification)
        }
    }

    private fun ensureDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        UPDATE_NOTIFICATION_CHANNEL,
                        "应用更新下载",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
        }
    }
}

private const val UPDATE_AVAILABLE_NOTIFICATION_CHANNEL = "school-update-available"
private const val UPDATE_AVAILABLE_NOTIFICATION_ID = UPDATE_NOTIFICATION_ID + 10
