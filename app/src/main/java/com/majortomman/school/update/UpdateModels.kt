package com.majortomman.school.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class UpdateApk(
    val fileName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
    val certificateSha256: String,
)

data class UpdateManifest(
    val schemaVersion: Int,
    val channel: String,
    val versionCode: Long,
    val versionName: String,
    val minimumSupportedVersionCode: Long,
    val mandatory: Boolean,
    val publishedAt: String,
    val changes: List<String>,
    val fixes: List<String>,
    val apk: UpdateApk,
)

data class UpdateSettings(
    val autoCheck: Boolean = true,
    val wifiOnly: Boolean = true,
    val lastCheckedAt: Long = 0L,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val progress: Int, val downloadedBytes: Long) : UpdateState
    data class Ready(val manifest: UpdateManifest, val apkFile: File) : UpdateState
    data class Error(val message: String, val recoverable: Boolean = true) : UpdateState
}

internal object UpdateRuntimeBus {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = mutableState.asStateFlow()

    fun publish(state: UpdateState) {
        mutableState.value = state
    }
}

internal const val UPDATE_CHANNEL = "development"
internal const val UPDATE_CHECK_WORK_NAME = "school-update-check"
internal const val UPDATE_DOWNLOAD_WORK_NAME = "school-update-download"
internal const val UPDATE_NOTIFICATION_CHANNEL = "school-updates"
internal const val UPDATE_NOTIFICATION_ID = 2107
