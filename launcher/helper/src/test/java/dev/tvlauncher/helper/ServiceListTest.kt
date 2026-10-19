package dev.tvlauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceListTest {
    private val h = ServiceList.HELPER

    @Test fun add_toEmptyNullAndBlank() {
        assertEquals(h, ServiceList.add(null))
        assertEquals(h, ServiceList.add(""))
        assertEquals(h, ServiceList.add("null"))
        assertEquals(h, ServiceList.add(" : :"))
    }

    @Test fun add_keepsOtherServices_inOrder() {
        assertEquals("a.b/a.b.S:$h", ServiceList.add("a.b/a.b.S"))
        assertEquals("a.b/a.b.S:c.d/.T:$h", ServiceList.add("a.b/a.b.S::c.d/.T:"))
    }

    @Test fun add_alreadyPresent_isUnchanged_inLongOrShortForm() {
        assertEquals("a.b/a.b.S:$h", ServiceList.add("a.b/a.b.S:$h"))
        assertEquals("dev.tvlauncher.helper/.HelperService", ServiceList.add("dev.tvlauncher.helper/.HelperService"))
        assertTrue(ServiceList.contains("x/y:dev.tvlauncher.helper/.HelperService"))
        assertFalse(ServiceList.contains("dev.tvlauncher.helper/dev.tvlauncher.helper.Other"))
    }

    @Test fun routes_neverOpenAMissingScreen() {
        assertEquals(EnableRoute.ALREADY_ON, EnableRoutes.pick(true, false, false, false))
        assertEquals(EnableRoute.SELF_ENABLE, EnableRoutes.pick(false, false, true, true))
        assertEquals(EnableRoute.OPEN_SCREEN, EnableRoutes.pick(false, true, false, true))
        assertEquals(EnableRoute.FINISH_SETUP, EnableRoutes.pick(false, false, false, true))
        assertEquals(EnableRoute.NONE, EnableRoutes.pick(false, false, false, false))
    }
}
