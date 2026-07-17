package com.majortomman.school.learning.language

import com.majortomman.school.learning.verification.ErrorType
import com.majortomman.school.learning.verification.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageLearningEngineTest {
    @Test
    fun englishContractionCanMatchExpandedText() {
        val result = LanguageAnswerVerifier.verifyText(
            expected = "I am a student.",
            actual = "I'm a student!",
            policy = LanguageAnswerPolicy(
                ignoreCase = true,
                ignorePunctuation = true,
                contractionEquivalents = mapOf("I'm" to "I am"),
            ),
        )

        assertEquals(VerificationStatus.CORRECT, result.status)
    }

    @Test
    fun strictEnglishAnswerReportsCapitalization() {
        val result = LanguageAnswerVerifier.verifyText(
            expected = "I play basketball.",
            actual = "i play basketball.",
        )

        assertEquals(VerificationStatus.INCORRECT, result.status)
        assertEquals(ErrorType.CAPITALIZATION, result.errorType)
    }

    @Test
    fun sentenceOrderReportsMisorderedKnownTokens() {
        val pattern = SentencePattern(
            id = "english_sample",
            language = LanguageCode.ENGLISH,
            tokens = listOf(
                LanguageToken("subject", "He", role = TokenRole.SUBJECT),
                LanguageToken("verb", "plays", role = TokenRole.PREDICATE),
                LanguageToken("object", "basketball", role = TokenRole.OBJECT),
                LanguageToken("time", "after school", role = TokenRole.TIME),
            ),
        )

        val result = LanguageAnswerVerifier.verifyOrder(
            pattern,
            listOf("subject", "object", "verb", "time"),
        )

        assertEquals(VerificationStatus.INCORRECT, result.status)
        assertEquals(ErrorType.WORD_ORDER, result.errorType)
    }

    @Test
    fun englishWordFormChecksThirdPersonSingular() {
        val play = EnglishLexeme(
            lemma = "play",
            forms = mapOf(
                EnglishForm.BASE to "play",
                EnglishForm.THIRD_PERSON_SINGULAR to "plays",
                EnglishForm.PAST to "played",
                EnglishForm.PAST_PARTICIPLE to "played",
                EnglishForm.GERUND to "playing",
            ),
        )

        assertEquals(
            VerificationStatus.CORRECT,
            EnglishWordFormVerifier.verify(play, EnglishForm.THIRD_PERSON_SINGULAR, "plays").status,
        )
        assertEquals(
            ErrorType.WORD_FORM,
            EnglishWordFormVerifier.verify(play, EnglishForm.THIRD_PERSON_SINGULAR, "play").errorType,
        )
    }

    @Test
    fun japaneseParticleCanAcceptTextbookAlternativeWithoutLosingPreference() {
        val rule = JapaneseParticleRule(
            preferred = "へ",
            accepted = setOf("へ", "に"),
            explanationByParticle = mapOf(
                "へ" to "「へ」强调移动的方向，在这里读作「え」。",
                "に" to "「に」也可以表示到达点。",
            ),
        )

        val result = JapaneseLanguageVerifier.verifyParticle(rule, "に")

        assertEquals(VerificationStatus.CORRECT, result.status)
        assertTrue(result.message.contains("到达点"))
    }

    @Test
    fun japanesePolitePresentUsesConfiguredConjugation() {
        val iku = JapaneseLexeme(
            dictionaryForm = "行く",
            reading = "いく",
            forms = mapOf(
                JapaneseForm.DICTIONARY to "行く",
                JapaneseForm.POLITE_PRESENT to "行きます",
                JapaneseForm.POLITE_NEGATIVE to "行きません",
                JapaneseForm.POLITE_PAST to "行きました",
                JapaneseForm.POLITE_PAST_NEGATIVE to "行きませんでした",
            ),
        )

        val result = JapaneseLanguageVerifier.verifyForm(
            iku,
            JapaneseForm.POLITE_PRESENT,
            "行きます",
        )

        assertEquals(VerificationStatus.CORRECT, result.status)
    }

    @Test
    fun japaneseKanaCanBeAnExplicitAcceptedAlternative() {
        val result = LanguageAnswerVerifier.verifyText(
            expected = "私は学生です。",
            actual = "わたしは学生です。",
            policy = LanguageAnswerPolicy(
                acceptedAlternatives = setOf("わたしは学生です。"),
            ),
        )

        assertEquals(VerificationStatus.CORRECT, result.status)
    }
}
