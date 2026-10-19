package dev.tvlauncher.panel

/**
 * Pure description of one panel screen: a header and an ordered list of entries. Every overlay
 * (Wi-Fi, Bluetooth, Inputs, Devices, the tile menu) builds one of these from plain values, so
 * ordering and state mapping are JVM-testable, and [PanelShell] renders all of them the same way.
 *
 * Icons are drawable resource ids (plain ints, also readable in JVM tests); null means no icon.
 */
data class PanelHeader(val icon: Int?, val title: String, val subtitle: String)

/** What a row shows at its end, after the optional value text. */
sealed class Trailing {
    object None : Trailing()
    /** An on/off switch (accent track when on). */
    data class Switch(val on: Boolean) : Trailing()
    /** The current / connected item. */
    object Check : Trailing()
    /** A secured network. */
    object Lock : Trailing()
    /** Work in progress (scanning, pairing). */
    object Busy : Trailing()
    /** Opens another screen (system settings, a sub-view). */
    object Chevron : Trailing()

    override fun toString(): String = javaClass.simpleName
}

sealed class PanelEntry {
    abstract val key: String

    /**
     * The single row style: leading [icon], one-line [label], optional [value] text and a [trailing]
     * indicator. Focusable; OK runs the host's handler for [key] unless [enabled] is false.
     */
    data class Row(
        override val key: String,
        val icon: Int?,
        val label: String,
        val value: String? = null,
        val trailing: Trailing = Trailing.None,
        val enabled: Boolean = true
    ) : PanelEntry()

    /**
     * A friendly, non-focusable state line (empty, loading, error, permission needed). [detail] is
     * an optional second line (for example a one-line ADB command); [busy] adds the dots.
     */
    data class Message(
        override val key: String,
        val text: String,
        val detail: String? = null,
        val busy: Boolean = false
    ) : PanelEntry()
}

data class PanelContent(val header: PanelHeader, val entries: List<PanelEntry>) {
    val rows: List<PanelEntry.Row> get() = entries.filterIsInstance<PanelEntry.Row>()
    val messages: List<PanelEntry.Message> get() = entries.filterIsInstance<PanelEntry.Message>()
}

/** Size rules shared by every panel (dp, 8 dp grid). */
object PanelLimits {
    /** At most this many rows are visible; the rest scroll with focus. */
    const val MAX_VISIBLE_ROWS = 7
    const val ROW_DP = 48
    const val HEADER_DP = 48
    /** Card padding above the header, and the gaps around the list (as laid out by [PanelShell]). */
    const val PAD_DP = 16
    const val GAP_DP = 8

    /** Height the list area may take before it scrolls. */
    fun bodyMaxDp(visibleRows: Int = MAX_VISIBLE_ROWS, rowDp: Int = ROW_DP): Int =
        visibleRows.coerceAtLeast(1) * rowDp

    /** Rows that fit in [availableDp] of list height (at least 1, at most [MAX_VISIBLE_ROWS]). */
    fun visibleRows(availableDp: Int, rowDp: Int = ROW_DP): Int =
        (availableDp / rowDp).coerceIn(1, MAX_VISIBLE_ROWS)

    /** Total card height for [rowCount] rows, capped at [MAX_VISIBLE_ROWS] (header, padding, gaps included). */
    fun cardDp(rowCount: Int): Int =
        PAD_DP + HEADER_DP + GAP_DP + bodyMaxDp(rowCount.coerceIn(1, MAX_VISIBLE_ROWS)) + GAP_DP

    /**
     * Index of the row to focus when a panel (re)renders: the previously focused key, else the
     * preferred key, else the first row. -1 when there are no rows.
     */
    fun focusIndex(keys: List<String>, focusedKey: String?, preferKey: String?): Int {
        if (keys.isEmpty()) return -1
        focusedKey?.let { k -> keys.indexOf(k).takeIf { it >= 0 }?.let { return it } }
        preferKey?.let { k -> keys.indexOf(k).takeIf { it >= 0 }?.let { return it } }
        return 0
    }
}

/** Calm, purposeful motion: 220 ms (160 ms on LITE devices), scaled by the system animator scale. */
object PanelMotion {
    const val FULL_MS = 220L
    const val LITE_MS = 160L

    /** [scale] is Settings.Global.ANIMATOR_DURATION_SCALE; 0 (or motion off) means instant. */
    fun duration(lite: Boolean, scale: Float, enabled: Boolean = true): Long {
        if (!enabled || scale <= 0f || scale.isNaN()) return 0L
        val base = if (lite) LITE_MS else FULL_MS
        return (base * scale.coerceAtMost(10f)).toLong()
    }

    /** Closing is a little quicker than opening. */
    fun closeDuration(lite: Boolean, scale: Float, enabled: Boolean = true): Long =
        duration(lite, scale, enabled) * 3 / 4
}
