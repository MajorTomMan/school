package com.majortomman.school.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.data.material.EducationStage
import com.majortomman.school.data.material.InstalledTextbook
import com.majortomman.school.data.material.MaterialLibraryState
import com.majortomman.school.data.material.SubjectTemplate
import com.majortomman.school.data.material.SubjectTemplates
import com.majortomman.school.data.material.TextbookProcessingState
import com.majortomman.school.data.material.TextbookProcessingStatus
import com.majortomman.school.data.material.TextbookSlot
import com.majortomman.school.data.material.TextbookVolume
import com.majortomman.school.data.material.gradeLabel

private val CenterBlack = Color(0xFF050608)
private val CenterWhite = Color(0xFFF5F7FA)
private val CenterBlue = Color(0xFF2D7BFF)
private val CenterRed = Color(0xFFFF453A)
private val CenterYellow = Color(0xFFFFCC00)
private val CenterMuted = CenterWhite.copy(alpha = 0.46f)
private val CenterLine = CenterWhite.copy(alpha = 0.13f)

private enum class CenterPage {
    STAGES,
    SUBJECTS,
    GRADES,
    SLOT,
    BROWSER,
    OCR_LOG,
}

@Composable
fun SubjectTextbookCenterScreen(
    libraryState: MaterialLibraryState,
    completedTutorials: Set<String>,
    onTutorialCompleted: (String, Int) -> Unit,
    onImport: (TextbookSlot, Uri) -> Unit,
    onCancelProcessing: (TextbookSlot) -> Unit,
    onRemove: (TextbookSlot) -> Unit,
    onEnterCourse: (InstalledTextbook) -> Unit,
    onOpenTextbook: (InstalledTextbook, Int) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE") val tutorialCompatibility = completedTutorials to onTutorialCompleted
    var pageName by rememberSaveable { mutableStateOf(CenterPage.STAGES.name) }
    var selectedStageId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSlotKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImportSlotKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingImportUri by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val slot = pendingImportSlotKey?.let(TextbookSlot::fromKey)
        val uri = pendingImportUri?.let(Uri::parse)
        if (slot != null && uri != null) onImport(slot, uri)
        pendingImportSlotKey = null
        pendingImportUri = null
    }

    fun beginImport(slot: TextbookSlot, uri: Uri) {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingImportSlotKey = slot.key
            pendingImportUri = uri.toString()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onImport(slot, uri)
            pendingImportSlotKey = null
            pendingImportUri = null
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pendingImportSlotKey?.let(TextbookSlot::fromKey)
        if (uri != null && slot != null) beginImport(slot, uri)
        else {
            pendingImportSlotKey = null
            pendingImportUri = null
        }
    }

    fun launchSystemImport(slot: TextbookSlot) {
        pendingImportSlotKey = slot.key
        pendingImportUri = null
        documentLauncher.launch(arrayOf("application/pdf"))
    }

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
                        confirmRemove = false
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
                    processing = libraryState.processing(selectedSlot),
                    confirmRemove = confirmRemove,
                    onBack = { pageName = CenterPage.GRADES.name },
                    onImport = { pageName = CenterPage.BROWSER.name },
                    onOpenOcrLog = { pageName = CenterPage.OCR_LOG.name },
                    onCancel = { onCancelProcessing(selectedSlot) },
                    onRemove = {
                        if (confirmRemove) {
                            onRemove(selectedSlot)
                            confirmRemove = false
                        } else {
                            confirmRemove = true
                        }
                    },
                    onEnterCourse = onEnterCourse,
                    onOpenTextbook = onOpenTextbook,
                )
            }

            CenterPage.BROWSER -> if (selectedSlot == null) {
                pageName = CenterPage.GRADES.name
            } else {
                InternalPdfBrowserScreen(
                    slot = selectedSlot,
                    installedTitles = libraryState.installedTextbooks.map { it.pack.manifest.title }.toSet(),
                    onSelect = { uri ->
                        beginImport(selectedSlot, uri)
                        pageName = CenterPage.SLOT.name
                    },
                    onOtherLocation = { launchSystemImport(selectedSlot) },
                    onBack = { pageName = CenterPage.SLOT.name },
                )
            }

            CenterPage.OCR_LOG -> {
                val installed = selectedSlot?.let(libraryState::installed)
                if (installed == null) pageName = CenterPage.SLOT.name
                else OcrDiagnosticsScreen(installed) { pageName = CenterPage.SLOT.name }
            }
        }
    }
}
