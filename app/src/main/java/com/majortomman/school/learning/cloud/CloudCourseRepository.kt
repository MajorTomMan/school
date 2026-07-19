package com.majortomman.school.learning.cloud

import android.content.Context
import com.majortomman.school.learning.course.CourseConclusionBlock
import com.majortomman.school.learning.course.CourseExerciseBlock
import com.majortomman.school.learning.course.CourseFormulaBlock
import com.majortomman.school.learning.course.CourseHeadingBlock
import com.majortomman.school.learning.course.CoursePageBlock
import com.majortomman.school.learning.course.CourseSourceExcerptBlock
import com.majortomman.school.learning.course.CourseSummaryBlock
import com.majortomman.school.learning.course.CourseTextBlock
import com.majortomman.school.learning.course.CourseTextRole
import com.majortomman.school.learning.course.CourseVisualizationBlock
import com.majortomman.school.learning.course.CourseWorkedExampleBlock
import com.majortomman.school.learning.course.RationalLessonPage
import com.majortomman.school.learning.course.RationalVisualizationKind
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object CloudCourseRepository {
    private const val ACTIVE_DIRECTORY = "course-packs/active"

    @Volatile
    private var appContext: Context? = null

    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    private val cacheLock = Any()
    private val parsedCache = mutableMapOf<String, CachedCourseDocument>()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun hasInstalledCourseContent(): Boolean {
        val context = appContext ?: return false
        val activeRoot = File(context.filesDir, ACTIVE_DIRECTORY)
        return activeRoot.listFiles().orEmpty().any { directory ->
            if (!directory.isDirectory) return@any false
            runCatching {
                val courseFile = File(directory, "course.json")
                if (!courseFile.isFile) return@runCatching false
                val course = JSONObject(courseFile.readText(Charsets.UTF_8))
                val pdfPath = course.getJSONObject("textbook")
                    .getJSONObject("pdf")
                    .getString("path")
                    .trim()
                pdfPath.isNotBlank() && File(directory, pdfPath).isFile
            }.getOrDefault(false)
        }
    }

    fun supports(title: String): Boolean =
        courseDocuments().any { document -> CloudCourseCodec.supports(document.root, title) }

    fun pagesFor(title: String, sourcePages: IntRange): List<RationalLessonPage> =
        courseDocuments().firstNotNullOfOrNull { document ->
            CloudCourseCodec.pagesFor(document.root, title, sourcePages).takeIf { it.isNotEmpty() }
        }.orEmpty()

    internal fun markContentChanged() {
        synchronized(cacheLock) { parsedCache.clear() }
        mutableRevision.value += 1L
    }

    private fun courseDocuments(): List<CachedCourseDocument> {
        val context = appContext ?: return emptyList()
        val activeRoot = File(context.filesDir, ACTIVE_DIRECTORY)
        val courseFiles = activeRoot.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .map { File(it, "course.json") }
            .filter(File::isFile)

        return courseFiles.mapNotNull { file ->
            synchronized(cacheLock) {
                val key = file.absolutePath
                val cached = parsedCache[key]
                if (cached != null && cached.lastModified == file.lastModified() && cached.length == file.length()) {
                    cached
                } else {
                    runCatching {
                        CachedCourseDocument(
                            lastModified = file.lastModified(),
                            length = file.length(),
                            root = JSONObject(file.readText(Charsets.UTF_8)),
                        )
                    }.getOrNull()?.also { parsedCache[key] = it }
                }
            }
        }
    }

    private data class CachedCourseDocument(
        val lastModified: Long,
        val length: Long,
        val root: JSONObject,
    )
}

