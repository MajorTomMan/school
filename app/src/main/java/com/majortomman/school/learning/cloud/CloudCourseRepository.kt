package com.majortomman.school.learning.cloud

import android.content.Context
import com.majortomman.school.learning.course.CourseBlock
import com.majortomman.school.learning.course.CourseChapter
import com.majortomman.school.learning.course.CourseConclusion
import com.majortomman.school.learning.course.CourseDocument
import com.majortomman.school.learning.course.CourseExample
import com.majortomman.school.learning.course.CourseExercise
import com.majortomman.school.learning.course.CourseFormula
import com.majortomman.school.learning.course.CourseHeading
import com.majortomman.school.learning.course.CourseList
import com.majortomman.school.learning.course.CoursePage
import com.majortomman.school.learning.course.CoursePdf
import com.majortomman.school.learning.course.CourseScene
import com.majortomman.school.learning.course.CourseSceneBlock
import com.majortomman.school.learning.course.CourseSceneData
import com.majortomman.school.learning.course.CourseSceneTemplate
import com.majortomman.school.learning.course.CourseSection
import com.majortomman.school.learning.course.CourseText
import com.majortomman.school.learning.course.CourseTextStyle
import com.majortomman.school.learning.course.CourseTextbook
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

    fun hasInstalledCourseContent(): Boolean = courseDocuments().isNotEmpty()

    fun supports(title: String): Boolean = courseDocuments().any { it.document.pagesFor(title).isNotEmpty() }

    fun pagesFor(title: String, sourcePages: IntRange): List<CoursePage> {
        val candidates = courseDocuments().mapNotNull { cached ->
            cached.document.pagesFor(title).takeIf(List<CoursePage>::isNotEmpty)
        }
        if (candidates.size <= 1) return candidates.firstOrNull().orEmpty()
        return candidates.firstOrNull { pages ->
            pages.any { page -> page.sourcePage <= sourcePages.last && page.sourcePageEnd >= sourcePages.first }
        }.orEmpty().ifEmpty { candidates.first() }
    }

    internal fun markContentChanged() {
        synchronized(cacheLock) { parsedCache.clear() }
        mutableRevision.value += 1L
    }

    private fun courseDocuments(): List<CachedCourseDocument> {
        val context = appContext ?: return emptyList()
        val activeRoot = File(context.filesDir, ACTIVE_DIRECTORY)
        return activeRoot.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .map { File(it, COURSE_FILE_NAME) }
            .filter(File::isFile)
            .mapNotNull { file ->
                synchronized(cacheLock) {
                    val key = file.absolutePath
                    val cached = parsedCache[key]
                    if (cached != null && cached.lastModified == file.lastModified() && cached.length == file.length()) {
                        cached
                    } else {
                        runCatching {
                            val document = CourseDocumentParser.decode(file.readText(Charsets.UTF_8))
                            val root = file.parentFile ?: error("课程文件缺少安装目录")
                            val pdf = File(root, document.textbook.pdf.path)
                            require(pdf.isFile) { "课程包缺少教材 PDF：${document.textbook.pdf.path}" }
                            CachedCourseDocument(file.lastModified(), file.length(), document)
                        }.getOrNull()?.also { parsedCache[key] = it }
                    }
                }
            }
    }

    private data class CachedCourseDocument(
        val lastModified: Long,
        val length: Long,
        val document: CourseDocument,
    )

    private const val COURSE_FILE_NAME = "course.json"
}

internal object CourseDocumentParser {
    fun decode(raw: String): CourseDocument = decode(JSONObject(raw))

    fun decode(root: JSONObject): CourseDocument {
        root.requireShape(required = setOf("textbook", "chapters"))
        val textbook = decodeTextbook(root.requireObject("textbook"))
        val chapterArray = root.requireArray("chapters")
        require(chapterArray.length() > 0) { "课程不包含章节" }
        val seenIds = linkedSetOf<String>()
        val chapters = chapterArray.objects().map { decodeChapter(it, textbook.pdf, seenIds) }
        require(chapters.isNotEmpty()) { "课程不包含章节" }
        return CourseDocument(textbook, chapters)
    }

