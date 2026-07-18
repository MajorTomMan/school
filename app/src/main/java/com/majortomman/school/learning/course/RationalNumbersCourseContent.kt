package com.majortomman.school.learning.course

enum class RationalVisualizationKind {
    OPPOSITE_QUANTITIES,
    RATIONAL_CLASSIFICATION,
    NUMBER_LINE,
    OPPOSITE_NUMBERS,
    ABSOLUTE_VALUE,
    NUMBER_COMPARISON,
    ADDITION_PROCESS,
    SUBTRACTION_TRANSFORM,
    MULTIPLICATION_SIGN,
    DIVISION_TRANSFORM,
    POWER_PROCESS,
    HISTORY,
}

data class RationalLessonPage(
    val id: String,
    val section: String,
    val title: String,
    val paragraphs: List<String>,
    val sourcePage: Int,
    val visualization: RationalVisualizationKind,
    val formula: String? = null,
    val conclusion: String? = null,
)

object RationalNumbersCourseFactory {
    fun supports(title: String): Boolean {
        val normalized = normalize(title)
        return supportedKeywords.any(normalized::contains) ||
            normalized in setOf(
                "有理数",
                "第一章有理数",
                "有理数的概念",
                "有理数概念",
                "有理数的运算",
                "第二章有理数的运算",
                "第一章小结",
                "第一章复习题",
                "第二章小结",
                "第二章复习题",
            )
    }

    fun pagesFor(title: String, sourcePages: IntRange): List<RationalLessonPage> {
        require(!sourcePages.isEmpty()) { "教材页码范围不能为空" }
        val normalized = normalize(title)
        return when {
            normalized.contains("第一章小结") -> chapterOneSummaryPages(sourcePages)
            normalized.contains("第一章复习") -> chapterOneExercisePages(sourcePages)
            normalized.contains("第二章小结") -> chapterTwoSummaryPages(sourcePages)
            normalized.contains("第二章复习") -> chapterTwoExercisePages(sourcePages)
            normalized.contains("允许偏差") -> allowedDeviationPages(sourcePages)
            normalized.contains("漫漫长路识负数") -> negativeNumberHistoryPages(sourcePages)
            normalized.contains("正负术") -> signedArithmeticHistoryPages(sourcePages)
            normalized.contains("数系扩充看乘法法则") -> numberSystemExtensionPages(sourcePages)
            normalized.contains("有理数的加法与减法") -> additionAndSubtractionPages(sourcePages)
            normalized.contains("有理数的乘法与除法") -> multiplicationAndDivisionPages(sourcePages)
            normalized.contains("有理数的乘方") -> powerPages(sourcePages)
            normalized == "有理数的运算" || normalized == "第二章有理数的运算" ->
                operationChapterPages(sourcePages)
            normalized.contains("正数和负数") -> positiveAndNegativePages(sourcePages)
            normalized.contains("数轴") -> numberLinePages(sourcePages)
            normalized.contains("相反数") -> oppositeNumberPages(sourcePages)
            normalized.contains("绝对值") -> absoluteValuePages(sourcePages)
            normalized.contains("有理数及其大小比较") -> rationalNumberAndComparisonPages(sourcePages)
            normalized == "有理数的概念" || normalized == "有理数概念" ->
                rationalConceptPages(sourcePages)
            normalized == "有理数" || normalized == "第一章有理数" ->
                rationalNumberChapterPages(sourcePages)
            else -> emptyList()
        }
    }

