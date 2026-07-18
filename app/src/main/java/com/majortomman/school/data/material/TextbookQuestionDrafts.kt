package com.majortomman.school.data.material

import java.io.File
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

/** Reads question drafts supplied by the cloud package. No local OCR extraction is performed. */
internal object TextbookQuestionDraftStore {
    private const val QUESTION_FILE = "generated/questions/textbook-drafts.json"

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
}
