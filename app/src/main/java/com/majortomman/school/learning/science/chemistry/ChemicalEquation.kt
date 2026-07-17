package com.majortomman.school.learning.science.chemistry

import com.majortomman.school.learning.science.expression.BigRational
import java.math.BigInteger

data class EquationTerm(
    val coefficient: BigInteger,
    val species: ChemicalSpecies,
) {
    init {
        require(coefficient.signum() > 0) { "化学计量系数必须为正整数。" }
    }

    fun display(hideOne: Boolean = true): String =
        (if (hideOne && coefficient == BigInteger.ONE) "" else coefficient.toString()) + species.display()
}

data class ChemicalEquation(
    val reactants: List<EquationTerm>,
    val products: List<EquationTerm>,
    val reversible: Boolean = false,
    val conditions: List<String> = emptyList(),
) {
    init {
        require(reactants.isNotEmpty() && products.isNotEmpty()) { "方程式两侧都必须有物质。" }
    }

    fun display(): String = buildString {
        append(reactants.joinToString(" + ") { it.display() })
        append(if (reversible) " ⇌ " else " → ")
        append(products.joinToString(" + ") { it.display() })
    }

    fun conservation(): ConservationReport {
        val elements = (reactants + products)
            .flatMap { it.species.formula.atomCounts.keys }
            .distinct()
            .sortedBy { it.atomicNumber }
        val elementRows = elements.associateWith { element ->
            val left = reactants.sumOfBigInteger { term ->
                term.coefficient * (term.species.formula.atomCounts[element] ?: BigInteger.ZERO)
            }
            val right = products.sumOfBigInteger { term ->
                term.coefficient * (term.species.formula.atomCounts[element] ?: BigInteger.ZERO)
            }
            ConservationRow(element.symbol, left, right)
        }
        val leftCharge = reactants.sumOf { it.coefficient.toInt() * it.species.formula.charge }
        val rightCharge = products.sumOf { it.coefficient.toInt() * it.species.formula.charge }
        return ConservationReport(
            elementRows = elementRows,
            leftCharge = leftCharge,
            rightCharge = rightCharge,
        )
    }

    fun normalized(): ChemicalEquation {
        val coefficients = (reactants + products).map { it.coefficient }
        val gcd = coefficients.reduce(BigInteger::gcd)
        if (gcd == BigInteger.ONE) return this
        return copy(
            reactants = reactants.map { it.copy(coefficient = it.coefficient / gcd) },
            products = products.map { it.copy(coefficient = it.coefficient / gcd) },
        )
    }
}

data class ConservationRow(
    val symbol: String,
    val reactantCount: BigInteger,
    val productCount: BigInteger,
) {
    val balanced: Boolean get() = reactantCount == productCount
}

data class ConservationReport(
    val elementRows: Map<ChemicalElement, ConservationRow>,
    val leftCharge: Int,
    val rightCharge: Int,
) {
    val atomsBalanced: Boolean get() = elementRows.values.all(ConservationRow::balanced)
    val chargeBalanced: Boolean get() = leftCharge == rightCharge
    val balanced: Boolean get() = atomsBalanced && chargeBalanced

    fun message(): String = when {
        balanced -> "元素原子数与总电荷均守恒。"
        !atomsBalanced && !chargeBalanced -> "原子数和总电荷都不守恒。"
        !atomsBalanced -> "原子数不守恒。"
        else -> "总电荷不守恒。"
    }
}

object ChemicalEquationParser {
    private val arrowPattern = Regex("(<=>|⇌|↔|->|→|=)")
    private val coefficientPattern = Regex("^(\\d+)\\s*(.+)$")

    fun parse(input: String): ChemicalEquation {
        val text = input.trim()
        require(text.length <= 512) { "化学方程式过长。" }
        val arrows = arrowPattern.findAll(text).toList()
        require(arrows.size == 1) { "方程式中必须且只能有一个反应箭头。" }
        val arrow = arrows.single()
        val left = text.substring(0, arrow.range.first).trim()
        val right = text.substring(arrow.range.last + 1).trim()
        require(left.isNotBlank() && right.isNotBlank()) { "反应箭头两侧都需要物质。" }
        val reversible = arrow.value in setOf("<=>", "⇌", "↔")
        return ChemicalEquation(
            reactants = splitTerms(left).map(::parseTerm),
            products = splitTerms(right).map(::parseTerm),
            reversible = reversible,
        )
    }