    private fun positiveAndNegativePages(source: IntRange) = listOf(
        page(
            "opposite-quantities",
            "1.1 正数和负数",
            "具有相反意义的量",
            source,
            0,
            RationalVisualizationKind.OPPOSITE_QUANTITIES,
            listOf(
                "在同一问题中，零上与零下、增加与减少、收入与支出等，分别表示意义相反的两个量。",
                "表示这类量时，应先规定其中一种意义为正，另一种意义为负；同一问题中的规定必须保持一致。",
            ),
            conclusion = "正数和负数可以表示具有相反意义的量。",
        ),
        page(
            "positive-negative-definition",
            "1.1 正数和负数",
            "正数和负数",
            source,
            1,
            RationalVisualizationKind.OPPOSITE_QUANTITIES,
            listOf(
                "像 3，50，7.8% 这样大于 0 的数叫作正数。",
                "像 −3，−10，−0.7% 这样在正数前加上负号的数叫作负数。0 既不是正数，也不是负数。",
            ),
            formula = "负数 < 0 < 正数",
            conclusion = "0 是正数与负数的分界。",
        ),
        page(
            "zero-as-reference",
            "1.1 正数和负数",
            "0 作为基准",
            source,
            2,
            RationalVisualizationKind.OPPOSITE_QUANTITIES,
            listOf(
                "0 不仅可以表示“没有”，还可以作为确定正、负的基准。",
                "海拔、温度、收支和变化率等问题中，必须先明确基准以及正方向的含义。",
            ),
            conclusion = "数的符号必须联系具体问题中的基准理解。",
        ),
    )

    private fun allowedDeviationPages(source: IntRange) = listOf(
        page(
            "allowed-deviation",
            "阅读与思考",
            "用正负数表示允许偏差",
            source,
            0,
            RationalVisualizationKind.OPPOSITE_QUANTITIES,
            listOf(
                "以规定的标准值为基准，超过标准值的部分记为正，低于标准值的部分记为负。",
                "偏差的符号表示方向，偏差的绝对值表示与标准值相差的大小。",
            ),
            formula = "实际值 = 标准值 + 偏差",
            conclusion = "判断实际值时，应把标准值与偏差合并考虑。",
        ),
    )

    private fun rationalConceptPages(source: IntRange) =
        rationalNumberAndComparisonPages(source).take(2)

    private fun rationalNumberAndComparisonPages(source: IntRange) = listOf(
        page(
            "rational-definition",
            "1.2 有理数及其大小比较",
            "有理数",
            source,
            0,
            RationalVisualizationKind.RATIONAL_CLASSIFICATION,
            listOf(
                "正整数、0、负整数统称为整数；正分数和负分数统称为分数。",
                "整数和分数统称为有理数。任何有理数都可以写成两个整数之比的形式，其中分母不为 0。",
            ),
            formula = "a/b（a，b 为整数，b≠0）",
            conclusion = "有理数包括整数和分数。",
        ),
        page(
            "rational-classification",
            "1.2 有理数及其大小比较",
            "有理数的分类",
            source,
            1,
            RationalVisualizationKind.RATIONAL_CLASSIFICATION,
            listOf(
                "按符号分类，有理数分为正有理数、0 和负有理数。",
                "按表示形式分类，有理数分为整数和分数。分类标准不同，所得分类形式也不同。",
            ),
            conclusion = "分类时必须明确所采用的标准。",
        ),
        page(
            "number-line-definition",
            "1.2 有理数及其大小比较",
            "数轴",
            source,
            2,
            RationalVisualizationKind.NUMBER_LINE,
            listOf(
                "规定了原点、正方向和单位长度的直线叫作数轴。",
                "原点表示 0，正数用原点右边的点表示，负数用原点左边的点表示。",
            ),
            conclusion = "原点、正方向和单位长度是数轴的三个要素。",
        ),
        page(
            "rational-on-line",
            "1.2 有理数及其大小比较",
            "用数轴表示有理数",
            source,
            3,
            RationalVisualizationKind.NUMBER_LINE,
            listOf(
                "任何一个有理数都可以用数轴上的一个点表示。",
                "表示分数时，应按照分母把相应单位长度等分，再确定点的位置。",
            ),
            conclusion = "数轴把数与直线上的位置联系起来。",
        ),
        page(
            "opposite-number",
            "1.2 有理数及其大小比较",
            "相反数",
            source,
            5,
            RationalVisualizationKind.OPPOSITE_NUMBERS,
            listOf(
                "只有符号不同的两个数互为相反数。0 的相反数是 0。",
                "在数轴上，互为相反数的两个点位于原点两侧，并且到原点的距离相等。",
            ),
            formula = "a 的相反数是 −a",
            conclusion = "互为相反数的两个点关于原点对称。",
        ),
        page(
            "absolute-value",
            "1.2 有理数及其大小比较",
            "绝对值",
            source,
            7,
            RationalVisualizationKind.ABSOLUTE_VALUE,
            listOf(
                "数轴上表示数 a 的点与原点的距离，叫作数 a 的绝对值，记作 |a|。",
                "正数的绝对值是它本身；负数的绝对值是它的相反数；0 的绝对值是 0。",
            ),
            formula = "|a|≥0",
            conclusion = "绝对值表示距离，因此绝对值不可能是负数。",
        ),
        page(
            "rational-comparison",
            "1.2 有理数及其大小比较",
            "有理数的大小比较",
            source,
            9,
            RationalVisualizationKind.NUMBER_COMPARISON,
            listOf(
                "在数轴上表示的两个数，右边的数总比左边的数大。",
                "正数大于 0，0 大于负数；两个负数比较大小，绝对值大的反而小。",
            ),
            conclusion = "比较有理数大小，可以利用数轴，也可以利用符号和绝对值。",
        ),
    )

