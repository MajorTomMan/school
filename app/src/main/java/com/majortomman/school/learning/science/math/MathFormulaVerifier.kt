package com.majortomman.school.learning.science.math

import com.majortomman.school.learning.science.expression.ComplexApprox
import com.majortomman.school.learning.science.expression.ScienceExpression
import com.majortomman.school.learning.science.expression.ScienceExpressionEvaluator
import com.majortomman.school.learning.science.expression.ScienceExpressionParser
import com.majortomman.school.learning.science.expression.ScienceExpressionRenderer
import com.majortomman.school.learning.science.expression.ScienceExpressionSimplifier
import com.majortomman.school.learning.science.expression.ScienceNumberDomain
import kotlin.math.abs

enum class MathRelationOperator(val symbol: String) {
    EQUAL("="),
    LESS("<"),
    LESS_OR_EQUAL("≤"),
    GREATER(">"),
    GREATER_OR_EQUAL("≥"),
}

enum class MathFormulaStatus {
    VALID_EXPRESSION,
    NEEDS_VARIABLE_VALUES,
    TRUE_AT_VALUES,
    FALSE_AT_VALUES,
    SAMPLE_MATCH,
    SAMPLE_COUNTEREXAMPLE,
    UNSUPPORTED,
}

data class MathFormulaSample(
    val variables: Map<String, Double>,
    val left: ComplexApprox,
    val right: ComplexApprox,
    val matches: Boolean,
)

data class MathFormulaVerificationResult(
    val original: String,
    val variables: List<String>,
    val normalizedLeft: String? = null,
    val normalizedRight: String? = null,
    val relation: MathRelationOperator? = null,
    val leftValue: ComplexApprox? = null,
    val rightValue: ComplexApprox? = null,
    val status: MathFormulaStatus,
    val message: String,
    val samples: List<MathFormulaSample> = emptyList(),
    val error: String? = null,
)

object MathFormulaVerifier {
    private const val MAX_INPUT_LENGTH = 256
    private const val EPSILON = 1e-8
    private val sampleValues = listOf(-3.0, -1.0, -0.5, 0.5, 1.0, 2.0, 3.0)

    fun verify(
        input: String,
        values: Map<String, Double> = emptyMap(),
        domain: ScienceNumberDomain = ScienceNumberDomain.REAL,
        sampleRelation: Boolean = false,
    ): MathFormulaVerificationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return unsupported(trimmed, "请输入数学表达式、等式或不等式。")
        if (trimmed.length > MAX_INPUT_LENGTH) return unsupported(trimmed, "输入内容超过 $MAX_INPUT_LENGTH 个字符。")

