package com.majortomman.school.ui

import androidx.compose.runtime.Composable

@Composable
internal fun ScienceCourseWorkbenchHost(spec: InteractiveLessonSpec) {
    when {
        spec.badge.startsWith("生物课程") -> BiologyCourseWorkbench(spec)
        else -> ChemistryCourseWorkbench(spec)
    }
}