    private fun numberLinePages(source: IntRange) =
        rationalNumberAndComparisonPages(source).filter {
            it.visualization == RationalVisualizationKind.NUMBER_LINE
        }

    private fun oppositeNumberPages(source: IntRange) =
        rationalNumberAndComparisonPages(source).filter {
            it.visualization == RationalVisualizationKind.OPPOSITE_NUMBERS
        }

    private fun absoluteValuePages(source: IntRange) =
        rationalNumberAndComparisonPages(source).filter {
            it.visualization == RationalVisualizationKind.ABSOLUTE_VALUE
        }

    private fun additionAndSubtractionPages(source: IntRange) = listOf(
        page(
            "addition-meaning",
            "2.1 有理数的加法与减法",
            "有理数加法",
            source,
            0,
            RationalVisualizationKind.ADDITION_PROCESS,
            listOf(
                "有理数相加时，既要确定和的符号，又要确定和的绝对值。",
                "同号两数相加，取相同的符号并把绝对值相加；异号两数相加，先比较绝对值。",
            ),
            conclusion = "符号与绝对值应分别判断。",
        ),
        page(
            "addition-rule",
            "2.1 有理数的加法与减法",
            "有理数加法法则",
            source,
            3,
            RationalVisualizationKind.ADDITION_PROCESS,
            listOf(
                "绝对值不相等的异号两数相加，取绝对值较大的加数的符号，并用较大的绝对值减去较小的绝对值。",
                "互为相反数的两个数相加得 0；一个数同 0 相加，仍得这个数。",
            ),
            formula = "3+(−5)=−(5−3)=−2",
            conclusion = "先确定符号，再计算绝对值。",
        ),
        page(
            "addition-laws",
            "2.1 有理数的加法与减法",
            "加法运算律",
            source,
            6,
            RationalVisualizationKind.ADDITION_PROCESS,
            listOf(
                "有理数的加法仍满足交换律和结合律。",
                "多个有理数相加时，可以交换加数的位置，也可以先把互为相反数或便于计算的数结合。",
            ),
            formula = "a+b=b+a；(a+b)+c=a+(b+c)",
            conclusion = "运算律可以改变运算顺序，但不改变结果。",
        ),
        page(
            "subtraction-rule",
            "2.1 有理数的加法与减法",
            "有理数减法法则",
            source,
            8,
            RationalVisualizationKind.SUBTRACTION_TRANSFORM,
            listOf(
                "有理数的减法可以转化为加法。",
                "减去一个数，等于加这个数的相反数。转化后，按照有理数加法法则计算。",
            ),
            formula = "a−b=a+(−b)",
            conclusion = "转化时，减数必须变为它的相反数。",
        ),
        page(
            "addition-subtraction-mixed",
            "2.1 有理数的加法与减法",
            "加减混合运算",
            source,
            10,
            RationalVisualizationKind.SUBTRACTION_TRANSFORM,
            listOf(
                "引入相反数后，加减混合运算可以统一为加法运算。",
                "省略括号和加号时，必须保持每个数原有的符号，再利用加法运算律进行计算。",
            ),
            formula = "a+b−c=a+b+(−c)",
            conclusion = "统一为加法后，再选择适当的运算顺序。",
        ),
    )

