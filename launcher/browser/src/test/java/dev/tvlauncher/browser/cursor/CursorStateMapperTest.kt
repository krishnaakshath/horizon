package dev.tvlauncher.browser.cursor

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorStateMapperTest {
    private fun map(h: HitInfo, loading: Boolean = false, edge: Boolean = false, disabled: Boolean = false) =
        CursorStateMapper.map(h, loading, edge, disabled)

    @Test fun link_byAnchorTagOrHref() {
        assertEquals(CursorState.LINK, map(HitInfo(tag = "a")))
        assertEquals(CursorState.LINK, map(HitInfo(tag = "span", href = "https://x.com/")))
    }

    @Test fun buttonsAndClickables_areLink() {
        assertEquals(CursorState.LINK, map(HitInfo(tag = "button")))
        assertEquals(CursorState.LINK, map(HitInfo(tag = "div", role = "button")))
        assertEquals(CursorState.LINK, map(HitInfo(tag = "div", clickable = true)))
    }

    @Test fun textInputs_areText() {
        assertEquals(CursorState.TEXT, map(HitInfo(tag = "input", editable = true)))
        assertEquals(CursorState.TEXT, map(HitInfo(tag = "textarea")))
        assertEquals(CursorState.TEXT, map(HitInfo(tag = "div", editable = true)))
    }

    @Test fun cssCursor_respected() {
        assertEquals(CursorState.LINK, map(HitInfo(tag = "div", cursor = "pointer")))
        assertEquals(CursorState.TEXT, map(HitInfo(tag = "div", cursor = "text")))
        assertEquals(CursorState.DISABLED, map(HitInfo(tag = "div", cursor = "not-allowed")))
        assertEquals(CursorState.DEFAULT, map(HitInfo(tag = "p", cursor = "auto")))
    }

    @Test fun precedence_edgeThenLoadingThenElement() {
        assertEquals(CursorState.SCROLL, map(HitInfo(tag = "a"), loading = true, edge = true))
        assertEquals(CursorState.BUSY, map(HitInfo(tag = "p"), loading = true))
        assertEquals(CursorState.LINK, map(HitInfo(tag = "a"), loading = true))
        assertEquals(CursorState.DISABLED, map(HitInfo(tag = "a"), disabled = true))
    }

    @Test fun empty_isDefault() {
        assertEquals(CursorState.DEFAULT, map(HitInfo.EMPTY))
    }
}
