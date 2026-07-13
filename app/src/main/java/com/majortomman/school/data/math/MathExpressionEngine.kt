package com.majortomman.school.data.math

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.abs

object MathExpressionEngine {
    fun evaluate(question: MathQuestion, rawAnswer: String): MathAnswerEvaluation {
        val answer = rawAnswer.trim()
        if (answer.isBlank()) {
            return MathAnswerEvaluation(
                correct = false,
                canonicalAnswer = question.canonicalAnswer,
                feedback = "还没有作答。先写下你能确定的第一步。",
                mistakeType = "未作答",
                normalizedAnswer = "",
            )
        }

        val result = runCatching {
            when (val spec = question.answerSpec) {
                is MathAnswerSpec.Choice -> evaluateChoice(spec, answer)
                is MathAnswerSpec.RationalValue -> evaluateRational(spec, answer)
                is MathAnswerSpec.RationalSet -> evaluateRationalSet(spec, answer)
                is MathAnswerSpec.Ordering -> evaluateOrdering(spec, answer)
                is MathAnswerSpec.EquivalentExpression -> evaluateExpression(spec, answer)
                is MathAnswerSpec.LinearEquation -> evaluateLinearEquation(spec, answer)
                is MathAnswerSpec.StepSequence -> evaluateSteps(spec, answer)
                is MathAnswerSpec.NumberLineValue -> evaluateNumberLine(spec, answer)
            }
        }.getOrElse { error ->
            InternalEvaluation(
                correct = false,
                normalized = answer,
                mistakeType = "表达不规范",
                detail = error.message ?: "无法识别这个答案格式",
            )
        }

        val feedback = if (result.correct) {
            when (question.type) {
                MathQuestionType.STEP_BY_STEP -> "每一步都保持了等式的解，结果也正确。"
                MathQuestionType.EXPRESSION_INPUT -> "这个表达式与标准形式等价。"
                MathQuestionType.ORDERING -> "顺序正确，比较依据成立。"
                MathQuestionType.NUMBER_LINE_POINT -> "位置正确。数轴上的坐标与目标数一致。"
                else -> "回答正确。${question.explanation}"
            }
        } else {
            buildString {
                append(result.detail)
                if (question.hints.isNotEmpty()) {
                    append(" ")
                    append(question.hints.first())
                }
            }.trim()
        }

        return MathAnswerEvaluation(
            correct = result.correct,
            canonicalAnswer = question.canonicalAnswer,
            feedback = feedback,
            mistakeType = result.mistakeType,
            normalizedAnswer = result.normalized,
        )
    }

    fun equivalentExpressions(left: String, right: String): Boolean = runCatching {
        val leftExpression = ExpressionParser(normalizeExpression(left)).parse()
        val rightExpression = ExpressionParser(normalizeExpression(right)).parse()
        var compared = 0
        for (sample in listOf(-5, -3, -1, 0, 2, 4, 7)) {
            val x = Rational.of(sample.toLong())
            val leftValue = runCatching { leftExpression.eval(x) }.getOrNull() ?: continue
            val rightValue = runCatching { rightExpression.eval(x) }.getOrNull() ?: continue
            compared += 1
            if (leftValue != rightValue) return false
        }
        compared >= 3
    }.getOrDefault(false)

    fun linearEquationSolution(equation: String): String? = runCatching {
        val parts = normalizeExpression(equation).split('=')
        require(parts.size == 2) { "方程必须只包含一个等号" }
        val left = ExpressionParser(parts[0]).parse()
        val right = ExpressionParser(parts[1]).parse()
        fun difference(x: Rational): Rational = left.eval(x) - right.eval(x)
        val f0 = difference(Rational.ZERO)
        val f1 = difference(Rational.ONE)
        val f2 = difference(Rational.of(2))
        val coefficient = f1 - f0
        require((f2 - f1) == coefficient) { "当前只支持一元一次方程" }
        require(!coefficient.isZero) { "方程没有唯一解" }
        ((-f0) / coefficient).toCanonicalString()
    }.getOrNull()

    private fun evaluateChoice(spec: MathAnswerSpec.Choice, answer: String): InternalEvaluation {
        val normalized = answer.trim()
        val correct = normalized.equals(spec.correctOptionId, ignoreCase = true)
        return InternalEvaluation(
            correct = correct,
            normalized = normalized,
            mistakeType = if (correct) null else "概念不理解",
            detail = if (correct) "" else "这个选项不符合题目中的定义或关系。",
        )
    }