    private fun splitTerms(side: String): List<String> {
        val spaced = side.split(Regex("\\s+\\+\\s+")).map(String::trim)
        if (spaced.size > 1) {
            require(spaced.all(String::isNotBlank)) { "加号两侧需要物质。" }
            return spaced
        }
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        side.forEachIndexed { index, char ->
            when (char) {
                '(', '[' -> depth++
                ')', ']' -> depth--
                '+' -> if (depth == 0 && side.getOrNull(index - 1) != '^') {
                    val part = side.substring(start, index).trim()
                    require(part.isNotEmpty()) { "加号左侧缺少物质。" }
                    result += part
                    start = index + 1
                }
            }
            require(depth >= 0) { "方程式括号不匹配。" }
        }
        require(depth == 0) { "方程式括号没有闭合。" }
        result += side.substring(start).trim().also { require(it.isNotEmpty()) { "加号右侧缺少物质。" } }
        return result
    }

    private fun parseTerm(text: String): EquationTerm {
        val match = coefficientPattern.matchEntire(text)
        val coefficient = match?.groupValues?.get(1)?.toBigInteger() ?: BigInteger.ONE
        val speciesText = match?.groupValues?.get(2) ?: text
        return EquationTerm(coefficient, ChemicalSpecies.parse(speciesText))
    }
}

object ChemicalEquationBalancer {
    fun balance(equation: ChemicalEquation): ChemicalEquation {
        val allTerms = equation.reactants + equation.products
        require(allTerms.size <= 24) { "方程式物质种类过多。" }
        val elements = allTerms.flatMap { it.species.formula.atomCounts.keys }
            .distinct()
            .sortedBy { it.atomicNumber }
        require(elements.isNotEmpty()) { "方程式没有元素。" }
        val includeCharge = allTerms.any { it.species.formula.charge != 0 }
        val rowCount = elements.size + if (includeCharge) 1 else 0
        val columnCount = allTerms.size
        val matrix = Array(rowCount) { Array(columnCount) { BigRational.ZERO } }

        elements.forEachIndexed { row, element ->
            allTerms.forEachIndexed { column, term ->
                val sign = if (column < equation.reactants.size) BigRational.ONE else BigRational.of(-1)
                val count = term.species.formula.atomCounts[element] ?: BigInteger.ZERO
                matrix[row][column] = BigRational.of(count) * sign
            }
        }
        if (includeCharge) {
            val row = elements.size
            allTerms.forEachIndexed { column, term ->
                val sign = if (column < equation.reactants.size) 1 else -1
                matrix[row][column] = BigRational.of((term.species.formula.charge * sign).toLong())
            }
        }

        val basis = nullSpaceBasis(matrix)
        require(basis.isNotEmpty()) { "无法找到非零配平解。" }
        val coefficients = choosePositiveIntegerVector(basis)
            ?: error("方程式存在多自由度或需要额外反应条件，无法得到全部为正的唯一教学配平。")
        val reactants = equation.reactants.mapIndexed { index, term -> term.copy(coefficient = coefficients[index]) }
        val products = equation.products.mapIndexed { index, term ->
            term.copy(coefficient = coefficients[index + equation.reactants.size])
        }
        return equation.copy(reactants = reactants, products = products).normalized().also {
            require(it.conservation().balanced) { "内部配平结果未通过守恒复核。" }
        }
    }

    private fun nullSpaceBasis(input: Array<Array<BigRational>>): List<List<BigRational>> {
        val rows = input.size
        val columns = input.first().size
        val matrix = Array(rows) { row -> input[row].clone() }
        val pivotColumns = mutableListOf<Int>()
        var pivotRow = 0
        for (column in 0 until columns) {
            val found = (pivotRow until rows).firstOrNull { !matrix[it][column].isZero } ?: continue
            val temporary = matrix[found]
            matrix[found] = matrix[pivotRow]
            matrix[pivotRow] = temporary

            val divisor = matrix[pivotRow][column]
            for (entry in column until columns) matrix[pivotRow][entry] /= divisor
            for (row in 0 until rows) {
                if (row == pivotRow) continue
                val factor = matrix[row][column]
                if (factor.isZero) continue
                for (entry in column until columns) {
                    matrix[row][entry] = matrix[row][entry] - factor * matrix[pivotRow][entry]
                }
            }
            pivotColumns += column
            pivotRow++
            if (pivotRow == rows) break
        }
        val freeColumns = (0 until columns).filterNot(pivotColumns::contains)
        return freeColumns.map { freeColumn ->
            MutableList(columns) { BigRational.ZERO }.also { vector ->
                vector[freeColumn] = BigRational.ONE
                pivotColumns.forEachIndexed { row, pivotColumn ->
                    vector[pivotColumn] = -matrix[row][freeColumn]
                }
            }
        }
    }

