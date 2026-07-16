package com.majortomman.school.data.material

internal object PrebuiltSubjectAnalysisFactory {
    fun create(slot: TextbookSlot, lesson: GeneratedLesson): LessonAnalysis {
        val role = lesson.role.uppercase()
        val pageLabel = if (lesson.pageStart == lesson.pageEnd) {
            "教材第 ${lesson.pageStart} 页"
        } else {
            "教材第 ${lesson.pageStart}—${lesson.pageEnd} 页"
        }
        val subjectPlan = when (slot.subjectId) {
            "chinese" -> chinesePlan(role, lesson.title)
            "english", "japanese" -> languagePlan(slot.subjectTitle, role, lesson.title)
            "physics", "chemistry" -> sciencePlan(slot.subjectTitle, role, lesson.title)
            else -> genericPlan(slot.subjectTitle, role, lesson.title)
        }
        return LessonAnalysis(
            lessonSourceId = lesson.sourceId,
            summary = subjectPlan.summary,
            objectives = subjectPlan.objectives,
            misconception = subjectPlan.misconception,
            sourcePages = lesson.pageStart..lesson.pageEnd,
            scene = LessonSceneSpec(
                type = subjectPlan.sceneType,
                title = subjectPlan.sceneTitle,
                prompt = subjectPlan.prompt,
                expression = subjectPlan.expression,
                conclusion = subjectPlan.conclusion,
                steps = subjectPlan.steps,
                sourcePage = lesson.pageStart,
            ),
            exercise = GeneratedExercise(
                question = subjectPlan.question,
                acceptedAnswers = subjectPlan.acceptedAnswers,
                hints = listOf(
                    "先回到$pageLabel，确认标题、任务说明和关键概念。",
                    "回答时至少说明一个教材依据，不要只写结论。",
                ),
                explanation = subjectPlan.exerciseExplanation,
            ),
            source = LessonAnalysisSource.PACK,
        )
    }

