package dev.tvlauncher.cast

import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.devices.InputsModel
import dev.tvlauncher.devices.RawInput
import dev.tvlauncher.spotlight.SpotlightConfig
import dev.tvlauncher.ui.QuickActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastModelTest {
    private val tclId = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW15"
    private fun state(cast: Boolean = true, airplay: String? = tclId, net: Boolean = true) =
        CastModel.State("Living room TV", cast, airplay, net)

    @Test fun airPlayInput_matchesLabelOrId() {
        assertTrue(CastModel.isAirPlayInput(tclId, "AirPlay"))
        assertTrue(CastModel.isAirPlayInput(tclId, "Air Play"))
        assertTrue(CastModel.isAirPlayInput("com.tcl.tvinput/.AirplayInput", null))
        assertFalse(CastModel.isAirPlayInput("com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW5", "HDMI 1"))
        assertFalse(CastModel.isAirPlayInput("x", "Air conditioner"))
    }

    @Test fun tvName_prefersDeviceName_thenBluetooth() {
        assertEquals("TCL TV", CastModel.tvName(" TCL TV ", "BT"))
        assertEquals("BT", CastModel.tvName("  ", "BT"))
        assertEquals(null, CastModel.tvName(null, null))
    }

    @Test fun statusMapping() {
        assertEquals("Ready", CastModel.status(true))
        assertEquals("Not on this TV", CastModel.status(false))
        assertEquals("Connected", CastModel.networkStatus(true))
        assertEquals("Not connected", CastModel.networkStatus(false))
    }

    @Test fun rows_withAirPlay_offerOpen_notGetApp() {
        val keys = CastModel.rows(state()).map { it.key }
        assertEquals(listOf(CastModel.NAME, CastModel.NETWORK, CastModel.CHROMECAST, CastModel.AIRPLAY, CastModel.OPEN_AIRPLAY, CastModel.HOWTO), keys)
    }

    @Test fun rows_withoutAirPlay_suggestReceiverApp() {
        val rows = CastModel.rows(state(airplay = null))
        assertTrue(CastModel.GET_RECEIVER in rows.map { it.key })
        assertFalse(CastModel.OPEN_AIRPLAY in rows.map { it.key })
        assertEquals("Not on this TV", rows.first { it.key == CastModel.AIRPLAY }.value)
    }

    @Test fun rows_neverShowNetworkName_andFitTheGuard() {
        for (s in listOf(state(), state(airplay = null, cast = false, net = false))) {
            val rows = CastModel.rows(s)
            assertTrue(rows.size <= 7)
            assertTrue(rows.all { it.key in CastModel.SPEC.primaryRows })
            val net = rows.first { it.key == CastModel.NETWORK }.value
            assertTrue(net == "Connected" || net == "Not connected")
        }
        assertTrue(CastModel.SPEC.withinBudget)
        val guard = dev.tvlauncher.appsettings.RowGuard(CastModel.SPEC)
        CastModel.rows(state()).forEach { guard.claim(it.key, false) }
    }

    @Test fun howTo_coversEachPlatformTheTvSupports() {
        val both = CastModel.howTo(state()).map { it.first }
        assertTrue(both.contains("iPhone, iPad and Mac"))
        assertTrue(both.contains("Android phones and tablets"))
        val none = CastModel.howTo(state(cast = false, airplay = null))
        assertEquals(1, none.size)
    }

    @Test fun inputsPanel_showsAirPlayAsItsOwnRow_afterThePorts() {
        val raw = listOf(
            RawInput(tclId, null, "AirPlay", 1000, InputsModel.STATE_CONNECTED),
            RawInput("hw1", null, "HDMI 1", InputsModel.TYPE_HDMI, InputsModel.STATE_DISCONNECTED)
        )
        val items = InputsModel.merge(raw)
        assertEquals(listOf("HDMI 1", "AirPlay"), items.map { it.label })
        val row = InputsModel.row(items.last())
        assertEquals("AirPlay", row.label)
        assertEquals(CastModel.AIRPLAY_SUBTITLE, row.value)
        assertEquals(InputsModel.PREFIX + tclId, row.key)
    }

    @Test fun quickAction_isHiddenByDefault_andOpensThePage() {
        assertTrue(QuickActions.Item.CAST in SpotlightConfig.DEFAULT_HIDDEN)
        assertFalse(QuickActions.Item.CAST in QuickActions.items(emptySet()))
        assertEquals(CastSection.ID, QuickActions.launcherSection(QuickActions.Item.CAST))
        assertEquals(QuickActions.Route.SYSTEM, QuickActions.route(QuickActions.Item.CAST, true))
    }

    @Test fun section_sitsInConnections_once() {
        val r = SettingsRegistry.newDefault()
        CastSection.register(r)
        CastSection.register(r)
        assertEquals(1, r.sections().count { it.id == CastSection.ID })
        assertEquals(SettingsGroup.CONNECTIONS, r.sections().first { it.id == CastSection.ID }.group)
        assertTrue(CastSection.description.contains("AirPlay"))
    }
}
