package dev.tvlauncher.link

import android.content.Context
import dev.tvlauncher.link.auth.Pairing
import dev.tvlauncher.link.update.UpdateCheck

/**
 * Link's private storage (app-private SharedPreferences; allowBackup is off). Holds the
 * Home Assistant URL and token, the WebDAV location and the hashes of paired phones. Nothing
 * here is ever returned to the launcher or the phone page except the "configured" facts.
 */
class LinkStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("link", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) { p.edit().putBoolean("enabled", v).apply() }

    var pairingRequired: Boolean
        get() = p.getBoolean("pairing_required", true)
        set(v) { p.edit().putBoolean("pairing_required", v).apply() }

    var tokens: List<Pairing.Token>
        get() = Pairing.decode(p.getString("tokens", ""))
        set(v) { p.edit().putString("tokens", Pairing.encode(v)).apply() }

    var haUrl: String
        get() = p.getString("ha_url", "") ?: ""
        set(v) { p.edit().putString("ha_url", v).apply() }

    var haToken: String
        get() = p.getString("ha_token", "") ?: ""
        set(v) { p.edit().putString("ha_token", v).apply() }

    var haFavourites: List<String>
        get() = (p.getString("ha_favs", "") ?: "").split(',').filter { it.isNotEmpty() }
        set(v) { p.edit().putString("ha_favs", v.joinToString(",")).apply() }

    val haConfigured: Boolean get() = haUrl.isNotEmpty() && haToken.isNotEmpty()

    var davUrl: String
        get() = p.getString("dav_url", "") ?: ""
        set(v) { p.edit().putString("dav_url", v).apply() }

    var davUser: String
        get() = p.getString("dav_user", "") ?: ""
        set(v) { p.edit().putString("dav_user", v).apply() }

    var davPassword: String
        get() = p.getString("dav_pass", "") ?: ""
        set(v) { p.edit().putString("dav_pass", v).apply() }

    var updateEnabled: Boolean
        get() = p.getBoolean("update_enabled", false)
        set(v) { p.edit().putBoolean("update_enabled", v).apply() }

    var updateRepo: String
        get() = p.getString("update_repo", UpdateCheck.DEFAULT_REPO) ?: ""
        set(v) { p.edit().putString("update_repo", v).apply() }

    var updateLatest: String
        get() = p.getString("update_latest", "") ?: ""
        set(v) { p.edit().putString("update_latest", v).apply() }

    var updateAvailable: Boolean
        get() = p.getBoolean("update_available", false)
        set(v) { p.edit().putBoolean("update_available", v).apply() }

    var updateSha: Boolean
        get() = p.getBoolean("update_sha", false)
        set(v) { p.edit().putBoolean("update_sha", v).apply() }

    var updateCheckedMs: Long
        get() = p.getLong("update_checked", 0L)
        set(v) { p.edit().putLong("update_checked", v).apply() }
}
