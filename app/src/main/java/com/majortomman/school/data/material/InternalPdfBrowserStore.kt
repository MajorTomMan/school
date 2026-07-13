package com.majortomman.school.data.material

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

internal data class PdfLibraryDirectory(
    val uri: String,
    val name: String,
)

internal data class PdfLibraryEntry(
    val uri: String,
    val name: String,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val inferredStage: EducationStage?,
    val inferredSubjectId: String?,
    val inferredGrade: Int?,
    val inferredVolume: TextbookVolume?,
) {
    fun matchScore(slot: TextbookSlot): Int {
        var score = 0
        if (inferredStage == slot.stage) score += 24
        if (inferredSubjectId == slot.subjectId) score += 34
        if (inferredGrade == slot.grade) score += 28
        if (inferredVolume == slot.volume) score += 14
        if (slot.subjectTitle in name) score += 8
        if (gradeLabel(slot.grade) in name) score += 8
        return score
    }

    fun inferredLabel(): String = listOfNotNull(
        inferredStage?.label,
        inferredSubjectId?.let { SubjectTemplates.find(it)?.title },
        inferredGrade?.let(::gradeLabel),
        inferredVolume?.let { volume -> inferredStage?.let(volume::labelFor) ?: volume.label },
    ).joinToString(" · ").ifBlank { "尚未识别教材信息" }

    fun sizeLabel(): String = when {
        sizeBytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(sizeBytes / (1024.0 * 1024.0 * 1024.0))
        sizeBytes >= 1024L * 1024L -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
        sizeBytes >= 1024L -> "%.1f KB".format(sizeBytes / 1024.0)
        sizeBytes > 0L -> "$sizeBytes B"
        else -> "大小未知"
    }

    fun modifiedLabel(): String = if (modifiedAt <= 0L) {
        "时间未知"
    } else {
        Instant.ofEpochMilli(modifiedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
}

internal data class PdfLibraryScanResult(
    val directories: List<PdfLibraryDirectory>,
    val files: List<PdfLibraryEntry>,
    val errors: List<String>,
)

internal object InternalPdfBrowserStore {
    private const val PREFS_NAME = "school_pdf_directories"
    private const val KEY_DIRECTORY_URIS = "directory_uris"
    private const val MAX_DEPTH = 12
    private const val MAX_DOCUMENTS = 8_000

    fun directories(context: Context): List<PdfLibraryDirectory> {
        val resolver = context.contentResolver
        return storedUris(context).mapNotNull { raw ->
            val uri = runCatching(Uri::parse).getOrNull() ?: return@mapNotNull null
            val name = queryDocumentName(resolver, uri).ifBlank { uri.lastPathSegment ?: "教材目录" }
            PdfLibraryDirectory(uri.toString(), name)
        }.sortedBy { it.name.lowercase() }
    }

    fun addDirectory(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val values = storedUris(context).toMutableSet()
        values += uri.toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_DIRECTORY_URIS, values)
            .apply()
    }

    fun removeDirectory(context: Context, uri: String) {
        val values = storedUris(context).toMutableSet()
        values -= uri
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_DIRECTORY_URIS, values)
            .apply()
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun scan(context: Context, slot: TextbookSlot): PdfLibraryScanResult {
        val resolver = context.contentResolver
        val directories = directories(context)
        val files = mutableListOf<PdfLibraryEntry>()
        val errors = mutableListOf<String>()
        var visited = 0

        directories.forEach { directory ->
            val treeUri = Uri.parse(directory.uri)
            runCatching {
                val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                val queue = ArrayDeque<PendingDirectory>()
                queue += PendingDirectory(rootId, directory.name, 0)
                while (queue.isNotEmpty() && visited < MAX_DOCUMENTS) {
                    val current = queue.removeFirst()
                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, current.documentId)
                    resolver.query(
                        childrenUri,
                        PROJECTION,
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                        val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        while (cursor.moveToNext() && visited < MAX_DOCUMENTS) {
                            visited += 1
                            val documentId = cursor.getString(idIndex) ?: continue
                            val name = cursor.getString(nameIndex).orEmpty().ifBlank { "未命名" }
                            val mime = cursor.getString(mimeIndex).orEmpty()
                            val relativePath = "${current.relativePath}/$name"
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                if (current.depth < MAX_DEPTH) {
                                    queue += PendingDirectory(documentId, relativePath, current.depth + 1)
                                }
                            } else if (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) {
                                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                                val inferred = infer(name)
                                files += PdfLibraryEntry(
                                    uri = documentUri.toString(),
                                    name = name,
                                    relativePath = relativePath,
                                    sizeBytes = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L,
                                    modifiedAt = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L,
                                    inferredStage = inferred.stage,
                                    inferredSubjectId = inferred.subjectId,
                                    inferredGrade = inferred.grade,
                                    inferredVolume = inferred.volume,
                                )
                            }
                        }
                    }
                }
            }.onFailure { error ->
                errors += "${directory.name}：${error.message ?: "目录权限已失效"}"
            }
        }

        return PdfLibraryScanResult(
            directories = directories,
            files = files.distinctBy { it.uri }.sortedWith(
                compareByDescending<PdfLibraryEntry> { it.matchScore(slot) }
                    .thenByDescending { it.modifiedAt }
                    .thenBy { it.name.lowercase() },
            ),
            errors = errors,
        )
    }

    private fun infer(name: String): InferredBook {
        val compact = name
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "")
            .lowercase()

        val grade = gradeTokens.entries.firstOrNull { (_, tokens) -> tokens.any { it.lowercase() in compact } }?.key
        val stage = when {
            grade != null -> EducationStage.fromGrade(grade)
            "小学" in compact -> EducationStage.PRIMARY
            "初中" in compact || "义务教育" in compact -> EducationStage.JUNIOR_HIGH
            "高中" in compact || "普通高中" in compact -> EducationStage.SENIOR_HIGH
            "大学" in compact || "高等数学" in compact || "大学物理" in compact -> EducationStage.UNIVERSITY
            else -> null
        }
        val subjectId = SubjectTemplates.all.firstOrNull { subject ->
            subject.title.lowercase() in compact || subjectAliases[subject.id].orEmpty().any { it.lowercase() in compact }
        }?.id
        val volume = when {
            volumeSecondTokens.any { it.lowercase() in compact } -> TextbookVolume.SECOND
            volumeFirstTokens.any { it.lowercase() in compact } -> TextbookVolume.FIRST
            else -> null
        }
        return InferredBook(stage, subjectId, grade, volume)
    }

    private fun queryDocumentName(resolver: ContentResolver, treeUri: Uri): String = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    private fun storedUris(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_DIRECTORY_URIS, emptySet())
            .orEmpty()
            .toSet()

    private data class PendingDirectory(
        val documentId: String,
        val relativePath: String,
        val depth: Int,
    )

    private data class InferredBook(
        val stage: EducationStage?,
        val subjectId: String?,
        val grade: Int?,
        val volume: TextbookVolume?,
    )

    private val PROJECTION = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    private val gradeTokens = mapOf(
        1 to listOf("一年级", "1年级"),
        2 to listOf("二年级", "2年级"),
        3 to listOf("三年级", "3年级"),
        4 to listOf("四年级", "4年级"),
        5 to listOf("五年级", "5年级"),
        6 to listOf("六年级", "6年级"),
        7 to listOf("七年级", "7年级", "初一"),
        8 to listOf("八年级", "8年级", "初二"),
        9 to listOf("九年级", "9年级", "初三"),
        10 to listOf("高一", "高中一年级"),
        11 to listOf("高二", "高中二年级"),
        12 to listOf("高三", "高中三年级"),
        13 to listOf("大一"),
        14 to listOf("大二"),
        15 to listOf("大三"),
        16 to listOf("大四"),
    )

    private val subjectAliases = mapOf(
        "math" to listOf("数学", "高等数学", "线性代数", "概率论"),
        "chinese" to listOf("语文", "大学语文"),
        "english" to listOf("英语", "english"),
        "physics" to listOf("物理", "大学物理"),
        "chemistry" to listOf("化学"),
        "biology" to listOf("生物"),
        "history" to listOf("历史"),
        "geography" to listOf("地理"),
        "politics" to listOf("道德与法治", "思想政治", "政治"),
        "computer" to listOf("计算机", "数据结构", "操作系统", "编程", "程序设计"),
        "economics" to listOf("经济学", "宏观经济", "微观经济"),
        "law" to listOf("法学", "法律"),
        "science" to listOf("科学"),
    )

    private val volumeFirstTokens = listOf("上册", "第一册", "上学期", "必修第一册", "必修1", "必修一")
    private val volumeSecondTokens = listOf("下册", "第二册", "下学期", "必修第二册", "必修2", "必修二")
}
