package com.majortomman.school

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.data.curriculum.CurriculumRepository
import com.majortomman.school.data.material.MaterialPackRepository
import com.majortomman.school.data.math.MathQuestionBankRepository
import com.majortomman.school.learning.cloud.CloudCourseRepository
import com.majortomman.school.learning.cloud.CourseDownloadCoordinator
import com.majortomman.school.learning.cloud.CourseDownloadUiState
import com.majortomman.school.learning.cloud.CourseUpdateKind
import com.majortomman.school.learning.cloud.CourseUpdateOffer
import com.majortomman.school.network.AppProxy
import com.majortomman.school.startup.StartupInitializationCoordinator
import com.majortomman.school.ui.SchoolApp
import com.majortomman.school.ui.UpdateOverlayHost
import com.majortomman.school.ui.theme.SchoolTheme
import com.majortomman.school.update.UpdateCoordinator
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val curriculumRepository by lazy {
        CurriculumRepository(applicationContext)
    }
    private val preferencesRepository by lazy {
        PreferencesRepository(applicationContext, curriculumRepository)
    }
    private val materialPackRepository by lazy {
        MaterialPackRepository(applicationContext)
    }
    private val mathQuestionBankRepository by lazy {
        MathQuestionBankRepository(applicationContext, curriculumRepository)
    }
    private val updateCoordinatorDelegate = lazy {
        UpdateCoordinator.get(applicationContext)
    }
    private val updateCoordinator: UpdateCoordinator
        get() = updateCoordinatorDelegate.value
    private val pendingCourseUpdate = MutableStateFlow<CourseUpdateOffer?>(null)

    private var firstFrameReady = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val activityStartedAt = SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)
        AppProxy.initialize(applicationContext)
        CloudCourseRepository.initialize(applicationContext)
        enableEdgeToEdge()
        val initialTextbookKey = intent.getStringExtra("open_textbook_slot")
        val courseContentInstalled = CloudCourseRepository.hasInstalledCourseContent()
        setContent {
            val courseUpdateOffer by pendingCourseUpdate.collectAsState()
            val courseDownloadState by CourseDownloadCoordinator.state.collectAsState()
            var showInitialCoursePrompt by rememberSaveable {
                mutableStateOf(!courseContentInstalled)
            }
            var hiddenProgressOperationId by rememberSaveable { mutableStateOf<Long?>(null) }

            LaunchedEffect(courseDownloadState) {
                when (courseDownloadState) {
                    is CourseDownloadUiState.Success -> {
                        showInitialCoursePrompt = false
                        pendingCourseUpdate.value = null
                        materialPackRepository.refreshCurrent()
                        CourseDownloadCoordinator.clearTerminalState()
                    }
                    else -> Unit
                }
            }

            SchoolTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    SchoolApp(
                        repository = preferencesRepository,
                        materialRepository = materialPackRepository,
                        curriculumRepository = curriculumRepository,
                        mathQuestionRepository = mathQuestionBankRepository,
                        initialTextbookKey = initialTextbookKey,
                    )
                    UpdateOverlayHost { updateCoordinator }

                    val downloadActive = courseDownloadState is CourseDownloadUiState.Queued ||
                        courseDownloadState is CourseDownloadUiState.Running
                    when {
                        showInitialCoursePrompt && !downloadActive -> {
                            CourseDownloadConfirmationDialog(
                                title = "下载课程包",
                                message = "学习内容尚未下载。课程包和教材 PDF 共约 13 MB，" +
                                    "下载完成后可以离线使用；后续更新只会获取发生变化的内容。",
                                confirmLabel = "下载课程",
                                onConfirm = {
                                    showInitialCoursePrompt = false
                                    hiddenProgressOperationId = null
                                    CourseDownloadCoordinator.enqueue(applicationContext)
                                },
                                onLater = { showInitialCoursePrompt = false },
                            )
                        }
                        courseUpdateOffer != null && !downloadActive -> {
                            val offer = requireNotNull(courseUpdateOffer)
                            CourseDownloadConfirmationDialog(
                                title = when (offer.kind) {
                                    CourseUpdateKind.INITIAL -> "下载新增课程"
                                    CourseUpdateKind.FULL -> "发现课程更新"
                                    CourseUpdateKind.INCREMENTAL -> "发现课程增量更新"
                                },
                                message = updateOfferMessage(offer),
                                confirmLabel = "下载更新",
                                onConfirm = {
                                    pendingCourseUpdate.value = null
                                    hiddenProgressOperationId = null
                                    CourseDownloadCoordinator.enqueue(applicationContext)
                                },
                                onLater = { pendingCourseUpdate.value = null },
                            )
                        }
                    }

                    val activeOperationId = courseDownloadState.operationIdOrNull()
                    if (
                        downloadActive &&
                        activeOperationId != null &&
                        hiddenProgressOperationId != activeOperationId
                    ) {
                        CourseDownloadProgressDialog(
                            state = courseDownloadState,
                            onBackground = { hiddenProgressOperationId = activeOperationId },
                        )
                    }
                }
            }
        }

        window.decorView.postOnAnimation {
            firstFrameReady = true
            Log.i(
                StartupInitializationCoordinator.LOG_TAG,
                "launcher frame callback: activity=${SystemClock.elapsedRealtime() - activityStartedAt} ms, " +
                    "process=${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()} ms",
            )
            requestNotificationPermissionOnce()
            updateCoordinator.onAppForeground()
        }
        StartupInitializationCoordinator.start(
            context = applicationContext,
            checkCourseUpdatesOnStartup = courseContentInstalled,
            onCourseCatalogChanged = {
                materialPackRepository.refreshCurrent()
            },
            onCourseUpdateAvailable = { offer ->
                pendingCourseUpdate.value = offer
            },
        )
    }

    override fun onStart() {
        super.onStart()
        if (firstFrameReady) updateCoordinator.onAppForeground()
    }

    override fun onStop() {
        if (updateCoordinatorDelegate.isInitialized()) updateCoordinator.onAppBackground()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_update_dialog", false)) {
            updateCoordinator.showDialog()
        }
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        val preferences = getSharedPreferences("school_updates", MODE_PRIVATE)
        if (preferences.getBoolean("notification_permission_requested", false)) return
        preferences.edit().putBoolean("notification_permission_requested", true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun CourseDownloadConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("稍后")
            }
        },
    )
}