    private fun chinesePlan(role: String, title: String): SubjectPlan = when (role) {
        "WRITING" -> SubjectPlan(
            summary = "本节围绕“$title”训练写作任务，重点是明确写作目的、组织材料并用具体语言完成表达。",
            objectives = listOf("明确写作对象和目的", "按中心组织材料与结构", "修改语言并检查证据是否充分"),
            misconception = "不要把写作训练理解成套模板；结构和语言必须服务于本次表达目的。",
            sceneType = LessonSceneType.PROCESS,
            sceneTitle = "从任务到成文",
            prompt = "完成“$title”时，内容应怎样从材料组织成完整表达？",
            conclusion = "先确定目的和中心，再选择材料、安排结构、完成表达并修改。",
            steps = listOf("辨认写作任务", "确定中心", "选择材料", "组织结构", "修改表达"),
            question = "完成“$title”最先要明确什么？",
            acceptedAnswers = listOf("写作目的", "表达目的", "中心"),
            exerciseExplanation = "写作首先要明确表达目的与中心，后续材料和结构才能有取舍。",
        )
        "SPEAKING" -> SubjectPlan(
            summary = "本节围绕“$title”训练口语交际，需要同时关注信息、对象、场合和回应。",
            objectives = listOf("提取交际任务中的关键信息", "根据对象与场合调整表达", "倾听并作出有效回应"),
            misconception = "口语交际不是把书面答案读出来；表达必须考虑听者和现场反馈。",
            sceneType = LessonSceneType.PROCESS,
            sceneTitle = "交流与回应",
            prompt = "在“$title”的交流中，怎样让信息清楚并得到回应？",
            conclusion = "明确目的、考虑对象、清楚表达，并根据对方反馈及时调整。",
            steps = listOf("明确目的", "判断对象与场合", "组织要点", "清楚表达", "倾听回应"),
            question = "口语交际除了说清内容，还要考虑什么？",
            acceptedAnswers = listOf("对象和场合", "听者", "对方反馈"),
            exerciseExplanation = "有效交流既包含信息，也包含对象、场合和回应。",
        )
        "WHOLE_BOOK" -> SubjectPlan(
            summary = "本节是“$title”的整本书阅读任务，重点是建立持续阅读计划，并用人物、情节、主题或语言证据形成整体理解。",
            objectives = listOf("制定可执行的阅读计划", "持续记录人物、情节或观点线索", "用书中证据形成整体判断"),
            misconception = "整本书阅读不能只依赖故事梗概；判断必须来自持续阅读和原书证据。",
            sceneType = LessonSceneType.PROCESS,
            sceneTitle = "建立整本书阅读线索",
            prompt = "阅读“$title”时，怎样把零散章节连接成整体理解？",
            conclusion = "围绕核心线索持续记录，并在阅读完成后用证据修正整体判断。",
            steps = listOf("制定计划", "确定观察线索", "分段阅读记录", "比较前后变化", "形成整体判断"),
            question = "整本书阅读形成判断时应依据什么？",
            acceptedAnswers = listOf("原书证据", "文本证据", "持续阅读记录"),
            exerciseExplanation = "整本书判断应由原书细节和持续记录支撑，而不是只看梗概。",
        )
        else -> SubjectPlan(
            summary = "本节学习“$title”，需要理解文本内容、结构和表达方式，并能用具体语句支撑自己的解释。",
            objectives = listOf("概括文本或任务的核心内容", "梳理结构、线索或观点关系", "引用具体语句说明理解"),
            misconception = "不要只复述情节或背结论；阅读判断需要由文本中的词句、结构和语境支撑。",
            sceneType = LessonSceneType.TEXT,
            sceneTitle = "从文本证据形成理解",
            prompt = "“$title”通过哪些内容和表达方式形成主要意义？",
            conclusion = "先理解整体内容，再定位关键语句和结构，用证据解释自己的判断。",
            steps = listOf("通读并确定对象", "概括主要内容", "梳理结构或线索", "定位关键语句", "用证据解释"),
            question = "解释“$title”时，最重要的依据是什么？",
            acceptedAnswers = listOf("文本证据", "具体语句", "原文"),
            exerciseExplanation = "语文阅读结论必须回到具体文本，说明词句、结构和语境如何支持判断。",
        )
    }

    private fun languagePlan(subject: String, role: String, title: String): SubjectPlan {
        val focus = when (role) {
            "LISTENING" -> "听力信息、语境和说话者意图"
            "SPEAKING" -> "口头表达、语音语调和互动回应"
            "READING" -> "篇章信息、结构和语境推断"
            "WRITING" -> "写作目的、结构和语言准确性"
            "GRAMMAR" -> "形式、意义和具体语境中的使用条件"
            "VOCABULARY" -> "词义、搭配和语境中的实际使用"
            "PROJECT" -> "综合语言运用、协作和成果表达"
            else -> "词汇、句型、语篇和实际交际任务"
        }
        return SubjectPlan(
            summary = "本节学习“$title”，重点训练$focus，而不是孤立记忆翻译或规则。",
            objectives = listOf("识别本节交际情境和语言目的", "理解核心词句在语境中的意义", "在新的相似情境中完成表达或理解"),
            misconception = "不要只背中文对应或语法名称；语言形式必须和语境、意义及使用目的一起学习。",
            sceneType = if (role in setOf("READING", "GRAMMAR", "VOCABULARY")) LessonSceneType.TEXT else LessonSceneType.PROCESS,
            sceneTitle = "从语境到表达",
            prompt = "在“$title”的情境中，为什么要使用这些词句和表达方式？",
            conclusion = "先确认交际目的和语境，再理解语言形式，最后迁移到新的相似任务。",
            steps = listOf("识别情境", "提取关键信息", "观察语言形式", "理解意义与条件", "完成迁移表达"),
            question = "学习“$title”时，语言形式应和什么一起理解？",
            acceptedAnswers = listOf("语境和意义", "语境", "交际目的"),
            exerciseExplanation = "$subject 学习不能脱离语境；同一形式在不同场景中可能承担不同作用。",
        )
    }