    private fun evaluateRational(spec: MathAnswerSpec.RationalValue, answer: String): InternalEvaluation {
        val expected = Rational.parse(spec.value)
        val actual = parseFirstRational(answer)
        val correct = actual == expected
        val mistakeType = when {
            correct -> null
            actual == -expected -> "符号错误"
            else -> "计算错误"
        }
        return InternalEvaluation(
            correct = correct,
            normalized = actual.toCanonicalString(),
            mistakeType = mistakeType,
            detail = when (mistakeType) {
                "符号错误" -> "数值大小接近，但正负号反了。先确认方向或移项后的符号。"
                else -> "数值还不正确。先把每一步运算写开，再约分或化简。"
            },
        )
    }

    private fun evaluateRationalSet(spec: MathAnswerSpec.RationalSet, answer: String): InternalEvaluation {
        val expected = spec.values.map(Rational::parse).toSet()
        val actual = extractRationals(answer).toSet()
        val correct = actual == expected
        val missing = expected - actual
        return InternalEvaluation(
            correct = correct,
            normalized = actual.sorted().joinToString(",") { it.toCanonicalString() },
            mistakeType = when {
                correct -> null
                actual.isNotEmpty() && actual.all { it in expected } && missing.isNotEmpty() -> "步骤遗漏"
                actual.map { -it }.toSet() == expected -> "符号错误"
                else -> "计算错误"
            },
            detail = when {
                actual.isEmpty() -> "没有识别到数值答案。可以写成 x=3 或 x=-3。"
                missing.isNotEmpty() && actual.all { it in expected } -> "已有解是正确的，但还漏掉了另一种可能。"
                else -> "解集不完整或包含错误的值。把每个候选值代回原式检查。"
            },
        )
    }

    private fun evaluateOrdering(spec: MathAnswerSpec.Ordering, answer: String): InternalEvaluation {
        val expected = spec.values.map(Rational::parse)
        val actual = extractOrderedRationals(answer)
        val correct = actual == expected
        return InternalEvaluation(
            correct = correct,
            normalized = actual.joinToString("<") { it.toCanonicalString() },
            mistakeType = when {
                correct -> null
                actual == expected.reversed() -> "顺序错误"
                else -> "比较错误"
            },
            detail = if (actual == expected.reversed()) {
                "你把从小到大的顺序写反了。"
            } else {
                "排序还不正确。可以把这些数放到同一条数轴上比较位置。"
            },
        )
    }

    private fun evaluateExpression(
        spec: MathAnswerSpec.EquivalentExpression,
        answer: String,
    ): InternalEvaluation {
        val correct = equivalentExpressions(spec.expression, answer)
        return InternalEvaluation(
            correct = correct,
            normalized = normalizeExpression(answer),
            mistakeType = if (correct) null else "等价变形错误",
            detail = if (correct) "" else "这个式子与原式并不总是相等。检查括号、分配律和同类项。",
        )
    }

    private fun evaluateLinearEquation(
        spec: MathAnswerSpec.LinearEquation,
        answer: String,
    ): InternalEvaluation {
        val expected = Rational.parse(spec.solution)
        val actual = extractRationals(answer).lastOrNull()
            ?: throw IllegalArgumentException("没有识别到方程的解")
        val correct = actual == expected
        return InternalEvaluation(
            correct = correct,
            normalized = "x=${actual.toCanonicalString()}",
            mistakeType = when {
                correct -> null
                actual == -expected -> "符号错误"
                else -> "方程计算错误"
            },
            detail = if (actual == -expected) {
                "解的符号反了。检查移项或两边同除时的符号。"
            } else {
                "把这个值代回原方程后，两边并不相等。"
            },
        )
    }

