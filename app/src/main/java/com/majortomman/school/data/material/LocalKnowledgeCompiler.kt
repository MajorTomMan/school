package com.majortomman.school.data.material

internal enum class KnowledgeCategory {
    DEFINITION,
    PROPERTY,
    METHOD,
}

internal data class KnowledgeEvidence(
    val page: Int,
    val text: String,
)

internal data class ExtractedKnowledgePoint(
    val id: String,
    val title: String,
    val category: KnowledgeCategory,
    val definition: String,
    val conditions: List<String>,
    val properties: List<String>,
    val confidence: Float,
    val evidence: List<KnowledgeEvidence>,
)

internal data class LocalKnowledgeCompilation(
    val primary: ExtractedKnowledgePoint,
    val related: List<ExtractedKnowledgePoint>,
    val analysis: LessonAnalysis,
)

internal object LocalKnowledgeCompiler {
    private const val MIN_CONFIDENCE = 0.52f

    fun compile(
        slot: TextbookSlot,
        lesson: GeneratedLesson,
        pages: List<OcrPageResult>,
    ): LocalKnowledgeCompilation? {
        if (slot.subjectId != "math") return compileGenericDefinition(lesson, pages)
        val lines = pages.flatMap { page ->
            page.lines.mapNotNull { line ->
                line.text.trim().takeIf(String::isNotBlank)?.let { KnowledgeEvidence(page.printedPage, it) }
            }
        }
        val corpus = (lesson.title + "\n" + lines.joinToString("\n") { it.text })
            .replace(Regex("\\s+"), " ")
            .trim()
        if (corpus.isBlank()) return null

        val candidates = rules.mapNotNull { rule ->
            val confidence = score(rule, lesson.title, corpus, lines)
            if (confidence < MIN_CONFIDENCE) null else rule.toPoint(confidence, lines, lesson.pageStart)
        }.sortedByDescending { it.confidence }
        val primary = candidates.firstOrNull() ?: return compileGenericDefinition(lesson, pages)
        val related = candidates.drop(1)
            .filter { it.confidence >= primary.confidence - 0.16f }
            .take(3)
        return LocalKnowledgeCompilation(primary, related, buildAnalysis(lesson, primary, related))
    }

    private fun score(
        rule: Rule,
        lessonTitle: String,
        corpus: String,
        lines: List<KnowledgeEvidence>,
    ): Float {
        var score = 0f
        if (rule.titleKeywords.any { it in lessonTitle }) score += 0.34f
        score += (rule.keywords.count { it in corpus } * 0.09f).coerceAtMost(0.36f)
        if (rule.required.isNotEmpty() && rule.required.all { it in corpus }) score += 0.16f
        if (lines.any { line ->
                definitionPatterns.any { it.containsMatchIn(line.text) } &&
                    rule.keywords.any { it in line.text }
            }
        ) {
            score += 0.12f
        }
        if (rule.formulas.any { it.containsMatchIn(corpus) }) score += 0.10f
        return score.coerceAtMost(0.98f)
    }

    private fun Rule.toPoint(
        confidence: Float,
        lines: List<KnowledgeEvidence>,
        fallbackPage: Int,
    ): ExtractedKnowledgePoint {
        val evidence = lines
            .filter { line -> keywords.any { it in line.text } || formulas.any { it.containsMatchIn(line.text) } }
            .sortedByDescending { line -> keywords.count { it in line.text } }
            .distinctBy { it.page to it.text }
            .take(4)
            .ifEmpty { listOf(KnowledgeEvidence(fallbackPage, definition)) }
        val extractedDefinition = evidence.firstOrNull { item ->
            definitionPatterns.any { it.containsMatchIn(item.text) }
        }?.text ?: definition
        return ExtractedKnowledgePoint(
            id = id,
            title = title,
            category = category,
            definition = extractedDefinition,
            conditions = conditions,
            properties = properties,
            confidence = confidence,
            evidence = evidence,
        )
    }

