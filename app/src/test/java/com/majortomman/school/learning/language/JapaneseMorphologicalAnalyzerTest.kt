package com.majortomman.school.learning.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseMorphologicalAnalyzerTest {
    private val analyzer: JapaneseMorphologicalAnalyzer = LuceneKuromojiMorphologicalAnalyzer()

    @Test
    fun segmentsJapaneseWithPartOfSpeechBaseFormAndReading() {
        val sentence = "私は学校へ行きます。"
        val morphemes = analyzer.analyze(sentence)

        assertEquals(sentence, morphemes.joinToString(separator = "") { it.surface })
        assertTrue(morphemes.any { it.surface == "私" && !it.reading.isNullOrBlank() })
        assertTrue(morphemes.any { it.surface == "は" && it.partOfSpeech.orEmpty().startsWith("助詞") })
        assertTrue(morphemes.any { it.surface == "学校" && !it.reading.isNullOrBlank() })
        assertTrue(morphemes.any { it.surface == "へ" && it.partOfSpeech.orEmpty().startsWith("助詞") })
        assertTrue(morphemes.any { it.surface == "行き" && it.baseForm == "行く" })
        assertTrue(morphemes.any { it.surface == "ます" && it.partOfSpeech.orEmpty().startsWith("助動詞") })
    }

    @Test
    fun exposesInflectionForPoliteNegativePastSentence() {
        val morphemes = analyzer.analyze("昨日は肉を食べませんでした。")

        assertTrue(morphemes.any { it.surface == "食べ" && it.baseForm == "食べる" })
        assertTrue(morphemes.any { it.partOfSpeech.orEmpty().startsWith("助動詞") })
        assertTrue(morphemes.any { !it.inflectionForm.isNullOrBlank() })
    }

    @Test
    fun preservesStableOffsets() {
        val morphemes = analyzer.analyze("日本語を勉強します。")

        assertTrue(morphemes.isNotEmpty())
        assertEquals(0, morphemes.first().startOffset)
        assertEquals("日本語を勉強します。".length, morphemes.last().endOffset)
        assertTrue(morphemes.zipWithNext().all { (left, right) -> left.endOffset <= right.startOffset })
    }

    @Test
    fun returnsEmptyForBlankInput() {
        assertTrue(analyzer.analyze("   ").isEmpty())
    }
}
