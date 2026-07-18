package com.majortomman.school.ui

import com.majortomman.school.data.Lesson
import com.majortomman.school.learning.course.BiologyCourseContentFactory
import com.majortomman.school.learning.course.ChemistryCourseContentFactory
import com.majortomman.school.learning.course.LessonEnrichment
import com.majortomman.school.learning.course.MathCourseContentFactory
import com.majortomman.school.learning.course.PhysicsCourseContentFactory

enum class InteractiveLessonKind {
    LINEAR_FUNCTION,
    NEWTON_FIRST_LAW,
    MATH_GENERAL,
    PHYSICS_GENERAL,
    SCIENCE_GENERAL,
}

data class InteractiveLessonSpec(
    val kind: InteractiveLessonKind,
    val badge: String,
    val title: String,
    val subtitle: String,
    val formula: String,
    val sourceSummary: String,
    val derivationTitle: String,
    val derivationSteps: List<String>,
    val background: List<String>,
    val misconception: String,
    val sourcePage: Int,
    val sourcePageEnd: Int,
    val enrichment: LessonEnrichment = LessonEnrichment(),
)

object InteractiveLessonCatalog {
    fun resolve(subjectId: String, lesson: Lesson): InteractiveLessonSpec? {
        val title = lesson.title.replace(" ", "").replace("　", "").replace("（", "(").replace("）", ")")
        val firstPage = lesson.textbookPages.first.coerceAtLeast(1)
        val lastPage = lesson.textbookPages.last.coerceAtLeast(firstPage)
        return when {
            subjectId == "math" && isLinearFunctionLesson(title) -> linearFunction(lesson, firstPage, lastPage)
            subjectId == "physics" && title.contains("牛顿第一定律") -> newtonFirstLaw(firstPage, lastPage)
            subjectId == "math" -> generalMath(lesson, firstPage, lastPage)
            subjectId == "physics" -> generalPhysics(lesson, firstPage, lastPage)
            subjectId == "chemistry" -> generalChemistry(lesson, firstPage, lastPage)
            subjectId == "biology" -> generalBiology(lesson, firstPage, lastPage)
            else -> null
        }
    }

    private fun linearFunction(lesson: Lesson, firstPage: Int, lastPage: Int): InteractiveLessonSpec {
        val content = MathCourseContentFactory.create(lesson)
        return InteractiveLessonSpec(
            kind = InteractiveLessonKind.LINEAR_FUNCTION,
            badge = "数学可视化课程 · 教材顺序版",
            title = "一次函数",
            subtitle = "先看对应值怎样变化，再把点画到坐标系中",
            formula = "y = kx + b",
            sourceSummary = "教材第114页给出一次函数定义。课程先从实际数量关系写出解析式，再观察共同形式；后续术语不在概念刚出现时提前作为前提。",
            derivationTitle = "教材怎样引出一次函数",
            derivationSteps = listOf(
                "从实际问题识别两个变量。",
                "根据变化规律写出具体数量关系。",
                "比较多个关系式的共同结构，再抽象为 y=kx+b。",
                "列表计算对应值，描点并观察图像。",
            ),
            background = content.background,
            misconception = "不要把一次函数理解成先有一条直线再调参数；教材顺序是先得到变量关系，再用表格和图像观察。",
            sourcePage = firstPage,
            sourcePageEnd = lastPage,
            enrichment = content.enrichment,
        )
    }