    private fun multiplicationAndDivisionPages(source: IntRange) = listOf(
        page(
            "multiplication-rule",
            "2.2 有理数的乘法与除法",
            "有理数乘法法则",
            source,
            0,
            RationalVisualizationKind.MULTIPLICATION_SIGN,
            listOf(
                "两数相乘，同号得正，异号得负，积的绝对值等于乘数绝对值的积。",
                "任何数与 0 相乘，都得 0。",
            ),
            formula = "(−3)×(−4)=+(3×4)=12",
            conclusion = "先确定积的符号，再计算绝对值的积。",
        ),
        page(
            "multiple-factors",
            "2.2 有理数的乘法与除法",
            "多个有理数相乘",
            source,
            3,
            RationalVisualizationKind.MULTIPLICATION_SIGN,
            listOf(
                "几个不等于 0 的数相乘，积的符号由负因数的个数决定。",
                "负因数有奇数个时积为负；负因数有偶数个时积为正。有一个因数为 0，积就是 0。",
            ),
            conclusion = "符号判断与绝对值计算应分开进行。",
        ),
        page(
            "reciprocal",
            "2.2 有理数的乘法与除法",
            "倒数",
            source,
            6,
            RationalVisualizationKind.DIVISION_TRANSFORM,
            listOf(
                "乘积是 1 的两个数互为倒数。",
                "非零有理数 a 的倒数是 1/a。0 没有倒数。",
            ),
            formula = "a·(1/a)=1（a≠0）",
            conclusion = "只有非零有理数才有倒数。",
        ),
        page(
            "division-rule",
            "2.2 有理数的乘法与除法",
            "有理数除法法则",
            source,
            8,
            RationalVisualizationKind.DIVISION_TRANSFORM,
            listOf(
                "除以一个不等于 0 的数，等于乘这个数的倒数。",
                "两数相除，同号得正，异号得负；0 除以任何一个不等于 0 的数，都得 0。",
            ),
            formula = "a÷b=a·(1/b)（b≠0）",
            conclusion = "除法可以转化为乘法，除数不能为 0。",
        ),
        page(
            "mixed-four-operations",
            "2.2 有理数的乘法与除法",
            "四则混合运算",
            source,
            10,
            RationalVisualizationKind.DIVISION_TRANSFORM,
            listOf(
                "有理数的加、减、乘、除混合运算，如无括号，按照先乘除、后加减的顺序进行。",
                "同级运算按照从左到右的顺序进行；有括号时，先算括号内的运算。",
            ),
            conclusion = "运算顺序决定每一步的运算对象。",
        ),
    )

    private fun powerPages(source: IntRange) = listOf(
        page(
            "power-definition",
            "2.3 有理数的乘方",
            "乘方",
            source,
            0,
            RationalVisualizationKind.POWER_PROCESS,
            listOf(
                "求 n 个相同因数的积的运算叫作乘方，乘方的结果叫作幂。",
                "在 aⁿ 中，a 叫作底数，n 叫作指数；aⁿ 表示 n 个 a 相乘。",
            ),
            formula = "aⁿ=a·a·…·a（n 个 a）",
            conclusion = "指数表示相同因数的个数。",
        ),
        page(
            "negative-base-power",
            "2.3 有理数的乘方",
            "负数的乘方",
            source,
            2,
            RationalVisualizationKind.POWER_PROCESS,
            listOf(
                "负数的奇次幂是负数，负数的偶次幂是正数。",
                "判断符号时，应先确定负号是否属于底数，再判断指数的奇偶性。",
            ),
            formula = "(−2)³=−8；(−2)⁴=16",
            conclusion = "括号明确指数作用的范围。",
        ),
        page(
            "mixed-operation-order",
            "2.3 有理数的乘方",
            "有理数混合运算的顺序",
            source,
            6,
            RationalVisualizationKind.POWER_PROCESS,
            listOf(
                "先乘方，再乘除，最后加减；同级运算从左到右进行。",
                "有括号时，先算括号内的运算，并按照小括号、中括号、大括号的顺序处理。",
            ),
            formula = "括号 → 乘方 → 乘除 → 加减",
            conclusion = "每一步都应明确运算依据。",
        ),
    )