    private fun decodeTextbook(json: JSONObject): CourseTextbook {
        json.requireShape(required = setOf("id", "title", "publisher", "edition", "grade", "semester", "subject", "pdf"))
        val pdfJson = json.requireObject("pdf")
        pdfJson.requireShape(required = setOf("path", "pageCount", "pageIndexOffset"))
        val pdf = CoursePdf(
            path = validateRelativePath(pdfJson.requireText("path")).also {
                require(it.endsWith(".pdf", ignoreCase = true)) { "textbook.pdf.path 必须指向 PDF 文件" }
            },
            pageCount = pdfJson.requirePositiveInt("pageCount"),
            pageIndexOffset = pdfJson.optInt("pageIndexOffset", 0).also {
                require(it in -10_000..10_000) { "textbook.pdf.pageIndexOffset 超出允许范围" }
            },
        )
        return CourseTextbook(
            id = json.requireIdentifier("id", "教材 ID"),
            title = json.requireText("title"),
            publisher = json.requireText("publisher"),
            edition = json.requireText("edition"),
            grade = json.requireText("grade"),
            semester = json.requireText("semester"),
            subject = json.requireText("subject"),
            pdf = pdf,
        )
    }

    private fun decodeChapter(
        json: JSONObject,
        pdf: CoursePdf,
        seenIds: MutableSet<String>,
    ): CourseChapter {
        json.requireShape(required = setOf("id", "number", "title", "aliases", "sections"), optional = setOf("review"))
        val id = json.requireIdentifier("id", "章节 ID").also { requireUniqueId(it, seenIds) }
        val title = json.requireText("title")
        val sections = json.requireArray("sections").objects().map {
            decodeSection(it, pdf, seenIds, fallbackTitle = title)
        }
        require(sections.isNotEmpty()) { "章节 $id 不包含小节" }
        val review = json.optJSONObject("review")?.let {
            decodeSection(it, pdf, seenIds, fallbackTitle = "$title 小结")
        }
        return CourseChapter(
            id = id,
            number = json.optString("number").trim(),
            title = title,
            aliases = json.optionalStrings("aliases"),
            sections = sections,
            review = review,
        )
    }

    private fun decodeSection(
        json: JSONObject,
        pdf: CoursePdf,
        seenIds: MutableSet<String>,
        fallbackTitle: String,
    ): CourseSection {
        json.requireShape(required = setOf("id", "title", "aliases", "pages"), optional = setOf("number"))
        val id = json.requireIdentifier("id", "小节 ID").also { requireUniqueId(it, seenIds) }
        val title = json.optString("title").trim().ifBlank { fallbackTitle }
        val pages = json.requireArray("pages").objects().map { decodePage(it, title, pdf, seenIds) }
        require(pages.isNotEmpty()) { "小节 $id 不包含课程页" }
        return CourseSection(
            id = id,
            number = json.optString("number").trim(),
            title = title,
            aliases = json.optionalStrings("aliases"),
            pages = pages,
        )
    }

    private fun decodePage(
        json: JSONObject,
        sectionTitle: String,
        pdf: CoursePdf,
        seenIds: MutableSet<String>,
    ): CoursePage {
        json.requireShape(required = setOf("id", "title", "sourcePage", "blocks"), optional = setOf("aliases", "sourcePageEnd"))
        val id = json.requireIdentifier("id", "课程页 ID").also { requireUniqueId(it, seenIds) }
        val sourcePage = json.requirePositiveInt("sourcePage")
        val sourcePageEnd = json.optInt("sourcePageEnd", sourcePage)
        require(sourcePageEnd >= sourcePage) { "课程页 $id 的教材页码范围无效" }
        require(sourcePageEnd <= pdf.pageCount) { "课程页 $id 超出教材 PDF 页数" }
        val blocks = json.requireArray("blocks").objects().mapIndexed { index, block ->
            decodeBlock(block, "$id.blocks[$index]")
        }
        require(blocks.isNotEmpty()) { "课程页 $id 不包含内容" }
        return CoursePage(
            id = id,
            section = sectionTitle,
            title = json.requireText("title"),
            aliases = json.optionalStrings("aliases"),
            sourcePage = sourcePage,
            sourcePageEnd = sourcePageEnd,
            blocks = blocks,
        )
    }

