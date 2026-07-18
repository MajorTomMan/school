package com.majortomman.school.learning.course

import com.majortomman.school.data.Lesson
import com.majortomman.school.learning.capability.ConceptId
import com.majortomman.school.learning.capability.ContentOrigin
import com.majortomman.school.learning.capability.OperationId
import com.majortomman.school.learning.capability.WidgetType

enum class MathCourseCategory {
    NUMBER,
    ALGEBRA,
    EQUATION,
    FUNCTION,
    GEOMETRY,
    VECTOR,
    PROBABILITY,
    STATISTICS,
    SEQUENCE,
    TRIGONOMETRY,
    CALCULUS,
    COMPLEX,
    GENERAL,
}

data class MathCourseContent(
    val category: MathCourseCategory,
    val badge: String,
    val subtitle: String,
    val representativeExpression: String,
    val sourceSummary: String,
    val reasoningTitle: String,
    val reasoningSteps: List<String>,
    val background: List<String>,
    val misconception: String,
    val enrichment: LessonEnrichment,
)

object MathCourseContentFactory {
    fun create(lesson: Lesson): MathCourseContent {
        val category = classify(lesson.title)
        val pages = lesson.textbookPages
        val profile = profile(category, lesson.title)
        val pageLabel = if (pages.first == pages.last) "第 ${pages.first} 页" else "第 ${pages.first}—${pages.last} 页"
        val sourceSummary = buildString {
            append("本课程按教材目录中的“${lesson.title}”及$pageLabel 组织。")
            append("当前仓库保存课程标题、层级和页码范围，不保存教材正文；因此这里使用 School 自有解释，不生成未经核对的教材逐字引文。")
            append("绑定原教材 PDF 后，可以直接打开本课页码核对定义、例题、图形和练习顺序。")
        }
        return MathCourseContent(
            category = category,
            badge = "数学课程 · 教材页码约束",
            subtitle = profile.subtitle,
            representativeExpression = profile.expression,
            sourceSummary = sourceSummary,
            reasoningTitle = "按教材顺序理解${lesson.title}",
            reasoningSteps = profile.steps,
            background = profile.background,
            misconception = profile.misconception,
            enrichment = LessonEnrichment(
                background = profile.background.mapIndexed { index, paragraph ->
                    CourseNote(
                        origin = ContentOrigin.SCHOOL_EXPLANATION,
                        title = if (index == 0) "背景知识" else "与旧知识的联系",
                        body = paragraph,
                    )
                },
                extensions = listOf(
                    CourseNote(
                        origin = ContentOrigin.OPTIONAL_EXTENSION,
                        title = "扩展：从本课继续研究",
                        body = profile.extension,
                    ),
                ),
                visualization = CourseVisualizationSpec(
                    kind = profile.visualization,
                    title = profile.visualTitle,
                    description = profile.visualDescription,
                    parameters = profile.parameters,
                    requiredConcepts = profile.concepts,
                    requiredOperations = profile.operations,
                    requiredWidgets = profile.widgets,
                ),
                verification = CourseVerificationSpec(
                    kind = CourseVerificationKind.MATH_EXPRESSION,
                    title = "自定义数学公式验证",
                    prompt = "输入与本课有关的表达式、等式或不等式，并填写变量值；也可以执行多组样本一致性检查。",
                    inputHint = "例如 √72=6√2、y=2x+1、x^2-5x+6=0",
                    examples = profile.examples,
                    requiredConcepts = profile.concepts,
                    requiredOperations = setOf(
                        OperationId.PARSE_EXPRESSION,
                        OperationId.SIMPLIFY_EXPRESSION,
                        OperationId.VERIFY_EQUALITY,
                    ),
                ),
            ),
        )
    }

