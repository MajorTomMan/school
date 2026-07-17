package com.majortomman.school.learning.science.quantity

import com.majortomman.school.learning.science.expression.BigRational

/** Seven SI base dimensions in the order L, M, T, I, Θ, N, J. */
data class Dimension(
    val length: Int = 0,
    val mass: Int = 0,
    val time: Int = 0,
    val electricCurrent: Int = 0,
    val temperature: Int = 0,
    val amountOfSubstance: Int = 0,
    val luminousIntensity: Int = 0,
) {
    operator fun times(other: Dimension): Dimension = Dimension(
        length + other.length,
        mass + other.mass,
        time + other.time,
        electricCurrent + other.electricCurrent,
        temperature + other.temperature,
        amountOfSubstance + other.amountOfSubstance,
        luminousIntensity + other.luminousIntensity,
    )

    operator fun div(other: Dimension): Dimension = this * other.pow(-1)

    fun pow(exponent: Int): Dimension = Dimension(
        length * exponent,
        mass * exponent,
        time * exponent,
        electricCurrent * exponent,
        temperature * exponent,
        amountOfSubstance * exponent,
        luminousIntensity * exponent,
    )

    val isDimensionless: Boolean get() = this == NONE

    fun symbol(): String {
        val parts = listOf(
            "L" to length,
            "M" to mass,
            "T" to time,
            "I" to electricCurrent,
            "Θ" to temperature,
            "N" to amountOfSubstance,
            "J" to luminousIntensity,
        ).filter { it.second != 0 }
        return if (parts.isEmpty()) "1" else parts.joinToString("·") { (symbol, exponent) ->
            if (exponent == 1) symbol else "$symbol^$exponent"
        }
    }

    companion object {
        val NONE = Dimension()
        val LENGTH = Dimension(length = 1)
        val MASS = Dimension(mass = 1)
        val TIME = Dimension(time = 1)
        val CURRENT = Dimension(electricCurrent = 1)
        val TEMPERATURE = Dimension(temperature = 1)
        val AMOUNT = Dimension(amountOfSubstance = 1)
        val LUMINOUS_INTENSITY = Dimension(luminousIntensity = 1)

        val AREA = LENGTH.pow(2)
        val VOLUME = LENGTH.pow(3)
        val SPEED = LENGTH / TIME
        val ACCELERATION = LENGTH / TIME.pow(2)
        val FORCE = MASS * ACCELERATION
        val PRESSURE = FORCE / AREA
        val ENERGY = FORCE * LENGTH
        val POWER = ENERGY / TIME
        val CHARGE = CURRENT * TIME
        val VOLTAGE = POWER / CURRENT
        val RESISTANCE = VOLTAGE / CURRENT
        val CAPACITANCE = CHARGE / VOLTAGE
        val FREQUENCY = TIME.pow(-1)
        val DENSITY = MASS / VOLUME
    }
}

data class UnitDefinition(
    val symbol: String,
    val name: String,
    val dimension: Dimension,
    val scaleToSi: BigRational = BigRational.ONE,
    /** SI value = (display value + offsetBeforeScale) × scaleToSi. */
    val offsetBeforeScale: BigRational = BigRational.ZERO,
) {
    init {
        require(symbol.isNotBlank()) { "Unit symbol cannot be blank." }
        require(!scaleToSi.isZero) { "Unit scale cannot be zero." }
    }

    fun toSi(value: BigRational): BigRational = (value + offsetBeforeScale) * scaleToSi

    fun fromSi(value: BigRational): BigRational = value / scaleToSi - offsetBeforeScale
}

data class Quantity(
    val value: BigRational,
    val unit: UnitDefinition,
) {
    val dimension: Dimension get() = unit.dimension

    fun convertTo(target: UnitDefinition): Quantity {
        require(dimension == target.dimension) {
            "量纲不一致：${unit.symbol} 是 ${dimension.symbol()}，${target.symbol} 是 ${target.dimension.symbol()}。"
        }
        return Quantity(target.fromSi(unit.toSi(value)), target)
    }

    operator fun plus(other: Quantity): Quantity {
        require(dimension == other.dimension) { "不同量纲不能相加。" }
        val converted = other.convertTo(unit)
        return copy(value = value + converted.value)
    }

    operator fun minus(other: Quantity): Quantity {
        require(dimension == other.dimension) { "不同量纲不能相减。" }
        val converted = other.convertTo(unit)
        return copy(value = value - converted.value)
    }
}

object UnitCatalog {
    val ONE = UnitDefinition("1", "无量纲", Dimension.NONE)

    val METER = UnitDefinition("m", "米", Dimension.LENGTH)
    val CENTIMETER = UnitDefinition("cm", "厘米", Dimension.LENGTH, BigRational.of(1, 100))
    val MILLIMETER = UnitDefinition("mm", "毫米", Dimension.LENGTH, BigRational.of(1, 1_000))
    val KILOMETER = UnitDefinition("km", "千米", Dimension.LENGTH, BigRational.of(1_000))

    val SQUARE_METER = UnitDefinition("m²", "平方米", Dimension.AREA)
    val CUBIC_METER = UnitDefinition("m³", "立方米", Dimension.VOLUME)
    val LITER = UnitDefinition("L", "升", Dimension.VOLUME, BigRational.of(1, 1_000))
    val MILLILITER = UnitDefinition("mL", "毫升", Dimension.VOLUME, BigRational.of(1, 1_000_000))