internal object CloudCourseCodec {
    fun validate(root: JSONObject) {
        require(root.getInt("schemaVersion") == SUPPORTED_COURSE_SCHEMA_VERSION) {
            "课程文件的数据结构版本不受支持"
        }
        val textbook = root.getJSONObject("textbook")
        require(textbook.getString("id").isNotBlank()) { "课程文件缺少教材 ID" }
        val chapters = root.getJSONArray("chapters")
        require(chapters.length() > 0) { "课程文件不包含章节" }
        for (chapterIndex in 0 until chapters.length()) {
            val chapter = chapters.getJSONObject(chapterIndex)
            require(chapter.getString("id").isNotBlank()) { "章节 ID 不能为空" }
            require(chapter.getString("title").isNotBlank()) { "章节标题不能为空" }
            val sections = chapter.optJSONArray("sections") ?: JSONArray()
            for (sectionIndex in 0 until sections.length()) {
                val section = sections.getJSONObject(sectionIndex)
                require(section.getString("id").isNotBlank()) { "小节 ID 不能为空" }
                require(section.getString("title").isNotBlank()) { "小节标题不能为空" }
                decodePages(section.optJSONArray("pages"), section.getString("title"), 1..1)
            }
            decodePages(chapter.optJSONObject("review")?.optJSONArray("pages"), "${chapter.getString("title")} 小结", 1..1)
        }
    }

    fun supports(root: JSONObject, title: String): Boolean =
        pagesFor(root, title, 1..1).isNotEmpty()

    fun pagesFor(root: JSONObject, title: String, sourcePages: IntRange): List<RationalLessonPage> {
        validate(root)
        val requested = normalizeTitle(title)
        val chapters = root.getJSONArray("chapters")
        for (chapterIndex in 0 until chapters.length()) {
            val chapter = chapters.getJSONObject(chapterIndex)
            val chapterTitle = chapter.getString("title")
            val chapterNames = namesOf(chapter, chapterTitle, chapter.optString("number") + chapterTitle)
            val sections = chapter.optJSONArray("sections") ?: JSONArray()

            if (chapterNames.any { normalizeTitle(it) == requested }) {
                return buildList {
                    for (sectionIndex in 0 until sections.length()) {
                        val section = sections.getJSONObject(sectionIndex)
                        addAll(decodePages(section.optJSONArray("pages"), section.getString("title"), sourcePages))
                    }
                    addAll(decodePages(chapter.optJSONObject("review")?.optJSONArray("pages"), "$chapterTitle 小结", sourcePages))
                }
            }

            for (sectionIndex in 0 until sections.length()) {
                val section = sections.getJSONObject(sectionIndex)
                val sectionTitle = section.getString("title")
                val sectionNames = namesOf(section, sectionTitle, section.optString("number") + sectionTitle)
                if (sectionNames.any { normalizeTitle(it) == requested }) {
                    return decodePages(section.optJSONArray("pages"), sectionTitle, sourcePages)
                }

                val pages = section.optJSONArray("pages") ?: JSONArray()
                for (pageIndex in 0 until pages.length()) {
                    val page = pages.getJSONObject(pageIndex)
                    if (namesOf(page, page.optString("title")).any { normalizeTitle(it) == requested }) {
                        return listOf(decodePage(page, sectionTitle, sourcePages))
                    }
                }
            }

            val review = chapter.optJSONObject("review")
            if (review != null) {
                val reviewTitle = review.optString("title", "$chapterTitle 小结")
                if (namesOf(review, reviewTitle).any { normalizeTitle(it) == requested }) {
                    return decodePages(review.optJSONArray("pages"), reviewTitle, sourcePages)
                }
            }
        }
        return emptyList()
    }

    private fun decodePages(
        pages: JSONArray?,
        sectionTitle: String,
        sourcePages: IntRange,
    ): List<RationalLessonPage> = buildList {
        if (pages == null) return@buildList
        for (index in 0 until pages.length()) {
            add(decodePage(pages.getJSONObject(index), sectionTitle, sourcePages))
        }
    }

