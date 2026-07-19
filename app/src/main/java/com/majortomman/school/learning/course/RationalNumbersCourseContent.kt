package com.majortomman.school.learning.course

/**
 * Renderer identifiers supported by the APK. Course packages may select one of these identifiers,
 * but they cannot provide executable code.
 */
enum class RationalVisualizationKind {
    NONE,
    OPPOSITE_QUANTITIES,
    RATIONAL_CLASSIFICATION,
    RATIONAL_DEFINITION_FLOW,
    NUMBER_LINE,
    OPPOSITE_NUMBERS,
    ABSOLUTE_VALUE,
    NUMBER_COMPARISON,
    ADDITION_PROCESS,
    SUBTRACTION_TRANSFORM,
    MULTIPLICATION_SIGN,
    DIVISION_TRANSFORM,
    POWER_PROCESS,
    EQUATION_BALANCE,
    EQUATION_SYSTEM,
    INEQUALITY_NUMBER_LINE,
    COORDINATE_PLANE,
    INTERSECTING_LINES,
    PARALLEL_LINES,
    TRANSLATION,
    TRIANGLE,
    CONGRUENT_TRIANGLES,
    AXIS_SYMMETRY,
    PYTHAGOREAN,
    QUADRILATERAL,
    FUNCTION_RELATION,
    FUNCTION_GRAPH,
    STATISTICS,
    PROBABILITY,
    ROTATION,
    CIRCLE,
    SIMILARITY,
    RIGHT_TRIANGLE,
    PROJECTION,
    ALGEBRA_PROCESS,
    HISTORY,
}

enum class CoursePageBlockKind {
    TEXTBOOK_TEXT,
    PROMPT,
    FORMULA,
    WORKED_EXAMPLE,
    EXERCISE,
    SUMMARY,
    CONCLUSION,
    VISUALIZATION,
}

/** Ordered content from one textbook page. */
data class CoursePageBlock(
    val kind: CoursePageBlockKind,
    val text: String = "",
    val label: String? = null,
    val items: List<String> = emptyList(),
    val visualization: RationalVisualizationKind = RationalVisualizationKind.NONE,
    val visualizationParams: Map<String, String> = emptyMap(),
)

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
    val sourcePageEnd: Int = sourcePage,
    val formula: String? = null,
    val conclusion: String? = null,
    val blocks: List<CoursePageBlock> = emptyList(),
)
