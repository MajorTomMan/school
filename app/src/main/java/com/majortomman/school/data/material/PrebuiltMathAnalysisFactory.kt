package com.majortomman.school.data.material

internal object PrebuiltMathAnalysisFactory {
    fun create(slot: TextbookSlot, lesson: GeneratedLesson): LessonAnalysis {
        val title = lesson.title
        val base = LessonAnalysisFallback.generate(slot, lesson)
        if (
            title.contains("数轴") || title.contains("相反数") || title.contains("绝对值") ||
            title.contains("大小比较") || title.contains("正数和负数")
        ) {
            return base.copy(source = LessonAnalysisSource.PACK)
        }

        return when {
            title.containsAny(
                "有理数的加法",
                "有理数的减法",
                "有理数的乘法",
                "有理数的除法",
                "有理数的乘方",
                "混合运算",
                "幂的运算",
            ) -> themed(
                lesson,
                summary = "$title 需要把符号判断、数值计算和运算顺序分开处理，再合并为最终结果。",
                prompt = "负号、括号和不同运算同时出现时，应该先处理哪一层关系？",
                expression = "(-3)+5=2",
                conclusion = "先判断符号与运算顺序，再处理数值部分，最后用估算检查结果是否合理。",
                steps = listOf("识别运算层级", "判断结果符号", "处理括号", "完成数值计算", "估算复核"),
                misconception = "只计算数字部分、最后随意补符号，或者忽略括号前的负号，会造成连续错误。",
                question = "完成一道$title，并说明第一步为什么这样做。",
                answers = listOf("符号", "运算顺序", "括号"),
            )

            title.containsAny(
                "代数式",
                "整式",
                "单项式",
                "多项式",
                "同类项",
                "去括号",
                "添括号",
                "因式分解",
                "分式",
                "乘法公式",
            ) -> themed(
                lesson,
                summary = "$title 研究表达式的结构与等价变形；形式可以改变，但相同取值下结果必须保持一致。",
                prompt = "怎样改变表达式的写法，同时确保它表示的数量关系没有改变？",
                expression = "2(x+3)=2x+6",
                conclusion = "先看项、因式和括号结构，再使用运算律或恒等变形，并通过代入复核。",
                steps = listOf("识别表达式结构", "选择运算律", "处理符号与括号", "整理同类结构", "代入检验"),
                misconception = "不同字母部分或不同次数的项不能直接合并，去括号时也不能漏掉任何一项。",
                question = "为什么等价变形后可以用代入同一个数来进行初步检查？",
                answers = listOf("值相等", "等价", "相同取值"),
            )

            title.containsAny("导数", "变化率", "极值") -> derivativeAnalysis(lesson)

            title.containsAny("单调性", "最大（小）值", "最大值", "最小值", "最值") ->
                themed(
                    lesson,
                    summary = "$title 描述函数在某个区间上的整体变化趋势或边界特征。",
                    prompt = "怎样通过自变量的变化趋势来判断函数值的增减性或最值？",
                    expression = "x₁<x₂ ⇒ f(x₁)<f(x₂)",
                    conclusion = "单调性是区间上的性质；最值则必须在定义域或指定区间内能够实际取到。",
                    steps = listOf("确定研究区间", "任取两个自变量", "比较函数值", "得出单调性", "结合端点或图象判断最值"),
                    misconception = "单调性必须在特定区间内讨论；最值也不能只看图象趋势而忽略定义域和端点。",
                    question = "用定义证明函数单调性时，为什么要先限定研究区间？",
                    answers = listOf("区间", "定义域", "单调性是区间性质"),
                )

            title.containsAny("函数", "映射") -> functionAnalysis(lesson)

            title.containsAny("一元二次方程", "配方法", "公式法", "根与系数") ->
                themed(
                    lesson,
                    summary = "$title 围绕一元二次方程的标准形式、根以及不同求解方法建立联系。",
                    prompt = "怎样根据方程结构选择配方法、公式法或因式分解法？",
                    expression = "ax²+bx+c=0",
                    conclusion = "先整理为标准形式，再选择方法；求出的每一个候选根都要回到原方程检查。",
                    steps = listOf("整理标准形式", "识别系数与结构", "选择求根方法", "得到候选根", "代回检验"),
                    misconception = "开平方或因式分解时漏掉一个根，以及公式中符号代错，是最常见的问题。",
                    question = "解方程x²-5x+6=0。",
                    answers = listOf("x=2", "x=3", "2,3"),
                )

            title.containsAny("一元一次方程", "二元一次方程", "分式方程", "方程的解", "方程组") ->
                themed(
                    lesson,
                    summary = "$title 的核心是保持等式平衡，通过等价变形逐步分离未知量。",
                    prompt = "移项为什么不能理解成凭空换符号？",
                    expression = "2x+3=9 → 2x=6 → x=3",
                    conclusion = "每一步都必须保持解集不变，得到答案后还应代回原方程检验。",
                    steps = listOf("整理方程", "两边执行同一运算", "分离未知量", "写出解", "代回检验"),
                    misconception = "移项只是等式两边同时加减同一项的简写；跳过平衡关系容易把符号改错。",
                    question = "解方程2x+3=9。",
                    answers = listOf("x=3", "3"),
                )

            title.containsAny("集合", "充分条件", "必要条件", "充要条件", "量词", "命题") ->
                themed(
                    lesson,
                    type = LessonSceneType.TEXT,
                    summary = "$title 把数学对象及其条件关系组织成明确、可检验的语言结构。",
                    prompt = "对象属于集合、集合包含于集合，以及条件推出结论，分别表达什么关系？",
                    expression = "x∈A, A⊆B, p⇒q",
                    conclusion = "先明确对象层级和推理方向，再判断属于、包含、充分、必要或等价。",
                    steps = listOf("确定对象", "写出条件", "辨别关系符号", "检查推理方向", "寻找反例"),
                    misconception = "把属于与包含混用，或只验证一个方向就判断充要条件，会让结论失真。",
                    question = "为什么判断充要条件需要分别检查两个推导方向？",
                    answers = listOf("充分", "必要", "两个方向"),
                )

            title.containsAny("不等式", "基本不等式") ->
                themed(
                    lesson,
                    type = LessonSceneType.COMPARISON,
                    summary = "$title 通过保持或控制大小关系，把条件转化为解集、范围或最值。",
                    prompt = "哪些变形保持不等号方向，哪些变形会让方向改变？",
                    expression = "a²+b²≥2ab",
                    conclusion = "每一步都要检查变量符号、定义域与等价性；求最值还要说明等号成立条件。",
                    steps = listOf("确定定义域", "整理大小关系", "执行同向变形", "得到范围", "检查等号条件"),
                    misconception = "忽略变量正负、定义域或等号成立条件，可能得到无法实际达到的结果。",
                    question = "使用基本不等式求最值时，还必须检查什么？",
                    answers = listOf("等号成立条件", "定义域"),
                )

            title.containsAny("平方根", "立方根", "二次根式", "根式", "方根") ->
                themed(
                    lesson,
                    summary = "$title 把乘方运算反向理解，并在有意义条件允许的范围内进行化简和运算。",
                    prompt = "根号内的量、根指数和结果符号分别受到什么限制？",
                    expression = "√(a²)=|a|",
                    conclusion = "先检查根式有意义条件，再使用性质化简；平方根与算术平方根不能混淆。",
                    steps = listOf("检查定义域", "识别根指数", "化简被开方数", "完成运算", "平方复核"),
                    misconception = "把±√a当作算术平方根，或忽略根号内非负条件，是常见错误。",
                    question = "为什么√(a²)通常写成|a|？",
                    answers = listOf("绝对值", "a可能为负"),
                )

            title.containsAny("椭圆", "双曲线", "抛物线", "圆的方程", "直线的方程") ->
                themed(
                    lesson,
                    summary = "$title 把几何轨迹转化为坐标方程，再从参数读取位置、形状和性质。",
                    prompt = "一个点满足怎样的距离或斜率关系，才能落在对应曲线上？",
                    expression = "几何条件 ⇄ 坐标方程",
                    conclusion = "解析几何的核心是几何条件、坐标表达、代数方程与几何解释之间的往返转换。",
                    steps = listOf("建立坐标系", "翻译几何条件", "化为标准方程", "读取参数", "用特殊点核对"),
                    misconception = "只背标准方程而不理解参数与图形的联系，容易在平移和范围判断中出错。",
                    question = "标准方程中的参数应怎样联系到图形？",
                    answers = listOf("位置", "形状", "焦点", "坐标"),
                )

            title.containsAny("坐标系", "坐标", "平移") ->
                themed(
                    lesson,
                    summary = "$title 把几何位置转化为有顺序的数值描述，位置变化可以通过坐标增量追踪。",
                    prompt = "点的位置变化怎样分别对应横坐标和纵坐标的变化？",
                    expression = "(x,y)→(x+a,y+b)",
                    conclusion = "坐标顺序固定；平移时每个点使用同一个位移，图形形状和大小保持不变。",
                    steps = listOf("建立坐标轴", "读取有序坐标", "确定位移", "更新坐标", "核对图形关系"),
                    misconception = "交换横纵坐标、忽略正负方向，或只移动部分点，会破坏图形关系。",
                    question = "点(x,y)向右平移a个单位后的坐标是什么？",
                    answers = listOf("(x+a,y)", "x+a"),
                )

            title.containsAny("直线", "平行线", "垂直", "相交线", "截线", "斜率") ->
                themed(
                    lesson,
                    summary = "$title 通过角、方向或斜率描述直线间的位置关系，并用判定与性质建立推理。",
                    prompt = "已知角或斜率时，怎样判断两条直线平行、垂直或相交？",
                    expression = "m=(y₂-y₁)/(x₂-x₁)",
                    conclusion = "判定用于从条件推出关系，性质用于从关系推出结论，两者方向不能混用。",
                    steps = listOf("标出直线与角", "识别已知条件", "选择判定或性质", "写出推理链", "检查逆向是否成立"),
                    misconception = "把性质当成判定条件，或忽略斜率不存在的特殊情况，会造成错误推理。",
                    question = "为什么几何推理中要区分判定和性质？",
                    answers = listOf("推理方向", "条件和结论"),
                )

            title.containsAny("三角形", "全等", "相似", "角的平分线") ->
                themed(
                    lesson,
                    summary = "$title 通过边、角和对应关系建立几何结论，关键是先找准对应元素。",
                    prompt = "哪些边和角互相对应，现有条件足以推出什么关系？",
                    expression = "△ABC≌△DEF",
                    conclusion = "对应顺序必须一致，每一步都要说明使用的定义、判定、性质或已知条件。",
                    steps = listOf("标记已知边角", "确定对应顶点", "选择判定", "写出对应关系", "推出结论"),
                    misconception = "只看到数值相等就随意对应顶点，或使用不足条件判定，会让证明整体错位。",
                    question = "书写三角形全等或相似时，为什么对应顶点顺序不能乱？",
                    answers = listOf("对应关系", "对应顶点"),
                )

            title.contains("勾股") ->
                themed(
                    lesson,
                    summary = "勾股定理把直角三角形的边长关系转化为平方关系，逆定理则利用边长判断直角。",
                    prompt = "已知三条边时，怎样判断哪一条应作为斜边？",
                    expression = "a²+b²=c²",
                    conclusion = "直角三角形中两直角边平方和等于斜边平方；使用逆定理时先确定最大边。",
                    steps = listOf("确定直角或最大边", "标记三边", "写出平方关系", "代入计算", "检查几何条件"),
                    misconception = "未确认斜边就套公式，或混淆定理与逆定理的条件方向，是常见错误。",
                    question = "边长3、4、5能组成直角三角形吗？",
                    answers = listOf("能", "3²+4²=5²"),
                )

            title.containsAny("四边形", "平行四边形", "矩形", "菱形", "正方形", "多边形") ->
                themed(
                    lesson,
                    summary = "$title 通过边、角、对角线和平行关系建立分类、性质与判定。",
                    prompt = "一个四边形满足哪些条件时，才能确定属于某种特殊四边形？",
                    expression = "正方形⊂矩形∩菱形",
                    conclusion = "特殊四边形的性质存在包含关系，判定时必须使用足够条件，不能只凭外观。",
                    steps = listOf("标出边角", "检查平行关系", "检查对角线", "选择判定", "整理性质包含关系"),
                    misconception = "看到一组边相等或一个直角就直接判定特殊四边形，通常条件不足。",
                    question = "为什么有一个直角的平行四边形可以判定为矩形？",
                    answers = listOf("平行四边形", "一个直角"),
                )

            title.containsAny("轴对称", "中心对称", "旋转", "位似") ->
                themed(
                    lesson,
                    type = LessonSceneType.MIRROR,
                    summary = "$title 研究图形在特定变换下的对应关系以及保持不变或按比例变化的量。",
                    prompt = "变换前后的对应点、距离、角度和方向有哪些关系？",
                    expression = "P→P′",
                    conclusion = "先确定变换中心、轴、角度或比例，再追踪关键点并验证对应关系。",
                    steps = listOf("确定变换要素", "选择关键点", "作对应点", "检查距离角度", "验证对应关系"),
                    misconception = "只凭视觉判断而不验证对应点与变换要素，容易得到看似相似但不正确的图形。",
                    question = "描述一次几何变换至少需要指出什么？",
                    answers = listOf("对称轴", "中心", "旋转角", "比例", "变换要素"),
                )

            title.containsAny("圆", "弧", "弦", "切线", "圆周角") ->
                themed(
                    lesson,
                    summary = "$title 围绕圆心、半径、弦、弧和角之间的关系建立几何推理。",
                    prompt = "同一个圆中，长度、弧和角度怎样相互决定？",
                    expression = "圆周角=1/2圆心角",
                    conclusion = "先识别圆心和半径，再利用垂径、圆周角或切线关系建立辅助线。",
                    steps = listOf("标出圆心半径", "识别弦弧角", "连接关键半径", "选择定理", "完成推导"),
                    misconception = "忽略同圆或等圆等前提，或未连接圆心就直接套性质，容易误用定理。",
                    question = "证明一条直线是圆的切线时，常见辅助线是什么？",
                    answers = listOf("连接圆心", "半径"),
                )

            title.contains("向量") ->
                themed(
                    lesson,
                    summary = "$title 把大小与方向统一编码，几何关系可以转化为向量或坐标运算。",
                    prompt = "向量的起点终点改变后，哪些信息仍然保持？",
                    expression = "a+b=c",
                    conclusion = "自由向量只由大小和方向决定；运算时要区分向量、数量和坐标表示。",
                    steps = listOf("识别方向长度", "选择几何或坐标表示", "执行运算", "解释几何意义", "检查方向维数"),
                    misconception = "把向量当普通数比较大小，或坐标计算后忽略方向意义，是常见错误。",
                    question = "两个向量相等需要满足什么条件？",
                    answers = listOf("大小相等", "方向相同"),
                )

            title.containsAny("复数", "虚数") ->
                themed(
                    lesson,
                    summary = "$title 把实数轴扩展为复平面，并通过实部、虚部和代数运算描述新的数系。",
                    prompt = "两个复数相等时，实部和虚部分别应满足什么关系？",
                    expression = "z=a+bi, i²=-1",
                    conclusion = "复数运算遵循代数规则并使用i²=-1化简，也可以用复平面理解其几何意义。",
                    steps = listOf("分离实部虚部", "执行代数运算", "用i²=-1化简", "比较实虚部", "复平面核对"),
                    misconception = "把i当作普通实数，或只比较模就判断复数相等，会得到错误结论。",
                    question = "a+bi与c+di相等需要满足什么？",
                    answers = listOf("a=c", "b=d", "实部相等", "虚部相等"),
                )

            title.containsAny("数列", "等差", "等比", "前n项和", "数学归纳法") ->
                themed(
                    lesson,
                    summary = "$title 研究按顺序排列的数及其递推、通项和求和结构。",
                    prompt = "怎样从相邻项关系识别规律，并推广到任意n？",
                    expression = "aₙ=a₁+(n-1)d",
                    conclusion = "要区分递推关系、通项公式与前n项和，并检查公式中n的取值范围。",
                    steps = listOf("观察相邻项", "提出规律", "验证初始项", "推导一般式", "检查取值范围"),
                    misconception = "用少数几项直接断言对所有项成立，或混淆aₙ与Sₙ，是常见错误。",
                    question = "通项公式和前n项和分别描述什么？",
                    answers = listOf("第n项", "前n项和"),
                )

            title.containsAny("排列", "组合", "计数原理", "二项式") ->
                themed(
                    lesson,
                    summary = "$title 把复杂选择拆成互斥分类或连续步骤，并区分是否关注顺序。",
                    prompt = "一个计数问题应该相加、相乘，还是使用排列或组合？",
                    expression = "C(n,k)=n!/[k!(n-k)!]",
                    conclusion = "互斥方案用加法，连续步骤用乘法；关注顺序用排列，不关注顺序用组合。",
                    steps = listOf("明确结果如何形成", "判断是否互斥", "判断是否连续", "判断是否关注顺序", "检查重复遗漏"),
                    misconception = "混淆分类与分步，或未判断顺序就套公式，会重复计数或漏计。",
                    question = "从5人选2人与安排2人的先后顺序，为什么方法不同？",
                    answers = listOf("顺序", "组合", "排列"),
                )

            title.containsAny("概率", "随机", "事件", "分布列", "期望", "独立性", "正态分布", "二项分布", "超几何分布") ->
                themed(
                    lesson,
                    summary = "$title 用样本空间、事件和频率描述随机现象，并区分条件、独立与分布。",
                    prompt = "一次随机试验的所有可能结果怎样组织，目标事件包含哪些结果？",
                    expression = "0≤P(A)≤1",
                    conclusion = "先明确样本空间和事件；条件概率改变参照范围，独立性描述事件是否相互影响。",
                    steps = listOf("确定随机试验", "列出样本空间", "定义事件", "选择概率模型", "检查概率范围"),
                    misconception = "把互斥当作独立，或在条件概率中继续使用原样本空间，是常见错误。",
                    question = "互斥事件与相互独立事件有什么区别？",
                    answers = listOf("不能同时发生", "是否影响", "互斥", "独立"),
                )

            title.containsAny(
                "统计",
                "抽样",
                "频率",
                "平均数",
                "中位数",
                "标准差",
                "直方图",
                "散点图",
                "回归",
                "列联表",
                "数据",
                "百分位数",
                "相关系数",
            ) -> themed(
                lesson,
                summary = "$title 把原始数据整理成能够比较、估计或预测的信息。",
                prompt = "选择哪一种统计量或图表，才能回答问题而不掩盖数据差异？",
                expression = "x̄=(x₁+…+xₙ)/n",
                conclusion = "统计结论依赖样本来源、指标选择和图表尺度；中心位置与离散程度应结合解释。",
                steps = listOf("明确总体样本", "整理变量类型", "选择统计量", "解释结果", "检查偏差异常值"),
                misconception = "只看平均数而忽略离散程度、样本代表性或异常值，可能产生误导。",
                question = "为什么比较两组数据时不能只看平均数？",
                answers = listOf("离散程度", "方差", "波动"),
            )

            title.containsAny("空间", "立体", "三视图", "投影") ->
                themed(
                    lesson,
                    summary = "$title 在二维表示和三维对象之间建立对应，关键是明确观察方向和点线面关系。",
                    prompt = "同一个立体从不同方向观察时，哪些轮廓改变，哪些关系保持？",
                    conclusion = "先建立空间位置关系，再用投影、坐标或向量把问题降维处理，最后回到空间解释。",
                    steps = listOf("识别空间对象", "确定观察方向", "标出点线面关系", "转化为平面问题", "回到空间验证"),
                    misconception = "只凭平面图直觉判断空间关系，可能把看似相交的线误认为实际相交。",
                    question = "处理空间问题时，为什么需要先说明观察方向或位置关系？",
                    answers = listOf("观察方向", "位置关系"),
                )

            else -> base.copy(
                summary = "本节把“$title”整理成对象、条件、变化和结论，学习时可以沿着步骤逐项核对教材原页。",
                source = LessonAnalysisSource.PACK,
                scene = base.scene.copy(
                    title = title,
                    prompt = "在“$title”中，哪些条件一步一步导向结论？",
                    conclusion = "先识别对象与条件，再选择对应定义或性质，最后用例题和反例核对。",
                    steps = listOf("定位教材原页", "找出核心对象", "标记条件", "建立推理或运算过程", "用例题验证"),
                ),
            )
        }
    }

