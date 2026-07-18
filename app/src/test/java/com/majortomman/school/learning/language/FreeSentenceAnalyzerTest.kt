package com.majortomman.school.learning.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSentenceAnalyzerTest {
    @Test
    fun analyzesCorrectEnglishSentenceStructure() {
        val result = FreeSentenceAnalyzer.analyze(
            FreeLanguage.ENGLISH,
            "He plays basketball after school.",
        )

        assertEquals(SentenceJudgement.SUPPORTED_CORRECT, result.judgement)
        assertTrue(result.segments.any { it.role == GrammarRole.SUBJECT && it.text == "He" })
        assertTrue(result.segments.any { it.role == GrammarRole.PREDICATE && it.text == "plays" })
        assertTrue(result.segments.any { it.role == GrammarRole.OBJECT && it.text == "basketball" })
    }

    @Test
    fun detectsEnglishThirdPersonAgreementIssue() {
        val result = FreeSentenceAnalyzer.analyze(
            FreeLanguage.ENGLISH,
            "He play basketball.",
        )

        assertEquals(SentenceJudgement.SUPPORTED_ISSUES, result.judgement)
        assertTrue(result.issues.any { it.code == "third_person_singular" })
    }

    @Test
    fun detectsBaseFormAfterDoes() {
        val result = FreeSentenceAnalyzer.analyze(
            FreeLanguage.ENGLISH,
            "Does he plays basketball?",
        )

        assertTrue(result.issues.any { it.code == "do_base_form" })
    }

    @Test
    fun analyzesBasicJapaneseSentence() {
        val result = FreeSentenceAnalyzer.analyze(
            FreeLanguage.JAPANESE,
            "私は学校へ行きます。",
        )

        assertEquals(SentenceJudgement.SUPPORTED_CORRECT, result.judgement)
        assertTrue(result.segments.any { it.role == GrammarRole.TOPIC && it.text == "私" })
        assertTrue(result.segments.any { it.role == GrammarRole.PREDICATE && it.text == "行きます" })
    }

    @Test
    fun detectsMovementParticleIssue() {
        val result = FreeSentenceAnalyzer.analyze(
            FreeLanguage.JAPANESE,
            "私は学校を行きます。",
        )

        assertEquals(SentenceJudgement.SUPPORTED_ISSUES, result.judgement)
        assertTrue(result.issues.any { it.code == "movement_particle" })
    }

    @Test
    fun detectsMixedJapaneseEnding() {
        val result = FreeSentenceAnalyzer.analyze(
            FreeLanguage.JAPANESE,
            "私は学生ですます。",
        )

        assertTrue(result.issues.any { it.code == "mixed_ending" })
    }

    @Test
    fun doesNotPretendComplexSentenceIsFullyProven() {
        val result = FreeSentenceAnalyzer.analyze(
            FreeLanguage.ENGLISH,
            "Although he was tired, he finished the work.",
        )

        assertEquals(SentenceJudgement.AMBIGUOUS, result.judgement)
        assertTrue(result.limitation.contains("复杂从句"))
    }
}
