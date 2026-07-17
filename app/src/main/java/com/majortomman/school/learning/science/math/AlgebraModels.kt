package com.majortomman.school.learning.science.math

import com.majortomman.school.learning.science.expression.BigRational
import com.majortomman.school.learning.science.expression.ComplexApprox
import com.majortomman.school.learning.science.expression.ScienceExpression
import com.majortomman.school.learning.science.expression.ScienceExpressionEvaluator
import com.majortomman.school.learning.science.expression.ScienceExpressionRenderer
import com.majortomman.school.learning.science.expression.ScienceExpressionSimplifier
import com.majortomman.school.learning.science.expression.ScienceNumberDomain

sealed interface EquationSolution {
    data object AllValues : EquationSolution
    data object NoSolution : EquationSolution
    data class Roots(val values: List<ExactRoot>) : EquationSolution
}

data class ExactRoot(
    val expression: ScienceExpression,
    val text: String,
    val approximate: ComplexApprox,
)

data class AlgebraStep(
    val title: String,
    val expression: String,
    val reason: String,
    val conditions: List<String> = emptyList(),
)

data class AlgebraSolution(
    val solution: EquationSolution,
    val steps: List<AlgebraStep>,
)

object AlgebraSolver {
    fun solveLinear(a: BigRational, b: BigRational, c: BigRational = BigRational.ZERO): AlgebraSolution {
        val steps = mutableListOf(
            AlgebraStep("原方程", "${a}x + $b = $c", "读取一次方程系数"),
        )
        if (a.isZero) {
            val solution = if (b == c) EquationSolution.AllValues else EquationSolution.NoSolution
            steps += AlgebraStep(
                "检查常量",
                "$b ${if (b == c) "=" else "≠"} $c",
                if (b == c) "恒等式对所有实数成立" else "矛盾式没有解",
            )
            return AlgebraSolution(solution, steps)
        }
        val root = (c - b) / a
        steps += AlgebraStep("移项", "${a}x = ${c - b}", "等式两边同时减去 $b")
        steps += AlgebraStep("除以系数", "x = $root", "等式两边同时除以非零数 $a", listOf("a ≠ 0"))
        return AlgebraSolution(
            EquationSolution.Roots(listOf(rootOf(ScienceExpression.RationalLiteral(root)))),
            steps,
        )
    }

    fun solveQuadratic(
        a: BigRational,
        b: BigRational,
        c: BigRational,
        domain: ScienceNumberDomain = ScienceNumberDomain.REAL,
    ): AlgebraSolution {
        require(!a.isZero) { "二次项系数 a 不能为 0。" }
        val discriminant = b.pow(2) - BigRational.of(4) * a * c
        val steps = mutableListOf(
            AlgebraStep("标准形式", "${a}x² + ${b}x + $c = 0", "读取 a、b、c"),
            AlgebraStep("判别式", "Δ = b² - 4ac = $discriminant", "判断实根数量和根式部分"),
        )
        if (discriminant.signum < 0 && domain == ScienceNumberDomain.REAL) {
            steps += AlgebraStep("实数域结论", "Δ < 0", "实数域内没有平方等于负数的数")
            return AlgebraSolution(EquationSolution.NoSolution, steps)
        }

        val negativeB = ScienceExpression.RationalLiteral(-b)
        val denominator = ScienceExpression.RationalLiteral(BigRational.of(2) * a)
        val radical = ScienceExpression.Radical(ScienceExpression.RationalLiteral(discriminant))
        val plus = ScienceExpressionSimplifier.simplify(
            ScienceExpression.Quotient(ScienceExpression.Sum(listOf(negativeB, radical)), denominator),
            domain,
        )
        if (discriminant.isZero) {
            val root = rootOf(plus)
            steps += AlgebraStep("重根", "x = ${root.text}", "Δ = 0，正负根式得到同一个值")
            return AlgebraSolution(EquationSolution.Roots(listOf(root)), steps)
        }
        val minus = ScienceExpressionSimplifier.simplify(
            ScienceExpression.Quotient(
                ScienceExpression.Sum(
                    listOf(
                        negativeB,
                        ScienceExpression.Product(
                            listOf(ScienceExpression.RationalLiteral(BigRational.of(-1)), radical),
                        ),
                    ),
                ),
                denominator,
            ),
            domain,
        )
        val roots = listOf(rootOf(plus), rootOf(minus)).sortedBy { it.approximate.real }
        steps += AlgebraStep(
            "求根公式",
            "x = (-b ± √Δ)/(2a)",
            if (discriminant.signum > 0) "Δ > 0，得到两个不同的根" else "复数域中保留虚根",
            listOf("a ≠ 0"),
        )
        steps += AlgebraStep("结果", roots.joinToString("，") { "x = ${it.text}" }, "保留精确根式")
        return AlgebraSolution(EquationSolution.Roots(roots), steps)
    }

    private fun rootOf(expression: ScienceExpression): ExactRoot {
        val simplified = ScienceExpressionSimplifier.simplify(expression, ScienceNumberDomain.COMPLEX)
        return ExactRoot(
            expression = simplified,
            text = ScienceExpressionRenderer.render(simplified),
            approximate = ScienceExpressionEvaluator.evaluate(simplified),
        )
    }
}

enum class Comparison {
    LESS,
    LESS_OR_EQUAL,
    GREATER,
    GREATER_OR_EQUAL,
    ;

