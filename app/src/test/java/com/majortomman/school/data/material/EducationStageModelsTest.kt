package com.majortomman.school.data.material

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EducationStageModelsTest {
    @Test
    fun `legacy slot without stage is assigned from grade`() {
        val legacy = JSONObject()
            .put("subjectId", "math")
            .put("subjectTitle", "数学")
            .put("grade", 7)
            .put("volume", 1)

        val slot = TextbookSlot.fromJson(legacy)

        assertEquals(EducationStage.JUNIOR_HIGH, slot.stage)
        assertEquals("七年级数学上册", slot.displayTitle)
        assertEquals("math-7-1", slot.key)
    }

    @Test
    fun `senior and university slots use semester labels`() {
        val senior = TextbookSlot("math", "数学", 10, TextbookVolume.FIRST)
        val university = TextbookSlot("computer", "计算机", 14, TextbookVolume.SECOND)

        assertEquals(EducationStage.SENIOR_HIGH, senior.stage)
        assertEquals("高一数学上学期", senior.displayTitle)
        assertEquals(EducationStage.UNIVERSITY, university.stage)
        assertEquals("大二计算机下学期", university.displayTitle)
    }

    @Test
    fun `subject templates are filtered by education stage`() {
        val primary = SubjectTemplates.forStage(EducationStage.PRIMARY).map { it.id }
        val university = SubjectTemplates.forStage(EducationStage.UNIVERSITY).map { it.id }

        assertTrue("science" in primary)
        assertTrue("computer" in university)
        assertTrue("economics" in university)
        assertTrue("law" in university)
    }

    @Test
    fun `stage survives slot json round trip`() {
        val original = TextbookSlot(
            subjectId = "physics",
            subjectTitle = "物理",
            grade = 11,
            volume = TextbookVolume.SECOND,
            stage = EducationStage.SENIOR_HIGH,
        )

        val restored = TextbookSlot.fromJson(original.toJson())

        assertEquals(original, restored)
        assertEquals("高二物理下学期", restored.displayTitle)
    }
}
