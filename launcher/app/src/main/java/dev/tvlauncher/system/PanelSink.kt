package dev.tvlauncher.system

import android.view.View
import dev.tvlauncher.panel.PanelContent
import dev.tvlauncher.panel.PanelShell

/** Where a panel screen draws: the home overlay ([ShellSink]) or a Settings page ([PanelRowsPage]). */
interface PanelSink {
    /** True while the screen is on display and accepts input. */
    val active: Boolean
    /** A view to post delayed work on. */
    val anchor: View
    fun render(content: PanelContent, preferKey: String? = null)
}

/** [PanelSink] over the home screen's overlay. */
class ShellSink(private val shell: PanelShell) : PanelSink {
    override val active: Boolean get() = shell.isOpen
    override val anchor: View get() = shell.root
    override fun render(content: PanelContent, preferKey: String?) = shell.render(content, preferKey)
}
