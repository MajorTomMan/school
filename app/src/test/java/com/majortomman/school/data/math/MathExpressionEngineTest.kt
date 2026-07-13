package com.majortomman.school.data.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathExpressionEngineTest {
    @Test
    fun `fractions and decimals are treated as the same rational value`() {
        val question = question(
            type = MathQuestionType.NUMERIC_INPUT,
            spec = MathAnswerSpec.RationalValue("1/2"),
            canonical = "1/2",
        )

        val evaluation = MathExpressionEngine.evaluate(question, "0.5")

        assertTrue(evaluation.correct)
        assertEquals("1/2", evaluation.normalizedAnswer)
    }

    @Test
    fun `equivalent expressions are accepted`() {
        val question = question(
            type = MathQuestionType.EXPRESSION_INPUT,
            spec = MathAnswerSpec.EquivalentExpression("2*x+6"),
            canonical = "2x+6",
        )

        assertTrue(MathExpressionEngine.evaluate(question, "2(x+3)").correct)
        assertTrue(MathExpressionEngine.evaluate(question, "6+2x").correct)
        assertFalse(MathExpressionEngine.evaluate(question, "2x+3").correct)
    }

    @Test
    fun `linear equation checks the final solution`() {
        val question = question(
            type = MathQuestionType.NUMERIC_INPUT,
            spec = MathAnswerSpec.LinearEquation("2*x+3=9", "3"),
            canonical = "x=3",
        )

        assertTrue(MathExpressionEngine.evaluate(question, "x=3").correct)
        assertFalse(MathExpressionEngine.evaluate(question, "x=-3").correct)
    }

    @Test
    fun `step sequence identifies the first non equivalent equation`() {
        val question = question(
            type = MathQuestionType.STEP_BY_STEP,
            spec = MathAnswerSpec.StepSequence("2*x+3=9", "3"),
            canonical = "x=3",
        )

        val valid = MathExpressionEngine.evaluate(question, "2x=6\nx=3")
        val invalid = MathExpressionEngine.evaluate(question, "2x=12\nx=6")

        assertTrue(valid.correct)
        assertFalse(invalid.correct)
        assertEquals("等式变形错误", invalid.mistakeType)
    }

    @Test
    fun `absolute value equation requires both solutions`() {
        val question = question(
            type = MathQuestionType.EXPRESSION_INPUT,
            spec = MathAnswerSpec.RationalSet(listOf("6", "-6")),
            canonical = "x=6 或 x=-6",
        )

        val incomplete = MathExpressionEngine.evaluate(question, "x=6")
        val complete = MathExpressionEngine.evaluate(question, "x=6 或 x=-6")

        assertFalse(incomplete.correct)
        assertEquals("步骤遗漏", incomplete.mistakeType)
        assertTrue(complete.correct)
    }

    private fun question(
        type: MathQuestionType,
        spec: MathAnswerSpec,
        canonical: String,
    ): MathQuestion = MathQuestion(
        id = "test-question",
        templateId = "test-template",
        textbookKey = "math-7-1",
        lessonId = "lesson",
        knowledgePointId = "absolute-value",
        type = type,
        difficulty = MathDifficulty.BASIC,
        source = MathQuestionSource.SYSTEM_TEMPLATE,
        prompt = "测试题",
        answerSpec = spec,
        canonicalAnswer = canonical,
        hints = listOf("检查定义。"),
        explanation = "测试解释。",
    )
}
