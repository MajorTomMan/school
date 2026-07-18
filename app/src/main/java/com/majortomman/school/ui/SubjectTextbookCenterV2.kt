package com.majortomman.school.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.majortomman.school.data.material.EducationStage
import com.majortomman.school.data.material.InstalledTextbook
import com.majortomman.school.data.material.MaterialLibraryState
import com.majortomman.school.data.material.SubjectTemplates
import com.majortomman.school.data.material.TextbookSlot

private val CenterBlack = Color(0xFF050608)

private enum class CenterPage {
    STAGES,
    SUBJECTS,
    GRADES,
    SLOT,
}

@Composable
fun SubjectTextbookCenterScreen(
    libraryState: MaterialLibraryState,
    onEnterCourse: (InstalledTextbook) -> Unit,
    onOpenTextbook: (InstalledTextbook, Int) -> Unit,
) {
    var pageName by rememberSaveable { mutableStateOf(CenterPage.STAGES.name) }
    var selectedStageId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSlotKey by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedStage = selectedStageId?.let(EducationStage::fromId)
    val selectedSubject = selectedSubjectId?.let(SubjectTemplates::find)
    val selectedSlot = selectedSlotKey?.let(TextbookSlot::fromKey)

    AnimatedContent(
        targetState = CenterPage.valueOf(pageName),
        modifier = Modifier.fillMaxSize().background(CenterBlack),
        transitionSpec = {
            (fadeIn(tween(260)) + slideInHorizontally(tween(360)) { it / 8 }) togetherWith
                (fadeOut(tween(150)) + slideOutHorizontally(tween(260)) { -it / 10 })
        },
        label = "subjectCenterNavigation",
    ) { page ->
        when (page) {
            CenterPage.STAGES -> StageListPage(libraryState) { stage ->
                selectedStageId = stage.id
                selectedSubjectId = null
                selectedSlotKey = null
                pageName = CenterPage.SUBJECTS.name
            }

            CenterPage.SUBJECTS -> if (selectedStage == null) {
                pageName = CenterPage.STAGES.name
            } else {
                SubjectListPage(
                    stage = selectedStage,
                    libraryState = libraryState,
                    onBack = { pageName = CenterPage.STAGES.name },
                    onSelect = { subject ->
                        selectedSubjectId = subject.id
                        selectedSlotKey = null
                        pageName = CenterPage.GRADES.name
                    },
                )
            }

            CenterPage.GRADES -> if (selectedStage == null || selectedSubject == null) {
                pageName = CenterPage.SUBJECTS.name
            } else {
                GradeListPage(
                    stage = selectedStage,
                    subject = selectedSubject,
                    libraryState = libraryState,
                    onBack = { pageName = CenterPage.SUBJECTS.name },
                    onSelect = { slot ->
                        selectedSlotKey = slot.key
                        pageName = CenterPage.SLOT.name
                    },
                )
            }

            CenterPage.SLOT -> if (selectedSlot == null) {
                pageName = CenterPage.GRADES.name
            } else {
                SlotPage(
                    slot = selectedSlot,
                    installed = libraryState.installed(selectedSlot),
                    onBack = { pageName = CenterPage.GRADES.name },
                    onEnterCourse = onEnterCourse,
                    onOpenTextbook = onOpenTextbook,
                )
            }
        }
    }
}
