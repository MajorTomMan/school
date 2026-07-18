package com.majortomman.school.learning.course

import com.majortomman.school.data.Lesson
import com.majortomman.school.learning.capability.ConceptId
import com.majortomman.school.learning.capability.ContentOrigin
import com.majortomman.school.learning.capability.OperationId
import com.majortomman.school.learning.capability.WidgetType

enum class BiologyCourseCategory {
    CELL,
    METABOLISM,
    PLANT,
    HUMAN,
    GENETICS,
    REPRODUCTION,
    CLASSIFICATION,
    EVOLUTION,
    ECOLOGY,
    EXPERIMENT,
    GENERAL,
}

data class BiologyCourseContent(
    val category: BiologyCourseCategory,
    val subtitle: String,
    val formula: String,
    val sourceSummary: String,
    val steps: List<String>,
    val background: List<String>,
    val misconception: String,
    val enrichment: LessonEnrichment,
)

object BiologyCourseContentFactory {
    fun create(lesson: Lesson): BiologyCourseContent {
        val category = classify(lesson.title)
        val profile = profile(category)
        val pages = lesson.textbookPages
        val pageLabel = if (pages.first == pages.last) "第 ${pages.first} 页" else "第 ${pages.first}—${pages.last} 页"
        return BiologyCourseContent(
            category = category,
            subtitle = profile.subtitle,
            formula = profile.formula,
            sourceSummary = "本课程依据教材目录中的“${lesson.title}”及$pageLabel 组织。仓库没有保存教材正文，因此不生成未经核对的原文、实验数据或生理数值；School 解释只覆盖当前主题的结构、过程、证据和数量关系。绑定 PDF 后可核对原页。",
            steps = profile.steps,
            background = profile.background,
            misconception = profile.misconception,
            enrichment = LessonEnrichment(
                background = profile.background.mapIndexed { index, text ->
                    CourseNote(ContentOrigin.SCHOOL_EXPLANATION, if (index == 0) "生物学背景" else "模型边界", text)
                },
                extensions = listOf(
                    CourseNote(ContentOrigin.OPTIONAL_EXTENSION, "扩展：从结构继续研究功能与调节", profile.extension),
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
                    kind = if (profile.examples.isEmpty()) CourseVerificationKind.BIOLOGICAL_PROCESS else CourseVerificationKind.BIOLOGICAL_RELATION,
                    title = "生物结构、过程与数量关系验证",
                    prompt = "选择当前主题允许的关系或过程，确认统计口径和条件，再填写数据或排列步骤。",
                    inputHint = profile.formula,
                    examples = profile.examples,
                    requiredConcepts = profile.concepts,
                    requiredOperations = profile.operations,
                ),
            ),
        )
    }

    fun classify(title: String): BiologyCourseCategory {
        val t = title.replace(" ", "").replace("　", "")
        return when {
            t.contains("实验") || t.contains("探究") || t.contains("观察") || t.contains("调查") -> BiologyCourseCategory.EXPERIMENT
            t.contains("生态") || t.contains("种群") || t.contains("群落") || t.contains("食物链") || t.contains("食物网") || t.contains("环境") || t.contains("生物圈") -> BiologyCourseCategory.ECOLOGY
            t.contains("进化") || t.contains("自然选择") || t.contains("生命起源") -> BiologyCourseCategory.EVOLUTION
            t.contains("分类") || t.contains("植物类群") || t.contains("动物类群") || t.contains("微生物") || t.contains("病毒") -> BiologyCourseCategory.CLASSIFICATION
            t.contains("遗传") || t.contains("基因") || t.contains("染色体") || t.contains("DNA") || t.contains("变异") || t.contains("性状") -> BiologyCourseCategory.GENETICS
            t.contains("生殖") || t.contains("发育") || t.contains("青春期") || t.contains("繁殖") -> BiologyCourseCategory.REPRODUCTION
            t.contains("人体") || t.contains("消化") || t.contains("呼吸") || t.contains("循环") || t.contains("神经") || t.contains("激素") || t.contains("免疫") || t.contains("感觉") || t.contains("泌尿") -> BiologyCourseCategory.HUMAN
            t.contains("植物") || t.contains("根") || t.contains("茎") || t.contains("叶") || t.contains("蒸腾") || t.contains("开花") || t.contains("种子") || t.contains("果实") -> BiologyCourseCategory.PLANT
            t.contains("光合") || t.contains("呼吸作用") || t.contains("酶") || t.contains("代谢") || t.contains("能量") || t.contains("物质运输") -> BiologyCourseCategory.METABOLISM
            t.contains("细胞") || t.contains("组织") || t.contains("显微镜") || t.contains("细胞器") || t.contains("细胞膜") || t.contains("细胞核") -> BiologyCourseCategory.CELL
            else -> BiologyCourseCategory.GENERAL
        }
    }

