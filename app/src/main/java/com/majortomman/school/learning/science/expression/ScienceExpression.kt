package com.majortomman.school.learning.science.expression

import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class ScienceNumberDomain {
    REAL,
    COMPLEX,
}

sealed interface ScienceExpression {
    data class RationalLiteral(val value: BigRational) : ScienceExpression
    data object Pi : ScienceExpression
    data object E : ScienceExpression
    data object ImaginaryUnit : ScienceExpression
    data class Variable(val name: String) : ScienceExpression
    data class Sum(val terms: List<ScienceExpression>) : ScienceExpression
    data class Product(val factors: List<ScienceExpression>) : ScienceExpression
    data class Quotient(val numerator: ScienceExpression, val denominator: ScienceExpression) : ScienceExpression
    data class Power(val base: ScienceExpression, val exponent: Int) : ScienceExpression
    data class Radical(val radicand: ScienceExpression, val index: Int = 2) : ScienceExpression
}

data class ComplexApprox(val real: Double, val imaginary: Double = 0.0) {
    operator fun plus(other: ComplexApprox) = ComplexApprox(real + other.real, imaginary + other.imaginary)
    operator fun minus(other: ComplexApprox) = ComplexApprox(real - other.real, imaginary - other.imaginary)
    operator fun times(other: ComplexApprox) = ComplexApprox(
        real = real * other.real - imaginary * other.imaginary,
        imaginary = real * other.imaginary + imaginary * other.real,
    )

    operator fun div(other: ComplexApprox): ComplexApprox {
        val denominator = other.real * other.real + other.imaginary * other.imaginary
        require(abs(denominator) > 1e-15) { "不能除以 0。" }
        return ComplexApprox(
            real = (real * other.real + imaginary * other.imaginary) / denominator,
            imaginary = (imaginary * other.real - real * other.imaginary) / denominator,
        )
    }

    fun pow(exponent: Int): ComplexApprox {
        if (exponent == 0) return ComplexApprox(1.0)
        if (exponent < 0) return ComplexApprox(1.0) / pow(-exponent)
        var result = ComplexApprox(1.0)
        var factor = this
        var remaining = exponent
        while (remaining > 0) {
            if (remaining and 1 == 1) result *= factor
            factor *= factor
            remaining = remaining ushr 1
        }
        return result
    }
}

object ScienceExpressionParser {
    fun parse(source: String): ScienceExpression {
        require(source.length <= 256) { "表达式过长。" }
        return Parser(expandImplicitMultiplication(tokenize(source))).parse()
    }

    private fun tokenize(source: String): List<Token> {
        val normalized = source
            .replace('−', '-')
            .replace('—', '-')
            .replace('×', '*')
            .replace('·', '*')
            .replace('÷', '/')
        val result = mutableListOf<Token>()
        var index = 0
        while (index < normalized.length) {
            val char = normalized[index]
            when {
                char.isWhitespace() -> index++
                char.isDigit() || char == '.' -> {
                    val start = index
                    var dots = 0
                    while (index < normalized.length && (normalized[index].isDigit() || normalized[index] == '.')) {
                        if (normalized[index] == '.') dots++
                        index++
                    }
                    require(dots <= 1) { "数字格式不正确。" }
                    result += Token.Number(normalized.substring(start, index))
                }
                char.isLetter() || char == '_' -> {
                    val start = index++
                    while (index < normalized.length && (normalized[index].isLetterOrDigit() || normalized[index] == '_')) index++
                    val identifier = normalized.substring(start, index)
                    result += if (identifier.equals("sqrt", ignoreCase = true)) Token.Radical else Token.Identifier(identifier)
                }
                char == 'π' -> { result += Token.Identifier("pi"); index++ }
                char == '√' -> { result += Token.Radical; index++ }
                char == '+' -> { result += Token.Plus; index++ }
                char == '-' -> { result += Token.Minus; index++ }
                char == '*' -> { result += Token.Multiply; index++ }
                char == '/' -> { result += Token.Divide; index++ }
                char == '^' -> { result += Token.Power; index++ }
                char == '(' -> { result += Token.LeftParen; index++ }
                char == ')' -> { result += Token.RightParen; index++ }
                else -> error("暂不支持字符“$char”。")
            }
            require(result.size <= 256) { "表达式过于复杂。" }
        }
        result += Token.End
        return result
    }

    private fun expandImplicitMultiplication(tokens: List<Token>): List<Token> {
        val expanded = mutableListOf<Token>()
        tokens.forEach { token ->
            val previous = expanded.lastOrNull()
            if (previous != null && canEndValue(previous) && canStartValue(token)) expanded += Token.Multiply
            expanded += token
        }
        return expanded
    }