    private fun buildAnalysis(
        lesson: GeneratedLesson,
        point: ExtractedKnowledgePoint,
        related: List<ExtractedKnowledgePoint>,
    ): LessonAnalysis {
        val sourcePage = point.evidence.firstOrNull()?.page
            ?.coerceIn(lesson.pageStart, lesson.pageEnd)
            ?: lesson.pageStart
        val scene = sceneFor(point.id, sourcePage)
        val relatedText = related.joinToString("、") { it.title }
        val summary = buildString {
            append(point.definition)
            if (point.conditions.isNotEmpty()) {
                append(" 关键条件是：")
                append(point.conditions.joinToString("、"))
                append("。")
            }
            if (relatedText.isNotBlank()) append(" 本节还关联：$relatedText。")
        }.trim()
        val objectives = buildList {
            add("理解${point.title}的核心含义")
            point.conditions.take(2).forEach { condition ->
                add("能说明${condition}为什么不可缺少")
            }
            point.properties.firstOrNull()?.let { property ->
                add("能使用${property}解决基础问题")
            }
            add("能回到教材第 $sourcePage 页核对定义与例题")
        }.distinct().take(5)
        return LessonAnalysis(
            lessonSourceId = lesson.sourceId,
            summary = summary,
            objectives = objectives,
            misconception = misconceptionFor(point.id),
            sourcePages = lesson.pageStart..lesson.pageEnd,
            scene = scene,
            exercise = exerciseFor(point.id, scene),
            source = LessonAnalysisSource.OCR_FALLBACK,
        )
    }

    private fun compileGenericDefinition(
        lesson: GeneratedLesson,
        pages: List<OcrPageResult>,
    ): LocalKnowledgeCompilation? {
        val evidence = pages.asSequence()
            .flatMap { page -> page.lines.asSequence().map { page.printedPage to it.text.trim() } }
            .firstOrNull { (_, text) ->
                text.length in 12..220 && definitionPatterns.any { it.containsMatchIn(text) }
            } ?: return null
        val title = extractDefinedTerm(evidence.second) ?: lesson.title
        val point = ExtractedKnowledgePoint(
            id = "local-definition-${title.hashCode().toUInt().toString(16)}",
            title = title,
            category = KnowledgeCategory.DEFINITION,
            definition = evidence.second,
            conditions = emptyList(),
            properties = emptyList(),
            confidence = 0.58f,
            evidence = listOf(KnowledgeEvidence(evidence.first, evidence.second)),
        )
        val sourcePage = evidence.first.coerceIn(lesson.pageStart, lesson.pageEnd)
        val analysis = LessonAnalysis(
            lessonSourceId = lesson.sourceId,
            summary = "本节首先明确“$title”的定义，再结合教材例题观察它如何被使用。",
            objectives = listOf(
                "能用自己的话说明${title}的含义",
                "能从教材中找到定义成立的条件",
                "能区分定义、例子与结论",
            ),
            misconception = "不要把某一个例子当成完整定义；定义必须保留教材给出的全部条件。",
            sourcePages = lesson.pageStart..lesson.pageEnd,
            scene = LessonSceneSpec(
                type = LessonSceneType.PROCESS,
                title = "从定义到应用",
                prompt = "教材用哪些条件定义了$title？",
                conclusion = evidence.second,
                steps = listOf("找到被定义的对象", "标出必要条件", "观察例题如何使用定义", "用自己的话复述"),
                sourcePage = sourcePage,
            ),
            exercise = GeneratedExercise(
                question = "请根据教材说明${title}的含义，并写出至少一个必要条件。",
                acceptedAnswers = listOf(title),
                hints = listOf("先找到“叫做、称为、是指”等词。", "定义前面的条件不能遗漏。"),
                explanation = evidence.second,
            ),
            source = LessonAnalysisSource.OCR_FALLBACK,
        )
        return LocalKnowledgeCompilation(point, emptyList(), analysis)
    }

