package com.majortomman.school.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.material.EducationStage
import com.majortomman.school.data.material.InstalledTextbook
import com.majortomman.school.data.material.MaterialLibraryState
import com.majortomman.school.data.material.SubjectTemplate
import com.majortomman.school.data.material.SubjectTemplates
import com.majortomman.school.data.material.TextbookSlot
import com.majortomman.school.data.material.TextbookVolume
import com.majortomman.school.data.material.gradeLabel

private val SelectionWhite = Color(0xFFF5F7FA)
private val SelectionBlue = Color(0xFF2D7BFF)
private val SelectionMuted = SelectionWhite.copy(alpha = 0.46f)

@Composable
internal fun StageListPage(
    libraryState: MaterialLibraryState,
    onSelect: (EducationStage) -> Unit,
) {
    CenterScrollPage {
        Text("学习阶段", color = SelectionWhite, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("课程、教材和题库由已安装课程包提供。", color = SelectionMuted, lineHeight = 23.sp)
        Spacer(Modifier.height(42.dp))
        EducationStage.entries.forEachIndexed { index, stage ->
            val installedCount = libraryState.installedTextbooks.count { it.slot.stage == stage }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(stage) }.padding(vertical = 21.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stage.label, color = SelectionWhite, fontSize = 31.sp, fontWeight = FontWeight.Medium)
                StatusText(installedCount)
            }
            if (index != EducationStage.entries.lastIndex) ThinDivider()
        }
    }
}

@Composable
internal fun SubjectListPage(
    stage: EducationStage,
    libraryState: MaterialLibraryState,
    onBack: () -> Unit,
    onSelect: (SubjectTemplate) -> Unit,
) {
    CenterScrollPage {
        CenterBack("学习阶段", onBack)
        Spacer(Modifier.height(30.dp))
        Text(stage.label, color = SelectionWhite, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("选择学科", color = SelectionMuted)
        Spacer(Modifier.height(38.dp))
        val subjects = SubjectTemplates.forStage(stage)
        subjects.forEachIndexed { index, subject ->
            val installedCount = libraryState.installedTextbooks.count {
                it.slot.stage == stage && it.slot.subjectId == subject.id
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(subject) }.padding(vertical = 19.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(subject.title, color = SelectionWhite, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                StatusText(installedCount)
            }
            if (index != subjects.lastIndex) ThinDivider()
        }
    }
}

@Composable
internal fun GradeListPage(
    stage: EducationStage,
    subject: SubjectTemplate,
    libraryState: MaterialLibraryState,
    onBack: () -> Unit,
    onSelect: (TextbookSlot) -> Unit,
) {
    val specialistSeniorSubject = stage == EducationStage.SENIOR_HIGH && subject.id in setOf(
        "chinese", "english", "japanese", "physics", "chemistry",
    )
    val grades = if (specialistSeniorSubject) listOf(10) else subject.gradesFor(stage).toList()
    val volumes = TextbookVolume.optionsFor(stage, subject.id)
    CenterScrollPage {
        CenterBack(stage.label, onBack)
        Spacer(Modifier.height(30.dp))
        Text(subject.title, color = SelectionWhite, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(if (specialistSeniorSubject) "选择教材卷册" else "选择年级和册次", color = SelectionMuted)
        Spacer(Modifier.height(38.dp))
        grades.forEachIndexed { gradeIndex, grade ->
            Text(
                if (specialistSeniorSubject) "高中教材" else gradeLabel(grade),
                color = SelectionWhite,
                fontSize = 23.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(14.dp))
            volumes.chunked(2).forEach { rowVolumes ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowVolumes.forEach { volume ->
                        val slot = TextbookSlot(subject.id, subject.title, grade, volume, stage)
                        val installed = libraryState.installed(slot)
                        SlotButton(
                            label = volume.labelFor(stage),
                            status = if (installed != null) "已下载" else "尚未安装",
                            color = if (installed != null) SelectionBlue else SelectionWhite.copy(alpha = 0.42f),
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(slot) },
                        )
                    }
                    if (rowVolumes.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(17.dp))
            if (gradeIndex != grades.lastIndex) ThinDivider()
            Spacer(Modifier.height(17.dp))
        }
    }
}

@Composable
internal fun SlotPage(
    slot: TextbookSlot,
    installed: InstalledTextbook?,
    onBack: () -> Unit,
    onEnterCourse: (InstalledTextbook) -> Unit,
    onOpenTextbook: (InstalledTextbook, Int) -> Unit,
) {
    CenterScrollPage {
        CenterBack(slot.subjectTitle, onBack)
        Spacer(Modifier.height(46.dp))
        Text(
            installed?.pack?.manifest?.title ?: slot.displayTitle,
            color = SelectionWhite,
            fontSize = 42.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text("${slot.stage.label} · ${slot.volumeLabel}", color = SelectionMuted, fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))

        if (installed != null) {
            Text(
                "课程与教材已缓存",
                color = SelectionBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text("${installed.lessons.size} 个课程 · ${installed.pageCount} 页教材", color = SelectionMuted)
            Spacer(Modifier.height(30.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CenterOutlinedButton(
                    label = "查看教材",
                    color = SelectionWhite.copy(alpha = 0.76f),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onOpenTextbook(installed, installed.lessons.firstOrNull()?.pageStart ?: 1)
                    },
                )
                CenterOutlinedButton(
                    label = "进入课程",
                    color = SelectionBlue,
                    modifier = Modifier.weight(1f),
                    onClick = { onEnterCourse(installed) },
                )
            }
        } else {
            Text("该教材尚未安装。", color = SelectionMuted, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "配置课程源后，应用可以检查并下载课程包；未配置时仅显示设备上已经安装并通过校验的课程。",
                color = SelectionWhite.copy(alpha = 0.34f),
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
        }
    }
}
