package dev.tvlauncher.link

import android.content.Context
import android.net.Uri
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.link.core.LinkContract

/** H02 Link's release check as its provider reports it. Off by default; no repository is built in. */
data class UpdateState(val enabled: Boolean, val repo: String, val latest: String, val available: Boolean)

/** About > Updates: the row's words (pure, tested). */
object UpdateRowText {
    fun active(s: UpdateState?): Boolean = s != null && s.enabled && s.repo.isNotBlank()

    fun value(s: UpdateState?): String = when {
        !active(s) -> "Off"
        s!!.available -> "Update available"
        s.latest.isNotEmpty() -> "Up to date"
        else -> "Not checked yet"
    }

    fun subtitle(s: UpdateState?): String = when {
        !active(s) -> "Update checks are switched off"
        s!!.available && s.latest.isNotEmpty() -> "Version ${s.latest} is ready in H02 Store"
        else -> "Checks once a day through H02 Link"
    }
}

/** Adds the About > Updates row when H02 Link is installed; nothing otherwise. */
object UpdateRow {
    const val ID = "about.update"

    fun read(context: Context): UpdateState? = try {
        context.contentResolver.query(Uri.parse(LinkContract.URI_UPDATE), null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            fun s(col: String) = c.getColumnIndex(col).let { if (it < 0) "" else c.getString(it) ?: "" }
            fun l(col: String) = c.getColumnIndex(col).let { if (it < 0) 0L else c.getLong(it) }
            UpdateState(l(LinkContract.COL_UPDATE_ENABLED) != 0L, s(LinkContract.COL_UPDATE_REPO), s(LinkContract.COL_UPDATE_LATEST),
                l(LinkContract.COL_UPDATE_AVAILABLE) != 0L)
        }
    } catch (e: Exception) {
        null
    }

    fun add(page: SettingsPage, context: Context, nav: SettingsNav) {
        if (!LinkStatus.read(context).installed) return
        val s = read(context)
        val kind = if (UpdateRowText.active(s)) RowKind.Action(UpdateRowText.value(s)) {
            Thread {
                LinkStatus.call(context, LinkContract.METHOD_UPDATE_CHECK, "force")
                page.post { nav.refresh() }
            }.apply { isDaemon = true }.start()
        } else RowKind.Info(UpdateRowText.value(s))
        page.row(ID, "Updates", kind, subtitle = UpdateRowText.subtitle(s), iconRes = R.drawable.ic_set_about)
    }
}
