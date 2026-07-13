package com.majortomman.school.data.material

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextbookQuestionDraftStoreTest {
    @Test
    fun `question like ocr lines become textbook drafts with source page`() {
        val root = Files.createTempDirectory("school-question-drafts").toFile()
        val lesson = GeneratedLesson(
            id = "math-7-1:number-line",
            sourceId = "number-line",
            title = "数轴",
            subtitle = "教材第 15—20 页",
            estimatedMinutes = 18,
            pageStart = 15,
            pageEnd = 20,
            objectives = emptyList(),
            explanation = "",
            commonMistake = "",
        )
        val page = OcrPageResult(
            printedPage = 16,
            pdfIndex = 18,
            width = 1600,
            height = 2200,
            text = "练习 比较 -3 与 2 的大小，并说明数轴上的位置关系。",
            lines = listOf(
                OcrTextLine(
                    text = "练习 比较 -3 与 2 的大小，并说明数轴上的位置关系。",
                    left = 0.08f,
                    top = 0.2f,
                    right = 0.92f,
                    bottom = 0.27f,
                ),
            ),
        )

        val drafts = TextbookQuestionDraftStore.extractAndWrite(root, lesson, listOf(page))
        val restored = TextbookQuestionDraftStore.read(root)

        assertEquals(1, drafts.size)
        assertEquals(16, drafts.first().page)
        assertEquals("number-line", drafts.first().knowledgePointId)
        assertEquals(drafts.first().id, restored.first().id)
        assertTrue(restored.first().excerpt.contains("-3"))
    }
}