    private fun negativeNumberHistoryPages(source: IntRange) = listOf(
        page(
            "negative-number-history",
            "图说数学史",
            "负数认识的发展",
            source,
            0,
            RationalVisualizationKind.HISTORY,
            listOf(
                "负数的形成经历了较长过程。收入与支出、盈与亏等实际计算，使人们逐步需要表示小于 0 的量。",
                "中国古代数学使用不同颜色的算筹区分正数和负数，并形成正负数的运算方法。",
            ),
            conclusion = "数学概念常在解决实际问题和完善运算体系的过程中逐步形成。",
        ),
    )

    private fun signedArithmeticHistoryPages(source: IntRange) = listOf(
        page(
            "positive-negative-method",
            "阅读与思考",
            "正负术",
            source,
            0,
            RationalVisualizationKind.HISTORY,
            listOf(
                "《九章算术》的“方程”章给出了正负数加减运算的规则。",
                "这些规则把具有相反意义的量纳入统一计算，体现了有理数运算从具体情境走向一般法则的过程。",
            ),
            conclusion = "正负数运算法则使相反意义的量能够统一计算。",
        ),
    )

    private fun numberSystemExtensionPages(source: IntRange) = listOf(
        page(
            "number-system-extension",
            "探究与发现",
            "数系扩充与乘法法则",
            source,
            0,
            RationalVisualizationKind.MULTIPLICATION_SIGN,
            listOf(
                "把非负有理数扩充到有理数后，原有运算律应继续成立。",
                "负数乘负数的符号规则应与分配律等已有规律相容，数系扩充必须保持运算结构的一致性。",
            ),
            conclusion = "新的运算法则必须与已经成立的运算律保持一致。",
        ),
    )

    private fun chapterOneSummaryPages(source: IntRange) = listOf(
        pageAt(
            "chapter-one-summary",
            "第一章 小结",
            "本章知识结构",
            source,
            21,
            RationalVisualizationKind.RATIONAL_CLASSIFICATION,
            listOf(
                "本章从具有相反意义的量出发引入负数，使数的范围扩大到有理数。",
                "数轴把有理数与直线上的点联系起来，并用于研究相反数、绝对值和有理数的大小比较。",
            ),
            conclusion = "数轴和数形结合是本章的主要研究工具。",
        ),
        pageAt(
            "chapter-one-review",
            "第一章 小结",
            "复习问题",
            source,
            21,
            RationalVisualizationKind.NUMBER_LINE,
            listOf(
                "梳理已经学习过的数，说明每次扩大数的范围时引入新数的原因。",
                "说明数轴与普通直线的不同，并说明怎样利用数轴解释相反数、绝对值和大小比较。",
                "举例说明正数、负数在表示具有相反意义的量时的作用。",
            ),
        ),
    )

    private fun chapterOneExercisePages(source: IntRange) = listOf(
        pageAt(
            "chapter-one-exercises-1-2",
            "第一章 章末练习",
            "正负数与数轴",
            source,
            22,
            RationalVisualizationKind.NUMBER_LINE,
            listOf(
                "1. 如果温度上升 3 ℃ 记作 +3 ℃，那么下降 2 ℃记作什么？如果收入用正数表示，−56 元表示什么？",
                "2. 在数轴上表示 3，−4，0，2，−2，−1，并按从小到大的顺序排列。",
            ),
            conclusion = "先明确正负号的实际意义，再利用数轴从左到右比较大小。",
        ),
        pageAt(
            "chapter-one-exercises-3-4",
            "第一章 章末练习",
            "相反数、绝对值与比较",
            source,
            22,
            RationalVisualizationKind.ABSOLUTE_VALUE,
            listOf(
                "3. 分别写出 −2，−5，7.5 的相反数和绝对值。",
                "4. 比较：+(−3) 与 −(−4)；−(−2) 与 −(+2)；+(−3) 与 −(+5)。",
            ),
            conclusion = "先化简符号，再根据数轴位置或绝对值比较。",
        ),
        pageAt(
            "chapter-one-exercises-5-8",
            "第一章 章末练习",
            "实际问题与字母表示",
            source,
            22,
            RationalVisualizationKind.NUMBER_COMPARISON,
            listOf(
                "5. 某公司四个季度的盈利分别为 −6.8，−10.7，31.5，27.8 万元，按从高到低排列。",
                "6. 比较 −5.6%，−4.0%，13.0%，−9.6% 的大小，并说明增幅为负数的意义。",
                "7. 已知 x 是整数且 −3<x<4，在数轴上表示 x 的所有可能取值。",
                "8. 已知数轴上 a<0<b，比较 a，−a，b，−b 的大小。",
            ),
        ),
        pageAt(
            "chapter-one-exercises-9-11",
            "第一章 章末练习",
            "偏差、区间与绝对值",
            source,
            23,
            RationalVisualizationKind.OPPOSITE_QUANTITIES,
            listOf(
                "9. 五个排球超过标准质量的克数分别为 +5，−3.5，+0.7，−2.5，−0.6。说明各数的意义，并判断哪个球最接近标准质量。",
                "10. 研究 −1 与 0、0 与 1、−3 与 −1 之间的数，并写出 3 个小于 −100 且大于 −103 的数。",
                "11. 如果 |x|=2，x 一定等于 2 吗？如果 |x|=0，x 等于几？如果 x=−x，x 等于几？",
            ),
            conclusion = "与标准值的接近程度由偏差的绝对值决定。",
        ),
    )