    private fun evaluateSteps(spec: MathAnswerSpec.StepSequence, answer: String): InternalEvaluation {
        val expected = Rational.parse(spec.solution)
        val lines = answer.lines()
            .map(String::trim)
            .filter(String::isNotEmpty)
        require(lines.isNotEmpty()) { "请至少写出一步" }

        val originalSolution = Rational.parse(
            linearEquationSolution(spec.equation)
                ?: throw IllegalArgumentException("标准方程不是可判定的一元一次方程"),
        )
        require(originalSolution == expected) { "标准答案配置错误" }

        var checkedEquations = 0
        for ((index, line) in lines.withIndex()) {
            if ('=' !in line) continue
            val solution = linearEquationSolution(line)
            if (solution != null) {
                checkedEquations += 1
                if (Rational.parse(solution) != expected) {
                    return InternalEvaluation(
                        correct = false,
                        normalized = lines.joinToString("\n"),
                        mistakeType = "等式变形错误",
                        detail = "第 ${index + 1} 步改变了原方程的解。等式两边必须进行相同的有效操作。",
                    )
                }
            }
        }

        val finalValues = extractRationals(lines.last())
        val finalCorrect = finalValues.lastOrNull() == expected
        val correct = checkedEquations > 0 && finalCorrect
        return InternalEvaluation(
            correct = correct,
            normalized = lines.joinToString("\n"),
            mistakeType = when {
                correct -> null
                checkedEquations == 0 -> "步骤表达不完整"
                else -> "最终答案错误"
            },
            detail = when {
                checkedEquations == 0 -> "没有识别到带等号的推导步骤。每一步单独写一行。"
                else -> "前面的等价变形可以继续，但最后需要写出正确的 x 值。"
            },
        )
    }

    private fun evaluateNumberLine(
        spec: MathAnswerSpec.NumberLineValue,
        answer: String,
    ): InternalEvaluation {
        val expected = Rational.parse(spec.value).toDouble()
        val actual = answer.toDoubleOrNull() ?: parseFirstRational(answer).toDouble()
        val correct = abs(actual - expected) <= spec.tolerance
        return InternalEvaluation(
            correct = correct,
            normalized = actual.toString(),
            mistakeType = if (correct) null else "图形定位错误",
            detail = if (correct) "" else "点的位置与目标数不一致。先找到原点，再按单位长度移动。",
        )
    }

    private fun parseFirstRational(text: String): Rational =
        extractRationals(text).firstOrNull()
            ?: throw IllegalArgumentException("没有识别到数值")

    private fun extractOrderedRationals(text: String): List<Rational> {
        val normalized = text
            .replace('，', ',')
            .replace('、', ',')
            .replace('；', ',')
            .replace("≤", "<")
            .replace("≥", ">")
        return extractRationals(normalized)
    }

    private fun extractRationals(text: String): List<Rational> {
        val normalized = normalizeExpression(text)
            .replace("x=", " ", ignoreCase = true)
            .replace("或", ",")
            .replace("and", ",", ignoreCase = true)
        val regex = Regex("[-+]?\\d+(?:\\.\\d+)?(?:/[-+]?\\d+(?:\\.\\d+)?)?")
        return regex.findAll(normalized).map { match -> Rational.parse(match.value) }.toList()
    }

    private fun normalizeExpression(raw: String): String {
        var text = raw
            .trim()
            .replace('−', '-')
            .replace('—', '-')
            .replace('×', '*')
            .replace('·', '*')
            .replace('÷', '/')
            .replace('（', '(')
            .replace('）', ')')
            .replace(" ", "")
            .lowercase()
        text = text.replace(Regex("(?<=[0-9x)])(?=[x(])"), "*")
        text = text.replace(Regex("(?<=\\))(?=[0-9x(])"), "*")
        return text
    }

    private data class InternalEvaluation(
        val correct: Boolean,
        val normalized: String,
        val mistakeType: String?,
        val detail: String,
    )
}