    fun classify(title: String): MathCourseCategory {
        val normalized = title.replace(" ", "").replace("　", "")
        return when {
            normalized.contains("复数") || normalized.contains("复平面") -> MathCourseCategory.COMPLEX
            normalized.contains("导数") || normalized.contains("微分") || normalized.contains("积分") || normalized.contains("极限") -> MathCourseCategory.CALCULUS
            normalized.contains("三角函数") || normalized.contains("正弦") || normalized.contains("余弦") || normalized.contains("锐角三角比") -> MathCourseCategory.TRIGONOMETRY
            normalized.contains("数列") || normalized.contains("等差") || normalized.contains("等比") -> MathCourseCategory.SEQUENCE
            normalized.contains("概率") || normalized.contains("随机") || normalized.contains("可能性") -> MathCourseCategory.PROBABILITY
            normalized.contains("统计") || normalized.contains("平均数") || normalized.contains("方差") || normalized.contains("频数") || normalized.contains("数据") -> MathCourseCategory.STATISTICS
            normalized.contains("向量") -> MathCourseCategory.VECTOR
            normalized.contains("函数") || normalized.contains("图象") || normalized.contains("图像") -> MathCourseCategory.FUNCTION
            normalized.contains("方程") || normalized.contains("不等式") || normalized.contains("不等式组") -> MathCourseCategory.EQUATION
            normalized.contains("三角形") || normalized.contains("四边形") || normalized.contains("圆") ||
                normalized.contains("几何") || normalized.contains("平行") || normalized.contains("垂直") ||
                normalized.contains("全等") || normalized.contains("相似") || normalized.contains("面积") || normalized.contains("体积") -> MathCourseCategory.GEOMETRY
            normalized.contains("整式") || normalized.contains("因式") || normalized.contains("分式") ||
                normalized.contains("代数式") || normalized.contains("根式") || normalized.contains("二次根式") -> MathCourseCategory.ALGEBRA
            normalized.contains("数轴") || normalized.contains("有理数") || normalized.contains("实数") ||
                normalized.contains("绝对值") || normalized.contains("相反数") || normalized.contains("平方根") ||
                normalized.contains("立方根") || normalized.contains("数的开方") -> MathCourseCategory.NUMBER
            else -> MathCourseCategory.GENERAL
        }
    }