    private fun canEndValue(token: Token): Boolean =
        token is Token.Number || token is Token.Identifier || token == Token.RightParen

    private fun canStartValue(token: Token): Boolean =
        token is Token.Number || token is Token.Identifier || token == Token.LeftParen || token == Token.Radical

    private class Parser(private val tokens: List<Token>) {
        private var index = 0

        fun parse(): ScienceExpression {
            val result = parseSum()
            require(peek() == Token.End) { "表达式中有无法识别的部分。" }
            return result
        }

        private fun parseSum(): ScienceExpression {
            val terms = mutableListOf(parseProduct())
            while (true) {
                when (peek()) {
                    Token.Plus -> { index++; terms += parseProduct() }
                    Token.Minus -> {
                        index++
                        terms += ScienceExpression.Product(
                            listOf(ScienceExpression.RationalLiteral(BigRational.of(-1)), parseProduct()),
                        )
                    }
                    else -> return if (terms.size == 1) terms.first() else ScienceExpression.Sum(terms)
                }
            }
        }

        private fun parseProduct(): ScienceExpression {
            var expression = parsePower()
            while (true) {
                expression = when (peek()) {
                    Token.Multiply -> {
                        index++
                        ScienceExpression.Product(listOf(expression, parsePower()))
                    }
                    Token.Divide -> {
                        index++
                        ScienceExpression.Quotient(expression, parsePower())
                    }
                    else -> return expression
                }
            }
        }

        private fun parsePower(): ScienceExpression {
            var base = parseUnary()
            if (peek() == Token.Power) {
                index++
                var sign = 1
                if (peek() == Token.Minus) { sign = -1; index++ }
                val exponent = (peek() as? Token.Number)?.text?.toIntOrNull()
                    ?: error("当前版本的指数必须是整数。")
                index++
                base = ScienceExpression.Power(base, exponent * sign)
            }
            return base
        }

        private fun parseUnary(): ScienceExpression = when (peek()) {
            Token.Plus -> { index++; parseUnary() }
            Token.Minus -> {
                index++
                ScienceExpression.Product(
                    listOf(ScienceExpression.RationalLiteral(BigRational.of(-1)), parseUnary()),
                )
            }
            Token.Radical -> {
                index++
                ScienceExpression.Radical(parseUnary())
            }
            else -> parsePrimary()
        }

        private fun parsePrimary(): ScienceExpression = when (val token = peek()) {
            is Token.Number -> {
                index++
                ScienceExpression.RationalLiteral(BigRational.parse(token.text))
            }
            is Token.Identifier -> {
                index++
                when (token.name.lowercase()) {
                    "pi" -> ScienceExpression.Pi
                    "e" -> ScienceExpression.E
                    "i" -> ScienceExpression.ImaginaryUnit
                    else -> ScienceExpression.Variable(token.name)
                }
            }
            Token.LeftParen -> {
                index++
                val expression = parseSum()
                require(peek() == Token.RightParen) { "括号没有成对出现。" }
                index++
                expression
            }
            else -> error("这里需要数字、变量、常量、根号或括号。")
        }

        private fun peek(): Token = tokens.getOrElse(index) { Token.End }
    }

    private sealed interface Token {
        data class Number(val text: String) : Token
        data class Identifier(val name: String) : Token
        data object Plus : Token
        data object Minus : Token
        data object Multiply : Token
        data object Divide : Token
        data object Power : Token
        data object Radical : Token
        data object LeftParen : Token
        data object RightParen : Token
        data object End : Token
    }
}

object ScienceExpressionSimplifier {
    fun simplify(
        expression: ScienceExpression,
        domain: ScienceNumberDomain = ScienceNumberDomain.REAL,
    ): ScienceExpression = when (expression) {
        is ScienceExpression.RationalLiteral,
        ScienceExpression.Pi,
        ScienceExpression.E,
        ScienceExpression.ImaginaryUnit,
        is ScienceExpression.Variable,
        -> expression
        is ScienceExpression.Sum -> simplifySum(expression, domain)
        is ScienceExpression.Product -> simplifyProduct(expression, domain)
        is ScienceExpression.Quotient -> simplifyQuotient(expression, domain)
        is ScienceExpression.Power -> simplifyPower(expression, domain)
        is ScienceExpression.Radical -> simplifyRadical(expression, domain)
    }

