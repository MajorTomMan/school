package com.majortomman.school.learning.course

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

internal enum class SignedMovementDirection {
    LEFT,
    RIGHT,
    STATIONARY,
}

internal data class SignedMovementModel(
    val start: Float,
    val delta: Float,
) {
    val end: Float = start + delta

    val direction: SignedMovementDirection = when {
        delta > 0f -> SignedMovementDirection.RIGHT
        delta < 0f -> SignedMovementDirection.LEFT
        else -> SignedMovementDirection.STATIONARY
    }

    val symmetricAxisBound: Int = (
        ceil(max(abs(start), abs(end)).toDouble()).toInt() + 1
    ).coerceAtLeast(5)
}

internal data class AbsoluteValueModel(
    val value: Float,
) {
    val absoluteValue: Float = abs(value)
    val sharesNumberLineCoordinate: Boolean = value >= 0f
}
