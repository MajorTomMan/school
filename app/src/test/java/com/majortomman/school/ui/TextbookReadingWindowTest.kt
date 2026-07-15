package com.majortomman.school.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextbookReadingWindowTest {
    @Test
    fun `reading window clamps pages to the current knowledge point`() {
        val window = TextbookReadingWindow(12, 17)

        assertEquals(12, window.clamp(3))
        assertEquals(15, window.clamp(15))
        assertEquals(17, window.clamp(28))
        assertTrue(window.contains(12))
        assertTrue(window.contains(17))
        assertFalse(window.contains(18))
    }
}
