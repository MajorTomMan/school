package com.majortomman.school.learning.science.physics

import kotlin.math.abs

data class CircuitComponentResult(
    val componentId: String,
    val voltageDropVolts: Double,
    /** Positive current follows nodeA -> nodeB. */
    val currentAmperes: Double?,
    val powerWatts: Double?,
    val lampBrightnessRatio: Double? = null,
)

data class CircuitSolution(
    val nodeVoltages: Map<CircuitNodeId, Double>,
    val components: Map<String, CircuitComponentResult>,
    val topology: CircuitTopologyReport,
) {
    fun voltage(node: CircuitNodeId): Double = nodeVoltages.getValue(node)
    fun component(id: String): CircuitComponentResult = components.getValue(id)
}

object LinearDcCircuitSolver {
    private const val WIRE_RESISTANCE = 1e-9
    private const val PIVOT_EPSILON = 1e-12

    fun solve(graph: CircuitGraph): CircuitSolution {
        val topology = CircuitTopologyValidator.validate(graph)
        require(topology.safeToSolve) {
            topology.errors.joinToString("；") { it.message }
        }

        val nonGroundNodes = graph.nodes.filter { it != graph.ground }.sortedBy { it.value }
        val nodeIndex = nonGroundNodes.withIndex().associate { it.value to it.index }
        val voltageSources = graph.components.filterIsInstance<CircuitComponent.VoltageSource>()
        val nodeCount = nonGroundNodes.size
        val size = nodeCount + voltageSources.size
        require(size > 0) { "电路没有可求解的未知量。" }

        val matrix = Array(size) { DoubleArray(size) }
        val right = DoubleArray(size)

        fun stampConductance(nodeA: CircuitNodeId, nodeB: CircuitNodeId, resistance: Double) {
            require(resistance > 0.0 && resistance.isFinite())
            val conductance = 1.0 / resistance
            val a = nodeIndex[nodeA]
            val b = nodeIndex[nodeB]
            if (a != null) matrix[a][a] += conductance
            if (b != null) matrix[b][b] += conductance
            if (a != null && b != null) {
                matrix[a][b] -= conductance
                matrix[b][a] -= conductance
            }
        }

        graph.components.forEach { component ->
            when (component) {
                is CircuitComponent.Resistor -> stampConductance(component.nodeA, component.nodeB, component.resistanceOhms)
                is CircuitComponent.Lamp -> stampConductance(component.nodeA, component.nodeB, component.resistanceOhms)
                is CircuitComponent.Wire -> stampConductance(component.nodeA, component.nodeB, WIRE_RESISTANCE)
                is CircuitComponent.Switch -> if (component.closed) {
                    stampConductance(component.nodeA, component.nodeB, WIRE_RESISTANCE)
                }
                is CircuitComponent.Ammeter -> stampConductance(
                    component.nodeA,
                    component.nodeB,
                    component.internalResistanceOhms,
                )
                is CircuitComponent.Voltmeter -> stampConductance(
                    component.nodeA,
                    component.nodeB,
                    component.internalResistanceOhms,
                )
                is CircuitComponent.CurrentSource -> {
                    nodeIndex[component.nodeA]?.let { right[it] -= component.currentAmperes }
                    nodeIndex[component.nodeB]?.let { right[it] += component.currentAmperes }
                }
                is CircuitComponent.VoltageSource -> Unit
                is CircuitComponent.Capacitor,
                is CircuitComponent.Inductor,
                is CircuitComponent.Diode,
                -> Unit
            }
        }

        voltageSources.forEachIndexed { sourceIndex, source ->
            val row = nodeCount + sourceIndex
            nodeIndex[source.nodeA]?.let { node ->
                matrix[node][row] += 1.0
                matrix[row][node] += 1.0
            }
            nodeIndex[source.nodeB]?.let { node ->
                matrix[node][row] -= 1.0
                matrix[row][node] -= 1.0
            }
            right[row] = source.voltageVolts
        }

        val solution = gaussianSolve(matrix, right)
        val nodeVoltages = buildMap {
            put(graph.ground, 0.0)
            nonGroundNodes.forEachIndexed { index, node -> put(node, solution[index]) }
        }
        val sourceCurrentIndex = voltageSources.withIndex().associate { it.value.id to nodeCount + it.index }
        val componentResults = graph.components.associate { component ->
            val voltageDrop = nodeVoltages.getValue(component.nodeA) - nodeVoltages.getValue(component.nodeB)
            val current = when (component) {
                is CircuitComponent.Resistor -> voltageDrop / component.resistanceOhms
                is CircuitComponent.Lamp -> voltageDrop / component.resistanceOhms
                is CircuitComponent.Wire -> voltageDrop / WIRE_RESISTANCE
                is CircuitComponent.Switch -> if (component.closed) voltageDrop / WIRE_RESISTANCE else 0.0
                is CircuitComponent.Ammeter -> voltageDrop / component.internalResistanceOhms
                is CircuitComponent.Voltmeter -> voltageDrop / component.internalResistanceOhms
                is CircuitComponent.CurrentSource -> component.currentAmperes
                is CircuitComponent.VoltageSource -> solution[sourceCurrentIndex.getValue(component.id)]
                is CircuitComponent.Capacitor -> 0.0
                is CircuitComponent.Inductor,
                is CircuitComponent.Diode,
                -> null
            }
            val power = current?.let { voltageDrop * it }
            val brightness = if (component is CircuitComponent.Lamp && power != null) {
                (abs(power) / component.ratedPowerWatts).coerceIn(0.0, 2.0)
            } else {
                null
            }
            component.id to CircuitComponentResult(
                componentId = component.id,
                voltageDropVolts = voltageDrop,
                currentAmperes = current,
                powerWatts = power,
                lampBrightnessRatio = brightness,
            )
        }
        return CircuitSolution(nodeVoltages, componentResults, topology)
    }

