package com.majortomman.school.data.curriculum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurriculumSnapshotTest {
    @Test
    fun `tree keeps parent order depth and legacy lesson mapping`() {
        val curriculum = Curriculum(
            id = "math-junior",
            subjectId = "math",
            title = "初中数学",
            levelSystemId = BuiltinCurriculumCatalog.BASIC_EDUCATION_LEVEL_SYSTEM,
            standard = null,
            region = "CN",
            version = "1",
            source = CurriculumSource.BUILTIN,
        )
        val root = CurriculumNode("root", curriculum.id, null, CurriculumNodeType.ROOT, "初中数学", 0)
        val grade = CurriculumNode("grade-7", curriculum.id, root.id, CurriculumNodeType.LEVEL, "七年级", 0)
        val term = CurriculumNode("term-1", curriculum.id, grade.id, CurriculumNodeType.TERM, "上册", 0)
        val second = CurriculumNode("lesson-2", curriculum.id, term.id, CurriculumNodeType.LESSON, "数轴", 2, "math-7-1:number-line")
        val first = CurriculumNode("lesson-1", curriculum.id, term.id, CurriculumNodeType.LESSON, "正数和负数", 1, "math-7-1:positive-negative")
        val snapshot = CurriculumSnapshot(
            subjects = listOf(BuiltinCurriculumCatalog.subject("math")),
            curricula = listOf(curriculum),
            nodes = listOf(second, root, term, first, grade),
        )

        val rows = snapshot.flattenedTree(curriculum.id)
        assertEquals(listOf("root", "grade-7", "term-1", "lesson-1", "lesson-2"), rows.map { it.node.id })
        assertEquals(listOf(0, 1, 2, 3, 3), rows.map { it.depth })
        assertEquals("lesson-2", snapshot.nodeForLegacyLesson("math-7-1:number-line")?.id)
        assertTrue(snapshot.validate().isEmpty())
    }

    @Test
    fun `knowledge graph supports multiple prerequisites`() {
        val points = BuiltinCurriculumCatalog.knowledgePoints
        val snapshot = CurriculumSnapshot(
            knowledgePoints = points,
            knowledgeRelations = BuiltinCurriculumCatalog.knowledgeRelations,
        )

        val prerequisites = snapshot.prerequisites("rational-compare").map { it.id }.toSet()
        assertEquals(setOf("positive-negative", "number-line", "absolute-value"), prerequisites)
    }

    @Test
    fun `validation rejects tree cycle and broken knowledge reference`() {
        val curriculum = Curriculum(
            id = "broken",
            subjectId = "math",
            title = "错误课程",
            levelSystemId = null,
            standard = null,
            region = null,
            version = "1",
            source = CurriculumSource.USER,
        )
        val snapshot = CurriculumSnapshot(
            curricula = listOf(curriculum),
            nodes = listOf(
                CurriculumNode("a", curriculum.id, "b", CurriculumNodeType.UNIT, "A", 0),
                CurriculumNode("b", curriculum.id, "a", CurriculumNodeType.UNIT, "B", 0),
            ),
            knowledgePoints = listOf(
                KnowledgePoint("known", "math", "已知", "", KnowledgeKind.CONCEPT),
            ),
            knowledgeRelations = listOf(
                KnowledgeRelation("missing", "known", KnowledgeRelationType.PREREQUISITE),
            ),
        )

        val errors = snapshot.validate()
        assertTrue(errors.any { "循环" in it })
        assertTrue(errors.any { "不存在的知识点" in it })
    }

    @Test
    fun `builtin subjects and knowledge identifiers are unique`() {
        assertEquals(
            BuiltinCurriculumCatalog.subjects.size,
            BuiltinCurriculumCatalog.subjects.map { it.id }.toSet().size,
        )
        assertEquals(
            BuiltinCurriculumCatalog.knowledgePoints.size,
            BuiltinCurriculumCatalog.knowledgePoints.map { it.id }.toSet().size,
        )
        assertTrue(BuiltinCurriculumCatalog.subject("math").capabilityIds.contains(LearningCapability.STEP_EVALUATION))
        assertTrue(BuiltinCurriculumCatalog.subject("english").capabilityIds.contains(LearningCapability.PRONUNCIATION))
        assertTrue(BuiltinCurriculumCatalog.subject("history").capabilityIds.contains(LearningCapability.TIMELINE))
    }

    @Test
    fun `generated knowledge id is stable across repeated imports`() {
        val first = BuiltinCurriculumCatalog.inferKnowledge("physics", "牛顿第一定律")
        val second = BuiltinCurriculumCatalog.inferKnowledge("physics", "牛顿第一定律")
        assertEquals(first.id, second.id)
        assertEquals("physics", first.subjectId)
    }
}