    private fun simplifySum(expression: ScienceExpression.Sum, domain: ScienceNumberDomain): ScienceExpression {
        val flattened = expression.terms
            .map { simplify(it, domain) }
            .flatMap { if (it is ScienceExpression.Sum) it.terms else listOf(it) }
        val coefficients = linkedMapOf<ScienceExpression?, BigRational>()
        val residual = mutableListOf<ScienceExpression>()
        flattened.forEach { term ->
            val linear = linearTerm(term)
            if (linear == null) residual += term
            else coefficients[linear.base] = (coefficients[linear.base] ?: BigRational.ZERO) + linear.coefficient
        }
        val terms = mutableListOf<ScienceExpression>()
        coefficients.forEach { (base, coefficient) ->
            if (!coefficient.isZero) terms += composeLinearTerm(coefficient, base)
        }
        terms += residual
        return when (terms.size) {
            0 -> ScienceExpression.RationalLiteral(BigRational.ZERO)
            1 -> terms.first()
            else -> ScienceExpression.Sum(terms)
        }
    }

    private fun simplifyProduct(expression: ScienceExpression.Product, domain: ScienceNumberDomain): ScienceExpression {
        val flattened = expression.factors
            .map { simplify(it, domain) }
            .flatMap { if (it is ScienceExpression.Product) it.factors else listOf(it) }
        var rational = BigRational.ONE
        val factors = mutableListOf<ScienceExpression>()
        flattened.forEach { factor ->
            if (factor is ScienceExpression.RationalLiteral) rational *= factor.value else factors += factor
        }
        if (rational.isZero) return ScienceExpression.RationalLiteral(BigRational.ZERO)
        if (rational != BigRational.ONE || factors.isEmpty()) factors.add(0, ScienceExpression.RationalLiteral(rational))
        return when (factors.size) {
            1 -> factors.first()
            else -> ScienceExpression.Product(factors)
        }
    }

    private fun simplifyQuotient(expression: ScienceExpression.Quotient, domain: ScienceNumberDomain): ScienceExpression {
        val numerator = simplify(expression.numerator, domain)
        val denominator = simplify(expression.denominator, domain)
        require(denominator !is ScienceExpression.RationalLiteral || !denominator.value.isZero) { "不能除以 0。" }
        if (numerator is ScienceExpression.RationalLiteral && denominator is ScienceExpression.RationalLiteral) {
            return ScienceExpression.RationalLiteral(numerator.value / denominator.value)
        }
        if (denominator is ScienceExpression.RationalLiteral && denominator.value == BigRational.ONE) return numerator
        return ScienceExpression.Quotient(numerator, denominator)
    }

    private fun simplifyPower(expression: ScienceExpression.Power, domain: ScienceNumberDomain): ScienceExpression {
        val base = simplify(expression.base, domain)
        if (expression.exponent == 0) return ScienceExpression.RationalLiteral(BigRational.ONE)
        if (expression.exponent == 1) return base
        if (base is ScienceExpression.RationalLiteral) {
            return ScienceExpression.RationalLiteral(base.value.pow(expression.exponent))
        }
        return ScienceExpression.Power(base, expression.exponent)
    }

    private fun simplifyRadical(expression: ScienceExpression.Radical, domain: ScienceNumberDomain): ScienceExpression {
        require(expression.index >= 2) { "根指数必须大于等于 2。" }
        val radicand = simplify(expression.radicand, domain)
        if (expression.index != 2 || radicand !is ScienceExpression.RationalLiteral || !radicand.value.isInteger) {
            return ScienceExpression.Radical(radicand, expression.index)
        }
        val integer = radicand.value.numerator
        if (integer.signum() < 0) {
            require(domain == ScienceNumberDomain.COMPLEX) { "当前数域为实数，偶次根号内不能为负数。" }
            val positive = simplifyRadical(
                ScienceExpression.Radical(ScienceExpression.RationalLiteral(BigRational.of(integer.negate())), 2),
                domain,
            )
            return simplifyProduct(ScienceExpression.Product(listOf(ScienceExpression.ImaginaryUnit, positive)), domain)
        }
        val exactRoot = integer.sqrt()
        if (exactRoot * exactRoot == integer) return ScienceExpression.RationalLiteral(BigRational.of(exactRoot))
        val (outside, inside) = extractSquare(integer)
        val base = ScienceExpression.Radical(ScienceExpression.RationalLiteral(BigRational.of(inside)), 2)
        return if (outside == BigInteger.ONE) base else simplifyProduct(
            ScienceExpression.Product(listOf(ScienceExpression.RationalLiteral(BigRational.of(outside)), base)),
            domain,
        )
    }

