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
