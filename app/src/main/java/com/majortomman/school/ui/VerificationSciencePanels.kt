package com.majortomman.school.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majortomman.school.learning.course.BiologyCourseCategory
import com.majortomman.school.learning.course.PhysicsCourseCategory
import com.majortomman.school.learning.science.biology.BiologyRelationId
import com.majortomman.school.learning.science.biology.BiologyRelationVerifier
import com.majortomman.school.learning.science.biology.BiologyVerificationStatus
import com.majortomman.school.learning.science.chemistry.ChemistryVerificationMode
import com.majortomman.school.learning.science.chemistry.ChemistryVerificationStatus
import com.majortomman.school.learning.science.chemistry.ChemistryVerifier
import com.majortomman.school.learning.science.physics.PhysicsRelationId
import com.majortomman.school.learning.science.physics.PhysicsRelationVerifier
import com.majortomman.school.learning.science.physics.PhysicsVariableValue
import com.majortomman.school.learning.science.physics.PhysicsVerificationStatus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val physicsCategoryChoices = listOf(
    PhysicsCourseCategory.MEASUREMENT to "测量",
    PhysicsCourseCategory.KINEMATICS to "运动",
    PhysicsCourseCategory.FORCE to "力",
    PhysicsCourseCategory.ENERGY to "功与能",
    PhysicsCourseCategory.MOMENTUM to "动量",
    PhysicsCourseCategory.GRAVITY to "重力",
    PhysicsCourseCategory.THERMAL to "热学",
    PhysicsCourseCategory.WAVE to "波",
    PhysicsCourseCategory.SOUND to "声",
    PhysicsCourseCategory.LIGHT to "光",
    PhysicsCourseCategory.ELECTRICITY to "电学",
    PhysicsCourseCategory.EXPERIMENT to "实验",
)

private val biologyCategoryChoices = listOf(
    BiologyCourseCategory.CELL to "细胞",
    BiologyCourseCategory.METABOLISM to "代谢",
    BiologyCourseCategory.PLANT to "植物",
    BiologyCourseCategory.HUMAN to "人体",
    BiologyCourseCategory.GENETICS to "遗传",
    BiologyCourseCategory.REPRODUCTION to "生殖发育",
    BiologyCourseCategory.ECOLOGY to "生态",
    BiologyCourseCategory.EXPERIMENT to "实验",
)

