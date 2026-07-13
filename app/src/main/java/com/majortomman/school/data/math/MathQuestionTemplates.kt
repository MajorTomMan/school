package com.majortomman.school.data.math

import com.majortomman.school.data.material.InstalledTextbook
import kotlin.math.absoluteValue
import kotlin.random.Random

data class MathSourceContext(
    val lessonId: String?,
    val sourcePage: Int?,
    val excerpt: String?,
)

object MathKnowledgeCatalog {
    val all = listOf(
        MathKnowledgePoint(
            id = "positive-negative",
            title = "正数和负数",
            description = "判断符号，并用正负数表示相反意义的量。",
            lessonKeywords = listOf("正数", "负数", "相反意义", "温度", "海拔"),
        ),
        MathKnowledgePoint(
            id = "number-line",
            title = "数轴",
            description = "理解原点、正方向和单位长度，并在数轴上定位数。",
            lessonKeywords = listOf("数轴", "原点", "单位长度", "坐标"),
        ),
        MathKnowledgePoint(
            id = "opposite-number",
            title = "相反数",
            description = "理解关于原点对称的位置关系。",
            lessonKeywords = listOf("相反数", "相反", "原点对称"),
        ),
        MathKnowledgePoint(
            id = "absolute-value",
            title = "绝对值",
            description = "把绝对值理解为数到原点的距离。",
            lessonKeywords = listOf("绝对值", "距离"),
        ),
        MathKnowledgePoint(
            id = "rational-compare",
            title = "有理数大小比较",
            description = "借助数轴、符号和绝对值比较有理数。",
            lessonKeywords = listOf("大小比较", "比较", "排序", "大于", "小于"),
        ),
        MathKnowledgePoint(
            id = "expression-equivalence",
            title = "整式与等价变形",
            description = "使用去括号、分配律和合并同类项进行等价变形。",
            lessonKeywords = listOf("整式", "代数式", "合并同类项", "去括号", "分配律"),
        ),
        MathKnowledgePoint(
            id = "linear-equation",
            title = "一元一次方程",
            description = "保持等式两边平衡，逐步求出未知数。",
            lessonKeywords = listOf("一元一次方程", "方程", "移项", "解方程"),
        ),
    )

    fun find(id: String): MathKnowledgePoint = all.firstOrNull { it.id == id } ?: all.first()

