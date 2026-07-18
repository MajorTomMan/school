package com.majortomman.school.data.material

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Read-only runtime view of cloud-installed textbooks.
 *
 * Textbooks, PDFs, catalogues and exercises are installed by the cloud course synchronizer. This
 * repository deliberately has no local file picker, URI permission, OCR or PDF import pipeline.
 */
class MaterialPackRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(MaterialLibraryState())

    val state: StateFlow<MaterialLibraryState> = mutableState.asStateFlow()

    init {
        refreshCurrent()
    }

    suspend fun loadLessonAnalysis(
        textbook: InstalledTextbook,
        lessonSourceId: String,
    ): LessonAnalysis? = withContext(Dispatchers.IO) {
        LessonAnalysisStore.read(File(textbook.pack.rootPath), lessonSourceId)
    }

    fun analyzedLessonCount(textbook: InstalledTextbook): Int =
        LessonAnalysisStore.count(File(textbook.pack.rootPath), textbook.lessons)

    suspend fun removeInstalled(slot: TextbookSlot) = withContext(Dispatchers.IO) {
        val removed = MaterialLibraryStore.remove(appContext, slot.key)
        removed?.pack?.rootPath?.let { File(it).deleteRecursively() }
        publish()
    }

    fun refreshCurrent() {
        ioScope.launch { publish() }
    }

    private fun publish() {
        mutableState.value = MaterialLibraryState(
            installedTextbooks = MaterialLibraryStore.read(appContext),
        )
    }
}