    private fun sceneFor(id: String, page: Int): LessonSceneSpec = when (id) {
        "number-line" -> LessonSceneSpec(
            type = LessonSceneType.NUMBER_LINE,
            title = "把数变成位置",
            prompt = "一条直线怎样才能真正表示数字？",
            values = listOf(-3.0, 0.0, 2.0),
            labels = listOf("-3", "0", "2"),
            expression = "-3 < 0 < 2",
            conclusion = "原点、正方向和单位长度共同确定数在直线上的位置。",
            steps = listOf("画出直线", "标出原点", "确定正方向", "按统一单位长度标刻度", "放置数字"),
            sourcePage = page,
        )
        "opposite-number" -> LessonSceneSpec(
            type = LessonSceneType.MIRROR,
            title = "关于原点的镜像",
            prompt = "到原点距离相同、方向相反的两个位置有什么关系？",
            values = listOf(-4.0, 4.0),
            labels = listOf("-4", "4"),
            expression = "-4 + 4 = 0",
            conclusion = "关于原点对称的两个数互为相反数，它们的和为 0。",
            steps = listOf("找到原点", "向右移动 4 格", "向左移动 4 格", "比较距离", "观察两数之和"),
            sourcePage = page,
        )
        "absolute-value" -> LessonSceneSpec(
            type = LessonSceneType.DISTANCE,
            title = "只看距离，不看方向",
            prompt = "-5 和 5 到原点的距离分别是多少？",
            values = listOf(-5.0, 0.0, 5.0),
            labels = listOf("-5", "0", "5"),
            expression = "|-5| = |5| = 5",
            conclusion = "绝对值表示到原点的距离，因此绝对值不会是负数。",
            steps = listOf("放置两个点", "连接到原点", "忽略左右方向", "读取距离", "写成绝对值"),
            sourcePage = page,
        )
        "rational-compare" -> LessonSceneSpec(
            type = LessonSceneType.COMPARISON,
            title = "位置决定大小",
            prompt = "-8 和 -3 都是负数，为什么 -3 更大？",
            values = listOf(-8.0, -3.0),
            labels = listOf("-8", "-3"),
            expression = "-8 < -3",
            conclusion = "数轴上越靠右的数越大；两个负数中，绝对值较大的反而较小。",
            steps = listOf("判断正负", "放到数轴", "比较左右位置", "写出大小关系"),
            sourcePage = page,
        )
        "positive-negative" -> LessonSceneSpec(
            type = LessonSceneType.COMPARISON,
            title = "以 0 为分界",
            prompt = "温度升高 3℃ 和下降 3℃ 应该怎样区分？",
            values = listOf(-3.0, 0.0, 3.0),
            labels = listOf("下降 3", "0", "升高 3"),
            expression = "-3 < 0 < 3",
            conclusion = "正负号用来区分相反意义的量，0 是它们的分界。",
            steps = listOf("确定基准 0", "识别两种相反方向", "规定正方向", "用正负号记录"),
            sourcePage = page,
        )
        "expression-transform" -> processScene(
            page = page,
            title = "保持式子的值不变",
            prompt = "去括号和合并同类项时，什么必须保持不变？",
            expression = "2(x + 3) = 2x + 6",
            conclusion = "等价变形改变写法，但不能改变表达式在相同取值下的值。",
            steps = listOf("识别运算结构", "按分配律去括号", "检查每一项的符号", "合并同类项", "代入数值复核"),
        )
        "linear-equation" -> processScene(
            page = page,
            title = "保持等式两边平衡",
            prompt = "解方程时，为什么等式两边必须做相同操作？",
            expression = "2x + 3 = 9 → 2x = 6 → x = 3",
            conclusion = "每一步都要保持方程的解不变，最终把未知数单独留下。",
            steps = listOf("确定未知数和常数项", "两边同时消去常数", "两边同除以系数", "代回原方程验证"),
        )
        else -> processScene(
            page = page,
            title = "从定义到结论",
            prompt = "哪些条件共同导向教材中的结论？",
            conclusion = "先识别对象与条件，再按照教材顺序建立关系。",
            steps = listOf("找到核心对象", "提取必要条件", "观察例题", "形成结论"),
        )
    }

