package com.majortomman.school.data.material

import org.json.JSONArray
import org.json.JSONObject

const val LESSON_ANALYSIS_SCHEMA_VERSION = 3

enum class LessonAnalysisSource(val label: String) {
    PACK("云端课程包"),
    AI_TEXT("旧版文本分析"),
    AI_VISION("旧版页面分析"),
    OCR_FALLBACK("旧版本地分析"),
    CATALOG_FALLBACK("旧版目录分析"),
}

enum class LessonSceneType {
    NUMBER_LINE,
    MIRROR,
    DISTANCE,
    COMPARISON,
    PROCESS,
    TEXT,
}

data class LessonSceneSpec(
    val type: LessonSceneType,
    val title: String,
    val prompt: String,
    val values: List<Double> = emptyList(),
    val labels: List<String> = emptyList(),
    val expression: String = "",
    val conclusion: String,
    val steps: List<String> = emptyList(),
    val sourcePage: Int,
)

data class GeneratedExercise(
    val question: String,
    val acceptedAnswers: List<String>,
    val hints: List<String>,
    val explanation: String,
)

data class LessonAnalysis(
    val schemaVersion: Int = LESSON_ANALYSIS_SCHEMA_VERSION,
    val lessonSourceId: String,
    val summary: String,
    val objectives: List<String>,
    val misconception: String,
    val sourcePages: IntRange,
    val scene: LessonSceneSpec,
    val exercise: GeneratedExercise,
    val source: LessonAnalysisSource,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("lessonSourceId", lessonSourceId)
        .put("summary", summary)
        .put("objectives", JSONArray(objectives))
        .put("misconception", misconception)
        .put("pageStart", sourcePages.first)
        .put("pageEnd", sourcePages.last)
        .put("source", source.name)
        .put(
            "scene",
            JSONObject()
                .put("type", scene.type.name)
                .put("title", scene.title)
                .put("prompt", scene.prompt)
                .put("values", JSONArray(scene.values))
                .put("labels", JSONArray(scene.labels))
                .put("expression", scene.expression)
                .put("conclusion", scene.conclusion)
                .put("steps", JSONArray(scene.steps))
                .put("sourcePage", scene.sourcePage),
        )
        .put(
            "exercise",
            JSONObject()
                .put("question", exercise.question)
                .put("acceptedAnswers", JSONArray(exercise.acceptedAnswers))
                .put("hints", JSONArray(exercise.hints))
                .put("explanation", exercise.explanation),
        )

    companion object {
        fun fromJson(root: JSONObject, forcedSource: LessonAnalysisSource? = null): LessonAnalysis {
            val sceneObject = root.optJSONObject("scene") ?: JSONObject()
            val exerciseObject = root.optJSONObject("exercise") ?: JSONObject()
            val pageStart = root.optInt("pageStart", sceneObject.optInt("sourcePage", 1)).coerceAtLeast(1)
            val pageEnd = root.optInt("pageEnd", pageStart).coerceAtLeast(pageStart)
            val sourcePage = sceneObject.optInt("sourcePage", pageStart).coerceIn(pageStart, pageEnd)
            return LessonAnalysis(
                schemaVersion = root.optInt("schemaVersion", LESSON_ANALYSIS_SCHEMA_VERSION),
                lessonSourceId = root.optString("lessonSourceId").trim(),
                summary = root.optString("summary").trim(),
                objectives = root.optJSONArray("objectives").toStringList().take(8),
                misconception = root.optString("misconception").trim(),
                sourcePages = pageStart..pageEnd,
                scene = LessonSceneSpec(
                    type = sceneObject.optString("type")
                        .let { value -> runCatching { LessonSceneType.valueOf(value.uppercase()) }.getOrNull() }
                        ?: LessonSceneType.TEXT,
                    title = sceneObject.optString("title").trim(),
                    prompt = sceneObject.optString("prompt").trim(),
                    values = sceneObject.optJSONArray("values").toDoubleList().take(16),
                    labels = sceneObject.optJSONArray("labels").toStringList().take(16),
                    expression = sceneObject.optString("expression").trim(),
                    conclusion = sceneObject.optString("conclusion").trim(),
                    steps = sceneObject.optJSONArray("steps").toStringList().take(16),
                    sourcePage = sourcePage,
                ),
                exercise = GeneratedExercise(
                    question = exerciseObject.optString("question").trim(),
                    acceptedAnswers = exerciseObject.optJSONArray("acceptedAnswers").toStringList().take(32),
                    hints = exerciseObject.optJSONArray("hints").toStringList().take(12),
                    explanation = exerciseObject.optString("explanation").trim(),
                ),
                source = forcedSource ?: root.optString("source")
                    .let { value -> runCatching { LessonAnalysisSource.valueOf(value) }.getOrNull() }
                    ?: LessonAnalysisSource.PACK,
            )
        }

        fun fromModelResponse(
            raw: String,
            lesson: GeneratedLesson,
            source: LessonAnalysisSource = LessonAnalysisSource.PACK,
        ): LessonAnalysis {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "模型没有返回课程 JSON" }
            val root = JSONObject(raw.substring(start, end + 1))
                .put("schemaVersion", LESSON_ANALYSIS_SCHEMA_VERSION)
                .put("lessonSourceId", lesson.sourceId)
                .put("pageStart", lesson.pageStart)
                .put("pageEnd", lesson.pageEnd)
                .put("source", source.name)
            return fromJson(root, source)
        }
    }
}

private fun JSONArray?.toStringList(): List<String> = buildList {
    val source = this@toStringList ?: return@buildList
    for (index in 0 until source.length()) {
        source.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
    }
}

private fun JSONArray?.toDoubleList(): List<Double> = buildList {
    val source = this@toDoubleList ?: return@buildList
    for (index in 0 until source.length()) {
        val value = source.optDouble(index, Double.NaN)
        if (!value.isNaN() && value.isFinite()) add(value)
    }
}