    private fun extractSquare(value: BigInteger): Pair<BigInteger, BigInteger> {
        if (value.bitLength() > 31) return BigInteger.ONE to value
        var remaining = value.toLong()
        var outside = 1L
        var factor = 2L
        while (factor * factor <= remaining) {
            var count = 0
            while (remaining % factor == 0L) {
                remaining /= factor
                count++
            }
            repeat(count / 2) { outside *= factor }
            if (count % 2 == 1) remaining *= factor
            factor++
        }
        val inside = value.toLong() / (outside * outside)
        return BigInteger.valueOf(outside) to BigInteger.valueOf(inside)
    }

    private data class LinearTerm(val coefficient: BigRational, val base: ScienceExpression?)

    private fun linearTerm(expression: ScienceExpression): LinearTerm? = when (expression) {
        is ScienceExpression.RationalLiteral -> LinearTerm(expression.value, null)
        ScienceExpression.Pi,
        ScienceExpression.ImaginaryUnit,
        is ScienceExpression.Radical,
        is ScienceExpression.Variable,
        -> LinearTerm(BigRational.ONE, expression)
        is ScienceExpression.Product -> {
            val rational = expression.factors.filterIsInstance<ScienceExpression.RationalLiteral>()
                .fold(BigRational.ONE) { accumulator, factor -> accumulator * factor.value }
            val other = expression.factors.filterNot { it is ScienceExpression.RationalLiteral }
            if (other.size == 1) LinearTerm(rational, other.first()) else null
        }
        else -> null
    }

    private fun composeLinearTerm(coefficient: BigRational, base: ScienceExpression?): ScienceExpression {
        if (base == null) return ScienceExpression.RationalLiteral(coefficient)
        if (coefficient == BigRational.ONE) return base
        return ScienceExpression.Product(listOf(ScienceExpression.RationalLiteral(coefficient), base))
    }
}

object ScienceExpressionEvaluator {
    fun evaluate(expression: ScienceExpression, variables: Map<String, Double> = emptyMap()): ComplexApprox = when (expression) {
        is ScienceExpression.RationalLiteral -> ComplexApprox(expression.value.toDouble())
        ScienceExpression.Pi -> ComplexApprox(Math.PI)
        ScienceExpression.E -> ComplexApprox(Math.E)
        ScienceExpression.ImaginaryUnit -> ComplexApprox(0.0, 1.0)
        is ScienceExpression.Variable -> ComplexApprox(variables[expression.name] ?: error("缺少变量 ${expression.name}。"))
        is ScienceExpression.Sum -> expression.terms.fold(ComplexApprox(0.0)) { result, term -> result + evaluate(term, variables) }
        is ScienceExpression.Product -> expression.factors.fold(ComplexApprox(1.0)) { result, factor -> result * evaluate(factor, variables) }
        is ScienceExpression.Quotient -> evaluate(expression.numerator, variables) / evaluate(expression.denominator, variables)
        is ScienceExpression.Power -> evaluate(expression.base, variables).pow(expression.exponent)
        is ScienceExpression.Radical -> {
            require(expression.index == 2) { "当前近似求值只支持平方根。" }
            val value = evaluate(expression.radicand, variables)
            require(abs(value.imaginary) < 1e-12) { "暂不支持复数的一般根式。" }
            if (value.real >= 0) ComplexApprox(sqrt(value.real)) else ComplexApprox(0.0, sqrt(-value.real))
        }
    }
}

object ScienceExpressionRenderer {
    fun render(expression: ScienceExpression): String = render(expression, 0)

    private fun render(expression: ScienceExpression, parentPrecedence: Int): String {
        val precedence = when (expression) {
            is ScienceExpression.Sum -> 1
            is ScienceExpression.Product,
            is ScienceExpression.Quotient,
            -> 2
            is ScienceExpression.Power -> 3
            else -> 4
        }
        val raw = when (expression) {
            is ScienceExpression.RationalLiteral -> expression.value.toString()
            ScienceExpression.Pi -> "π"
            ScienceExpression.E -> "e"
            ScienceExpression.ImaginaryUnit -> "i"
            is ScienceExpression.Variable -> expression.name
            is ScienceExpression.Sum -> expression.terms.joinToString(" + ") { render(it, precedence) }.replace("+ -", "- ")
            is ScienceExpression.Product -> expression.factors.joinToString("·") { render(it, precedence) }
            is ScienceExpression.Quotient -> "${render(expression.numerator, precedence)}/${render(expression.denominator, precedence)}"
            is ScienceExpression.Power -> "${render(expression.base, precedence)}^${expression.exponent}"
            is ScienceExpression.Radical -> if (expression.index == 2) {
                "√${render(expression.radicand, precedence)}"
            } else {
                "${expression.index}√${render(expression.radicand, precedence)}"
            }
        }
        return if (precedence < parentPrecedence) "($raw)" else raw
    }
}
