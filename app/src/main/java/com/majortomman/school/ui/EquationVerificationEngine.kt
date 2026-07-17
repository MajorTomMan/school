package com.majortomman.school.ui

import kotlin.math.abs

internal data class EquationVerificationResult(
    val original: String,
    val variables: List<String>,
    val substitutedLeft: String? = null,
    val substitutedRight: String? = null,
    val leftValue: Double? = null,
    val rightValue: Double? = null,
    val isCorrect: Boolean? = null,
    val error: String? = null,
)

internal object EquationVerificationEngine {
    private const val EPSILON = 1e-8

    fun variablesOf(input: String): List<String> = runCatching {
        splitEquation(input).flatMap { tokenize(it) }
            .filterIsInstance<Token.Identifier>()
            .map { it.name }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())

    fun verify(input: String, values: Map<String, Double>): EquationVerificationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return EquationVerificationResult(trimmed, emptyList(), error = "请输入公式或方程。")
        }
        if (trimmed.length > 120) {
            return EquationVerificationResult(trimmed, emptyList(), error = "输入内容过长。")
        }

        return runCatching {
            val sides = splitEquation(trimmed)
            val leftTokens = tokenize(sides[0])
            val rightTokens = sides.getOrNull(1)?.let(::tokenize)
            val variables = (leftTokens + (rightTokens ?: emptyList()))
                .filterIsInstance<Token.Identifier>()
                .map { it.name }
                .distinct()
                .sorted()
            val missing = variables.filterNot(values::containsKey)
            if (missing.isNotEmpty()) {
                return EquationVerificationResult(
                    original = trimmed,
                    variables = variables,
                    error = "请填写：${missing.joinToString("、")}。",
                )
            }

            val leftValue = Parser(leftTokens, values).parse()
            val rightValue = rightTokens?.let { Parser(it, values).parse() }
            EquationVerificationResult(
                original = trimmed,
                variables = variables,
                substitutedLeft = renderSubstitution(leftTokens, values),
                substitutedRight = rightTokens?.let { renderSubstitution(it, values) },
                leftValue = leftValue,
                rightValue = rightValue,
                isCorrect = rightValue?.let { nearlyEqual(leftValue, it) },
            )
        }.getOrElse { throwable ->
            EquationVerificationResult(
                original = trimmed,
                variables = variablesOf(trimmed),
                error = throwable.message ?: "无法识别这个式子。",
            )
        }
    }

    private fun splitEquation(input: String): List<String> {
        val normalized = input
            .replace('＝', '=')
            .replace('×', '*')
            .replace('·', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace('—', '-')
        val count = normalized.count { it == '=' }
        require(count <= 1) { "一个验证式中只能出现一个等号。" }
        val sides = normalized.split('=')
        require(sides.all { it.isNotBlank() }) { "等号两边都需要有内容。" }
        return sides
    }

    private fun tokenize(source: String): List<Token> {
        val raw = mutableListOf<Token>()
        var index = 0
        while (index < source.length) {
            val char = source[index]
            when {
                char.isWhitespace() -> index++
                char.isDigit() || char == '.' -> {
                    val start = index
                    var dotCount = 0
                    while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                        if (source[index] == '.') dotCount++
                        index++
                    }
                    require(dotCount <= 1) { "数字格式不正确。" }
                    val text = source.substring(start, index)
                    val value = text.toDoubleOrNull() ?: error("数字格式不正确。")
                    raw += Token.Number(value, text)
                }
                char.isLetter() || char == '_' -> {
                    val start = index
                    index++
                    while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
                    raw += Token.Identifier(source.substring(start, index))
                }
                char == '+' -> { raw += Token.Plus; index++ }
                char == '-' -> { raw += Token.Minus; index++ }
                char == '*' -> { raw += Token.Multiply; index++ }
                char == '/' -> { raw += Token.Divide; index++ }
                char == '(' -> { raw += Token.LeftParen; index++ }
                char == ')' -> { raw += Token.RightParen; index++ }
                else -> error("暂不支持字符“$char”。")
            }
        }

        val expanded = mutableListOf<Token>()
        raw.forEachIndexed { tokenIndex, token ->
            val previous = expanded.lastOrNull()
            if (previous != null && canEndValue(previous) && canStartValue(token)) {
                expanded += Token.Multiply
            }
            expanded += token
            if (tokenIndex > 200) error("式子过于复杂。")
        }
        expanded += Token.End
        return expanded
    }

    private fun canEndValue(token: Token): Boolean =
        token is Token.Number || token is Token.Identifier || token == Token.RightParen

    private fun canStartValue(token: Token): Boolean =
        token is Token.Number || token is Token.Identifier || token == Token.LeftParen

    private fun renderSubstitution(tokens: List<Token>, values: Map<String, Double>): String = buildString {
        tokens.takeWhile { it != Token.End }.forEach { token ->
            when (token) {
                is Token.Number -> append(token.text)
                is Token.Identifier -> append('(').append(formatEquationNumber(values.getValue(token.name))).append(')')
                Token.Plus -> append(" + ")
                Token.Minus -> append(" - ")
                Token.Multiply -> append(" × ")
                Token.Divide -> append(" ÷ ")
                Token.LeftParen -> append('(')
                Token.RightParen -> append(')')
                Token.End -> Unit
            }
        }
    }

    private fun nearlyEqual(left: Double, right: Double): Boolean {
        val scale = maxOf(1.0, abs(left), abs(right))
        return abs(left - right) <= EPSILON * scale
    }

    private class Parser(
        private val tokens: List<Token>,
        private val values: Map<String, Double>,
    ) {
        private var index = 0

        fun parse(): Double {
            val value = parseExpression()
            require(peek() == Token.End) { "式子中有无法识别的部分。" }
            require(value.isFinite()) { "计算结果不是有限数值。" }
            return value
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                value = when (peek()) {
                    Token.Plus -> { index++; value + parseTerm() }
                    Token.Minus -> { index++; value - parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseUnary()
            while (true) {
                value = when (peek()) {
                    Token.Multiply -> { index++; value * parseUnary() }
                    Token.Divide -> {
                        index++
                        val divisor = parseUnary()
                        require(abs(divisor) > EPSILON) { "不能除以 0。" }
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseUnary(): Double = when (peek()) {
            Token.Plus -> { index++; parseUnary() }
            Token.Minus -> { index++; -parseUnary() }
            else -> parsePrimary()
        }

        private fun parsePrimary(): Double = when (val token = peek()) {
            is Token.Number -> { index++; token.value }
            is Token.Identifier -> {
                index++
                values[token.name] ?: error("请填写变量 ${token.name}。")
            }
            Token.LeftParen -> {
                index++
                val value = parseExpression()
                require(peek() == Token.RightParen) { "括号没有成对出现。" }
                index++
                value
            }
            else -> error("这里需要一个数字、变量或括号。")
        }

        private fun peek(): Token = tokens.getOrElse(index) { Token.End }
    }

    private sealed interface Token {
        data class Number(val value: Double, val text: String) : Token
        data class Identifier(val name: String) : Token
        data object Plus : Token
        data object Minus : Token
        data object Multiply : Token
        data object Divide : Token
        data object LeftParen : Token
        data object RightParen : Token
        data object End : Token
    }
}

internal fun formatEquationNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
    return if (abs(rounded - rounded.toLong()) < 1e-9) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
