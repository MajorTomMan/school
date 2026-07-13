package com.majortomman.school.data.material

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonAnalysisModelsTest {
    private val slot = TextbookSlot("math", "数学", 7, TextbookVolume.FIRST)
    private val lesson = GeneratedLesson(
        id = "math-7-1:number-line",
        sourceId = "number-line",
        title = "数轴",
        subtitle = "教材第 15—20 页",
        estimatedMinutes = 18,
        pageStart = 15,
        pageEnd = 20,
        objectives = emptyList(),
        explanation = "",
        commonMistake = "",
    )

    @Test
    fun fallbackBuildsNumberLineScene() {
        val analysis = LessonAnalysisFallback.generate(slot, lesson)

        assertEquals(LessonSceneType.NUMBER_LINE, analysis.scene.type)
        assertEquals(listOf(-3.0, 2.0), analysis.scene.values)
        assertEquals(15, analysis.scene.sourcePage)
        assertTrue(analysis.exercise.acceptedAnswers.isNotEmpty())
    }

    @Test
    fun modelResponseIsNormalizedAndBoundToLesson() {
        val raw = """
            ```json
            {
              "summary":"数轴把数表示为位置。",
              "objectives":["认识原点","认识正方向"],
              "misconception":"缺少单位长度。",
              "scene":{
                "type":"number_line",
                "title":"位置",
                "prompt":"观察 -2 和 1",
                "values":[-2,1],
                "labels":["-2","1"],
                "expression":"-2 < 1",
                "conclusion":"右边更大",
                "steps":["画轴","放点"],
                "sourcePage":16
              },
              "exercise":{
                "question":"谁更大？",
                "acceptedAnswers":["1"],
                "hints":["看左右"],
                "explanation":"1 在右边"
              }
            }
            ```
        """.trimIndent()

        val analysis = LessonAnalysis.fromModelResponse(raw, lesson)

        assertEquals("number-line", analysis.lessonSourceId)
        assertEquals(LessonAnalysisSource.AI_VISION, analysis.source)
        assertEquals(15..20, analysis.sourcePages)
        assertEquals(LessonSceneType.NUMBER_LINE, analysis.scene.type)
        assertEquals(16, analysis.scene.sourcePage)
        assertEquals("谁更大？", analysis.exercise.question)
    }
}
