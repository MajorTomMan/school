package com.majortomman.school.learning.science

import com.majortomman.school.learning.science.expression.BigRational
import com.majortomman.school.learning.science.expression.ScienceExpressionParser
import com.majortomman.school.learning.science.expression.ScienceExpressionRenderer
import com.majortomman.school.learning.science.expression.ScienceExpressionSimplifier
import com.majortomman.school.learning.science.expression.ScienceNumberDomain
import com.majortomman.school.learning.science.quantity.Dimension
import com.majortomman.school.learning.science.quantity.DimensionalAnalyzer
import com.majortomman.school.learning.science.quantity.QuantityParser
import com.majortomman.school.learning.science.quantity.SignificantFigures
import com.majortomman.school.learning.science.quantity.UnitCatalog
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScienceCoreFoundationTest {
    @Test
    fun rationalArithmeticStaysExact() {
        assertEquals(BigRational.of(1, 2), BigRational.parse("1/3") + BigRational.parse("1/6"))
        assertEquals(BigRational.of(1, 10), BigRational.parse("0.1"))
    }

    @Test
    fun radicalAndPiSimplifyWithoutPrematureDecimalConversion() {
        val radical = ScienceExpressionSimplifier.simplify(ScienceExpressionParser.parse("√72"))
        val pi = ScienceExpressionSimplifier.simplify(ScienceExpressionParser.parse("2π + 3π"))

        assertEquals("6·√2", ScienceExpressionRenderer.render(radical))
        assertEquals("5·π", ScienceExpressionRenderer.render(pi))
    }

    @Test
    fun negativeSquareRootHonorsNumberDomain() {
        assertThrows(IllegalArgumentException::class.java) {
            ScienceExpressionSimplifier.simplify(ScienceExpressionParser.parse("√(-8)"))
        }
        val complex = ScienceExpressionSimplifier.simplify(
            ScienceExpressionParser.parse("√(-8)"),
            ScienceNumberDomain.COMPLEX,
        )
        assertEquals("2·i·√2", ScienceExpressionRenderer.render(complex))
    }

    @Test
    fun speedAndEnergyConversionsAreExact() {
        val speed = QuantityParser.parse("72 km/h").convertTo(UnitCatalog.METER_PER_SECOND)
        val energy = QuantityParser.parse("1 kWh").convertTo(UnitCatalog.JOULE)

        assertEquals(BigRational.of(20), speed.value)
        assertEquals(BigRational.of(3_600_000), energy.value)
    }

    @Test
    fun affineTemperatureConversionWorks() {
        val kelvin = QuantityParser.parse("20 °C").convertTo(UnitCatalog.KELVIN)
        assertEquals(BigRational.of(29_315, 100), kelvin.value)
    }

    @Test
    fun dimensionalAnalyzerRejectsDifferentPhysicalKinds() {
        val result = DimensionalAnalyzer.requireSame(Dimension.LENGTH, Dimension.TIME)
        assertFalse(result.valid)
        assertTrue(result.message.contains("量纲不一致"))
    }

    @Test
    fun significantFiguresCountAndRound() {
        assertEquals(3, SignificantFigures.count("0.0120"))
        assertEquals(BigDecimal("12.3"), SignificantFigures.round(BigDecimal("12.345"), 3))
    }
}
