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
import com.majortomman.school.learning.verification.VerificationHubCatalog
import com.majortomman.school.learning.verification.VerificationSubject

@Composable
internal fun VerificationHubScreen() {
    var selectedName by rememberSaveable { mutableStateOf(VerificationSubject.MATHEMATICS.name) }
    val selected = VerificationSubject.valueOf(selectedName)
    val capability = VerificationHubCatalog.capability(selected)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InteractiveBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text("验证", color = InteractiveWhite, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "选择学科，输入自己的公式、模型、方程式或句子。系统会展示结构、参数、可视化结果和可解释诊断。",
            color = InteractiveMuted,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        )
        Spacer(Modifier.height(22.dp))

        VerificationHubCatalog.subjects.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                rowItems.forEach { subject ->
                    VerificationSubjectChoice(
                        subject = subject,
                        selected = subject == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedName = subject.name },
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(14.dp))
        VerificationBoundary(
            deterministic = capability.deterministic,
            limitation = capability.limitation,
        )
        Spacer(Modifier.height(30.dp))

        when (selected) {
            VerificationSubject.MATHEMATICS -> MathematicsVerificationPanel()
            VerificationSubject.PHYSICS -> PhysicsVerificationPanel()
            VerificationSubject.CHEMISTRY -> ChemistryVerificationPanel()
            VerificationSubject.BIOLOGY -> BiologyVerificationPanel()
            VerificationSubject.ENGLISH -> LanguageVerificationPanel(english = true)
            VerificationSubject.JAPANESE -> LanguageVerificationPanel(english = false)
        }
        Spacer(Modifier.height(52.dp))
    }
}

@Composable
private fun VerificationSubjectChoice(
    subject: VerificationSubject,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            subject.label,
            color = if (selected) InteractiveBlue else InteractiveWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(subject.subtitle, color = InteractiveMuted, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (selected) 2.dp else 1.dp)
                .background(if (selected) InteractiveBlue else InteractiveLine),
        )
    }
}

@Composable
private fun VerificationBoundary(deterministic: Boolean, limitation: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("判断边界", color = InteractivePurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                if (deterministic) "确定性计算" else "规则分析",
                color = if (deterministic) InteractiveGreen else InteractiveYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(limitation, color = InteractiveWhite.copy(alpha = 0.72f), fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
    }
}