@Composable
private fun CourseDownloadProgressDialog(
    state: CourseDownloadUiState,
    onBackground: () -> Unit,
) {
    val running = state as? CourseDownloadUiState.Running
    val downloaded = running?.downloadedBytes ?: 0L
    val total = running?.totalBytes ?: 0L
    val fraction = if (total > 0L) {
        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    AlertDialog(
        onDismissRequest = onBackground,
        title = { Text("正在下载课程内容") },
        text = {
            Column {
                Text(running?.stage ?: "正在等待网络")
                running?.currentItem?.takeIf(String::isNotBlank)?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it)
                }
                Spacer(modifier = Modifier.height(14.dp))
                if (total > 0L) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${formatBytes(downloaded)} / ${formatBytes(total)}  ${(fraction * 100).toInt()}%")
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBackground) {
                Text("后台下载")
            }
        },
    )
}

private fun updateOfferMessage(offer: CourseUpdateOffer): String {
    val kindText = when (offer.kind) {
        CourseUpdateKind.INITIAL -> "新增课程内容"
        CourseUpdateKind.FULL -> "完整课程更新"
        CourseUpdateKind.INCREMENTAL -> "增量课程更新"
    }
    return "$kindText 已准备好，预计下载 ${formatBytes(offer.estimatedBytes)}。" +
        "确认后将在后台下载，下载进度会同时显示在应用和通知栏中。"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun CourseDownloadUiState.operationIdOrNull(): Long? = when (this) {
    CourseDownloadUiState.Idle -> null
    is CourseDownloadUiState.Queued -> operationId
    is CourseDownloadUiState.Running -> operationId
    is CourseDownloadUiState.Success -> operationId
    is CourseDownloadUiState.Failed -> operationId
}
