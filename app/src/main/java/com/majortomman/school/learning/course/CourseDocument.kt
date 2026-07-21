package com.majortomman.school.learning.course

/**
 * Runtime course model. The course package is pure business data; package correctness is enforced
 * by the APK before a course becomes active.
 */
data class CourseDocument(
    val textbook: CourseTextbook,
    val chapters: List<CourseChapter>,
)

data class CourseTextbook(
    val id: String,
    val title: String,
    val publisher: String,
    val edition: String,
    val grade: String,
    val semester: String,
    val subject: String,
    val pdf: CoursePdf,
)

data class CoursePdf(
    val path: String,
    val pageCount: Int,
    val pageIndexOffset: Int,
)

data class CourseChapter(
    val id: String,
    val number: String,
    val title: String,
    val aliases: List<String>,
    val sections: List<CourseSection>,
    val review: CourseSection? = null,
)

data class CourseSection(
    val id: String,
    val number: String,
    val title: String,
    val aliases: List<String>,
    val pages: List<CoursePage>,
)

data class CoursePage(
    val id: String,
    val section: String,
    val title: String,
    val aliases: List<String>,
    val sourcePage: Int,
    val sourcePageEnd: Int,
    val blocks: List<CourseBlock>,
)

enum class CourseTextStyle {
    TEXTBOOK,
    EXPLANATION,
    HISTORY,
    PROMPT,
    CAPTION,
}

sealed interface CourseBlock

data class CourseHeading(val text: String) : CourseBlock

data class CourseText(
    val text: String,
    val style: CourseTextStyle,
) : CourseBlock

data class CourseFormula(
    val expression: String,
    val conditions: List<String>,
) : CourseBlock

data class CourseList(val items: List<String>) : CourseBlock

data class CourseExample(
    val label: String,
    val statement: String,
    val steps: List<String>,
    val result: String?,
) : CourseBlock

data class CourseExercise(
    val number: String,
    val stem: String,
    val choices: List<String>,
    val hints: List<String>,
) : CourseBlock

data class CourseConclusion(val text: String) : CourseBlock

data class CourseSceneBlock(val scene: CourseScene) : CourseBlock

/** Canonical scene templates built into the APK. Course packages use only these exact identifiers. */
enum class CourseSceneTemplate(val id: String) {
    OPPOSITE_QUANTITIES("opposite_quantities"),
    RATIONAL_CLASSIFICATION("rational_classification"),
    INTEGER_TO_FRACTION("integer_to_fraction"),
    NUMBER_LINE("number_line"),
    OPPOSITE_NUMBERS("opposite_numbers"),
    ABSOLUTE_VALUE("absolute_value"),
    NUMBER_COMPARISON("number_comparison"),
    ADDITION_PROCESS("addition_process"),
    SUBTRACTION_TRANSFORM("subtraction_transform"),
    MULTIPLICATION_SIGN("multiplication_sign"),
    DIVISION_TRANSFORM("division_transform"),
    POWER_PROCESS("power_process"),
    ALGEBRA_PROCESS("algebra_process"),
    EQUATION_BALANCE("equation_balance"),
    ROOT_NUMBER_LINE("root_number_line"),
    CARTESIAN_PLANE("cartesian_plane"),
    FUNCTION_GRAPH("function_graph"),
    GEOMETRY("geometry"),
    TRANSFORMATION("transformation"),
    RIGHT_TRIANGLE("right_triangle"),
    DATA_CHART("data_chart"),
    PROBABILITY("probability"),
    PROJECTION("projection"),
    DECLARATIVE_DIAGRAM("diagram"),
    ;

    companion object {
        fun fromId(id: String): CourseSceneTemplate? = entries.firstOrNull { it.id == id }
    }
}

data class CourseScene(
    val template: CourseSceneTemplate,
    val data: CourseSceneData,
)

/** Typed immutable scene data. Values are decoded and validated by the APK. */
class CourseSceneData internal constructor(
    private val values: Map<String, Any?>,
) {
    fun has(key: String): Boolean = values.containsKey(key)

    fun string(key: String, default: String = ""): String = values[key] as? String ?: default

    fun boolean(key: String, default: Boolean = false): Boolean = values[key] as? Boolean ?: default

    fun number(key: String, default: Double = 0.0): Double = when (val value = values[key]) {
        is Number -> value.toDouble()
        else -> default
    }

    fun integer(key: String, default: Int = 0): Int = when (val value = values[key]) {
        is Number -> value.toInt()
        else -> default
    }

    @Suppress("UNCHECKED_CAST")
    fun objects(key: String): List<Map<String, Any?>> = values[key] as? List<Map<String, Any?>> ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    fun strings(key: String): List<String> = values[key] as? List<String> ?: emptyList()

    fun raw(key: String): Any? = values[key]

    fun keys(): Set<String> = values.keys
}
