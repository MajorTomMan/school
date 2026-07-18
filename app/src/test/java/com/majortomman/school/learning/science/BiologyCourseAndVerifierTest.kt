package com.majortomman.school.learning.science

import com.majortomman.school.learning.course.BiologyCourseCategory
import com.majortomman.school.learning.science.biology.BiologyRelationId
import com.majortomman.school.learning.science.biology.BiologyRelationVerifier
import com.majortomman.school.learning.science.biology.BiologyVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiologyCourseAndVerifierTest {
    @Test
    fun verifiesMagnificationWithSameUnits() {
        val result = BiologyRelationVerifier.verify(
            category = BiologyCourseCategory.CELL,
            relation = BiologyRelationId.MAGNIFICATION,
            values = mapOf("image" to 5.0, "actual" to 0.05),
            submitted = 100.0,
            unit = "×",
            conditionsAccepted = true,
        )

        assertEquals(BiologyVerificationStatus.CORRECT, result.status)
        assertEquals(100.0, result.expected ?: 0.0, 1e-9)
    }

    @Test
    fun verifiesEcologicalEnergyEfficiency() {
        val result = BiologyRelationVerifier.verify(
            category = BiologyCourseCategory.ECOLOGY,
            relation = BiologyRelationId.ENERGY_EFFICIENCY,
            values = mapOf("next" to 100.0, "previous" to 1000.0),
            submitted = 10.0,
            unit = "%",
            conditionsAccepted = true,
        )

        assertEquals(BiologyVerificationStatus.CORRECT, result.status)
    }

    @Test
    fun refusesRelationOutsidePackagePermission() {
        val result = BiologyRelationVerifier.verify(
            category = BiologyCourseCategory.GENETICS,
            relation = BiologyRelationId.CARDIAC_OUTPUT,
            values = emptyMap(),
            submitted = null,
            unit = "",
            conditionsAccepted = true,
        )

        assertEquals(BiologyVerificationStatus.NOT_ALLOWED, result.status)
    }

    @Test
    fun zeroDenominatorReturnsModelError() {
        val result = BiologyRelationVerifier.verify(
            category = BiologyCourseCategory.ECOLOGY,
            relation = BiologyRelationId.POPULATION_DENSITY,
            values = mapOf("count" to 20.0, "area" to 0.0),
            submitted = 0.0,
            unit = "个/m²",
            conditionsAccepted = true,
        )

        assertEquals(BiologyVerificationStatus.INVALID_MODEL, result.status)
        assertTrue(result.message.contains("大于 0"))
    }

    @Test
    fun requiresExperimentConditions() {
        val result = BiologyRelationVerifier.verify(
            category = BiologyCourseCategory.METABOLISM,
            relation = BiologyRelationId.PHOTOSYNTHESIS_RATE,
            values = mapOf("product" to 20.0, "time" to 10.0),
            submitted = 2.0,
            unit = "单位量/min",
            conditionsAccepted = false,
        )

        assertEquals(BiologyVerificationStatus.INVALID_MODEL, result.status)
    }
}
