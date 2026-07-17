package com.majortomman.school.learning.science.physics

@JvmInline
value class CircuitNodeId(val value: String) {
    init {
        require(value.isNotBlank()) { "电路节点 ID 不能为空。" }
    }

    override fun toString(): String = value
}

sealed interface CircuitComponent {
    val id: String
    val nodeA: CircuitNodeId
    val nodeB: CircuitNodeId

    data class Resistor(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        val resistanceOhms: Double,
    ) : CircuitComponent {
        init { require(resistanceOhms > 0.0 && resistanceOhms.isFinite()) { "电阻必须是正有限值。" } }
    }

    data class Lamp(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        val resistanceOhms: Double,
        val ratedPowerWatts: Double,
    ) : CircuitComponent {
        init {
            require(resistanceOhms > 0.0 && resistanceOhms.isFinite())
            require(ratedPowerWatts > 0.0 && ratedPowerWatts.isFinite())
        }
    }

    data class VoltageSource(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        val voltageVolts: Double,
    ) : CircuitComponent {
        init { require(voltageVolts.isFinite()) }
    }

    data class CurrentSource(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        /** Positive current flows from nodeA to nodeB. */
        val currentAmperes: Double,
    ) : CircuitComponent {
        init { require(currentAmperes.isFinite()) }
    }

    data class Wire(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
    ) : CircuitComponent

    data class Switch(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        val closed: Boolean,
    ) : CircuitComponent

    data class Ammeter(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        val internalResistanceOhms: Double = 1e-6,
    ) : CircuitComponent {
        init { require(internalResistanceOhms > 0.0) }
    }

    data class Voltmeter(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        val internalResistanceOhms: Double = 1e12,
    ) : CircuitComponent {
        init { require(internalResistanceOhms > 0.0) }
    }

    data class Capacitor(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        val capacitanceFarads: Double,
    ) : CircuitComponent {
        init { require(capacitanceFarads > 0.0) }
    }

    data class Inductor(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        val inductanceHenrys: Double,
    ) : CircuitComponent {
        init { require(inductanceHenrys > 0.0) }
    }

    data class Diode(
        override val id: String,
        override val nodeA: CircuitNodeId,
        override val nodeB: CircuitNodeId,
        val forwardVoltageVolts: Double = 0.7,
    ) : CircuitComponent {
        init { require(forwardVoltageVolts >= 0.0) }
    }
}

data class CircuitGraph(
    val nodes: Set<CircuitNodeId>,
    val components: List<CircuitComponent>,
    val ground: CircuitNodeId,
) {
    init {
        require(ground in nodes) { "接地点必须属于电路节点。" }
        require(components.map { it.id }.toSet().size == components.size) { "元件 ID 不能重复。" }
        require(components.all { it.nodeA in nodes && it.nodeB in nodes }) { "元件连接了未声明的节点。" }
    }

    fun component(id: String): CircuitComponent = components.firstOrNull { it.id == id }
        ?: error("找不到元件 $id。")

    fun incident(node: CircuitNodeId): List<CircuitComponent> =
        components.filter { it.nodeA == node || it.nodeB == node }
}

enum class CircuitIssueSeverity {
    INFO,
    WARNING,
    ERROR,
}

enum class CircuitIssueType {
    SELF_LOOP,
    DANGLING_NODE,
    OPEN_CIRCUIT,
    SOURCE_SHORT_CIRCUIT,
    AMMETER_PARALLEL,
    VOLTMETER_SERIES,
    UNSUPPORTED_DYNAMIC_COMPONENT,
}

data class CircuitIssue(
    val severity: CircuitIssueSeverity,
    val type: CircuitIssueType,
    val componentIds: Set<String> = emptySet(),
    val nodeIds: Set<CircuitNodeId> = emptySet(),
    val message: String,
)

data class CircuitTopologyReport(
    val issues: List<CircuitIssue>,
) {
    val safeToSolve: Boolean get() = issues.none { it.severity == CircuitIssueSeverity.ERROR }
    val errors: List<CircuitIssue> get() = issues.filter { it.severity == CircuitIssueSeverity.ERROR }
    val warnings: List<CircuitIssue> get() = issues.filter { it.severity == CircuitIssueSeverity.WARNING }
}

