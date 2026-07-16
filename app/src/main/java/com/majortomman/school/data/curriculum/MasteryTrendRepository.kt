package com.majortomman.school.data.curriculum

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.majortomman.school.data.local.KnowledgeMasteryEntity
import com.majortomman.school.data.local.SchoolDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class MasteryTrendEventType {
    BASELINE,
    CORRECT,
    INCORRECT,
}

data class KnowledgeMasteryHistory(
    val subjectId: String,
    val knowledgePointId: String,
    val score: Double,
    val attempts: Int,
    val correctStreak: Int,
    val wrongStreak: Int,
    val lastCorrect: Boolean,
    val eventType: MasteryTrendEventType,
    val recordedAt: Long,
)

data class SubjectMasteryDailySnapshot(
    val subjectId: String,
    val epochDay: Long,
    val averageScore: Double,
    val knowledgeCount: Int,
    val practicedCount: Int,
    val dueKnowledgeCount: Int,
    val updatedAt: Long,
)

@Entity(
    tableName = "knowledge_mastery_history",
    indices = [
        Index(value = ["subjectId", "knowledgePointId", "sourceUpdatedAt"], unique = true),
        Index(value = ["subjectId", "knowledgePointId", "recordedAt"]),
        Index(value = ["subjectId", "recordedAt"]),
    ],
)
internal data class KnowledgeMasteryHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: String,
    val knowledgePointId: String,
    val score: Double,
    val attempts: Int,
    val correctStreak: Int,
    val wrongStreak: Int,
    val lastCorrect: Boolean,
    val eventType: String,
    val sourceUpdatedAt: Long,
    val recordedAt: Long,
)

@Entity(
    tableName = "subject_mastery_daily",
    primaryKeys = ["subjectId", "epochDay"],
    indices = [Index(value = ["epochDay"]), Index(value = ["updatedAt"])],
)
internal data class SubjectMasteryDailyEntity(
    val subjectId: String,
    val epochDay: Long,
    val averageScore: Double,
    val knowledgeCount: Int,
    val practicedCount: Int,
    val dueKnowledgeCount: Int,
    val updatedAt: Long,
)

