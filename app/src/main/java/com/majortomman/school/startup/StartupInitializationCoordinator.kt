package com.majortomman.school.startup

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.majortomman.school.data.curriculum.MasteryTrendRepository
import com.majortomman.school.data.material.MaterialLibraryStore
import com.majortomman.school.learning.cloud.CloudCourseCatalogInstaller
import com.majortomman.school.learning.cloud.CourseSyncManager
import com.majortomman.school.learning.cloud.CourseSyncResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps course-cache synchronization and analytics initialization outside the launch-critical path. */
object StartupInitializationCoordinator {
    const val LOG_TAG = "SchoolStartup"

    private const val FIRST_FRAME_GRACE_MILLIS = 750L
    private const val ANALYTICS_GRACE_MILLIS = 750L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    fun start(
        context: Context,
        onCourseCatalogChanged: () -> Unit,
    ) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext

        scope.launch {
            delay(FIRST_FRAME_GRACE_MILLIS)

            val removed = MaterialLibraryStore.purgeLegacyBundledContent(appContext)
            val cachedCatalogs = CloudCourseCatalogInstaller.refreshFromCache(appContext)
            if (removed > 0 || cachedCatalogs > 0) {
                withContext(Dispatchers.Main.immediate) { onCourseCatalogChanged() }
            }
            Log.i(
                LOG_TAG,
                "course cache ready: removedBundled=$removed, cachedCatalogs=$cachedCatalogs",
            )

            val courseSyncStartedAt = SystemClock.elapsedRealtime()
            when (val result = CourseSyncManager.syncOnStartup(appContext)) {
                CourseSyncResult.Disabled -> Log.i(LOG_TAG, "cloud course synchronization is not configured")
                is CourseSyncResult.Success -> {
                    val installedCatalogs = CloudCourseCatalogInstaller.refreshFromCache(appContext)
                    if (result.updatedTextbooks > 0 || installedCatalogs > 0) {
                        withContext(Dispatchers.Main.immediate) { onCourseCatalogChanged() }
                    }
                    Log.i(
                        LOG_TAG,
                        "cloud course synchronization finished in " +
                            "${SystemClock.elapsedRealtime() - courseSyncStartedAt} ms; " +
                            "updated=${result.updatedTextbooks}, contentVersion=${result.contentVersion}, " +
                            "catalogs=$installedCatalogs",
                    )
                }
                is CourseSyncResult.Failed -> Log.w(
                    LOG_TAG,
                    "cloud course synchronization failed; existing cache remains active: ${result.message}",
                )
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
}
