package com.majortomman.school.learning.cloud

import android.content.Context
import android.os.SystemClock
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

    suspend fun checkForUpdates(context: Context): CourseUpdateCheckResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val manifestUrl = BuildConfig.COURSE_MANIFEST_URL.trim()
            if (manifestUrl.isBlank()) return@withLock CourseUpdateCheckResult.Disabled

            val appContext = context.applicationContext
            runCatching {
                val manifest = downloadManifest(appContext, manifestUrl)
                val store = CoursePackStore(appContext)
                val planned = plannedUpdates(manifest, store)
                if (planned.isEmpty()) {
                    CourseUpdateCheckResult.NoUpdate
                } else {
                    val kind = when {
                        planned.any { it.local == null } -> CourseUpdateKind.INITIAL
                        planned.any { it.plan is CourseUpdatePlan.Full } -> CourseUpdateKind.FULL
                        else -> CourseUpdateKind.INCREMENTAL
                    }
                    CourseUpdateCheckResult.Available(
                        CourseUpdateOffer(
                            kind = kind,
                            textbookCount = planned.size,
                            estimatedBytes = planned.sumOf(::estimatedTransferBytes),
                        ),
                    )
                }
            }.getOrElse { error ->
                Log.w(LOG_TAG, "course update check failed", error)
                CourseUpdateCheckResult.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    }

    suspend fun syncOnStartup(context: Context): CourseSyncResult =
        syncAfterConfirmation(context, onProgress = {})

    suspend fun syncAfterConfirmation(
        context: Context,
        onProgress: (CourseSyncProgress) -> Unit,
    ): CourseSyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val manifestUrl = BuildConfig.COURSE_MANIFEST_URL.trim()
            if (manifestUrl.isBlank()) {
                Log.i(LOG_TAG, "course manifest URL is empty; no cloud course can be installed")
                return@withLock CourseSyncResult.Disabled
            }

            val appContext = context.applicationContext
            val store = CoursePackStore(appContext)
            runCatching {
                onProgress(CourseSyncProgress(0L, 0L, "课程清单", "正在检查更新"))
                val manifest = downloadManifest(appContext, manifestUrl)
                val planned = plannedUpdates(manifest, store)
                if (planned.isEmpty()) {
                    onProgress(CourseSyncProgress(0L, 0L, "", "课程已经是最新版本"))
                    return@runCatching CourseSyncResult.Success(0)
                }

                val tracker = ProgressTracker(
                    initialTotalBytes = planned.sumOf(::estimatedTransferBytes).coerceAtLeast(1L),
                    listener = onProgress,
                )
                var updatedCount = 0

                planned.forEach { update ->
                    val remote = update.remote
                    when (val plan = update.plan) {
                        CourseUpdatePlan.None -> Unit
                        is CourseUpdatePlan.Full -> {
                            Log.i(LOG_TAG, "full update ${remote.id}: ${plan.reason}")
                            installFull(appContext, store, remote, tracker)
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
                                    downloadToFile(appContext, file, destination, tracker)
                                }
                            }.getOrElse { incrementalError ->
                                Log.w(LOG_TAG, "incremental update failed; retry full package", incrementalError)
                                tracker.addTotal(estimatedFullTransferBytes(remote, update.local))
                                installFull(appContext, store, remote, tracker)
                            }
                            updatedCount += 1
                        }
                    }
                }

                tracker.complete("正在校验并启用课程")
                if (updatedCount > 0) CloudCourseRepository.markContentChanged()
                CourseSyncResult.Success(updatedCount)
            }.getOrElse { error ->
                Log.e(LOG_TAG, "course sync failed; keep the previous verified cloud cache", error)
                CourseSyncResult.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    }

    private fun plannedUpdates(
        manifest: CourseManifest,
        store: CoursePackStore,
    ): List<PlannedCourseUpdate> = manifest.textbooks.mapNotNull { remote ->
        val local = store.readLocalState(remote.id)
        val plan = CourseUpdatePlanner.plan(remote, local)
        plan.takeUnless { it == CourseUpdatePlan.None }?.let {
            PlannedCourseUpdate(remote, local, it)
        }
    }

    private fun estimatedTransferBytes(update: PlannedCourseUpdate): Long = when (val plan = update.plan) {
        CourseUpdatePlan.None -> 0L
        is CourseUpdatePlan.Incremental -> plan.changedFiles.sumOf(CourseFileSpec::size)
        is CourseUpdatePlan.Full -> estimatedFullTransferBytes(update.remote, update.local)
    }

    private fun estimatedFullTransferBytes(
        remote: CourseTextbookManifest,
        local: LocalCourseState?,
    ): Long {
        val externalBytes = remote.files
            .filterNot(CourseFileSpec::bundled)
            .filter { file ->
                local?.files?.get(file.path)?.let { state ->
                    state.size == file.size && state.sha256 == file.sha256
                } != true
            }
            .sumOf(CourseFileSpec::size)
        return remote.packageFile.size + externalBytes
    }

    private fun installFull(
        context: Context,
        store: CoursePackStore,
        remote: CourseTextbookManifest,
        tracker: ProgressTracker,
    ) {
        val packageFile = store.temporaryDownloadFile(remote.id, "full")
        try {
            downloadToFile(context, remote.packageFile, packageFile, tracker)
            store.installFull(remote, packageFile) { file, destination ->
                downloadToFile(context, file, destination, tracker)
            }
        } finally {
            packageFile.delete()
        }
    }

    private fun downloadToFile(
        context: Context,
        spec: CourseDownloadSpec,
        destination: File,
        tracker: ProgressTracker,
    ) {
        require(spec.url.isNotBlank()) { "课程文件 ${spec.path} 缺少下载地址" }
        destination.parentFile?.mkdirs()
        destination.delete()
        tracker.beginFile(spec.path)
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
                        tracker.addBytes(count.toLong(), spec.path)
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

    private fun downloadManifest(context: Context, url: String): CourseManifest =
        CourseManifestCodec.decode(
            downloadBytes(
                context = context,
                url = url,
                maximumBytes = MAX_MANIFEST_BYTES,
            ).toString(Charsets.UTF_8),
        )

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

    private data class PlannedCourseUpdate(
        val remote: CourseTextbookManifest,
        val local: LocalCourseState?,
        val plan: CourseUpdatePlan,
    )

    private class ProgressTracker(
        initialTotalBytes: Long,
        private val listener: (CourseSyncProgress) -> Unit,
    ) {
        private var downloadedBytes = 0L
        private var totalBytes = initialTotalBytes
        private var lastEmissionAt = 0L
        private var currentItem = ""

        fun beginFile(path: String) {
            currentItem = path.substringAfterLast('/')
            emit(force = true, stage = "正在下载")
        }

        fun addBytes(count: Long, path: String) {
            downloadedBytes += count
            currentItem = path.substringAfterLast('/')
            emit(force = downloadedBytes >= totalBytes, stage = "正在下载")
        }

        fun addTotal(bytes: Long) {
            totalBytes += bytes.coerceAtLeast(0L)
            emit(force = true, stage = "正在切换为完整课程包")
        }

        fun complete(stage: String) {
            downloadedBytes = totalBytes
            emit(force = true, stage = stage)
        }

        private fun emit(force: Boolean, stage: String) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - lastEmissionAt < PROGRESS_EMIT_INTERVAL_MS) return
            lastEmissionAt = now
            listener(
                CourseSyncProgress(
                    downloadedBytes = downloadedBytes.coerceAtMost(totalBytes),
                    totalBytes = totalBytes,
                    currentItem = currentItem,
                    stage = stage,
                ),
            )
        }
    }

    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 120_000
    private const val PROGRESS_EMIT_INTERVAL_MS = 200L
}

data class CourseSyncProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val currentItem: String,
    val stage: String,
)

enum class CourseUpdateKind {
    INITIAL,
    FULL,
    INCREMENTAL,
}

data class CourseUpdateOffer(
    val kind: CourseUpdateKind,
    val textbookCount: Int,
    val estimatedBytes: Long,
)

sealed interface CourseUpdateCheckResult {
    data object Disabled : CourseUpdateCheckResult
    data object NoUpdate : CourseUpdateCheckResult
    data class Available(val offer: CourseUpdateOffer) : CourseUpdateCheckResult
    data class Failed(val message: String) : CourseUpdateCheckResult
}

sealed interface CourseSyncResult {
    data object Disabled : CourseSyncResult
    data class Success(val updatedTextbooks: Int) : CourseSyncResult
    data class Failed(val message: String) : CourseSyncResult
}
