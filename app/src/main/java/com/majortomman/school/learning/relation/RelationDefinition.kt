package com.majortomman.school.learning.relation

import kotlin.math.abs

data class VariableDefinition(
    val id: String,
    val label: String,
    val defaultValue: Double = 0.0,
    val unit: String? = null,
)

data class SolveRule(
    val targetId: String,
    val inputIds: List<String>,
    val evaluate: (Map<String, Double>) -> Double,
)

data class RelationDefinition(
    val id: String,
    val displayFormula: String,
    val variables: List<VariableDefinition>,
    val solveRules: Map<String, SolveRule>,
) {
    init {
        val variableIds = variables.map { it.id }.toSet()
        require(variableIds.size == variables.size) { "变量 ID 不能重复。" }
        solveRules.forEach { (targetId, rule) ->
            require(targetId == rule.targetId) { "求解规则的目标量不一致。" }
            require(targetId in variableIds) { "目标量 $targetId 不在关系中。" }
            require(rule.inputIds.all { it in variableIds }) { "求解规则包含未知变量。" }
            require(targetId !in rule.inputIds) { "目标量不能同时作为已知量。" }
        }
    }

    fun requiredInputs(targetId: String): List<VariableDefinition> {
        val rule = solveRules[targetId] ?: error("暂不支持求 $targetId。")
        return rule.inputIds.map { inputId ->
            variables.first { it.id == inputId }
        }
    }

    fun solve(targetId: String, knownValues: Map<String, Double>): RelationSolveResult {
        val rule = solveRules[targetId]
            ?: return RelationSolveResult.Error("暂不支持求 $targetId。")
        val missing = rule.inputIds.filterNot(knownValues::containsKey)
        if (missing.isNotEmpty()) {
            return RelationSolveResult.Error("请填写：${missing.joinToString("、")}。")
        }
        return runCatching {
            val value = rule.evaluate(knownValues)
            require(value.isFinite()) { "计算结果不是有限数值。" }
            RelationSolveResult.Success(targetId, value)
        }.getOrElse { throwable ->
            RelationSolveResult.Error(throwable.message ?: "无法完成计算。")
        }
    }

    fun verify(values: Map<String, Double>, tolerance: Double = 1e-8): RelationVerificationResult {
        val missing = variables.map { it.id }.filterNot(values::containsKey)
        if (missing.isNotEmpty()) {
            return RelationVerificationResult(false, "请填写：${missing.joinToString("、")}。")
        }
        val firstRule = solveRules.values.firstOrNull()
            ?: return RelationVerificationResult(false, "当前关系没有验证规则。")
        val expected = runCatching { firstRule.evaluate(values) }.getOrElse {
            return RelationVerificationResult(false, it.message ?: "无法验证。")
        }
        val actual = values.getValue(firstRule.targetId)
        val scale = maxOf(1.0, abs(expected), abs(actual))
        val correct = abs(expected - actual) <= tolerance * scale
        return RelationVerificationResult(
            correct = correct,
            message = if (correct) "验证成立。" else "${firstRule.targetId} 应为 ${formatRelationNumber(expected)}。",
        )
    }
}

sealed interface RelationSolveResult {
    data class Success(val targetId: String, val value: Double) : RelationSolveResult
    data class Error(val message: String) : RelationSolveResult
}

data class RelationVerificationResult(
    val correct: Boolean,
    val message: String,
)

fun formatRelationNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
    return if (abs(rounded - rounded.toLong()) < 1e-9) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
