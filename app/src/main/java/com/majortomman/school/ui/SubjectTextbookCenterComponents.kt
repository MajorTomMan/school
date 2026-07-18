package com.majortomman.school.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CenterBlack = Color(0xFF050608)
private val CenterWhite = Color(0xFFF5F7FA)
private val CenterBlue = Color(0xFF2D7BFF)
private val CenterMuted = CenterWhite.copy(alpha = 0.46f)
private val CenterLine = CenterWhite.copy(alpha = 0.13f)

@Composable
fun NoActiveTextbookScreen(onOpenSubjects: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(CenterBlack).systemBarsPadding().padding(26.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("先选择教材", color = CenterWhite, fontSize = 42.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Text("课程与教材 PDF 会从云端课程包生成。", color = CenterMuted, fontSize = 18.sp, lineHeight = 27.sp)
        Spacer(Modifier.height(30.dp))
        CenterOutlinedButton("前往学科", CenterBlue, onClick = onOpenSubjects)
    }
}

@Composable
internal fun CenterScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 24.dp),
        content = content,
    )
}

@Composable
internal fun StatusText(installedCount: Int) {
    Text(
        if (installedCount > 0) "$installedCount 本教材" else "暂无缓存",
        color = if (installedCount > 0) CenterBlue else CenterMuted,
        fontSize = 13.sp,
    )
}

@Composable
internal fun CenterBack(label: String, onClick: () -> Unit) {
    Text(
        text = "‹  $label",
        modifier = Modifier.clickable(onClick = onClick),
        color = CenterWhite.copy(alpha = 0.72f),
        fontSize = 15.sp,
    )
}

@Composable
internal fun SlotButton(
    label: String,
    status: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(82.dp)
            .border(1.dp, color.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = CenterWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
        Text(status, color = color, fontSize = 12.sp)
    }
}

@Composable
internal fun CenterOutlinedButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = modifier
            .height(48.dp)
            .border(1.dp, color, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        color = color,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
internal fun ThinDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CenterLine))
}
