package com.majortomman.school.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupInitializationCoordinatorTest {
    @Test
    fun currentMarkerUsesFastPath() {
        assertFalse(
            StartupInitializationCoordinator.needsPrebuiltBootstrap(
                StartupInitializationCoordinator.CURRENT_PREBUILT_VERSION,
            ),
        )
    }

    @Test
    fun missingOrOlderMarkerSchedulesBackgroundBootstrap() {
        assertTrue(StartupInitializationCoordinator.needsPrebuiltBootstrap(null))
        assertTrue(StartupInitializationCoordinator.needsPrebuiltBootstrap("startup-v1"))
    }
}
