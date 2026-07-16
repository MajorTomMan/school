package com.majortomman.school.data.material

import java.io.File
import java.util.Base64
import java.util.zip.GZIPInputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledTextbookCatalogIndexTest {
    @Test
    fun `all-subject index contains 34 books and 1027 hierarchical lessons`() {
        val files = listOf(
            "index-00.b64",
            "index-01.b64",
            "index-02.b64",
            "index-03a.b64",
            "index-03b.b64",
            "index-04a.b64",
            "index-04b.b64",
        ).map { name -> File("src/main/assets/prebuilt/textbooks/$name") }
        assertTrue("全学科预制目录文件不存在", files.all(File::isFile))
        val encoded = files.joinToString(separator = "") { it.readText(Charsets.US_ASCII) }
        val compressed = Base64.getMimeDecoder().decode(encoded)
        val root = GZIPInputStream(compressed.inputStream()).bufferedReader(Charsets.UTF_8).use { reader ->
            JSONObject(reader.readText())
        }
        val books = root.getJSONArray("books")
        val hashes = mutableSetOf<String>()
        val slots = mutableSetOf<String>()
        val subjects = mutableSetOf<String>()
        var lessonCount = 0

        assertEquals(2, root.getInt("schemaVersion"))
        assertEquals(34, books.length())
        assertEquals(34, root.getInt("bookCount"))
        assertEquals(1_027, root.getInt("lessonCount"))

        for (bookIndex in 0 until books.length()) {
            val book = books.getJSONObject(bookIndex)
            val title = book.getString("title")
            val hash = book.getString("sha256")
            val pageCount = book.getInt("pageCount")
            val offset = book.getInt("pageIndexOffset")
            val subjectId = book.getString("subjectId")
            val subjectTitle = book.getString("subjectTitle")
            val stage = EducationStage.fromId(book.getString("stage"))
                ?: error("教材阶段无法识别：$title")
            val slot = TextbookSlot(
                subjectId = subjectId,
                subjectTitle = subjectTitle,
                grade = book.getInt("grade"),
                volume = TextbookVolume.fromId(book.getInt("volume")),
                stage = stage,
            )
            val lessons = book.getJSONArray("lessons")

            assertEquals(64, hash.length)
            assertTrue("教材指纹重复：$title", hashes.add(hash))
            assertTrue("教材槽位重复：${slot.key}", slots.add(slot.key))
            assertTrue(pageCount > 50)
            assertTrue(offset in -20..80)
            assertTrue(lessons.length() > 0)
            subjects += subjectId

            val lessonIds = mutableSetOf<String>()
            for (lessonIndex in 0 until lessons.length()) {
                val lesson = lessons.getJSONObject(lessonIndex)
                val id = lesson.getString("id")
                val lessonTitle = lesson.getString("title")
                val start = lesson.getInt("pageStart")
                val end = lesson.getInt("pageEnd")
                val role = lesson.getString("role")
                val path = lesson.getJSONArray("path")

                assertTrue("课程 ID 重复：$title / $id", lessonIds.add(id))
                assertTrue(lessonTitle.isNotBlank())
                assertTrue(role.isNotBlank())
                assertTrue(start > 0)
                assertTrue(end >= start)
                assertTrue(start - 1 + offset in 0 until pageCount)
                assertTrue(end - 1 + offset in 0 until pageCount)

                for (pathIndex in 0 until path.length()) {
                    val node = path.getJSONObject(pathIndex)
                    assertTrue(node.getString("id").isNotBlank())
                    assertTrue(node.getString("title").isNotBlank())
                    assertTrue(
                        node.getString("type") in setOf(
                            "STAGE", "LEVEL", "TERM", "COURSE", "MODULE", "UNIT", "CHAPTER", "LESSON", "TOPIC",
                        ),
                    )
                }
                lessonCount += 1
            }
        }

        assertEquals(setOf("chinese", "english", "japanese", "physics", "chemistry"), subjects)
        assertEquals(1_027, lessonCount)
    }

    @Test
    fun `extended senior-high volumes remain stable`() {
        val expected = mapOf(
            101 to TextbookVolume.COMPULSORY_1,
            102 to TextbookVolume.COMPULSORY_2,
            103 to TextbookVolume.COMPULSORY_3,
            111 to TextbookVolume.COMPULSORY_UPPER,
            112 to TextbookVolume.COMPULSORY_LOWER,
            201 to TextbookVolume.SELECTIVE_1,
            202 to TextbookVolume.SELECTIVE_2,
            203 to TextbookVolume.SELECTIVE_3,
            204 to TextbookVolume.SELECTIVE_4,
            211 to TextbookVolume.SELECTIVE_UPPER,
            212 to TextbookVolume.SELECTIVE_MIDDLE,
            213 to TextbookVolume.SELECTIVE_LOWER,
        )
        expected.forEach { (id, volume) -> assertEquals(volume, TextbookVolume.fromId(id)) }
        assertTrue(TextbookVolume.optionsFor(EducationStage.SENIOR_HIGH, "english").size == 7)
        assertTrue(SubjectTemplates.find("japanese") != null)
    }
}