internal data class Rational private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger,
) : Comparable<Rational> {
    init {
        require(denominator != BigInteger.ZERO) { "分母不能为 0" }
    }

    val isZero: Boolean
        get() = numerator == BigInteger.ZERO

    operator fun plus(other: Rational): Rational = of(
        numerator * other.denominator + other.numerator * denominator,
        denominator * other.denominator,
    )

    operator fun minus(other: Rational): Rational = of(
        numerator * other.denominator - other.numerator * denominator,
        denominator * other.denominator,
    )

    operator fun times(other: Rational): Rational = of(
        numerator * other.numerator,
        denominator * other.denominator,
    )

    operator fun div(other: Rational): Rational {
        require(!other.isZero) { "不能除以 0" }
        return of(numerator * other.denominator, denominator * other.numerator)
    }

    operator fun unaryMinus(): Rational = of(-numerator, denominator)

    override fun compareTo(other: Rational): Int =
        (numerator * other.denominator).compareTo(other.numerator * denominator)

    fun toCanonicalString(): String = if (denominator == BigInteger.ONE) {
        numerator.toString()
    } else {
        "$numerator/$denominator"
    }

    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()

    companion object {
        val ZERO: Rational = of(0)
        val ONE: Rational = of(1)

        fun of(value: Long): Rational = Rational(BigInteger.valueOf(value), BigInteger.ONE)

        fun of(numerator: BigInteger, denominator: BigInteger): Rational {
            require(denominator != BigInteger.ZERO) { "分母不能为 0" }
            if (numerator == BigInteger.ZERO) return Rational(BigInteger.ZERO, BigInteger.ONE)
            val sign = if (denominator.signum() < 0) BigInteger.valueOf(-1) else BigInteger.ONE
            val signedNumerator = numerator * sign
            val positiveDenominator = denominator * sign
            val gcd = signedNumerator.abs().gcd(positiveDenominator)
            return Rational(signedNumerator / gcd, positiveDenominator / gcd)
        }

        fun parse(raw: String): Rational {
            val text = raw.trim()
                .replace('−', '-')
                .replace(" ", "")
            if ('/' in text) {
                val parts = text.split('/')
                require(parts.size == 2) { "无效分数：$raw" }
                return parse(parts[0]) / parse(parts[1])
            }
            if ('.' in text) {
                val decimal = BigDecimal(text).stripTrailingZeros()
                val scale = decimal.scale().coerceAtLeast(0)
                val denominator = BigInteger.TEN.pow(scale)
                return of(decimal.movePointRight(scale).toBigIntegerExact(), denominator)
            }
            return of(text.toLong())
        }
    }
}

private sealed interface ExpressionNode {
    fun eval(x: Rational): Rational

    data class Constant(val value: Rational) : ExpressionNode {
        override fun eval(x: Rational): Rational = value
    }

    data object Variable : ExpressionNode {
        override fun eval(x: Rational): Rational = x
    }

    data class UnaryMinus(val value: ExpressionNode) : ExpressionNode {
        override fun eval(x: Rational): Rational = -value.eval(x)
    }

    data class Binary(
        val left: ExpressionNode,
        val operator: Char,
        val right: ExpressionNode,
    ) : ExpressionNode {
        override fun eval(x: Rational): Rational = when (operator) {
            '+' -> left.eval(x) + right.eval(x)
            '-' -> left.eval(x) - right.eval(x)
            '*' -> left.eval(x) * right.eval(x)
            '/' -> left.eval(x) / right.eval(x)
            else -> error("不支持的运算符")
        }
    }
}

private class ExpressionParser(
    private val text: String,
) {
    private var position = 0

    fun parse(): ExpressionNode {
        val node = parseExpression()
        require(position == text.length) { "无法识别：${text.drop(position)}" }
        return node
    }

    private fun parseExpression(): ExpressionNode {
        var node = parseTerm()
        while (position < text.length && text[position] in "+-") {
            val operator = text[position++]
            node = ExpressionNode.Binary(node, operator, parseTerm())
        }
        return node
    }

    private fun parseTerm(): ExpressionNode {
        var node = parseFactor()
        while (position < text.length && text[position] in "*/") {
            val operator = text[position++]
            node = ExpressionNode.Binary(node, operator, parseFactor())
        }
        return node
    }

    private fun parseFactor(): ExpressionNode {
        if (position < text.length && text[position] == '+') {
            position += 1
            return parseFactor()
        }
        if (position < text.length && text[position] == '-') {
            position += 1
            return ExpressionNode.UnaryMinus(parseFactor())
        }
        return parsePrimary()
    }

    private fun parsePrimary(): ExpressionNode {
        require(position < text.length) { "表达式不完整" }
        if (text[position] == '(') {
            position += 1
            val node = parseExpression()
            require(position < text.length && text[position] == ')') { "缺少右括号" }
            position += 1
            return node
        }
        if (text[position] == 'x') {
            position += 1
            return ExpressionNode.Variable
        }
        val start = position
        while (position < text.length && (text[position].isDigit() || text[position] == '.')) {
            position += 1
        }
        require(position > start) { "无法识别字符 ${text[position]}" }
        return ExpressionNode.Constant(Rational.parse(text.substring(start, position)))
    }
}