    fun infer(text: String): MathKnowledgePoint? {
        val normalized = text.lowercase()
        return all
            .map { point -> point to point.lessonKeywords.count { keyword -> keyword.lowercase() in normalized } }
            .filter { (_, score) -> score > 0 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    fun forTextbook(textbook: InstalledTextbook): List<MathKnowledgePoint> {
        val inferred = textbook.lessons.mapNotNull { lesson -> infer(lesson.title) }.distinctBy { it.id }
        return inferred.ifEmpty { all.take(5) }
    }

    fun lessonIdFor(textbook: InstalledTextbook, knowledgePointId: String): String? {
        val point = find(knowledgePointId)
        return textbook.lessons.firstOrNull { lesson ->
            point.lessonKeywords.any { keyword -> keyword in lesson.title }
        }?.id
    }
}

object MathQuestionTemplateCatalog {
    val templateIds = listOf(
        "sign-classification",
        "number-line-locate",
        "opposite-value",
        "absolute-value-basic",
        "absolute-value-equation",
        "rational-compare-choice",
        "rational-ordering",
        "expression-expand",
        "linear-equation-solve",
        "linear-equation-steps",
    )

    fun templatesFor(knowledgePointId: String): List<String> = when (knowledgePointId) {
        "positive-negative" -> listOf("sign-classification")
        "number-line" -> listOf("number-line-locate", "rational-compare-choice")
        "opposite-number" -> listOf("opposite-value", "number-line-locate")
        "absolute-value" -> listOf("absolute-value-basic", "absolute-value-equation")
        "rational-compare" -> listOf("rational-compare-choice", "rational-ordering")
        "expression-equivalence" -> listOf("expression-expand")
        "linear-equation" -> listOf("linear-equation-solve", "linear-equation-steps")
        else -> listOf("sign-classification", "number-line-locate", "opposite-value", "absolute-value-basic", "rational-compare-choice")
    }

    fun generate(
        textbookKey: String,
        lessonId: String?,
        knowledgePointId: String,
        difficulty: MathDifficulty,
        seed: Long,
        source: MathQuestionSource = MathQuestionSource.SYSTEM_TEMPLATE,
        sourceContext: MathSourceContext? = null,
        excludedTemplateIds: Set<String> = emptySet(),
    ): MathQuestion {
        val candidates = templatesFor(knowledgePointId)
        val available = candidates.filterNot { it in excludedTemplateIds }.ifEmpty { candidates }
        val random = Random(seed)
        val templateId = available[random.nextInt(available.size)]
        val effectiveLessonId = sourceContext?.lessonId ?: lessonId
        return when (templateId) {
            "sign-classification" -> signClassification(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext)
            "number-line-locate" -> numberLineLocate(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext)
            "opposite-value" -> oppositeValue(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext)
            "absolute-value-basic" -> absoluteValueBasic(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext)
            "absolute-value-equation" -> absoluteValueEquation(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext)
            "rational-ordering" -> rationalOrdering(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext)
            "expression-expand" -> expressionExpand(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext)
            "linear-equation-solve" -> linearEquationSolve(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext, steps = false)
            "linear-equation-steps" -> linearEquationSolve(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext, steps = true)
            else -> rationalCompare(textbookKey, effectiveLessonId, difficulty, random, source, sourceContext)
        }
    }

    private fun signClassification(
        textbookKey: String,
        lessonId: String?,
        difficulty: MathDifficulty,
        random: Random,
        source: MathQuestionSource,
        context: MathSourceContext?,
    ): MathQuestion {
        val value = when (random.nextInt(4)) {
            0 -> random.nextInt(1, 20)
            1 -> -random.nextInt(1, 20)
            2 -> 0
            else -> if (difficulty.level >= 2) -random.nextInt(1, 10) else random.nextInt(1, 10)
        }
        val correct = when {
            value > 0 -> "positive"
            value < 0 -> "negative"
            else -> "zero"
        }
        return base(
            textbookKey = textbookKey,
            lessonId = lessonId,
            templateId = "sign-classification",
            knowledgePointId = "positive-negative",
            type = MathQuestionType.SINGLE_CHOICE,
            difficulty = difficulty,
            source = source,
            parameters = value.toString(),
            prompt = "数 $value 属于哪一类？",
            answerSpec = MathAnswerSpec.Choice(correct),
            canonicalAnswer = when (correct) {
                "positive" -> "正数"
                "negative" -> "负数"
                else -> "零"
            },
            options = listOf(
                MathQuestionOption("positive", "正数"),
                MathQuestionOption("negative", "负数"),
                MathQuestionOption("zero", "零"),
            ),
            hints = listOf("先看这个数与 0 的大小关系。", "正数大于 0，负数小于 0。"),
            explanation = "正负号描述数在 0 的哪一侧，0 既不是正数也不是负数。",
            context = context,
        )
    }

    private fun numberLineLocate(
        textbookKey: String,
        lessonId: String?,
        difficulty: MathDifficulty,
        random: Random,
        source: MathQuestionSource,
        context: MathSourceContext?,
    ): MathQuestion {
        val max = if (difficulty.level >= 3) 12 else 8
        val value = randomNonZero(random, -max, max)
        return base(
            textbookKey = textbookKey,
            lessonId = lessonId,
            templateId = "number-line-locate",
            knowledgePointId = "number-line",
            type = MathQuestionType.NUMBER_LINE_POINT,
            difficulty = difficulty,
            source = source,
            parameters = value.toString(),
            prompt = "在数轴上标出 $value。",
            answerSpec = MathAnswerSpec.NumberLineValue(value.toString()),
            canonicalAnswer = value.toString(),
            hints = listOf("先找到原点 0。", "正数向右，负数向左。", "每一格必须保持相同的单位长度。"),
            explanation = "$value 位于原点的${if (value > 0) "右" else "左"}侧，距离原点 ${value.absoluteValue} 个单位长度。",
            numberLineMin = minOf(-10, value - 2),
            numberLineMax = maxOf(10, value + 2),
            context = context,
        )
    }

    private fun oppositeValue(
        textbookKey: String,
        lessonId: String?,
        difficulty: MathDifficulty,
        random: Random,
        source: MathQuestionSource,
        context: MathSourceContext?,
    ): MathQuestion {
        val max = if (difficulty.level >= 3) 30 else 12
        val value = randomNonZero(random, -max, max)
        val answer = -value
        return base(
            textbookKey = textbookKey,
            lessonId = lessonId,
            templateId = "opposite-value",
            knowledgePointId = "opposite-number",
            type = MathQuestionType.NUMERIC_INPUT,
            difficulty = difficulty,
            source = source,
            parameters = value.toString(),
            prompt = "写出 $value 的相反数。",
            answerSpec = MathAnswerSpec.RationalValue(answer.toString()),
            canonicalAnswer = answer.toString(),
            hints = listOf("相反数到原点的距离相同。", "它们分别位于原点两侧。"),
            explanation = "$value 与 $answer 的和是 0，所以它们互为相反数。",
            context = context,
        )
    }

    private fun absoluteValueBasic(
        textbookKey: String,
        lessonId: String?,
        difficulty: MathDifficulty,
        random: Random,
        source: MathQuestionSource,
        context: MathSourceContext?,
    ): MathQuestion {
        val max = if (difficulty.level >= 3) 50 else 15
        val value = randomNonZero(random, -max, max)
        val answer = value.absoluteValue
        return base(
            textbookKey = textbookKey,
            lessonId = lessonId,
            templateId = "absolute-value-basic",
            knowledgePointId = "absolute-value",
            type = MathQuestionType.NUMERIC_INPUT,
            difficulty = difficulty,
            source = source,
            parameters = value.toString(),
            prompt = "计算 |$value|。",
            answerSpec = MathAnswerSpec.RationalValue(answer.toString()),
            canonicalAnswer = answer.toString(),
            hints = listOf("绝对值表示到原点的距离。", "距离不会是负数。"),
            explanation = "$value 到原点的距离是 $answer，所以 |$value|=$answer。",
            context = context,
        )
    }

    private fun absoluteValueEquation(
        textbookKey: String,
        lessonId: String?,
        difficulty: MathDifficulty,
        random: Random,
        source: MathQuestionSource,
        context: MathSourceContext?,
    ): MathQuestion {
        val distance = random.nextInt(2, if (difficulty.level >= 3) 25 else 12)
        return base(
            textbookKey = textbookKey,
            lessonId = lessonId,
            templateId = "absolute-value-equation",
            knowledgePointId = "absolute-value",
            type = MathQuestionType.EXPRESSION_INPUT,
            difficulty = difficulty,
            source = source,
            parameters = distance.toString(),
            prompt = "若 |x|=$distance，写出所有可能的 x。",
            answerSpec = MathAnswerSpec.RationalSet(listOf(distance.toString(), (-distance).toString())),
            canonicalAnswer = "x=$distance 或 x=-$distance",
            hints = listOf("把它理解成点到原点的距离。", "原点左右两侧各有一个点满足条件。"),
            explanation = "到原点距离为 $distance 的点有两个，因此 x=$distance 或 x=-$distance。",
            context = context,
        )
    }

    private fun rationalCompare(
        textbookKey: String,
        lessonId: String?,
        difficulty: MathDifficulty,
        random: Random,
        source: MathQuestionSource,
        context: MathSourceContext?,
    ): MathQuestion {
        var left = random.nextInt(-12, 13)
        var right = random.nextInt(-12, 13)
        while (left == right) right = random.nextInt(-12, 13)
        val correct = if (left > right) "left" else "right"
        val relation = if (left > right) "$left > $right" else "$left < $right"
        return base(
            textbookKey = textbookKey,
            lessonId = lessonId,
            templateId = "rational-compare-choice",
            knowledgePointId = "rational-compare",
            type = MathQuestionType.SINGLE_CHOICE,
            difficulty = difficulty,
            source = source,
            parameters = "$left,$right",
            prompt = "比较 $left 与 $right，选择较大的数。",
            answerSpec = MathAnswerSpec.Choice(correct),
            canonicalAnswer = if (correct == "left") left.toString() else right.toString(),
            options = listOf(
                MathQuestionOption("left", left.toString()),
                MathQuestionOption("right", right.toString()),
            ),
            hints = listOf("把两个数放在同一条数轴上。", "数轴上越靠右的数越大。"),
            explanation = "$relation，因为数轴上越靠右的数越大。",
            context = context,
        )
    }

    private fun rationalOrdering(
        textbookKey: String,
        lessonId: String?,
        difficulty: MathDifficulty,
        random: Random,
        source: MathQuestionSource,
        context: MathSourceContext?,
    ): MathQuestion {
        val count = if (difficulty.level >= 3) 5 else 4
        val values = buildSet {
            while (size < count) add(random.nextInt(-12, 13))
        }.toList()
        val sorted = values.sorted()
        val shuffled = values.shuffled(random)
        return base(
            textbookKey = textbookKey,
            lessonId = lessonId,
            templateId = "rational-ordering",
            knowledgePointId = "rational-compare",
            type = MathQuestionType.ORDERING,
            difficulty = difficulty,
            source = source,
            parameters = values.joinToString(","),
            prompt = "将这些数按从小到大排列。",
            answerSpec = MathAnswerSpec.Ordering(sorted.map(Int::toString)),
            canonicalAnswer = sorted.joinToString(" < "),
            orderingItems = shuffled.map(Int::toString),
            hints = listOf("先找出所有负数。", "负数中离原点越远，数反而越小。", "最后再放 0 和正数。"),
            explanation = "按数轴从左到右排列得到 ${sorted.joinToString(" < ")}。",
            context = context,
        )
    }

    private fun expressionExpand(
        textbookKey: String,
        lessonId: String?,
        difficulty: MathDifficulty,
        random: Random,
        source: MathQuestionSource,
        context: MathSourceContext?,
    ): MathQuestion {
        val a = randomNonZero(random, -6, 7)
        val b = random.nextInt(-8, 9)
        val c = if (difficulty.level >= 3) random.nextInt(-6, 7) else 0
        val original = if (c == 0) "$a*(x${signed(b)})" else "$a*(x${signed(b)})${signed(c)}"
        val constant = a * b + c
        val expanded = "${coefficientText(a)}x${signed(constant)}"
        return base(
            textbookKey = textbookKey,
            lessonId = lessonId,
            templateId = "expression-expand",
            knowledgePointId = "expression-equivalence",
            type = MathQuestionType.EXPRESSION_INPUT,
            difficulty = difficulty,
            source = source,
            parameters = "$a,$b,$c",
            prompt = "化简：$original",
            answerSpec = MathAnswerSpec.EquivalentExpression(expanded),
            canonicalAnswer = expanded,
            hints = listOf("先用分配律去括号。", "括号外的系数要乘括号内每一项。", "最后合并常数项。"),
            explanation = "$original=$expanded。",
            context = context,
        )
    }

    private fun linearEquationSolve(
        textbookKey: String,
        lessonId: String?,
        difficulty: MathDifficulty,
        random: Random,
        source: MathQuestionSource,
        context: MathSourceContext?,
        steps: Boolean,
    ): MathQuestion {
        val solution = randomNonZero(random, -9, 10)
        val coefficientValue = randomNonZero(random, -6, 7)
        val constant = random.nextInt(-10, 11)
        val right = coefficientValue * solution + constant
        val equation = "${coefficientText(coefficientValue)}x${signed(constant)}=$right"
        return base(
            textbookKey = textbookKey,
            lessonId = lessonId,
            templateId = if (steps) "linear-equation-steps" else "linear-equation-solve",
            knowledgePointId = "linear-equation",
            type = if (steps) MathQuestionType.STEP_BY_STEP else MathQuestionType.NUMERIC_INPUT,
            difficulty = difficulty,
            source = source,
            parameters = "$coefficientValue,$constant,$right",
            prompt = if (steps) "逐步解方程，每一步单独写一行：$equation" else "解方程：$equation",
            answerSpec = if (steps) {
                MathAnswerSpec.StepSequence(equation, solution.toString())
            } else {
                MathAnswerSpec.LinearEquation(equation, solution.toString())
            },
            canonicalAnswer = "x=$solution",
            hints = listOf("先消去常数项。", "等式两边必须进行相同的操作。", "最后同除以 x 的系数。"),
            explanation = "保持等式两边平衡，最终得到 x=$solution。",
            context = context,
        )
    }

    private fun base(
        textbookKey: String,
        lessonId: String?,
        templateId: String,
        knowledgePointId: String,
        type: MathQuestionType,
        difficulty: MathDifficulty,
        source: MathQuestionSource,
        parameters: String,
        prompt: String,
        answerSpec: MathAnswerSpec,
        canonicalAnswer: String,
        options: List<MathQuestionOption> = emptyList(),
        orderingItems: List<String> = emptyList(),
        hints: List<String>,
        explanation: String,
        numberLineMin: Int = -10,
        numberLineMax: Int = 10,
        context: MathSourceContext?,
    ): MathQuestion {
        val stableParameters = parameters.replace(" ", "")
        val questionId = "$textbookKey:$templateId:$stableParameters"
        return MathQuestion(
            id = questionId,
            templateId = templateId,
            textbookKey = textbookKey,
            lessonId = lessonId,
            knowledgePointId = knowledgePointId,
            type = type,
            difficulty = difficulty,
            source = source,
            prompt = prompt,
            answerSpec = answerSpec,
            canonicalAnswer = canonicalAnswer,
            options = options,
            orderingItems = orderingItems,
            hints = hints,
            explanation = explanation,
            numberLineMin = numberLineMin,
            numberLineMax = numberLineMax,
            sourcePage = context?.sourcePage,
            sourceExcerpt = context?.excerpt,
        )
    }

    private fun randomNonZero(random: Random, from: Int, until: Int): Int {
        var value = random.nextInt(from, until)
        while (value == 0) value = random.nextInt(from, until)
        return value
    }

    private fun signed(value: Int): String = when {
        value > 0 -> "+$value"
        value < 0 -> value.toString()
        else -> ""
    }

    private fun coefficientText(value: Int): String = when (value) {
        1 -> ""
        -1 -> "-"
        else -> value.toString()
    }
}