    private fun decodeBlock(json: JSONObject, location: String): CourseBlock = when (val type = json.requireText("type")) {
        "heading" -> {
            json.requireShape(required = setOf("type", "text"))
            CourseHeading(json.requireText("text"))
        }
        "text" -> {
            json.requireShape(required = setOf("type", "style", "text"))
            val style = json.requireText("style").uppercase().let { value ->
                CourseTextStyle.entries.firstOrNull { it.name == value }
                    ?: error("$location 使用了不支持的文本样式：${json.getString("style")}")
            }
            CourseText(json.requireText("text"), style)
        }
        "formula" -> {
            json.requireShape(required = setOf("type", "expression"), optional = setOf("conditions"))
            CourseFormula(json.requireText("expression"), json.optionalStrings("conditions"))
        }
        "list" -> {
            json.requireShape(required = setOf("type", "items"))
            val items = json.requireStrings("items")
            require(items.isNotEmpty()) { "$location 的列表不能为空" }
            CourseList(items)
        }
        "example" -> {
            json.requireShape(required = setOf("type", "statement"), optional = setOf("label", "steps", "result"))
            CourseExample(
                label = json.optString("label").trim().ifBlank { "例题" },
                statement = json.requireText("statement"),
                steps = json.optionalStrings("steps"),
                result = json.optString("result").trim().takeIf(String::isNotBlank),
            )
        }
        "exercise" -> {
            json.requireShape(required = setOf("type", "stem"), optional = setOf("number", "choices", "hints"))
            CourseExercise(
                number = json.optString("number").trim(),
                stem = json.requireText("stem"),
                choices = json.optionalStrings("choices"),
                hints = json.optionalStrings("hints"),
            )
        }
        "conclusion" -> {
            json.requireShape(required = setOf("type", "text"))
            CourseConclusion(json.requireText("text"))
        }
        "scene" -> {
            json.requireShape(required = setOf("type", "template", "data"))
            val templateId = json.requireText("template")
            val template = CourseSceneTemplate.fromId(templateId)
                ?: error("$location 使用了 APK 不支持的场景：$templateId")
            val data = decodeSceneData(template, json.optJSONObject("data") ?: JSONObject(), location)
            CourseSceneBlock(CourseScene(template, data))
        }
        else -> error("$location 使用了不支持的内容类型：$type")
    }

    private fun decodeSceneData(
        template: CourseSceneTemplate,
        json: JSONObject,
        location: String,
    ): CourseSceneData {
        val allowed = when (template) {
            CourseSceneTemplate.OPPOSITE_QUANTITIES -> setOf("title", "scene", "scenes")
            CourseSceneTemplate.RATIONAL_CLASSIFICATION -> setOf("title", "mode")
            CourseSceneTemplate.NUMBER_LINE -> setOf("title", "mode", "signed", "initial")
            CourseSceneTemplate.SUBTRACTION_TRANSFORM,
            CourseSceneTemplate.DIVISION_TRANSFORM,
            -> setOf("title", "expression")
            CourseSceneTemplate.ALGEBRA_PROCESS,
            CourseSceneTemplate.EQUATION_BALANCE,
            -> setOf("title", "left", "right", "note")
            CourseSceneTemplate.FUNCTION_GRAPH -> setOf("title", "function", "note")
            CourseSceneTemplate.GEOMETRY -> setOf("title", "shape", "note")
            CourseSceneTemplate.TRANSFORMATION -> setOf("title", "mode", "note")
            CourseSceneTemplate.RIGHT_TRIANGLE -> setOf("title", "formula", "note")
            CourseSceneTemplate.DATA_CHART -> setOf("title", "mode", "note")
            CourseSceneTemplate.ROOT_NUMBER_LINE,
            CourseSceneTemplate.CARTESIAN_PLANE,
            CourseSceneTemplate.PROBABILITY,
            CourseSceneTemplate.PROJECTION,
            -> setOf("title", "note")
            CourseSceneTemplate.DECLARATIVE_DIAGRAM -> setOf("height", "elements")
            else -> setOf("title")
        }
        json.requireShape(required = emptySet(), optional = allowed)
        val values = linkedMapOf<String, Any?>()
        json.keys().forEach { key -> values[key] = decodeJsonValue(json.get(key), "$location.data.$key") }

        fun optionalString(key: String, allowedValues: Set<String>? = null) {
            if (!values.containsKey(key)) return
            val value = values[key]
            require(value is String && value.isNotBlank()) { "$location.data.$key 必须是非空字符串" }
            if (allowedValues != null) require(value in allowedValues) {
                "$location.data.$key 的值不受支持：$value"
            }
        }
        listOf("title", "left", "right", "note", "expression", "formula").forEach(::optionalString)
        when (template) {
            CourseSceneTemplate.OPPOSITE_QUANTITIES -> {
                val allowedScenes = setOf("temperature", "account", "elevation", "change", "tolerance", "deviation")
                optionalString("scene", allowedScenes)
                values["scenes"]?.let { raw ->
                    require(raw is List<*> && raw.isNotEmpty() && raw.all { it is String && it in allowedScenes }) {
                        "$location.data.scenes 必须是受支持的非空场景数组"
                    }
                }
            }
            CourseSceneTemplate.RATIONAL_CLASSIFICATION -> optionalString("mode", setOf("definition", "fraction_form"))
            CourseSceneTemplate.NUMBER_LINE -> {
                optionalString("mode", setOf("road", "construction", "value", "example", "read_points"))
                values["signed"]?.let { require(it is Boolean) { "$location.data.signed 必须是布尔值" } }
                values["initial"]?.let { require(it is Number && it.toDouble().isFinite()) { "$location.data.initial 必须是有限数" } }
            }
            CourseSceneTemplate.FUNCTION_GRAPH -> optionalString("function", setOf("linear", "quadratic", "inverse"))
            CourseSceneTemplate.GEOMETRY -> optionalString("shape", setOf("triangle", "parallel", "circle"))
            CourseSceneTemplate.TRANSFORMATION -> optionalString("mode", setOf("translation", "rotation", "symmetry"))
            CourseSceneTemplate.DATA_CHART -> optionalString("mode", setOf("bar", "line"))
            CourseSceneTemplate.DECLARATIVE_DIAGRAM -> validateDeclarativeDiagram(values, location)
            else -> Unit
        }
        return CourseSceneData(values)
    }

