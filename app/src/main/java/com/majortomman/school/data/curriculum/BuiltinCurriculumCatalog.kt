package com.majortomman.school.data.curriculum

import java.security.MessageDigest

object BuiltinCurriculumCatalog {
    const val BASIC_EDUCATION_LEVEL_SYSTEM = "cn-basic-education"
    const val UNIVERSITY_LEVEL_SYSTEM = "university-year"

    val subjects: List<SubjectDefinition> = listOf(
        subject("chinese", "语文", SubjectCategory.LANGUAGE, "阅读、表达、写作与文学理解。", stages("primary", "junior-high", "senior-high"), LearningCapability.TEXT_READING, LearningCapability.TEXT_WRITING, LearningCapability.ESSAY),
        subject("math", "数学", SubjectCategory.MATHEMATICS, "数量、结构、空间、变化与推理。", stages("primary", "junior-high", "senior-high", "university"), LearningCapability.NUMERIC_EVALUATION, LearningCapability.EXPRESSION_EVALUATION, LearningCapability.STEP_EVALUATION, LearningCapability.GRAPH_RENDERING, LearningCapability.GEOMETRY),
        subject("english", "英语", SubjectCategory.LANGUAGE, "词汇、语法、听说读写。", stages("primary", "junior-high", "senior-high", "university"), LearningCapability.TEXT_READING, LearningCapability.TEXT_WRITING, LearningCapability.VOCABULARY, LearningCapability.GRAMMAR, LearningCapability.AUDIO, LearningCapability.PRONUNCIATION),
        subject("japanese", "日语", SubjectCategory.LANGUAGE, "假名、词汇、语法、听说读写。", emptySet(), LearningCapability.TEXT_READING, LearningCapability.TEXT_WRITING, LearningCapability.VOCABULARY, LearningCapability.GRAMMAR, LearningCapability.AUDIO, LearningCapability.PRONUNCIATION),
        subject("science", "科学", SubjectCategory.NATURAL_SCIENCE, "小学阶段的综合自然科学与实验观察。", stages("primary"), LearningCapability.EXPERIMENT, LearningCapability.CAUSAL_REASONING),
        subject("physics", "物理", SubjectCategory.NATURAL_SCIENCE, "物质、运动、能量与实验规律。", stages("junior-high", "senior-high", "university"), LearningCapability.NUMERIC_EVALUATION, LearningCapability.EXPRESSION_EVALUATION, LearningCapability.FORMULA, LearningCapability.EXPERIMENT, LearningCapability.GRAPH_RENDERING),
        subject("chemistry", "化学", SubjectCategory.NATURAL_SCIENCE, "物质组成、变化、反应与实验。", stages("junior-high", "senior-high", "university"), LearningCapability.FORMULA, LearningCapability.EXPERIMENT, LearningCapability.NUMERIC_EVALUATION),
        subject("biology", "生物", SubjectCategory.NATURAL_SCIENCE, "生命结构、过程、遗传、生态与实验。", stages("junior-high", "senior-high", "university"), LearningCapability.TEXT_READING, LearningCapability.EXPERIMENT, LearningCapability.CAUSAL_REASONING),
        subject("history", "历史", SubjectCategory.SOCIAL_SCIENCE, "时间线、人物、事件与因果解释。", stages("junior-high", "senior-high", "university"), LearningCapability.TIMELINE, LearningCapability.CAUSAL_REASONING, LearningCapability.ESSAY),
        subject("geography", "地理", SubjectCategory.SOCIAL_SCIENCE, "空间分布、区域联系、地图与人地关系。", stages("junior-high", "senior-high", "university"), LearningCapability.MAP, LearningCapability.GRAPH_RENDERING, LearningCapability.CAUSAL_REASONING),
        subject("politics", "思想政治", SubjectCategory.SOCIAL_SCIENCE, "社会制度、公共生活、哲学与论证。", stages("junior-high", "senior-high", "university"), LearningCapability.TEXT_READING, LearningCapability.CAUSAL_REASONING, LearningCapability.ESSAY),
        subject("computer", "计算机", SubjectCategory.TECHNOLOGY, "计算机系统、数据、网络与软件。", stages("senior-high", "university"), LearningCapability.PROGRAMMING, LearningCapability.CAUSAL_REASONING),
        subject("programming", "编程", SubjectCategory.TECHNOLOGY, "程序设计、算法、工程实践与调试。", emptySet(), LearningCapability.PROGRAMMING, LearningCapability.STEP_EVALUATION),
        subject("economics", "经济学", SubjectCategory.SOCIAL_SCIENCE, "资源配置、市场、宏观经济与数据分析。", stages("university"), LearningCapability.GRAPH_RENDERING, LearningCapability.NUMERIC_EVALUATION, LearningCapability.CAUSAL_REASONING, LearningCapability.ESSAY),
        subject("law", "法学", SubjectCategory.SOCIAL_SCIENCE, "规则体系、案例分析与法律论证。", stages("university"), LearningCapability.TEXT_READING, LearningCapability.CAUSAL_REASONING, LearningCapability.ESSAY),
        subject("music", "音乐", SubjectCategory.ARTS, "听辨、节奏、乐理与作品理解。", stages("primary", "junior-high", "senior-high"), LearningCapability.AUDIO),
        subject("art", "美术", SubjectCategory.ARTS, "视觉观察、造型、设计与作品理解。", stages("primary", "junior-high", "senior-high")),
        subject("pe-health", "体育与健康", SubjectCategory.HEALTH, "运动技能、健康知识与训练计划。", stages("primary", "junior-high", "senior-high")),
    ).mapIndexed { index, subject -> subject.copy(orderIndex = index) }

