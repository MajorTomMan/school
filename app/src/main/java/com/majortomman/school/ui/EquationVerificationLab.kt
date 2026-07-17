package com.majortomman.school.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EquationVerificationLab(lessonId: String) {
    var equationText by rememberSaveable(lessonId, "equation-text") { mutableStateOf("y=2x") }
    var valueTexts by remember { mutableStateOf<Map<String, String>>(mapOf("x" to "3", "y" to "6")) }
    val variables = EquationVerificationEngine.variablesOf(equationText).take(8)

    LaunchedEffect(variables) {
        valueTexts = variables.associateWith { variable -> valueTexts[variable].orEmpty() }
    }

    val numericValues = variables.mapNotNull { variable ->
        valueTexts[variable]?.toDoubleOrNull()?.let { variable to it }
    }.toMap()
    val result = EquationVerificationEngine.verify(equationText, numericValues)

    SectionTitle("验证公式或方程", InteractiveYellow)
    Spacer(Modifier.height(12.dp))
    Text(
        "输入一个式子，再填写式子中出现的量。应用会依次显示代入结果、等号左边和右边的值，并判断当前等式是否成立。",
        color = InteractiveWhite.copy(alpha = 0.75f),
        fontSize = 16.sp,
        lineHeight = 25.sp,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "本页只处理数字、字母、括号以及加、减、乘、除。它用于检查计算，不替代教材中的书写步骤。",
        color = InteractiveMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    Spacer(Modifier.height(16.dp))

    VerificationInput(
        label = "输入公式或方程",
        value = equationText,
        color = InteractiveYellow,
        placeholder = "例如：y=2x 或 z=x+y",
        onValueChange = { next -> if (next.length <= 120) equationText = next },
    )

    if (variables.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(
            "需要填写：${variables.joinToString("、")}",
            color = InteractiveMuted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(10.dp))
        variables.chunked(2).forEachIndexed { rowIndex, rowVariables ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowVariables.forEach { variable ->
                    VerificationInput(
                        label = variable,
                        value = valueTexts[variable].orEmpty(),
                        color = if (variable == "x") InteractiveBlue else InteractivePurple,
                        placeholder = "输入 $variable",
                        modifier = Modifier.weight(1f),
                        onValueChange = { next ->
                            if (isVerificationNumberDraft(next)) {
                                valueTexts = valueTexts + (variable to next)
                            }
                        },
                    )
                }
                if (rowVariables.size == 1) Spacer(Modifier.weight(1f))
            }
            if (rowIndex != variables.chunked(2).lastIndex) Spacer(Modifier.height(10.dp))
        }
    }

    Spacer(Modifier.height(16.dp))
    VerificationTraceCard(result)
}

@Composable
private fun VerificationTraceCard(result: EquationVerificationResult) {
    val borderColor = when {
        result.error != null -> InteractiveRed
        result.isCorrect == true -> InteractiveGreen
        result.isCorrect == false -> InteractiveRed
        else -> InteractiveBlue
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(borderColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, borderColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("检查过程", color = borderColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (result.error != null) {
            Text(result.error, color = InteractiveWhite, fontSize = 16.sp, lineHeight = 24.sp)
            return@Column
        }

        Text("原式：${result.original}", color = InteractiveWhite, fontSize = 16.sp, lineHeight = 24.sp)
        result.substitutedLeft?.let { left ->
            if (result.substitutedRight != null) {
                Text("代入：$left = ${result.substitutedRight}", color = InteractiveMuted, fontSize = 15.sp, lineHeight = 23.sp)
            } else {
                Text("代入：$left", color = InteractiveMuted, fontSize = 15.sp, lineHeight = 23.sp)
            }
        }
        result.leftValue?.let { leftValue ->
            Text(
                if (result.rightValue == null) {
                    "计算结果：${formatEquationNumber(leftValue)}"
                } else {
                    "左边：${formatEquationNumber(leftValue)}"
                },
                color = InteractiveYellow,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        result.rightValue?.let { rightValue ->
            Text(
                "右边：${formatEquationNumber(rightValue)}",
                color = InteractiveBlue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        when (result.isCorrect) {
            true -> Text("验证通过：等号两边的值相同。", color = InteractiveGreen, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            false -> Text("验证未通过：等号两边的值不同。", color = InteractiveRed, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            null -> if (result.leftValue != null) {
                Text("这是一个计算式，上方显示的是它的值。", color = InteractiveGreen, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun VerificationInput(
    label: String,
    value: String,
    color: Color,
    placeholder: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .background(InteractivePanel, RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(label, color = InteractiveMuted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = InteractiveWhite, fontSize = 21.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(color),
            singleLine = true,
            decorationBox = { inner ->
                if (value.isBlank()) Text(placeholder, color = InteractiveMuted.copy(alpha = 0.55f), fontSize = 17.sp)
                inner()
            },
        )
    }
}

private fun isVerificationNumberDraft(value: String): Boolean =
    value.length <= 16 && value.matches(Regex("-?\\d*(\\.\\d*)?"))