    val SECOND = UnitDefinition("s", "秒", Dimension.TIME)
    val MINUTE = UnitDefinition("min", "分钟", Dimension.TIME, BigRational.of(60))
    val HOUR = UnitDefinition("h", "小时", Dimension.TIME, BigRational.of(3_600))

    val KILOGRAM = UnitDefinition("kg", "千克", Dimension.MASS)
    val GRAM = UnitDefinition("g", "克", Dimension.MASS, BigRational.of(1, 1_000))
    val MILLIGRAM = UnitDefinition("mg", "毫克", Dimension.MASS, BigRational.of(1, 1_000_000))

    val AMPERE = UnitDefinition("A", "安培", Dimension.CURRENT)
    val MILLIAMPERE = UnitDefinition("mA", "毫安", Dimension.CURRENT, BigRational.of(1, 1_000))
    val KELVIN = UnitDefinition("K", "开尔文", Dimension.TEMPERATURE)
    val CELSIUS = UnitDefinition("°C", "摄氏度", Dimension.TEMPERATURE, offsetBeforeScale = BigRational.of(27_315, 100))
    val MOLE = UnitDefinition("mol", "摩尔", Dimension.AMOUNT)

    val METER_PER_SECOND = UnitDefinition("m/s", "米每秒", Dimension.SPEED)
    val KILOMETER_PER_HOUR = UnitDefinition("km/h", "千米每小时", Dimension.SPEED, BigRational.of(5, 18))
    val METER_PER_SECOND_SQUARED = UnitDefinition("m/s²", "米每二次方秒", Dimension.ACCELERATION)

    val NEWTON = UnitDefinition("N", "牛顿", Dimension.FORCE)
    val PASCAL = UnitDefinition("Pa", "帕斯卡", Dimension.PRESSURE)
    val JOULE = UnitDefinition("J", "焦耳", Dimension.ENERGY)
    val KILOJOULE = UnitDefinition("kJ", "千焦", Dimension.ENERGY, BigRational.of(1_000))
    val WATT = UnitDefinition("W", "瓦特", Dimension.POWER)
    val KILOWATT = UnitDefinition("kW", "千瓦", Dimension.POWER, BigRational.of(1_000))
    val COULOMB = UnitDefinition("C", "库仑", Dimension.CHARGE)
    val VOLT = UnitDefinition("V", "伏特", Dimension.VOLTAGE)
    val MILLIVOLT = UnitDefinition("mV", "毫伏", Dimension.VOLTAGE, BigRational.of(1, 1_000))
    val OHM = UnitDefinition("Ω", "欧姆", Dimension.RESISTANCE)
    val KILOOHM = UnitDefinition("kΩ", "千欧", Dimension.RESISTANCE, BigRational.of(1_000))
    val FARAD = UnitDefinition("F", "法拉", Dimension.CAPACITANCE)
    val HERTZ = UnitDefinition("Hz", "赫兹", Dimension.FREQUENCY)
    val KILOWATT_HOUR = UnitDefinition("kWh", "千瓦时", Dimension.ENERGY, BigRational.of(3_600_000))
    val KILOGRAM_PER_CUBIC_METER = UnitDefinition("kg/m³", "千克每立方米", Dimension.DENSITY)
    val GRAM_PER_CUBIC_CENTIMETER = UnitDefinition("g/cm³", "克每立方厘米", Dimension.DENSITY, BigRational.of(1_000))

    private val units = listOf(
        ONE,
        METER, CENTIMETER, MILLIMETER, KILOMETER,
        SQUARE_METER, CUBIC_METER, LITER, MILLILITER,
        SECOND, MINUTE, HOUR,
        KILOGRAM, GRAM, MILLIGRAM,
        AMPERE, MILLIAMPERE, KELVIN, CELSIUS, MOLE,
        METER_PER_SECOND, KILOMETER_PER_HOUR, METER_PER_SECOND_SQUARED,
        NEWTON, PASCAL, JOULE, KILOJOULE, WATT, KILOWATT,
        COULOMB, VOLT, MILLIVOLT, OHM, KILOOHM, FARAD, HERTZ,
        KILOWATT_HOUR, KILOGRAM_PER_CUBIC_METER, GRAM_PER_CUBIC_CENTIMETER,
    )

    private val bySymbol = units.associateBy { it.symbol }

    fun bySymbol(symbol: String): UnitDefinition =
        bySymbol[symbol.trim()] ?: error("暂不支持单位“$symbol”。")

    fun all(): List<UnitDefinition> = units
}

object QuantityParser {
    private val pattern = Regex("^\\s*([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)(?:/\\d+)?)\\s*(.+?)\\s*$")

    fun parse(text: String): Quantity {
        val match = pattern.matchEntire(text) ?: error("请输入“数值 单位”，例如 72 km/h。")
        return Quantity(
            value = BigRational.parse(match.groupValues[1]),
            unit = UnitCatalog.bySymbol(match.groupValues[2]),
        )
    }
}

data class DimensionalCheck(
    val valid: Boolean,
    val left: Dimension,
    val right: Dimension,
    val message: String,
)

object DimensionalAnalyzer {
    fun requireSame(left: Dimension, right: Dimension): DimensionalCheck = if (left == right) {
        DimensionalCheck(true, left, right, "量纲一致：${left.symbol()}。")
    } else {
        DimensionalCheck(false, left, right, "量纲不一致：${left.symbol()} ≠ ${right.symbol()}。")
    }
}
