package com.majortomman.school.learning.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseUpdatePlannerTest {
    @Test
    fun missingLocalCourseUsesFullPackage() {
        val plan = CourseUpdatePlanner.plan(remote(), null)

        assertTrue(plan is CourseUpdatePlan.Full)
    }

    @Test
    fun smallChangedFileUsesIncrementalUpdate() {
        val remote = remote(
            fullSize = 1_000,
            files = listOf(
                file("course.json", 100, SHA_B),
                file("assets/figure.webp", 400, SHA_C),
            ),
        )
        val local = local(
            files = mapOf(
                "course.json" to LocalCourseFileState(100, SHA_A),
                "assets/figure.webp" to LocalCourseFileState(400, SHA_C),
            ),
        )

        val plan = CourseUpdatePlanner.plan(remote, local)

        assertTrue(plan is CourseUpdatePlan.Incremental)
        plan as CourseUpdatePlan.Incremental
        assertEquals(listOf("course.json"), plan.changedFiles.map(CourseFileSpec::path))
    }

    @Test
    fun largeChangeUsesFullPackage() {
        val remote = remote(
            fullSize = 1_000,
            files = listOf(file("course.json", 700, SHA_B)),
        )
        val local = local(files = mapOf("course.json" to LocalCourseFileState(700, SHA_A)))

        assertTrue(CourseUpdatePlanner.plan(remote, local) is CourseUpdatePlan.Full)
    }

    @Test
    fun schemaChangeUsesFullPackage() {
        val remote = remote().copy(schemaVersion = 2)
        val local = local().copy(schemaVersion = 1)

        assertTrue(CourseUpdatePlanner.plan(remote, local) is CourseUpdatePlan.Full)
    }

    @Test
    fun identicalFilesNeedNoDownload() {
        val remote = remote()
        val local = local(files = mapOf("course.json" to LocalCourseFileState(100, SHA_A)))

        assertEquals(CourseUpdatePlan.None, CourseUpdatePlanner.plan(remote, local))
    }

    @Test
    fun removedFilesAreDeletedIncrementally() {
        val remote = remote()
        val local = local(
            files = mapOf(
                "course.json" to LocalCourseFileState(100, SHA_A),
                "legacy.json" to LocalCourseFileState(50, SHA_C),
            ),
        )

        val plan = CourseUpdatePlanner.plan(remote, local)

        assertTrue(plan is CourseUpdatePlan.Incremental)
        assertEquals(listOf("legacy.json"), (plan as CourseUpdatePlan.Incremental).deletedFiles)
    }

    private fun remote(
        fullSize: Long = 1_000,
        files: List<CourseFileSpec> = listOf(file("course.json", 100, SHA_A)),
    ) = CourseTextbookManifest(
        schemaVersion = 1,
        contentVersion = 2,
        id = "pep-math-7-1",
        title = "数学七年级上册",
        version = 2,
        minimumAppVersion = 1,
        fullPackage = file("pep-math-7-1-v2.zip", fullSize, SHA_D),
        files = files,
        deletedFiles = emptyList(),
    )

    private fun local(
        files: Map<String, LocalCourseFileState> = mapOf("course.json" to LocalCourseFileState(100, SHA_A)),
    ) = LocalCourseState(
        schemaVersion = 1,
        contentVersion = 1,
        textbookVersion = 1,
        files = files,
    )

    private fun file(path: String, size: Long, sha: String) = CourseFileSpec(
        path = path,
        url = "https://drive.google.com/file/d/example/view",
        size = size,
        sha256 = sha,
    )

    private companion object {
        val SHA_A = "a".repeat(64)
        val SHA_B = "b".repeat(64)
        val SHA_C = "c".repeat(64)
        val SHA_D = "d".repeat(64)
    }
}
