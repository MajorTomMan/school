package com.majortomman.school.learning.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseUpdatePlannerTest {
    @Test
    fun missingLocalCourseUsesFullPackage() {
        assertTrue(CourseUpdatePlanner.plan(remote(), null) is CourseUpdatePlan.Full)
    }

    @Test
    fun smallChangedFileUsesIncrementalUpdate() {
        val remote = remote(
            packageSize = 1_000,
            files = listOf(file("course.json", 100, SHA_B), file("assets/figure.webp", 400, SHA_C)),
        )
        val local = local(mapOf(
            "course.json" to LocalCourseFileState(100, SHA_A),
            "assets/figure.webp" to LocalCourseFileState(400, SHA_C),
        ))

        val plan = CourseUpdatePlanner.plan(remote, local) as CourseUpdatePlan.Incremental
        assertEquals(listOf("course.json"), plan.changedFiles.map(CourseFileSpec::path))
    }

    @Test
    fun externalPdfCostIsIncludedInFullTransferDecision() {
        val remote = remote(
            packageSize = 1_000,
            files = listOf(
                file("course.json", 100, SHA_A),
                file("assets/textbook.pdf", 700, SHA_B, bundled = false),
            ),
        )
        val local = local(mapOf(
            "course.json" to LocalCourseFileState(100, SHA_A),
            "assets/textbook.pdf" to LocalCourseFileState(700, SHA_A),
        ))

        val plan = CourseUpdatePlanner.plan(remote, local) as CourseUpdatePlan.Incremental
        assertEquals(listOf("assets/textbook.pdf"), plan.changedFiles.map(CourseFileSpec::path))
    }

    @Test
    fun identicalFilesNeedNoDownload() {
        assertEquals(CourseUpdatePlan.None, CourseUpdatePlanner.plan(remote(), local()))
    }

    @Test
    fun removedFilesAreDerivedFromFileSets() {
        val plan = CourseUpdatePlanner.plan(
            remote(),
            local(mapOf(
                "course.json" to LocalCourseFileState(100, SHA_A),
                "legacy.json" to LocalCourseFileState(50, SHA_C),
            )),
        ) as CourseUpdatePlan.Incremental

        assertEquals(listOf("legacy.json"), plan.deletedFiles)
    }

    @Test
    fun manifestContainsOnlyIntegrityAndDownloadFields() {
        val manifest = CourseManifestCodec.decode(
            """
            {
              "textbooks": [{
                "id": "pep-math-7-1",
                "package": {
                  "path": "pep-math-7-1.zip",
                  "url": "https://github.com/example.zip",
                  "size": 100,
                  "sha256": "$SHA_D"
                },
                "files": [
                  {
                    "path": "course.json",
                    "url": "https://github.com/course.json",
                    "size": 100,
                    "sha256": "$SHA_A",
                    "bundled": true
                  },
                  {
                    "path": "assets/textbook.pdf",
                    "url": "https://drive.google.com/file/d/example/view",
                    "size": 700,
                    "sha256": "$SHA_B",
                    "bundled": false
                  }
                ]
              }]
            }
            """.trimIndent(),
        )

        assertFalse(manifest.textbooks.single().files.last().bundled)
    }


    @Test
    fun duplicatedBusinessMetadataAndArchiveOnlyFieldsAreRejected() {
        val manifest = validManifest()
        assertThrows(IllegalArgumentException::class.java) {
            CourseManifestCodec.decode(manifest.replace("\"package\":", "\"title\":\"重复标题\",\"package\":"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CourseManifestCodec.decode(manifest.replace("\"sha256\": \"$SHA_D\"", "\"sha256\": \"$SHA_D\",\"bundled\": true"))
        }
    }

    @Test
    fun versionFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CourseManifestCodec.decode("{\"schemaVersion\":1,\"textbooks\":[]}")
        }
    }


    private fun validManifest(): String = """
        {
          "textbooks": [{
            "id": "pep-math-7-1",
            "package": {
              "path": "pep-math-7-1.zip",
              "url": "https://github.com/example.zip",
              "size": 100,
              "sha256": "$SHA_D"
            },
            "files": [{
              "path": "course.json",
              "url": "https://github.com/course.json",
              "size": 100,
              "sha256": "$SHA_A",
              "bundled": true
            }]
          }]
        }
    """.trimIndent()

    private fun remote(
        packageSize: Long = 1_000,
        files: List<CourseFileSpec> = listOf(file("course.json", 100, SHA_A)),
    ) = CourseTextbookManifest(
        id = "pep-math-7-1",
        packageFile = CourseArchiveSpec(
            path = "pep-math-7-1.zip",
            url = "https://example.com/pep-math-7-1.zip",
            size = packageSize,
            sha256 = SHA_D,
        ),
        files = files,
    )

    private fun local(
        files: Map<String, LocalCourseFileState> = mapOf("course.json" to LocalCourseFileState(100, SHA_A)),
    ) = LocalCourseState(files)

    private fun file(
        path: String,
        size: Long,
        sha: String,
        bundled: Boolean = true,
    ) = CourseFileSpec(
        path = path,
        url = "https://example.com/$path",
        size = size,
        sha256 = sha,
        bundled = bundled,
    )

    private companion object {
        val SHA_A = "a".repeat(64)
        val SHA_B = "b".repeat(64)
        val SHA_C = "c".repeat(64)
        val SHA_D = "d".repeat(64)
    }
}