    private fun sciencePlan(subject: String, role: String, title: String): SubjectPlan {
        val experiment = role == "EXPERIMENT"
        return if (experiment) {
            SubjectPlan(
                summary = "本节通过“$title”训练科学探究，重点是控制条件、记录现象、处理证据并形成有限度的结论。",
                objectives = listOf("明确探究问题和变量", "按步骤观察并记录现象", "区分实验事实、解释和结论"),
                misconception = "不要把预期结果当作观察结果，也不要由一次现象推出超出实验条件的结论。",
                sceneType = LessonSceneType.PROCESS,
                sceneTitle = "从实验问题到证据",
                prompt = "“$title”中，哪些条件必须控制，哪些现象才是有效证据？",
                conclusion = "实验结论只能由受控条件下的可重复证据支持。",
                steps = listOf("提出问题", "识别变量与条件", "执行并记录", "处理证据", "形成并限制结论"),
                question = "实验结论最直接的依据是什么？",
                acceptedAnswers = listOf("实验现象和数据", "实验数据", "可重复证据"),
                exerciseExplanation = "科学实验应先记录事实，再依据数据解释，不能用预期替代证据。",
            )
        } else {
            SubjectPlan(
                summary = "本节学习“$title”，需要明确研究对象、条件、规律及其证据，并知道结论的适用范围。",
                objectives = listOf("识别核心对象和物理化学量", "说明条件、变化和规律之间的关系", "使用定义、图示、公式或证据解决基本问题"),
                misconception = "不要只背公式或反应结论；使用前必须确认对象、条件、单位和适用范围。",
                sceneType = LessonSceneType.PROCESS,
                sceneTitle = "条件如何导向规律",
                prompt = "在“$title”中，研究对象和条件如何一步步导向结论？",
                conclusion = "先定义对象和条件，再建立量或现象之间的关系，最后用证据检验。",
                steps = listOf("确定研究对象", "列出条件和已知量", "建立关系", "推导或观察结果", "检查范围与单位"),
                question = "使用“$title”的规律前，首先要确认什么？",
                acceptedAnswers = listOf("条件和适用范围", "适用条件", "研究对象"),
                exerciseExplanation = "$subject 规律不是脱离条件的口号，应用前必须确认对象和适用范围。",
            )
        }
    }

    private fun genericPlan(subject: String, role: String, title: String): SubjectPlan = SubjectPlan(
        summary = "本节学习“$title”，围绕$subject 中的核心对象、条件、方法和证据建立可复述、可应用的理解。",
        objectives = listOf("识别本节核心对象", "说明条件与结论之间的关系", "完成一次基于教材证据的应用"),
        misconception = "不要脱离条件背结论；先确认教材中的对象、范围和证据。",
        sceneType = LessonSceneType.PROCESS,
        sceneTitle = "从对象和条件到结论",
        prompt = "“$title”中，哪些对象和条件共同决定结论？",
        conclusion = "明确对象、条件、方法和证据，才能把结论迁移到新问题。",
        steps = listOf("定位主题", "识别对象", "整理条件", "形成关系", "用证据检验"),
        question = "理解“$title”时不能忽略什么？",
        acceptedAnswers = listOf("条件和证据", "适用条件", "教材证据", role),
        exerciseExplanation = "可靠理解需要同时包含对象、条件、关系和证据。",
    )

    private data class SubjectPlan(
        val summary: String,
        val objectives: List<String>,
        val misconception: String,
        val sceneType: LessonSceneType,
        val sceneTitle: String,
        val prompt: String,
        val expression: String = "",
        val conclusion: String,
        val steps: List<String>,
        val question: String,
        val acceptedAnswers: List<String>,
        val exerciseExplanation: String,
    )
}
