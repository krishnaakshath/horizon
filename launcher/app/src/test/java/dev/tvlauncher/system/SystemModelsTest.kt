package dev.tvlauncher.system

import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import dev.tvlauncher.devices.ConnectedDevice
import dev.tvlauncher.devices.DeviceKind
import dev.tvlauncher.devices.InputSignal
import dev.tvlauncher.devices.InputsModel
import dev.tvlauncher.devices.RawInput
import dev.tvlauncher.devices.TvInputItem
import dev.tvlauncher.ui.QuickActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSpecsTest {
    @Test fun everySystemPage_fitsTheRowBudget_andPassesRowGuard() {
        for (spec in SystemSpecs.ALL) {
            assertTrue("${spec.id}: ${spec.primaryRows.size}", spec.primaryRows.size <= PageSpec.MAX_PRIMARY)
            assertTrue(spec.id, spec.primaryRows.isNotEmpty())
            val g = RowGuard(spec)
            spec.primaryRows.forEach { g.claim(it, advanced = false) }
            spec.advancedRows.forEach { g.claim(it, advanced = true) }
            assertEquals(spec.primaryRows.size, g.primaryCount)
            assertEquals(spec.id, (spec.primaryRows + spec.advancedRows).size, (spec.primaryRows + spec.advancedRows).toSet().size)
        }
    }

    @Test fun pageIds_areUnique() {
        assertEquals(SystemSpecs.ALL.size, SystemSpecs.ALL.map { it.id }.toSet().size)
    }

    @Test fun managedByTheTv_isUnderAdvanced() {
        assertTrue("sys.managed" in SystemSpecs.SYSTEM_HUB.advancedRows)
        assertFalse("sys.managed" in SystemSpecs.SYSTEM_HUB.primaryRows)
        assertTrue("sys.reset" in SystemSpecs.SYSTEM_HUB.advancedRows)
    }

    @Test fun replacedSections_keepTheirIds() {
        assertEquals("network", SystemSpecs.NETWORK)
        assertEquals("bluetooth", SystemSpecs.BLUETOOTH)
        assertEquals("system", SystemSpecs.SYSTEM)
    }
}

class InsistGateTest {
    @Test fun secondPressWithinWindowOpens() {
        val g = InsistGate(4000)
        assertFalse(g.press("a", 0))
        assertTrue(g.isArmed("a", 100))
        assertTrue(g.press("a", 3000))
        assertFalse(g.isArmed("a", 3001))
    }

    @Test fun otherRowOrLatePress_rearms() {
        val g = InsistGate(4000)
        assertFalse(g.press("a", 0))
        assertFalse(g.press("b", 10))
        assertFalse(g.press("a", 20))
        assertFalse(g.press("a", 9000))
        assertTrue(g.press("a", 9500))
    }
}

class TvScreensTest {
    @Test fun everyScreen_endsInTheTvSettings_andPictureTriesTclFirst() {
        for (s in TvScreens.Screen.values()) {
            val c = TvScreens.chain(s)
            assertEquals(TvScreens.Target.Pkg(TvScreens.TV_SETTINGS), c.last())
            assertTrue(c.contains(TvScreens.Target.Action("android.settings.SETTINGS")))
            assertFalse(s.why.contains("adb", true))
        }
        assertEquals(TvScreens.Target.Pkg(TvScreens.TCL_SETTINGS), TvScreens.chain(TvScreens.Screen.PICTURE).first())
    }
}

class PinRulesTest {
    @Test fun fourDigitsOnly() {
        assertTrue(PinRules.valid("0420"))
        assertFalse(PinRules.valid("042"))
        assertFalse(PinRules.valid("04a0"))
        assertFalse(PinRules.valid("04200"))
    }

    @Test fun hashIsSalted_andMatches() {
        val h = PinRules.hash("1234", "s1")
        assertNotEquals(h, PinRules.hash("1234", "s2"))
        assertNotEquals("1234", h)
        assertTrue(PinRules.matches("1234", "s1", h))
        assertFalse(PinRules.matches("1235", "s1", h))
        assertFalse(PinRules.matches("1234", "s1", null))
    }

