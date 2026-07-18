package com.majortomman.school.learning.verification

enum class VerificationSubject(
    val label: String,
    val subtitle: String,
    val inputExample: String,
) {
    MATHEMATICS(
        label = "数学",
        subtitle = "公式 · 等式 · 不等式 · 复平面 · 空间坐标",
        inputExample = "√72=6√2",
    ),
    PHYSICS(
        label = "物理",
        subtitle = "教材模型 · 条件 · 单位 · 数值关系",
        inputExample = "U=I·R",
    ),
    CHEMISTRY(
        label = "化学",
        subtitle = "化学式 · 方程式 · 配平 · 有机结构",
        inputExample = "Fe+O2→Fe2O3",
    ),
    BIOLOGY(
        label = "生物",
        subtitle = "数量关系 · 结构 · 过程 · 实验条件",
        inputExample = "能量传递效率",
    ),
    ENGLISH(
        label = "英语",
        subtitle = "任意句子构造 · 词形 · 语序 · 语法结构",
        inputExample = "He plays basketball after school.",
    ),
    JAPANESE(
        label = "日语",
        subtitle = "任意句子构造 · 助词 · 活用 · 语体结构",
        inputExample = "私は学校へ行きます。",
    ),
}

data class VerificationHubCapability(
    val subject: VerificationSubject,
    val deterministic: Boolean,
    val limitation: String,
)

object VerificationHubCatalog {
    val subjects: List<VerificationSubject> = VerificationSubject.entries

    fun capability(subject: VerificationSubject): VerificationHubCapability = when (subject) {
        VerificationSubject.MATHEMATICS -> VerificationHubCapability(
            subject,
            deterministic = true,
            limitation = "样本一致性检查不是严格恒等证明；超出当前表达式语法时会明确拒绝。",
        )
        VerificationSubject.PHYSICS -> VerificationHubCapability(
            subject,
            deterministic = true,
            limitation = "只验证已声明的教材物理模型；必须确认适用条件并自行统一单位。",
        )
        VerificationSubject.CHEMISTRY -> VerificationHubCapability(
            subject,
            deterministic = true,
            limitation = "可以验证和配平已给出的完整方程式，但不会仅凭反应物猜测未知产物。",
        )
        VerificationSubject.BIOLOGY -> VerificationHubCapability(
            subject,
            deterministic = true,
            limitation = "只验证已声明的教材数量关系；统计口径、实验条件和单位必须先确认。",
        )
        VerificationSubject.ENGLISH -> VerificationHubCapability(
            subject,
            deterministic = false,
            limitation = "规则分析器可解释基础结构与常见错误；复杂从句、习语和语义自然度会返回歧义或能力边界。",
        )
        VerificationSubject.JAPANESE -> VerificationHubCapability(
            subject,
            deterministic = false,
            limitation = "规则分析器可解释基础助词、谓语和语体；省略、复杂连体修饰和强文脉依赖不会被判成绝对正确。",
        )
    }
}