    private fun profile(category: BiologyCourseCategory): BiologyProfile = when (category) {
        BiologyCourseCategory.CELL -> p(
            "从显微观察、细胞结构和功能建立层级联系",
            "放大倍数=图像大小/实际大小",
            CourseVisualizationKind.CELL,
            "细胞结构与显微观察",
            "调节细胞类型、放大倍数和结构高亮，观察细胞壁、膜、质、核及细胞器层级。",
            listOf(num("magnification", "放大倍数", "100", 40.0, 1000.0, 10.0, "×")),
            listOf("明确观察材料和制片方法", "调节显微镜并获得清晰视野", "区分图像方向和实际方向", "按边界识别细胞结构", "由结构联系功能并说明示意图非真实比例"),
            listOf("细胞是生物体结构和功能的基本单位。", "显微镜图像大小、视野范围和实际结构大小必须区分。"),
            "不能把教学颜色当作真实颜色，也不能仅凭示意图比例判断细胞器实际大小。",
            "可进一步比较原核、真核和不同细胞器，但只在教材引入后加入正式要求。",
            setOf(ConceptId.CELL_STRUCTURE), setOf(OperationId.LABEL_DIAGRAM), setOf(WidgetType.BIOLOGY_DIAGRAM), listOf("放大倍数=图像大小/实际大小"),
        )
        BiologyCourseCategory.METABOLISM -> p(
            "把物质变化、能量转换、场所和条件组织成过程",
            "速率=变化量/时间",
            CourseVisualizationKind.BIOLOGICAL_PROCESS,
            "代谢过程与速率",
            "调节光照、温度、气体变化量或时间，观察光合作用、呼吸作用和速率趋势。",
            listOf(num("light", "光照强度", "50", 0.0, 100.0, 5.0, "%"), num("time", "时间", "10", 1.0, 60.0, 1.0, "min")),
            listOf("确定研究的是物质变化还是能量变化", "标出反应场所、原料和产物", "列出必要条件", "比较过程方向和相互联系", "使用实验指标计算相对速率"),
            listOf("生物代谢由多步反应和酶调节组成，教材式子是总过程概括。", "测得的气体变化可能是多个过程共同作用后的净结果。"),
            "不能把光合作用理解成只在白天发生、呼吸作用只在夜间发生。",
            "可继续研究限制因素和酶动力学，但不把复杂曲线模型提前作为基础章节必会。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.SOLVE_RELATION), setOf(WidgetType.BIOLOGY_DIAGRAM, WidgetType.RELATION_CALCULATOR), listOf("呼吸速率=气体变化量/时间", "光合速率=产物变化量/时间"),
        )
        BiologyCourseCategory.PLANT -> p(
            "从器官结构、运输、光合和生殖理解植物生活",
            "结构 → 功能 → 整体协调",
            CourseVisualizationKind.BIOLOGICAL_PROCESS,
            "植物体内运输与生长",
            "调节光、水、气孔开度和环境条件，观察吸收、运输、蒸腾和光合联系。",
            listOf(num("water", "水分供应", "60", 0.0, 100.0, 5.0, "%"), num("stomata", "气孔开度", "50", 0.0, 100.0, 5.0, "%")),
            listOf("识别根、茎、叶或花果实结构", "说明结构适应的功能", "沿运输方向连接器官", "加入环境条件", "用实验或现象解释整体变化"),
            listOf("植物体是多个器官协同工作的整体。", "蒸腾、运输和气体交换受环境和结构共同影响。"),
            "不能把根吸收的水全部理解为光合作用原料，大部分水参与运输和蒸腾。",
            "可进一步研究植物激素和向性调节，但只在对应章节启用。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.LABEL_DIAGRAM), setOf(WidgetType.BIOLOGY_DIAGRAM), listOf("光合速率=产物变化量/时间"),
        )
        BiologyCourseCategory.HUMAN -> p(
            "从器官系统、物质运输和调节建立人体稳态联系",
            "心输出量=心率×每搏输出量",
            CourseVisualizationKind.BIOLOGICAL_PROCESS,
            "人体系统与物质运输",
            "调节心率、呼吸频率或活动强度，观察循环、呼吸、消化和调节系统协同。",
            listOf(num("heartRate", "心率", "75", 40.0, 200.0, 1.0, "次/min"), num("strokeVolume", "每搏输出量", "70", 20.0, 150.0, 1.0, "mL")),
            listOf("定位器官和系统", "沿物质流向追踪输入、运输和排出", "说明结构适应功能", "加入神经或体液调节", "用测量数据验证状态变化"),
            listOf("人体各系统通过物质运输和调节共同维持相对稳定。", "生理指标受年龄、活动和个体差异影响，教材数值是特定情境。"),
            "不能把单一器官独立解释成完整生命活动，也不能把健康建议当作医学诊断。",
            "可进一步观察稳态反馈，但复杂临床判断不属于教材验证器范围。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.SOLVE_RELATION, OperationId.LABEL_DIAGRAM), setOf(WidgetType.BIOLOGY_DIAGRAM, WidgetType.RELATION_CALCULATOR), listOf("心输出量=心率×每搏输出量", "呼吸速率=气体变化量/时间"),
        )
        BiologyCourseCategory.GENETICS -> p(
            "从性状、基因、染色体和配子组合解释遗传概率",
            "概率=符合条件组合数/全部等可能组合数",
            CourseVisualizationKind.DATA_TABLE,
            "配子组合与性状概率",
            "选择亲本基因型，观察配子、组合表和子代基因型/表现型比例。",
            listOf(choice("cross", "亲本组合", "Aa×Aa", listOf("AA×aa", "Aa×Aa", "Aa×aa"))),
            listOf("明确性状和显隐性关系", "写出亲本基因型", "列出各类配子", "建立不重不漏的组合", "区分基因型概率和表现型概率"),
            listOf("遗传概率建立在配子形成与结合的等可能模型上。", "性状表现还可能受多基因、环境和相互作用影响。"),
            "概率描述大量后代中的期望比例，不保证少量后代严格按比例出现。",
            "可进一步研究伴性、多基因和连锁，但当前验证器只在教材明确模型下使用。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.SOLVE_RELATION), setOf(WidgetType.RELATION_CALCULATOR), listOf("遗传概率=有利组合数/全部组合数"),
        )
        BiologyCourseCategory.REPRODUCTION -> p(
            "把生殖细胞、受精、发育阶段和环境条件按时间组织",
            "成活率=成活个体数/总个体数×100%",
            CourseVisualizationKind.BIOLOGICAL_PROCESS,
            "生殖与发育过程",
            "调节温度、营养或个体数量，观察发育阶段与成活率。",
            listOf(num("total", "总个体数", "100", 1.0, 1000.0, 1.0), num("survived", "成活个体数", "80", 0.0, 1000.0, 1.0)),
            listOf("识别生殖方式", "按时间排列生殖和发育阶段", "说明遗传物质来源", "加入环境条件", "用数量数据描述成活或繁殖结果"),
            listOf("生殖保证种族延续，发育具有阶段性。", "有性生殖增加遗传组合，无性生殖通常保持更多亲本特征。"),
            "不能把生长和发育完全等同，也不能把所有生物的生活史套成同一模式。",
            "可比较不同物种生活史策略，但不作为当前物种章节的必会内容。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.SOLVE_RELATION), setOf(WidgetType.BIOLOGY_DIAGRAM), listOf("成活率=成活个体数/总个体数×100%"),
        )
        BiologyCourseCategory.CLASSIFICATION -> p(
            "依据可观察和可比较特征建立分类层级",
            "共同特征 → 分类单元",
            CourseVisualizationKind.DATA_TABLE,
            "生物特征与分类",
            "选择形态、结构或生活方式特征，观察分类结果和共同特征。",
            listOf(choice("trait", "分类特征", "细胞结构", listOf("细胞结构", "营养方式", "繁殖方式", "形态特征"))),
            listOf("明确分类目的", "选择稳定且可比较的特征", "记录共同点和差异", "建立层级或检索表", "说明分类证据和例外"),
            listOf("分类帮助组织生物多样性知识，分类依据会随研究证据发展。", "形态相似不一定表示亲缘关系最近。"),
            "不能只凭一个外观特征完成所有分类，也不能把俗名直接当作分类地位。",
            "可比较现代系统发育观点，但教材基础分类仍以当前章节提供的特征为准。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.LABEL_DIAGRAM), setOf(WidgetType.BIOLOGY_DIAGRAM), emptyList(),
        )
        BiologyCourseCategory.EVOLUTION -> p(
            "从变异、遗传、环境选择和时间尺度理解群体变化",
            "可遗传变异 + 选择 + 世代积累",
            CourseVisualizationKind.BIOLOGICAL_PROCESS,
            "自然选择过程",
            "调节环境、性状频率和世代数，观察群体中性状比例变化。",
            listOf(num("generation", "世代数", "10", 1.0, 100.0, 1.0), num("advantage", "相对适应优势", "10", -50.0, 50.0, 1.0, "%")),
            listOf("确认群体中存在变异", "判断哪些变异可遗传", "说明环境选择压力", "比较不同个体繁殖成功", "在多世代尺度观察频率变化"),
            listOf("自然选择作用于个体差异，结果表现为群体在世代中的变化。", "进化没有预先设定的目标。"),
            "不能说个体为了适应环境而主动产生所需遗传变异。",
            "可进一步研究遗传漂变和基因流，但基础章节先掌握自然选择证据链。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.SOLVE_RELATION), setOf(WidgetType.RELATION_CALCULATOR), emptyList(),
        )
        BiologyCourseCategory.ECOLOGY -> p(
            "从个体、种群、群落和生态系统层级追踪物质与能量",
            "种群密度=N/S  ·  能量效率=E₂/E₁×100%",
            CourseVisualizationKind.BIOLOGICAL_PROCESS,
            "生态关系与能量流动",
            "调节种群数量、面积、营养级能量和环境条件，观察密度、增长和能量传递。",
            listOf(num("count", "个体数", "120", 0.0, 1000.0, 10.0), num("area", "调查面积", "20", 0.1, 100.0, 1.0, "m²")),
            listOf("确定生态层级和调查边界", "识别生产者、消费者和分解者", "画出食物链或食物网", "计算种群或能量数量关系", "说明环境条件与反馈"),
            listOf("生态系统中的能量单向流动，物质循环利用。", "种群密度和增长率都依赖时间、空间和调查方法。"),
            "不能把食物网箭头理解成捕食动作方向；它通常表示物质和能量流向。",
            "可扩展到种群模型和生态服务，但不能用简单增长公式替代真实生态系统。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.SOLVE_RELATION), setOf(WidgetType.RELATION_CALCULATOR, WidgetType.BIOLOGY_DIAGRAM), listOf("种群密度=个体数/面积", "能量传递效率=下一营养级/上一营养级×100%"),
        )
        BiologyCourseCategory.EXPERIMENT -> p(
            "按问题、假设、变量、步骤、数据和结论组织生物实验",
            "变量控制 + 重复 + 证据",
            CourseVisualizationKind.PROCESS,
            "生物实验与数据",
            "调节样本量、重复次数和自变量，观察数据波动及结论可靠性。",
            listOf(num("sample", "样本量", "30", 1.0, 300.0, 1.0), num("repeat", "重复次数", "3", 1.0, 20.0, 1.0)),
            listOf("提出可检验问题", "形成假设并确定变量", "设置对照与重复", "按步骤记录原始数据", "分析数据并区分支持、反对或证据不足"),
            listOf("生物个体差异使重复和样本量尤其重要。", "实验结论受样本、条件和测量方法限制。"),
            "不能把一次观察直接推广到所有生物，也不能删除不符合预期的数据。",
            "可进一步加入统计检验，但只在教材已经引入统计方法时使用。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.VALIDATE_MODEL_CONDITIONS), setOf(WidgetType.BIOLOGY_DIAGRAM), BiologyRelationIdExamples.all,
        )
        BiologyCourseCategory.GENERAL -> p(
            "从结构、功能、过程、环境和证据进入本课",
            "结构 ↔ 功能 ↔ 环境",
            CourseVisualizationKind.BIOLOGICAL_PROCESS,
            "生物学关系",
            "调节教材涉及的条件，观察结构、过程和结果如何联系。",
            listOf(num("condition", "示例条件", "50", 0.0, 100.0, 5.0, "%")),
            listOf("定位教材页码和研究层级", "识别结构或对象", "按时间或空间组织过程", "加入环境和调节条件", "用观察、实验或数量关系验证"),
            listOf("生物学解释通常需要同时考虑结构、功能、历史和环境。", "模型帮助理解规律，但个体差异和条件不能被忽略。"),
            "不要把教学示意图、典型比例或单一例子当作所有生物的固定事实。",
            "可从分子、个体或生态层级继续研究，但扩展不作为本课必会前提。",
            setOf(ConceptId.BIOLOGICAL_PROCESS), setOf(OperationId.LABEL_DIAGRAM), setOf(WidgetType.BIOLOGY_DIAGRAM), emptyList(),
        )
    }

