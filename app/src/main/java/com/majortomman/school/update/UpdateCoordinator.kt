package com.majortomman.school.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateCoordinator private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val repository = UpdateRepository(appContext)
    private val preferences = UpdatePreferences(appContext)
    private val workManager = WorkManager.getInstance(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state = UpdateRuntimeBus.state
    val dialogVisible = UpdateRuntimeBus.dialogVisible

    private val mutableSettings = MutableStateFlow(repository.settings())
    val settings = mutableSettings.asStateFlow()

    init {
        repository.restoreCachedState().also { restored ->
            if (restored is UpdateState.Available || restored is UpdateState.Ready) {
                UpdateRuntimeBus.showDialog()
            }
        }
        schedulePeriodicCheck()
        UpdatePushRegistrar.setEnabled(appContext, preferences.settings().autoCheck)
    }

    fun onAppForeground() {
        UpdateRuntimeBus.setAppForeground(true)
        val current = state.value
        val restored = if (current is UpdateState.Checking || current is UpdateState.Downloading) {
            current
        } else {
            repository.restoreCachedState()
        }
        if (restored is UpdateState.Available || restored is UpdateState.Ready) {
            UpdateRuntimeBus.showDialog()
        }
        if (
            restored !is UpdateState.Checking &&
            restored !is UpdateState.Downloading &&
            repository.shouldCheckOnForeground()
        ) {
            checkNow(force = false)
        }
    }

    fun onAppBackground() {
        UpdateRuntimeBus.setAppForeground(false)
    }

    fun checkNow(force: Boolean = true) {
        scope.launch {
            val result = repository.check(force)
            mutableSettings.value = repository.settings()
            val shouldShow = when (result) {
                is UpdateState.Available,
                is UpdateState.Downloading,
                is UpdateState.Ready,
                -> true
                is UpdateState.Error,
                is UpdateState.UpToDate,
                -> force
                else -> false
            }
            if (shouldShow) UpdateRuntimeBus.showDialog() else UpdateRuntimeBus.hideDialog()
        }
    }

    fun download(manifest: UpdateManifest) {
        val networkType = if (preferences.settings().wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .build()
        UpdateRuntimeBus.publish(UpdateState.Downloading(manifest, 0, 0L))
        UpdateRuntimeBus.showDialog()
        workManager.enqueueUniqueWork(UPDATE_DOWNLOAD_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelDownload() {
        workManager.cancelUniqueWork(UPDATE_DOWNLOAD_WORK_NAME)
        repository.restoreCachedState()
        UpdateRuntimeBus.hideDialog()
    }

    fun remindLater(manifest: UpdateManifest) {
        repository.snooze(manifest)
        UpdateRuntimeBus.hideDialog()
    }

    fun ignoreVersion(manifest: UpdateManifest) {
        repository.ignore(manifest)
        UpdateRuntimeBus.hideDialog()
    }

    fun dismissStatus() {
        UpdateRuntimeBus.hideDialog()
        if (state.value is UpdateState.UpToDate || state.value is UpdateState.Error) {
            UpdateRuntimeBus.publish(UpdateState.Idle)
        }
    }

    fun showDialog() {
        UpdateRuntimeBus.showDialog()
    }

    fun setAutoCheck(enabled: Boolean) {
        repository.setAutoCheck(enabled)
        mutableSettings.value = repository.settings()
        schedulePeriodicCheck()
        UpdatePushRegistrar.setEnabled(appContext, enabled)
    }

    fun setWifiOnly(enabled: Boolean) {
        repository.setWifiOnly(enabled)
        mutableSettings.value = repository.settings()
        schedulePeriodicCheck()
    }

    fun isMandatory(manifest: UpdateManifest): Boolean = repository.isMandatory(manifest)

    private fun schedulePeriodicCheck() {
        if (!preferences.settings().autoCheck) {
            workManager.cancelUniqueWork(UPDATE_CHECK_WORK_NAME)
            return
        }
        val networkType = if (preferences.settings().wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            UPDATE_CHECK_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        @Volatile
        private var instance: UpdateCoordinator? = null

        fun get(context: Context): UpdateCoordinator = instance ?: synchronized(this) {
            instance ?: UpdateCoordinator(context.applicationContext).also { instance = it }
        }
    }
}
