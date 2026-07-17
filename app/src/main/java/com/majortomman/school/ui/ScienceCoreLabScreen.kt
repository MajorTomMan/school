package com.majortomman.school.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.science.expression.BigRational
import com.majortomman.school.learning.science.expression.ScienceExpressionEvaluator
import com.majortomman.school.learning.science.expression.ScienceExpressionParser
import com.majortomman.school.learning.science.expression.ScienceExpressionRenderer
import com.majortomman.school.learning.science.expression.ScienceExpressionSimplifier
import com.majortomman.school.learning.science.math.AlgebraSolver
import com.majortomman.school.learning.science.math.EquationSolution
import com.majortomman.school.learning.science.math.Vector2
import com.majortomman.school.learning.science.physics.CircuitComponent
import com.majortomman.school.learning.science.physics.CircuitGraph
import com.majortomman.school.learning.science.physics.CircuitNodeId
import com.majortomman.school.learning.science.physics.LinearDcCircuitSolver
import com.majortomman.school.learning.science.quantity.QuantityParser
import com.majortomman.school.learning.science.quantity.UnitCatalog

private enum class ScienceCoreSection(val label: String) {
    EXPRESSION("精确表达式"),
    QUANTITY("单位与量纲"),
    MATHEMATICS("数学深化"),
    PHYSICS("电路与电力"),
}

@Composable
internal fun ScienceCoreLabSample() {
    var selectedName by rememberSaveable { mutableStateOf(ScienceCoreSection.EXPRESSION.name) }
    val selected = ScienceCoreSection.valueOf(selectedName)

    SectionTitle("科学计算内核", InteractiveBlue)
    Spacer(Modifier.height(12.dp))
    Text(
        "这里验证数学、物理和化学共用的确定性基础。精确值不会过早转换为 Double，物理计算会同时检查模型条件和量纲。",
        color = InteractiveMuted,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    )
    Spacer(Modifier.height(18.dp))
    ScienceCoreSection.entries.chunked(2).forEach { rowItems ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            rowItems.forEach { item ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedName = item.name }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        item.label,
                        color = if (selected == item) InteractiveBlue else InteractiveMuted,
                        fontSize = 14.sp,
                        fontWeight = if (selected == item) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (selected == item) 2.dp else 1.dp)
                            .background(if (selected == item) InteractiveBlue else InteractiveLine),
                    )
                }
            }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(24.dp))

    when (selected) {
        ScienceCoreSection.EXPRESSION -> ExactExpressionSample()
        ScienceCoreSection.QUANTITY -> QuantitySample()
        ScienceCoreSection.MATHEMATICS -> MathematicsSample()
        ScienceCoreSection.PHYSICS -> PhysicsCircuitSample()
    }
}