    @Test fun lockoutAfterFiveMisses() {
        assertEquals(0, PinRules.lockoutSeconds(4))
        assertEquals(30, PinRules.lockoutSeconds(5))
    }
}

class A11yModelTest {
    private val helper = A11yModel.HELPER_COMPONENT

    @Test fun enable_appendsOnce_keepsOthers() {
        assertEquals(helper, A11yModel.enable(null, helper))
        assertEquals("a/b.C:$helper", A11yModel.enable("a/b.C", helper))
        assertEquals("a/b.C:$helper", A11yModel.enable("a/b.C:$helper", helper))
        // Short form of the same component counts as already enabled.
        assertEquals("dev.tvlauncher.helper/.HelperService", A11yModel.enable("dev.tvlauncher.helper/.HelperService", helper))
    }

    @Test fun disable_removesOnlyThatService() {
        assertEquals("a/b.C", A11yModel.disable("a/b.C:$helper", helper))
        assertEquals("", A11yModel.disable(helper, helper))
        assertEquals("a/b.C", A11yModel.disable("a/b.C:dev.tvlauncher.helper/.HelperService", helper))
    }

    @Test fun isEnabled_toleratesSpacesAndEmpties() {
        assertTrue(A11yModel.isEnabled(" a/b.C : $helper :", helper))
        assertFalse(A11yModel.isEnabled("", helper))
        assertFalse(A11yModel.isEnabled(null, helper))
    }

    @Test fun textSizes() {
        assertEquals(A11yModel.TextSize.M, A11yModel.textSizeOf(null))
        assertEquals(A11yModel.TextSize.XL, A11yModel.textSizeOf("XL"))
        assertEquals(1f, A11yModel.TextSize.M.scale)
        assertTrue(A11yModel.TextSize.values().toList().zipWithNext().all { (a, b) -> a.scale < b.scale })
    }

    @Test fun plan_withoutGrant_showsStateOnly_withOneNote() {
        val p = A11yRows.plan(readerInstalled = false, helperInstalled = true, canWriteSecure = false)
        assertEquals(listOf("a11y.text", "a11y.contrast", "a11y.motion", "a11y.captions", "a11y.helper"), p.keys)
        assertFalse("a11y.helper" in p.switchable)
        assertFalse("a11y.captions" in p.switchable)
        assertTrue(p.note)
        assertTrue(p.keys.size <= PageSpec.MAX_PRIMARY)
    }

    @Test fun plan_withGrant_switchesEverything_readerOnlyWhenInstalled() {
        val p = A11yRows.plan(readerInstalled = true, helperInstalled = true, canWriteSecure = true)
        assertTrue("a11y.reader" in p.keys)
        assertTrue(p.switchable.containsAll(listOf("a11y.captions", "a11y.reader", "a11y.helper")))
        assertFalse(p.note)
        assertTrue(p.keys.all { it in SystemSpecs.ACCESS.primaryRows })
        val noHelper = A11yRows.plan(readerInstalled = false, helperInstalled = false, canWriteSecure = true)
        assertFalse("a11y.helper" in noHelper.switchable)
    }
}

class SleepTimerTest {
    @Test fun labels_andCycling() {
        assertEquals("Off", SleepTimer.label(0))
        assertEquals("30 min", SleepTimer.label(30))
        assertEquals("1 h", SleepTimer.label(60))
        assertEquals("1 h 30 min", SleepTimer.label(90))
        assertEquals(15, SleepTimer.next(0))
        assertEquals(0, SleepTimer.next(120))
    }

    @Test fun minutesLeft_roundsUp() {
        assertEquals(0, SleepTimer.minutesLeft(0, 10))
        assertEquals(1, SleepTimer.minutesLeft(61_000, 60_000))
        assertEquals(3, SleepTimer.minutesLeft(120_001, 0))
        assertEquals(2, SleepTimer.minutesLeft(120_000, 0))
    }
}

