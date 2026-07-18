package com.majortomman.school.learning.science.chemistry

import com.majortomman.school.learning.science.chemistry.organic.FunctionalGroupDetector
import com.majortomman.school.learning.science.chemistry.organic.OrganicIsomerAnalyzer
import com.majortomman.school.learning.science.chemistry.organic.OrganicNotationParser
import java.math.BigDecimal

enum class ChemistryVerificationMode {
    FORMULA,
    EQUATION_CHECK,
    EQUATION_BALANCE,
    ORGANIC_STRUCTURE,
    ORGANIC_ISOMER,
}

enum class ChemistryVerificationStatus {
    VALID,
    BALANCED,
    UNBALANCED,
    BALANCED_RESULT,
    INVALID,
    UNSUPPORTED,
}

data class ChemistryVerificationResult(
    val mode: ChemistryVerificationMode,
    val status: ChemistryVerificationStatus,
    val title: String,
    val normalized: String? = null,
    val rows: List<Pair<String, String>> = emptyList(),
    val message: String,
)

object ChemistryVerifier {
    fun verify(
        mode: ChemistryVerificationMode,
        primary: String,
        secondary: String = "",
    ): ChemistryVerificationResult = runCatching {
        when (mode) {
            ChemistryVerificationMode.FORMULA -> verifyFormula(primary)
            ChemistryVerificationMode.EQUATION_CHECK -> verifyEquation(primary, balance = false)
            ChemistryVerificationMode.EQUATION_BALANCE -> verifyEquation(primary, balance = true)
            ChemistryVerificationMode.ORGANIC_STRUCTURE -> verifyOrganic(primary)
            ChemistryVerificationMode.ORGANIC_ISOMER -> compareOrganic(primary, secondary)
        }
    }.getOrElse { error ->
        ChemistryVerificationResult(
            mode = mode,
            status = ChemistryVerificationStatus.INVALID,
            title = "无法完成验证",
            message = error.message ?: "输入内容不符合当前化学语法。",
        )
    }

    private fun verifyFormula(input: String): ChemistryVerificationResult {
        val formula = ChemicalFormulaParser.parse(input)
        val counts = formula.atomCounts.entries.map { (element, count) -> element.symbol to count.toString() }
        val mass = runCatching { formula.relativeMolecularMass().stripTrailingZeros().toPlainString() }.getOrNull()
        return ChemistryVerificationResult(
            mode = ChemistryVerificationMode.FORMULA,
            status = ChemistryVerificationStatus.VALID,
            title = "化学式结构有效",
            normalized = formula.canonicalText(),
            rows = buildList {
                addAll(counts)
                add("净电荷" to signedCharge(formula.charge))
                mass?.let { add("相对式量" to it) }
            },
            message = "已解析元素、下标、括号、结晶水和电荷。相对式量只在元素教学原子质量可用时计算。",
        )
    }

    private fun verifyEquation(input: String, balance: Boolean): ChemistryVerificationResult {
        require(input.contains("→") || input.contains("->") || input.contains("=") || input.contains("⇌") || input.contains("↔")) {
            "必须同时给出反应物、生成物和反应箭头；系统不会只根据反应物猜测未知产物。"
        }
        val equation = ChemicalEquationParser.parse(input)
        val result = if (balance) ChemicalEquationBalancer.balance(equation) else equation
        val report = result.conservation()
        return ChemistryVerificationResult(
            mode = if (balance) ChemistryVerificationMode.EQUATION_BALANCE else ChemistryVerificationMode.EQUATION_CHECK,
            status = when {
                balance -> ChemistryVerificationStatus.BALANCED_RESULT
                report.balanced -> ChemistryVerificationStatus.BALANCED
                else -> ChemistryVerificationStatus.UNBALANCED
            },
            title = if (balance) "已生成最简整数配平" else if (report.balanced) "方程式守恒" else "方程式不守恒",
            normalized = result.display(),
            rows = buildList {
                report.elementRows.values.forEach { row ->
                    add(row.symbol to "${row.reactantCount} → ${row.productCount}")
                }
                add("总电荷" to "${signedCharge(report.leftCharge)} → ${signedCharge(report.rightCharge)}")
            },
            message = if (balance) {
                "使用元素与电荷守恒的有理数矩阵求得最小正整数系数，并再次复核守恒。"
            } else {
                report.message()
            },
        )
    }

    private fun verifyOrganic(input: String): ChemistryVerificationResult {
        val molecule = OrganicNotationParser.parse(input)
        val groups = FunctionalGroupDetector.detect(molecule)
        return ChemistryVerificationResult(
            mode = ChemistryVerificationMode.ORGANIC_STRUCTURE,
            status = ChemistryVerificationStatus.VALID,
            title = "有机分子图有效",
            normalized = molecule.sourceNotation,
            rows = buildList {
                add("分子式" to molecule.molecularFormula())
                add("形式电荷" to signedCharge(molecule.totalFormalCharge))
                add("原子数" to molecule.atoms.size.toString())
                add("化学键数" to molecule.bonds.size.toString())
                add("官能团" to groups.joinToString { it.type.label }.ifBlank { "未识别到首批官能团" })
            },
            message = "结构按原子—化学键图解析。当前支持分支、环、单双三键、芳香键和简单带电原子。",
        )
    }

    private fun compareOrganic(left: String, right: String): ChemistryVerificationResult {
        require(right.isNotBlank()) { "同分异构比较需要输入两个结构。" }
        val comparison = OrganicIsomerAnalyzer.compare(
            OrganicNotationParser.parse(left),
            OrganicNotationParser.parse(right),
        )
        return ChemistryVerificationResult(
            mode = ChemistryVerificationMode.ORGANIC_ISOMER,
            status = ChemistryVerificationStatus.VALID,
            title = "有机结构比较完成",
            normalized = "$left  ↔  $right",
            rows = listOf(
                "分子式相同" to yesNo(comparison.sameMolecularFormula),
                "连接关系相同" to yesNo(comparison.sameConnectivity),
                "构造异构体" to yesNo(comparison.constitutionalIsomers),
            ),
            message = comparison.message,
        )
    }

    private fun signedCharge(value: Int): String = when {
        value > 0 -> "+$value"
        else -> value.toString()
    }

    private fun yesNo(value: Boolean): String = if (value) "是" else "否"
}

private fun BigDecimal.cleanText(): String = stripTrailingZeros().toPlainString()