    val levelSystems: List<LearningLevelSystem> = listOf(
        LearningLevelSystem(BASIC_EDUCATION_LEVEL_SYSTEM, "中国基础教育", "小学、初中和高中年级体系。"),
        LearningLevelSystem(UNIVERSITY_LEVEL_SYSTEM, "大学学年", "大学一至四年级的通用学年体系。"),
    )

    val levels: List<LearningLevel> = buildList {
        add(LearningLevel("primary", BASIC_EDUCATION_LEVEL_SYSTEM, null, "小学", 0))
        (1..6).forEach { grade ->
            add(LearningLevel("grade-$grade", BASIC_EDUCATION_LEVEL_SYSTEM, "primary", gradeTitle(grade), grade, grade))
        }
        add(LearningLevel("junior-high", BASIC_EDUCATION_LEVEL_SYSTEM, null, "初中", 10))
        (7..9).forEach { grade ->
            add(LearningLevel("grade-$grade", BASIC_EDUCATION_LEVEL_SYSTEM, "junior-high", gradeTitle(grade), grade, grade))
        }
        add(LearningLevel("senior-high", BASIC_EDUCATION_LEVEL_SYSTEM, null, "高中", 20))
        (10..12).forEach { grade ->
            add(LearningLevel("grade-$grade", BASIC_EDUCATION_LEVEL_SYSTEM, "senior-high", gradeTitle(grade), grade, grade))
        }
        add(LearningLevel("university", UNIVERSITY_LEVEL_SYSTEM, null, "大学", 0))
        (13..16).forEach { grade ->
            add(LearningLevel("grade-$grade", UNIVERSITY_LEVEL_SYSTEM, "university", gradeTitle(grade), grade, grade))
        }
    }

    val knowledgePoints: List<KnowledgePoint> = listOf(
        KnowledgePoint("positive-negative", "math", "正数和负数", "判断符号，并用正负数表示相反意义的量。", KnowledgeKind.CONCEPT, "math.sign", keywords("正数", "负数", "相反意义", "温度", "海拔")),
        KnowledgePoint("number-line", "math", "数轴", "理解原点、正方向和单位长度，并在数轴上定位数。", KnowledgeKind.CONCEPT, "math.number-line", keywords("数轴", "原点", "单位长度", "坐标")),
        KnowledgePoint("opposite-number", "math", "相反数", "理解关于原点对称的位置关系。", KnowledgeKind.CONCEPT, "math.rational", keywords("相反数", "原点对称")),
        KnowledgePoint("absolute-value", "math", "绝对值", "把绝对值理解为数到原点的距离。", KnowledgeKind.CONCEPT, "math.rational", keywords("绝对值", "距离")),
        KnowledgePoint("rational-compare", "math", "有理数大小比较", "借助数轴、符号和绝对值比较有理数。", KnowledgeKind.PROCEDURE, "math.order", keywords("有理数", "大小比较", "排序", "大于", "小于")),
        KnowledgePoint("expression-equivalence", "math", "整式与等价变形", "使用去括号、分配律和合并同类项进行等价变形。", KnowledgeKind.PROCEDURE, "math.expression", keywords("整式", "代数式", "合并同类项", "去括号", "分配律")),
        KnowledgePoint("linear-equation", "math", "一元一次方程", "保持等式两边平衡，逐步求出未知数。", KnowledgeKind.PROCEDURE, "math.equation", keywords("一元一次方程", "移项", "解方程")),
    )

