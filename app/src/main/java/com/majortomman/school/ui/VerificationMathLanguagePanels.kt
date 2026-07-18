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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.language.FreeLanguage
import com.majortomman.school.learning.language.FreeSentenceAnalyzer
import com.majortomman.school.learning.language.SentenceJudgement
import com.majortomman.school.learning.science.expression.ComplexApprox
import com.majortomman.school.learning.science.math.MathFormulaStatus
import com.majortomman.school.learning.science.math.MathFormulaVerifier
import kotlin.math.abs

private enum class MathematicsVerificationView(
    val label: String,
    val subtitle: String,
) {
    FORMULA("公式", "任意受支持表达式"),
    COMPLEX("复平面", "实部与虚部"),
    SPACE("空间坐标", "三维投影"),
    SCIENCE("精确表达式", "根号 · π · 单位"),
}

@Composable
internal fun MathematicsVerificationPanel() {
    var viewName by rememberSaveable { mutableStateOf(MathematicsVerificationView.FORMULA.name) }
    val view = MathematicsVerificationView.valueOf(viewName)

    SectionTitle("数学验证", InteractiveYellow)
    Spacer(Modifier.height(12.dp))
    VerificationChoiceGrid(
        items = MathematicsVerificationView.entries.map {
            VerificationChoiceItem(it.name, it.label, it.subtitle)
        },
        selectedId = viewName,
        onSelect = { viewName = it },
    )
    Spacer(Modifier.height(18.dp))

    when (view) {
        MathematicsVerificationView.FORMULA -> FreeMathFormulaPanel()
        MathematicsVerificationView.COMPLEX -> {
            Text(
                "输入实部和虚部，点位、模和复平面位置同步变化。这里是原“实验室”复平面样板，现在作为数学验证可视化。",
                color = InteractiveMuted,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(16.dp))
            ComplexPlaneLabSample()
        }
        MathematicsVerificationView.SPACE -> {
            Text(
                "输入空间点坐标并调整视角，验证正交投影和二维/三维表示的一致性。",
                color = InteractiveMuted,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(16.dp))
            Coordinate3DLabSample()
        }
        MathematicsVerificationView.SCIENCE -> {
            Text(
                "验证精确分数、根式、π、单位换算和量纲。结果会区分精确值与近似值。",
                color = InteractiveMuted,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(16.dp))
            ScienceCoreLabSample()
        }
    }
}

