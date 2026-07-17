package com.majortomman.school.learning.science.chemistry

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

private val ONE_INTEGER: BigInteger = BigInteger.ONE

data class FormulaPart(
    val multiplier: BigInteger,
    val atomCounts: Map<ChemicalElement, BigInteger>,
) {
    init {
        require(multiplier.signum() > 0) { "化学式片段系数必须为正整数。" }
        require(atomCounts.isNotEmpty()) { "化学式片段不能为空。" }
        require(atomCounts.values.all { it.signum() > 0 })
    }
}

data class ChemicalFormula(
    val parts: List<FormulaPart>,
    val charge: Int = 0,
    val source: String,
) {
    init {
        require(parts.isNotEmpty()) { "化学式不能为空。" }
    }

    val atomCounts: Map<ChemicalElement, BigInteger> by lazy {
        val result = linkedMapOf<ChemicalElement, BigInteger>()
        parts.forEach { part ->
            part.atomCounts.forEach { (element, count) ->
                result[element] = (result[element] ?: BigInteger.ZERO) + count * part.multiplier
            }
        }
        result.toSortedMap(compareBy { it.atomicNumber })
    }

    fun count(symbol: String): BigInteger = atomCounts[PeriodicTable.bySymbol(symbol)] ?: BigInteger.ZERO

    fun relativeMolecularMass(mathContext: MathContext = MathContext.DECIMAL128): BigDecimal =
        atomCounts.entries.fold(BigDecimal.ZERO) { total, (element, count) ->
            val mass = element.relativeAtomicMass
                ?: error("元素 ${element.symbol} 暂无稳定的教学相对原子质量。")
            total.add(mass.multiply(BigDecimal(count), mathContext), mathContext)
        }

    fun massFractions(mathContext: MathContext = MathContext.DECIMAL64): Map<ChemicalElement, BigDecimal> {
        val total = relativeMolecularMass(mathContext)
        require(total.signum() > 0)
        return atomCounts.mapValues { (element, count) ->
            element.relativeAtomicMass!!
                .multiply(BigDecimal(count), mathContext)
                .divide(total, mathContext)
        }
    }

    fun canonicalText(): String = buildString {
        append(source)
        if (charge != 0 && !source.contains('^')) {
            append('^')
            if (kotlin.math.abs(charge) != 1) append(kotlin.math.abs(charge))
            append(if (charge > 0) '+' else '-')
        }
    }
}

object ChemicalFormulaParser {
    private val caretCharge = Regex("\\^(\\d*)([+-])$")
    private val repeatedCharge = Regex("([+-]+)$")

    fun parse(input: String): ChemicalFormula {
        var text = input.trim().replace('•', '·')
        require(text.isNotEmpty()) { "请输入化学式。" }
        require(text.length <= 160) { "化学式过长。" }

        val chargeResult = parseCharge(text)
        text = chargeResult.first
        val charge = chargeResult.second
        require(text.isNotBlank()) { "电荷前缺少化学式。" }

        val partTexts = text.split('·')
        require(partTexts.all { it.isNotBlank() }) { "结晶水点号两侧都需要化学式。" }
        val parts = partTexts.map(::parsePart)
        return ChemicalFormula(parts = parts, charge = charge, source = text)
    }

    private fun parseCharge(input: String): Pair<String, Int> {
        caretCharge.find(input)?.let { match ->
            val magnitude = match.groupValues[1].ifBlank { "1" }.toInt()
            require(magnitude in 1..16) { "离子电荷大小超出教学范围。" }
            val charge = if (match.groupValues[2] == "+") magnitude else -magnitude
            return input.removeRange(match.range).trim() to charge
        }
        repeatedCharge.find(input)?.let { match ->
            val signs = match.value
            require(signs.all { it == signs.first() }) { "电荷符号不能混合。" }
            require(signs.length <= 8) { "离子电荷大小超出教学范围。" }
            val charge = if (signs.first() == '+') signs.length else -signs.length
            return input.removeRange(match.range).trim() to charge
        }
        return input to 0
    }

