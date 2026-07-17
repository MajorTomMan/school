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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class LabSample(val label: String, val subtitle: String) {
    COMPLEX("复平面", "z=a+bi"),
    SPACE("三维坐标", "P(x,y,z)"),
    CHEMISTRY("化学配平", "左侧输入 → 右侧自动生成"),
    BIOLOGY("细胞标注", "植物细胞示意图"),
    ENGLISH("英语", "词形 · 语序 · 等价表达"),
    JAPANESE("日语", "读音 · 助词 · 活用"),
    SCIENCE_CORE("科学内核", "根号 · π · 单位 · 量纲"),
    CHEMISTRY_CORE("化学内核", "化学式 · 离子 · 通用配平"),
    ORGANIC_CORE("有机化学", "分子图 · 官能团 · 异构"),
}

@Composable
internal fun InteractionLabScreen() {
    var selectedName by rememberSaveable { mutableStateOf(LabSample.COMPLEX.name) }
    val selected = LabSample.valueOf(selectedName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InteractiveBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text("交互能力实验室", color = InteractiveWhite, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "独立技术样板，不属于任何教材正文。课程只有在章节权限允许时才会调用对应能力。",
            color = InteractiveMuted,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        )
        Spacer(Modifier.height(22.dp))

        LabSample.entries.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                rowItems.forEach { item ->
                    LabChoice(item, item == selected, Modifier.weight(1f)) { selectedName = item.name }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(28.dp))

        when (selected) {
            LabSample.COMPLEX -> ComplexPlaneLabSample()
            LabSample.SPACE -> Coordinate3DLabSample()
            LabSample.CHEMISTRY -> ChemicalBalanceLabSample()
            LabSample.BIOLOGY -> BiologyCellLabSample()
            LabSample.ENGLISH -> EnglishLanguageLabSample()
            LabSample.JAPANESE -> JapaneseLanguageLabSample()
            LabSample.SCIENCE_CORE -> ScienceCoreLabSample()
            LabSample.CHEMISTRY_CORE -> ChemistryCoreLabSample()
            LabSample.ORGANIC_CORE -> OrganicChemistryLabSample()
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun LabChoice(item: LabSample, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            item.label,
            color = if (selected) InteractiveBlue else InteractiveWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(3.dp))
        Text(item.subtitle, color = InteractiveMuted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (selected) 2.dp else 1.dp)
                .background(if (selected) InteractiveBlue else InteractiveLine),
        )
    }
}