    private fun validateDeclarativeDiagram(values: Map<String, Any?>, location: String) {
        values["height"]?.let {
            require(it is Number && it.toDouble() in 120.0..1000.0) { "$location.data.height 超出允许范围" }
        }
        val elements = values["elements"]
        require(elements is List<*> && elements.isNotEmpty()) { "$location.data.elements 必须是非空数组" }
        elements.forEachIndexed { index, raw ->
            require(raw is Map<*, *>) { "$location.data.elements[$index] 必须是对象" }
            @Suppress("UNCHECKED_CAST")
            validateDiagramElement(raw as Map<String, Any?>, "$location.data.elements[$index]")
        }
    }

    private fun validateDiagramElement(element: Map<String, Any?>, location: String) {
        val type = element["type"] as? String ?: error("$location 缺少 type")
        val common = setOf("type", "color", "stroke")
        val allowed = common + when (type) {
            "line", "arrow" -> setOf("x1", "y1", "x2", "y2")
            "point" -> setOf("x", "y", "radius")
            "circle" -> setOf("x", "y", "radius")
            "rectangle" -> setOf("x", "y", "width", "height")
            "text" -> setOf("x", "y", "text", "size")
            "polyline" -> setOf("points")
            "number_line" -> setOf("x1", "x2", "y", "min", "max", "step")
            else -> error("$location 使用了不支持的图元：$type")
        }
        val unknown = element.keys - allowed
        require(unknown.isEmpty()) { "$location 包含未知字段：${unknown.joinToString()}" }
        element["color"]?.let { require(it in setOf("blue", "yellow", "muted", "white")) { "$location.color 不受支持" } }
        element["stroke"]?.let { requireNumber(it, "$location.stroke", 0.5..20.0) }
        fun ratio(key: String, required: Boolean = true) {
            val value = element[key]
            if (required || value != null) requireNumber(value, "$location.$key", 0.0..1.0)
        }
        when (type) {
            "line", "arrow" -> listOf("x1", "y1", "x2", "y2").forEach(::ratio)
            "point", "circle" -> {
                ratio("x"); ratio("y"); requireNumber(element["radius"], "$location.radius", 0.001..1.0)
            }
            "rectangle" -> {
                ratio("x"); ratio("y"); requireNumber(element["width"], "$location.width", 0.001..1.0)
                requireNumber(element["height"], "$location.height", 0.001..1.0)
            }
            "text" -> {
                ratio("x"); ratio("y")
                require((element["text"] as? String)?.isNotBlank() == true) { "$location.text 必须是非空字符串" }
                element["size"]?.let { requireNumber(it, "$location.size", 8.0..72.0) }
            }
            "polyline" -> {
                val points = element["points"]
                require(points is List<*> && points.size >= 2) { "$location.points 至少包含两个点" }
                points.forEachIndexed { index, point ->
                    require(point is Map<*, *> && point.keys == setOf("x", "y")) { "$location.points[$index] 格式无效" }
                    requireNumber(point["x"], "$location.points[$index].x", 0.0..1.0)
                    requireNumber(point["y"], "$location.points[$index].y", 0.0..1.0)
                }
            }
            "number_line" -> {
                ratio("x1", false); ratio("x2", false); ratio("y", false)
                val minimum = (element["min"] as? Number)?.toDouble() ?: -5.0
                val maximum = (element["max"] as? Number)?.toDouble() ?: 5.0
                val step = (element["step"] as? Number)?.toDouble() ?: 1.0
                require(minimum.isFinite() && maximum.isFinite() && minimum < maximum) { "$location 的数轴范围无效" }
                require(step.isFinite() && step > 0.0 && (maximum - minimum) / step <= 200.0) { "$location.step 无效" }
            }
        }
    }

