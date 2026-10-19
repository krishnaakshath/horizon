package dev.tvlauncher.ui

import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.apps.component

sealed class TileArt<out D> {
    data class Banner<D>(val d: D) : TileArt<D>()
    data class Adaptive<D>(val bg: D, val fg: D) : TileArt<D>()
    data class Legacy<D>(val d: D) : TileArt<D>()
    data class Letter(val letter: Char, val color: Int) : TileArt<Nothing>()
}

interface ArtSource<D> {
    fun banner(pkg: String): D?
    fun adaptiveLayers(pkg: String): Pair<D, D>?
    fun legacyIcon(pkg: String): D?
}

object TileArtResolver {
    private val PALETTE = intArrayOf(
        0xFF5E35B1.toInt(), 0xFF00897B.toInt(), 0xFFC62828.toInt(), 0xFF1565C0.toInt(),
        0xFFEF6C00.toInt(), 0xFF2E7D32.toInt(), 0xFFAD1457.toInt(), 0xFF455A64.toInt()
    )

    fun <D> resolve(source: ArtSource<D>, app: AppEntry): TileArt<D> {
        source.banner(app.packageName)?.let { return TileArt.Banner(it) }
        source.adaptiveLayers(app.packageName)?.let { return TileArt.Adaptive(it.first, it.second) }
        source.legacyIcon(app.packageName)?.let { return TileArt.Legacy(it) }
        return letterTile(app)
    }

    fun letterTile(app: AppEntry): TileArt.Letter {
        val letter = app.label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
        val idx = (app.packageName.hashCode() and Int.MAX_VALUE) % PALETTE.size
        return TileArt.Letter(letter, PALETTE[idx])
    }

    fun resolve(pm: PackageManager, app: AppEntry): TileArt<Drawable> =
        resolve(PackageManagerArtSource(pm, app), app)
}

class PackageManagerArtSource(private val pm: PackageManager, private val app: AppEntry) :
    ArtSource<Drawable> {
    override fun banner(pkg: String): Drawable? = try {
        pm.getActivityBanner(app.component()) ?: pm.getApplicationBanner(pkg)
    } catch (e: Exception) { null }

    override fun adaptiveLayers(pkg: String): Pair<Drawable, Drawable>? {
        if (Build.VERSION.SDK_INT < 26) return null
        return try {
            val d = pm.getApplicationIcon(pkg) as? AdaptiveIconDrawable ?: return null
            val bg = d.background ?: return null
            val fg = d.foreground ?: return null
            bg to fg
        } catch (e: Exception) { null }
    }

    override fun legacyIcon(pkg: String): Drawable? = try {
        pm.getApplicationIcon(pkg)
    } catch (e: Exception) { null }
}
