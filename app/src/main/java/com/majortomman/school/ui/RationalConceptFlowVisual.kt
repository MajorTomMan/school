package com.majortomman.school.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class RationalFormExample(
    val id: String,
    val tabLabel: String,
    val display: String,
    val sourceKind: String,
    val numerator: String,
    val denominator: String,
)

private val integerFractionExamples = listOf(
    RationalFormExample("positive_integer", "2", "2", "正整数", "2", "1"),
    RationalFormExample("negative_integer", "−3", "−3", "负整数", "−3", "1"),
    RationalFormExample("zero", "0", "0", "0", "0", "1"),
)

private val rationalDefinitionExamples = integerFractionExamples + listOf(
    RationalFormExample("finite_decimal", "0.5", "0.5", "有限小数", "1", "2"),
    RationalFormExample("repeating_decimal", "0.3循环", "0.3（3循环）", "无限循环小数", "1", "3"),
)

/**
 * “有理数的概念”原创交互。
 *
 * 不用分类卡片或教材截图。学习者点选一种写法，沿着竖直关系观察它如何变为分数形式，
 * 再落到“有理数”这个共同概念上。
 */
@Composable
internal fun RationalConceptFlowVisual(params: Map<String, String> = emptyMap()) {
    val definitionMode = params["mode"] == "definition"
    val examples = if (definitionMode) rationalDefinitionExamples else integerFractionExamples
    var selectedId by rememberSaveable(params["mode"]) {
        mutableStateOf(examples.first().id)
    }
    val selected = examples.firstOrNull { it.id == selectedId } ?: examples.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            examples.forEach { example ->
                val active = example.id == selected.id
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedId = example.id }
                        .padding(horizontal = 2.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = example.tabLabel,
                        color = if (active) InteractiveWhite else InteractiveMuted,
                        fontSize = if (definitionMode) 12.sp else 15.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (active) InteractiveBlue else Color.Transparent),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = selected.sourceKind,
                color = InteractiveMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = selected.display,
                color = InteractiveWhite,
                fontSize = if (selected.id == "repeating_decimal") 25.sp else 34.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "↓  写成分数形式",
                color = InteractiveBlue,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            FractionForm(
                numerator = selected.numerator,
                denominator = selected.denominator,
            )
            if (definitionMode) {
                Text(
                    text = "↓",
                    color = InteractiveBlue,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
                Text(
                    text = "有理数",
                    color = InteractiveYellow,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Text(
            text = if (definitionMode) {
                "${selected.display}可以写成${selected.numerator}/${selected.denominator}，所以它属于有理数。"
            } else {
                "给${selected.display}补上分母1，数的大小没有改变。"
            },
            modifier = Modifier.fillMaxWidth(),
            color = InteractiveWhite.copy(alpha = 0.82f),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        if (definitionMode) {
            Text(
                text = "共同形式：a/b（a、b为整数，b≠0）",
                modifier = Modifier.fillMaxWidth(),
                color = InteractiveMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FractionForm(
    numerator: String,
    denominator: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = numerator,
            color = InteractiveYellow,
            fontSize = 27.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            Modifier
                .width(72.dp)
                .height(2.dp)
                .background(InteractiveYellow),
        )
        Text(
            text = denominator,
            color = InteractiveYellow,
            fontSize = 27.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
