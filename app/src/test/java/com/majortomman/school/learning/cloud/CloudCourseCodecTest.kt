package com.majortomman.school.learning.cloud

import com.majortomman.school.learning.course.CoursePageBlockKind
import com.majortomman.school.learning.course.RationalVisualizationKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCourseCodecTest {
    @Test
    fun lessonAliasLoadsPagedCourseWithExercise() {
        val pages = CloudCourseCodec.pagesFor(JSONObject(SAMPLE_COURSE), "有理数的大小比较", 14..24)

        assertEquals("有理数", pages.first().title)
        assertTrue(pages.any { it.title == "随堂练习" })
        assertTrue(pages.any { it.visualization == RationalVisualizationKind.NUMBER_COMPARISON })
        assertTrue(pages.all { it.sourcePage in 14..24 })
        assertEquals(22, pages.first { it.title == "大小比较" }.sourcePageEnd)
    }

    @Test
    fun chapterRouteIncludesReviewPages() {
        val pages = CloudCourseCodec.pagesFor(JSONObject(SAMPLE_COURSE), "有理数", 1..24)

        assertTrue(pages.any { it.title == "本章知识结构" })
        assertTrue(pages.any { it.title == "章末练习" })
    }


    @Test
    fun textbookBlocksKeepPdfOrderAndVisualizationParameters() {
        val pages = CloudCourseCodec.pagesFor(JSONObject(ORDERED_BLOCK_COURSE), "函数的表示", 100..100)
        val page = pages.single()

        assertEquals(
            listOf(
                CoursePageBlockKind.TEXTBOOK_TEXT,
                CoursePageBlockKind.PROMPT,
                CoursePageBlockKind.WORKED_EXAMPLE,
                CoursePageBlockKind.VISUALIZATION,
                CoursePageBlockKind.CONCLUSION,
            ),
            page.blocks.map { it.kind },
        )
        assertEquals(RationalVisualizationKind.FUNCTION_GRAPH, page.blocks[3].visualization)
        assertEquals("linear", page.blocks[3].visualizationParams["mode"])
    }

    @Test
    fun allJuniorMathRendererNamesAreAccepted() {
        val renderers = listOf(
            "rational_definition_flow", "equation_balance", "equation_system",
            "inequality_number_line", "coordinate_plane", "intersecting_lines",
            "parallel_lines", "translation", "triangle", "congruent_triangles",
            "axis_symmetry", "pythagorean", "quadrilateral", "function_relation",
            "function_graph", "statistics", "probability", "rotation", "circle",
            "similarity", "right_triangle", "projection", "algebra_process",
        )
        renderers.forEachIndexed { index, renderer ->
            val course = JSONObject(
                ORDERED_BLOCK_COURSE.replace(
                    "\"renderer\":\"function_graph\"",
                    "\"renderer\":\"$renderer\"",
                ),
            )
            val page = CloudCourseCodec.pagesFor(course, "函数的表示", 100..100).single()
            assertTrue("renderer $index should be registered", page.visualization != RationalVisualizationKind.HISTORY)
        }
    }

    @Test
    fun googleDriveShareLinkBecomesDirectDownloadLink() {
        assertEquals(
            "https://drive.google.com/uc?export=download&id=abcDEF123",
            CourseSyncManager.normalizeGoogleDriveDownloadUrl(
                "https://drive.google.com/file/d/abcDEF123/view?usp=sharing",
            ),
        )
        assertEquals(
            "https://drive.google.com/uc?export=download&id=xyz987",
            CourseSyncManager.normalizeGoogleDriveDownloadUrl(
                "https://drive.google.com/open?id=xyz987",
            ),
        )
    }

    private companion object {
        val ORDERED_BLOCK_COURSE = """
            {
              "schemaVersion":1,
              "textbook":{"id":"pep-math-8-2","title":"数学八年级下册"},
              "chapters":[{
                "id":"chapter-22","number":"第二十二章","title":"函数",
                "sections":[{
                  "id":"22.2","number":"22.2","title":"函数的表示",
                  "pages":[{
                    "id":"function-page","title":"函数的表示","sourcePage":100,
                    "blocks":[
                      {"type":"textbook_text","text":"表示函数关系时，要根据问题选择适当的方法。"},
                      {"type":"prompt","text":"思考三种表示方法之间有什么联系。"},
                      {"type":"worked_example","statement":"例：根据表格描出对应点。","steps":[]},
                      {"type":"visualization","renderer":"function_graph","params":{"mode":"linear"}},
                      {"type":"conclusion","text":"函数可以用解析式、列表和图象表示。"}
                    ]
                  }]
                }]
              }]
            }
        """.trimIndent()

        val SAMPLE_COURSE = """
            {
              "schemaVersion": 1,
              "textbook": {"id":"pep-math-7-1","title":"数学七年级上册"},
              "chapters": [
                {
                  "id":"chapter-01",
                  "number":"第一章",
                  "title":"有理数",
                  "sections":[
                    {
                      "id":"1.2",
                      "number":"1.2",
                      "title":"有理数及其大小比较",
                      "aliases":["有理数的大小比较"],
                      "pages":[
                        {
                          "id":"definition",
                          "title":"有理数",
                          "sourcePage":14,
                          "blocks":[
                            {"type":"textbook_text","text":"整数和分数统称为有理数。"},
                            {"type":"visualization","renderer":"rational_classification"}
                          ]
                        },
                        {
                          "id":"comparison",
                          "title":"大小比较",
                          "sourcePage":21,
                          "sourcePageEnd":22,
                          "blocks":[
                            {"type":"textbook_text","text":"数轴右边的数大于左边的数。"},
                            {"type":"visualization","renderer":"number_comparison"}
                          ]
                        },
                        {
                          "id":"exercise",
                          "title":"随堂练习",
                          "sourcePage":22,
                          "blocks":[
                            {"type":"exercise","number":"1","stem":"比较 −4 与 −2 的大小。"},
                            {"type":"visualization","renderer":"number_comparison"}
                          ]
                        }
                      ]
                    }
                  ],
                  "review": {
                    "id":"chapter-01-review",
                    "title":"第一章小结",
                    "pages":[
                      {
                        "id":"summary",
                        "title":"本章知识结构",
                        "sourcePage":21,
                        "blocks":[
                          {"type":"summary","items":["正数和负数","数轴"]},
                          {"type":"visualization","renderer":"rational_classification"}
                        ]
                      },
                      {
                        "id":"chapter-exercise",
                        "title":"章末练习",
                        "sourcePage":22,
                        "blocks":[
                          {"type":"exercise","number":"1","stem":"在数轴上表示各数。"},
                          {"type":"visualization","renderer":"number_line"}
                        ]
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