class SystemTextTest {
    @Test fun bytes() {
        assertEquals("512 B", SystemText.bytes(512))
        assertEquals("1.5 KB", SystemText.bytes(1500))
        assertEquals("16.0 GB", SystemText.bytes(16_000_000_000))
        assertEquals("120 GB", SystemText.bytes(120_000_000_000))
        assertEquals("Unknown", SystemText.bytes(-1))
    }

    @Test fun usage_volume_internet() {
        assertEquals("4.0 GB of 8.0 GB used", SystemText.usedOf(4_000_000_000, 8_000_000_000))
        assertEquals(50, SystemText.percent(1, 2))
        assertEquals("Unknown", SystemText.usedOf(1, 0))
        assertEquals("50 %", SystemText.volume(50, 100))
        assertEquals("47 %", SystemText.volume(7, 15))
        assertEquals("Working", SystemText.internet(true, true, false))
        assertEquals("Connected, no internet", SystemText.internet(true, false, false))
        assertEquals("Sign-in needed", SystemText.internet(true, false, true))
        assertEquals("No network", SystemText.internet(false, false, false))
    }
}

class InputSignalTest {
    @Test fun onlyHdmiReportsRealState() {
        assertEquals(InputSignal.CONNECTED, InputsModel.signal(InputsModel.TYPE_HDMI, InputsModel.STATE_CONNECTED))
        assertEquals(InputSignal.STANDBY, InputsModel.signal(InputsModel.TYPE_HDMI, InputsModel.STATE_STANDBY))
        assertEquals(InputSignal.NO_SIGNAL, InputsModel.signal(InputsModel.TYPE_HDMI, InputsModel.STATE_DISCONNECTED))
        for (t in listOf(InputsModel.TYPE_COMPOSITE, InputsModel.TYPE_SVIDEO, InputsModel.TYPE_SCART, InputsModel.TYPE_COMPONENT, InputsModel.TYPE_VGA)) {
            for (s in 0..2) assertEquals(InputSignal.AVAILABLE, InputsModel.signal(t, s))
        }
    }

    @Test fun aLiveCecDevice_isRealDetection() {
        assertEquals(InputSignal.CONNECTED, InputsModel.signal(InputsModel.TYPE_DVI, InputsModel.STATE_CONNECTED, cec = true))
        assertEquals(InputSignal.AVAILABLE, InputsModel.signal(InputsModel.TYPE_DVI, InputsModel.STATE_CONNECTED, cec = false))
    }

    @Test fun availableIsNeverConnected_norCounted() {
        assertEquals("Available", InputSignal.AVAILABLE.word)
        assertFalse(InputSignal.AVAILABLE.live)
        val items = InputsModel.merge(listOf(
            RawInput("av", null, "AV", InputsModel.TYPE_COMPOSITE, InputsModel.STATE_CONNECTED),
            RawInput("h1", null, "HDMI 1", InputsModel.TYPE_HDMI, InputsModel.STATE_DISCONNECTED)
        ))
        assertEquals("Nothing connected", InputsModel.header(items).subtitle)
        assertEquals(listOf("Available", "No signal"), InputsModel.build(items).rows.map { it.value })
        assertFalse(InputsModel.build(items).rows.any { it.value == "Connected" })
    }

    @Test fun cecChildMarksPort() {
        val items = InputsModel.merge(listOf(
            RawInput("h2", null, "HDMI 2", InputsModel.TYPE_HDMI, InputsModel.STATE_CONNECTED),
            RawInput("h2/HDMI2000", "h2", "PS5", InputsModel.TYPE_HDMI, InputsModel.STATE_CONNECTED)
        ))
        assertTrue(items.single().cec)
        assertEquals("PS5", InputsModel.row(items.single()).value)
    }
}

class DevicesPageModelTest {
    private fun dev(id: String, name: String, kind: DeviceKind, detail: String? = null) = ConnectedDevice(id, name, kind, null, detail)

