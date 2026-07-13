package com.majortomman.school.data.material

import org.json.JSONArray
import org.json.JSONObject

const val LESSON_ANALYSIS_SCHEMA_VERSION = 1

enum class LessonAnalysisSource(val label: String) {
    PACK("教材包扫描结果"),
    AI_VISION("教材页面视觉分析"),
    CATALOG_FALLBACK("目录生成"),
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
            return LessonAnalysis(
                schemaVersion = root.optInt("schemaVersion", LESSON_ANALYSIS_SCHEMA_VERSION),
                lessonSourceId = root.optString("lessonSourceId").trim(),
                summary = root.optString("summary").trim(),
                objectives = root.optJSONArray("objectives").toStringList(),
                misconception = root.optString("misconception").trim(),
                sourcePages = pageStart..pageEnd,
                scene = LessonSceneSpec(
                    type = sceneObject.optString("type")
                        .let { value -> runCatching { LessonSceneType.valueOf(value.uppercase()) }.getOrNull() }
                        ?: LessonSceneType.TEXT,
                    title = sceneObject.optString("title").trim(),
                    prompt = sceneObject.optString("prompt").trim(),
                    values = sceneObject.optJSONArray("values").toDoubleList().take(8),
                    labels = sceneObject.optJSONArray("labels").toStringList().take(8),
                    expression = sceneObject.optString("expression").trim(),
                    conclusion = sceneObject.optString("conclusion").trim(),
                    steps = sceneObject.optJSONArray("steps").toStringList().take(8),
                    sourcePage = sceneObject.optInt("sourcePage", pageStart).coerceIn(pageStart, pageEnd),
                ),
                exercise = GeneratedExercise(
                    question = exerciseObject.optString("question").trim(),
                    acceptedAnswers = exerciseObject.optJSONArray("acceptedAnswers").toStringList().take(12),
                    hints = exerciseObject.optJSONArray("hints").toStringList().take(6),
                    explanation = exerciseObject.optString("explanation").trim(),
                ),
                source = forcedSource ?: root.optString("source")
                    .let { value -> runCatching { LessonAnalysisSource.valueOf(value) }.getOrNull() }
                    ?: LessonAnalysisSource.CATALOG_FALLBACK,
            ).normalized()
        }

        fun fromModelResponse(
            raw: String,
            lesson: GeneratedLesson,
        ): LessonAnalysis {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start) { "模型没有返回课程 JSON" }
            val root = JSONObject(raw.substring(start, end + 1))
                .put("lessonSourceId", lesson.sourceId)
                .put("pageStart", lesson.pageStart)
                .put("pageEnd", lesson.pageEnd)
                .put("source", LessonAnalysisSource.AI_VISION.name)
            return fromJson(root, LessonAnalysisSource.AI_VISION)
        }
    }

    private fun normalized(): LessonAnalysis {
        val safeSummary = summary.ifBlank { "围绕课程主题理解教材中的核心概念与例题。" }.take(1_200)
        val safeObjectives = objectives.filter { it.isNotBlank() }.map { it.take(160) }.take(5).ifEmpty {
            listOf("理解本节教材的核心概念", "能说明教材例题的关键依据", "完成本节独立练习")
        }
        val safeMisconception = misconception.ifBlank { "不要只记结论，先确认教材中的条件、定义和适用范围。" }.take(800)
        val safeScene = scene.copy(
            title = scene.title.ifBlank { "观察教材中的关系" }.take(120),
            prompt = scene.prompt.ifBlank { "教材中的对象发生了什么变化？" }.take(300),
            expression = scene.expression.take(180),
            conclusion = scene.conclusion.ifBlank { safeSummary }.take(500),
            steps = scene.steps.filter { it.isNotBlank() }.map { it.take(160) }.take(8),
        )
        val safeExercise = exercise.copy(
            question = exercise.question.ifBlank { "请用自己的话说明本节最重要的结论。" }.take(800),
            acceptedAnswers = exercise.acceptedAnswers.filter { it.isNotBlank() }.map { it.take(120) }.take(12),
            hints = exercise.hints.filter { it.isNotBlank() }.map { it.take(220) }.take(6).ifEmpty {
                listOf("先回忆动态场景中发生变化的对象。", "再核对教材原页中的定义或例题。")
            },
            explanation = exercise.explanation.ifBlank { safeSummary }.take(800),
        )
        return copy(
            summary = safeSummary,
            objectives = safeObjectives,
            misconception = safeMisconception,
            scene = safeScene,
            exercise = safeExercise,
        )
    }
}