    private fun chapterTwoSummaryPages(source: IntRange) = listOf(
        pageAt(
            "chapter-two-summary",
            "第二章 小结",
            "本章知识结构",
            source,
            59,
            RationalVisualizationKind.MULTIPLICATION_SIGN,
            listOf(
                "本章把非负有理数的加法和乘法推广到有理数范围，并研究了它们的逆运算——减法和除法。",
                "研究有理数运算时，一般同时考虑数的符号和绝对值，并把与负数有关的运算转化为正数之间的运算。",
            ),
            conclusion = "转化、数形结合和保持运算一致性是本章的重要思想。",
        ),
        pageAt(
            "chapter-two-review",
            "第二章 小结",
            "复习问题",
            source,
            60,
            RationalVisualizationKind.SUBTRACTION_TRANSFORM,
            listOf(
                "举例说明如何借助绝对值，把与负数有关的运算转化为正数之间的运算。",
                "说明加法与减法、乘法与除法之间的关系，以及有理数混合运算的顺序。",
                "结合例子说明交换律、结合律和分配律在有理数运算中的作用。",
            ),
            formula = "减法 → 加法；除法 → 乘法",
        ),
    )

    private fun chapterTwoExercisePages(source: IntRange) = listOf(
        pageAt(
            "chapter-two-exercises-1-3",
            "第二章 章末练习",
            "基本运算",
            source,
            61,
            RationalVisualizationKind.ADDITION_PROCESS,
            listOf(
                "1. 计算：−150+250；−15+(−23)；−5−65；−26−(−15)；(−6)×(−16)；8÷(−16)。",
                "2. 计算含加减乘除和乘方的混合算式，并按规定顺序书写每一步。",
                "3. 互为相反数的两个数的和是多少？互为倒数的两个数的积是多少？",
            ),
        ),
        pageAt(
            "chapter-two-exercises-4-6",
            "第二章 章末练习",
            "科学记数法、近似数与括号",
            source,
            61,
            RationalVisualizationKind.POWER_PROCESS,
            listOf(
                "4. 用科学记数法表示 100000000，4500000，692400000000。",
                "5. 按要求用四舍五入法取近似数：245.635 精确到 0.1；175.65 精确到个位；12.004 精确到百分位。",
                "6. 比较 −2−(−3) 与 −[2−(−3)] 的计算过程和结果。",
            ),
            conclusion = "括号决定运算对象；科学记数法中的系数应大于或等于 1 且小于 10。",
        ),
        pageAt(
            "chapter-two-exercises-7-8",
            "第二章 章末练习",
            "比赛净胜球与平均成绩",
            source,
            61,
            RationalVisualizationKind.ADDITION_PROCESS,
            listOf(
                "7. 根据三场足球比赛的进球数和失球数，分别计算红、黄、蓝三队的净胜球数。",
                "8. 十名学生的成绩为 82，83，78，66，95，75，61，93，82，81。先估算平均成绩，再准确计算。",
            ),
            conclusion = "净胜球数等于进球数与失球数的代数和。",
        ),
        pageAt(
            "chapter-two-exercises-9-12",
            "第二章 章末练习",
            "实际问题中的有理数运算",
            source,
            62,
            RationalVisualizationKind.SUBTRACTION_TRANSFORM,
            listOf(
                "9. 文具店一周盈亏表中星期六的数据缺失，已知全周合计为 458 元，求星期六的盈亏。",
                "10. 巡道员从驻地向东走 7 km，再向东走 3 km，随后向西走 11.5 km，判断最终方向和距离。",
                "11. 金属丝随温度每升高 1 ℃伸长约 0.002 mm，先由 20 ℃升至 30 ℃，再降至 5 ℃，分析长度变化。",
                "12. 1 个天文单位约为 1.496 亿千米，用科学记数法表示。",
            ),
        ),
        pageAt(
            "chapter-two-exercises-13-15",
            "第二章 章末练习",
            "归纳、反例与规律",
            source,
            62,
            RationalVisualizationKind.POWER_PROCESS,
            listOf(
                "13. 通过特例比较：小于 1 的正数 a、a²、a³；大于 −1 的负数 b、b²、b³。",
                "14. 判断并说明：任何数都不等于它的相反数；互为相反数的两个数的同一正偶数次幂相等；若 a>b，则 1/a<1/b。",
                "15. 计算 1×1，11×11，111×111，1111×1111，归纳规律并预测更长的同类乘积。",
            ),
            conclusion = "判断一般性命题时，应同时考虑证明和反例。",
        ),
    )

