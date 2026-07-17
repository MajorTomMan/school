package com.majortomman.school.learning.science.physics

import com.majortomman.school.learning.science.expression.BigRational
import com.majortomman.school.learning.science.quantity.Dimension
import com.majortomman.school.learning.science.quantity.Quantity
import com.majortomman.school.learning.science.quantity.UnitCatalog

data class PhysicalAssumption(
    val id: String,
    val description: String,
    val required: Boolean = true,
)

data class PhysicalVariableDefinition(
    val symbol: String,
    val name: String,
    val dimension: Dimension,
    val directionSensitive: Boolean = false,
)

data class PhysicalEquationDefinition(
    val id: String,
    val display: String,
    val variables: Set<String>,
    val condition: String,
)

data class PhysicalModelSpecification(
    val id: String,
    val title: String,
    val variables: List<PhysicalVariableDefinition>,
    val assumptions: List<PhysicalAssumption>,
    val equations: List<PhysicalEquationDefinition>,
) {
    init {
        require(id.isNotBlank())
        require(variables.map { it.symbol }.toSet().size == variables.size) { "物理量符号不能重复。" }
        val known = variables.map { it.symbol }.toSet()
        require(equations.all { equation -> equation.variables.all(known::contains) }) {
            "物理方程引用了未声明的变量。"
        }
    }
}

data class ModelConditionState(
    val enabledAssumptions: Set<String>,
) {
    fun validate(specification: PhysicalModelSpecification): ModelConditionValidation {
        val missing = specification.assumptions
            .filter { it.required && it.id !in enabledAssumptions }
            .map { it.description }
        return ModelConditionValidation(
            valid = missing.isEmpty(),
            missingRequiredAssumptions = missing,
            message = if (missing.isEmpty()) "物理模型条件完整。" else "缺少模型条件：${missing.joinToString("；")}。",
        )
    }
}

data class ModelConditionValidation(
    val valid: Boolean,
    val missingRequiredAssumptions: List<String>,
    val message: String,
)

object ElectricalRelations {
    fun current(voltage: Quantity, resistance: Quantity): Quantity {
        require(voltage.dimension == Dimension.VOLTAGE) { "电压量纲不正确。" }
        require(resistance.dimension == Dimension.RESISTANCE) { "电阻量纲不正确。" }
        val volts = voltage.convertTo(UnitCatalog.VOLT).value
        val ohms = resistance.convertTo(UnitCatalog.OHM).value
        require(!ohms.isZero) { "电阻不能为 0。" }
        return Quantity(volts / ohms, UnitCatalog.AMPERE)
    }

    fun power(voltage: Quantity, current: Quantity): Quantity {
        require(voltage.dimension == Dimension.VOLTAGE) { "电压量纲不正确。" }
        require(current.dimension == Dimension.CURRENT) { "电流量纲不正确。" }
        return Quantity(
            voltage.convertTo(UnitCatalog.VOLT).value * current.convertTo(UnitCatalog.AMPERE).value,
            UnitCatalog.WATT,
        )
    }

    fun energy(power: Quantity, time: Quantity): Quantity {
        require(power.dimension == Dimension.POWER) { "功率量纲不正确。" }
        require(time.dimension == Dimension.TIME) { "时间量纲不正确。" }
        return Quantity(
            power.convertTo(UnitCatalog.WATT).value * time.convertTo(UnitCatalog.SECOND).value,
            UnitCatalog.JOULE,
        )
    }

    fun jouleHeat(current: Quantity, resistance: Quantity, time: Quantity): Quantity {
        require(current.dimension == Dimension.CURRENT)
        require(resistance.dimension == Dimension.RESISTANCE)
        require(time.dimension == Dimension.TIME)
        val amperes = current.convertTo(UnitCatalog.AMPERE).value
        val ohms = resistance.convertTo(UnitCatalog.OHM).value
        val seconds = time.convertTo(UnitCatalog.SECOND).value
        return Quantity(amperes.pow(2) * ohms * seconds, UnitCatalog.JOULE)
    }

    fun commonPowerForms(voltage: BigRational, current: BigRational, resistance: BigRational): Map<String, BigRational> {
        require(!resistance.isZero)
        return linkedMapOf(
            "UI" to voltage * current,
            "I²R" to current.pow(2) * resistance,
            "U²/R" to voltage.pow(2) / resistance,
        )
    }
}
