package dev.tvlauncher.link.web

import dev.tvlauncher.link.auth.Pairing
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.net.HttpRequest
import dev.tvlauncher.link.net.HttpResponse
import dev.tvlauncher.link.net.IpPolicy
import dev.tvlauncher.link.net.RateLimiter
import dev.tvlauncher.link.net.UrlPolicy
import dev.tvlauncher.link.util.B64
import org.json.JSONArray
import org.json.JSONObject

/** What the phone page can ask of the TV. Implemented by the service; faked in tests. */
interface LinkBackend {
    val pairing: Pairing
    fun pairingRequired(): Boolean
    /** Persists the paired-phone list after a change. */
    fun pairingChanged()
    fun page(): ByteArray
    /** Each returns a [LinkContract.Outcome] code. */
    fun key(key: LinkContract.Key): Int
    fun text(text: String, enter: Boolean): Int
    fun openUrl(url: String): Int
    fun launch(pkg: String): Int
    fun volume(action: String): Boolean
    /** Current music volume: level, max, muted (null when unreadable). */
    fun volumeLevel(): Triple<Int, Int, Boolean>? = null
    fun media(action: String): Int = LinkContract.Outcome.NONE
    /** What is playing, as JSON for the phone (empty object when nothing). */
    fun nowPlaying(): JSONObject = JSONObject()
    fun powerMenu(): Int = LinkContract.Outcome.NONE
    fun quick(item: String): Int = LinkContract.Outcome.NONE
    fun apps(): List<Pair<String, String>>
    fun helperOn(): Boolean
    fun haStatus(): JSONObject
    /** Saves (and tests) the connection; [token] null keeps the stored one. Returns an HA_* state. */
    fun haConfigure(url: String, token: String?): String
    fun haForget()
    /** Every supported entity with a "favourite" flag, or null when it cannot be reached. */
    fun haEntities(): JSONArray?
    fun haSetFavourites(ids: List<String>)
    fun haAction(entityId: String, action: String): Boolean
    fun backupExport(passphrase: String): ByteArray?
    /** "ok", "passphrase", "file" or "launcher". */
    fun backupImport(file: ByteArray, passphrase: String): String
}

/**
 * The phone page's HTTP surface. Order of checks for every request:
 *
 * 1. the client is on the home network ([IpPolicy.isLocal]; the server also checks before reading);
 * 2. the Host header names this TV by a local IP literal (defeats DNS rebinding: a public name
 *    that resolves to the TV is refused) and the port is [LinkContract.PORT];
 * 3. a per-client rate limit;
 * 4. for the /api/ paths: POST with Content-Type application/json, the custom header X-H02-Link: 1 (a
 *    cross-site form or image cannot send it, and a cross-site fetch with it needs a CORS
 *    preflight we never answer), Origin and Sec-Fetch-Site, when present, must be this origin;
 * 5. the pairing cookie (unless pairing is switched off), except for /api/hello and /api/pair,
 *    which has its own slower limit.
 * No response ever carries Access-Control-* headers.
 */
class LinkApi(private val backend: LinkBackend, private val now: () -> Long = System::currentTimeMillis) {
    private val general = RateLimiter(capacity = 60.0, perSecond = 25.0)
    private val pairLimit = RateLimiter(capacity = 5.0, perSecond = 5.0 / 60.0)
    private val heavy = RateLimiter(capacity = 3.0, perSecond = 1.0 / 20.0)

    fun handle(req: HttpRequest, client: ByteArray): HttpResponse {
        if (!IpPolicy.isLocal(client)) return HttpResponse.error(403, "local_only")
        if (!hostOk(req.header("host"))) return HttpResponse.error(421, "host")
        val who = client.joinToString(".") { (it.toInt() and 0xff).toString() }
        if (!general.tryAcquire(who, now())) return HttpResponse.error(429, "slow_down")

        if (req.method == "GET" || req.method == "HEAD") {
            return when (req.path) {
                "/", "/index.html" -> HttpResponse(200, "text/html; charset=utf-8", backend.page())
                else -> HttpResponse.error(404, "not_found")
            }
        }
        if (!req.path.startsWith("/api/")) return HttpResponse.error(404, "not_found")
        csrfProblem(req)?.let { return HttpResponse.error(403, it) }
        val body = try {
            if (req.body.isEmpty()) JSONObject() else JSONObject(req.bodyText())
        } catch (e: Exception) {
            return HttpResponse.error(400, "json")
        }
        val token = req.cookie(Pairing.COOKIE)
        val paired = backend.pairing.isPaired(token)
        when (req.path) {
            "/api/hello" -> return ok(JSONObject()
                .put("paired", paired || !backend.pairingRequired())
                .put("pairingRequired", backend.pairingRequired())
                .put("windowOpen", backend.pairing.windowOpen(now()))
                .put("helper", backend.helperOn()))
            "/api/pair" -> return pair(body, who)
        }
        if (backend.pairingRequired() && !paired) return HttpResponse.error(401, "pair_first")
        return route(req.path, body, token, who)
    }

