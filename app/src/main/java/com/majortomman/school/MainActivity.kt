package com.majortomman.school

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.majortomman.school.data.ImportTutorialRepository
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.data.material.MaterialPackRepository
import com.majortomman.school.ui.SchoolApp
import com.majortomman.school.ui.theme.SchoolTheme

class MainActivity : ComponentActivity() {
    private val preferencesRepository by lazy {
        PreferencesRepository(applicationContext)
    }
    private val materialPackRepository by lazy {
        MaterialPackRepository(applicationContext)
    }
    private val importTutorialRepository by lazy {
        ImportTutorialRepository(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialTextbookKey = intent.getStringExtra("open_textbook_slot")
        setContent {
            SchoolTheme {
                SchoolApp(
                    repository = preferencesRepository,
                    materialRepository = materialPackRepository,
                    tutorialRepository = importTutorialRepository,
                    initialTextbookKey = initialTextbookKey,
                )
            }
        }
    }
}