    private fun processScene(
        page: Int,
        title: String,
        prompt: String,
        expression: String = "",
        conclusion: String,
        steps: List<String>,
    ) = LessonSceneSpec(
        type = LessonSceneType.PROCESS,
        title = title,
        prompt = prompt,
        expression = expression,
        conclusion = conclusion,
        steps = steps,
        sourcePage = page,
    )

    private fun exerciseFor(id: String, scene: LessonSceneSpec): GeneratedExercise = when (id) {
        "number-line" -> exercise(
            "一条直线有箭头和刻度，但没有原点。它是完整的数轴吗？为什么？",
            listOf("不是", "没有原点", "缺少原点"), scene,
        )
        "opposite-number" -> exercise(
            "写出 -4 的相反数，并说明两者在数轴上的位置关系。",
            listOf("4", "关于原点对称", "到原点距离相等"), scene,
        )
        "absolute-value" -> exercise(
            "|-5| 等于多少？为什么结果不是 -5？",
            listOf("5", "距离不能是负数", "到原点距离是5"), scene,
        )
        "rational-compare" -> exercise(
            "比较 -8 与 -3 的大小，并用数轴位置说明理由。",
            listOf("-8 < -3", "-3更大", "-3在右边"), scene,
        )
        "positive-negative" -> GeneratedExercise(
            question = "若收入 20 元记作 +20，支出 20 元应记作什么？",
            acceptedAnswers = listOf("-20", "负20"),
            hints = listOf("收入和支出是相反意义的量。", "已经规定收入为正。"),
            explanation = "支出与收入方向相反，因此记作 -20。",
        )
        "expression-transform" -> exercise(
            "化简 2(x+3)，并说明使用了什么运算律。",
            listOf("2x+6", "分配律"), scene,
        )
        "linear-equation" -> exercise(
            "解方程 2x+3=9，并写出关键步骤。",
            listOf("x=3", "2x=6"), scene,
        )
        else -> exercise("请说明本节核心定义及其中不能遗漏的条件。", emptyList(), scene)
    }

    private fun exercise(
        question: String,
        answers: List<String>,
        scene: LessonSceneSpec,
    ) = GeneratedExercise(
        question = question,
        acceptedAnswers = answers,
        hints = scene.steps.take(3),
        explanation = scene.conclusion,
    )

    private fun misconceptionFor(id: String): String = when (id) {
        "number-line" -> "只有直线和箭头还不够；没有原点或统一单位长度，就不能确定每个数的位置。"
        "opposite-number" -> "相反数不是倒数。相反数的和为 0，倒数的乘积为 1。"
        "absolute-value" -> "绝对值保留的是距离，不是原来的正负号；距离不能为负。"
        "rational-compare" -> "比较负数时不能只看数字部分；应先回到数轴位置或比较绝对值。"
        "positive-negative" -> "正负号必须依赖事先规定的基准和方向，不能脱离语境单独解释。"
        "expression-transform" -> "去括号时括号外的系数要乘括号内每一项，负号尤其不能遗漏。"
        "linear-equation" -> "移项不是凭空换符号；本质是等式两边同时进行相同的加减操作。"
        else -> "不要把一个例子当成完整定义，必须保留教材中的全部条件。"
    }

    private fun extractDefinedTerm(text: String): String? {
        val compact = text.replace(Regex("\\s+"), " ").trim()
        return Regex("(?:叫做|称为|是指)([^，。；：]{2,24})")
            .find(compact)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("([^，。；：]{2,24})(?:是|叫做|称为)")
                .find(compact)?.groupValues?.getOrNull(1)?.trim()
    }

    private data class Rule(
        val id: String,
        val title: String,
        val category: KnowledgeCategory,
        val titleKeywords: List<String>,
        val keywords: List<String>,
        val required: List<String>,
        val formulas: List<Regex>,
        val definition: String,
        val conditions: List<String>,
        val properties: List<String>,
    )