        return runCatching {
            val parsed = splitRelation(trimmed)
            val left = ScienceExpressionParser.parse(parsed.left)
            val right = parsed.right?.let(ScienceExpressionParser::parse)
            val variables = (variablesOf(left) + right?.let(::variablesOf).orEmpty()).sorted()
            val normalizedLeft = ScienceExpressionRenderer.render(ScienceExpressionSimplifier.simplify(left, domain))
            val normalizedRight = right?.let {
                ScienceExpressionRenderer.render(ScienceExpressionSimplifier.simplify(it, domain))
            }

            if (right == null) {
                return verifyExpression(trimmed, left, variables, normalizedLeft, values)
            }
            if (sampleRelation && variables.isNotEmpty()) {
                return verifyBySamples(
                    original = trimmed,
                    left = left,
                    right = right,
                    relation = requireNotNull(parsed.operator),
                    variables = variables,
                    normalizedLeft = normalizedLeft,
                    normalizedRight = requireNotNull(normalizedRight),
                )
            }
            verifyAtValues(
                original = trimmed,
                left = left,
                right = right,
                relation = requireNotNull(parsed.operator),
                variables = variables,
                normalizedLeft = normalizedLeft,
                normalizedRight = requireNotNull(normalizedRight),
                values = values,
            )
        }.getOrElse { error -> unsupported(trimmed, error.message ?: "无法识别这个数学式子。") }
    }

    private fun verifyExpression(
        original: String,
        expression: ScienceExpression,
        variables: List<String>,
        normalized: String,
        values: Map<String, Double>,
    ): MathFormulaVerificationResult {
        val missing = variables.filterNot(values::containsKey)
        if (missing.isNotEmpty()) {
            return MathFormulaVerificationResult(
                original = original,
                variables = variables,
                normalizedLeft = normalized,
                status = MathFormulaStatus.NEEDS_VARIABLE_VALUES,
                message = "表达式结构有效；填写 ${missing.joinToString("、")} 后可以计算数值。",
            )
        }
        return MathFormulaVerificationResult(
            original = original,
            variables = variables,
            normalizedLeft = normalized,
            leftValue = ScienceExpressionEvaluator.evaluate(expression, values),
            status = MathFormulaStatus.VALID_EXPRESSION,
            message = "表达式结构有效，已完成化简和代入计算。",
        )
    }

    private fun verifyAtValues(
        original: String,
        left: ScienceExpression,
        right: ScienceExpression,
        relation: MathRelationOperator,
        variables: List<String>,
        normalizedLeft: String,
        normalizedRight: String,
        values: Map<String, Double>,
    ): MathFormulaVerificationResult {
        val missing = variables.filterNot(values::containsKey)
        if (missing.isNotEmpty()) {
            return MathFormulaVerificationResult(
                original = original,
                variables = variables,
                normalizedLeft = normalizedLeft,
                normalizedRight = normalizedRight,
                relation = relation,
                status = MathFormulaStatus.NEEDS_VARIABLE_VALUES,
                message = "关系式结构有效；填写 ${missing.joinToString("、")} 后验证当前取值。",
            )
        }
        val leftValue = ScienceExpressionEvaluator.evaluate(left, values)
        val rightValue = ScienceExpressionEvaluator.evaluate(right, values)
        val matches = compare(leftValue, rightValue, relation)
        return MathFormulaVerificationResult(
            original = original,
            variables = variables,
            normalizedLeft = normalizedLeft,
            normalizedRight = normalizedRight,
            relation = relation,
            leftValue = leftValue,
            rightValue = rightValue,
            status = if (matches) MathFormulaStatus.TRUE_AT_VALUES else MathFormulaStatus.FALSE_AT_VALUES,
            message = if (matches) {
                "在当前变量取值下，关系成立。"
            } else {
                "在当前变量取值下，关系不成立；这已经构成当前关系的一个反例。"
            },
        )
    }

    private fun verifyBySamples(
        original: String,
        left: ScienceExpression,
        right: ScienceExpression,
        relation: MathRelationOperator,
        variables: List<String>,
        normalizedLeft: String,
        normalizedRight: String,
    ): MathFormulaVerificationResult {
        if (variables.size > 3) {
            return MathFormulaVerificationResult(
                original = original,
                variables = variables,
                normalizedLeft = normalizedLeft,
                normalizedRight = normalizedRight,
                relation = relation,
                status = MathFormulaStatus.UNSUPPORTED,
                message = "样本一致性检查最多支持 3 个变量；可以改为填写具体变量值。",
                error = "变量数量超过样本检查上限。",
            )
        }
        val samples = sampleAssignments(variables)
            .take(49)
            .mapNotNull { assignment ->
                runCatching {
                    val leftValue = ScienceExpressionEvaluator.evaluate(left, assignment)
                    val rightValue = ScienceExpressionEvaluator.evaluate(right, assignment)
                    MathFormulaSample(
                        variables = assignment,
                        left = leftValue,
                        right = rightValue,
                        matches = compare(leftValue, rightValue, relation),
                    )
                }.getOrNull()
            }
            .toList()
        if (samples.isEmpty()) {
            return MathFormulaVerificationResult(
                original = original,
                variables = variables,
                normalizedLeft = normalizedLeft,
                normalizedRight = normalizedRight,
                relation = relation,
                status = MathFormulaStatus.UNSUPPORTED,
                message = "选取的安全样本都不在表达式定义域内，请填写具体变量值。",
                error = "没有可用样本。",
            )
        }
        val counterexample = samples.firstOrNull { !it.matches }
        return MathFormulaVerificationResult(
            original = original,
            variables = variables,
            normalizedLeft = normalizedLeft,
            normalizedRight = normalizedRight,
            relation = relation,
            status = if (counterexample == null) MathFormulaStatus.SAMPLE_MATCH else MathFormulaStatus.SAMPLE_COUNTEREXAMPLE,
            message = if (counterexample == null) {
                "已检查 ${samples.size} 组安全样本，暂未发现反例；这只是样本一致性检查，不是严格恒等证明。"
            } else {
                "找到反例 ${counterexample.variables.entries.joinToString { "${it.key}=${format(it.value)}" }}，关系并非对所有这些取值成立。"
            },
            samples = samples,
        )
    }

    private fun splitRelation(input: String): ParsedRelation {
        val normalized = input
            .replace("π", "pi")
            .replace("<=", "≤")
            .replace(">=", "≥")
            .replace('＝', '=')
        val candidates = listOf(
            MathRelationOperator.LESS_OR_EQUAL,
            MathRelationOperator.GREATER_OR_EQUAL,
            MathRelationOperator.EQUAL,
            MathRelationOperator.LESS,
            MathRelationOperator.GREATER,
        )
        val matches = candidates.flatMap { operator ->
            normalized.indices.filter { index -> normalized.startsWith(operator.symbol, index) }
                .map { operator to it }
        }.sortedBy { it.second }
        require(matches.size <= 1) { "一个验证式中只能出现一个关系符号。" }
        if (matches.isEmpty()) return ParsedRelation(normalized, null, null)
        val (operator, index) = matches.single()
        val left = normalized.substring(0, index).trim()
        val right = normalized.substring(index + operator.symbol.length).trim()
        require(left.isNotEmpty() && right.isNotEmpty()) { "关系符号两边都需要有表达式。" }
        return ParsedRelation(left, right, operator)
    }

    private fun variablesOf(expression: ScienceExpression): Set<String> = when (expression) {
        is ScienceExpression.Variable -> setOf(expression.name)
        is ScienceExpression.Sum -> expression.terms.flatMapTo(linkedSetOf(), ::variablesOf)
        is ScienceExpression.Product -> expression.factors.flatMapTo(linkedSetOf(), ::variablesOf)
        is ScienceExpression.Quotient -> variablesOf(expression.numerator) + variablesOf(expression.denominator)
        is ScienceExpression.Power -> variablesOf(expression.base)
        is ScienceExpression.Radical -> variablesOf(expression.radicand)
        else -> emptySet()
    }

    private fun compare(left: ComplexApprox, right: ComplexApprox, operator: MathRelationOperator): Boolean {
        if (operator != MathRelationOperator.EQUAL) {
            require(abs(left.imaginary) < EPSILON && abs(right.imaginary) < EPSILON) { "复数不能直接比较大小。" }
        }
        return when (operator) {
            MathRelationOperator.EQUAL -> nearlyEqual(left.real, right.real) && nearlyEqual(left.imaginary, right.imaginary)
            MathRelationOperator.LESS -> left.real < right.real && !nearlyEqual(left.real, right.real)
            MathRelationOperator.LESS_OR_EQUAL -> left.real < right.real || nearlyEqual(left.real, right.real)
            MathRelationOperator.GREATER -> left.real > right.real && !nearlyEqual(left.real, right.real)
            MathRelationOperator.GREATER_OR_EQUAL -> left.real > right.real || nearlyEqual(left.real, right.real)
        }
    }

    private fun sampleAssignments(variables: List<String>): Sequence<Map<String, Double>> = sequence {
        if (variables.isEmpty()) {
            yield(emptyMap())
            return@sequence
        }
        suspend fun SequenceScope<Map<String, Double>>.walk(index: Int, values: MutableMap<String, Double>) {
            if (index == variables.size) {
                yield(values.toMap())
                return
            }
            sampleValues.forEach { value ->
                values[variables[index]] = value
                walk(index + 1, values)
            }
            values.remove(variables[index])
        }
        walk(0, linkedMapOf())
    }

    private fun nearlyEqual(left: Double, right: Double): Boolean {
        val scale = maxOf(1.0, abs(left), abs(right))
        return abs(left - right) <= EPSILON * scale
    }

    private fun format(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun unsupported(input: String, message: String) = MathFormulaVerificationResult(
        original = input,
        variables = emptyList(),
        status = MathFormulaStatus.UNSUPPORTED,
        message = message,
        error = message,
    )

    private data class ParsedRelation(
        val left: String,
        val right: String?,
        val operator: MathRelationOperator?,
    )
}
