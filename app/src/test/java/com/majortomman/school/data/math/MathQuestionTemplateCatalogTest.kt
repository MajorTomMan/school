package com.majortomman.school.data.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathQuestionTemplateCatalogTest {
    @Test
    fun `same seed creates the same verified question`() {
        val first = MathQuestionTemplateCatalog.generate(
            textbookKey = "math-7-1",
            lessonId = "number-line",
            knowledgePointId = "number-line",
            difficulty = MathDifficulty.CONSOLIDATION,
            seed = 42L,
        )
        val second = MathQuestionTemplateCatalog.generate(
            textbookKey = "math-7-1",
            lessonId = "number-line",
            knowledgePointId = "number-line",
            difficulty = MathDifficulty.CONSOLIDATION,
            seed = 42L,
        )

        assertEquals(first.id, second.id)
        assertEquals(first.prompt, second.prompt)
        assertEquals(first.canonicalAnswer, second.canonicalAnswer)
        assertEquals(MathQuestionQuality.VERIFIED, first.quality)
    }

    @Test
    fun `recent template can be avoided when another template exists`() {
        val first = MathQuestionTemplateCatalog.generate(
            textbookKey = "math-7-1",
            lessonId = "absolute-value",
            knowledgePointId = "absolute-value",
            difficulty = MathDifficulty.BASIC,
            seed = 7L,
        )
        val second = MathQuestionTemplateCatalog.generate(
            textbookKey = "math-7-1",
            lessonId = "absolute-value",
            knowledgePointId = "absolute-value",
            difficulty = MathDifficulty.BASIC,
            seed = 7L,
            excludedTemplateIds = setOf(first.templateId),
        )

        assertNotEquals(first.templateId, second.templateId)
    }

    @Test
    fun `generated equation answer satisfies its own deterministic checker`() {
        val question = MathQuestionTemplateCatalog.generate(
            textbookKey = "math-7-1",
            lessonId = "linear-equation",
            knowledgePointId = "linear-equation",
            difficulty = MathDifficulty.IMPROVEMENT,
            seed = 83L,
            excludedTemplateIds = setOf("linear-equation-steps"),
        )

        val evaluation = MathExpressionEngine.evaluate(question, question.canonicalAnswer)

        assertTrue(evaluation.correct)
    }
}
