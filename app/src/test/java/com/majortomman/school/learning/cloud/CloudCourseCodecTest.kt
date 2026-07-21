package com.majortomman.school.learning.cloud

import com.majortomman.school.learning.course.CourseSceneBlock
import com.majortomman.school.learning.course.CourseSceneTemplate
import com.majortomman.school.learning.course.CourseText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCourseCodecTest {
    @Test
    fun businessOnlyCourseDecodesOrderedBlocksAndTypedSceneData() {
        val document = CourseDocumentParser.decode(SAMPLE_COURSE)
        val page = document.chapters.single().sections.single().pages.single()

        assertEquals("有理数的概念", page.title)
        assertTrue(page.blocks[0] is CourseText)
        val scene = (page.blocks[1] as CourseSceneBlock).scene
        assertEquals(CourseSceneTemplate.NUMBER_LINE, scene.template)
        assertTrue(scene.data.boolean("signed"))
        assertEquals(6.5, scene.data.number("initial"), 0.0)
        assertEquals(listOf("定义", "例题"), page.aliases)
    }

    @Test
    fun oldProtocolFieldsAreRejectedInsteadOfSilentlyDowngraded() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            CourseDocumentParser.decode(SAMPLE_COURSE.replaceFirst("{", "{\"schemaVersion\":1,"))
        }
        assertTrue(error.message.orEmpty().contains("不识别的字段"))
    }

    @Test
    fun unsupportedSceneAndInvalidDataAreRejectedByApk() {
        assertThrows(IllegalStateException::class.java) {
            CourseDocumentParser.decode(SAMPLE_COURSE.replace("number_line", "unknown_scene"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CourseDocumentParser.decode(SAMPLE_COURSE.replace("\"signed\":true", "\"signed\":\"true\""))
        }
    }


    @Test
    fun missingRequiredBusinessFieldIsRejected() {
        val withoutSubject = SAMPLE_COURSE.replace("\"subject\":\"数学\",", "")
        val error = assertThrows(IllegalArgumentException::class.java) {
            CourseDocumentParser.decode(withoutSubject)
        }
        assertTrue(error.message.orEmpty().contains("缺少必需字段"))
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

    @Test
    fun courseContainsNoUpdateMetadata() {
        assertFalse(SAMPLE_COURSE.contains("sha256"))
        assertFalse(SAMPLE_COURSE.contains("minimumAppVersion"))
        assertFalse(SAMPLE_COURSE.contains("schemaVersion"))
    }

    private companion object {
        val SAMPLE_COURSE = """
            {
              "textbook": {
                "id":"pep-math-7-1",
                "title":"数学七年级上册",
                "publisher":"人民教育出版社",
                "edition":"人教版",
                "grade":"七年级",
                "semester":"上册",
                "subject":"数学",
                "pdf":{"path":"assets/textbook.pdf","pageCount":202,"pageIndexOffset":7}
              },
              "chapters": [{
                "id":"chapter-01",
                "number":"第一章",
                "title":"有理数",
                "aliases":[],
                "sections":[{
                  "id":"1.2.1",
                  "number":"1.2.1",
                  "title":"有理数的概念",
                  "aliases":[],
                  "pages":[{
                    "id":"concept",
                    "title":"有理数的概念",
                    "aliases":["定义","例题"],
                    "sourcePage":7,
                    "blocks":[
                      {"type":"text","style":"textbook","text":"正整数、0、负整数统称为整数。"},
                      {"type":"scene","template":"number_line","data":{"mode":"value","signed":true,"initial":6.5}}
                    ]
                  }]
                }]
              }]
            }
        """.trimIndent()
    }
}