    private fun decodePage(
        page: JSONObject,
        sectionTitle: String,
        sourcePages: IntRange,
    ): RationalLessonPage {
        val decodedBlocks = mutableListOf<CoursePageBlock>()
        val paragraphs = mutableListOf<String>()
        var formula: String? = null
        var conclusion: String? = page.optString("conclusion").takeIf(String::isNotBlank)
        var visualization = RationalVisualizationKind.HISTORY
        val blocks = page.optJSONArray("blocks") ?: JSONArray()
        val pageSource = page.optInt("sourcePage", sourcePages.first).coerceAtLeast(1)

        for (index in 0 until blocks.length()) {
            val block = blocks.getJSONObject(index)
            when (block.getString("type")) {
                "heading" -> block.optString("text").takeIf(String::isNotBlank)?.let {
                    decodedBlocks += CourseHeadingBlock(it)
                }
                "textbook_text", "explanation", "historical_note", "prompt", "caption" -> {
                    val text = block.optString("text").trim()
                    if (text.isNotBlank()) {
                        val role = when (block.getString("type")) {
                            "explanation" -> CourseTextRole.EXPLANATION
                            "historical_note" -> CourseTextRole.HISTORY
                            "prompt" -> CourseTextRole.PROMPT
                            "caption" -> CourseTextRole.CAPTION
                            else -> CourseTextRole.TEXTBOOK
                        }
                        decodedBlocks += CourseTextBlock(text, role)
                        paragraphs += text
                    }
                }
                "formula" -> {
                    val expression = block.optString("expression").trim()
                    val conditions = block.optJSONArray("conditions")?.strings().orEmpty()
                    if (expression.isNotBlank()) {
                        decodedBlocks += CourseFormulaBlock(expression, conditions)
                        if (formula == null) {
                            formula = buildString {
                                append(expression)
                                if (conditions.isNotEmpty()) append("（${conditions.joinToString("，")}）")
                            }
                        }
                    }
                }
                "summary" -> {
                    val items = block.optJSONArray("items")?.strings().orEmpty()
                    if (items.isNotEmpty()) {
                        decodedBlocks += CourseSummaryBlock(items)
                        paragraphs += items
                    }
                }
                "worked_example" -> {
                    val statement = block.optString("statement").trim()
                    val steps = block.optJSONArray("steps")?.strings().orEmpty()
                    val result = block.optString("result").trim().takeIf(String::isNotBlank)
                    if (statement.isNotBlank()) {
                        decodedBlocks += CourseWorkedExampleBlock(
                            label = block.optString("label", "例题").ifBlank { "例题" },
                            statement = statement,
                            steps = steps,
                            result = result,
                        )
                        paragraphs += statement
                        paragraphs += steps
                        result?.let { paragraphs += "结果：$it" }
                    }
                }
                "exercise" -> {
                    val number = block.optString("number").trim()
                    val stem = block.optString("stem").trim()
                    val choices = block.optJSONArray("choices")?.strings().orEmpty()
                    val hints = block.optJSONArray("hints")?.strings().orEmpty()
                    if (stem.isNotBlank()) {
                        decodedBlocks += CourseExerciseBlock(number, stem, choices, hints)
                        paragraphs += listOf(number, stem).filter(String::isNotBlank).joinToString(". ")
                    }
                }
                "conclusion" -> {
                    block.optString("text").trim().takeIf(String::isNotBlank)?.let {
                        decodedBlocks += CourseConclusionBlock(it)
                        conclusion = it
                    }
                }
                "visualization" -> {
                    val renderer = block.optString("renderer").trim()
                    val kind = visualizationKind(renderer)
                    val params = block.optJSONObject("params")?.stringMap().orEmpty()
                    decodedBlocks += CourseVisualizationBlock(renderer, kind, params)
                    if (visualization == RationalVisualizationKind.HISTORY) visualization = kind
                }
                "source_excerpt" -> {
                    val bbox = block.optJSONArray("bbox")
                    if (bbox != null && bbox.length() == 4) {
                        decodedBlocks += CourseSourceExcerptBlock(
                            sourcePage = block.optInt("sourcePage", pageSource).coerceAtLeast(1),
                            left = bbox.optDouble(0).toFloat(),
                            top = bbox.optDouble(1).toFloat(),
                            right = bbox.optDouble(2).toFloat(),
                            bottom = bbox.optDouble(3).toFloat(),
                            fallbackText = block.optString("fallbackText").trim(),
                            altText = block.optString("altText", "教材原图").ifBlank { "教材原图" },
                        )
                    }
                }
            }
        }

        val sourcePage = pageSource
        val sourcePageEnd = page.optInt("sourcePageEnd", sourcePage).coerceAtLeast(sourcePage)
        return RationalLessonPage(
            id = page.getString("id"),
            section = page.optString("section", sectionTitle),
            title = page.getString("title"),
            paragraphs = paragraphs,
            sourcePage = sourcePage,
            sourcePageEnd = sourcePageEnd,
            visualization = visualization,
            formula = formula,
            conclusion = conclusion,
            blocks = decodedBlocks,
        )
    }

