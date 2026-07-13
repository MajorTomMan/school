package com.majortomman.school.data.material

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val TEXTBOOK_OCR_SCHEMA_VERSION = 1

data class OcrTextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("text", text)
        .put("left", left.toDouble())
        .put("top", top.toDouble())
        .put("right", right.toDouble())
        .put("bottom", bottom.toDouble())

    companion object {
        fun fromJson(root: JSONObject): OcrTextLine = OcrTextLine(
            text = root.optString("text").trim(),
            left = root.optDouble("left", 0.0).toFloat().coerceIn(0f, 1f),
            top = root.optDouble("top", 0.0).toFloat().coerceIn(0f, 1f),
            right = root.optDouble("right", 1.0).toFloat().coerceIn(0f, 1f),
            bottom = root.optDouble("bottom", 1.0).toFloat().coerceIn(0f, 1f),
        )
    }
}

data class OcrPageResult(
    val schemaVersion: Int = TEXTBOOK_OCR_SCHEMA_VERSION,
    val printedPage: Int,
    val pdfIndex: Int,
    val width: Int,
    val height: Int,
    val text: String,
    val lines: List<OcrTextLine>,
    val engine: String = "ML_KIT_CHINESE",
) {
    val compactText: String
        get() = text.replace(Regex("\\s+"), " ").trim()

    val isUsable: Boolean
        get() {
            val compact = compactText.filterNot(Char::isWhitespace)
            if (compact.length < MIN_USABLE_CHARACTERS) return false
            val meaningful = compact.count { character ->
                character.isLetterOrDigit() || character in "，。；：！？、（）()[]【】+-×÷=<>|%°"
            }
            return meaningful.toFloat() / compact.length.coerceAtLeast(1) >= MIN_MEANINGFUL_RATIO
        }

    fun toJson(): JSONObject = JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("printedPage", printedPage)
        .put("pdfIndex", pdfIndex)
        .put("width", width)
        .put("height", height)
        .put("engine", engine)
        .put("text", text)
        .put("lines", JSONArray().apply { lines.forEach { put(it.toJson()) } })

    companion object {
        private const val MIN_USABLE_CHARACTERS = 24
        private const val MIN_MEANINGFUL_RATIO = 0.45f

        fun fromJson(root: JSONObject): OcrPageResult {
            val lineArray = root.optJSONArray("lines") ?: JSONArray()
            val lines = buildList {
                for (index in 0 until lineArray.length()) {
                    add(OcrTextLine.fromJson(lineArray.getJSONObject(index)))
                }
            }
            return OcrPageResult(
                schemaVersion = root.optInt("schemaVersion", TEXTBOOK_OCR_SCHEMA_VERSION),
                printedPage = root.getInt("printedPage"),
                pdfIndex = root.getInt("pdfIndex"),
                width = root.optInt("width", 1).coerceAtLeast(1),
                height = root.optInt("height", 1).coerceAtLeast(1),
                text = root.optString("text"),
                lines = lines,
                engine = root.optString("engine", "ML_KIT_CHINESE"),
            )
        }
    }
}

internal object TextbookOcrStore {
    private const val OCR_DIRECTORY = "generated/ocr"

    fun read(textbookRoot: File, printedPage: Int): OcrPageResult? = runCatching {
        val file = pageFile(textbookRoot, printedPage)
        if (!file.isFile) return@runCatching null
        val result = OcrPageResult.fromJson(JSONObject(file.readText(Charsets.UTF_8)))
        result.takeIf { it.schemaVersion == TEXTBOOK_OCR_SCHEMA_VERSION }
    }.getOrNull()

    fun write(textbookRoot: File, result: OcrPageResult) {
        val file = pageFile(textbookRoot, result.printedPage)
        file.parentFile?.let { parent ->
            require(parent.mkdirs() || parent.isDirectory) { "无法创建 OCR 页面目录" }
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(result.toJson().toString(2), Charsets.UTF_8)
        if (file.exists()) file.delete()
        require(temporary.renameTo(file)) { "无法保存 OCR 页面结果" }
    }

    private fun pageFile(root: File, printedPage: Int): File =
        File(root, "$OCR_DIRECTORY/page-${printedPage.toString().padStart(5, '0')}.json")
}

class TextbookOcrEngine : Closeable {
    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build(),
    )

    suspend fun recognize(
        bitmap: Bitmap,
        printedPage: Int,
        pdfIndex: Int,
    ): OcrPageResult {
        val recognized = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
        val lines = recognized.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val bounds = line.boundingBox ?: return@mapNotNull null
                val text = line.text.trim()
                if (text.isBlank()) return@mapNotNull null
                bounds.toOcrLine(text, bitmap.width, bitmap.height)
            }
        }.sortedWith(compareBy<OcrTextLine> { it.top }.thenBy { it.left })
        val orderedText = lines.joinToString("\n") { it.text }
            .ifBlank { recognized.text.trim() }
        return OcrPageResult(
            printedPage = printedPage,
            pdfIndex = pdfIndex,
            width = bitmap.width,
            height = bitmap.height,
            text = orderedText,
            lines = lines,
        )
    }

    override fun close() {
        recognizer.close()
    }
}

private fun Rect.toOcrLine(text: String, width: Int, height: Int): OcrTextLine {
    val safeWidth = width.coerceAtLeast(1).toFloat()
    val safeHeight = height.coerceAtLeast(1).toFloat()
    return OcrTextLine(
        text = text,
        left = left / safeWidth,
        top = top / safeHeight,
        right = right / safeWidth,
        bottom = bottom / safeHeight,
    )
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
