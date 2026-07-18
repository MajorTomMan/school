package com.majortomman.school.learning.language

import java.io.StringReader
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.ja.tokenattributes.BaseFormAttribute
import org.apache.lucene.analysis.ja.tokenattributes.InflectionAttribute
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute
import org.apache.lucene.analysis.ja.tokenattributes.ReadingAttribute
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute

data class JapaneseMorpheme(
    val surface: String,
    val baseForm: String?,
    val reading: String?,
    val pronunciation: String?,
    val partOfSpeech: String?,
    val inflectionType: String?,
    val inflectionForm: String?,
    val startOffset: Int,
    val endOffset: Int,
)

interface JapaneseMorphologicalAnalyzer {
    val engineName: String

    fun analyze(text: String): List<JapaneseMorpheme>
}

/**
 * Apache Lucene Kuromoji adapter.
 *
 * School owns this interface and data model so the course and UI layers do not depend directly on
 * Lucene classes. A future dictionary or analyzer replacement only needs another implementation.
 */
class LuceneKuromojiMorphologicalAnalyzer(
    private val mode: JapaneseTokenizer.Mode = JapaneseTokenizer.Mode.SEARCH,
) : JapaneseMorphologicalAnalyzer {
    override val engineName: String = "Apache Lucene Kuromoji 10.5.0"

    override fun analyze(text: String): List<JapaneseMorpheme> {
        if (text.isBlank()) return emptyList()

        val tokenizer = JapaneseTokenizer(
            null,
            false,
            true,
            mode,
        )
        tokenizer.setReader(StringReader(text))

        val term = tokenizer.addAttribute(CharTermAttribute::class.java)
        val offsets = tokenizer.addAttribute(OffsetAttribute::class.java)
        val baseForm = tokenizer.addAttribute(BaseFormAttribute::class.java)
        val partOfSpeech = tokenizer.addAttribute(PartOfSpeechAttribute::class.java)
        val reading = tokenizer.addAttribute(ReadingAttribute::class.java)
        val inflection = tokenizer.addAttribute(InflectionAttribute::class.java)
        val result = mutableListOf<JapaneseMorpheme>()

        tokenizer.use {
            it.reset()
            while (it.incrementToken()) {
                result += JapaneseMorpheme(
                    surface = term.toString(),
                    baseForm = baseForm.baseForm,
                    reading = reading.reading,
                    pronunciation = reading.pronunciation,
                    partOfSpeech = partOfSpeech.partOfSpeech,
                    inflectionType = inflection.inflectionType,
                    inflectionForm = inflection.inflectionForm,
                    startOffset = offsets.startOffset(),
                    endOffset = offsets.endOffset(),
                )
            }
            it.end()
        }
        return result
    }
}