    private fun derivativeAnalysis(lesson: GeneratedLesson): LessonAnalysis = themed(
        lesson,
        summary = "${lesson.title}用局部变化率描述函数变化，并把导数符号与单调性、极值联系起来。",
        prompt = "平均变化率怎样在区间缩小时逼近瞬时变化率？",
        expression = "f′(x)=lim Δx→0 [f(x+Δx)-f(x)]/Δx",
        conclusion = "导数来自差商极限；判断函数性质时还要结合定义域、临界点、不可导点和端点。",
        steps = listOf("写出差商", "令增量趋近0", "得到导数", "分析导数符号", "结合区间端点"),
        misconception = "只解f′(x)=0就直接认定极值，忽略导数变号和端点，是常见错误。",
        question = "为什么f′(x)=0只是极值候选条件？",
        answers = listOf("导数变号", "还要检查", "候选"),
    )

    private fun functionAnalysis(lesson: GeneratedLesson): LessonAnalysis {
        val title = lesson.title
        val expression = when {
            title.contains("二次函数") -> "y=ax²+bx+c"
            title.contains("反比例函数") -> "y=k/x"
            title.contains("一次函数") -> "y=kx+b"
            title.contains("指数函数") -> "y=aˣ"
            title.contains("对数函数") -> "y=logₐx"
            title.containsAny("三角函数", "正弦", "余弦") -> "y=sin x"
            else -> "y=f(x)"
        }
        return themed(
            lesson,
            summary = "$title 研究输入与输出之间的对应关系，解析式、表格和图象是同一关系的不同表达。",
            prompt = "当自变量变化时，函数值和图象上的位置怎样响应？",
            expression = expression,
            conclusion = "理解函数必须同时关注定义域、对应法则、图象和关键性质，不能只背公式。",
            steps = listOf("确定定义域", "识别对应法则", "选择关键自变量", "观察函数值", "用图象验证性质"),
            misconception = "忽略定义域，或把图象形状与参数作用割裂，容易误判单调性、最值和交点。",
            question = "研究${title}时，为什么必须先确定定义域？",
            answers = listOf("定义域", "自变量范围"),
        )
    }