    fun equivalentResistance(
        graph: CircuitGraph,
        terminalA: CircuitNodeId,
        terminalB: CircuitNodeId,
    ): Double {
        require(terminalA in graph.nodes && terminalB in graph.nodes && terminalA != terminalB)
        val passiveComponents = graph.components.mapNotNull { component ->
            when (component) {
                is CircuitComponent.VoltageSource -> CircuitComponent.Wire(
                    id = "deactivated_${component.id}",
                    nodeA = component.nodeA,
                    nodeB = component.nodeB,
                )
                is CircuitComponent.CurrentSource -> null
                else -> component
            }
        }
        val testSource = CircuitComponent.VoltageSource(
            id = "__equivalent_resistance_test__",
            nodeA = terminalA,
            nodeB = terminalB,
            voltageVolts = 1.0,
        )
        val testGraph = CircuitGraph(
            nodes = graph.nodes,
            components = passiveComponents + testSource,
            ground = terminalB,
        )
        val current = solve(testGraph).component(testSource.id).currentAmperes ?: error("无法得到测试电流。")
        require(abs(current) > PIVOT_EPSILON) { "两端之间没有导电通路，等效电阻为无穷大。" }
        return abs(1.0 / current)
    }

    fun parallelGroups(graph: CircuitGraph): List<Set<String>> = graph.components
        .filter { it is CircuitComponent.Resistor || it is CircuitComponent.Lamp }
        .groupBy { component ->
            listOf(component.nodeA.value, component.nodeB.value).sorted().joinToString("|")
        }
        .values
        .filter { it.size > 1 }
        .map { group -> group.map { it.id }.toSet() }

    private fun gaussianSolve(inputMatrix: Array<DoubleArray>, inputRight: DoubleArray): DoubleArray {
        val size = inputRight.size
        val matrix = Array(size) { inputMatrix[it].clone() }
        val right = inputRight.clone()

        for (column in 0 until size) {
            var pivot = column
            for (row in column + 1 until size) {
                if (abs(matrix[row][column]) > abs(matrix[pivot][column])) pivot = row
            }
            require(abs(matrix[pivot][column]) > PIVOT_EPSILON) {
                "电路方程奇异：可能存在悬空节点、冲突电压源或未闭合回路。"
            }
            if (pivot != column) {
                val temporaryRow = matrix[pivot]
                matrix[pivot] = matrix[column]
                matrix[column] = temporaryRow
                val temporaryValue = right[pivot]
                right[pivot] = right[column]
                right[column] = temporaryValue
            }

            val divisor = matrix[column][column]
            for (entry in column until size) matrix[column][entry] /= divisor
            right[column] /= divisor

            for (row in 0 until size) {
                if (row == column) continue
                val factor = matrix[row][column]
                if (abs(factor) <= PIVOT_EPSILON) continue
                for (entry in column until size) matrix[row][entry] -= factor * matrix[column][entry]
                right[row] -= factor * right[column]
            }
        }
        require(right.all(Double::isFinite)) { "电路求解结果不是有限数值。" }
        return right
    }
}
