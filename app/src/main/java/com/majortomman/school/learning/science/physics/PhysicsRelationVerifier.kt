package com.majortomman.school.learning.science.physics

import kotlin.math.abs
import kotlin.math.pow

enum class PhysicsRelationId(
    val display: String,
    val target: String,
    val variables: List<String>,
    val condition: String,
) {
    SPEED("v=s/t", "v", listOf("s", "t"), "路程与时间使用同一参考过程，且 t>0"),
    ACCELERATION("a=(v-v0)/t", "a", listOf("v", "v0", "t"), "平均加速度模型，且 t>0"),
    DENSITY("ρ=m/V", "rho", listOf("m", "V"), "物质样品质量与体积对应，且 V>0"),
    PRESSURE("p=F/S", "p", listOf("F", "S"), "压力垂直作用于受力面积，且 S>0"),
    NEWTON_SECOND("F=m·a", "F", listOf("m", "a"), "F 表示合力，质量 m>0"),
    WORK("W=F·s", "W", listOf("F", "s"), "力与位移同方向；一般情形还需方向夹角"),
    POWER("P=W/t", "P", listOf("W", "t"), "平均功率，且 t>0"),
    KINETIC_ENERGY("Ek=m·v²/2", "Ek", listOf("m", "v"), "经典力学、质点模型，质量 m>0"),
    GRAVITATIONAL_POTENTIAL("Ep=m·g·h", "Ep", listOf("m", "g", "h"), "近地面匀强重力场，零势能面已选定"),
    MOMENTUM("p=m·v", "p", listOf("m", "v"), "经典力学，速度方向决定动量方向"),
    HEAT("Q=c·m·ΔT", "Q", listOf("c", "m", "dT"), "无相变且比热容近似不变"),
    WAVE_SPEED("v=f·λ", "v", listOf("f", "lambda"), "同一介质中的周期波"),
    OHM("U=I·R", "U", listOf("I", "R"), "导体状态和温度条件适合欧姆定律"),
    ELECTRIC_POWER("P=U·I", "P", listOf("U", "I"), "电压与电流对应同一用电器和时刻"),
    ELECTRIC_ENERGY("W=P·t", "W", listOf("P", "t"), "功率在该时间段内按教材模型处理"),
    JOULE_HEAT("Q=I²·R·t", "Q", listOf("I", "R", "t"), "纯电阻或教材规定的焦耳热模型"),
    FREQUENCY_PERIOD("f=1/T", "f", listOf("T"), "周期 T>0"),
    LENS("1/f=1/u+1/v", "f", listOf("u", "v"), "薄透镜、近轴光线；符号约定需与教材一致"),
}

data class PhysicsVariableValue(
    val symbol: String,
    val value: Double,
    val unit: String,
)

enum class PhysicsVerificationStatus {
    CORRECT,
    INCORRECT,
    MISSING_VALUES,
    INVALID_MODEL,
    NOT_ALLOWED,
}

data class PhysicsVerificationResult(
    val relation: PhysicsRelationId,
    val status: PhysicsVerificationStatus,
    val expected: Double? = null,
    val submitted: Double? = null,
    val targetUnit: String = "",
    val steps: List<String> = emptyList(),
    val message: String,
)

object PhysicsRelationVerifier {
    private const val EPSILON = 1e-7

    fun allowedRelations(category: PhysicsCourseCategory): List<PhysicsRelationId> = when (category) {
        PhysicsCourseCategory.MEASUREMENT -> listOf(PhysicsRelationId.DENSITY)
        PhysicsCourseCategory.KINEMATICS -> listOf(PhysicsRelationId.SPEED, PhysicsRelationId.ACCELERATION)
        PhysicsCourseCategory.FORCE -> listOf(PhysicsRelationId.PRESSURE, PhysicsRelationId.NEWTON_SECOND)
        PhysicsCourseCategory.ENERGY -> listOf(
            PhysicsRelationId.WORK,
            PhysicsRelationId.POWER,
            PhysicsRelationId.KINETIC_ENERGY,
            PhysicsRelationId.GRAVITATIONAL_POTENTIAL,
        )
        PhysicsCourseCategory.MOMENTUM -> listOf(PhysicsRelationId.MOMENTUM)
        PhysicsCourseCategory.THERMAL -> listOf(PhysicsRelationId.HEAT)
        PhysicsCourseCategory.WAVE,
        PhysicsCourseCategory.SOUND,
        -> listOf(PhysicsRelationId.WAVE_SPEED, PhysicsRelationId.FREQUENCY_PERIOD)
        PhysicsCourseCategory.LIGHT -> listOf(PhysicsRelationId.LENS)
        PhysicsCourseCategory.ELECTRICITY -> listOf(
            PhysicsRelationId.OHM,
            PhysicsRelationId.ELECTRIC_POWER,
            PhysicsRelationId.ELECTRIC_ENERGY,
            PhysicsRelationId.JOULE_HEAT,
        )
        PhysicsCourseCategory.MAGNETISM -> emptyList()
        PhysicsCourseCategory.GRAVITY -> listOf(PhysicsRelationId.GRAVITATIONAL_POTENTIAL)
        PhysicsCourseCategory.EXPERIMENT -> listOf(
            PhysicsRelationId.SPEED,
            PhysicsRelationId.DENSITY,
            PhysicsRelationId.OHM,
        )
        PhysicsCourseCategory.GENERAL -> listOf(PhysicsRelationId.SPEED, PhysicsRelationId.POWER)
    }