@Dao
internal interface MasteryTrendDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(items: List<KnowledgeMasteryHistoryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailySnapshot(item: SubjectMasteryDailyEntity)

    @Query(
        """
        SELECT * FROM knowledge_mastery_history
        WHERE subjectId = :subjectId
          AND knowledgePointId = :knowledgePointId
          AND recordedAt >= :sinceMillis
        ORDER BY recordedAt, id
        """,
    )
    fun observeKnowledgeHistory(
        subjectId: String,
        knowledgePointId: String,
        sinceMillis: Long,
    ): Flow<List<KnowledgeMasteryHistoryEntity>>

    @Query(
        """
        SELECT * FROM subject_mastery_daily
        WHERE subjectId = :subjectId AND epochDay >= :sinceEpochDay
        ORDER BY epochDay
        """,
    )
    fun observeSubjectDaily(
        subjectId: String,
        sinceEpochDay: Long,
    ): Flow<List<SubjectMasteryDailyEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM knowledge_mastery_history
        WHERE subjectId = :subjectId
          AND eventType != 'BASELINE'
          AND recordedAt >= :dayStartMillis
          AND recordedAt < :dayEndMillis
        """,
    )
    suspend fun practicedCount(
        subjectId: String,
        dayStartMillis: Long,
        dayEndMillis: Long,
    ): Int

    @Query("DELETE FROM knowledge_mastery_history")
    suspend fun clearHistory()

    @Query("DELETE FROM subject_mastery_daily")
    suspend fun clearDailySnapshots()
}

@Database(
    entities = [KnowledgeMasteryHistoryEntity::class, SubjectMasteryDailyEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class MasteryAnalyticsDatabase : RoomDatabase() {
    abstract fun masteryTrendDao(): MasteryTrendDao
}

class MasteryTrendRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val coreDao = SchoolDatabase.getInstance(appContext).curriculumDao()
    private val analyticsDatabase = Room.databaseBuilder(
        appContext,
        MasteryAnalyticsDatabase::class.java,
        ANALYTICS_DATABASE_NAME,
    ).build()
    private val dao = analyticsDatabase.masteryTrendDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var firstSynchronization = true

    init {
        scope.launch {
            coreDao.observeAllKnowledgeMastery().collect { rows ->
                synchronize(rows)
            }
        }
    }

    fun observeSubjectTrend(
        subjectId: String,
        days: Int = DEFAULT_DAYS,
    ): Flow<List<SubjectMasteryDailySnapshot>> {
        if (subjectId.isBlank()) return flowOf(emptyList())
        val since = LocalDate.now()
            .minusDays((days.coerceAtLeast(1) - 1).toLong())
            .toEpochDay()
        return dao.observeSubjectDaily(subjectId, since).map { rows ->
            rows.map { row ->
                SubjectMasteryDailySnapshot(
                    subjectId = row.subjectId,
                    epochDay = row.epochDay,
                    averageScore = row.averageScore,
                    knowledgeCount = row.knowledgeCount,
                    practicedCount = row.practicedCount,
                    dueKnowledgeCount = row.dueKnowledgeCount,
                    updatedAt = row.updatedAt,
                )
            }
        }
    }

    fun observeKnowledgeTrend(
        subjectId: String,
        knowledgePointId: String,
        days: Int = DEFAULT_DAYS,
    ): Flow<List<KnowledgeMasteryHistory>> {
        if (subjectId.isBlank() || knowledgePointId.isBlank()) return flowOf(emptyList())
        val since = System.currentTimeMillis() - days.coerceAtLeast(1) * DAY_MILLIS
        return dao.observeKnowledgeHistory(subjectId, knowledgePointId, since).map { rows ->
            rows.map { row ->
                KnowledgeMasteryHistory(
                    subjectId = row.subjectId,
                    knowledgePointId = row.knowledgePointId,
                    score = row.score,
                    attempts = row.attempts,
                    correctStreak = row.correctStreak,
                    wrongStreak = row.wrongStreak,
                    lastCorrect = row.lastCorrect,
                    eventType = runCatching { MasteryTrendEventType.valueOf(row.eventType) }
                        .getOrDefault(MasteryTrendEventType.BASELINE),
                    recordedAt = row.recordedAt,
                )
            }
        }
    }

    private suspend fun synchronize(rows: List<KnowledgeMasteryEntity>) {
        if (rows.isEmpty()) {
            analyticsDatabase.withTransaction {
                dao.clearHistory()
                dao.clearDailySnapshots()
            }
            firstSynchronization = false
            return
        }

        val observedAt = System.currentTimeMillis()
        val eventTypeForInitialEmission = firstSynchronization
        val candidates = rows.map { row ->
            KnowledgeMasteryHistoryEntity(
                subjectId = row.subjectId,
                knowledgePointId = row.knowledgePointId,
                score = row.score,
                attempts = row.attempts,
                correctStreak = row.correctStreak,
                wrongStreak = row.wrongStreak,
                lastCorrect = row.lastCorrect,
                eventType = when {
                    eventTypeForInitialEmission -> MasteryTrendEventType.BASELINE.name
                    row.lastCorrect -> MasteryTrendEventType.CORRECT.name
                    else -> MasteryTrendEventType.INCORRECT.name
                },
                sourceUpdatedAt = row.updatedAt,
                recordedAt = observedAt,
            )
        }

        analyticsDatabase.withTransaction {
            val insertResults = dao.insertHistory(candidates)
            val changedSubjects = candidates.zip(insertResults)
                .filter { (_, insertedId) -> insertedId != -1L }
                .map { (event, _) -> event.subjectId }
                .toSet()

            changedSubjects.forEach { subjectId ->
                val subjectRows = rows.filter { it.subjectId == subjectId }
                if (subjectRows.isEmpty()) return@forEach
                val zone = ZoneId.systemDefault()
                val date = Instant.ofEpochMilli(observedAt).atZone(zone).toLocalDate()
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                dao.upsertDailySnapshot(
                    SubjectMasteryDailyEntity(
                        subjectId = subjectId,
                        epochDay = date.toEpochDay(),
                        averageScore = subjectRows.map { it.score }.average().coerceIn(0.0, 1.0),
                        knowledgeCount = subjectRows.size,
                        practicedCount = dao.practicedCount(subjectId, dayStart, dayEnd),
                        dueKnowledgeCount = subjectRows.count { it.dueAt <= observedAt },
                        updatedAt = observedAt,
                    ),
                )
            }
        }
        firstSynchronization = false
    }

    companion object {
        private const val ANALYTICS_DATABASE_NAME = "school-analytics.db"
        private const val DEFAULT_DAYS = 30
        private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

        @Volatile
        private var instance: MasteryTrendRepository? = null

        fun getInstance(context: Context): MasteryTrendRepository = instance ?: synchronized(this) {
            instance ?: MasteryTrendRepository(context).also { instance = it }
        }
    }
}

/**
 * 进程启动时开启掌握度趋势监听。它不暴露任何数据接口，只负责保证即使用户没有打开
 * “路径”页面，所有学科的通用掌握度变化也会持续写入本地分析库。
 */
class MasteryTrendBootstrapProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.applicationContext?.let(MasteryTrendRepository::getInstance)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
