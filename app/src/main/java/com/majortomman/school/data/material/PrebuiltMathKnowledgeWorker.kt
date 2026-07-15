package com.majortomman.school.data.material

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PrebuiltMathKnowledgeWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val slotKey = inputData.getString(TextbookProcessingContract.KEY_SLOT_KEY)
            ?: return@withContext Result.failure(
                workDataOf(TextbookProcessingContract.KEY_MESSAGE to "教材槽位信息不完整"),
            )
        try {
            val textbook = MaterialLibraryStore.read(applicationContext)
                .firstOrNull { it.slot.key == slotKey }
                ?: return@withContext Result.retry()
            val upgraded = BundledMathKnowledgePack.upgradeIfMatched(applicationContext, textbook)
            val message = if (upgraded.pack.manifest.version == PREBUILT_MATH_VERSION) {
                "已加载预制数学知识包"
            } else {
                "未匹配预制知识包，继续使用本地教材分析"
            }
            Result.success(
                workDataOf(
                    TextbookProcessingContract.KEY_SLOT_KEY to slotKey,
                    TextbookProcessingContract.KEY_STAGE to TextbookProcessingStage.GENERATING_COURSES.name,
                    TextbookProcessingContract.KEY_PROGRESS to 1,
                    TextbookProcessingContract.KEY_MESSAGE to message,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(
                workDataOf(
                    TextbookProcessingContract.KEY_SLOT_KEY to slotKey,
                    TextbookProcessingContract.KEY_STAGE to TextbookProcessingStage.GENERATING_COURSES.name,
                    TextbookProcessingContract.KEY_PROGRESS to 0,
                    TextbookProcessingContract.KEY_MESSAGE to (error.message ?: "预制数学知识包安装失败"),
                ),
            )
        }
    }

    private companion object {
        const val PREBUILT_MATH_VERSION = "prebuilt-math-v1"
    }
}
