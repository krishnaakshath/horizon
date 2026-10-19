package dev.tvlauncher.room

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoomIconsTest {
    @Test fun every_choice_has_a_24dp_vector() {
        for (c in RoomChoice.values()) {
            val f = File("src/main/res/drawable/${RoomQuickAction.iconKey(c)}.xml")
            assertTrue("missing ${f.path}", f.isFile)
            val xml = f.readText()
            assertTrue(xml.contains("<vector") && xml.contains("android:width=\"24dp\"") && xml.contains("android:viewportWidth=\"24\""))
        }
    }
}
