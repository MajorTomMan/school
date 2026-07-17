package com.majortomman.school.learning.science

import com.majortomman.school.learning.science.expression.BigRational
import com.majortomman.school.learning.science.physics.CircuitComponent
import com.majortomman.school.learning.science.physics.CircuitGraph
import com.majortomman.school.learning.science.physics.CircuitIssueType
import com.majortomman.school.learning.science.physics.CircuitNodeId
import com.majortomman.school.learning.science.physics.CircuitTopologyValidator
import com.majortomman.school.learning.science.physics.ElectricalRelations
import com.majortomman.school.learning.science.physics.LinearDcCircuitSolver
import com.majortomman.school.learning.science.physics.ModelConditionState
import com.majortomman.school.learning.science.physics.PhysicalAssumption
import com.majortomman.school.learning.science.physics.PhysicalEquationDefinition
import com.majortomman.school.learning.science.physics.PhysicalModelSpecification
import com.majortomman.school.learning.science.physics.PhysicalVariableDefinition
import com.majortomman.school.learning.science.quantity.Dimension
import com.majortomman.school.learning.science.quantity.Quantity
import com.majortomman.school.learning.science.quantity.UnitCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsCircuitFoundationTest {
    private val ground = CircuitNodeId("g")
    private val positive = CircuitNodeId("p")
    private val middle = CircuitNodeId("m")

    @Test
    fun physicalModelRequiresDeclaredAssumptions() {
        val model = PhysicalModelSpecification(
            id = "ideal_projectile",
            title = "理想抛体",
            variables = listOf(
                PhysicalVariableDefinition("v", "速度", Dimension.SPEED, directionSensitive = true),
                PhysicalVariableDefinition("t", "时间", Dimension.TIME),
            ),
            assumptions = listOf(PhysicalAssumption("no_air", "忽略空气阻力")),
            equations = listOf(
                PhysicalEquationDefinition("motion", "x=vt", setOf("v", "t"), "匀速分量"),
            ),
        )

        assertFalse(ModelConditionState(emptySet()).validate(model).valid)
        assertTrue(ModelConditionState(setOf("no_air")).validate(model).valid)
    }

    @Test
    fun electricalRelationsKeepExactValuesAndDimensions() {
        val current = ElectricalRelations.current(
            Quantity(BigRational.of(12), UnitCatalog.VOLT),
            Quantity(BigRational.of(6), UnitCatalog.OHM),
        )
        val power = ElectricalRelations.power(
            Quantity(BigRational.of(12), UnitCatalog.VOLT),
            current,
        )
        val energy = ElectricalRelations.energy(
            power,
            Quantity(BigRational.of(10), UnitCatalog.SECOND),
        )

        assertEquals(BigRational.of(2), current.value)
        assertEquals(BigRational.of(24), power.value)
        assertEquals(BigRational.of(240), energy.value)
    }

    @Test
    fun seriesCircuitSolvesNodeVoltageCurrentAndPower() {
        val graph = CircuitGraph(
            nodes = setOf(ground, positive, middle),
            ground = ground,
            components = listOf(
                CircuitComponent.VoltageSource("battery", positive, ground, 12.0),
                CircuitComponent.Resistor("r1", positive, middle, 6.0),
                CircuitComponent.Resistor("r2", middle, ground, 6.0),
            ),
        )

        val solution = LinearDcCircuitSolver.solve(graph)

        assertEquals(12.0, solution.voltage(positive), 1e-8)
        assertEquals(6.0, solution.voltage(middle), 1e-8)
        assertEquals(1.0, solution.component("r1").currentAmperes!!, 1e-8)
        assertEquals(6.0, solution.component("r1").powerWatts!!, 1e-8)
        assertEquals(-12.0, solution.component("battery").powerWatts!!, 1e-8)
    }

    @Test
    fun parallelCircuitReportsBranchCurrentsAndEquivalentResistance() {
        val passive = CircuitGraph(
            nodes = setOf(ground, positive),
            ground = ground,
            components = listOf(
                CircuitComponent.Resistor("r1", positive, ground, 12.0),
                CircuitComponent.Resistor("r2", positive, ground, 12.0),
            ),
        )
        val powered = passive.copy(
            components = passive.components + CircuitComponent.VoltageSource("battery", positive, ground, 12.0),
        )

        val solution = LinearDcCircuitSolver.solve(powered)

        assertEquals(1.0, solution.component("r1").currentAmperes!!, 1e-8)
        assertEquals(1.0, solution.component("r2").currentAmperes!!, 1e-8)
        assertEquals(6.0, LinearDcCircuitSolver.equivalentResistance(passive, positive, ground), 1e-7)
        assertEquals(setOf("r1", "r2"), LinearDcCircuitSolver.parallelGroups(passive).single())
    }

    @Test
    fun topologyRejectsSourceShortAndParallelAmmeter() {
        val graph = CircuitGraph(
            nodes = setOf(ground, positive),
            ground = ground,
            components = listOf(
                CircuitComponent.VoltageSource("battery", positive, ground, 12.0),
                CircuitComponent.Wire("wire", positive, ground),
                CircuitComponent.Resistor("load", positive, ground, 6.0),
                CircuitComponent.Ammeter("meter", positive, ground),
            ),
        )

        val report = CircuitTopologyValidator.validate(graph)

        assertTrue(report.issues.any { it.type == CircuitIssueType.SOURCE_SHORT_CIRCUIT })
        assertTrue(report.issues.any { it.type == CircuitIssueType.AMMETER_PARALLEL })
        assertThrows(IllegalArgumentException::class.java) { LinearDcCircuitSolver.solve(graph) }
    }

    @Test
    fun openSwitchCreatesOpenCircuitWarning() {
        val graph = CircuitGraph(
            nodes = setOf(ground, positive, middle),
            ground = ground,
            components = listOf(
                CircuitComponent.VoltageSource("battery", positive, ground, 6.0),
                CircuitComponent.Switch("switch", positive, middle, closed = false),
                CircuitComponent.Resistor("load", middle, ground, 6.0),
            ),
        )

        val report = CircuitTopologyValidator.validate(graph)
        assertTrue(report.issues.any { it.type == CircuitIssueType.OPEN_CIRCUIT })
    }
}
