package com.majortomman.school.data.material

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class TextbookQuestionDraft(
    val id: String,
    val lessonId: String,
    val lessonSourceId: String,
    val page: Int,
    val excerpt: String,
    val knowledgePointId: String?,
    val typeHint: String,
    val quality: String = "DRAFT",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("lessonId", lessonId)
        .put("lessonSourceId", lessonSourceId)
        .put("page", page)
        .put("excerpt", excerpt)
        .put("knowledgePointId", knowledgePointId)
        .put("typeHint", typeHint)
        .put("quality", quality)

    companion object {
        fun fromJson(root: JSONObject): TextbookQuestionDraft = TextbookQuestionDraft(
            id = root.getString("id"),
            lessonId = root.getString("lessonId"),
            lessonSourceId = root.getString("lessonSourceId"),
            page = root.getInt("page"),
            excerpt = root.getString("excerpt"),
            knowledgePointId = root.optString("knowledgePointId").takeIf { it.isNotBlank() && it != "null" },
            typeHint = root.optString("typeHint", "TEXT"),
            quality = root.optString("quality", "DRAFT"),
        )
    }
}

internal object TextbookQuestionDraftStore {
    private const val QUESTION_FILE = "generated/questions/textbook-drafts.json"

    fun extractAndWrite(
        textbookRoot: File,
        lesson: GeneratedLesson,
        pages: List<OcrPageResult>,
    ): List<TextbookQuestionDraft> {
        val extracted = pages.flatMap { page -> extractPage(lesson, page) }
            .distinctBy { it.id }
            .take(MAX_DRAFTS_PER_LESSON)
        val current = read(textbookRoot).filterNot { it.lessonSourceId == lesson.sourceId }
        write(textbookRoot, current + extracted)
        return extracted
    }

    fun read(textbookRoot: File): List<TextbookQuestionDraft> = runCatching {
        val file = File(textbookRoot, QUESTION_FILE)
        if (!file.isFile) return@runCatching emptyList()
        val array = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("questions") ?: JSONArray()
        buildList {
            for (index in 0 until array.length()) {
                add(TextbookQuestionDraft.fromJson(array.getJSONObject(index)))
            }
        }
    }.getOrDefault(emptyList())

    fun hasDrafts(textbookRoot: File, lessonSourceId: String): Boolean =
        read(textbookRoot).any { it.lessonSourceId == lessonSourceId }

    private fun extractPage(
        lesson: GeneratedLesson,
        page: OcrPageResult,
    ): List<TextbookQuestionDraft> {
        if (!page.isUsable) return emptyList()
        val lines = page.lines.map { it.text.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        return buildList {
            lines.forEachIndexed { index, line ->
                if (!looksLikeQuestion(line)) return@forEachIndexed
                val excerpt = lines.subList(index, minOf(lines.size, index + 4))
                    .joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .take(480)
                if (excerpt.length < 8) return@forEachIndexed
                val knowledgePoint = inferKnowledgePoint("${lesson.title} $excerpt")
                add(
                    TextbookQuestionDraft(
                        id = stableId("${lesson.sourceId}:${page.printedPage}:$excerpt"),
                        lessonId = lesson.id,
                        lessonSourceId = lesson.sourceId,
                        page = page.printedPage,
                        excerpt = excerpt,
                        knowledgePointId = knowledgePoint,
                        typeHint = inferType(excerpt),
                    ),
                )
            }
        }
    }

    private fun looksLikeQuestion(line: String): Boolean {
        val compact = line.replace(" ", "")
        return questionPrefix.containsMatchIn(compact) ||
            compact.endsWith("？") || compact.endsWith("?") ||
            compact.contains("求出") || compact.contains("计算") ||
            compact.contains("比较") || compact.contains("化简") || compact.contains("解方程")
    }

    private fun inferKnowledgePoint(text: String): String? = when {
        text.contains("绝对值") || text.contains("距离") -> "absolute-value"
        text.contains("相反数") || text.contains("相反") -> "opposite-number"
        text.contains("数轴") || text.contains("原点") -> "number-line"
        text.contains("比较") || text.contains("从小到大") || text.contains("从大到小") -> "rational-compare"
        text.contains("方程") || text.contains("移项") -> "linear-equation"
        text.contains("化简") || text.contains("去括号") || text.contains("同类项") -> "expression-equivalence"
        text.contains("正数") || text.contains("负数") -> "positive-negative"
        else -> null
    }

    private fun inferType(text: String): String = when {
        text.contains("选择") -> "SINGLE_CHOICE"
        text.contains("从小到大") || text.contains("从大到小") || text.contains("排列") -> "ORDERING"
        text.contains("数轴") && (text.contains("表示") || text.contains("标出")) -> "NUMBER_LINE_POINT"
        text.contains("解方程") || text.contains("每一步") -> "STEP_BY_STEP"
        text.contains("化简") -> "EXPRESSION_INPUT"
        else -> "NUMERIC_INPUT"
    }

    private fun write(root: File, drafts: List<TextbookQuestionDraft>) {
        val file = File(root, QUESTION_FILE)
        file.parentFile?.let { parent ->
            require(parent.mkdirs() || parent.isDirectory) { "无法创建教材题目目录" }
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(
            JSONObject()
                .put("schemaVersion", 1)
                .put("questions", JSONArray().apply { drafts.forEach { put(it.toJson()) } })
                .toString(2),
            Charsets.UTF_8,
        )
        if (file.exists()) file.delete()
        require(temporary.renameTo(file)) { "无法保存教材题目线索" }
    }

    private fun stableId(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(24)

    private val questionPrefix = Regex("^(例\\d*|例题\\d*|练习|习题|问题|思考|探究|做一做|试一试|随堂练习|课后练习)")
    private const val MAX_DRAFTS_PER_LESSON = 18
}