    val knowledgeRelations: List<KnowledgeRelation> = listOf(
        KnowledgeRelation("positive-negative", "number-line", KnowledgeRelationType.PREREQUISITE, 0.9),
        KnowledgeRelation("number-line", "opposite-number", KnowledgeRelationType.PREREQUISITE, 1.0),
        KnowledgeRelation("number-line", "absolute-value", KnowledgeRelationType.PREREQUISITE, 1.0),
        KnowledgeRelation("positive-negative", "rational-compare", KnowledgeRelationType.PREREQUISITE, 0.8),
        KnowledgeRelation("number-line", "rational-compare", KnowledgeRelationType.PREREQUISITE, 1.0),
        KnowledgeRelation("absolute-value", "rational-compare", KnowledgeRelationType.PREREQUISITE, 0.7),
        KnowledgeRelation("expression-equivalence", "linear-equation", KnowledgeRelationType.PREREQUISITE, 1.0),
    )

    val subjectById: Map<String, SubjectDefinition> = subjects.associateBy(SubjectDefinition::id)
    val knowledgeById: Map<String, KnowledgePoint> = knowledgePoints.associateBy(KnowledgePoint::id)

    fun subject(id: String, fallbackTitle: String = id): SubjectDefinition = subjectById[id]
        ?: SubjectDefinition(
            id = id,
            title = fallbackTitle,
            category = SubjectCategory.OTHER,
            description = "$fallbackTitle 的个人学习课程。",
            capabilityIds = emptySet(),
            orderIndex = subjects.size,
        )

    fun inferKnowledge(subjectId: String, title: String): KnowledgePoint {
        if (subjectId == "math") {
            val normalized = title.lowercase()
            knowledgePoints
                .filter { it.subjectId == "math" }
                .map { point ->
                    val candidates = point.metadata["keywords"].orEmpty().split('|').filter(String::isNotBlank)
                    point to candidates.count { it.lowercase() in normalized }
                }
                .filter { (_, score) -> score > 0 }
                .maxByOrNull { (_, score) -> score }
                ?.first
                ?.let { return it }
        }
        val normalizedTitle = title.trim().ifBlank { "未命名知识点" }
        return KnowledgePoint(
            id = "$subjectId:${stableId(normalizedTitle)}",
            subjectId = subjectId,
            title = normalizedTitle,
            description = "学习并掌握$normalizedTitle。",
            kind = KnowledgeKind.CONCEPT,
            evaluatorId = defaultEvaluator(subjectId),
            metadata = mapOf("generated" to "true"),
        )
    }

    fun defaultEvaluator(subjectId: String): String? = when (subjectId) {
        "math" -> "math.expression"
        "physics", "chemistry" -> "science.formula"
        "english", "japanese" -> "language.text"
        "programming", "computer" -> "programming.code"
        else -> "text.rubric"
    }

    fun stableId(raw: String): String {
        val ascii = raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        if (ascii.length >= 3) return ascii.take(48)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return "k-$digest"
    }

    private fun subject(
        id: String,
        title: String,
        category: SubjectCategory,
        description: String,
        stageIds: Set<String>,
        vararg capabilities: LearningCapability,
    ): SubjectDefinition = SubjectDefinition(
        id = id,
        title = title,
        category = category,
        description = description,
        capabilityIds = capabilities.toSet(),
        stageIds = stageIds,
    )

    private fun stages(vararg ids: String): Set<String> = ids.toSet()
    private fun keywords(vararg values: String): Map<String, String> = mapOf("keywords" to values.joinToString("|"))

    private fun gradeTitle(grade: Int): String = when (grade) {
        1 -> "一年级"
        2 -> "二年级"
        3 -> "三年级"
        4 -> "四年级"
        5 -> "五年级"
        6 -> "六年级"
        7 -> "七年级"
        8 -> "八年级"
        9 -> "九年级"
        10 -> "高一"
        11 -> "高二"
        12 -> "高三"
        13 -> "大一"
        14 -> "大二"
        15 -> "大三"
        16 -> "大四"
        else -> "第${grade}学年"
    }
}
