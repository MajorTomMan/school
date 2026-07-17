package com.majortomman.school.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.majortomman.school.data.Lesson
import com.majortomman.school.data.material.InstalledMaterialPack

internal val InteractiveBlack = Color(0xFF050608)
internal val InteractivePanel = Color(0xFF0D1015)
internal val InteractiveWhite = Color(0xFFF5F7FA)
internal val InteractiveMuted = InteractiveWhite.copy(alpha = 0.52f)
internal val InteractiveLine = InteractiveWhite.copy(alpha = 0.12f)
internal val InteractiveBlue = Color(0xFF58C4DD)
internal val InteractiveYellow = Color(0xFFF4D35E)
internal val InteractiveGreen = Color(0xFF83C167)
internal val InteractiveRed = Color(0xFFFC6255)
internal val InteractivePurple = Color(0xFF9A72AC)

@Composable
fun InteractiveLessonScreen(
    lesson: Lesson,
    spec: InteractiveLessonSpec,
    installedMaterial: InstalledMaterialPack,
    nextLessonTitle: String?,
    onOpenTextbook: (Int) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    var sourceExpanded by rememberSaveable(lesson.id) { mutableStateOf(true) }
    val pageLabel = if (spec.sourcePage == spec.sourcePageEnd) {
        "第 ${spec.sourcePage} 页"
    } else {
        "第 ${spec.sourcePage}—${spec.sourcePageEnd} 页"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(InteractiveBlack)
            .systemBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction("返回", InteractiveMuted, onBack)
            Text("交互课程", color = InteractiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("V1.2", color = InteractiveMuted, fontSize = 13.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Text(spec.badge, color = InteractiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                spec.title,
                color = InteractiveWhite,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(spec.subtitle, color = InteractiveMuted, fontSize = 17.sp, lineHeight = 25.sp)
            Spacer(Modifier.height(26.dp))

            FormulaHero(spec.formula)
            Spacer(Modifier.height(22.dp))

            SourceSummaryCard(
                title = installedMaterial.manifest.title,
                pageLabel = pageLabel,
                summary = spec.sourceSummary,
                expanded = sourceExpanded,
                onToggle = { sourceExpanded = !sourceExpanded },
                onOpenTextbook = { onOpenTextbook(spec.sourcePage) },
                sourceAvailable = installedMaterial.pdfFile.isFile,
            )
            Spacer(Modifier.height(28.dp))

            when (spec.kind) {
                InteractiveLessonKind.LINEAR_FUNCTION -> {
                    LinearFunctionLab(lesson.id)
                    Spacer(Modifier.height(34.dp))
                    LinearCoordinateValidationLab(lesson.id)
                    Spacer(Modifier.height(34.dp))
                    EquationVerificationLab(lesson.id)
                }
                InteractiveLessonKind.NEWTON_FIRST_LAW -> NewtonFirstLawLab(lesson.id)
            }

            Spacer(Modifier.height(34.dp))
            SectionTitle(spec.derivationTitle, InteractiveYellow)
            Spacer(Modifier.height(14.dp))
            DerivationSteps(spec.derivationSteps)

            Spacer(Modifier.height(34.dp))
            SectionTitle("背景知识", InteractivePurple)
            Spacer(Modifier.height(12.dp))
            spec.background.forEach { paragraph ->
                Text(
                    paragraph,
                    modifier = Modifier.padding(bottom = 16.dp),
                    color = InteractiveWhite.copy(alpha = 0.78f),
                    fontSize = 17.sp,
                    lineHeight = 27.sp,
                )
            }

            MisconceptionCard(spec.misconception)
            Spacer(Modifier.height(38.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(InteractiveBlack)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InteractiveAction(
                label = "教材原页",
                color = InteractiveYellow,
                modifier = Modifier.weight(1f),
                enabled = installedMaterial.pdfFile.isFile,
            ) { onOpenTextbook(spec.sourcePage) }
            InteractiveAction(
                label = nextLessonTitle?.let { "完成并继续" } ?: "完成课程",
                color = InteractiveBlue,
                modifier = Modifier.weight(1f),
                onClick = onComplete,
            )
        }
    }
}

@Composable
private fun FormulaHero(formula: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractivePanel, RoundedCornerShape(18.dp))
            .border(1.dp, InteractiveLine, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            formula,
            color = InteractiveYellow,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SourceSummaryCard(
    title: String,
    pageLabel: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenTextbook: () -> Unit,
    sourceAvailable: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractivePanel, RoundedCornerShape(16.dp))
            .border(1.dp, InteractiveBlue.copy(alpha = 0.36f), RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("教材内容依据", color = InteractiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("$title · $pageLabel", color = InteractiveMuted, fontSize = 13.sp)
            }
            Text(if (expanded) "收起" else "展开", color = InteractiveMuted, fontSize = 12.sp)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220)) + expandVertically(tween(260)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(210)),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
                Spacer(Modifier.height(16.dp))
                Text(summary, color = InteractiveWhite, fontSize = 18.sp, lineHeight = 29.sp)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (sourceAvailable) "此处为移动端整理内容，点击下方按钮可核对教材原页。" else "尚未绑定 PDF；绑定后可直接核对教材原页。",
                    color = InteractiveMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
                if (sourceAvailable) {
                    Spacer(Modifier.height(14.dp))
                    TextAction("打开教材原页 →", InteractiveYellow, onOpenTextbook)
                }
            }
        }
    }
}

@Composable
private fun DerivationSteps(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("%02d".format(index + 1), color = InteractiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    step,
                    modifier = Modifier.weight(1f),
                    color = InteractiveWhite.copy(alpha = 0.82f),
                    fontSize = 17.sp,
                    lineHeight = 27.sp,
                )
            }
            if (index != steps.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
        }
    }
}

@Composable
private fun MisconceptionCard(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InteractiveRed.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .border(1.dp, InteractiveRed.copy(alpha = 0.44f), RoundedCornerShape(16.dp))
            .padding(18.dp),
    ) {
        Text("常见误区", color = InteractiveRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(text, color = InteractiveWhite, fontSize = 17.sp, lineHeight = 27.sp)
    }
}

@Composable
internal fun SectionTitle(title: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.height(3.dp).weight(0.12f).background(color, RoundedCornerShape(10.dp)))
        Text(title, modifier = Modifier.weight(0.88f), color = InteractiveWhite, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TextAction(label: String, color: Color, onClick: () -> Unit) {
    Text(label, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp), color = color, fontSize = 14.sp)
}

@Composable
internal fun InteractiveAction(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .border(1.dp, if (enabled) color.copy(alpha = 0.85f) else InteractiveLine, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) color else InteractiveMuted.copy(alpha = 0.45f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