@Composable
private fun FreeMathFormulaPanel() {
    var formula by rememberSaveable { mutableStateOf("√72=6√2") }
    var sampleMode by rememberSaveable { mutableStateOf(false) }
    var valueTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val numericValues = valueTexts.mapNotNull { (key, value) -> value.toDoubleOrNull()?.let { key to it } }.toMap()
    val result = remember(formula, numericValues, sampleMode) {
        MathFormulaVerifier.verify(
            input = formula,
            values = numericValues,
            sampleRelation = sampleMode,
        )
    }

    LaunchedEffect(result.variables) {
        valueTexts = result.variables.associateWith { variable -> valueTexts[variable] ?: "1" }
    }

    Text(
        "支持分数、根号、π、e、i、整数幂、隐式乘法、等式和不等式。输入关系式后会自动识别变量。",
        color = InteractiveMuted,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    Spacer(Modifier.height(14.dp))
    VerificationTextInput(
        label = "数学表达式或关系式",
        value = formula,
        onValueChange = { formula = it },
        hint = "例如 (x+1)^2=x^2+2x+1",
        maxLength = 256,
        accent = InteractiveYellow,
    )

    if (result.variables.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        result.variables.chunked(2).forEach { rowVariables ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowVariables.forEach { variable ->
                    Box(Modifier.weight(1f)) {
                        VerificationNumberInput(
                            label = "变量 $variable",
                            value = valueTexts[variable].orEmpty(),
                            onValueChange = { changed -> valueTexts = valueTexts + (variable to changed) },
                        )
                    }
                }
                if (rowVariables.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        VerificationToggle(
            label = "多组安全样本检查",
            checked = sampleMode,
            description = "用于寻找反例；未发现反例不代表已经完成严格证明。",
            onToggle = { sampleMode = !sampleMode },
        )
    }

    Spacer(Modifier.height(18.dp))
    val color = when (result.status) {
        MathFormulaStatus.VALID_EXPRESSION,
        MathFormulaStatus.TRUE_AT_VALUES,
        MathFormulaStatus.SAMPLE_MATCH,
        -> InteractiveGreen
        MathFormulaStatus.FALSE_AT_VALUES,
        MathFormulaStatus.SAMPLE_COUNTEREXAMPLE,
        MathFormulaStatus.UNSUPPORTED,
        -> InteractiveRed
        MathFormulaStatus.NEEDS_VARIABLE_VALUES -> InteractiveYellow
    }
    val normalized = result.normalizedLeft?.let { left ->
        result.normalizedRight?.let { right -> "$left ${result.relation?.symbol.orEmpty()} $right" } ?: left
    }
    VerificationStatusBlock(
        title = mathStatusTitle(result.status),
        message = result.message,
        color = color,
        normalized = normalized,
        rows = buildList {
            result.leftValue?.let { add("左侧/表达式值" to formatComplexVerification(it)) }
            result.rightValue?.let { add("右侧值" to formatComplexVerification(it)) }
            if (result.samples.isNotEmpty()) add("已检查样本" to result.samples.size.toString())
        },
    )
}

@Composable
internal fun LanguageVerificationPanel(english: Boolean) {
    val language = if (english) FreeLanguage.ENGLISH else FreeLanguage.JAPANESE
    val defaultSentence = if (english) "He plays basketball after school." else "私は学校へ行きます。"
    var sentence by rememberSaveable(english) { mutableStateOf(defaultSentence) }
    val analysis = remember(language, sentence) { FreeSentenceAnalyzer.analyze(language, sentence) }

    SectionTitle(if (english) "英语句子验证" else "日语句子验证", InteractiveYellow)
    Spacer(Modifier.height(12.dp))
    Text(
        if (english) {
            "输入任意英语句子，分析主语、谓语、宾语/补语、助动词、修饰语，以及基础词形、语序和一致性问题。"
        } else {
            "输入任意日语句子，分析主题、主语、宾语、助词、谓语和语体，并提示基础助词与活用问题。"
        },
        color = InteractiveMuted,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    Spacer(Modifier.height(14.dp))
    VerificationTextInput(
        label = if (english) "英语句子" else "日语句子",
        value = sentence,
        onValueChange = { sentence = it },
        hint = defaultSentence,
        maxLength = 500,
        accent = InteractiveYellow,
    )

    Spacer(Modifier.height(20.dp))
    SectionTitle("语法结构可视化", InteractiveBlue)
    Spacer(Modifier.height(12.dp))
    if (analysis.segments.isEmpty()) {
        Text("暂未识别到可展示的句子结构。", color = InteractiveMuted, fontSize = 14.sp)
    } else {
        analysis.segments.forEachIndexed { index, segment ->
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(segment.text, color = InteractiveWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(segment.role.label, color = roleColor(index), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(segment.explanation, color = InteractiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(roleColor(index).copy(alpha = 0.66f)))
            }
        }
    }

    Spacer(Modifier.height(22.dp))
    val color = when (analysis.judgement) {
        SentenceJudgement.SUPPORTED_CORRECT -> InteractiveGreen
        SentenceJudgement.SUPPORTED_ISSUES -> InteractiveRed
        SentenceJudgement.AMBIGUOUS -> InteractiveYellow
        SentenceJudgement.UNSUPPORTED -> InteractiveRed
    }
    VerificationStatusBlock(
        title = when (analysis.judgement) {
            SentenceJudgement.SUPPORTED_CORRECT -> "当前规则内未发现明确错误"
            SentenceJudgement.SUPPORTED_ISSUES -> "发现可解释问题"
            SentenceJudgement.AMBIGUOUS -> "结构存在歧义"
            SentenceJudgement.UNSUPPORTED -> "超出当前支持范围"
        },
        normalized = analysis.normalized,
        message = analysis.summary,
        color = color,
        rows = analysis.issues.map { issue ->
            issue.message to issue.suggestion.orEmpty().ifBlank { "请结合教材语境检查" }
        },
    )
    Spacer(Modifier.height(16.dp))
    Text("能力边界", color = InteractivePurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text(analysis.limitation, color = InteractiveMuted, fontSize = 13.sp, lineHeight = 20.sp)
}

private fun mathStatusTitle(status: MathFormulaStatus): String = when (status) {
    MathFormulaStatus.VALID_EXPRESSION -> "表达式有效"
    MathFormulaStatus.NEEDS_VARIABLE_VALUES -> "等待变量取值"
    MathFormulaStatus.TRUE_AT_VALUES -> "当前取值成立"
    MathFormulaStatus.FALSE_AT_VALUES -> "当前取值不成立"
    MathFormulaStatus.SAMPLE_MATCH -> "样本暂未发现反例"
    MathFormulaStatus.SAMPLE_COUNTEREXAMPLE -> "找到反例"
    MathFormulaStatus.UNSUPPORTED -> "无法验证"
}

private fun roleColor(index: Int): Color = when (index % 5) {
    0 -> InteractiveBlue
    1 -> InteractiveYellow
    2 -> InteractiveGreen
    3 -> InteractivePurple
    else -> InteractiveRed
}

private fun formatComplexVerification(value: ComplexApprox): String = when {
    abs(value.imaginary) < 1e-9 -> formatVerificationNumber(value.real)
    abs(value.real) < 1e-9 -> "${formatVerificationNumber(value.imaginary)}i"
    value.imaginary > 0 -> "${formatVerificationNumber(value.real)}+${formatVerificationNumber(value.imaginary)}i"
    else -> "${formatVerificationNumber(value.real)}${formatVerificationNumber(value.imaginary)}i"
}

internal fun formatVerificationNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
    return if (abs(rounded - rounded.toLong()) < 1e-9) rounded.toLong().toString()
    else rounded.toString().trimEnd('0').trimEnd('.')
}
