package com.majortomman.school.learning.lab

import kotlin.math.cos
import kotlin.math.sin

data class ComplexValue(
    val real: Double,
    val imaginary: Double,
) {
    operator fun plus(other: ComplexValue): ComplexValue = ComplexValue(
        real = real + other.real,
        imaginary = imaginary + other.imaginary,
    )

    operator fun times(other: ComplexValue): ComplexValue = ComplexValue(
        real = real * other.real - imaginary * other.imaginary,
        imaginary = real * other.imaginary + imaginary * other.real,
    )
}

data class Point3D(
    val x: Double,
    val y: Double,
    val z: Double,
)

data class Point2D(
    val x: Double,
    val y: Double,
    val depth: Double,
)

object OrthographicProjector {
    fun project(
        point: Point3D,
        yawDegrees: Double,
        pitchDegrees: Double,
    ): Point2D {
        val yaw = Math.toRadians(yawDegrees)
        val pitch = Math.toRadians(pitchDegrees)

        val xAfterYaw = point.x * cos(yaw) - point.y * sin(yaw)
        val yAfterYaw = point.x * sin(yaw) + point.y * cos(yaw)
        val screenY = point.z * cos(pitch) - yAfterYaw * sin(pitch)
        val depth = point.z * sin(pitch) + yAfterYaw * cos(pitch)
        return Point2D(xAfterYaw, screenY, depth)
    }
}

data class AtomCounts(
    val hydrogen: Int,
    val oxygen: Int,
)

data class WaterEquationBalance(
    val hydrogenCoefficient: Int,
    val oxygenCoefficient: Int,
    val waterCoefficient: Int,
) {
    val reactants: AtomCounts
        get() = AtomCounts(
            hydrogen = hydrogenCoefficient * 2,
            oxygen = oxygenCoefficient * 2,
        )

    val products: AtomCounts
        get() = AtomCounts(
            hydrogen = waterCoefficient * 2,
            oxygen = waterCoefficient,
        )

    val isBalanced: Boolean
        get() = reactants == products && hydrogenCoefficient > 0 && oxygenCoefficient > 0 && waterCoefficient > 0
}

/**
 * 在生成物限定为 H₂O 的前提下，根据左侧 H₂、O₂ 系数推导右侧系数。
 *
 * 对于 aH₂ + bO₂ → cH₂O：
 * - 氢守恒要求 c = a；
 * - 氧守恒要求 c = 2b；
 * 因此只有 a = 2b 时，才能得到只含 H₂O 的合法右侧。
 */
data class WaterEquationDerivation(
    val hydrogenCoefficient: Int,
    val oxygenCoefficient: Int,
) {
    val reactants: AtomCounts
        get() = AtomCounts(
            hydrogen = hydrogenCoefficient * 2,
            oxygen = oxygenCoefficient * 2,
        )

    val waterCoefficient: Int?
        get() = hydrogenCoefficient.takeIf {
            hydrogenCoefficient > 0 &&
                oxygenCoefficient > 0 &&
                hydrogenCoefficient == oxygenCoefficient * 2
        }

    val products: AtomCounts?
        get() = waterCoefficient?.let { coefficient ->
            AtomCounts(
                hydrogen = coefficient * 2,
                oxygen = coefficient,
            )
        }

    val balance: WaterEquationBalance?
        get() = waterCoefficient?.let { coefficient ->
            WaterEquationBalance(
                hydrogenCoefficient = hydrogenCoefficient,
                oxygenCoefficient = oxygenCoefficient,
                waterCoefficient = coefficient,
            )
        }

    val isValid: Boolean
        get() = waterCoefficient != null

    val explanation: String
        get() = when {
            hydrogenCoefficient <= 0 || oxygenCoefficient <= 0 ->
                "左侧必须同时包含正整数个 H₂ 和 O₂。"
            isValid ->
                "左侧 H₂ 与 O₂ 的系数比为 2:1，右侧可自动生成 ${waterCoefficient}H₂O。"
            else ->
                "右侧若只含 H₂O，H₂ 与 O₂ 的系数必须满足 2:1；当前应将 H₂ 系数设为 ${oxygenCoefficient * 2}。"
        }
}

enum class CellPart(
    val label: String,
    val description: String,
    val normalizedX: Float,
    val normalizedY: Float,
) {
    CELL_WALL("细胞壁", "位于植物细胞最外层，对细胞起支持和保护作用。", 0.13f, 0.50f),
    CELL_MEMBRANE("细胞膜", "紧贴细胞壁内侧，控制物质进出细胞。", 0.18f, 0.50f),
    CYTOPLASM("细胞质", "位于细胞膜以内，细胞核和其他结构分布其中。", 0.73f, 0.68f),
    NUCLEUS("细胞核", "通常呈圆形或椭圆形，是细胞中较明显的结构。", 0.35f, 0.40f),
    VACUOLE("液泡", "植物细胞中常见的较大囊状结构，内部有细胞液。", 0.58f, 0.48f),
    CHLOROPLAST("叶绿体", "在植物绿色部分的细胞中常见，通常画成绿色椭圆形结构。", 0.65f, 0.72f),
}
