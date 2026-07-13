package com.majortomman.school.data.math

import org.json.JSONArray
import org.json.JSONObject

const val MATH_QUESTION_SCHEMA_VERSION = 1

enum class MathQuestionType(val label: String) {
    SINGLE_CHOICE("单项选择"),
    NUMERIC_INPUT("数值填空"),
    EXPRESSION_INPUT("表达式"),
    ORDERING("排序"),
    NUMBER_LINE_POINT("数轴点选"),
    STEP_BY_STEP("分步解题"),
}

enum class MathPracticeMode(val label: String, val description: String) {
    TEXTBOOK("教材同步", "按照当前教材章节和原题线索练习"),
    WEAKNESS("薄弱强化", "优先练习掌握度较低的知识点"),
    MISTAKES("错题重练", "重做错题并生成同类型变式"),
    MIXED("综合练习", "在本册已识别知识点中混合出题"),
}

enum class MathQuestionSource(val label: String) {
    SYSTEM_TEMPLATE("系统模板"),
    TEXTBOOK_VARIANT("教材变式"),
    MISTAKE_VARIANT("错题变式"),
}

enum class MathDifficulty(val label: String, val level: Int) {
    BASIC("基础", 1),
    CONSOLIDATION("巩固", 2),
    IMPROVEMENT("提高", 3),
    CHALLENGE("挑战", 4),
}

enum class MathQuestionQuality {
    VERIFIED,
    DRAFT,
}

data class MathKnowledgePoint(
    val id: String,
    val title: String,
    val description: String,
    val lessonKeywords: List<String>,
)

sealed interface MathAnswerSpec {
    val type: String

    data class Choice(val correctOptionId: String) : MathAnswerSpec {
        override val type: String = "choice"
    }

    data class RationalValue(val value: String) : MathAnswerSpec {
        override val type: String = "rational"
    }

    data class RationalSet(val values: List<String>) : MathAnswerSpec {
        override val type: String = "rational_set"
    }

    data class Ordering(val values: List<String>) : MathAnswerSpec {
        override val type: String = "ordering"
    }

    data class EquivalentExpression(val expression: String) : MathAnswerSpec {
        override val type: String = "equivalent_expression"
    }

    data class LinearEquation(val equation: String, val solution: String) : MathAnswerSpec {
        override val type: String = "linear_equation"
    }

    data class StepSequence(val equation: String, val solution: String) : MathAnswerSpec {
        override val type: String = "step_sequence"
    }

    data class NumberLineValue(val value: String, val tolerance: Double = 0.18) : MathAnswerSpec {
        override val type: String = "number_line"
    }
}

data class MathQuestionOption(
    val id: String,
    val text: String,
)

