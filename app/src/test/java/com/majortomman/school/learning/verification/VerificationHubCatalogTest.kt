package com.majortomman.school.learning.verification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationHubCatalogTest {
    @Test
    fun exposesSixSubjectEntriesInStableOrder() {
        assertEquals(
            listOf("数学", "物理", "化学", "生物", "英语", "日语"),
            VerificationHubCatalog.subjects.map { it.label },
        )
        assertEquals(6, VerificationHubCatalog.subjects.distinct().size)
    }

    @Test
    fun everySubjectDeclaresExampleAndBoundary() {
        VerificationHubCatalog.subjects.forEach { subject ->
            assertTrue(subject.inputExample.isNotBlank())
            assertTrue(subject.subtitle.isNotBlank())
            assertTrue(VerificationHubCatalog.capability(subject).limitation.isNotBlank())
        }
    }

    @Test
    fun scienceAndMathAreDeterministicButLanguageIsRuleAnalysis() {
        assertTrue(VerificationHubCatalog.capability(VerificationSubject.MATHEMATICS).deterministic)
        assertTrue(VerificationHubCatalog.capability(VerificationSubject.PHYSICS).deterministic)
        assertTrue(VerificationHubCatalog.capability(VerificationSubject.CHEMISTRY).deterministic)
        assertTrue(VerificationHubCatalog.capability(VerificationSubject.BIOLOGY).deterministic)
        assertFalse(VerificationHubCatalog.capability(VerificationSubject.ENGLISH).deterministic)
        assertFalse(VerificationHubCatalog.capability(VerificationSubject.JAPANESE).deterministic)
    }

    @Test
    fun chemistryBoundaryRejectsUnknownProductPrediction() {
        val boundary = VerificationHubCatalog.capability(VerificationSubject.CHEMISTRY).limitation
        assertTrue(boundary.contains("不会仅凭反应物猜测未知产物"))
    }

    @Test
    fun mathematicsBoundaryDoesNotCallSamplingProof() {
        val boundary = VerificationHubCatalog.capability(VerificationSubject.MATHEMATICS).limitation
        assertTrue(boundary.contains("不是严格恒等证明"))
    }
}
