package com.majortomman.school

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.majortomman.school.data.PreferencesRepository
import com.majortomman.school.ui.SchoolApp
import com.majortomman.school.ui.theme.SchoolTheme

class MainActivity : ComponentActivity() {
    private val preferencesRepository by lazy {
        PreferencesRepository(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SchoolTheme {
                SchoolApp(preferencesRepository)
            }
        }
    }
}