@Composable
internal fun PhysicsVerificationPanel() {
    var categoryName by rememberSaveable { mutableStateOf(PhysicsCourseCategory.KINEMATICS.name) }
    val category = PhysicsCourseCategory.valueOf(categoryName)
    val allowedRelations = PhysicsRelationVerifier.allowedRelations(category)
    var relationName by rememberSaveable { mutableStateOf(PhysicsRelationId.SPEED.name) }
    val relation = allowedRelations.firstOrNull { it.name == relationName } ?: allowedRelations.first()
    var valueTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var unitTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var targetText by rememberSaveable { mutableStateOf("1") }
    var targetUnit by rememberSaveable { mutableStateOf(defaultPhysicsUnit(relation.target)) }
    var conditionsAccepted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(category) {
        if (relationName !in allowedRelations.map { it.name }) relationName = allowedRelations.first().name
    }
    LaunchedEffect(relation) {
        valueTexts = relation.variables.associateWith { defaultPhysicsValue(relation, it) }
        unitTexts = relation.variables.associateWith(::defaultPhysicsUnit)
        targetText = defaultPhysicsTarget(relation)
        targetUnit = defaultPhysicsUnit(relation.target)
        conditionsAccepted = false
    }

    val values = relation.variables.mapNotNull { symbol ->
        valueTexts[symbol]?.toDoubleOrNull()?.let { value ->
            symbol to PhysicsVariableValue(symbol, value, unitTexts[symbol].orEmpty())
        }
    }.toMap()
    val submitted = targetText.toDoubleOrNull()?.let { PhysicsVariableValue(relation.target, it, targetUnit) }
    val result = remember(category, relation, values, submitted, conditionsAccepted) {
        PhysicsRelationVerifier.verify(category, relation, values, submitted, conditionsAccepted)
    }

    SectionTitle("物理模型验证", InteractiveYellow)
    Spacer(Modifier.height(12.dp))
    Text(
        "先选择教材主题，再选择该主题允许的关系。验证不会把其他章节公式无条件塞进当前模型。",
        color = InteractiveMuted,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    Spacer(Modifier.height(14.dp))
    VerificationChoiceGrid(
        items = physicsCategoryChoices.map { (id, label) -> VerificationChoiceItem(id.name, label) },
        selectedId = categoryName,
        columns = 3,
        onSelect = { categoryName = it },
    )
    Spacer(Modifier.height(16.dp))
    VerificationChoiceGrid(
        items = allowedRelations.map { VerificationChoiceItem(it.name, it.display, it.condition) },
        selectedId = relation.name,
        onSelect = { relationName = it },
    )

    Spacer(Modifier.height(20.dp))
    PhysicsRelationDiagram(relation, values.mapValues { it.value.value })
    Spacer(Modifier.height(18.dp))

    relation.variables.forEach { symbol ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1.25f)) {
                VerificationNumberInput(
                    label = "已知量 $symbol",
                    value = valueTexts[symbol].orEmpty(),
                    unit = unitTexts[symbol].orEmpty(),
                    onValueChange = { valueTexts = valueTexts + (symbol to it) },
                )
            }
            Box(Modifier.weight(0.75f)) {
                VerificationTextInput(
                    label = "单位",
                    value = unitTexts[symbol].orEmpty(),
                    onValueChange = { unitTexts = unitTexts + (symbol to it) },
                    hint = defaultPhysicsUnit(symbol),
                    maxLength = 12,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.weight(1.25f)) {
            VerificationNumberInput(
                label = "待验证结果 ${relation.target}",
                value = targetText,
                unit = targetUnit,
                onValueChange = { targetText = it },
            )
        }
        Box(Modifier.weight(0.75f)) {
            VerificationTextInput(
                label = "结果单位",
                value = targetUnit,
                onValueChange = { targetUnit = it },
                hint = defaultPhysicsUnit(relation.target),
                maxLength = 12,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    VerificationToggle(
        label = "确认模型条件",
        checked = conditionsAccepted,
        description = relation.condition,
        onToggle = { conditionsAccepted = !conditionsAccepted },
    )
    Spacer(Modifier.height(18.dp))

    val color = when (result.status) {
        PhysicsVerificationStatus.CORRECT -> InteractiveGreen
        PhysicsVerificationStatus.INCORRECT,
        PhysicsVerificationStatus.INVALID_MODEL,
        PhysicsVerificationStatus.NOT_ALLOWED,
        -> InteractiveRed
        PhysicsVerificationStatus.MISSING_VALUES -> InteractiveYellow
    }
    VerificationStatusBlock(
        title = when (result.status) {
            PhysicsVerificationStatus.CORRECT -> "模型与数值一致"
            PhysicsVerificationStatus.INCORRECT -> "数值不一致"
            PhysicsVerificationStatus.MISSING_VALUES -> "等待完整参数"
            PhysicsVerificationStatus.INVALID_MODEL -> "模型条件不成立"
            PhysicsVerificationStatus.NOT_ALLOWED -> "当前主题不允许该关系"
        },
        normalized = relation.display,
        rows = buildList {
            result.expected?.let { add("模型计算值" to "${formatVerificationNumber(it)} ${result.targetUnit}") }
            result.submitted?.let { add("输入结果" to "${formatVerificationNumber(it)} ${result.targetUnit}") }
        },
        steps = result.steps,
        message = result.message,
        color = color,
    )
}

@Composable
internal fun ChemistryVerificationPanel() {
    var modeName by rememberSaveable { mutableStateOf(ChemistryVerificationMode.EQUATION_BALANCE.name) }
    val mode = ChemistryVerificationMode.valueOf(modeName)
    var primary by rememberSaveable { mutableStateOf("Fe+O2→Fe2O3") }
    var secondary by rememberSaveable { mutableStateOf("") }

    fun selectMode(id: String) {
        modeName = id
        val selected = ChemistryVerificationMode.valueOf(id)
        primary = chemistryExample(selected).first
        secondary = chemistryExample(selected).second
    }

    val result = remember(mode, primary, secondary) { ChemistryVerifier.verify(mode, primary, secondary) }

    SectionTitle("化学与有机化学验证", InteractiveYellow)
    Spacer(Modifier.height(12.dp))
    Text(
        "输入完整化学式、完整方程式或受支持的有机结构。配平只调整已给物种的系数，不预测未知生成物。",
        color = InteractiveMuted,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    Spacer(Modifier.height(14.dp))
    VerificationChoiceGrid(
        items = ChemistryVerificationMode.entries.map {
            VerificationChoiceItem(it.name, chemistryModeLabel(it), chemistryModeSubtitle(it))
        },
        selectedId = modeName,
        onSelect = ::selectMode,
    )
    Spacer(Modifier.height(16.dp))
    VerificationTextInput(
        label = chemistryPrimaryLabel(mode),
        value = primary,
        onValueChange = { primary = it },
        hint = chemistryExample(mode).first,
        maxLength = 300,
        accent = InteractiveYellow,
    )
    if (mode == ChemistryVerificationMode.ORGANIC_ISOMER) {
        Spacer(Modifier.height(14.dp))
        VerificationTextInput(
            label = "第二个有机结构",
            value = secondary,
            onValueChange = { secondary = it },
            hint = "COC",
            maxLength = 200,
            accent = InteractivePurple,
        )
    }
    Spacer(Modifier.height(20.dp))
    ChemistryResultDiagram(result.rows, result.status)
    Spacer(Modifier.height(18.dp))

    val color = when (result.status) {
        ChemistryVerificationStatus.VALID,
        ChemistryVerificationStatus.BALANCED,
        ChemistryVerificationStatus.BALANCED_RESULT,
        -> InteractiveGreen
        ChemistryVerificationStatus.UNBALANCED -> InteractiveYellow
        ChemistryVerificationStatus.INVALID,
        ChemistryVerificationStatus.UNSUPPORTED,
        -> InteractiveRed
    }
    VerificationStatusBlock(
        title = result.title,
        normalized = result.normalized,
        rows = result.rows,
        message = result.message,
        color = color,
    )
}

@Composable
internal fun BiologyVerificationPanel() {
    var categoryName by rememberSaveable { mutableStateOf(BiologyCourseCategory.CELL.name) }
    val category = BiologyCourseCategory.valueOf(categoryName)
    val allowedRelations = BiologyRelationVerifier.allowedRelations(category)
    var relationName by rememberSaveable { mutableStateOf(BiologyRelationId.MAGNIFICATION.name) }
    val relation = allowedRelations.firstOrNull { it.name == relationName } ?: allowedRelations.first()
    var valueTexts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var targetText by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf(defaultBiologyUnit(relation)) }
    var conditionsAccepted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(category) {
        if (relationName !in allowedRelations.map { it.name }) relationName = allowedRelations.first().name
    }
    LaunchedEffect(relation) {
        valueTexts = relation.variables.associateWith { defaultBiologyValue(relation, it) }
        targetText = defaultBiologyTarget(relation)
        unit = defaultBiologyUnit(relation)
        conditionsAccepted = false
    }

    val values = relation.variables.mapNotNull { symbol ->
        valueTexts[symbol]?.toDoubleOrNull()?.let { symbol to it }
    }.toMap()
    val submitted = targetText.toDoubleOrNull()
    val result = remember(category, relation, values, submitted, unit, conditionsAccepted) {
        BiologyRelationVerifier.verify(category, relation, values, submitted, unit, conditionsAccepted)
    }

    SectionTitle("生物数量关系验证", InteractiveYellow)
    Spacer(Modifier.height(12.dp))
    Text(
        "先选择教材主题，再验证该主题中的数量关系。统计口径、样本范围、单位和实验条件属于模型本身。",
        color = InteractiveMuted,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    Spacer(Modifier.height(14.dp))
    VerificationChoiceGrid(
        items = biologyCategoryChoices.map { (id, label) -> VerificationChoiceItem(id.name, label) },
        selectedId = categoryName,
        columns = 3,
        onSelect = { categoryName = it },
    )
    Spacer(Modifier.height(16.dp))
    VerificationChoiceGrid(
        items = allowedRelations.map { VerificationChoiceItem(it.name, it.display, it.condition) },
        selectedId = relation.name,
        onSelect = { relationName = it },
    )
    Spacer(Modifier.height(20.dp))
    BiologyRelationDiagram(category, relation, values)
    Spacer(Modifier.height(18.dp))

    relation.variables.forEach { symbol ->
        VerificationNumberInput(
            label = biologyVariableLabel(symbol),
            value = valueTexts[symbol].orEmpty(),
            onValueChange = { valueTexts = valueTexts + (symbol to it) },
        )
        Spacer(Modifier.height(12.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.weight(1.25f)) {
            VerificationNumberInput(
                label = "待验证结果 ${relation.target}",
                value = targetText,
                unit = unit,
                onValueChange = { targetText = it },
            )
        }
        Box(Modifier.weight(0.75f)) {
            VerificationTextInput(
                label = "结果单位",
                value = unit,
                onValueChange = { unit = it },
                hint = defaultBiologyUnit(relation),
                maxLength = 16,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    VerificationToggle(
        label = "确认统计或实验条件",
        checked = conditionsAccepted,
        description = relation.condition,
        onToggle = { conditionsAccepted = !conditionsAccepted },
    )
    Spacer(Modifier.height(18.dp))

    val color = when (result.status) {
        BiologyVerificationStatus.CORRECT -> InteractiveGreen
        BiologyVerificationStatus.INCORRECT,
        BiologyVerificationStatus.INVALID_MODEL,
        BiologyVerificationStatus.NOT_ALLOWED,
        -> InteractiveRed
        BiologyVerificationStatus.MISSING_VALUES -> InteractiveYellow
    }
    VerificationStatusBlock(
        title = when (result.status) {
            BiologyVerificationStatus.CORRECT -> "数量关系一致"
            BiologyVerificationStatus.INCORRECT -> "结果不一致"
            BiologyVerificationStatus.MISSING_VALUES -> "等待完整数据"
            BiologyVerificationStatus.INVALID_MODEL -> "条件或数据无效"
            BiologyVerificationStatus.NOT_ALLOWED -> "当前主题不允许该关系"
        },
        normalized = relation.display,
        rows = buildList {
            result.expected?.let { add("关系计算值" to "${formatVerificationNumber(it)} ${result.unit}") }
            result.submitted?.let { add("输入结果" to "${formatVerificationNumber(it)} ${result.unit}") }
        },
        steps = result.steps,
        message = result.message,
        color = color,
    )

    if (category == BiologyCourseCategory.CELL) {
        Spacer(Modifier.height(30.dp))
        SectionTitle("结构标注验证", InteractiveBlue)
        Spacer(Modifier.height(12.dp))
        BiologyCellLabSample()
    }
}

@Composable
private fun PhysicsRelationDiagram(relation: PhysicsRelationId, values: Map<String, Double>) {
    Column(Modifier.fillMaxWidth()) {
        Text("模型可视化", color = InteractiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(230.dp).background(InteractivePanel.copy(alpha = 0.35f))) {
            when (relation) {
                PhysicsRelationId.SPEED,
                PhysicsRelationId.ACCELERATION,
                PhysicsRelationId.MOMENTUM,
                PhysicsRelationId.KINETIC_ENERGY,
                -> drawMotionDiagram(values)
                PhysicsRelationId.PRESSURE,
                PhysicsRelationId.NEWTON_SECOND,
                PhysicsRelationId.WORK,
                PhysicsRelationId.POWER,
                PhysicsRelationId.GRAVITATIONAL_POTENTIAL,
                -> drawForceDiagram(values)
                PhysicsRelationId.HEAT -> drawThermalDiagram(values)
                PhysicsRelationId.WAVE_SPEED,
                PhysicsRelationId.FREQUENCY_PERIOD,
                -> drawWaveDiagram(values)
                PhysicsRelationId.OHM,
                PhysicsRelationId.ELECTRIC_POWER,
                PhysicsRelationId.ELECTRIC_ENERGY,
                PhysicsRelationId.JOULE_HEAT,
                -> drawCircuitDiagram()
                PhysicsRelationId.LENS -> drawLensDiagram()
                PhysicsRelationId.DENSITY -> drawDensityDiagram(values)
            }
        }
    }
}

@Composable
private fun ChemistryResultDiagram(
    rows: List<Pair<String, String>>,
    status: ChemistryVerificationStatus,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("组成与守恒可视化", color = InteractiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(220.dp).background(InteractivePanel.copy(alpha = 0.35f))) {
            val elementRows = rows.filter { (label, _) -> label.matches(Regex("[A-Z][a-z]?")) }.take(6)
            if (elementRows.isEmpty()) {
                val count = rows.firstOrNull { it.first == "原子数" }?.second?.toIntOrNull()?.coerceIn(2, 16) ?: 6
                repeat(count) { index ->
                    val columns = 6
                    val x = size.width * (index % columns + 1f) / (columns + 1f)
                    val y = size.height * (index / columns + 1f) / 4f
                    drawCircle(if (index % 2 == 0) InteractiveBlue else InteractiveYellow, 13.dp.toPx(), Offset(x, y + size.height * 0.18f))
                    if (index > 0) {
                        val previousX = size.width * ((index - 1) % columns + 1f) / (columns + 1f)
                        val previousY = size.height * ((index - 1) / columns + 1f) / 4f + size.height * 0.18f
                        drawLine(InteractiveLine.copy(alpha = 0.9f), Offset(previousX, previousY), Offset(x, y + size.height * 0.18f), 2.dp.toPx())
                    }
                }
            } else {
                elementRows.forEachIndexed { rowIndex, (_, value) ->
                    val parts = value.split("→").mapNotNull { it.trim().toIntOrNull() }
                    val left = parts.getOrElse(0) { value.toIntOrNull() ?: 0 }.coerceIn(0, 12)
                    val right = parts.getOrElse(1) { left }.coerceIn(0, 12)
                    val y = size.height * (rowIndex + 1f) / (elementRows.size + 1f)
                    repeat(left) { index ->
                        drawCircle(InteractiveBlue, 5.dp.toPx(), Offset(size.width * 0.08f + index * 10.dp.toPx(), y))
                    }
                    repeat(right) { index ->
                        drawCircle(
                            if (status == ChemistryVerificationStatus.UNBALANCED && left != right) InteractiveRed else InteractiveGreen,
                            5.dp.toPx(),
                            Offset(size.width * 0.58f + index * 10.dp.toPx(), y),
                        )
                    }
                    drawLine(InteractiveLine, Offset(size.width * 0.45f, y), Offset(size.width * 0.53f, y), 2.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun BiologyRelationDiagram(
    category: BiologyCourseCategory,
    relation: BiologyRelationId,
    values: Map<String, Double>,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("生物模型可视化", color = InteractiveBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(220.dp).background(InteractivePanel.copy(alpha = 0.35f))) {
            when (category) {
                BiologyCourseCategory.CELL -> drawCellMagnification(values)
                BiologyCourseCategory.ECOLOGY -> drawEcologyDiagram(relation, values)
                BiologyCourseCategory.GENETICS,
                BiologyCourseCategory.REPRODUCTION,
                -> drawGeneticsDiagram(values)
                BiologyCourseCategory.HUMAN,
                BiologyCourseCategory.METABOLISM,
                BiologyCourseCategory.PLANT,
                -> drawBiologicalFlow(values)
                else -> drawBiologicalData(values)
            }
        }
    }
}

private fun DrawScope.drawMotionDiagram(values: Map<String, Double>) {
    val groundY = size.height * 0.72f
    drawLine(InteractiveLine.copy(alpha = 0.9f), Offset(16.dp.toPx(), groundY), Offset(size.width - 16.dp.toPx(), groundY), 2.dp.toPx())
    val speed = abs(values["v"] ?: values["s"] ?: 2.0).coerceIn(0.0, 10.0)
    val x = size.width * (0.18f + speed.toFloat() * 0.055f).coerceAtMost(0.78f)
    drawCircle(InteractiveYellow, 18.dp.toPx(), Offset(x, groundY - 18.dp.toPx()))
    val arrowEnd = Offset((x + (35 + speed * 5).dp.toPx()).coerceAtMost(size.width - 20.dp.toPx()), groundY - 55.dp.toPx())
    drawLine(InteractiveBlue, Offset(x, groundY - 55.dp.toPx()), arrowEnd, 4.dp.toPx(), StrokeCap.Round)
    drawLine(InteractiveBlue, arrowEnd, Offset(arrowEnd.x - 10.dp.toPx(), arrowEnd.y - 7.dp.toPx()), 3.dp.toPx())
    drawLine(InteractiveBlue, arrowEnd, Offset(arrowEnd.x - 10.dp.toPx(), arrowEnd.y + 7.dp.toPx()), 3.dp.toPx())
}

private fun DrawScope.drawForceDiagram(values: Map<String, Double>) {
    val blockSize = Size(size.width * 0.22f, size.height * 0.22f)
    val blockTop = Offset(size.width * 0.39f, size.height * 0.52f)
    drawRect(InteractivePurple.copy(alpha = 0.72f), blockTop, blockSize)
    val center = Offset(blockTop.x + blockSize.width / 2f, blockTop.y + blockSize.height / 2f)
    val force = abs(values["F"] ?: 4.0).coerceIn(0.5, 12.0)
    val end = Offset((center.x + (35 + force * 8).dp.toPx()).coerceAtMost(size.width - 18.dp.toPx()), center.y)
    drawLine(InteractiveYellow, center, end, 5.dp.toPx(), StrokeCap.Round)
    drawLine(InteractiveYellow, end, Offset(end.x - 12.dp.toPx(), end.y - 8.dp.toPx()), 3.dp.toPx())
    drawLine(InteractiveYellow, end, Offset(end.x - 12.dp.toPx(), end.y + 8.dp.toPx()), 3.dp.toPx())
    drawLine(InteractiveBlue, Offset(center.x, blockTop.y), Offset(center.x, size.height * 0.22f), 4.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.drawThermalDiagram(values: Map<String, Double>) {
    val temperature = abs(values["dT"] ?: 20.0).coerceIn(1.0, 100.0)
    repeat(20) { index ->
        val column = index % 5
        val row = index / 5
        val jitter = (temperature / 100.0 * ((index % 3) - 1) * 14).toFloat()
        val x = size.width * (column + 1f) / 6f + jitter
        val y = size.height * (row + 1f) / 5f - jitter * 0.4f
        drawCircle(if (index % 2 == 0) InteractiveRed else InteractiveYellow, 8.dp.toPx(), Offset(x, y))
    }
}

private fun DrawScope.drawWaveDiagram(values: Map<String, Double>) {
    val frequency = (values["f"] ?: 2.0).coerceIn(0.3, 8.0)
    val path = Path()
    repeat(180) { index ->
        val x = size.width * index / 179f
        val y = size.height / 2f + sin(index / 179.0 * PI * 2.0 * frequency).toFloat() * size.height * 0.28f
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, InteractiveBlue, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
    drawLine(InteractiveLine, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
}

private fun DrawScope.drawCircuitDiagram() {
    val left = size.width * 0.18f
    val right = size.width * 0.82f
    val top = size.height * 0.24f
    val bottom = size.height * 0.76f
    drawLine(InteractiveBlue, Offset(left, top), Offset(right, top), 4.dp.toPx())
    drawLine(InteractiveBlue, Offset(right, top), Offset(right, bottom), 4.dp.toPx())
    drawLine(InteractiveBlue, Offset(right, bottom), Offset(left, bottom), 4.dp.toPx())
    drawLine(InteractiveBlue, Offset(left, bottom), Offset(left, top), 4.dp.toPx())
    drawLine(InteractiveYellow, Offset(left - 10.dp.toPx(), size.height * 0.43f), Offset(left + 10.dp.toPx(), size.height * 0.43f), 5.dp.toPx())
    drawLine(InteractiveYellow, Offset(left - 18.dp.toPx(), size.height * 0.57f), Offset(left + 18.dp.toPx(), size.height * 0.57f), 5.dp.toPx())
    val resistor = Path().apply {
        moveTo(size.width * 0.36f, top)
        val segment = size.width * 0.035f
        repeat(8) { index -> lineTo(size.width * 0.36f + segment * (index + 1), top + if (index % 2 == 0) -12.dp.toPx() else 12.dp.toPx()) }
        lineTo(size.width * 0.68f, top)
    }
    drawPath(resistor, InteractiveRed, style = Stroke(3.dp.toPx()))
}

private fun DrawScope.drawLensDiagram() {
    val lensX = size.width * 0.52f
    drawLine(InteractiveBlue, Offset(lensX, size.height * 0.13f), Offset(lensX, size.height * 0.87f), 5.dp.toPx(), StrokeCap.Round)
    val objectTop = Offset(size.width * 0.16f, size.height * 0.25f)
    val objectBase = Offset(size.width * 0.16f, size.height * 0.72f)
    drawLine(InteractiveYellow, objectBase, objectTop, 5.dp.toPx())
    drawLine(InteractiveYellow, objectTop, Offset(objectTop.x - 8.dp.toPx(), objectTop.y + 12.dp.toPx()), 3.dp.toPx())
    drawLine(InteractiveYellow, objectTop, Offset(objectTop.x + 8.dp.toPx(), objectTop.y + 12.dp.toPx()), 3.dp.toPx())
    val image = Offset(size.width * 0.84f, size.height * 0.76f)
    drawLine(InteractivePurple, objectTop, Offset(lensX, objectTop.y), 2.dp.toPx())
    drawLine(InteractivePurple, Offset(lensX, objectTop.y), image, 2.dp.toPx())
    drawLine(InteractiveGreen, objectTop, image, 2.dp.toPx())
}

private fun DrawScope.drawDensityDiagram(values: Map<String, Double>) {
    val mass = abs(values["m"] ?: 2.0).coerceIn(0.1, 10.0)
    val volume = abs(values["V"] ?: 1.0).coerceIn(0.1, 10.0)
    val width = (size.width * (0.18 + volume / 20.0)).toFloat()
    val height = (size.height * (0.25 + mass / 25.0)).toFloat()
    drawRect(InteractiveBlue.copy(alpha = 0.56f), Offset(size.width / 2f - width / 2f, size.height / 2f - height / 2f), Size(width, height))
    repeat((mass * 2).toInt().coerceIn(2, 20)) { index ->
        val x = size.width / 2f - width * 0.38f + (index % 5) * width * 0.19f
        val y = size.height / 2f - height * 0.3f + (index / 5) * height * 0.2f
        drawCircle(InteractiveYellow, 5.dp.toPx(), Offset(x, y))
    }
}

private fun DrawScope.drawCellMagnification(values: Map<String, Double>) {
    val factor = ((values["image"] ?: 10.0) / (values["actual"] ?: 1.0).coerceAtLeast(0.1)).coerceIn(1.0, 20.0)
    val radius = (20 + factor * 3).dp.toPx().coerceAtMost(size.minDimension * 0.36f)
    val center = Offset(size.width / 2f, size.height / 2f)
    drawOval(InteractiveGreen.copy(alpha = 0.18f), Offset(center.x - radius * 1.25f, center.y - radius), Size(radius * 2.5f, radius * 2f), style = Stroke(4.dp.toPx()))
    drawCircle(InteractivePurple.copy(alpha = 0.7f), radius * 0.27f, center)
    repeat(7) { index ->
        val angle = index / 7.0 * PI * 2
        drawCircle(InteractiveGreen, 5.dp.toPx(), Offset(center.x + sin(angle).toFloat() * radius * 0.7f, center.y + kotlin.math.cos(angle).toFloat() * radius * 0.55f))
    }
}

private fun DrawScope.drawEcologyDiagram(relation: BiologyRelationId, values: Map<String, Double>) {
    val levels = if (relation == BiologyRelationId.ENERGY_EFFICIENCY) 4 else 3
    repeat(levels) { index ->
        val ratio = 1f - index * 0.19f
        val width = size.width * ratio * 0.72f
        val height = size.height * 0.16f
        val top = size.height * (0.72f - index * 0.19f)
        drawRect(
            color = listOf(InteractiveGreen, InteractiveBlue, InteractiveYellow, InteractiveRed)[index],
            topLeft = Offset(size.width / 2f - width / 2f, top),
            size = Size(width, height),
        )
    }
    val density = values.values.firstOrNull()?.coerceIn(0.0, 20.0) ?: 5.0
    repeat(density.toInt()) { index ->
        drawCircle(InteractiveWhite.copy(alpha = 0.7f), 3.dp.toPx(), Offset(size.width * (index + 1f) / (density.toInt() + 1f), size.height * 0.18f))
    }
}

private fun DrawScope.drawGeneticsDiagram(values: Map<String, Double>) {
    val left = size.width * 0.18f
    val top = size.height * 0.18f
    val cellWidth = size.width * 0.25f
    val cellHeight = size.height * 0.25f
    repeat(2) { row ->
        repeat(2) { column ->
            drawRect(
                color = if ((row + column) % 2 == 0) InteractivePurple.copy(alpha = 0.5f) else InteractiveYellow.copy(alpha = 0.5f),
                topLeft = Offset(left + column * cellWidth, top + row * cellHeight),
                size = Size(cellWidth, cellHeight),
                style = Stroke(2.dp.toPx()),
            )
            drawCircle(
                if ((row + column) % 2 == 0) InteractivePurple else InteractiveYellow,
                9.dp.toPx(),
                Offset(left + column * cellWidth + cellWidth / 2f, top + row * cellHeight + cellHeight / 2f),
            )
        }
    }
    val favorable = values["favorable"] ?: 1.0
    val total = values["total"] ?: 4.0
    val fraction = (favorable / total.coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
    drawRect(InteractiveLine, Offset(left, size.height * 0.82f), Size(cellWidth * 2, 8.dp.toPx()))
    drawRect(InteractiveGreen, Offset(left, size.height * 0.82f), Size(cellWidth * 2 * fraction, 8.dp.toPx()))
}

private fun DrawScope.drawBiologicalFlow(values: Map<String, Double>) {
    val nodes = 4
    repeat(nodes) { index ->
        val x = size.width * (index + 1f) / (nodes + 1f)
        val y = size.height * (0.42f + if (index % 2 == 0) -0.08f else 0.08f)
        if (index > 0) {
            val previousX = size.width * index / (nodes + 1f)
            val previousY = size.height * (0.42f + if ((index - 1) % 2 == 0) -0.08f else 0.08f)
            drawLine(InteractiveBlue, Offset(previousX, previousY), Offset(x, y), 4.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(listOf(InteractiveGreen, InteractiveBlue, InteractiveYellow, InteractivePurple)[index], 15.dp.toPx(), Offset(x, y))
    }
    val scale = values.values.firstOrNull()?.let(::abs)?.coerceIn(0.0, 20.0)?.toFloat() ?: 5f
    drawRect(InteractiveGreen.copy(alpha = 0.4f), Offset(size.width * 0.18f, size.height * 0.76f), Size(size.width * 0.64f * scale / 20f, 10.dp.toPx()))
}

private fun DrawScope.drawBiologicalData(values: Map<String, Double>) {
    val entries = values.values.ifEmpty { listOf(1.0, 2.0, 3.0) }.take(6)
    val max = entries.maxOf { abs(it) }.coerceAtLeast(1.0)
    entries.forEachIndexed { index, value ->
        val slot = size.width / entries.size
        val height = (abs(value) / max).toFloat() * size.height * 0.68f
        drawRect(
            color = if (index % 2 == 0) InteractiveGreen else InteractiveBlue,
            topLeft = Offset(slot * index + slot * 0.2f, size.height * 0.82f - height),
            size = Size(slot * 0.6f, height),
        )
    }
}

private fun chemistryModeLabel(mode: ChemistryVerificationMode): String = when (mode) {
    ChemistryVerificationMode.FORMULA -> "化学式"
    ChemistryVerificationMode.EQUATION_CHECK -> "守恒检查"
    ChemistryVerificationMode.EQUATION_BALANCE -> "自动配平"
    ChemistryVerificationMode.ORGANIC_STRUCTURE -> "有机结构"
    ChemistryVerificationMode.ORGANIC_ISOMER -> "构造异构"
}

private fun chemistryModeSubtitle(mode: ChemistryVerificationMode): String = when (mode) {
    ChemistryVerificationMode.FORMULA -> "组成 · 电荷 · 式量"
    ChemistryVerificationMode.EQUATION_CHECK -> "元素与电荷"
    ChemistryVerificationMode.EQUATION_BALANCE -> "最简整数比"
    ChemistryVerificationMode.ORGANIC_STRUCTURE -> "分子图 · 官能团"
    ChemistryVerificationMode.ORGANIC_ISOMER -> "分子式与连接"
}

private fun chemistryPrimaryLabel(mode: ChemistryVerificationMode): String = when (mode) {
    ChemistryVerificationMode.FORMULA -> "化学式"
    ChemistryVerificationMode.EQUATION_CHECK,
    ChemistryVerificationMode.EQUATION_BALANCE,
    -> "完整化学方程式"
    ChemistryVerificationMode.ORGANIC_STRUCTURE -> "有机结构式技术输入"
    ChemistryVerificationMode.ORGANIC_ISOMER -> "第一个有机结构"
}

private fun chemistryExample(mode: ChemistryVerificationMode): Pair<String, String> = when (mode) {
    ChemistryVerificationMode.FORMULA -> "Al2(SO4)3" to ""
    ChemistryVerificationMode.EQUATION_CHECK -> "2H2+O2→2H2O" to ""
    ChemistryVerificationMode.EQUATION_BALANCE -> "Fe+O2→Fe2O3" to ""
    ChemistryVerificationMode.ORGANIC_STRUCTURE -> "CCO" to ""
    ChemistryVerificationMode.ORGANIC_ISOMER -> "CCO" to "COC"
}

private fun defaultPhysicsValue(relation: PhysicsRelationId, symbol: String): String = when (relation) {
    PhysicsRelationId.SPEED -> if (symbol == "s") "100" else "20"
    PhysicsRelationId.ACCELERATION -> when (symbol) { "v" -> "10"; "v0" -> "2"; else -> "4" }
    PhysicsRelationId.DENSITY -> if (symbol == "m") "2" else "0.001"
    PhysicsRelationId.PRESSURE -> if (symbol == "F") "100" else "0.5"
    PhysicsRelationId.NEWTON_SECOND -> if (symbol == "m") "2" else "3"
    PhysicsRelationId.WORK -> if (symbol == "F") "10" else "5"
    PhysicsRelationId.POWER -> if (symbol == "W") "100" else "20"
    PhysicsRelationId.KINETIC_ENERGY -> if (symbol == "m") "2" else "3"
    PhysicsRelationId.GRAVITATIONAL_POTENTIAL -> when (symbol) { "m" -> "2"; "g" -> "9.8"; else -> "5" }
    PhysicsRelationId.MOMENTUM -> if (symbol == "m") "2" else "3"
    PhysicsRelationId.HEAT -> when (symbol) { "c" -> "4200"; "m" -> "1"; else -> "10" }
    PhysicsRelationId.WAVE_SPEED -> if (symbol == "f") "2" else "3"
    PhysicsRelationId.OHM -> if (symbol == "I") "2" else "3"
    PhysicsRelationId.ELECTRIC_POWER -> if (symbol == "U") "6" else "2"
    PhysicsRelationId.ELECTRIC_ENERGY -> if (symbol == "P") "12" else "10"
    PhysicsRelationId.JOULE_HEAT -> when (symbol) { "I" -> "2"; "R" -> "3"; else -> "10" }
    PhysicsRelationId.FREQUENCY_PERIOD -> "0.5"
    PhysicsRelationId.LENS -> if (symbol == "u") "30" else "60"
}

private fun defaultPhysicsTarget(relation: PhysicsRelationId): String = runCatching {
    formatVerificationNumber(
        PhysicsRelationVerifier.calculate(
            relation,
            relation.variables.associateWith { defaultPhysicsValue(relation, it).toDouble() },
        ),
    )
}.getOrDefault("1")

private fun defaultPhysicsUnit(symbol: String): String = when (symbol) {
    "s", "h", "u", "v" -> "m"
    "t", "T" -> "s"
    "m" -> "kg"
    "V" -> "m³"
    "S" -> "m²"
    "F" -> "N"
    "W", "Ek", "Ep", "Q" -> "J"
    "P" -> "W"
    "a" -> "m/s²"
    "v0" -> "m/s"
    "rho" -> "kg/m³"
    "p" -> "Pa"
    "g" -> "m/s²"
    "c" -> "J/(kg·°C)"
    "dT" -> "°C"
    "f" -> "Hz"
    "lambda" -> "m"
    "U" -> "V"
    "I" -> "A"
    "R" -> "Ω"
    else -> ""
}

private fun defaultBiologyValue(relation: BiologyRelationId, symbol: String): String = when (relation) {
    BiologyRelationId.MAGNIFICATION -> if (symbol == "image") "10" else "0.1"
    BiologyRelationId.POPULATION_DENSITY -> if (symbol == "count") "100" else "20"
    BiologyRelationId.GROWTH_RATE -> if (symbol == "final") "120" else "100"
    BiologyRelationId.ENERGY_EFFICIENCY -> if (symbol == "next") "120" else "1000"
    BiologyRelationId.CARDIAC_OUTPUT -> if (symbol == "heartRate") "70" else "70"
    BiologyRelationId.RESPIRATION_RATE -> if (symbol == "gas") "20" else "5"
    BiologyRelationId.PHOTOSYNTHESIS_RATE -> if (symbol == "product") "30" else "5"
    BiologyRelationId.GENETIC_PROBABILITY -> if (symbol == "favorable") "1" else "4"
    BiologyRelationId.SURVIVAL_RATE -> if (symbol == "survived") "90" else "100"
}

private fun defaultBiologyTarget(relation: BiologyRelationId): String = runCatching {
    formatVerificationNumber(
        BiologyRelationVerifier.calculate(
            relation,
            relation.variables.associateWith { defaultBiologyValue(relation, it).toDouble() },
        ),
    )
}.getOrDefault("1")

private fun defaultBiologyUnit(relation: BiologyRelationId): String = when (relation) {
    BiologyRelationId.MAGNIFICATION -> "倍"
    BiologyRelationId.POPULATION_DENSITY -> "个/面积"
    BiologyRelationId.GROWTH_RATE,
    BiologyRelationId.ENERGY_EFFICIENCY,
    BiologyRelationId.SURVIVAL_RATE,
    -> "%"
    BiologyRelationId.CARDIAC_OUTPUT -> "mL/min"
    BiologyRelationId.RESPIRATION_RATE,
    BiologyRelationId.PHOTOSYNTHESIS_RATE,
    -> "变化量/时间"
    BiologyRelationId.GENETIC_PROBABILITY -> "概率"
}

private fun biologyVariableLabel(symbol: String): String = when (symbol) {
    "image" -> "图像大小"
    "actual" -> "实际大小"
    "count" -> "个体数"
    "area" -> "调查面积"
    "final" -> "末数量"
    "initial" -> "初数量"
    "next" -> "下一营养级能量"
    "previous" -> "上一营养级能量"
    "heartRate" -> "心率"
    "strokeVolume" -> "每搏输出量"
    "gas" -> "气体变化量"
    "time" -> "时间"
    "product" -> "产物或气体变化量"
    "favorable" -> "符合条件组合数"
    "total" -> "全部组合或总个体数"
    "survived" -> "成活个体数"
    else -> symbol
}
