package com.majortomman.school.data.material

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MaterialPackRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cacheLock = Any()

    @Volatile
    private var installedCache: List<InstalledTextbook> = emptyList()

    @Volatile
    private var latestWorkInfos: List<WorkInfo> = emptyList()

    private var observedWorkStates: Map<UUID, WorkInfo.State> = emptyMap()
    private val mutableState = MutableStateFlow(MaterialLibraryState())
    private val workObserver = Observer<List<WorkInfo>> { workInfos ->
        val current = workInfos.orEmpty()
        val shouldReload = synchronized(cacheLock) {
            val reload = current.any { info ->
                observedWorkStates[info.id] != info.state && info.state == WorkInfo.State.SUCCEEDED
            }
            observedWorkStates = current.associate { it.id to it.state }
            latestWorkInfos = current
            reload
        }
        publish(current)
        if (shouldReload) {
            ioScope.launch {
                reloadInstalledCache()
                publish(latestWorkInfos)
            }
        }
    }

    val state: StateFlow<MaterialLibraryState> = mutableState.asStateFlow()

    init {
        workManager.getWorkInfosByTagLiveData(TextbookProcessingContract.TAG)
            .observeForever(workObserver)
        ioScope.launch {
            val installed = reloadInstalledCache()
            scheduleMissingAnalyses(installed)
            val workInfos = workManager.getWorkInfosByTag(TextbookProcessingContract.TAG).get()
            synchronized(cacheLock) {
                observedWorkStates = workInfos.associate { it.id to it.state }
                latestWorkInfos = workInfos
            }
            publish(workInfos)
        }
    }

    fun enqueueImport(
        slot: TextbookSlot,
        uri: Uri,
    ) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val input = workDataOf(
            TextbookProcessingContract.KEY_SOURCE_URI to uri.toString(),
            TextbookProcessingContract.KEY_SUBJECT_ID to slot.subjectId,
            TextbookProcessingContract.KEY_SUBJECT_TITLE to slot.subjectTitle,
            TextbookProcessingContract.KEY_GRADE to slot.grade,
            TextbookProcessingContract.KEY_VOLUME to slot.volume.id,
            TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
        )
        val importRequest = OneTimeWorkRequestBuilder<TextbookProcessingWorker>()
            .setInputData(input)
            .addTag(TextbookProcessingContract.TAG)
            .addTag(TextbookProcessingContract.slotTag(slot))
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.SECONDS,
            )
            .build()
        workManager.beginUniqueWork(
            TextbookProcessingContract.uniqueWorkName(slot),
            ExistingWorkPolicy.KEEP,
            importRequest,
        )
            .then(analysisRequest(slot))
            .then(questionExtractionRequest(slot))
            .enqueue()
    }

    fun enqueueAnalysis(slot: TextbookSlot) {
        val installed = installedSnapshot().firstOrNull { it.slot.key == slot.key } ?: return
        if (installed.isCloudCourseOnly()) return
        ioScope.launch {
            File(installed.pack.rootPath, "generated/analysis").deleteRecursively()
            File(installed.pack.rootPath, "generated/questions").deleteRecursively()
            workManager.beginUniqueWork(
                analysisWorkName(slot),
                ExistingWorkPolicy.REPLACE,
                analysisRequest(slot),
            )
                .then(questionExtractionRequest(slot))
                .enqueue()
        }
    }

    fun cancelProcessing(slot: TextbookSlot) {
        workManager.cancelUniqueWork(TextbookProcessingContract.uniqueWorkName(slot))
        workManager.cancelUniqueWork(analysisWorkName(slot))
        workManager.cancelUniqueWork(questionWorkName(slot))
    }

    suspend fun loadLessonAnalysis(
        textbook: InstalledTextbook,
        lessonSourceId: String,
    ): LessonAnalysis? = withContext(Dispatchers.IO) {
        LessonAnalysisStore.read(File(textbook.pack.rootPath), lessonSourceId)
    }

    fun analyzedLessonCount(textbook: InstalledTextbook): Int {
        val active = installedSnapshot().firstOrNull { it.key == textbook.key } ?: textbook
        return LessonAnalysisStore.count(File(active.pack.rootPath), active.lessons)
    }

    suspend fun removeInstalled(slot: TextbookSlot) = withContext(Dispatchers.IO) {
        cancelProcessing(slot)
        val removed = MaterialLibraryStore.remove(appContext, slot.key)
        removed?.pack?.rootPath?.let { File(it).deleteRecursively() }
        MaterialLibraryStore.processingRoot(appContext, slot).deleteRecursively()
        reloadInstalledCache()
        val workInfos = workManager.getWorkInfosByTag(TextbookProcessingContract.TAG).get()
        publish(workInfos)
    }

    fun refreshCurrent() {
        ioScope.launch {
            reloadInstalledCache()
            val workInfos = workManager.getWorkInfosByTag(TextbookProcessingContract.TAG).get()
            publish(workInfos)
        }
    }

    private fun reloadInstalledCache(): List<InstalledTextbook> {
        val installed = MaterialLibraryStore.read(appContext)
        synchronized(cacheLock) {
            installedCache = installed
        }
        return installed
    }

    private fun installedSnapshot(): List<InstalledTextbook> = synchronized(cacheLock) {
        installedCache.toList()
    }

    private fun scheduleMissingAnalyses(textbooks: List<InstalledTextbook>) {
        textbooks.filterNot { it.isCloudCourseOnly() }.forEach { textbook ->
            val root = File(textbook.pack.rootPath)
            val completed = LessonAnalysisStore.count(root, textbook.lessons)
            val needsAnalysis = completed < textbook.lessons.size
            val needsQuestions = textbook.slot.subjectId == "math" && textbook.lessons.any { lesson ->
                !TextbookQuestionDraftStore.hasDrafts(root, lesson.sourceId)
            }
            when {
                needsAnalysis -> workManager.beginUniqueWork(
                    TextbookProcessingContract.uniqueWorkName(textbook.slot),
                    ExistingWorkPolicy.KEEP,
                    analysisRequest(textbook.slot),
                )
                    .then(questionExtractionRequest(textbook.slot))
                    .enqueue()

                needsQuestions -> workManager.enqueueUniqueWork(
                    questionWorkName(textbook.slot),
                    ExistingWorkPolicy.KEEP,
                    questionExtractionRequest(textbook.slot),
                )
            }
        }
    }

    private fun InstalledTextbook.isCloudCourseOnly(): Boolean =
        pack.manifest.version.startsWith(CLOUD_COURSE_VERSION_PREFIX) && !pack.pdfFile.isFile

    private fun analysisRequest(slot: TextbookSlot) = OneTimeWorkRequestBuilder<TextbookAnalysisWorker>()
        .setInputData(slotInput(slot))
        .addTag(TextbookProcessingContract.TAG)
        .addTag(TextbookProcessingContract.slotTag(slot))
        .setBackoffCriteria(
            androidx.work.BackoffPolicy.EXPONENTIAL,
            30,
            TimeUnit.SECONDS,
        )
        .build()

    private fun questionExtractionRequest(slot: TextbookSlot) =
        OneTimeWorkRequestBuilder<TextbookQuestionExtractionWorker>()
            .setInputData(slotInput(slot))
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS,
            )
            .build()

    private fun slotInput(slot: TextbookSlot) = workDataOf(
        TextbookProcessingContract.KEY_SUBJECT_ID to slot.subjectId,
        TextbookProcessingContract.KEY_SUBJECT_TITLE to slot.subjectTitle,
        TextbookProcessingContract.KEY_GRADE to slot.grade,
        TextbookProcessingContract.KEY_VOLUME to slot.volume.id,
        TextbookProcessingContract.KEY_SLOT_KEY to slot.key,
    )

    private fun publish(workInfos: List<WorkInfo>) {
        synchronized(cacheLock) {
            latestWorkInfos = workInfos
        }
        val jobs = workInfos
            .mapNotNull(::toProcessingState)
            .groupBy { it.slot.key }
            .mapValues { (_, states) ->
                states.maxByOrNull { statePriority(it.status) } ?: states.first()
            }
        mutableState.value = MaterialLibraryState(
            installedTextbooks = installedSnapshot(),
            processing = jobs,
            message = jobs.values.firstOrNull { it.status == TextbookProcessingStatus.FAILED }?.message,
        )
    }

    private fun toProcessingState(info: WorkInfo): TextbookProcessingState? {
        val slotKey = info.tags
            .firstOrNull { it.startsWith(TextbookProcessingContract.TAG_SLOT_PREFIX) }
            ?.removePrefix(TextbookProcessingContract.TAG_SLOT_PREFIX)
            ?: return null
        val slot = TextbookSlot.fromKey(slotKey) ?: return null
        val data = if (info.state == WorkInfo.State.FAILED) info.outputData else info.progress
        val stage = data.getString(TextbookProcessingContract.KEY_STAGE)
            ?.let { runCatching { TextbookProcessingStage.valueOf(it) }.getOrNull() }
            ?: TextbookProcessingStage.PREPARING
        val progress = data.getInt(TextbookProcessingContract.KEY_PROGRESS, 0).coerceIn(0, 100)
        val message = data.getString(TextbookProcessingContract.KEY_MESSAGE)
            ?: when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "等待后台处理"
                WorkInfo.State.RUNNING -> stage.label
                WorkInfo.State.FAILED -> "教材处理失败"
                else -> ""
            }
        val status = when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> TextbookProcessingStatus.QUEUED
            WorkInfo.State.RUNNING -> TextbookProcessingStatus.RUNNING
            WorkInfo.State.FAILED -> TextbookProcessingStatus.FAILED
            else -> return null
        }
        return TextbookProcessingState(
            slot = slot,
            status = status,
            stage = stage,
            progress = progress,
            message = message,
        )
    }

    private fun statePriority(status: TextbookProcessingStatus): Int = when (status) {
        TextbookProcessingStatus.RUNNING -> 3
        TextbookProcessingStatus.QUEUED -> 2
        TextbookProcessingStatus.FAILED -> 1
    }

    private fun analysisWorkName(slot: TextbookSlot): String = "textbook-analysis-${slot.key}"
    private fun questionWorkName(slot: TextbookSlot): String = "textbook-question-extraction-${slot.key}"

    private companion object {
        const val CLOUD_COURSE_VERSION_PREFIX = "cloud-course-"
    }
}
