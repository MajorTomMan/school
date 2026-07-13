package com.majortomman.school.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MinimalBlack = Color(0xFF000000)
private val MinimalWhite = Color(0xFFF5F5F7)
private val MinimalBlue = Color(0xFF0A84FF)
private val MinimalRed = Color(0xFFFF453A)
private val MinimalYellow = Color(0xFFFFD60A)

private val MinimalColors = darkColorScheme(
    primary = MinimalBlue,
    onPrimary = MinimalWhite,
    primaryContainer = Color(0xFF001D3A),
    onPrimaryContainer = MinimalWhite,
    secondary = MinimalYellow,
    onSecondary = MinimalBlack,
    secondaryContainer = Color(0xFF211C00),
    onSecondaryContainer = MinimalWhite,
    tertiary = MinimalRed,
    onTertiary = MinimalWhite,
    tertiaryContainer = Color(0xFF2B0806),
    onTertiaryContainer = MinimalWhite,
    background = MinimalBlack,
    onBackground = MinimalWhite,
    surface = MinimalBlack,
    onSurface = MinimalWhite,
    surfaceVariant = Color(0xFF0B0B0D),
    onSurfaceVariant = Color(0xFFA1A1A6),
    outline = Color(0xFF3A3A3C),
    error = MinimalRed,
    onError = MinimalWhite,
)

private val SchoolTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val MinimalShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun SchoolTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MinimalColors,
        typography = SchoolTypography,
        shapes = MinimalShapes,
        content = content,
    )
}
