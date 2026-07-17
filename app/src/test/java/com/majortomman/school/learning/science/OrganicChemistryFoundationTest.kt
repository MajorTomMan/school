package com.majortomman.school.learning.science

import com.majortomman.school.learning.science.chemistry.organic.FunctionalGroupDetector
import com.majortomman.school.learning.science.chemistry.organic.FunctionalGroupType
import com.majortomman.school.learning.science.chemistry.organic.MoleculeIsomorphism
import com.majortomman.school.learning.science.chemistry.organic.OrganicIsomerAnalyzer
import com.majortomman.school.learning.science.chemistry.organic.OrganicMoleculeLayout
import com.majortomman.school.learning.science.chemistry.organic.OrganicNotationParser
import com.majortomman.school.learning.science.chemistry.organic.TextbookOrganicReactionTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganicChemistryFoundationTest {
    @Test
    fun parserBuildsFormulaAndValenceFromImplicitHydrogens() {
        val ethanol = OrganicNotationParser.parse("CCO")
        val ethene = OrganicNotationParser.parse("C=C")
        val benzene = OrganicNotationParser.parse("c1ccccc1")

        assertEquals("C2H6O", ethanol.molecularFormula())
        assertEquals("C2H4", ethene.molecularFormula())
        assertEquals("C6H6", benzene.molecularFormula())
        assertTrue(ethanol.validateValence().isEmpty())
        assertTrue(benzene.validateValence().isEmpty())
    }

    @Test
    fun parserSupportsBranchesRingsAndBracketCharges() {
        val tertButanol = OrganicNotationParser.parse("CC(C)(C)O")
        val ammonium = OrganicNotationParser.parse("[NH4+]")

        assertEquals("C4H10O", tertButanol.molecularFormula())
        assertEquals("H4N^+", ammonium.molecularFormula())
        assertEquals(1, ammonium.totalFormalCharge)
    }

    @Test
    fun functionalGroupsDistinguishAlcoholEtherAcidAndEster() {
        val ethanolGroups = FunctionalGroupDetector.detect(OrganicNotationParser.parse("CCO")).map { it.type }
        val etherGroups = FunctionalGroupDetector.detect(OrganicNotationParser.parse("COC")).map { it.type }
        val acidGroups = FunctionalGroupDetector.detect(OrganicNotationParser.parse("CC(=O)O")).map { it.type }
        val esterGroups = FunctionalGroupDetector.detect(OrganicNotationParser.parse("CC(=O)OCC")).map { it.type }

        assertTrue(FunctionalGroupType.HYDROXYL in ethanolGroups)
        assertTrue(FunctionalGroupType.ETHER in etherGroups)
        assertTrue(FunctionalGroupType.CARBOXYL in acidGroups)
        assertTrue(FunctionalGroupType.ESTER in esterGroups)
    }

    @Test
    fun isomorphismRecognizesReorderedWritingAndConstitutionalIsomers() {
        val ethanol = OrganicNotationParser.parse("CCO")
        val ethanolReversed = OrganicNotationParser.parse("OCC")
        val dimethylEther = OrganicNotationParser.parse("COC")

        assertTrue(MoleculeIsomorphism.areIsomorphic(ethanol, ethanolReversed))
        assertFalse(MoleculeIsomorphism.areIsomorphic(ethanol, dimethylEther))
        val comparison = OrganicIsomerAnalyzer.compare(ethanol, dimethylEther)
        assertTrue(comparison.constitutionalIsomers)
        assertEquals("C2H6O", ethanol.molecularFormula())
        assertEquals("C2H6O", dimethylEther.molecularFormula())
    }

    @Test
    fun carbonSkeletonRecordsUnsaturationPositions() {
        val molecule = OrganicNotationParser.parse("CC=CC#C")
        val skeleton = molecule.carbonSkeleton()

        assertEquals(5, skeleton.longestChain.size)
        assertEquals(listOf(2), skeleton.doubleBondPositions)
        assertEquals(listOf(4), skeleton.tripleBondPositions)
    }

    @Test
    fun textbookReactionTemplateChecksReactantsAndConservation() {
        val template = TextbookOrganicReactionTemplates.etheneHydrogenation

        assertTrue(template.conservation().balanced)
        assertTrue(template.matchesReactants(listOf(
            OrganicNotationParser.parse("H-H"),
            OrganicNotationParser.parse("C=C"),
        )))
        assertFalse(template.matchesReactants(listOf(OrganicNotationParser.parse("CC"))))
        assertEquals("C2H6", template.products.single().molecularFormula())
    }

    @Test
    fun deterministicLayoutProducesFiniteNormalizedCoordinates() {
        val points = OrganicMoleculeLayout.layout(OrganicNotationParser.parse("CC(=O)OCC"))

        assertEquals(6, points.size)
        assertTrue(points.all { it.x.isFinite() && it.y.isFinite() && it.x in 0.0..1.0 && it.y in 0.0..1.0 })
    }
}