    private fun newtonFirstLaw(firstPage: Int, lastPage: Int) = InteractiveLessonSpec(
        kind = InteractiveLessonKind.NEWTON_FIRST_LAW,
        badge = "物理思想实验 · 教材顺序版",
        title = "牛顿第一定律",
        subtitle = "先看小球为什么停下，再想象把阻力逐渐减小",
        formula = "若没有摩擦，球将保持运动。",
        sourceSummary = "教材第84页通过伽利略斜面理想实验逐渐排除阻力，说明力不是维持运动的原因。",
        derivationTitle = "教材怎样完成这个思想实验",
        derivationSteps = listOf(
            "观察小球沿两个斜面运动并趋向回到原高度。",
            "第二个斜面越平缓，小球运动距离越远。",
            "把现实中的停止归因于摩擦等阻力。",
            "继续理想化到无阻力水平面，得到保持运动的结论。",
        ),
        background = listOf(
            "理想实验从真实观察出发，逐步去掉干扰因素，再用逻辑推理抓住本质。",
            "本节不提前使用摩擦系数或牛顿第二定律公式。",
        ),
        misconception = "现实小球停下说明阻力改变了运动状态，不说明运动必须由力维持。",
        sourcePage = firstPage,
        sourcePageEnd = lastPage,
    )

    private fun generalMath(lesson: Lesson, firstPage: Int, lastPage: Int): InteractiveLessonSpec {
        val content = MathCourseContentFactory.create(lesson)
        return InteractiveLessonSpec(
            kind = InteractiveLessonKind.MATH_GENERAL,
            badge = "数学课程 · 教材页码约束",
            title = lesson.title,
            subtitle = content.subtitle,
            formula = content.representativeExpression,
            sourceSummary = content.sourceSummary,
            derivationTitle = content.reasoningTitle,
            derivationSteps = content.reasoningSteps,
            background = content.background,
            misconception = content.misconception,
            sourcePage = firstPage,
            sourcePageEnd = lastPage,
            enrichment = content.enrichment,
        )
    }

    private fun generalPhysics(lesson: Lesson, firstPage: Int, lastPage: Int): InteractiveLessonSpec {
        val content = PhysicsCourseContentFactory.create(lesson)
        return InteractiveLessonSpec(
            kind = InteractiveLessonKind.PHYSICS_GENERAL,
            badge = "物理课程 · 教材模型约束",
            title = lesson.title,
            subtitle = content.subtitle,
            formula = content.formula,
            sourceSummary = content.sourceSummary,
            derivationTitle = "按教材顺序理解${lesson.title}",
            derivationSteps = content.steps,
            background = content.background,
            misconception = content.misconception,
            sourcePage = firstPage,
            sourcePageEnd = lastPage,
            enrichment = content.enrichment,
        )
    }

    private fun generalChemistry(lesson: Lesson, firstPage: Int, lastPage: Int): InteractiveLessonSpec {
        val content = ChemistryCourseContentFactory.create(lesson)
        return InteractiveLessonSpec(
            kind = InteractiveLessonKind.SCIENCE_GENERAL,
            badge = "化学课程 · 组成与守恒约束",
            title = lesson.title,
            subtitle = content.subtitle,
            formula = content.formula,
            sourceSummary = content.sourceSummary,
            derivationTitle = "按教材顺序理解${lesson.title}",
            derivationSteps = content.steps,
            background = content.background,
            misconception = content.misconception,
            sourcePage = firstPage,
            sourcePageEnd = lastPage,
            enrichment = content.enrichment,
        )
    }

    private fun generalBiology(lesson: Lesson, firstPage: Int, lastPage: Int): InteractiveLessonSpec {
        val content = BiologyCourseContentFactory.create(lesson)
        return InteractiveLessonSpec(
            kind = InteractiveLessonKind.SCIENCE_GENERAL,
            badge = "生物课程 · 结构过程与证据约束",
            title = lesson.title,
            subtitle = content.subtitle,
            formula = content.formula,
            sourceSummary = content.sourceSummary,
            derivationTitle = "按教材顺序理解${lesson.title}",
            derivationSteps = content.steps,
            background = content.background,
            misconception = content.misconception,
            sourcePage = firstPage,
            sourcePageEnd = lastPage,
            enrichment = content.enrichment,
        )
    }

    private fun isLinearFunctionLesson(title: String): Boolean =
        title == "一次函数" || title.contains("一次函数的概念") || title.contains("一次函数的图象和性质")
}
