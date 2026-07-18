package com.majortomman.school.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class VerificationChoiceItem(
    val id: String,
    val label: String,
    val subtitle: String = "",
)

@Composable
internal fun VerificationChoiceGrid(
    items: List<VerificationChoiceItem>,
    selectedId: String,
    columns: Int = 2,
    onSelect: (String) -> Unit,
) {
    items.chunked(columns).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            rowItems.forEach { item ->
                val selected = item.id == selectedId
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(item.id) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        item.label,
                        color = if (selected) InteractiveBlue else InteractiveWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(item.subtitle, color = InteractiveMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (selected) 2.dp else 1.dp)
                            .background(if (selected) InteractiveBlue else InteractiveLine),
                    )
                }
            }
            repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
internal fun VerificationTextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    maxLength: Int = 500,
    accent: Color = InteractiveBlue,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = InteractiveMuted, fontSize = 12.sp)
        Spacer(Modifier.height(7.dp))
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            textStyle = TextStyle(color = InteractiveWhite, fontSize = 18.sp, lineHeight = 26.sp),
            cursorBrush = SolidColor(accent),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isBlank() && hint.isNotBlank()) {
                        Text(hint, color = InteractiveMuted.copy(alpha = 0.55f), fontSize = 17.sp)
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
    }
}

@Composable
internal fun VerificationNumberInput(
    label: String,
    value: String,
    unit: String = "",
    onValueChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = InteractiveMuted, fontSize = 12.sp)
            if (unit.isNotBlank()) Text(unit, color = InteractiveBlue, fontSize = 12.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = { changed ->
                if (changed.matches(Regex("-?\\d*(?:\\.\\d*)?"))) onValueChange(changed.take(20))
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            textStyle = TextStyle(color = InteractiveWhite, fontSize = 18.sp),
            cursorBrush = SolidColor(InteractiveBlue),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
    }
}

@Composable
internal fun VerificationToggle(
    label: String,
    checked: Boolean,
    description: String = "",
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = InteractiveWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(description, color = InteractiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Text(
            if (checked) "已确认" else "未确认",
            color = if (checked) InteractiveGreen else InteractiveYellow,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun VerificationStatusBlock(
    title: String,
    message: String,
    color: Color,
    normalized: String? = null,
    rows: List<Pair<String, String>> = emptyList(),
    steps: List<String> = emptyList(),
) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(color))
        Spacer(Modifier.height(12.dp))
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        normalized?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = InteractiveWhite, fontSize = 20.sp, lineHeight = 28.sp)
        }
        if (rows.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, color = InteractiveMuted, fontSize = 13.sp)
                    Text(value, color = InteractiveWhite, fontSize = 14.sp, textAlign = TextAlign.End)
                }
            }
        }
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("%02d".format(index + 1), color = InteractiveBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(step, modifier = Modifier.weight(1f), color = InteractiveWhite.copy(alpha = 0.78f), fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(message, color = InteractiveWhite.copy(alpha = 0.76f), fontSize = 14.sp, lineHeight = 22.sp)
    }
}
