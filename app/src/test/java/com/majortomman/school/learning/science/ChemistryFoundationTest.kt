package com.majortomman.school.learning.science

import com.majortomman.school.learning.science.chemistry.ChemicalEquationBalancer
import com.majortomman.school.learning.science.chemistry.ChemicalEquationParser
import com.majortomman.school.learning.science.chemistry.ChemicalFormulaParser
import com.majortomman.school.learning.science.chemistry.ChemicalSpecies
import com.majortomman.school.learning.science.chemistry.IonicEquationReducer
import com.majortomman.school.learning.science.chemistry.PeriodicTable
import com.majortomman.school.learning.science.chemistry.Stoichiometry
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChemistryFoundationTest {
    @Test
    fun periodicTableContainsEveryElementIdentity() {
        val elements = PeriodicTable.all()
        assertEquals(118, elements.size)
        assertEquals(118, elements.map { it.symbol }.toSet().size)
        assertEquals("Fe", PeriodicTable.byAtomicNumber(26).symbol)
    }

    @Test
    fun formulaParserHandlesGroupsHydratesAndCharges() {
        val sulfate = ChemicalFormulaParser.parse("Al2(SO4)3")
        val hydrate = ChemicalFormulaParser.parse("CuSO4·5H2O")
        val ion = ChemicalFormulaParser.parse("SO4^2-")

        assertEquals(BigInteger.valueOf(2), sulfate.count("Al"))
        assertEquals(BigInteger.valueOf(3), sulfate.count("S"))
        assertEquals(BigInteger.valueOf(12), sulfate.count("O"))
        assertEquals(BigInteger.ONE, hydrate.count("Cu"))
        assertEquals(BigInteger.valueOf(9), hydrate.count("O"))
        assertEquals(BigInteger.valueOf(10), hydrate.count("H"))
        assertEquals(-2, ion.charge)
    }

    @Test
    fun formulaCalculatesClassroomRelativeMolecularMass() {
        val water = ChemicalFormulaParser.parse("H2O")
        assertEquals(BigDecimal("18.015"), water.relativeMolecularMass())
    }

    @Test
    fun matrixBalancerFindsSmallestWholeNumberCoefficients() {
        val equation = ChemicalEquationParser.parse("Fe + O2 -> Fe2O3")
        val balanced = ChemicalEquationBalancer.balance(equation)

        assertEquals(listOf(4, 3, 2), (balanced.reactants + balanced.products).map { it.coefficient.toInt() })
        assertTrue(balanced.conservation().balanced)
        assertEquals("4Fe + 3O2 → 2Fe2O3", balanced.display())
    }

    @Test
    fun matrixBalancerHandlesMultiSpeciesRedoxEquation() {
        val equation = ChemicalEquationParser.parse("KMnO4 + HCl -> KCl + MnCl2 + H2O + Cl2")
        val balanced = ChemicalEquationBalancer.balance(equation)

        assertEquals(
            listOf(2, 16, 2, 2, 8, 5),
            (balanced.reactants + balanced.products).map { it.coefficient.toInt() },
        )
        assertTrue(balanced.conservation().balanced)
    }

    @Test
    fun ionicReducerCancelsUnchangedAqueousSpecies() {
        val full = ChemicalEquationParser.parse(
            "Ag^+(aq) + NO3^-(aq) + Na^+(aq) + Cl^-(aq) -> AgCl(s) + Na^+(aq) + NO3^-(aq)",
        )
        val reduced = IonicEquationReducer.cancelUnchangedSpecies(full)

        assertEquals("Ag^+(aq) + Cl^-(aq) → AgCl(s)", reduced.display())
        assertTrue(reduced.conservation().balanced)
    }

    @Test
    fun limitingReagentProducesAmountsAndLeftovers() {
        val equation = ChemicalEquationBalancer.balance(
            ChemicalEquationParser.parse("H2 + O2 -> H2O"),
        )
        val result = Stoichiometry.limitingReagent(
            equation,
            mapOf(
                "H2" to BigDecimal("3"),
                "O2" to BigDecimal("1"),
            ),
        )

        assertEquals(listOf("O2"), result.limitingSpecies.map(ChemicalSpecies::display))
        assertEquals(BigDecimal("1"), result.leftoverReactants.getValue("H2").stripTrailingZeros())
        assertEquals(BigDecimal("2"), result.productAmounts.getValue("H2O").stripTrailingZeros())
    }
}
