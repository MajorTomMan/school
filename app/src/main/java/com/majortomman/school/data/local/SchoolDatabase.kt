package com.majortomman.school.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PracticeAttemptEntity::class,
        ReviewScheduleEntity::class,
        MathPracticeAttemptEntity::class,
        MathKnowledgeMasteryEntity::class,
        MathMistakeEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SchoolDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao

    companion object {
        @Volatile
        private var instance: SchoolDatabase? = null

        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `math_practice_attempts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `textbookKey` TEXT NOT NULL,
                        `lessonId` TEXT,
                        `questionId` TEXT NOT NULL,
                        `templateId` TEXT NOT NULL,
                        `knowledgePointId` TEXT NOT NULL,
                        `questionJson` TEXT NOT NULL,
                        `answer` TEXT NOT NULL,
                        `canonicalAnswer` TEXT NOT NULL,
                        `correct` INTEGER NOT NULL,
                        `mistakeType` TEXT,
                        `usedHint` INTEGER NOT NULL,
                        `durationMillis` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_math_practice_attempts_textbookKey` ON `math_practice_attempts` (`textbookKey`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_math_practice_attempts_knowledgePointId` ON `math_practice_attempts` (`knowledgePointId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_math_practice_attempts_questionId` ON `math_practice_attempts` (`questionId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_math_practice_attempts_createdAt` ON `math_practice_attempts` (`createdAt`)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `math_knowledge_mastery` (
                        `textbookKey` TEXT NOT NULL,
                        `knowledgePointId` TEXT NOT NULL,
                        `score` REAL NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `correctStreak` INTEGER NOT NULL,
                        `wrongStreak` INTEGER NOT NULL,
                        `lastCorrect` INTEGER NOT NULL,
                        `dueAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`textbookKey`, `knowledgePointId`)
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_math_knowledge_mastery_dueAt` ON `math_knowledge_mastery` (`dueAt`)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `math_mistakes` (
                        `mistakeKey` TEXT NOT NULL,
                        `textbookKey` TEXT NOT NULL,
                        `lessonId` TEXT,
                        `questionId` TEXT NOT NULL,
                        `templateId` TEXT NOT NULL,
                        `knowledgePointId` TEXT NOT NULL,
                        `questionJson` TEXT NOT NULL,
                        `wrongCount` INTEGER NOT NULL,
                        `resolvedStreak` INTEGER NOT NULL,
                        `lastAnswer` TEXT NOT NULL,
                        `mistakeType` TEXT NOT NULL,
                        `dueAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`mistakeKey`)
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_math_mistakes_textbookKey` ON `math_mistakes` (`textbookKey`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_math_mistakes_knowledgePointId` ON `math_mistakes` (`knowledgePointId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_math_mistakes_dueAt` ON `math_mistakes` (`dueAt`)")
            }
        }

        fun getInstance(context: Context): SchoolDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SchoolDatabase::class.java,
                "school.db",
            )
                .addMigrations(migration1To2)
                .build()
                .also { instance = it }
        }
    }
}
