package com.majortomman.school.learning.course

/**
 * Renderer identifiers supported by the APK. Course packages may select one of these identifiers,
 * but they cannot provide executable code.
 */
enum class RationalVisualizationKind {
    NONE,
    OPPOSITE_QUANTITIES,
    RATIONAL_CLASSIFICATION,
    NUMBER_LINE,
    OPPOSITE_NUMBERS,
    ABSOLUTE_VALUE,
    NUMBER_COMPARISON,
    ADDITION_PROCESS,
    SUBTRACTION_TRANSFORM,
    MULTIPLICATION_SIGN,
    DIVISION_TRANSFORM,
    POWER_PROCESS,
    HISTORY,
}

/**
 * Runtime page model decoded from an installed cloud course package.
 *
 * The historical class name is retained for binary/source compatibility while the course runtime is
 * migrated to a fully generic page contract. It contains no bundled course content.
 */
data class RationalLessonPage(
    val id: String,
    val section: String,
    val title: String,
    val paragraphs: List<String>,
    val sourcePage: Int,
    val visualization: RationalVisualizationKind,
    val formula: String? = null,
    val conclusion: String? = null,
)