    private fun themed(
        lesson: GeneratedLesson,
        summary: String,
        prompt: String,
        conclusion: String,
        steps: List<String>,
        misconception: String,
        question: String,
        answers: List<String>,
        expression: String = "",
        type: LessonSceneType = LessonSceneType.PROCESS,
    ): LessonAnalysis = LessonAnalysis(
        lessonSourceId = lesson.sourceId,
        summary = summary,
        objectives = listOf(
            "理解${lesson.title}的核心对象与条件",
            "能按顺序说明关键关系或运算过程",
            "能用定义、性质、例题或反例检查结论",
        ),
        misconception = misconception,
        sourcePages = lesson.pageStart..lesson.pageEnd,
        scene = LessonSceneSpec(
            type = type,
            title = lesson.title,
            prompt = prompt,
            expression = expression,
            conclusion = conclusion,
            steps = steps,
            sourcePage = lesson.pageStart,
        ),
        exercise = GeneratedExercise(
            question = question,
            acceptedAnswers = answers,
            hints = listOf("先确定研究对象和已知条件。", "再核对教材原页中的定义、性质或例题。"),
            explanation = conclusion,
        ),
        source = LessonAnalysisSource.PACK,
    )

    private fun String.containsAny(vararg values: String): Boolean = values.any(::contains)
}