    fun reversed(): Comparison = when (this) {
        LESS -> GREATER
        LESS_OR_EQUAL -> GREATER_OR_EQUAL
        GREATER -> LESS
        GREATER_OR_EQUAL -> LESS_OR_EQUAL
    }

    fun symbol(): String = when (this) {
        LESS -> "<"
        LESS_OR_EQUAL -> "≤"
        GREATER -> ">"
        GREATER_OR_EQUAL -> "≥"
    }
}

data class Boundary(val value: BigRational, val inclusive: Boolean)

data class RealInterval(
    val lower: Boundary? = null,
    val upper: Boundary? = null,
) {
    init {
        require(lower == null || upper == null || lower.value <= upper.value) { "区间下界不能大于上界。" }
    }

    fun render(variable: String = "x"): String = when {
        lower == null && upper == null -> "$variable ∈ ℝ"
        lower == null -> "$variable ${if (upper!!.inclusive) "≤" else "<"} ${upper.value}"
        upper == null -> "$variable ${if (lower.inclusive) "≥" else ">"} ${lower.value}"
        else -> "${lower.value} ${if (lower.inclusive) "≤" else "<"} $variable ${if (upper.inclusive) "≤" else "<"} ${upper.value}"
    }
}

data class InequalitySolution(
    val intervals: List<RealInterval>,
    val steps: List<AlgebraStep>,
)

object InequalitySolver {
    fun solveLinear(
        a: BigRational,
        b: BigRational,
        comparison: Comparison,
        c: BigRational,
    ): InequalitySolution {
        val steps = mutableListOf(
            AlgebraStep("原不等式", "${a}x + $b ${comparison.symbol()} $c", "读取不等式"),
        )
        if (a.isZero) {
            val trueStatement = compare(b, comparison, c)
            return InequalitySolution(
                intervals = if (trueStatement) listOf(RealInterval()) else emptyList(),
                steps = steps + AlgebraStep(
                    "常量判断",
                    "$b ${comparison.symbol()} $c",
                    if (trueStatement) "恒成立" else "不成立",
                ),
            )
        }
        val boundary = (c - b) / a
        val finalComparison = if (a.signum < 0) comparison.reversed() else comparison
        steps += AlgebraStep("移项", "${a}x ${comparison.symbol()} ${c - b}", "两边同时减去 $b")
        if (a.signum < 0) {
            steps += AlgebraStep("除以负数", "x ${finalComparison.symbol()} $boundary", "除以负数时不等号方向反转")
        } else {
            steps += AlgebraStep("除以正数", "x ${finalComparison.symbol()} $boundary", "除以正数时不等号方向不变")
        }
        val inclusive = finalComparison == Comparison.LESS_OR_EQUAL || finalComparison == Comparison.GREATER_OR_EQUAL
        val interval = when (finalComparison) {
            Comparison.LESS, Comparison.LESS_OR_EQUAL -> RealInterval(upper = Boundary(boundary, inclusive))
            Comparison.GREATER, Comparison.GREATER_OR_EQUAL -> RealInterval(lower = Boundary(boundary, inclusive))
        }
        return InequalitySolution(listOf(interval), steps)
    }

    private fun compare(left: BigRational, comparison: Comparison, right: BigRational): Boolean = when (comparison) {
        Comparison.LESS -> left < right
        Comparison.LESS_OR_EQUAL -> left <= right
        Comparison.GREATER -> left > right
        Comparison.GREATER_OR_EQUAL -> left >= right
    }
}

sealed interface DomainConstraint {
    val description: String
    fun isSatisfied(variables: Map<String, Double>): Boolean

    data class NonZero(val expression: ScienceExpression) : DomainConstraint {
        override val description: String = "${ScienceExpressionRenderer.render(expression)} ≠ 0"
        override fun isSatisfied(variables: Map<String, Double>): Boolean =
            kotlin.math.abs(ScienceExpressionEvaluator.evaluate(expression, variables).real) > 1e-10
    }

    data class NonNegative(val expression: ScienceExpression) : DomainConstraint {
        override val description: String = "${ScienceExpressionRenderer.render(expression)} ≥ 0"
        override fun isSatisfied(variables: Map<String, Double>): Boolean =
            ScienceExpressionEvaluator.evaluate(expression, variables).real >= -1e-10
    }

    data class Positive(val expression: ScienceExpression) : DomainConstraint {
        override val description: String = "${ScienceExpressionRenderer.render(expression)} > 0"
        override fun isSatisfied(variables: Map<String, Double>): Boolean =
            ScienceExpressionEvaluator.evaluate(expression, variables).real > 1e-10
    }
}

data class FunctionDefinition(
    val name: String,
    val variable: String,
    val expression: ScienceExpression,
    val domain: List<DomainConstraint> = emptyList(),
) {
    fun evaluate(value: Double): Double {
        val variables = mapOf(variable to value)
        require(domain.all { it.isSatisfied(variables) }) {
            "x=$value 不满足定义域：${domain.filterNot { it.isSatisfied(variables) }.joinToString { it.description }}"
        }
        val result = ScienceExpressionEvaluator.evaluate(expression, variables)
        require(kotlin.math.abs(result.imaginary) < 1e-10) { "当前函数值不在实数域。" }
        return result.real
    }

    fun sample(start: Double, end: Double, points: Int): List<Pair<Double, Double?>> {
        require(points >= 2) { "采样点至少为 2。" }
        val step = (end - start) / (points - 1)
        return List(points) { index ->
            val x = start + index * step
            x to runCatching { evaluate(x) }.getOrNull()
        }
    }
}
