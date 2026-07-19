package com.majortomman.school.learning.course

/**
 * Renderer identifiers supported by the APK. Course packages may select one of these identifiers,
 * but they cannot provide executable code.
 *
 * The historical enum name is retained for source compatibility. The values now cover the reusable
 * junior-high mathematics renderer set rather than one chapter only.
 */
enum class RationalVisualizationKind {
    NONE,
    OPPOSITE_QUANTITIES,
    RATIONAL_CLASSIFICATION,
    INTEGER_TO_FRACTION,
    NUMBER_LINE,
    OPPOSITE_NUMBERS,
    ABSOLUTE_VALUE,
    NUMBER_COMPARISON,
    ADDITION_PROCESS,
    SUBTRACTION_TRANSFORM,
    MULTIPLICATION_SIGN,
    DIVISION_TRANSFORM,
    POWER_PROCESS,
    ALGEBRA_PROCESS,
    EQUATION_BALANCE,
    ROOT_NUMBER_LINE,
    CARTESIAN_PLANE,
    FUNCTION_GRAPH,
    GEOMETRY,
    TRANSFORMATION,
    RIGHT_TRIANGLE,
    DATA_CHART,
    PROBABILITY,
    PROJECTION,
    HISTORY,
}

enum class CourseTextRole {
    TEXTBOOK,
    EXPLANATION,
    HISTORY,
    PROMPT,
    CAPTION,
}

sealed interface CoursePageBlock

data class CourseTextBlock(
    val text: String,
    val role: CourseTextRole = CourseTextRole.TEXTBOOK,
) : CoursePageBlock

data class CourseHeadingBlock(val text: String) : CoursePageBlock

data class CourseFormulaBlock(
    val expression: String,
    val conditions: List<String> = emptyList(),
) : CoursePageBlock

data class CourseSummaryBlock(val items: List<String>) : CoursePageBlock

data class CourseWorkedExampleBlock(
    val label: String = "例题",
    val statement: String,
    val steps: List<String> = emptyList(),
    val result: String? = null,
) : CoursePageBlock

data class CourseExerciseBlock(
    val number: String = "",
    val stem: String,
    val choices: List<String> = emptyList(),
    val hints: List<String> = emptyList(),
) : CoursePageBlock

data class CourseConclusionBlock(val text: String) : CoursePageBlock

data class CourseVisualizationBlock(
    val renderer: String,
    val kind: RationalVisualizationKind,
    val params: Map<String, String> = emptyMap(),
) : CoursePageBlock

/**
 * A faithful crop of the installed textbook PDF. It is used for formulas, diagrams and layouts that
 * cannot be transcribed reliably without changing the textbook's meaning.
 * Coordinates use PDF points in the source page coordinate system.
 */
data class CourseSourceExcerptBlock(
    val sourcePage: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val fallbackText: String = "",
    val altText: String = "教材原图",
) : CoursePageBlock

/**
 * Runtime page model decoded from an installed cloud course package.
 *
 * The historical class name is retained for compatibility. Ordered [blocks] are authoritative;
 * the older flattened fields remain so existing tests and renderers can migrate incrementally.
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
