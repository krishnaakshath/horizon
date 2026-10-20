package dev.tvlauncher.link

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import dev.tvlauncher.link.auth.Pairing
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.ha.HaRepository
import dev.tvlauncher.link.ha.HaUrls
import dev.tvlauncher.link.net.IpPolicy
import dev.tvlauncher.link.net.LinkServer
import dev.tvlauncher.link.remote.Forwarder
import dev.tvlauncher.link.sync.BackupCrypto
import dev.tvlauncher.link.sync.BackupZip
import dev.tvlauncher.link.web.LinkApi
import dev.tvlauncher.link.web.LinkBackend
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface
import java.security.SecureRandom

/** Process-wide state of the phone remote: the pairing state, the server and its address. */
object LinkRuntime {
    private val random = SecureRandom()
    val pairing = Pairing { n -> ByteArray(n).also { random.nextBytes(it) } }
    @Volatile private var loaded = false
    @Volatile var server: LinkServer? = null
        private set

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            pairing.restore(LinkStore(context).tokens)
            loaded = true
        }
    }

    @Synchronized
    fun startServer(context: Context): Boolean {
        load(context)
        if (server?.running == true) return true
        val s = LinkServer(LinkContract.PORT, LinkApi(AndroidBackend(context.applicationContext)))
        return try { s.start(); server = s; true } catch (e: Exception) { false }
    }

    @Synchronized
    fun stopServer() {
        server?.stop()
        server = null
    }

    /** "http://192.168.1.38:8765", or "" while the TV has no home-network address. */
    fun address(): String {
        val all = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { i -> i.inetAddresses.toList().map { it.address } }
        } catch (e: Exception) {
            emptyList()
        }
        val ip = IpPolicy.pickLanAddress(all) ?: return ""
        return "http://${IpPolicy.formatV4(ip)}:${LinkContract.PORT}"
    }

    fun helperOn(context: Context): Boolean = try {
        (Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .split(':').any { it.startsWith(LinkContract.HELPER_PACKAGE + "/") }
    } catch (e: Exception) {
        false
    }
}

/** The phone page's actions on this TV. */
class AndroidBackend(private val context: Context) : LinkBackend {
    private val store = LinkStore(context)
    override val pairing: Pairing get() = LinkRuntime.pairing

    override fun pairingRequired(): Boolean = store.pairingRequired
    override fun pairingChanged() { store.tokens = pairing.snapshot() }

    @Volatile private var pageCache: ByteArray? = null
    override fun page(): ByteArray = pageCache ?: context.assets.open("phone.html").use { it.readBytes() }.also { pageCache = it }

    override fun key(key: LinkContract.Key): Int = Forwarder.key(context, key)
    override fun text(text: String, enter: Boolean): Int = Forwarder.text(context, text, enter)
    override fun openUrl(url: String): Int = Forwarder.openUrl(context, url)
    override fun launch(pkg: String): Int = Forwarder.launch(context, pkg)
    override fun helperOn(): Boolean = LinkRuntime.helperOn(context)

    override fun volume(action: String): Boolean = try {
        val a = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val dir = when (action) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            else -> AudioManager.ADJUST_TOGGLE_MUTE
        }
        a.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        true
    } catch (e: Exception) {
        false
    }

    override fun apps(): List<Pair<String, String>> {
        val pm = context.packageManager
        val seen = LinkedHashMap<String, String>()
        for (cat in listOf("android.intent.category.LEANBACK_LAUNCHER", Intent.CATEGORY_LAUNCHER)) {
            val list = try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0) } catch (e: Exception) { emptyList() }
            for (r in list) {
                val pkg = r.activityInfo?.packageName ?: continue
                if (pkg == context.packageName || pkg == LinkContract.LAUNCHER_PACKAGE || pkg in seen) continue
                val label = try { r.loadLabel(pm).toString() } catch (e: Exception) { pkg }
                seen[pkg] = label.filter { !it.isISOControl() }.take(40)
            }
        }
        return seen.entries.map { it.value to it.key }.sortedBy { it.first.lowercase() }
    }

    override fun haStatus(): JSONObject {
        val state = HaRepository.refresh(context)
        val favs = JSONArray()
        HaRepository.favourites(context).forEach { e ->
            favs.put(JSONObject().put("entity", e.entityId).put("name", e.name).put("control", e.control.wire)
                .put("state", dev.tvlauncher.link.ha.StateText.of(e)).put("on", e.isOn).put("available", e.available))
        }
        return JSONObject().put("configured", store.haConfigured).put("host", HaUrls.displayHost(store.haUrl))
            .put("state", state).put("favourites", favs)
    }

    override fun haConfigure(url: String, token: String?): String = HaRepository.configure(context, url, token)
    override fun haForget() = HaRepository.forget(context)

    override fun haEntities(): JSONArray? {
        if (HaRepository.refresh(context) != LinkContract.HA_OK) return null
        val favs = store.haFavourites.toSet()
        val out = JSONArray()
        HaRepository.all().sortedWith(compareBy({ it.domain }, { it.name.lowercase() })).take(400).forEach { e ->
            out.put(JSONObject().put("entity", e.entityId).put("name", e.name).put("domain", e.domain)
                .put("state", dev.tvlauncher.link.ha.StateText.of(e)).put("favourite", e.entityId in favs))
        }
        return out
    }

    override fun haSetFavourites(ids: List<String>) { store.haFavourites = ids.distinct().take(LinkContract.MAX_HOME_ROWS) }
    override fun haAction(entityId: String, action: String): Boolean = HaRepository.act(context, entityId, action)

    override fun backupExport(passphrase: String): ByteArray? {
        val json = LauncherConfig.export(context) ?: return null
        return BackupZip.seal(json, passphrase)
    }

    override fun backupImport(file: ByteArray, passphrase: String): String {
        val json = try { BackupZip.open(file, passphrase) } catch (e: BackupCrypto.BadPassphrase) {
            return "passphrase"
        } catch (e: Exception) {
            return "file"
        } ?: return "file"
        return if (LauncherConfig.import(context, json)) "ok" else "launcher"
    }
}

/** The launcher's configuration document, through its signature-protected provider. */
object LauncherConfig {
    private val uri: Uri = Uri.parse(LinkContract.URI_CONFIG)

    fun export(context: Context): String? = try {
        context.contentResolver.call(uri, LinkContract.METHOD_EXPORT_CONFIG, null, null)?.getString(LinkContract.EXTRA_JSON)
    } catch (e: Exception) {
        null
    }

    fun import(context: Context, json: String): Boolean = try {
        val extras = Bundle().apply { putString(LinkContract.EXTRA_JSON, json) }
        context.contentResolver.call(uri, LinkContract.METHOD_IMPORT_CONFIG, null, extras)?.getBoolean(LinkContract.EXTRA_OK) == true
    } catch (e: Exception) {
        false
    }

    fun launcherInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(LinkContract.LAUNCHER_PACKAGE, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