    private fun route(path: String, body: JSONObject, token: String?, who: String): HttpResponse = when (path) {
        "/api/key" -> {
            val k = LinkContract.Key.fromWire(body.optString("key")) ?: return HttpResponse.error(400, "key")
            outcome(backend.key(k))
        }
        "/api/text" -> {
            val text = LinkContract.cleanText(body.optString("text", ""))
            outcome(backend.text(text, enter = body.optBoolean("enter", false)))
        }
        "/api/volume" -> {
            val a = body.optString("action")
            if (a !in setOf("up", "down", "mute", "get")) HttpResponse.error(400, "action")
            else {
                val done = a == "get" || backend.volume(a)
                ok(volumeJson(JSONObject().put("done", done)))
            }
        }
        "/api/media" -> {
            val a = body.optString("action")
            if (a !in LinkContract.MEDIA_ACTIONS) HttpResponse.error(400, "action") else outcome(backend.media(a))
        }
        "/api/nowplaying" -> ok(JSONObject().put("np", backend.nowPlaying()).also { volumeJson(it) })
        "/api/power" -> outcome(backend.powerMenu())
        "/api/quick" -> {
            val item = body.optString("item")
            if (item !in LinkContract.QUICK_ITEMS) HttpResponse.error(400, "item") else outcome(backend.quick(item))
        }
        "/api/open" -> {
            val url = body.optString("url").trim().let { if (it.isNotEmpty() && !it.contains("://")) "https://$it" else it }
            if (!LinkContract.isOpenableUrl(url)) HttpResponse.error(400, "url") else outcome(backend.openUrl(url))
        }
        "/api/apps" -> ok(JSONObject().put("apps", JSONArray().also { a ->
            backend.apps().take(MAX_APPS).forEach { (label, pkg) -> a.put(JSONObject().put("label", label).put("package", pkg)) }
        }))
        "/api/launch" -> {
            val pkg = body.optString("package")
            if (!LinkContract.isValidPackage(pkg)) HttpResponse.error(400, "package") else outcome(backend.launch(pkg))
        }
        "/api/forget" -> { backend.pairing.forget(token); backend.pairingChanged(); ok(JSONObject()) }
        "/api/ha/status" -> ok(backend.haStatus())
        "/api/ha/config" -> {
            val url = body.optString("url")
            val t = body.optString("token", "").trim().ifEmpty { null }
            if (UrlPolicy.parse(if (url.contains("://")) url else "http://$url") == null) HttpResponse.error(400, "url")
            else ok(JSONObject().put("state", backend.haConfigure(url, t)))
        }
        "/api/ha/forget" -> { backend.haForget(); ok(JSONObject()) }
        "/api/ha/entities" -> backend.haEntities()?.let { ok(JSONObject().put("entities", it)) }
            ?: HttpResponse.error(502, "unreachable")
        "/api/ha/favourites" -> {
            val arr = body.optJSONArray("ids") ?: JSONArray()
            val ids = (0 until minOf(arr.length(), LinkContract.MAX_HOME_ROWS)).map { arr.optString(it) }
            if (ids.any { !dev.tvlauncher.link.ha.HaParser.isEntityId(it) }) HttpResponse.error(400, "ids")
            else { backend.haSetFavourites(ids); ok(JSONObject()) }
        }
        "/api/ha/action" -> {
            val id = body.optString("entity")
            val action = body.optString("action")
            if (!dev.tvlauncher.link.ha.HaParser.isEntityId(id) || action !in HA_ACTIONS) HttpResponse.error(400, "action")
            else ok(JSONObject().put("done", backend.haAction(id, action)))
        }
        "/api/backup/export" -> {
            val pass = body.optString("passphrase")
            when {
                !dev.tvlauncher.link.sync.BackupCrypto.passphraseOk(pass) -> HttpResponse.error(400, "passphrase")
                !heavy.tryAcquire(who, now()) -> HttpResponse.error(429, "slow_down")
                else -> backend.backupExport(pass)?.let {
                    HttpResponse(200, "application/octet-stream", it,
                        listOf("Content-Disposition" to "attachment; filename=\"${dev.tvlauncher.link.sync.BackupZip.FILE_NAME}\""))
                } ?: HttpResponse.error(502, "launcher")
            }
        }
        "/api/backup/import" -> {
            val pass = body.optString("passphrase")
            val data = B64.decode(body.optString("data"))
            when {
                data == null || data.isEmpty() -> HttpResponse.error(400, "file")
                !heavy.tryAcquire(who, now()) -> HttpResponse.error(429, "slow_down")
                else -> {
                    val r = backend.backupImport(data, pass)
                    if (r == "ok") ok(JSONObject()) else HttpResponse.error(400, r)
                }
            }
        }
        else -> HttpResponse.error(404, "not_found")
    }