    private fun p(
        subtitle: String,
        formula: String,
        visualization: CourseVisualizationKind,
        visualTitle: String,
        visualDescription: String,
        parameters: List<CourseParameterSpec>,
        steps: List<String>,
        background: List<String>,
        misconception: String,
        extension: String,
        concepts: Set<ConceptId>,
        operations: Set<OperationId>,
        widgets: Set<WidgetType>,
        examples: List<String>,
    ) = BiologyProfile(subtitle, formula, visualization, visualTitle, visualDescription, parameters, steps, background, misconception, extension, concepts, operations, widgets, examples)

    private fun num(id: String, label: String, default: String, min: Double, max: Double, step: Double, unit: String = "") = CourseParameterSpec(
        id = id,
        label = label,
        kind = if (step >= 1.0 && default.toDoubleOrNull()?.rem(1.0) == 0.0) CourseParameterKind.INTEGER else CourseParameterKind.NUMBER,
        defaultValue = default,
        unit = unit,
        minimum = min,
        maximum = max,
        step = step,
    )

    private fun choice(id: String, label: String, default: String, choices: List<String>) = CourseParameterSpec(
        id = id,
        label = label,
        kind = CourseParameterKind.CHOICE,
        defaultValue = default,
        choices = choices,
    )

    private data class BiologyProfile(
        val subtitle: String,
        val formula: String,
        val visualization: CourseVisualizationKind,
        val visualTitle: String,
        val visualDescription: String,
        val parameters: List<CourseParameterSpec>,
        val steps: List<String>,
        val background: List<String>,
        val misconception: String,
        val extension: String,
        val concepts: Set<ConceptId>,
        val operations: Set<OperationId>,
        val widgets: Set<WidgetType>,
        val examples: List<String>,
    )

    private object BiologyRelationIdExamples {
        val all = listOf(
            "放大倍数=图像大小/实际大小",
            "种群密度=个体数/面积",
            "增长率=(末数量-初数量)/初数量×100%",
            "遗传概率=符合条件组合数/全部组合数",
        )
    }
}
