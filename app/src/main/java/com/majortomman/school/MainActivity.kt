package com.majortomman.school

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.majortomman.school.data.ImportTutorialRepository
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.data.curriculum.CurriculumRepository
import com.majortomman.school.data.material.MaterialPackRepository
import com.majortomman.school.data.math.MathQuestionBankRepository
import com.majortomman.school.startup.StartupInitializationCoordinator
import com.majortomman.school.ui.SchoolApp
import com.majortomman.school.ui.theme.SchoolTheme

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
    private val importTutorialRepository by lazy {
        ImportTutorialRepository(applicationContext)
    }
    private val mathQuestionBankRepository by lazy {
        MathQuestionBankRepository(applicationContext, curriculumRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val activityStartedAt = SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialTextbookKey = intent.getStringExtra("open_textbook_slot")
        setContent {
            SchoolTheme {
                SchoolApp(
                    repository = preferencesRepository,
                    materialRepository = materialPackRepository,
                    curriculumRepository = curriculumRepository,
                    tutorialRepository = importTutorialRepository,
                    mathQuestionRepository = mathQuestionBankRepository,
                    initialTextbookKey = initialTextbookKey,
                )
            }
        }

        window.decorView.postOnAnimation {
            Log.i(
                StartupInitializationCoordinator.LOG_TAG,
                "launcher frame callback: activity=${SystemClock.elapsedRealtime() - activityStartedAt} ms, " +
                    "process=${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()} ms",
            )
        }
        StartupInitializationCoordinator.start(applicationContext) {
            materialPackRepository.refreshCurrent()
        }
    }
}