    fun verify(
        category: PhysicsCourseCategory,
        relation: PhysicsRelationId,
        values: Map<String, PhysicsVariableValue>,
        submittedTarget: PhysicsVariableValue?,
        conditionsAccepted: Boolean,
    ): PhysicsVerificationResult {
        if (relation !in allowedRelations(category)) {
            return PhysicsVerificationResult(
                relation = relation,
                status = PhysicsVerificationStatus.NOT_ALLOWED,
                message = "${relation.display} 不属于当前教材主题允许的验证模型。",
            )
        }
        if (!conditionsAccepted) {
            return PhysicsVerificationResult(
                relation = relation,
                status = PhysicsVerificationStatus.INVALID_MODEL,
                message = "请先确认模型条件：${relation.condition}。",
            )
        }
        val missing = relation.variables.filterNot(values::containsKey)
        if (missing.isNotEmpty() || submittedTarget == null) {
            val names = buildList {
                addAll(missing)
                if (submittedTarget == null) add(relation.target)
            }
            return PhysicsVerificationResult(
                relation = relation,
                status = PhysicsVerificationStatus.MISSING_VALUES,
                message = "请填写 ${names.joinToString("、")}。",
            )
        }

        return runCatching {
            val expected = calculate(relation, values.mapValues { it.value.value })
            require(expected.isFinite()) { "计算结果不是有限值。" }
            val submitted = submittedTarget.value
            require(submitted.isFinite()) { "提交值不是有限数。" }
            val correct = nearlyEqual(expected, submitted)
            PhysicsVerificationResult(
                relation = relation,
                status = if (correct) PhysicsVerificationStatus.CORRECT else PhysicsVerificationStatus.INCORRECT,
                expected = expected,
                submitted = submitted,
                targetUnit = submittedTarget.unit,
                steps = buildSteps(relation, values, expected, submittedTarget.unit),
                message = if (correct) {
                    "数值与当前物理模型一致；仍需确认所用单位已经统一。"
                } else {
                    "当前结果与模型计算不一致，请检查单位换算、代入顺序和模型条件。"
                },
            )
        }.getOrElse { error ->
            PhysicsVerificationResult(
                relation = relation,
                status = PhysicsVerificationStatus.INVALID_MODEL,
                message = error.message ?: "无法使用当前物理模型计算。",
            )
        }
    }

    fun calculate(relation: PhysicsRelationId, v: Map<String, Double>): Double = when (relation) {
        PhysicsRelationId.SPEED -> v.required("s") / v.positive("t")
        PhysicsRelationId.ACCELERATION -> (v.required("v") - v.required("v0")) / v.positive("t")
        PhysicsRelationId.DENSITY -> v.required("m") / v.positive("V")
        PhysicsRelationId.PRESSURE -> v.required("F") / v.positive("S")
        PhysicsRelationId.NEWTON_SECOND -> v.positive("m") * v.required("a")
        PhysicsRelationId.WORK -> v.required("F") * v.required("s")
        PhysicsRelationId.POWER -> v.required("W") / v.positive("t")
        PhysicsRelationId.KINETIC_ENERGY -> v.positive("m") * v.required("v").pow(2) / 2.0
        PhysicsRelationId.GRAVITATIONAL_POTENTIAL -> v.positive("m") * v.positive("g") * v.required("h")
        PhysicsRelationId.MOMENTUM -> v.positive("m") * v.required("v")
        PhysicsRelationId.HEAT -> v.positive("c") * v.positive("m") * v.required("dT")
        PhysicsRelationId.WAVE_SPEED -> v.positive("f") * v.positive("lambda")
        PhysicsRelationId.OHM -> v.required("I") * v.positive("R")
        PhysicsRelationId.ELECTRIC_POWER -> v.required("U") * v.required("I")
        PhysicsRelationId.ELECTRIC_ENERGY -> v.required("P") * v.positive("t")
        PhysicsRelationId.JOULE_HEAT -> v.required("I").pow(2) * v.positive("R") * v.positive("t")
        PhysicsRelationId.FREQUENCY_PERIOD -> 1.0 / v.positive("T")
        PhysicsRelationId.LENS -> {
            val reciprocal = 1.0 / v.requiredNonZero("u") + 1.0 / v.requiredNonZero("v")
            require(abs(reciprocal) > EPSILON) { "当前物距与像距使焦距无有限解。" }
            1.0 / reciprocal
        }
    }

    private fun buildSteps(
        relation: PhysicsRelationId,
        values: Map<String, PhysicsVariableValue>,
        expected: Double,
        targetUnit: String,
    ): List<String> = listOf(
        "选用关系：${relation.display}",
        "适用条件：${relation.condition}",
        "代入：${relation.variables.joinToString { symbol -> "$symbol=${format(values.getValue(symbol).value)} ${values.getValue(symbol).unit}" }}",
        "计算：${relation.target}=${format(expected)} $targetUnit",
    )

    private fun Map<String, Double>.required(symbol: String): Double =
        get(symbol)?.takeIf(Double::isFinite) ?: error("缺少有效物理量 $symbol。")

    private fun Map<String, Double>.positive(symbol: String): Double =
        required(symbol).also { require(it > 0.0) { "$symbol 必须大于 0。" } }

    private fun Map<String, Double>.requiredNonZero(symbol: String): Double =
        required(symbol).also { require(abs(it) > EPSILON) { "$symbol 不能为 0。" } }

    private fun nearlyEqual(left: Double, right: Double): Boolean {
        val scale = maxOf(1.0, abs(left), abs(right))
        return abs(left - right) <= EPSILON * scale
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0
        return if (abs(rounded - rounded.toLong()) < 1e-9) rounded.toLong().toString()
        else rounded.toString().trimEnd('0').trimEnd('.')
    }
}