object LessonAnalysisFallback {
    fun generate(
        slot: TextbookSlot,
        lesson: GeneratedLesson,
    ): LessonAnalysis {
        val title = lesson.title
        val page = lesson.pageStart
        val lower = title.lowercase()
        val scene = when {
            slot.subjectId == "math" && (title.contains("数轴") || lower.contains("number line")) -> LessonSceneSpec(
                type = LessonSceneType.NUMBER_LINE,
                title = "把数变成位置",
                prompt = "-3 和 2 放在同一条数轴上，谁在右边？",
                values = listOf(-3.0, 2.0),
                labels = listOf("-3", "2"),
                expression = "-3 < 2",
                conclusion = "数轴上越靠右的数越大。",
                steps = listOf("确定原点", "确认正方向", "按单位长度放置数字", "比较左右位置"),
                sourcePage = page,
            )
            slot.subjectId == "math" && title.contains("相反") -> LessonSceneSpec(
                type = LessonSceneType.MIRROR,
                title = "从原点向两边看",
                prompt = "两个点到原点距离相同，但方向相反，会得到什么关系？",
                values = listOf(-3.0, 3.0),
                labels = listOf("-3", "3"),
                expression = "-3 ↔ 3",
                conclusion = "到原点距离相等、方向相反的两个数互为相反数。",
                steps = listOf("找到原点", "向右移动 3 格", "向左移动 3 格", "比较两边距离"),
                sourcePage = page,
            )
            slot.subjectId == "math" && title.contains("绝对值") -> LessonSceneSpec(
                type = LessonSceneType.DISTANCE,
                title = "只保留距离",
                prompt = "去掉方向后，-5 和 5 到原点的距离分别是多少？",
                values = listOf(-5.0, 5.0),
                labels = listOf("-5", "5"),
                expression = "|-5| = |5| = 5",
                conclusion = "绝对值表示到原点的距离，因此不会是负数。",
                steps = listOf("放置两个点", "连接到原点", "忽略左右方向", "比较距离长度"),
                sourcePage = page,
            )
            slot.subjectId == "math" && (title.contains("比较") || title.contains("大小")) -> LessonSceneSpec(
                type = LessonSceneType.COMPARISON,
                title = "先看位置，再下结论",
                prompt = "-8 和 -3 都是负数，哪一个在数轴上更靠右？",
                values = listOf(-8.0, -3.0),
                labels = listOf("-8", "-3"),
                expression = "-8 < -3",
                conclusion = "两个负数比较时，绝对值更大的数在数轴上更靠左。",
                steps = listOf("判断正负", "放到数轴", "比较左右位置", "写出大小关系"),
                sourcePage = page,
            )
            else -> LessonSceneSpec(
                type = LessonSceneType.PROCESS,
                title = "把教材拆成过程",
                prompt = "${lesson.title}中，哪些条件会一步一步导向结论？",
                expression = "",
                conclusion = "先识别条件与对象，再按照教材顺序建立关系。",
                steps = listOf("定位教材原页", "找出核心对象", "识别条件或变化", "形成结论", "用例题验证"),
                sourcePage = page,
            )
        }
        return LessonAnalysis(
            lessonSourceId = lesson.sourceId,
            summary = when (scene.type) {
                LessonSceneType.NUMBER_LINE -> "数轴把抽象的数变成位置，原点、正方向和单位长度共同决定每个数的位置。"
                LessonSceneType.MIRROR -> "相反数可以理解为关于原点对称的两个位置。"
                LessonSceneType.DISTANCE -> "绝对值只描述一个数到原点的距离，不保留方向。"
                LessonSceneType.COMPARISON -> "大小关系可以通过教材中的位置、顺序或数量关系直观判断。"
                else -> "本节根据教材第 ${lesson.pageStart}—${lesson.pageEnd} 页的目录和页面范围生成学习过程。"
            },
            objectives = listOf(
                "理解${lesson.title}的核心对象和条件",
                "能从教材图形、定义或例题中得到结论",
                "能用自己的话解释${lesson.title}",
            ),
            misconception = when (scene.type) {
                LessonSceneType.NUMBER_LINE -> "只画带箭头的直线还不够，数轴必须同时有原点、正方向和统一的单位长度。"
                LessonSceneType.MIRROR -> "相反数不是倒数。相反数描述符号和位置，倒数描述乘积等于 1。"
                LessonSceneType.DISTANCE -> "距离不能是负数，所以负数的绝对值不会仍然是负数。"
                LessonSceneType.COMPARISON -> "不要只比较数字表面的大小；涉及负数时应先回到数轴位置。"
                else -> "不要脱离教材条件直接记结论，先确认定义和例题的适用范围。"
            },
            sourcePages = lesson.pageStart..lesson.pageEnd,
            scene = scene,
            exercise = GeneratedExercise(
                question = when (scene.type) {
                    LessonSceneType.NUMBER_LINE -> "在数轴上，-3 与 2 哪个数更大？请说明理由。"
                    LessonSceneType.MIRROR -> "写出 -3 的相反数，并说明它们在数轴上的位置关系。"
                    LessonSceneType.DISTANCE -> "|-5| 等于多少？为什么结果不是负数？"
                    LessonSceneType.COMPARISON -> "比较 -8 与 -3 的大小，并说明理由。"
                    else -> "请用两三句话说明${lesson.title}的核心结论和成立条件。"
                },
                acceptedAnswers = when (scene.type) {
                    LessonSceneType.NUMBER_LINE -> listOf("2", "2更大", "2 > -3", "-3 < 2")
                    LessonSceneType.MIRROR -> listOf("3", "-3的相反数是3")
                    LessonSceneType.DISTANCE -> listOf("5", "|-5|=5")
                    LessonSceneType.COMPARISON -> listOf("-8 < -3", "-3更大")
                    else -> emptyList()
                },
                hints = scene.steps.take(3),
                explanation = scene.conclusion,
            ),
            source = LessonAnalysisSource.CATALOG_FALLBACK,
        )
    }
}

private fun JSONArray?.toStringList(): List<String> = buildList {
    if (this@toStringList == null) return@buildList
    for (index in 0 until length()) {
        val value = optString(index).trim()
        if (value.isNotEmpty()) add(value)
    }
}

private fun JSONArray?.toDoubleList(): List<Double> = buildList {
    if (this@toDoubleList == null) return@buildList
    for (index in 0 until length()) {
        val value = optDouble(index, Double.NaN)
        if (!value.isNaN() && value.isFinite()) add(value)
    }
}
