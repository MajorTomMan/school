package com.majortomman.school.learning.cloud

import android.content.Context
import android.util.Log
import com.majortomman.school.BuildConfig
import com.majortomman.school.network.AppProxy
import com.majortomman.school.network.ProxyRoute
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object CourseSyncManager {
    const val LOG_TAG = "SchoolCourseSync"

    private val syncMutex = Mutex()

    suspend fun syncOnStartup(context: Context): CourseSyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val manifestUrl = BuildConfig.COURSE_MANIFEST_URL.trim()
            if (manifestUrl.isBlank()) {
                Log.i(LOG_TAG, "course manifest URL is empty; bundled fallback remains active")
                return@withLock CourseSyncResult.Disabled
            }

            val appContext = context.applicationContext
            val store = CoursePackStore(appContext)
            runCatching {
                val rawManifest = downloadBytes(
                    context = appContext,
                    url = manifestUrl,
                    maximumBytes = MAX_MANIFEST_BYTES,
                ).toString(Charsets.UTF_8)
                val manifest = CourseManifestCodec.decode(rawManifest)
                var updatedCount = 0

                manifest.textbooks.forEach { remote ->
                    if (remote.minimumAppVersion > BuildConfig.VERSION_CODE) {
                        Log.i(
                            LOG_TAG,
                            "skip ${remote.id}: requires app ${remote.minimumAppVersion}, current ${BuildConfig.VERSION_CODE}",
                        )
                        return@forEach
                    }
                    val local = store.readLocalState(remote.id)
                    when (val plan = CourseUpdatePlanner.plan(remote, local)) {
                        CourseUpdatePlan.None -> Log.i(LOG_TAG, "${remote.id} already current at ${remote.version}")
                        is CourseUpdatePlan.Full -> {
                            Log.i(LOG_TAG, "full update ${remote.id}: ${plan.reason}")
                            installFull(appContext, store, remote)
                            updatedCount += 1
                        }
                        is CourseUpdatePlan.Incremental -> {
                            Log.i(
                                LOG_TAG,
                                "incremental update ${remote.id}: ${plan.changedFiles.size} changed, " +
                                    "${plan.deletedFiles.size} deleted",
                            )
                            runCatching {
                                store.installIncremental(remote, plan) { file, destination ->
                                    downloadToFile(appContext, file, destination)
                                }
                            }.getOrElse { incrementalError ->
                                Log.w(LOG_TAG, "incremental update failed; retry full package", incrementalError)
                                installFull(appContext, store, remote)
                            }
                            updatedCount += 1
                        }
                    }
                }

                if (updatedCount > 0) CloudCourseRepository.markContentChanged()
                CourseSyncResult.Success(updatedCount, manifest.contentVersion)
            }.getOrElse { error ->
                Log.e(LOG_TAG, "course sync failed; keep existing cache or bundled fallback", error)
                CourseSyncResult.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    }

    private fun installFull(
        context: Context,
        store: CoursePackStore,
        remote: CourseTextbookManifest,
    ) {
        val packageFile = store.temporaryDownloadFile(remote.id, "full")
        try {
            downloadToFile(context, remote.fullPackage, packageFile)
            store.installFull(remote, packageFile)
        } finally {
            packageFile.delete()
        }
    }

    private fun downloadToFile(context: Context, spec: CourseFileSpec, destination: File) {
        require(spec.url.isNotBlank()) { "课程文件 ${spec.path} 缺少下载地址" }
        destination.parentFile?.mkdirs()
        destination.delete()
        val connection = AppProxy.openConnection(
            context,
            normalizeGoogleDriveDownloadUrl(spec.url),
            ProxyRoute.UPDATES,
        ).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream, application/json")
            setRequestProperty("User-Agent", "School-Course/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            require(connection.responseCode in 200..299) {
                "课程服务器返回 ${connection.responseCode}：${spec.path}"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(destination).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        downloaded += count
                        require(downloaded <= spec.size) { "课程文件超过清单声明大小：${spec.path}" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(downloaded == spec.size) { "课程文件下载不完整：${spec.path}" }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha == spec.sha256) { "课程文件 SHA-256 校验失败：${spec.path}" }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadBytes(context: Context, url: String, maximumBytes: Int): ByteArray {
        val connection = AppProxy.openConnection(
            context,
            normalizeGoogleDriveDownloadUrl(url),
            ProxyRoute.UPDATES,
        ).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, application/octet-stream")
            setRequestProperty("User-Agent", "School-Course/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            require(connection.responseCode in 200..299) { "课程清单服务器返回 ${connection.responseCode}" }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    total += count
                    require(total <= maximumBytes) { "课程清单响应过大" }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    internal fun normalizeGoogleDriveDownloadUrl(value: String): String {
        val trimmed = value.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
        if (!uri.host.orEmpty().endsWith("drive.google.com")) return trimmed

        val pathMatch = Regex("/file/d/([^/]+)").find(uri.path.orEmpty())
        val queryId = uri.rawQuery
            ?.split('&')
            ?.mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2 && pieces[0] == "id") pieces[1] else null
            }
            ?.firstOrNull()
        val fileId = pathMatch?.groupValues?.getOrNull(1) ?: queryId ?: return trimmed
        return "https://drive.google.com/uc?export=download&id=$fileId"
    }

    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000
}

sealed interface CourseSyncResult {
    data object Disabled : CourseSyncResult
    data class Success(val updatedTextbooks: Int, val contentVersion: Long) : CourseSyncResult
    data class Failed(val message: String) : CourseSyncResult
}
