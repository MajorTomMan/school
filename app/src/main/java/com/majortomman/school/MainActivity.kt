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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.data.curriculum.CurriculumRepository
import com.majortomman.school.data.material.MaterialPackRepository
import com.majortomman.school.data.math.MathQuestionBankRepository
import com.majortomman.school.learning.cloud.CloudCourseRepository
import com.majortomman.school.network.AppProxy
import com.majortomman.school.startup.StartupInitializationCoordinator
import com.majortomman.school.ui.SchoolApp
import com.majortomman.school.ui.UpdateOverlayHost
import com.majortomman.school.ui.theme.SchoolTheme
import com.majortomman.school.update.UpdateCoordinator

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
            var showCourseDownloadPrompt by rememberSaveable {
                mutableStateOf(!courseContentInstalled)
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
                    if (showCourseDownloadPrompt) {
                        CourseDownloadPrompt(
                            onDownload = {
                                showCourseDownloadPrompt = false
                                StartupInitializationCoordinator.requestCourseSync(applicationContext) {
                                    materialPackRepository.refreshCurrent()
                                }
                            },
                            onLater = { showCourseDownloadPrompt = false },
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
            syncCoursesOnStartup = courseContentInstalled,
        ) {
            materialPackRepository.refreshCurrent()
        }
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
private fun CourseDownloadPrompt(
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("下载课程包") },
        text = {
            Text(
                "学习内容尚未下载。课程包和教材 PDF 共约 13 MB，下载完成后可以离线使用；" +
                    "后续更新只会获取发生变化的内容。",
            )
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text("下载课程")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("稍后")
            }
        },
    )
}