    private fun choosePositiveIntegerVector(basis: List<List<BigRational>>): List<BigInteger>? {
        if (basis.size == 1) return normalizePositive(basis.single())
        require(basis.size <= 4) { "配平自由变量过多。" }
        var best: List<BigInteger>? = null
        val coefficients = IntArray(basis.size)

        fun search(index: Int) {
            if (index == coefficients.size) {
                if (coefficients.all { it == 0 }) return
                val vector = MutableList(basis.first().size) { BigRational.ZERO }
                basis.forEachIndexed { basisIndex, basisVector ->
                    val scale = BigRational.of(coefficients[basisIndex].toLong())
                    basisVector.forEachIndexed { entry, value -> vector[entry] = vector[entry] + value * scale }
                }
                val normalized = normalizePositive(vector) ?: return
                if (best == null || normalized.sumOf { it } < best!!.sumOf { it }) best = normalized
                return
            }
            for (value in -5..5) {
                coefficients[index] = value
                search(index + 1)
            }
        }
        search(0)
        return best
    }

    private fun normalizePositive(vector: List<BigRational>): List<BigInteger>? {
        if (vector.any { it.isZero }) return null
        val allPositive = vector.all { it.signum > 0 }
        val allNegative = vector.all { it.signum < 0 }
        if (!allPositive && !allNegative) return null
        val lcm = vector.map { it.denominator }.reduce(::lcm)
        var integers = vector.map { rational -> rational.numerator * (lcm / rational.denominator) }
        if (allNegative) integers = integers.map(BigInteger::negate)
        val gcd = integers.map(BigInteger::abs).reduce(BigInteger::gcd)
        return integers.map { it / gcd }
    }

    private fun lcm(left: BigInteger, right: BigInteger): BigInteger =
        left / left.gcd(right) * right
}

object IonicEquationReducer {
    fun cancelUnchangedSpecies(equation: ChemicalEquation): ChemicalEquation {
        val left = equation.reactants.toMutableList()
        val right = equation.products.toMutableList()
        val keys = (left + right).map { speciesKey(it.species) }.distinct()
        keys.forEach { key ->
            val leftTotal = left.filter { speciesKey(it.species) == key }.sumOfBigInteger(EquationTerm::coefficient)
            val rightTotal = right.filter { speciesKey(it.species) == key }.sumOfBigInteger(EquationTerm::coefficient)
            var cancel = minOf(leftTotal, rightTotal)
            if (cancel.signum() == 0) return@forEach
            cancelFrom(left, key, cancel)
            cancelFrom(right, key, cancel)
        }
        require(left.isNotEmpty() && right.isNotEmpty()) { "约去旁观离子后没有净反应。" }
        return equation.copy(reactants = left, products = right).normalized()
    }

    private fun cancelFrom(terms: MutableList<EquationTerm>, key: String, amount: BigInteger) {
        var remaining = amount
        val iterator = terms.listIterator()
        while (iterator.hasNext() && remaining.signum() > 0) {
            val term = iterator.next()
            if (speciesKey(term.species) != key) continue
            val removed = minOf(term.coefficient, remaining)
            val next = term.coefficient - removed
            remaining -= removed
            if (next.signum() == 0) iterator.remove() else iterator.set(term.copy(coefficient = next))
        }
    }

    private fun speciesKey(species: ChemicalSpecies): String =
        species.formula.atomCounts.entries.joinToString(";") { "${it.key.symbol}:${it.value}" } +
            "|q=${species.formula.charge}|state=${species.state}"
}

private inline fun <T> Iterable<T>.sumOfBigInteger(selector: (T) -> BigInteger): BigInteger =
    fold(BigInteger.ZERO) { result, item -> result + selector(item) }