data class MathQuestion(
    val schemaVersion: Int = MATH_QUESTION_SCHEMA_VERSION,
    val id: String,
    val templateId: String,
    val textbookKey: String,
    val lessonId: String?,
    val knowledgePointId: String,
    val type: MathQuestionType,
    val difficulty: MathDifficulty,
    val source: MathQuestionSource,
    val prompt: String,
    val answerSpec: MathAnswerSpec,
    val canonicalAnswer: String,
    val options: List<MathQuestionOption> = emptyList(),
    val orderingItems: List<String> = emptyList(),
    val hints: List<String> = emptyList(),
    val explanation: String,
    val numberLineMin: Int = -10,
    val numberLineMax: Int = 10,
    val sourcePage: Int? = null,
    val sourceExcerpt: String? = null,
    val quality: MathQuestionQuality = MathQuestionQuality.VERIFIED,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("id", id)
        .put("templateId", templateId)
        .put("textbookKey", textbookKey)
        .put("lessonId", lessonId)
        .put("knowledgePointId", knowledgePointId)
        .put("type", type.name)
        .put("difficulty", difficulty.name)
        .put("source", source.name)
        .put("prompt", prompt)
        .put("answerSpec", answerSpec.toJson())
        .put("canonicalAnswer", canonicalAnswer)
        .put("options", JSONArray().apply {
            options.forEach { option ->
                put(JSONObject().put("id", option.id).put("text", option.text))
            }
        })
        .put("orderingItems", JSONArray(orderingItems))
        .put("hints", JSONArray(hints))
        .put("explanation", explanation)
        .put("numberLineMin", numberLineMin)
        .put("numberLineMax", numberLineMax)
        .put("sourcePage", sourcePage)
        .put("sourceExcerpt", sourceExcerpt)
        .put("quality", quality.name)

    companion object {
        fun fromJson(root: JSONObject): MathQuestion {
            val optionArray = root.optJSONArray("options") ?: JSONArray()
            val options = buildList {
                for (index in 0 until optionArray.length()) {
                    val option = optionArray.getJSONObject(index)
                    add(MathQuestionOption(option.getString("id"), option.getString("text")))
                }
            }
            return MathQuestion(
                schemaVersion = root.optInt("schemaVersion", MATH_QUESTION_SCHEMA_VERSION),
                id = root.getString("id"),
                templateId = root.getString("templateId"),
                textbookKey = root.getString("textbookKey"),
                lessonId = root.optString("lessonId").takeIf { it.isNotBlank() && it != "null" },
                knowledgePointId = root.getString("knowledgePointId"),
                type = runCatching { MathQuestionType.valueOf(root.getString("type")) }
                    .getOrDefault(MathQuestionType.NUMERIC_INPUT),
                difficulty = runCatching { MathDifficulty.valueOf(root.getString("difficulty")) }
                    .getOrDefault(MathDifficulty.BASIC),
                source = runCatching { MathQuestionSource.valueOf(root.getString("source")) }
                    .getOrDefault(MathQuestionSource.SYSTEM_TEMPLATE),
                prompt = root.getString("prompt"),
                answerSpec = answerSpecFromJson(root.getJSONObject("answerSpec")),
                canonicalAnswer = root.getString("canonicalAnswer"),
                options = options,
                orderingItems = root.optJSONArray("orderingItems").toStringList(),
                hints = root.optJSONArray("hints").toStringList(),
                explanation = root.optString("explanation"),
                numberLineMin = root.optInt("numberLineMin", -10),
                numberLineMax = root.optInt("numberLineMax", 10),
                sourcePage = root.optInt("sourcePage", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                sourceExcerpt = root.optString("sourceExcerpt").takeIf { it.isNotBlank() && it != "null" },
                quality = runCatching { MathQuestionQuality.valueOf(root.optString("quality")) }
                    .getOrDefault(MathQuestionQuality.VERIFIED),
            )
        }
    }
}

data class MathAnswerEvaluation(
    val correct: Boolean,
    val canonicalAnswer: String,
    val feedback: String,
    val mistakeType: String?,
    val normalizedAnswer: String,
)

data class MathMasterySnapshot(
    val knowledgePointId: String,
    val title: String,
    val score: Double,
    val attempts: Int,
    val correctStreak: Int,
    val dueAt: Long,
) {
    val percent: Int
        get() = (score.coerceIn(0.0, 1.0) * 100).toInt()
}

data class MathMistakeSnapshot(
    val mistakeKey: String,
    val question: MathQuestion,
    val wrongCount: Int,
    val resolvedStreak: Int,
    val lastAnswer: String,
    val mistakeType: String,
    val dueAt: Long,
)

data class MathSubmissionResult(
    val question: MathQuestion,
    val evaluation: MathAnswerEvaluation,
    val masteryScore: Double,
)

private fun MathAnswerSpec.toJson(): JSONObject = when (this) {
    is MathAnswerSpec.Choice -> JSONObject().put("type", type).put("correctOptionId", correctOptionId)
    is MathAnswerSpec.RationalValue -> JSONObject().put("type", type).put("value", value)
    is MathAnswerSpec.RationalSet -> JSONObject().put("type", type).put("values", JSONArray(values))
    is MathAnswerSpec.Ordering -> JSONObject().put("type", type).put("values", JSONArray(values))
    is MathAnswerSpec.EquivalentExpression -> JSONObject().put("type", type).put("expression", expression)
    is MathAnswerSpec.LinearEquation -> JSONObject()
        .put("type", type)
        .put("equation", equation)
        .put("solution", solution)
    is MathAnswerSpec.StepSequence -> JSONObject()
        .put("type", type)
        .put("equation", equation)
        .put("solution", solution)
    is MathAnswerSpec.NumberLineValue -> JSONObject()
        .put("type", type)
        .put("value", value)
        .put("tolerance", tolerance)
}

private fun answerSpecFromJson(root: JSONObject): MathAnswerSpec = when (root.getString("type")) {
    "choice" -> MathAnswerSpec.Choice(root.getString("correctOptionId"))
    "rational_set" -> MathAnswerSpec.RationalSet(root.getJSONArray("values").toStringList())
    "ordering" -> MathAnswerSpec.Ordering(root.getJSONArray("values").toStringList())
    "equivalent_expression" -> MathAnswerSpec.EquivalentExpression(root.getString("expression"))
    "linear_equation" -> MathAnswerSpec.LinearEquation(
        equation = root.getString("equation"),
        solution = root.getString("solution"),
    )
    "step_sequence" -> MathAnswerSpec.StepSequence(
        equation = root.getString("equation"),
        solution = root.getString("solution"),
    )
    "number_line" -> MathAnswerSpec.NumberLineValue(
        value = root.getString("value"),
        tolerance = root.optDouble("tolerance", 0.18),
    )
    else -> MathAnswerSpec.RationalValue(root.getString("value"))
}

private fun JSONArray?.toStringList(): List<String> = buildList {
    if (this@toStringList == null) return@buildList
    for (index in 0 until length()) {
        optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
    }
}
