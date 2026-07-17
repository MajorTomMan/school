package com.majortomman.school.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EquationVerificationEngineTest {
    @Test
    fun verifiesImplicitMultiplication() {
        val result = EquationVerificationEngine.verify(
            input = "y=2x",
            values = mapOf("x" to 3.0, "y" to 6.0),
        )

        assertNull(result.error)
        assertTrue(result.isCorrect == true)
        assertEquals(6.0, result.leftValue ?: Double.NaN, 1e-9)
        assertEquals(6.0, result.rightValue ?: Double.NaN, 1e-9)
    }

    @Test
    fun rejectsIncorrectEquationValues() {
        val result = EquationVerificationEngine.verify(
            input = "y=2x",
            values = mapOf("x" to 3.0, "y" to 5.0),
        )

        assertNull(result.error)
        assertFalse(result.isCorrect == true)
    }

    @Test
    fun detectsAndVerifiesThreeVariables() {
        assertEquals(listOf("x", "y", "z"), EquationVerificationEngine.variablesOf("z=x+y"))

        val result = EquationVerificationEngine.verify(
            input = "z=x+y",
            values = mapOf("x" to 2.0, "y" to 4.0, "z" to 6.0),
        )

        assertTrue(result.isCorrect == true)
    }

    @Test
    fun evaluatesParenthesesAndArithmeticExpression() {
        val result = EquationVerificationEngine.verify(
            input = "2(x+1)",
            values = mapOf("x" to 4.0),
        )

        assertNull(result.error)
        assertNull(result.isCorrect)
        assertEquals(10.0, result.leftValue ?: Double.NaN, 1e-9)
    }

    @Test
    fun reportsMissingVariable() {
        val result = EquationVerificationEngine.verify(
            input = "z=x+y",
            values = mapOf("x" to 2.0, "z" to 6.0),
        )

        assertTrue(result.error?.contains("y") == true)
    }
}
