package dev.tvlauncher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings

/** System shortcuts. Every launch is guarded so a missing handler never crashes. */
object SystemActions {
    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun settings(context: Context) = start(context, Intent(Settings.ACTION_SETTINGS))

    fun inputs(context: Context): Boolean {
        val id = try {
            val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            val list = tim?.tvInputList.orEmpty().filter { it.isPassthroughInput }
            (list.firstOrNull { it.type == TvInputInfo.TYPE_HDMI } ?: list.firstOrNull())?.id
        } catch (e: Exception) {
            null
        } ?: return false
        return start(context, Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)))
    }

    fun assistant(context: Context): Boolean =
        start(context, Intent(Intent.ACTION_VOICE_COMMAND)) || start(context, Intent(Intent.ACTION_ASSIST))
}
