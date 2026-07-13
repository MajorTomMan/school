package com.majortomman.school.data.review

import com.majortomman.school.data.local.ReviewScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {
    private val dayMillis = 24L * 60L * 60L * 1_000L

    @Test
    fun firstCorrectAttemptSchedulesTomorrow() {
        val now = 1_000L
        val next = ReviewScheduler.next(
            lessonId = "number-line",
            previous = null,
            correct = true,
            now = now,
        )

        assertEquals(1, next.repetitions)
        assertEquals(1, next.intervalDays)
        assertEquals(now + dayMillis, next.dueAt)
        assertTrue(next.lastCorrect)
    }

    @Test
    fun secondCorrectAttemptSchedulesSixDays() {
        val previous = ReviewScheduleEntity(
            lessonId = "number-line",
            dueAt = 0,
            intervalDays = 1,
            repetitions = 1,
            easeFactor = 2.6,
            lastReviewedAt = 0,
            lastCorrect = true,
        )

        val next = ReviewScheduler.next(
            lessonId = "number-line",
            previous = previous,
            correct = true,
            now = 0,
        )

        assertEquals(2, next.repetitions)
        assertEquals(6, next.intervalDays)
    }

    @Test
    fun incorrectAttemptResetsRepetitionsAndKeepsMinimumEase() {
        val previous = ReviewScheduleEntity(
            lessonId = "number-line",
            dueAt = 0,
            intervalDays = 30,
            repetitions = 5,
            easeFactor = 1.3,
            lastReviewedAt = 0,
            lastCorrect = true,
        )

        val next = ReviewScheduler.next(
            lessonId = "number-line",
            previous = previous,
            correct = false,
            now = 0,
        )

        assertEquals(0, next.repetitions)
        assertEquals(1, next.intervalDays)
        assertEquals(1.3, next.easeFactor, 0.0001)
    }
}