@Composable
private fun ExactExpressionSample() {
    var input by rememberSaveable { mutableStateOf("√8 + 3π + 2π") }
    val result = runCatching {
        val parsed = ScienceExpressionParser.parse(input)
        val simplified = ScienceExpressionSimplifier.simplify(parsed)
        Triple(
            ScienceExpressionRenderer.render(parsed),
            ScienceExpressionRenderer.render(simplified),
            ScienceExpressionEvaluator.evaluate(simplified),
        )
    }

    Text("根号、π、分数和隐式乘法", color = InteractivePurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    CoreTextInput(input) { input = it.take(120) }
    Spacer(Modifier.height(18.dp))
    result.fold(
        onSuccess = { (parsed, simplified, approximate) ->
            CoreResultLine("解析", parsed)
            CoreResultLine("精确化简", simplified)
            CoreResultLine(
                "近似值",
                if (kotlin.math.abs(approximate.imaginary) < 1e-10) {
                    "%.6f".format(approximate.real)
                } else {
                    "%.6f %+.6fi".format(approximate.real, approximate.imaginary)
                },
            )
        },
        onFailure = { CoreError(it.message ?: "无法解析表达式。") },
    )
    Spacer(Modifier.height(14.dp))
    Text(
        "可试：1/3 + 1/6、√72、2π + 3π、2√(x+1)。含变量的式子保持符号形式。",
        color = InteractiveMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun QuantitySample() {
    var input by rememberSaveable { mutableStateOf("72 km/h") }
    var targetSymbol by rememberSaveable { mutableStateOf("m/s") }
    val result = runCatching {
        val source = QuantityParser.parse(input)
        source to source.convertTo(UnitCatalog.bySymbol(targetSymbol))
    }

    Text("精确单位换算与量纲检查", color = InteractiveGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    CoreTextInput(input) { input = it.take(80) }
    Spacer(Modifier.height(14.dp))
    Text("目标单位", color = InteractiveMuted, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        listOf("m/s", "km/h", "J", "kWh", "K", "°C").forEach { symbol ->
            Text(
                symbol,
                modifier = Modifier.clickable { targetSymbol = symbol }.padding(vertical = 8.dp),
                color = if (targetSymbol == symbol) InteractiveGreen else InteractiveMuted,
                fontSize = 14.sp,
                fontWeight = if (targetSymbol == symbol) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    result.fold(
        onSuccess = { (source, converted) ->
            CoreResultLine("原值", "${source.value} ${source.unit.symbol}")
            CoreResultLine("目标值", "${converted.value} ${converted.unit.symbol}")
            CoreResultLine("量纲", source.dimension.symbol())
        },
        onFailure = { CoreError(it.message ?: "无法换算单位。") },
    )
    Spacer(Modifier.height(14.dp))
    Text(
        "可试：1 kWh → J、20 °C → K、1000 g → kg。不同量纲之间会明确拒绝换算。",
        color = InteractiveMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun MathematicsSample() {
    var aText by rememberSaveable { mutableStateOf("1") }
    var bText by rememberSaveable { mutableStateOf("-5") }
    var cText by rememberSaveable { mutableStateOf("6") }
    val result = runCatching {
        AlgebraSolver.solveQuadratic(
            a = BigRational.parse(aText),
            b = BigRational.parse(bText),
            c = BigRational.parse(cText),
        )
    }
    val vector = Vector2(3.0, 4.0)
    val axis = Vector2(1.0, 1.0)
    val projection = vector.projectionOnto(axis)

    Text("方程、判别式、向量与证明步骤", color = InteractiveYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text("输入 ax² + bx + c = 0 的系数", color = InteractiveMuted, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CoefficientInput("a", aText) { aText = it.take(12) }
        CoefficientInput("b", bText) { bText = it.take(12) }
        CoefficientInput("c", cText) { cText = it.take(12) }
    }
    Spacer(Modifier.height(18.dp))
    result.fold(
        onSuccess = { solution ->
            val roots = when (val value = solution.solution) {
                EquationSolution.AllValues -> "所有实数"
                EquationSolution.NoSolution -> "实数域无解"
                is EquationSolution.Roots -> value.values.joinToString("，") { "x=${it.text}" }
            }
            CoreResultLine("精确根", roots)
            solution.steps.forEach { step ->
                CoreResultLine(step.title, step.expression)
                Text(step.reason, color = InteractiveMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        },
        onFailure = { CoreError(it.message ?: "无法求解方程。") },
    )
    Spacer(Modifier.height(24.dp))
    CoreResultLine("向量", "v=(3,4)")
    CoreResultLine("投影方向", "u=(1,1)")
    CoreResultLine("v 在 u 上的投影", "(%.2f, %.2f)".format(projection.x, projection.y))
    Spacer(Modifier.height(12.dp))
    Text(
        "同一底层还提供多项式四则运算、一次不等式、函数定义域、直线与圆、三维向量和平面、证明步骤依赖检查。",
        color = InteractiveMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun PhysicsCircuitSample() {
    var parallel by rememberSaveable { mutableStateOf(false) }
    val ground = CircuitNodeId("GND")
    val positive = CircuitNodeId("P")
    val middle = CircuitNodeId("M")
    val graph = if (parallel) {
        CircuitGraph(
            nodes = setOf(ground, positive),
            ground = ground,
            components = listOf(
                CircuitComponent.VoltageSource("电池", positive, ground, 12.0),
                CircuitComponent.Lamp("灯泡 A", positive, ground, 12.0, 12.0),
                CircuitComponent.Lamp("灯泡 B", positive, ground, 12.0, 12.0),
            ),
        )
    } else {
        CircuitGraph(
            nodes = setOf(ground, positive, middle),
            ground = ground,
            components = listOf(
                CircuitComponent.VoltageSource("电池", positive, ground, 12.0),
                CircuitComponent.Lamp("灯泡 A", positive, middle, 6.0, 6.0),
                CircuitComponent.Lamp("灯泡 B", middle, ground, 6.0, 6.0),
            ),
        )
    }
    val result = runCatching { LinearDcCircuitSolver.solve(graph) }

    Text("直流电路图、节点求解与功率", color = InteractiveRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        listOf(false to "两灯串联", true to "两灯并联").forEach { (value, label) ->
            Text(
                label,
                modifier = Modifier.clickable { parallel = value }.padding(vertical = 8.dp),
                color = if (parallel == value) InteractiveRed else InteractiveMuted,
                fontSize = 14.sp,
                fontWeight = if (parallel == value) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        if (parallel) "12 V 电源，两只 12 Ω 灯泡并联" else "12 V 电源，两只 6 Ω 灯泡串联",
        color = InteractiveWhite,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(16.dp))
    result.fold(
        onSuccess = { solution ->
            solution.nodeVoltages.entries.sortedBy { it.key.value }.forEach { (node, voltage) ->
                CoreResultLine("节点 $node", "%.3f V".format(voltage))
            }
            graph.components.filterIsInstance<CircuitComponent.Lamp>().forEach { lamp ->
                val state = solution.component(lamp.id)
                CoreResultLine(
                    lamp.id,
                    "I=%.3f A · P=%.3f W".format(state.currentAmperes, state.powerWatts),
                )
                CoreResultLine("亮度比例", "%.0f%%".format((state.lampBrightnessRatio ?: 0.0) * 100.0))
            }
            val source = solution.component("电池")
            CoreResultLine("电源输出功率", "%.3f W".format(kotlin.math.abs(source.powerWatts ?: 0.0)))
            CoreResultLine("拓扑检查", if (solution.topology.issues.isEmpty()) "无异常" else "${solution.topology.issues.size} 条提示")
        },
        onFailure = { CoreError(it.message ?: "电路无法求解。") },
    )
    Spacer(Modifier.height(14.dp))
    Text(
        "底层还会检查电源短路、电流表并联、电压表疑似串联、开路和悬空节点；电容、电感和二极管已预留结构，但不伪装成已完成的直流线性求解。",
        color = InteractiveMuted,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun CoefficientInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = InteractiveMuted, fontSize = 12.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = InteractiveWhite, fontSize = 20.sp),
            cursorBrush = SolidColor(InteractiveYellow),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
    }
}

@Composable
private fun CoreTextInput(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(color = InteractiveWhite, fontSize = 22.sp, lineHeight = 30.sp),
        cursorBrush = SolidColor(InteractiveBlue),
    )
    Spacer(Modifier.height(10.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(InteractiveLine))
}

@Composable
private fun CoreResultLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = InteractiveMuted, fontSize = 13.sp)
        Text(value, color = InteractiveWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CoreError(message: String) {
    Text(message, color = InteractiveRed, fontSize = 14.sp, lineHeight = 21.sp)
}
