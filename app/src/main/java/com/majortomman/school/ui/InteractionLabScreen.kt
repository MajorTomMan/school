package com.majortomman.school.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry kept for older navigation code.
 * The former experiment gallery has been replaced by the subject-based verification hub.
 */
@Composable
internal fun InteractionLabScreen() {
    VerificationHubScreen()
}
