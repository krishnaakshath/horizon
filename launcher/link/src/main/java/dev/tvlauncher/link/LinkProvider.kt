package dev.tvlauncher.link

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import dev.tvlauncher.link.spotify.SpotifyArt
import dev.tvlauncher.link.spotify.SpotifyCore
import dev.tvlauncher.link.spotify.SpotifyKeyStore
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.ha.HaRepository
import dev.tvlauncher.link.ha.HaUrls
import dev.tvlauncher.link.ha.StateText
import dev.tvlauncher.link.update.Updates

/**
 * What the launcher (no INTERNET) may read and ask: phone-remote status, the smart-home
 * favourites and the update check. Guarded by the signature permission in the manifest
 * (readPermission) and again in [call], which the framework does not guard by itself.
 * Never exposes the Home Assistant token, WebDAV password, pairing tokens or the Spotify secret;
 * the only file it serves is a cached Spotify picture ([openFile], read-only).
 */
class LinkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun ctx(): Context = context ?: throw IllegalStateException()

    private fun enforce() {
        if (ctx().checkCallingOrSelfPermission(LinkContract.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("signature permission required")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        enforce()
        val c = ctx()
        val store = LinkStore(c)
        return when (uri.lastPathSegment) {
            LinkContract.PATH_STATUS -> {
                LinkRuntime.load(c)
                val p = LinkRuntime.pairing
                val now = System.currentTimeMillis()
                val open = p.windowOpen(now)
                MatrixCursor(LinkContract.STATUS_COLUMNS).apply {
                    addRow(arrayOf<Any?>(
                        if (store.enabled) 1 else 0,
                        if (LinkRuntime.server?.running == true) 1 else 0,
                        LinkRuntime.address(),
                        if (store.pairingRequired) 1 else 0,
                        if (open) p.pin else null,
                        if (open) p.expiresMs else 0L,
                        p.pairedCount(),
                        if (store.haConfigured) 1 else 0,
                        HaUrls.displayHost(store.haUrl),
                    ))
                }
            }
            LinkContract.PATH_HOME -> MatrixCursor(LinkContract.HOME_COLUMNS).apply {
                val state = if (store.haConfigured) HaRepository.state else LinkContract.HA_NOT_CONFIGURED
                extras = Bundle().apply { putString(LinkContract.COL_HA_STATE, state) }
                for (e in HaRepository.favourites(c)) addRow(arrayOf<Any?>(
                    e.entityId, e.name, e.control.wire, StateText.of(e), if (e.isOn) 1 else 0, if (e.available) 1 else 0,
                    e.target, e.step, state, HaRepository.updatedMs,
                ))
            }
            LinkContract.PATH_UPDATE -> MatrixCursor(LinkContract.UPDATE_COLUMNS).apply {
                addRow(arrayOf<Any?>(
                    if (store.updateEnabled) 1 else 0, store.updateRepo, store.updateLatest,
                    if (store.updateEnabled && store.updateAvailable) 1 else 0, store.updateCheckedMs, if (store.updateSha) 1 else 0,
                ))
            }
            LinkContract.PATH_SPOTIFY -> MatrixCursor(LinkContract.SPOTIFY_COLUMNS).apply {
                val k = SpotifyKeyStore(c)
                addRow(arrayOf<Any?>(if (k.configured) 1 else 0, k.idHint(),
                    if (k.configured) k.state else LinkContract.SP_NOT_CONFIGURED))
            }
            else -> null
        }
    }

    /** content://dev.tvlauncher.link/art/<key>: read-only, cached Spotify pictures only. */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        enforce()
        if (mode != "r") throw SecurityException("read-only")
        val seg = uri.pathSegments
        if (seg.size != 2 || seg[0] != LinkContract.PATH_ART) throw java.io.FileNotFoundException()
        val f = SpotifyArt.file(ctx(), seg[1])?.takeIf { it.isFile } ?: throw java.io.FileNotFoundException()
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        enforce()
        val c = ctx()
        val store = LinkStore(c)
        val out = Bundle()
        when (method) {
            LinkContract.METHOD_SET_ENABLED -> { store.enabled = arg == "1"; LinkService.sync(c); out.putBoolean(LinkContract.EXTRA_OK, true) }
            LinkContract.METHOD_SET_PAIRING_REQUIRED -> { store.pairingRequired = arg != "0"; out.putBoolean(LinkContract.EXTRA_OK, true) }
            LinkContract.METHOD_OPEN_PAIRING -> { LinkRuntime.load(c); out.putString(LinkContract.EXTRA_PIN, LinkRuntime.pairing.openWindow(System.currentTimeMillis())) }
            LinkContract.METHOD_CLOSE_PAIRING -> LinkRuntime.pairing.closeWindow()
            LinkContract.METHOD_FORGET_PHONES -> { LinkRuntime.load(c); LinkRuntime.pairing.forgetAll(); store.tokens = emptyList(); out.putBoolean(LinkContract.EXTRA_OK, true) }
            LinkContract.METHOD_HA_REFRESH -> out.putString(LinkContract.EXTRA_STATE, HaRepository.refresh(c))
            LinkContract.METHOD_HA_ACTION -> {
                val action = extras?.getString(LinkContract.EXTRA_ACTION)
                out.putBoolean(LinkContract.EXTRA_OK, arg != null && action != null && HaRepository.act(c, arg, action))
            }
            LinkContract.METHOD_HA_CONFIGURE -> {
                val url = arg?.trim().orEmpty()
                val token = extras?.getString(LinkContract.EXTRA_VALUE)?.trim()?.ifEmpty { null }
                val ok = url.isNotEmpty() && dev.tvlauncher.link.net.UrlPolicy.parse(if (url.contains("://")) url else "http://$url") != null
                out.putString(LinkContract.EXTRA_STATE, if (ok) HaRepository.configure(c, url, token) else LinkContract.HA_NOT_CONFIGURED)
            }
            LinkContract.METHOD_HA_FORGET -> { HaRepository.forget(c); out.putBoolean(LinkContract.EXTRA_OK, true) }
            LinkContract.METHOD_UPDATE_CHECK -> out.putBoolean(LinkContract.EXTRA_OK, Updates.check(c, force = arg == "force"))
            LinkContract.METHOD_SPOTIFY_SET ->
                out.putString(LinkContract.EXTRA_STATE, SpotifyArt.save(c, arg?.trim().orEmpty(), extras?.getString(LinkContract.EXTRA_VALUE)?.trim().orEmpty()))
            LinkContract.METHOD_SPOTIFY_TEST -> out.putString(LinkContract.EXTRA_STATE, SpotifyArt.test(c))
            LinkContract.METHOD_SPOTIFY_REMOVE -> { SpotifyArt.remove(c); out.putBoolean(LinkContract.EXTRA_OK, true) }
            LinkContract.METHOD_SPOTIFY_ART -> {
                val kind = SpotifyCore.Kind.of(extras?.getString(LinkContract.EXTRA_ART_KIND)) ?: SpotifyCore.Kind.ALBUM
                val key = arg.orEmpty()
                out.putString(LinkContract.EXTRA_STATE, if (!SpotifyCore.validKey(key)) LinkContract.ART_NONE else SpotifyArt.request(
                    c, key, extras?.getString(LinkContract.EXTRA_TITLE), extras?.getString(LinkContract.EXTRA_ARTIST),
                    extras?.getString(LinkContract.EXTRA_ALBUM), kind))
            }
            else -> return null
        }
        return out
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only")
}
