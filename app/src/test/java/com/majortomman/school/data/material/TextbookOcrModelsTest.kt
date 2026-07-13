package com.majortomman.school.data.material

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextbookOcrModelsTest {
    @Test
    fun `ocr page keeps text coordinates after json round trip`() {
        val original = OcrPageResult(
            printedPage = 15,
            pdfIndex = 18,
            width = 1600,
            height = 2200,
            text = "规定了原点、正方向和单位长度的直线叫做数轴。",
            lines = listOf(
                OcrTextLine(
                    text = "规定了原点、正方向和单位长度的直线叫做数轴。",
                    left = 0.08f,
                    top = 0.15f,
                    right = 0.91f,
                    bottom = 0.22f,
                ),
            ),
        )

        val restored = OcrPageResult.fromJson(original.toJson())

        assertEquals(15, restored.printedPage)
        assertEquals(18, restored.pdfIndex)
        assertEquals(original.text, restored.text)
        assertEquals(1, restored.lines.size)
        assertEquals(original.lines.first().left, restored.lines.first().left)
        assertTrue(restored.isUsable)
    }

    @Test
    fun `very short or noisy text is not treated as usable textbook content`() {
        val short = OcrPageResult(
            printedPage = 1,
            pdfIndex = 0,
            width = 100,
            height = 100,
            text = "第1页",
            lines = emptyList(),
        )
        val noisy = short.copy(text = "@@@@ #### $$$$ %%%% ^^^^ &&&& ****")

        assertFalse(short.isUsable)
        assertFalse(noisy.isUsable)
    }
}
