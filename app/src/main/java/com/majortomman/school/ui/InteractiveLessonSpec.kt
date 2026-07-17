package com.majortomman.school.ui

import com.majortomman.school.data.Lesson

enum class InteractiveLessonKind {
    LINEAR_FUNCTION,
    NEWTON_FIRST_LAW,
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
)

object InteractiveLessonCatalog {
    fun resolve(subjectId: String, lesson: Lesson): InteractiveLessonSpec? {
        val title = lesson.title
            .replace(" ", "")
            .replace("　", "")
            .replace("（", "(")
            .replace("）", ")")
        val firstPage = lesson.textbookPages.first.coerceAtLeast(1)
        val lastPage = lesson.textbookPages.last.coerceAtLeast(firstPage)

        return when {
            subjectId == "math" && isLinearFunctionLesson(title) -> InteractiveLessonSpec(
                kind = InteractiveLessonKind.LINEAR_FUNCTION,
                badge = "数学可视化实验 · 教材顺序版",
                title = "一次函数",
                subtitle = "先看对应值怎样变化，再把点画到坐标系中",
                formula = "y = kx + b",
                sourceSummary = "教材原文（第114页）：“形如 y=kx+b 的函数，叫作一次函数。”\n\n教材并不是先讲斜率或截距，而是从登山时气温随海拔变化、铁块质量随体积变化等问题出发，写出变量之间的关系，再观察这些解析式的共同形式。",
                derivationTitle = "教材怎样引出一次函数",
                derivationSteps = listOf(
                    "大本营气温为 5 ℃，海拔每升高 1 km，气温下降 6 ℃。先用已经学过的数量关系表示变化。",
                    "登高 x km 时，气温减少 6x ℃，于是得到 y=5-6x，也可以写成 y=-6x+5。",
                    "再比较铁块质量、练习本总厚度、标准体重、矩形面积等关系式，寻找它们共同的结构。",
                    "最后才抽象出 y=kx+b，并把这一类函数命名为一次函数；这里的 k、b 暂时只理解为常数。",
                ),
                background = listOf(
                    "这一阶段的重点是识别两个变量之间是否存在确定的对应关系，并能从实际情境写出解析式。",
                    "图像学习从列表、描点、连线开始。斜率和截距可以作为后续更系统研究直线时的语言，不需要在概念刚出现时提前使用。",
                ),
                misconception = "不要把一次函数理解成“先有一条直线，再去调整两个参数”。教材的顺序是先从实际数量关系得到函数，再用表格和图像观察它。",
                sourcePage = firstPage,
                sourcePageEnd = lastPage,
            )

            subjectId == "physics" && title.contains("牛顿第一定律") -> InteractiveLessonSpec(
                kind = InteractiveLessonKind.NEWTON_FIRST_LAW,
                badge = "物理思想实验 · 教材顺序版",
                title = "牛顿第一定律",
                subtitle = "先看小球为什么停下，再想象把阻力逐渐减小",
                formula = "若没有摩擦，球将永远运动下去。",
                sourceSummary = "教材原文（第84页）：“若没有摩擦，球将永远运动下去。”\n\n教材先提出滑冰运动员为什么会慢慢停下，再说明日常经验容易受到摩擦干扰。随后通过伽利略斜面理想实验，把阻力逐渐排除，得出力不是维持运动的原因。",
                derivationTitle = "教材怎样完成这个思想实验",
                derivationSteps = listOf(
                    "小球沿斜面向下滚动时越来越快，沿另一斜面向上滚动时越来越慢，并趋向回到原来的高度。",
                    "第二个斜面越平缓，小球为了达到原来的高度就要运动得越远。",
                    "现实中小球最终会停下，教材把这解释为摩擦等阻力持续影响运动。",
                    "继续想象把阻力去掉、把第二个斜面放到水平，小球就没有停下来的理由，将一直运动下去。",
                ),
                background = listOf(
                    "这是理想实验：从真实实验出发，逐步去掉干扰因素，再用逻辑推理抓住运动和力关系的本质。",
                    "本节不需要使用摩擦系数，也不需要借用后面才系统学习的牛顿第二定律公式。只比较阻力明显、阻力较小和理想无阻力三种情形。",
                ),
                misconception = "小球在现实地面上停下，不代表运动必须由力维持；它说明阻力正在改变小球原来的运动状态。",
                sourcePage = firstPage,
                sourcePageEnd = lastPage,
            )

            else -> null
        }
    }

    private fun isLinearFunctionLesson(title: String): Boolean =
        title == "一次函数" ||
            title.contains("一次函数的概念") ||
            title.contains("一次函数的图象和性质")
}