    private fun rationalNumberChapterPages(source: IntRange): List<RationalLessonPage> =
        positiveAndNegativePages(subRange(source, 1, 5)) +
            rationalNumberAndComparisonPages(subRange(source, 6, 16)) +
            chapterOneSummaryPages(source) +
            chapterOneExercisePages(source)

    private fun operationChapterPages(source: IntRange): List<RationalLessonPage> =
        additionAndSubtractionPages(subRange(source, 1, 13)) +
            multiplicationAndDivisionPages(subRange(source, 14, 26)) +
            powerPages(subRange(source, 27, 34)) +
            chapterTwoSummaryPages(source) +
            chapterTwoExercisePages(source)

    private fun page(
        id: String,
        section: String,
        title: String,
        source: IntRange,
        offset: Int,
        visualization: RationalVisualizationKind,
        paragraphs: List<String>,
        formula: String? = null,
        conclusion: String? = null,
    ) = RationalLessonPage(
        id = id,
        section = section,
        title = title,
        paragraphs = paragraphs,
        sourcePage = (source.first + offset).coerceIn(source.first, source.last),
        visualization = visualization,
        formula = formula,
        conclusion = conclusion,
    )

    private fun pageAt(
        id: String,
        section: String,
        title: String,
        source: IntRange,
        preferredSourcePage: Int,
        visualization: RationalVisualizationKind,
        paragraphs: List<String>,
        formula: String? = null,
        conclusion: String? = null,
    ) = RationalLessonPage(
        id = id,
        section = section,
        title = title,
        paragraphs = paragraphs,
        sourcePage = preferredSourcePage.coerceIn(source.first, source.last),
        visualization = visualization,
        formula = formula,
        conclusion = conclusion,
    )

    private fun subRange(source: IntRange, startOffset: Int, endOffset: Int): IntRange {
        val start = (source.first + startOffset).coerceIn(source.first, source.last)
        val end = (source.first + endOffset).coerceIn(start, source.last)
        return start..end
    }

    private fun normalize(title: String): String = title
        .replace(" ", "")
        .replace("　", "")
        .replace("（", "(")
        .replace("）", ")")
        .replace(Regex("^[第0-9一二三四五六七八九十.、]+"), "")

    private val supportedKeywords = listOf(
        "正数和负数",
        "允许偏差",
        "有理数及其大小比较",
        "有理数的概念",
        "有理数概念",
        "数轴",
        "相反数",
        "绝对值",
        "漫漫长路识负数",
        "有理数的加法与减法",
        "正负术",
        "有理数的乘法与除法",
        "数系扩充看乘法法则",
        "有理数的乘方",
    )
}