    @Test fun tclRemote_isNamedTvRemote_andFoldedOnce() {
        assertTrue(DevicesPageModel.isRemote("TCL_RC902V_M4 Keyboard"))
        assertTrue(DevicesPageModel.isRemote("Smart Remote"))
        assertFalse(DevicesPageModel.isRemote("Logitech K400"))
        assertFalse(DevicesPageModel.isRemote("Arcade"))
        val s = DevicesPageModel.sections(listOf(
            dev("u1", "TCL_RC902V_M4 Keyboard", DeviceKind.KEYBOARD),
            dev("u2", "TCL_RC902V_M4 Mouse", DeviceKind.MOUSE),
            dev("b1", "DualSense Wireless Controller", DeviceKind.BT_GAMEPAD)
        ), emptyList())
        val ctrl = s.first { it.title == "Controllers & remotes" }
        assertEquals(listOf(DevicesPageModel.REMOTE_NAME, "DualSense Wireless Controller"), ctrl.rows.map { it.title })
        assertNull(ctrl.rows.first().value)
    }

    @Test fun groups_inputsFirst_networkLast_oneRow() {
        val inputs = listOf(
            TvInputItem("h1", "HDMI 1", InputsModel.TYPE_HDMI, 0, "Apple TV"),
            TvInputItem("av", "AV", InputsModel.TYPE_COMPOSITE, 0, null)
        )
        val s = DevicesPageModel.sections(listOf(
            dev("tv:h1", "Apple TV", DeviceKind.STREAMER),
            dev("net:wifi", "Wi-Fi", DeviceKind.NETWORK_WIFI, "Home"),
            dev("usb:1", "SanDisk", DeviceKind.USB_STORAGE)
        ), inputs)
        assertEquals(listOf("Inputs", "Storage", "Network"), s.map { it.title })
        assertEquals(listOf("Apple TV", "Available"), s[0].rows.map { it.value })
        assertEquals(DevicesPageModel.Action.SWITCH_INPUT, s[0].rows[0].action)
        val net = s.last().rows.single()
        assertEquals("Wi-Fi", net.title); assertEquals("Home", net.value)
        assertEquals(DevicesPageModel.Action.NETWORK, net.action)
        assertFalse(s.flatMap { it.rows }.any { it.title == "Apple TV" && it.action == DevicesPageModel.Action.NONE })
    }

    @Test fun tidyName_dropsHidSuffixAndUnderscores() {
        assertEquals("Logi K400", DevicesPageModel.tidyName(dev("x", "Logi_K400 Keyboard", DeviceKind.KEYBOARD)))
        assertEquals("Keyboard", DevicesPageModel.tidyName(dev("x", " ", DeviceKind.KEYBOARD)))
    }

    @Test fun noNetwork_saysSo() {
        val s = DevicesPageModel.sections(emptyList(), emptyList())
        assertEquals("Not connected", s.single().rows.single().value)
    }
}

class AppsModelTest {
    @Test fun userAppsFirst_thenOpenableSystemApps_hiddenDropped() {
        val apps = listOf(
            AppRow("b.sys", "Bravo", system = true, launchable = true),
            AppRow("svc", "Service", system = true, launchable = false),
            AppRow("z.user", "zulu", system = false, launchable = false),
            AppRow("a.user", "Alpha", system = false, launchable = true),
            AppRow("h.user", "Hidden", system = false, launchable = true)
        )
        assertEquals(listOf("a.user", "z.user", "b.sys"), AppsModel.order(apps, hidden = setOf("h.user")).map { it.pkg })
    }

    @Test fun permissionWords() {
        assertEquals(listOf("Microphone", "Location"), AppsModel.permissionWords(listOf(
            "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION", "android.permission.INTERNET")))
    }
}

class QuickActionsSystemTest {
    @Test fun noQuickActionMapsToTheTvSettingsApp() {
        for (i in QuickActions.Item.values()) {
            val s = QuickActions.launcherSection(i) ?: continue
            assertFalse(s.startsWith("android.settings"))
        }
    }
}

class SystemLinksTest {
    @Test fun appPages() {
        assertEquals("com.netflix.ninja", SystemLinks.appOf(SystemLinks.appPage("com.netflix.ninja")))
        assertNull(SystemLinks.appOf(SystemLinks.VOLUME))
        assertNull(SystemLinks.appOf(null))
        assertNull(SystemLinks.appOf(SystemLinks.appPage("")))
    }
}
