package com.majortomman.school.learning.science.chemistry

import java.math.BigDecimal
import java.math.MathContext

private val CHEMISTRY_CONTEXT = MathContext.DECIMAL64

data class SubstanceAmount(
    val species: ChemicalSpecies,
    val moles: BigDecimal,
) {
    init { require(moles.signum() >= 0) { "物质的量不能为负数。" } }
}

data class LimitingReagentResult(
    val limitingSpecies: List<ChemicalSpecies>,
    val reactionExtentMoles: BigDecimal,
    val leftoverReactants: Map<String, BigDecimal>,
    val productAmounts: Map<String, BigDecimal>,
)

object Stoichiometry {
    fun molarMass(formula: ChemicalFormula): BigDecimal = formula.relativeMolecularMass(CHEMISTRY_CONTEXT)

    fun molesFromMass(formula: ChemicalFormula, massGrams: BigDecimal): BigDecimal {
        require(massGrams.signum() >= 0) { "质量不能为负数。" }
        return massGrams.divide(molarMass(formula), CHEMISTRY_CONTEXT)
    }

    fun massFromMoles(formula: ChemicalFormula, moles: BigDecimal): BigDecimal {
        require(moles.signum() >= 0) { "物质的量不能为负数。" }
        return moles.multiply(molarMass(formula), CHEMISTRY_CONTEXT)
    }

    fun molarity(moles: BigDecimal, solutionVolumeLiters: BigDecimal): BigDecimal {
        require(moles.signum() >= 0) { "溶质物质的量不能为负数。" }
        require(solutionVolumeLiters.signum() > 0) { "溶液体积必须为正数。" }
        return moles.divide(solutionVolumeLiters, CHEMISTRY_CONTEXT)
    }

    fun dilute(
        initialMolarity: BigDecimal,
        initialVolumeLiters: BigDecimal,
        finalVolumeLiters: BigDecimal,
    ): BigDecimal {
        require(initialMolarity.signum() >= 0)
        require(initialVolumeLiters.signum() > 0)
        require(finalVolumeLiters >= initialVolumeLiters) { "稀释后的总体积不能小于初始体积。" }
        return initialMolarity.multiply(initialVolumeLiters, CHEMISTRY_CONTEXT)
            .divide(finalVolumeLiters, CHEMISTRY_CONTEXT)
    }

    fun limitingReagent(
        balancedEquation: ChemicalEquation,
        availableReactantMoles: Map<String, BigDecimal>,
    ): LimitingReagentResult {
        require(balancedEquation.conservation().balanced) { "必须先配平方程式。" }
        val extents = balancedEquation.reactants.associate { term ->
            val key = term.species.display()
            val available = availableReactantMoles[key]
                ?: error("缺少反应物 $key 的物质的量。")
            require(available.signum() >= 0) { "$key 的物质的量不能为负数。" }
            key to available.divide(BigDecimal(term.coefficient), CHEMISTRY_CONTEXT)
        }
        val minimum = extents.values.minOrNull() ?: error("方程式没有反应物。")
        val limiting = balancedEquation.reactants
            .filter { term -> extents.getValue(term.species.display()).compareTo(minimum) == 0 }
            .map(EquationTerm::species)
        val leftovers = balancedEquation.reactants.associate { term ->
            val key = term.species.display()
            val consumed = minimum.multiply(BigDecimal(term.coefficient), CHEMISTRY_CONTEXT)
            key to availableReactantMoles.getValue(key).subtract(consumed, CHEMISTRY_CONTEXT).max(BigDecimal.ZERO)
        }
        val products = balancedEquation.products.associate { term ->
            term.species.display() to minimum.multiply(BigDecimal(term.coefficient), CHEMISTRY_CONTEXT)
        }
        return LimitingReagentResult(
            limitingSpecies = limiting,
            reactionExtentMoles = minimum,
            leftoverReactants = leftovers,
            productAmounts = products,
        )
    }

    fun theoreticalYieldGrams(
        balancedEquation: ChemicalEquation,
        availableReactantMoles: Map<String, BigDecimal>,
        product: ChemicalSpecies,
    ): BigDecimal {
        val result = limitingReagent(balancedEquation, availableReactantMoles)
        val productMoles = result.productAmounts[product.display()]
            ?: error("${product.display()} 不是方程式生成物。")
        return massFromMoles(product.formula, productMoles)
    }

    fun percentYield(actualYieldGrams: BigDecimal, theoreticalYieldGrams: BigDecimal): BigDecimal {
        require(actualYieldGrams.signum() >= 0)
        require(theoreticalYieldGrams.signum() > 0)
        return actualYieldGrams.divide(theoreticalYieldGrams, CHEMISTRY_CONTEXT)
            .multiply(BigDecimal(100), CHEMISTRY_CONTEXT)
    }
}