    private val rules = listOf(
        Rule(
            "number-line", "数轴", KnowledgeCategory.DEFINITION,
            listOf("数轴"), listOf("数轴", "原点", "正方向", "单位长度", "直线"),
            listOf("原点", "正方向", "单位长度"), listOf(Regex("-?\\d+\\s*[<>]\\s*-?\\d+")),
            "规定了原点、正方向和单位长度的直线叫做数轴。",
            listOf("有原点", "有明确的正方向", "刻度使用统一的单位长度"),
            listOf("数轴上的点可以表示数", "右边的数大于左边的数"),
        ),
        Rule(
            "opposite-number", "相反数", KnowledgeCategory.DEFINITION,
            listOf("相反数", "相反"), listOf("相反数", "互为相反数", "原点对称", "距离相等", "和为0", "和为 0"),
            emptyList(), listOf(Regex("-?\\d+\\s*\\+\\s*-?\\d+\\s*=\\s*0")),
            "只有符号不同的两个数互为相反数，它们在数轴上关于原点对称。",
            listOf("到原点的距离相等", "位于原点两侧或都为 0"),
            listOf("互为相反数的两个数之和为 0"),
        ),
        Rule(
            "absolute-value", "绝对值", KnowledgeCategory.DEFINITION,
            listOf("绝对值"), listOf("绝对值", "到原点的距离", "距离", "非负"),
            listOf("绝对值"), listOf(Regex("\\|[^|]{1,20}\\|"), Regex("≥\\s*0")),
            "一个数的绝对值表示这个数在数轴上对应的点到原点的距离。",
            listOf("先确定数在数轴上的位置", "只计算到原点的距离，不保留方向"),
            listOf("任意数的绝对值都大于或等于 0"),
        ),
        Rule(
            "rational-compare", "有理数大小比较", KnowledgeCategory.METHOD,
            listOf("大小比较", "比较"), listOf("比较大小", "大小比较", "大于", "小于", "越靠右", "从小到大", "从大到小"),
            emptyList(), listOf(Regex("-?\\d+\\s*[<>]\\s*-?\\d+")),
            "数轴上右边的数总比左边的数大。两个负数比较时，绝对值大的数反而小。",
            listOf("先判断正负", "必要时把数放到同一条数轴上"),
            listOf("正数大于 0，0 大于负数", "两个负数绝对值大的反而小"),
        ),
        Rule(
            "positive-negative", "正数和负数", KnowledgeCategory.DEFINITION,
            listOf("正数", "负数"), listOf("正数", "负数", "相反意义", "大于0", "小于0", "收入", "支出", "上升", "下降"),
            emptyList(), listOf(Regex("[+-]\\d+")),
            "正数和负数可以表示具有相反意义的量，0 是正负数之间的分界。",
            listOf("先确定基准", "规定其中一个方向为正方向"),
            listOf("正数大于 0，负数小于 0"),
        ),
        Rule(
            "expression-transform", "整式等价变形", KnowledgeCategory.METHOD,
            listOf("整式", "化简", "去括号", "合并同类项"), listOf("整式", "化简", "去括号", "合并同类项", "分配律", "同类项"),
            emptyList(), listOf(Regex("\\d*\\s*[a-zA-Z]\\s*[+-]"), Regex("\\([^)]{1,30}\\)")),
            "整式化简通过去括号和合并同类项改变写法，但保持表达式的值不变。",
            listOf("遵守运算顺序", "去括号时处理每一项", "只合并同类项"),
            listOf("等价变形前后在相同取值下结果相同"),
        ),
        Rule(
            "linear-equation", "一元一次方程", KnowledgeCategory.METHOD,
            listOf("一元一次方程", "解方程", "方程"), listOf("一元一次方程", "方程", "解方程", "移项", "等式两边", "未知数"),
            emptyList(), listOf(Regex("[a-zA-Z].*=.*\\d"), Regex("\\d+[a-zA-Z].*=")),
            "解一元一次方程的过程，是通过等价变形把未知数单独留下，同时保持方程的解不变。",
            listOf("等式两边进行相同的有效操作", "每一步都保持原方程的解"),
            listOf("得到结果后可以代回原方程检验"),
        ),
    )

    private val definitionPatterns = listOf(
        Regex("叫做"), Regex("称为"), Regex("是指"), Regex("定义为"), Regex("一般地"),
    )
}
