package com.majortomman.school.startup

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.majortomman.school.data.curriculum.MasteryTrendRepository
import com.majortomman.school.data.material.MaterialLibraryStore
import com.majortomman.school.learning.cloud.CloudCourseCatalogInstaller
import com.majortomman.school.learning.cloud.CourseSyncManager
import com.majortomman.school.learning.cloud.CourseUpdateCheckResult
import com.majortomman.school.learning.cloud.CourseUpdateOffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps course-cache checks and analytics initialization outside the launch-critical path. */
object StartupInitializationCoordinator {
    const val LOG_TAG = "SchoolStartup"

    private const val FIRST_FRAME_GRACE_MILLIS = 750L
    private const val ANALYTICS_GRACE_MILLIS = 750L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    fun start(
        context: Context,
        checkCourseUpdatesOnStartup: Boolean,
        onCourseCatalogChanged: () -> Unit,
        onCourseUpdateAvailable: (CourseUpdateOffer) -> Unit,
    ) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext

        scope.launch {
            delay(FIRST_FRAME_GRACE_MILLIS)

            runCatching {
                val removed = MaterialLibraryStore.purgeLegacyBundledContent(appContext)
                val cachedCatalogs = CloudCourseCatalogInstaller.refreshFromCache(appContext)
                if (removed > 0 || cachedCatalogs > 0) {
                    withContext(Dispatchers.Main.immediate) { onCourseCatalogChanged() }
                }
                Log.i(
                    LOG_TAG,
                    "course cache ready: removedBundled=$removed, cachedCatalogs=$cachedCatalogs",
                )
            }.onFailure { error ->
                Log.e(LOG_TAG, "course cache initialization failed", error)
            }

            if (checkCourseUpdatesOnStartup) {
                checkCourseUpdates(appContext, onCourseUpdateAvailable)
            } else {
                Log.i(LOG_TAG, "initial course download waits for user confirmation")
            }

            delay(ANALYTICS_GRACE_MILLIS)
            val analyticsStartedAt = SystemClock.elapsedRealtime()
            runCatching { MasteryTrendRepository.getInstance(appContext) }
                .onSuccess {
                    Log.i(
                        LOG_TAG,
                        "mastery analytics listener ready in " +
                            "${SystemClock.elapsedRealtime() - analyticsStartedAt} ms",
                    )
                }
                .onFailure { error ->
                    Log.e(LOG_TAG, "mastery analytics initialization failed", error)
                }
        }
    }

    fun requestCourseUpdateCheck(
        context: Context,
        onCourseUpdateAvailable: (CourseUpdateOffer) -> Unit,
    ) {
        scope.launch {
            checkCourseUpdates(context.applicationContext, onCourseUpdateAvailable)
        }
    }

    private suspend fun checkCourseUpdates(
        appContext: Context,
        onCourseUpdateAvailable: (CourseUpdateOffer) -> Unit,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        when (val result = CourseSyncManager.checkForUpdates(appContext)) {
            CourseUpdateCheckResult.Disabled -> Log.i(LOG_TAG, "cloud course synchronization is not configured")
            is CourseUpdateCheckResult.NoUpdate -> Log.i(
                LOG_TAG,
                "course content is current at ${result.contentVersion}; checked in " +
                    "${SystemClock.elapsedRealtime() - startedAt} ms",
            )
            is CourseUpdateCheckResult.Available -> {
                Log.i(
                    LOG_TAG,
                    "course update available: kind=${result.offer.kind}, bytes=${result.offer.estimatedBytes}",
                )
                withContext(Dispatchers.Main.immediate) {
                    onCourseUpdateAvailable(result.offer)
                }
            }
            is CourseUpdateCheckResult.Failed -> Log.w(
                LOG_TAG,
                "course update check failed; existing cache remains active: ${result.message}",
            )
        }
    }
}
