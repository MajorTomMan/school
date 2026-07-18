package com.majortomman.school.learning.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object CourseDownloadCoordinator {
    private const val UNIQUE_WORK_NAME = "school-course-download"
    private val operationCounter = AtomicLong(System.currentTimeMillis())
    private val mutableState = MutableStateFlow<CourseDownloadUiState>(CourseDownloadUiState.Idle)

    val state = mutableState.asStateFlow()

    fun enqueue(context: Context) {
        val current = mutableState.value
        if (current is CourseDownloadUiState.Queued || current is CourseDownloadUiState.Running) return

        val operationId = operationCounter.incrementAndGet()
        mutableState.value = CourseDownloadUiState.Queued(operationId)
        val request = OneTimeWorkRequestBuilder<CourseDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    internal fun reportRunning(progress: CourseSyncProgress) {
        val operationId = when (val current = mutableState.value) {
            is CourseDownloadUiState.Queued -> current.operationId
            is CourseDownloadUiState.Running -> current.operationId
            else -> operationCounter.incrementAndGet()
        }
        mutableState.value = CourseDownloadUiState.Running(
            operationId = operationId,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            currentItem = progress.currentItem,
            stage = progress.stage,
        )
    }

    internal fun reportSuccess(updatedTextbooks: Int) {
        val operationId = mutableState.value.operationIdOrNew()
        mutableState.value = CourseDownloadUiState.Success(operationId, updatedTextbooks)
    }

    internal fun reportFailure(message: String) {
        val operationId = mutableState.value.operationIdOrNew()
        mutableState.value = CourseDownloadUiState.Failed(operationId, message)
    }

    fun clearTerminalState() {
        if (mutableState.value is CourseDownloadUiState.Success || mutableState.value is CourseDownloadUiState.Failed) {
            mutableState.value = CourseDownloadUiState.Idle
        }
    }

    private fun CourseDownloadUiState.operationIdOrNew(): Long = when (this) {
        CourseDownloadUiState.Idle -> operationCounter.incrementAndGet()
        is CourseDownloadUiState.Queued -> operationId
        is CourseDownloadUiState.Running -> operationId
        is CourseDownloadUiState.Success -> operationId
        is CourseDownloadUiState.Failed -> operationId
    }
}

sealed interface CourseDownloadUiState {
    data object Idle : CourseDownloadUiState
    data class Queued(val operationId: Long) : CourseDownloadUiState
    data class Running(
        val operationId: Long,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val currentItem: String,
        val stage: String,
    ) : CourseDownloadUiState
    data class Success(val operationId: Long, val updatedTextbooks: Int) : CourseDownloadUiState
    data class Failed(val operationId: Long, val message: String) : CourseDownloadUiState
}