    private fun parsePart(text: String): FormulaPart {
        var index = 0
        while (index < text.length && text[index].isDigit()) index++
        val multiplier = if (index == 0) ONE_INTEGER else text.substring(0, index).toBigInteger()
        require(multiplier.signum() > 0) { "化学式片段系数必须大于 0。" }
        val body = text.substring(index)
        require(body.isNotBlank()) { "片段系数后缺少化学式。" }
        val parser = FormulaBodyParser(body)
        val counts = parser.parse()
        return FormulaPart(multiplier, counts)
    }

    private class FormulaBodyParser(private val source: String) {
        private var index = 0
        private var tokenCount = 0

        fun parse(): Map<ChemicalElement, BigInteger> {
            val result = parseSequence(expectedClosing = null)
            require(index == source.length) { "化学式中有无法识别的部分。" }
            require(result.isNotEmpty()) { "化学式不能为空。" }
            return result
        }

        private fun parseSequence(expectedClosing: Char?): MutableMap<ChemicalElement, BigInteger> {
            val result = linkedMapOf<ChemicalElement, BigInteger>()
            while (index < source.length) {
                val char = source[index]
                if (char == ')' || char == ']') {
                    require(expectedClosing == char) { "化学式括号不匹配。" }
                    index++
                    return multiply(parseSubscript(), result)
                }
                when {
                    char == '(' || char == '[' -> {
                        val closing = if (char == '(') ')' else ']'
                        index++
                        val group = parseSequence(closing)
                        merge(result, group)
                    }
                    char.isUpperCase() -> {
                        val start = index++
                        if (index < source.length && source[index].isLowerCase()) index++
                        val symbol = source.substring(start, index)
                        val element = PeriodicTable.bySymbol(symbol)
                        val count = parseSubscript()
                        result[element] = (result[element] ?: BigInteger.ZERO) + count
                    }
                    else -> error("化学式中无法识别字符“$char”。")
                }
                tokenCount++
                require(tokenCount <= 128) { "化学式结构过于复杂。" }
            }
            require(expectedClosing == null) { "化学式括号没有闭合。" }
            return result
        }

        private fun parseSubscript(): BigInteger {
            val start = index
            while (index < source.length && source[index].isDigit()) index++
            if (start == index) return ONE_INTEGER
            val value = source.substring(start, index).toBigInteger()
            require(value.signum() > 0) { "化学式下标必须为正整数。" }
            require(value <= BigInteger.valueOf(10_000)) { "化学式下标超出教学范围。" }
            return value
        }

        private fun multiply(
            multiplier: BigInteger,
            counts: MutableMap<ChemicalElement, BigInteger>,
        ): MutableMap<ChemicalElement, BigInteger> {
            if (multiplier == ONE_INTEGER) return counts
            counts.replaceAll { _, value -> value * multiplier }
            return counts
        }

        private fun merge(
            destination: MutableMap<ChemicalElement, BigInteger>,
            source: Map<ChemicalElement, BigInteger>,
        ) {
            source.forEach { (element, count) ->
                destination[element] = (destination[element] ?: BigInteger.ZERO) + count
            }
        }
    }
}

enum class SubstanceState(val suffix: String) {
    SOLID("s"),
    LIQUID("l"),
    GAS("g"),
    AQUEOUS("aq"),
}

data class ChemicalSpecies(
    val formula: ChemicalFormula,
    val state: SubstanceState? = null,
) {
    fun display(): String = formula.canonicalText() + (state?.let { "(${it.suffix})" } ?: "")

    companion object {
        private val statePattern = Regex("\\((aq|s|l|g)\\)$", RegexOption.IGNORE_CASE)

        fun parse(text: String): ChemicalSpecies {
            val trimmed = text.trim()
            val stateMatch = statePattern.find(trimmed)
            val state = stateMatch?.groupValues?.get(1)?.lowercase()?.let { suffix ->
                SubstanceState.entries.first { it.suffix == suffix }
            }
            val formulaText = if (stateMatch == null) trimmed else trimmed.removeRange(stateMatch.range).trim()
            return ChemicalSpecies(ChemicalFormulaParser.parse(formulaText), state)
        }
    }
}