    private fun requireNumber(value: Any?, location: String, range: ClosedFloatingPointRange<Double>) {
        require(value is Number && value.toDouble().isFinite() && value.toDouble() in range) {
            "$location 必须是 ${range.start}..${range.endInclusive} 范围内的数"
        }
    }

    private fun decodeJsonValue(value: Any?, location: String): Any? = when (value) {
        JSONObject.NULL, null -> null
        is String, is Boolean, is Int, is Long, is Double -> value
        is Number -> value.toDouble()
        is JSONObject -> buildMap {
            value.keys().forEach { key -> put(key, decodeJsonValue(value.get(key), "$location.$key")) }
        }
        is JSONArray -> buildList {
            for (index in 0 until value.length()) add(decodeJsonValue(value.get(index), "$location[$index]"))
        }
        else -> error("$location 包含不支持的数据类型")
    }

    private fun CourseDocument.pagesFor(title: String): List<CoursePage> {
        val requested = normalizeTitle(title)
        chapters.forEach { chapter ->
            if (chapter.names().any { normalizeTitle(it) == requested }) {
                return chapter.sections.flatMap(CourseSection::pages) + chapter.review?.pages.orEmpty()
            }
            chapter.sections.forEach { section ->
                if (section.names().any { normalizeTitle(it) == requested }) return section.pages
                section.pages.firstOrNull { page -> page.names().any { normalizeTitle(it) == requested } }
                    ?.let { return listOf(it) }
            }
            chapter.review?.let { review ->
                if (review.names().any { normalizeTitle(it) == requested }) return review.pages
                review.pages.firstOrNull { page -> page.names().any { normalizeTitle(it) == requested } }
                    ?.let { return listOf(it) }
            }
        }
        return emptyList()
    }

    private fun CourseChapter.names(): List<String> = buildList {
        add(title)
        if (number.isNotBlank()) add(number + title)
        addAll(aliases)
    }

    private fun CourseSection.names(): List<String> = buildList {
        add(title)
        if (number.isNotBlank()) add(number + title)
        addAll(aliases)
    }

    private fun CoursePage.names(): List<String> = listOf(title) + aliases

    private fun normalizeTitle(value: String): String = value
        .replace(" ", "")
        .replace("　", "")
        .replace("（", "(")
        .replace("）", ")")
        .trim()

    private fun requireUniqueId(id: String, seen: MutableSet<String>) {
        require(seen.add(id)) { "课程包含重复 ID：$id" }
    }
}

private fun JSONObject.requireShape(
    required: Set<String>,
    optional: Set<String> = emptySet(),
) {
    val actual = keys().asSequence().toSet()
    val unknown = actual - required - optional
    val missing = required - actual
    require(unknown.isEmpty()) { "课程包含 APK 不识别的字段：${unknown.sorted().joinToString()}" }
    require(missing.isEmpty()) { "课程缺少必需字段：${missing.sorted().joinToString()}" }
}

private fun JSONObject.requireObject(key: String): JSONObject =
    optJSONObject(key) ?: error("课程缺少对象字段：$key")

private fun JSONObject.requireArray(key: String): JSONArray =
    optJSONArray(key) ?: error("课程缺少数组字段：$key")

private fun JSONObject.requireText(key: String): String =
    optString(key).trim().also { require(it.isNotBlank()) { "课程字段 $key 不能为空" } }

private fun JSONObject.requireIdentifier(key: String, label: String): String =
    requireText(key).also { require(it.matches(Regex("[A-Za-z0-9._-]+"))) { "$label 格式无效：$it" } }

private fun JSONObject.requirePositiveInt(key: String): Int =
    getInt(key).also { require(it > 0) { "课程字段 $key 必须大于 0" } }

private fun JSONObject.optionalStrings(key: String): List<String> =
    optJSONArray(key)?.strings().orEmpty().also { values ->
        require(values.all(String::isNotBlank)) { "课程字段 $key 不能包含空文本" }
        require(values.distinct().size == values.size) { "课程字段 $key 不能包含重复文本" }
    }

private fun JSONObject.requireStrings(key: String): List<String> =
    requireArray(key).strings().also { values -> require(values.all(String::isNotBlank)) { "课程字段 $key 不能包含空文本" } }

private fun JSONArray.strings(): List<String> = buildList {
    for (index in 0 until length()) add(getString(index).trim())
}

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (index in 0 until length()) add(getJSONObject(index))
}
