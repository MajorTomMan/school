package com.majortomman.school.data.material

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TextbookQuestionExtractionWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val slotKey = inputData.getString(TextbookProcessingContract.KEY_SLOT_KEY)
            ?: return@withContext Result.failure()
        val textbook = MaterialLibraryStore.read(applicationContext)
            .firstOrNull { it.slot.key == slotKey }
            ?: return@withContext Result.retry()
        val root = File(textbook.pack.rootPath)

        textbook.lessons.forEach { lesson ->
            if (isStopped) return@withContext Result.retry()
            if (TextbookQuestionDraftStore.hasDrafts(root, lesson.sourceId)) return@forEach
            val pages = representativePages(lesson)
                .mapNotNull { printedPage -> TextbookOcrStore.read(root, printedPage) }
            if (pages.isNotEmpty()) {
                TextbookQuestionDraftStore.extractAndWrite(root, lesson, pages)
            }
        }
        Result.success()
    }

    private fun representativePages(lesson: GeneratedLesson): Set<Int> = linkedSetOf(
        lesson.pageStart,
        (lesson.pageStart + lesson.pageEnd) / 2,
        lesson.pageEnd,
    )
}
