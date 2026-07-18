package com.majortomman.school.learning.course

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberLineModelsTest {
    @Test
    fun negativeStartPlusPositiveMovementMovesRight() {
        val movement = SignedMovementModel(start = -3f, delta = 5f)

        assertEquals(2f, movement.end)
        assertEquals(SignedMovementDirection.RIGHT, movement.direction)
        assertEquals(5, movement.symmetricAxisBound)
    }

    @Test
    fun negativeStartPlusNegativeMovementMovesLeft() {
        val movement = SignedMovementModel(start = -3f, delta = -2f)

        assertEquals(-5f, movement.end)
        assertEquals(SignedMovementDirection.LEFT, movement.direction)
        assertEquals(6, movement.symmetricAxisBound)
    }

    @Test
    fun zeroMovementKeepsTheSamePosition() {
        val movement = SignedMovementModel(start = -3f, delta = 0f)

        assertEquals(-3f, movement.end)
        assertEquals(SignedMovementDirection.STATIONARY, movement.direction)
    }

    @Test
    fun negativeAbsoluteValueIsMirroredToPositiveHalfAxis() {
        val model = AbsoluteValueModel(-4f)

        assertEquals(4f, model.absoluteValue)
        assertFalse(model.sharesNumberLineCoordinate)
    }

    @Test
    fun positiveValueAndAbsoluteValueShareTheSameCoordinate() {
        val model = AbsoluteValueModel(3f)

        assertEquals(3f, model.absoluteValue)
        assertTrue(model.sharesNumberLineCoordinate)
    }
}
