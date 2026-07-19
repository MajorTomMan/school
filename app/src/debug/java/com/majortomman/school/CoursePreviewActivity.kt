package com.majortomman.school

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.majortomman.school.learning.course.CoursePageBlock
import com.majortomman.school.learning.course.CoursePageBlockKind
import com.majortomman.school.learning.course.RationalLessonPage
import com.majortomman.school.learning.course.RationalVisualizationKind
import com.majortomman.school.ui.CloudCoursePreviewScreen
import com.majortomman.school.ui.theme.SchoolTheme

class CoursePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preview = intent.getStringExtra("preview").orEmpty()
        setContent {
            SchoolTheme {
                CloudCoursePreviewScreen(previewPage(preview))
            }
        }
    }

    private fun previewPage(kind: String): RationalLessonPage = when (kind) {
        "function" -> RationalLessonPage(
            id = "preview-function",
            section = "一次函数的图象和性质",
            title = "一次函数的图象",
            paragraphs = emptyList(),
            sourcePage = 117,
            visualization = RationalVisualizationKind.FUNCTION_GRAPH,
            blocks = listOf(
                CoursePageBlock(
                    kind = CoursePageBlockKind.TEXTBOOK_TEXT,
                    text = "一般地，形如y=kx+b（k，b是常数，k≠0）的函数，叫做一次函数。",
                ),
                CoursePageBlock(
                    kind = CoursePageBlockKind.PROMPT,
                    text = "画出函数图象，观察当k和b改变时，直线的位置怎样变化。",
                ),
                CoursePageBlock(
                    kind = CoursePageBlockKind.FORMULA,
                    text = "y = kx + b（k ≠ 0）",
                ),
                CoursePageBlock(
                    kind = CoursePageBlockKind.VISUALIZATION,
                    visualization = RationalVisualizationKind.FUNCTION_GRAPH,
                    visualizationParams = mapOf("mode" to "linear"),
                ),
            ),
        )
        "geometry" -> RationalLessonPage(
            id = "preview-geometry",
            section = "三角形",
            title = "三角形的概念",
            paragraphs = emptyList(),
            sourcePage = 2,
            visualization = RationalVisualizationKind.TRIANGLE,
            blocks = listOf(
                CoursePageBlock(
                    kind = CoursePageBlockKind.TEXTBOOK_TEXT,
                    text = "由不在同一条直线上的三条线段首尾顺次相接所组成的图形叫作三角形。",
                ),
                CoursePageBlock(
                    kind = CoursePageBlockKind.TEXTBOOK_TEXT,
                    text = "组成三角形的线段叫作三角形的边，相邻两边的公共端点叫作三角形的顶点，相邻两边所组成的角叫作三角形的内角。",
                ),
                CoursePageBlock(
                    kind = CoursePageBlockKind.VISUALIZATION,
                    visualization = RationalVisualizationKind.TRIANGLE,
                ),
            ),
        )
        else -> RationalLessonPage(
            id = "preview-rational",
            section = "有理数及其大小比较",
            title = "整数写成分数形式",
            paragraphs = emptyList(),
            sourcePage = 7,
            visualization = RationalVisualizationKind.RATIONAL_DEFINITION_FLOW,
            blocks = listOf(
                CoursePageBlock(CoursePageBlockKind.TEXTBOOK_TEXT, "进一步地，正整数可以写成正分数的形式，例如"),
                CoursePageBlock(CoursePageBlockKind.FORMULA, "2 = 2/1"),
                CoursePageBlock(CoursePageBlockKind.TEXTBOOK_TEXT, "负整数可以写成负分数的形式，例如"),
                CoursePageBlock(CoursePageBlockKind.FORMULA, "−3 = −3/1"),
                CoursePageBlock(CoursePageBlockKind.TEXTBOOK_TEXT, "0也可以写成分数的形式"),
                CoursePageBlock(CoursePageBlockKind.FORMULA, "0 = 0/1"),
                CoursePageBlock(CoursePageBlockKind.TEXTBOOK_TEXT, "这样，整数可以写成分数的形式。"),
                CoursePageBlock(
                    kind = CoursePageBlockKind.VISUALIZATION,
                    visualization = RationalVisualizationKind.RATIONAL_DEFINITION_FLOW,
                ),
            ),
        )
    }
}
