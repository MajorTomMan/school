package com.majortomman.school.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.science.chemistry.ChemicalEquationBalancer
import com.majortomman.school.learning.science.chemistry.ChemicalEquationParser
import com.majortomman.school.learning.science.chemistry.ChemicalFormulaParser

@Composable
internal fun ChemistryCoreLabSample() {
    var formulaText by rememberSaveable { mutableStateOf("Al2(SO4)3") }
    var equationText by rememberSaveable { mutableStateOf("Fe + O2 -> Fe2O3") }
    val formulaResult = runCatching { ChemicalFormulaParser.parse(formulaText) }
    val equationResult = runCatching {
        ChemicalEquationBalancer.balance(ChemicalEquationParser.parse(equationText))
    }

    SectionTitle("通用化学内核", InteractiveGreen)
    Spacer(Modifier.height(12.dp))
    Text(
        "化学式和方程式现在使用真实语义结构，不再依赖水反应的固定字段。支持元素、括号、下标、结晶水、离子电荷、状态、守恒和矩阵配平。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(24.dp))

    ChemistrySubtitle("化学式解析", InteractiveBlue)
    Spacer(Modifier.height(10.dp))
    ChemistryInput(formulaText, InteractiveBlue) { formulaText = it.take(100) }
    Spacer(Modifier.height(16.dp))
    formulaResult.fold(
        onSuccess = { formula ->
            ChemistryLine("规范化", formula.canonicalText())
            ChemistryLine(
                "原子组成",
                formula.atomCounts.entries.joinToString(" · ") { "${it.key.symbol} ${it.value}" },
            )
            ChemistryLine("总电荷", if (formula.charge == 0) "0" else "%+d".format(formula.charge))
            ChemistryLine("相对式量", formula.relativeMolecularMass().stripTrailingZeros().toPlainString())
        },
        onFailure = { ChemistryError(it.message ?: "无法解析化学式。") },
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "可试：Ca(OH)2、Al2(SO4)3、CuSO4·5H2O、SO4^2-。",
        color = InteractiveMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )

    Spacer(Modifier.height(30.dp))
    ChemistrySubtitle("方程式矩阵配平", InteractiveGreen)
    Spacer(Modifier.height(10.dp))
    ChemistryInput(equationText, InteractiveGreen) { equationText = it.take(240) }
    Spacer(Modifier.height(16.dp))
    equationResult.fold(
        onSuccess = { equation ->
            ChemistryLine("最简整数比", equation.display())
            equation.conservation().elementRows.values.forEach { row ->
                ChemistryLine(row.symbol, "${row.reactantCount} → ${row.productCount}")
            }
            ChemistryLine(
                "电荷",
                "${equation.conservation().leftCharge} → ${equation.conservation().rightCharge}",
            )
            Text(
                equation.conservation().message(),
                color = InteractiveGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        onFailure = { ChemistryError(it.message ?: "无法配平方程式。") },
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "可试：H2 + O2 -> H2O；KMnO4 + HCl -> KCl + MnCl2 + H2O + Cl2。离子电荷使用 ^2- 或 ^+。",
        color = InteractiveMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "底层同时提供摩尔质量、质量与物质的量换算、浓度、稀释、限量试剂、理论产率和旁观离子约去。反应产物仍由教材或反应规则给出，守恒本身不能可靠预测未知产物。",
        color = InteractiveWhite.copy(alpha = 0.76f),
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
}

@Composable
private fun ChemistrySubtitle(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ChemistryInput(
    value: String,
    color: androidx.compose.ui.graphics.Color,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(color = InteractiveWhite, fontSize = 20.sp, lineHeight = 28.sp),
        cursorBrush = SolidColor(color),
    )
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
}

@Composable
private fun ChemistryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = InteractiveMuted, fontSize = 13.sp, modifier = Modifier.weight(0.3f))
        Text(
            value,
            color = InteractiveWhite,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.7f),
        )
    }
}

@Composable
private fun ChemistryError(message: String) {
    Text(message, color = InteractiveRed, fontSize = 14.sp, lineHeight = 21.sp)
}
