package dev.tvlauncher.audit

import dev.tvlauncher.ui.Theme
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetSizeTest {
    @Test fun topBarPillMinimum_matchesTheAuditorsFocusTargetFloor() {
        // The top-bar pills use Theme.MIN_TARGET_DP as their minimum size; the auditor flags anything smaller.
        assertEquals(AuditConfig().minTargetDp, Theme.MIN_TARGET_DP.toFloat(), 0f)
    }
}