    private fun visualizationKind(renderer: String): RationalVisualizationKind = when (renderer.trim().lowercase()) {
        "opposite_quantities" -> RationalVisualizationKind.OPPOSITE_QUANTITIES
        "rational_classification" -> RationalVisualizationKind.RATIONAL_CLASSIFICATION
        "integer_to_fraction" -> RationalVisualizationKind.INTEGER_TO_FRACTION
        "number_line" -> RationalVisualizationKind.NUMBER_LINE
        "opposite_numbers" -> RationalVisualizationKind.OPPOSITE_NUMBERS
        "absolute_value" -> RationalVisualizationKind.ABSOLUTE_VALUE
        "number_comparison" -> RationalVisualizationKind.NUMBER_COMPARISON
        "addition_process", "signed_chips" -> RationalVisualizationKind.ADDITION_PROCESS
        "subtraction_transform", "expression_transform" -> RationalVisualizationKind.SUBTRACTION_TRANSFORM
        "multiplication_sign", "sign_rule" -> RationalVisualizationKind.MULTIPLICATION_SIGN
        "division_transform" -> RationalVisualizationKind.DIVISION_TRANSFORM
        "power_process" -> RationalVisualizationKind.POWER_PROCESS
        "algebra_process", "algebra_tiles" -> RationalVisualizationKind.ALGEBRA_PROCESS
        "equation_balance", "equation_process" -> RationalVisualizationKind.EQUATION_BALANCE
        "root_number_line", "square_root" -> RationalVisualizationKind.ROOT_NUMBER_LINE
        "cartesian_plane", "coordinate_plane" -> RationalVisualizationKind.CARTESIAN_PLANE
        "function_graph", "linear_function", "quadratic_function", "inverse_function" -> RationalVisualizationKind.FUNCTION_GRAPH
        "geometry", "triangle", "parallel_lines", "circle" -> RationalVisualizationKind.GEOMETRY
        "transformation", "translation", "rotation", "symmetry", "similarity" -> RationalVisualizationKind.TRANSFORMATION
        "right_triangle", "pythagorean", "trigonometry" -> RationalVisualizationKind.RIGHT_TRIANGLE
        "data_chart", "statistics", "histogram" -> RationalVisualizationKind.DATA_CHART
        "probability", "probability_tree" -> RationalVisualizationKind.PROBABILITY
        "projection", "three_views" -> RationalVisualizationKind.PROJECTION
        else -> RationalVisualizationKind.HISTORY
    }

    private fun JSONObject.stringMap(): Map<String, String> = buildMap {
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, opt(key)?.toString().orEmpty())
        }
    }

    private fun namesOf(json: JSONObject, vararg defaults: String): List<String> = buildList {
        defaults.filter(String::isNotBlank).forEach(::add)
        json.optJSONArray("aliases")?.strings()?.filter(String::isNotBlank)?.forEach(::add)
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private fun normalizeTitle(value: String): String = value
        .replace(" ", "")
        .replace("　", "")
        .replace("（", "(")
        .replace("）", ")")
        .trim()
}