object CircuitTopologyValidator {
    fun validate(graph: CircuitGraph): CircuitTopologyReport {
        val issues = mutableListOf<CircuitIssue>()

        graph.components.filter { it.nodeA == it.nodeB }.forEach { component ->
            issues += CircuitIssue(
                severity = CircuitIssueSeverity.ERROR,
                type = CircuitIssueType.SELF_LOOP,
                componentIds = setOf(component.id),
                nodeIds = setOf(component.nodeA),
                message = "元件 ${component.id} 的两端连接到了同一个节点。",
            )
        }

        graph.nodes.filter { graph.incident(it).size <= 1 && it != graph.ground }.forEach { node ->
            issues += CircuitIssue(
                severity = CircuitIssueSeverity.WARNING,
                type = CircuitIssueType.DANGLING_NODE,
                nodeIds = setOf(node),
                message = "节点 $node 可能悬空。",
            )
        }

        graph.components.filterIsInstance<CircuitComponent.VoltageSource>().forEach { source ->
            if (connectedByIdealPath(graph, source.nodeA, source.nodeB, excluded = setOf(source.id))) {
                issues += CircuitIssue(
                    severity = CircuitIssueSeverity.ERROR,
                    type = CircuitIssueType.SOURCE_SHORT_CIRCUIT,
                    componentIds = setOf(source.id),
                    nodeIds = setOf(source.nodeA, source.nodeB),
                    message = "电压源 ${source.id} 的两端被理想导线直接连通，形成短路。",
                )
            } else if (!connectedByConductivePath(graph, source.nodeA, source.nodeB, excluded = setOf(source.id))) {
                issues += CircuitIssue(
                    severity = CircuitIssueSeverity.WARNING,
                    type = CircuitIssueType.OPEN_CIRCUIT,
                    componentIds = setOf(source.id),
                    message = "电压源 ${source.id} 外部没有闭合导电回路。",
                )
            }
        }

        graph.components.filterIsInstance<CircuitComponent.Ammeter>().forEach { meter ->
            val parallel = graph.components.filter { component ->
                component.id != meter.id && sameEndpoints(component, meter) && component !is CircuitComponent.Wire
            }
            if (parallel.isNotEmpty()) {
                issues += CircuitIssue(
                    severity = CircuitIssueSeverity.ERROR,
                    type = CircuitIssueType.AMMETER_PARALLEL,
                    componentIds = setOf(meter.id) + parallel.map { it.id },
                    message = "电流表 ${meter.id} 与其他元件并联，可能造成近似短路。",
                )
            }
        }

        graph.components.filterIsInstance<CircuitComponent.Voltmeter>().forEach { meter ->
            val pathWithoutMeter = connectedByConductivePath(graph, meter.nodeA, meter.nodeB, excluded = setOf(meter.id))
            val lowDegree = graph.incident(meter.nodeA).size <= 2 || graph.incident(meter.nodeB).size <= 2
            if (!pathWithoutMeter && lowDegree) {
                issues += CircuitIssue(
                    severity = CircuitIssueSeverity.WARNING,
                    type = CircuitIssueType.VOLTMETER_SERIES,
                    componentIds = setOf(meter.id),
                    message = "电压表 ${meter.id} 可能串联在主回路中；电压表应并联在被测元件两端。",
                )
            }
        }

        graph.components.filter { it is CircuitComponent.Capacitor || it is CircuitComponent.Inductor || it is CircuitComponent.Diode }
            .forEach { component ->
                issues += CircuitIssue(
                    severity = CircuitIssueSeverity.INFO,
                    type = CircuitIssueType.UNSUPPORTED_DYNAMIC_COMPONENT,
                    componentIds = setOf(component.id),
                    message = "元件 ${component.id} 已有结构定义，但不参与当前线性直流稳态求解。",
                )
            }

        return CircuitTopologyReport(issues)
    }

    private fun sameEndpoints(left: CircuitComponent, right: CircuitComponent): Boolean =
        (left.nodeA == right.nodeA && left.nodeB == right.nodeB) ||
            (left.nodeA == right.nodeB && left.nodeB == right.nodeA)

    private fun connectedByIdealPath(
        graph: CircuitGraph,
        start: CircuitNodeId,
        target: CircuitNodeId,
        excluded: Set<String>,
    ): Boolean = connected(
        graph,
        start,
        target,
        excluded,
    ) { component ->
        component is CircuitComponent.Wire ||
            component is CircuitComponent.Ammeter ||
            component is CircuitComponent.Switch && component.closed
    }

    private fun connectedByConductivePath(
        graph: CircuitGraph,
        start: CircuitNodeId,
        target: CircuitNodeId,
        excluded: Set<String>,
    ): Boolean = connected(
        graph,
        start,
        target,
        excluded,
    ) { component ->
        when (component) {
            is CircuitComponent.Switch -> component.closed
            is CircuitComponent.Voltmeter,
            is CircuitComponent.Capacitor,
            -> false
            else -> true
        }
    }

    private fun connected(
        graph: CircuitGraph,
        start: CircuitNodeId,
        target: CircuitNodeId,
        excluded: Set<String>,
        accepts: (CircuitComponent) -> Boolean,
    ): Boolean {
        val visited = mutableSetOf(start)
        val queue = ArrayDeque<CircuitNodeId>()
        queue += start
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node == target) return true
            graph.incident(node)
                .filter { it.id !in excluded && accepts(it) }
                .forEach { component ->
                    val other = if (component.nodeA == node) component.nodeB else component.nodeA
                    if (visited.add(other)) queue += other
                }
        }
        return false
    }
}
