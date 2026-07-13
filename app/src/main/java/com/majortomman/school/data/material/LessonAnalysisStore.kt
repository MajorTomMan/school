package com.majortomman.school.data.material

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

internal object LessonAnalysisStore {
    private const val GENERATED_DIRECTORY = "generated"
    private const val ANALYSIS_DIRECTORY = "analysis"
    private const val PACK_ANALYSIS_DIRECTORY = "analysis/lessons"

    fun read(
        textbookRoot: File,
        lessonSourceId: String,
    ): LessonAnalysis? = runCatching {
        val file = generatedFile(textbookRoot, lessonSourceId)
        if (!file.isFile) return@runCatching null
        val root = JSONObject(file.readText(Charsets.UTF_8))
        if (root.optInt("schemaVersion", 1) != LESSON_ANALYSIS_SCHEMA_VERSION) {
            return@runCatching null
        }
        LessonAnalysis.fromJson(root)
    }.getOrNull()

    fun readPackProvided(
        textbookRoot: File,
        lesson: GeneratedLesson,
    ): LessonAnalysis? = runCatching {
        val candidates = listOf(
            File(textbookRoot, "$PACK_ANALYSIS_DIRECTORY/${safeStem(lesson.sourceId)}.json"),
            File(textbookRoot, "$PACK_ANALYSIS_DIRECTORY/${lesson.sourceId}.json"),
        )
        val file = candidates.firstOrNull { it.isFile } ?: return@runCatching null
        val root = JSONObject(file.readText(Charsets.UTF_8))
            .put("schemaVersion", LESSON_ANALYSIS_SCHEMA_VERSION)
            .put("lessonSourceId", lesson.sourceId)
            .put("pageStart", lesson.pageStart)
            .put("pageEnd", lesson.pageEnd)
            .put("source", LessonAnalysisSource.PACK.name)
        LessonAnalysis.fromJson(root, LessonAnalysisSource.PACK)
    }.getOrNull()

    fun write(
        textbookRoot: File,
        analysis: LessonAnalysis,
    ) {
        val file = generatedFile(textbookRoot, analysis.lessonSourceId)
        file.parentFile?.let { parent ->
            require(parent.mkdirs() || parent.isDirectory) { "无法创建课程分析目录" }
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(analysis.toJson().toString(2), Charsets.UTF_8)
        if (file.exists()) file.delete()
        require(temporary.renameTo(file)) { "无法保存课程分析结果" }
    }

    fun count(
        textbookRoot: File,
        lessons: List<GeneratedLesson>,
    ): Int = lessons.count { read(textbookRoot, it.sourceId) != null }

    private fun generatedFile(root: File, lessonSourceId: String): File =
        File(root, "$GENERATED_DIRECTORY/$ANALYSIS_DIRECTORY/${stableName(lessonSourceId)}.json")

    private fun stableName(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return digest.take(24)
    }

    private fun safeStem(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .take(96)
        .ifBlank { stableName(value) }
}
