package dev.tvlauncher.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditCommandsTest {
    @Test
    fun releaseOrUnknownAction_ignored() {
        assertNull(AuditCommands.decide(AuditCommands.AUDIT, debuggable = false, rawLabel = "x", rawNonce = null))
        assertNull(AuditCommands.decide(null, true, "x", null))
        assertNull(AuditCommands.decide("dev.tvlauncher.debug.TRIP_SAFE_MODE", true, "x", null))
    }

    @Test
    fun decide_modes() {
        assertEquals(AuditCommands.Command(AuditCommands.Mode.AUDIT, "home-1", "ab12"),
            AuditCommands.decide(AuditCommands.AUDIT, true, "home-1", "ab12"))
        assertEquals(AuditCommands.Mode.DUMP_TREE, AuditCommands.decide(AuditCommands.DUMP_TREE, true, null, null)!!.mode)
    }

    @Test
    fun label_sanitized() {
        assertEquals("home-focus_2.v1", AuditCommands.sanitizeLabel("home-focus_2.v1"))
        assertEquals("....etcpasswd", AuditCommands.sanitizeLabel("../../etc/passwd")) // no separators survive
        assertEquals("abrm-rf", AuditCommands.sanitizeLabel("a;b \$(rm -rf)"))
        assertEquals(40, AuditCommands.sanitizeLabel("x".repeat(100)).length)
        for (bad in listOf(null, 7, "", ";;;", "..", "/")) assertEquals(AuditCommands.DEFAULT_LABEL, AuditCommands.sanitizeLabel(bad))
    }

    @Test
    fun nonce_strict() {
        assertEquals("Ab9", AuditCommands.sanitizeNonce("Ab9"))
        for (bad in listOf(null, 5, "", "a b", "x".repeat(17), "a;b")) assertNull(AuditCommands.sanitizeNonce(bad))
    }

    @Test
    fun files_and_logLine() {
        val c = AuditCommands.Command(AuditCommands.Mode.AUDIT, "home", "n1")
        assertEquals("home-5.json", AuditCommands.fileName(c, 5))
        assertEquals("latest.json", AuditCommands.latestName(c))
        assertEquals("LAYOUT_AUDIT label=home findings=3 high=1 file=/x/home-5.json nonce=n1",
            AuditCommands.logLine(c, 3, 1, 99, "/x/home-5.json", null))
        val t = c.copy(mode = AuditCommands.Mode.DUMP_TREE, nonce = null)
        assertEquals("home-5-tree.json", AuditCommands.fileName(t, 5))
        val line = AuditCommands.logLine(t, 0, 0, 12, null, "no resumed activity")
        assertEquals("LAYOUT_AUDIT_TREE label=home nodes=12 file=- error=no_resumed_activity", line)
        assertTrue(!line.contains("LAYOUT_AUDIT label="))
    }
}