    private fun profile(category: MathCourseCategory, title: String): MathProfile = when (category) {
        MathCourseCategory.NUMBER -> MathProfile(
            subtitle = "把数的定义、表示、运算和数域边界放在同一条线上理解",
            expression = "√a、|x|、a/b",
            steps = listOf("确认教材正在研究哪一类数", "观察定义与表示方式", "按教材例题总结运算规则", "检查结果是否仍在当前数域", "用数轴或精确表达式验证"),
            background = listOf("数的扩展通常是为了解决原有数域中无法完成的运算；每次扩展都要保留已有运算规律。", "精确分数、根式和 π 不应过早变成小数，近似值必须和精确值分开。"),
            misconception = "不要把近似小数当成精确值，也不要在教材尚未引入复数时把负数开平方直接写成实数。",
            extension = "完成本课后，可以比较有理数、无理数、实数和复数之间的包含关系，但扩展内容不作为当前章节练习的前置条件。",
            visualization = CourseVisualizationKind.NUMBER_LINE,
            visualTitle = "数与位置或精确值",
            visualDescription = "调节输入值，观察数轴位置、根式化简和精确值/近似值差异。",
            parameters = listOf(numberParameter("x", "输入数", "2", -10.0, 10.0, 0.5)),
            concepts = setOf(ConceptId.EXACT_NUMBER, ConceptId.RATIONAL_NUMBER, ConceptId.RADICAL),
            operations = setOf(OperationId.PARSE_EXPRESSION, OperationId.SIMPLIFY_EXPRESSION),
            widgets = setOf(WidgetType.EXACT_EXPRESSION),
            examples = listOf("√72", "2π+3π", "|-5|=5"),
        )
        MathCourseCategory.ALGEBRA -> MathProfile(
            subtitle = "先识别式子的结构，再执行有依据的等价变形",
            expression = "2x+3x=5x",
            steps = listOf("识别项、因式、次数或分母", "确认变形目标", "每一步只使用已学运算律", "保留定义域和非零条件", "代入样本或展开回去检查"),
            background = listOf("代数式把数量关系压缩为符号结构；同一个字母在同一道题中表示同一个量。", "等价变形要求前后在允许取值范围内表示相同关系。"),
            misconception = "不能只看符号相似就合并，也不能约去可能等于零的因式而不说明条件。",
            extension = "可进一步观察多项式次数、因式和图像之间的联系，但先完成教材规定的整式或分式变形。",
            visualization = CourseVisualizationKind.DATA_TABLE,
            visualTitle = "代数式结构与代入",
            visualDescription = "改变变量值，对比变形前后结果，并显示每一项对总值的贡献。",
            parameters = listOf(numberParameter("x", "变量 x", "2", -10.0, 10.0, 0.5)),
            concepts = setOf(ConceptId.VARIABLE, ConceptId.POLYNOMIAL, ConceptId.FACTORIZATION),
            operations = setOf(OperationId.PARSE_EXPRESSION, OperationId.SIMPLIFY_EXPRESSION, OperationId.MULTIPLY_POLYNOMIAL),
            widgets = setOf(WidgetType.POLYNOMIAL_WORKBENCH),
            examples = listOf("2x+3x=5x", "(x+1)(x-1)=x^2-1"),
        )
        MathCourseCategory.EQUATION -> MathProfile(
            subtitle = "把未知量、等量关系、解法步骤和验根放在一起",
            expression = "ax²+bx+c=0",
            steps = listOf("从题意或原式确定等量关系", "说明未知量和允许取值", "按教材方法执行等价变形", "得到候选解", "代回原式检验并写出结论"),
            background = listOf("方程的解是使等号成立的未知数取值，解方程的目标不是把符号机械移到另一边。", "不等式变形还要关注乘除负数时方向改变。"),
            misconception = "得到候选值后不能跳过检验；含分母、根号或平方的方程尤其可能出现无效解。",
            extension = "可进一步比较代数解、图像交点和数值近似三种观点，但教材指定的方法仍是本课核心。",
            visualization = CourseVisualizationKind.CARTESIAN_GRAPH,
            visualTitle = "等式两边与交点",
            visualDescription = "调节系数，观察等式两边的图像、交点和判别式变化。",
            parameters = listOf(
                numberParameter("a", "系数 a", "1", -5.0, 5.0, 1.0),
                numberParameter("b", "系数 b", "-5", -10.0, 10.0, 1.0),
                numberParameter("c", "系数 c", "6", -10.0, 10.0, 1.0),
            ),
            concepts = setOf(ConceptId.EQUATION, ConceptId.INEQUALITY, ConceptId.DISCRIMINANT),
            operations = setOf(OperationId.SOLVE_LINEAR_EQUATION, OperationId.SOLVE_QUADRATIC_EQUATION, OperationId.SOLVE_LINEAR_INEQUALITY),
            widgets = setOf(WidgetType.EQUATION_SOLVER, WidgetType.COORDINATE_GRAPH_2D),
            examples = listOf("x^2-5x+6=0", "2x+3=7", "-2x<6"),
        )
        MathCourseCategory.FUNCTION -> MathProfile(
            subtitle = "从变量对应关系、表格和图像理解函数，而不是只记图形外观",
            expression = "y=f(x)",
            steps = listOf("确认自变量和因变量", "从情境或解析式计算对应值", "列出有代表性的点", "在允许定义域内描点或作图", "用图像和解析式共同解释变化"),
            background = listOf("函数首先是一种确定的对应关系；图像是所有允许对应点的集合。", "定义域决定哪些输入有意义，不能只看画出的曲线。"),
            misconception = "不能把函数等同于一条曲线，也不能忽略实际问题中的取值范围和单位。",
            extension = "完成教材核心后，可比较参数变化如何影响图像；参数语言不得替代本节尚未引入的教材概念。",
            visualization = CourseVisualizationKind.CARTESIAN_GRAPH,
            visualTitle = "对应值与函数图像",
            visualDescription = "改变 x 和教材允许的参数，公式、表格、点和图像同步变化。",
            parameters = listOf(
                numberParameter("x", "自变量 x", "1", -10.0, 10.0, 0.5),
                numberParameter("k", "参数 k", "2", -5.0, 5.0, 0.5),
                numberParameter("b", "参数 b", "0", -5.0, 5.0, 0.5),
            ),
            concepts = setOf(ConceptId.VARIABLE, ConceptId.CORRESPONDING_VALUE, ConceptId.FUNCTION, ConceptId.FUNCTION_GRAPH),
            operations = setOf(OperationId.SUBSTITUTE, OperationId.PLOT_2D, OperationId.CHECK_FUNCTION_DOMAIN),
            widgets = setOf(WidgetType.RELATION_CALCULATOR, WidgetType.COORDINATE_GRAPH_2D),
            examples = listOf("y=2x+1", "y=x^2", "y=1/x"),
        )
        MathCourseCategory.GEOMETRY -> MathProfile(
            subtitle = "从图形对象、条件和关系出发，动态观察但不以图代证",
            expression = "条件 → 关系 → 结论",
            steps = listOf("辨认点、线、角、面或立体", "把已知条件逐条标在图上", "区分观察结果和已证明关系", "选择教材中已经学习的定理", "写出依据并检查结论范围"),
            background = listOf("几何图形帮助形成直觉，但测量或看起来相等不能代替证明。", "拖动图形时应保持题设约束，否则新图形已经不是原问题。"),
            misconception = "不要根据不按比例的示意图直接断言平行、垂直、全等或相似。",
            extension = "可用坐标、向量或变换重新描述同一图形，但只有在教材已经引入时才用于正式解题。",
            visualization = CourseVisualizationKind.GEOMETRY_2D,
            visualTitle = "保持约束的动态图形",
            visualDescription = "调节长度和角度，同时保持题设中的平行、垂直或等长约束。",
            parameters = listOf(
                numberParameter("length", "基准长度", "4", 0.5, 10.0, 0.5, "cm"),
                numberParameter("angle", "角度", "60", 5.0, 175.0, 5.0, "°"),
            ),
            concepts = setOf(ConceptId.COORDINATE_POINT, ConceptId.LINE, ConceptId.CIRCLE, ConceptId.GEOMETRIC_TRANSFORMATION, ConceptId.PROOF_STEP),
            operations = setOf(OperationId.INTERSECT_GEOMETRY, OperationId.TRANSFORM_GEOMETRY, OperationId.VALIDATE_PROOF_STRUCTURE),
            widgets = setOf(WidgetType.VECTOR_GEOMETRY, WidgetType.PROOF_STEPS),
            examples = listOf("AB=AC", "∠A=60", "x^2+y^2=25"),
        )
        MathCourseCategory.VECTOR -> MathProfile(
            subtitle = "同时观察大小、方向、分解和投影",
            expression = "a·b=|a||b|cosθ",
            steps = listOf("确定向量起点与终点", "写出分量或大小方向", "选择加减、数乘、点积或投影", "完成计算", "回到图形解释结果方向和符号"),
            background = listOf("向量不是只有长度的数，它还携带方向；坐标只是表示向量的一种方式。", "点积把方向关系转化为数量，可用于角度和投影。"),
            misconception = "不能把向量除法当作普通数的除法，也不能忽略零向量没有确定方向。",
            extension = "三维向量和叉积是二维向量的后续扩展，只在对应教材章节中作为正式能力。",
            visualization = CourseVisualizationKind.VECTOR,
            visualTitle = "向量合成与投影",
            visualDescription = "调节两个向量分量，观察和向量、点积、夹角和投影。",
            parameters = listOf(
                numberParameter("ax", "aₓ", "3", -5.0, 5.0, 0.5),
                numberParameter("ay", "aᵧ", "2", -5.0, 5.0, 0.5),
                numberParameter("bx", "bₓ", "1", -5.0, 5.0, 0.5),
                numberParameter("by", "bᵧ", "4", -5.0, 5.0, 0.5),
            ),
            concepts = setOf(ConceptId.VECTOR, ConceptId.DOT_PRODUCT),
            operations = setOf(OperationId.COMPUTE_VECTOR),
            widgets = setOf(WidgetType.VECTOR_GEOMETRY),
            examples = listOf("a=(3,2)", "b=(1,4)", "a·b=11"),
        )
        MathCourseCategory.PROBABILITY -> MathProfile(
            subtitle = "区分随机试验、样本空间、事件和长期频率",
            expression = "P(A)=有利结果数/所有等可能结果数",
            steps = listOf("明确随机试验", "列出不重不漏的可能结果", "定义所求事件", "确认是否满足等可能条件", "计算并用模拟频率检查直觉"),
            background = listOf("概率描述不确定事件的可能程度，不保证一次试验一定出现某结果。", "频率在大量重复试验中趋于稳定，但有限次数仍会波动。"),
            misconception = "不能把连续几次没出现某结果理解为下一次更容易出现，除非试验条件本身发生改变。",
            extension = "可增加重复次数观察大数趋势，但模拟结果不能替代理论条件的检查。",
            visualization = CourseVisualizationKind.DATA_TABLE,
            visualTitle = "随机试验与频率",
            visualDescription = "调节理论概率和试验次数，观察频率波动及其长期趋势。",
            parameters = listOf(
                numberParameter("probability", "理论概率", "0.5", 0.0, 1.0, 0.05),
                integerParameter("trials", "试验次数", "100", 1.0, 1000.0, 1.0),
            ),
            concepts = setOf(ConceptId.VARIABLE),
            operations = setOf(OperationId.SOLVE_RELATION),
            widgets = setOf(WidgetType.RELATION_CALCULATOR),
            examples = listOf("P(A)=1/2", "频率=出现次数/试验次数"),
        )
        MathCourseCategory.STATISTICS -> MathProfile(
            subtitle = "先理解数据来源和分布，再选择统计量",
            expression = "平均数、方差、频数分布",
            steps = listOf("确认总体、样本和数据来源", "整理数据并检查异常", "根据问题选择图表或统计量", "完成计算", "解释结果能说明什么、不能说明什么"),
            background = listOf("同一个平均数可能对应非常不同的分布，统计量必须和原始数据或图表一起看。", "样本是否具有代表性，往往比计算公式本身更重要。"),
            misconception = "不能仅凭平均数判断所有个体，也不能把相关关系直接解释成因果关系。",
            extension = "可进一步比较中位数、四分位数和标准差，但只在教材引入后纳入正式练习。",
            visualization = CourseVisualizationKind.DATA_TABLE,
            visualTitle = "数据分布与统计量",
            visualDescription = "修改一组数据，观察平均数、中位数、极差和分布图怎样变化。",
            parameters = listOf(numberParameter("value", "可调数据点", "5", -20.0, 20.0, 1.0)),
            concepts = setOf(ConceptId.VARIABLE),
            operations = setOf(OperationId.SOLVE_RELATION),
            widgets = setOf(WidgetType.RELATION_CALCULATOR),
            examples = listOf("平均数=(x1+x2+x3)/3", "方差=离均差平方的平均"),
        )
        MathCourseCategory.SEQUENCE -> MathProfile(
            subtitle = "从项的变化规律建立通项和递推关系",
            expression = "aₙ=a₁+(n-1)d",
            steps = listOf("观察相邻项或项号关系", "判断教材关注递推还是通项", "用已知项确定参数", "计算目标项或和", "代回前几项检验规律"),
            background = listOf("数列是一列按确定顺序排列的数，项号是离散自变量。", "递推公式说明相邻项如何产生，通项公式直接连接项号和项值。"),
            misconception = "只根据前几项猜到一个规律并不能证明它是唯一规律，题目必须给出确定条件。",
            extension = "可用函数图像观察数列点，但数列定义域仍是离散项号。",
            visualization = CourseVisualizationKind.CARTESIAN_GRAPH,
            visualTitle = "项号与项值",
            visualDescription = "调节首项、公差或公比，观察离散点和前 n 项和。",
            parameters = listOf(
                numberParameter("a1", "首项", "1", -10.0, 10.0, 1.0),
                numberParameter("d", "公差", "2", -5.0, 5.0, 0.5),
                integerParameter("n", "项号 n", "6", 1.0, 30.0, 1.0),
            ),
            concepts = setOf(ConceptId.FUNCTION, ConceptId.VARIABLE),
            operations = setOf(OperationId.SUBSTITUTE, OperationId.PLOT_2D),
            widgets = setOf(WidgetType.COORDINATE_GRAPH_2D),
            examples = listOf("a_n=1+(n-1)2", "S_n=n(a1+a_n)/2"),
        )
        MathCourseCategory.TRIGONOMETRY -> MathProfile(
            subtitle = "把角、单位圆、三角形比值和周期变化联系起来",
            expression = "sin²θ+cos²θ=1",
            steps = listOf("确认角的范围和单位", "选择直角三角形或单位圆观点", "写出相应三角关系", "完成计算或变形", "检查符号、周期和取值范围"),
            background = listOf("三角函数把角与比值或单位圆坐标联系起来。", "角度制和弧度制是两种角度单位，代入前必须确认一致。"),
            misconception = "不能在没有确认象限时只凭参考角决定正负，也不能混用度和弧度。",
            extension = "可观察周期、相位和频率变化，但这些参数只在对应函数章节中作为正式概念。",
            visualization = CourseVisualizationKind.CARTESIAN_GRAPH,
            visualTitle = "单位圆与函数图像",
            visualDescription = "调节角度，单位圆坐标、三角比和函数图像同步变化。",
            parameters = listOf(numberParameter("angle", "角 θ", "30", -360.0, 360.0, 5.0, "°")),
            concepts = setOf(ConceptId.FUNCTION, ConceptId.FUNCTION_GRAPH, ConceptId.CIRCLE),
            operations = setOf(OperationId.PLOT_2D, OperationId.SUBSTITUTE),
            widgets = setOf(WidgetType.COORDINATE_GRAPH_2D, WidgetType.VECTOR_GEOMETRY),
            examples = listOf("sin(30°)=1/2", "sin^2(x)+cos^2(x)=1"),
        )
        MathCourseCategory.CALCULUS -> MathProfile(
            subtitle = "从变化率和累积量的直觉进入极限、导数或积分",
            expression = "变化率、切线、累积",
            steps = listOf("先明确教材研究的是局部变化还是整体累积", "从平均变化或分割近似出发", "让间隔按教材思想逐渐缩小", "得到导数或积分表达", "回到图像和实际量解释"),
            background = listOf("导数描述局部变化率，积分描述累积；二者都建立在极限思想上。", "符号运算必须和函数定义域、连续性及实际单位一起理解。"),
            misconception = "导数不是简单地把指数移下来，积分也不是只做形式上的逆运算；公式有适用条件。",
            extension = "可用数值差分或面积分割观察逼近过程，但数值模拟不替代教材中的定义和证明。",
            visualization = CourseVisualizationKind.CARTESIAN_GRAPH,
            visualTitle = "割线逼近切线或分割逼近面积",
            visualDescription = "调节 x 和间隔 Δx，观察平均变化率向局部变化率逼近。",
            parameters = listOf(
                numberParameter("x", "位置 x", "1", -5.0, 5.0, 0.25),
                numberParameter("delta", "间隔 Δx", "1", 0.01, 2.0, 0.01),
            ),
            concepts = setOf(ConceptId.FUNCTION, ConceptId.FUNCTION_GRAPH, ConceptId.POLYNOMIAL),
            operations = setOf(OperationId.DIFFERENTIATE_POLYNOMIAL, OperationId.PLOT_2D),
            widgets = setOf(WidgetType.POLYNOMIAL_WORKBENCH, WidgetType.COORDINATE_GRAPH_2D),
            examples = listOf("f(x)=x^2", "f'(x)=2x"),
        )
        MathCourseCategory.COMPLEX -> MathProfile(
            subtitle = "把实部、虚部、代数运算和复平面位置统一起来",
            expression = "z=a+bi",
            steps = listOf("区分实部和虚部", "在复平面标出对应点", "按代数规则完成运算", "用共轭或模检查结果", "解释运算在复平面上的意义"),
            background = listOf("复数把实数轴扩展为二维复平面，虚数单位满足 i²=-1。", "复数不能像实数那样直接比较大小，但可以比较模或特定实数量。"),
            misconception = "虚部是 i 的系数，不是把整个 bi 当作虚部数值；复数大小关系也不能照搬实数。",
            extension = "可进一步观察复数乘法对应旋转和伸缩，但只在教材相关章节中作为正式内容。",
            visualization = CourseVisualizationKind.CARTESIAN_GRAPH,
            visualTitle = "复平面与复数运算",
            visualDescription = "调节实部和虚部，观察点、模、共轭和简单运算。",
            parameters = listOf(
                numberParameter("real", "实部", "3", -5.0, 5.0, 0.5),
                numberParameter("imaginary", "虚部", "2", -5.0, 5.0, 0.5),
            ),
            concepts = setOf(ConceptId.REAL_PART, ConceptId.IMAGINARY_PART, ConceptId.COMPLEX_PLANE),
            operations = setOf(OperationId.PLOT_COMPLEX, OperationId.PARSE_EXPRESSION),
            widgets = setOf(WidgetType.COMPLEX_PLANE, WidgetType.EXACT_EXPRESSION),
            examples = listOf("z=3+2i", "i^2=-1", "(1+i)(1-i)=2"),
        )
        MathCourseCategory.GENERAL -> MathProfile(
            subtitle = "围绕教材中的对象、条件、关系和方法建立可验证理解",
            expression = title,
            steps = listOf("定位本课教材页码", "识别核心对象和符号", "整理已知条件", "按例题顺序建立关系", "用自定义取值或图形验证"),
            background = listOf("数学结论必须建立在定义、条件和推理上，而不是只背最终形式。", "同一结论可以有符号、图形、表格和语言等多种表示。"),
            misconception = "不要脱离教材条件套用公式；先确认符号含义、数域和适用范围。",
            extension = "可尝试用另一种表示解释本课内容，但不把尚未学习的高级工具作为必需步骤。",
            visualization = CourseVisualizationKind.PROCESS,
            visualTitle = "条件到结论",
            visualDescription = "调整本课涉及的量，观察条件、步骤和结论如何联动。",
            parameters = listOf(numberParameter("x", "示例参数", "1", -10.0, 10.0, 1.0)),
            concepts = setOf(ConceptId.VARIABLE),
            operations = setOf(OperationId.PARSE_EXPRESSION, OperationId.VERIFY_EQUALITY),
            widgets = setOf(WidgetType.FORMULA_VERIFIER),
            examples = listOf("2(x+1)=2x+2"),
        )
    }

    private fun numberParameter(
        id: String,
        label: String,
        default: String,
        minimum: Double,
        maximum: Double,
        step: Double,
        unit: String = "",
    ) = CourseParameterSpec(
        id = id,
        label = label,
        kind = CourseParameterKind.NUMBER,
        defaultValue = default,
        unit = unit,
        minimum = minimum,
        maximum = maximum,
        step = step,
    )

    private fun integerParameter(
        id: String,
        label: String,
        default: String,
        minimum: Double,
        maximum: Double,
        step: Double,
    ) = CourseParameterSpec(
        id = id,
        label = label,
        kind = CourseParameterKind.INTEGER,
        defaultValue = default,
        minimum = minimum,
        maximum = maximum,
        step = step,
    )

    private data class MathProfile(
        val subtitle: String,
        val expression: String,
        val steps: List<String>,
        val background: List<String>,
        val misconception: String,
        val extension: String,
        val visualization: CourseVisualizationKind,
        val visualTitle: String,
        val visualDescription: String,
        val parameters: List<CourseParameterSpec>,
        val concepts: Set<ConceptId>,
        val operations: Set<OperationId>,
        val widgets: Set<WidgetType>,
        val examples: List<String>,
    )
}