    private fun pair(body: JSONObject, who: String): HttpResponse {
        if (!pairLimit.tryAcquire(who, now())) return HttpResponse.error(429, "slow_down")
        var cookie: String? = null
        val r = backend.pairing.pair(body.optString("pin", ""), now()) { cookie = it }
        return when (r) {
            Pairing.Result.OK -> {
                backend.pairingChanged()
                HttpResponse.json(200, "{\"ok\":true}", listOf("Set-Cookie" to
                    "${Pairing.COOKIE}=$cookie; Path=/; Max-Age=31536000; HttpOnly; SameSite=Strict"))
            }
            Pairing.Result.WRONG -> HttpResponse.error(403, "wrong_pin")
            Pairing.Result.LOCKED -> HttpResponse.error(403, "locked")
            Pairing.Result.NO_WINDOW -> HttpResponse.error(403, "no_window")
        }
    }

    private fun volumeJson(o: JSONObject): JSONObject {
        backend.volumeLevel()?.let { (level, max, muted) ->
            o.put("volume", JSONObject().put("level", level).put("max", max).put("muted", muted)
                .put("percent", volumePercent(level, max, muted)))
        }
        return o
    }

    private fun ok(o: JSONObject) = HttpResponse.json(200, o.put("ok", true).toString())

    private fun outcome(code: Int): HttpResponse =
        ok(JSONObject().put("where", LinkContract.Outcome.name(code))
            .put("done", code == LinkContract.Outcome.LAUNCHER || code == LinkContract.Outcome.HELPER))

    companion object {
        const val MAX_APPS = 200
        const val MAX_BODY = 16 * 1024
        const val MAX_UPLOAD = 1_500_000
        val HA_ACTIONS = setOf(
            LinkContract.ACTION_TOGGLE, LinkContract.ACTION_ACTIVATE, LinkContract.ACTION_TEMP_UP,
            LinkContract.ACTION_TEMP_DOWN, LinkContract.ACTION_PLAY_PAUSE,
        )

        /** 0..100 for the phone's level bar; muted reads 0. */
        fun volumePercent(level: Int, max: Int, muted: Boolean): Int =
            if (muted || max <= 0) 0 else (level.coerceIn(0, max) * 100 + max / 2) / max

        fun maxBody(path: String): Int = if (path == "/api/backup/import") MAX_UPLOAD else MAX_BODY

        /** "192.168.1.38:8765", "[fe80::1]:8765" or "localhost:8765"; never a DNS name. */
        fun hostOk(host: String?): Boolean {
            if (host == null || host.isEmpty() || host.length > 64) return false
            val (h, port) = if (host.startsWith("[")) {
                val end = host.indexOf(']')
                if (end < 0) return false
                host.substring(1, end) to host.substring(end + 1).removePrefix(":")
            } else {
                host.substringBefore(':') to (if (':' in host) host.substringAfter(':') else "")
            }
            if (port.isNotEmpty() && port != LinkContract.PORT.toString()) return false
            if (h == "localhost") return true
            return UrlPolicy.isLocalLiteral(h)
        }

        /** Null when the request passes the cross-site checks, else the reason. */
        fun csrfProblem(req: HttpRequest): String? {
            if (req.method != "POST") return "method"
            if (req.header("x-h02-link") != "1") return "header"
            val type = req.header("content-type")?.substringBefore(';')?.trim()?.lowercase()
            if (type != "application/json") return "type"
            val origin = req.header("origin")
            if (origin != null && origin != "http://" + req.header("host")) return "origin"
            val site = req.header("sec-fetch-site")
            if (site != null && site != "same-origin" && site != "none") return "site"
            return null
        }
    }
}
