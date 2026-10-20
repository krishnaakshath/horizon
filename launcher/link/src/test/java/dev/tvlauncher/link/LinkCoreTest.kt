package dev.tvlauncher.link

import dev.tvlauncher.link.auth.Pairing
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.ha.Backoff
import dev.tvlauncher.link.ha.HaClient
import dev.tvlauncher.link.ha.HaParser
import dev.tvlauncher.link.ha.HaRetry
import dev.tvlauncher.link.ha.HaUrls
import dev.tvlauncher.link.ha.ServiceCall
import dev.tvlauncher.link.ha.StateText
import dev.tvlauncher.link.net.HttpParser
import dev.tvlauncher.link.net.HttpRequest
import dev.tvlauncher.link.net.HttpError
import dev.tvlauncher.link.net.IpPolicy
import dev.tvlauncher.link.net.RateLimiter
import dev.tvlauncher.link.net.SafeHttp
import dev.tvlauncher.link.net.UrlPolicy
import dev.tvlauncher.link.sync.BackupCrypto
import dev.tvlauncher.link.sync.BackupZip
import dev.tvlauncher.link.update.UpdateCheck
import dev.tvlauncher.link.util.B64
import dev.tvlauncher.link.web.LinkApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class LinkCoreTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun brand() {
        assertEquals("M02 Link", LinkBrand.APP_NAME)
        assertTrue(File("src/main/res/values/strings.xml").readText().contains(">M02 Link<"))
    }

    @Test fun pbkdf2_rfc7914Vectors() {
        assertArrayEquals(hex("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"),
            BackupCrypto.pbkdf2("passwd".toByteArray(), "salt".toByteArray(), 1, 64))
        assertArrayEquals(hex("4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d"),
            BackupCrypto.pbkdf2("Password".toByteArray(), "NaCl".toByteArray(), 80000, 64))
    }

    @Test fun backup_roundTripFixedVectorAndWrongPassphrase() {
        val salt = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(12) { (it + 100).toByte() }
        val a = BackupCrypto.encryptWith("hello".toByteArray(), "correct horse", 200_000, salt, nonce)
        val b = BackupCrypto.encryptWith("hello".toByteArray(), "correct horse", 200_000, salt, nonce)
        assertArrayEquals(a, b) // deterministic for fixed salt/nonce
        assertEquals(BackupCrypto.HEADER + 5 + 16, a.size)
        assertEquals("hello", String(BackupCrypto.decrypt(a, "correct horse")))
        try { BackupCrypto.decrypt(a, "wrong horse"); throw AssertionError() } catch (e: BackupCrypto.BadPassphrase) { }
        val tampered = a.copyOf().also { it[6] = 9 } // iteration count is authenticated
        try { BackupCrypto.decrypt(tampered, "correct horse"); throw AssertionError() } catch (e: Exception) { }
        val json = "{\"format\":\"tvlauncher-config\"}"
        assertEquals(json, BackupZip.unpack(BackupZip.pack(json)))
        assertNull(BackupZip.unpack(byteArrayOf(1, 2, 3)))
    }

    @Test fun base64() {
        for (s in listOf("", "f", "fo", "foo", "foob", "fooba", "foobar")) {
            assertArrayEquals(s.toByteArray(), B64.decode(B64.encode(s.toByteArray())))
        }
        assertEquals("Zm9vYmFy", B64.encode("foobar".toByteArray()))
        assertNull(B64.decode("Zm9v!"))
    }

    @Test fun ipPolicy() {
        for (ok in listOf("192.168.1.38", "10.0.0.1", "172.16.0.1", "172.31.255.255", "169.254.1.1", "127.0.0.1")) assertTrue(ok, UrlPolicy.isLocalLiteral(ok))
        for (no in listOf("8.8.8.8", "172.32.0.1", "100.64.0.1", "1.1.1.1", "192.169.0.1")) assertFalse(no, UrlPolicy.isLocalLiteral(no))
        assertTrue(UrlPolicy.isLocalLiteral("fe80::1"))
        assertTrue(UrlPolicy.isLocalLiteral("fd00::5"))
        assertTrue(UrlPolicy.isLocalLiteral("::ffff:192.168.0.2"))
        assertFalse(UrlPolicy.isLocalLiteral("2001:db8::1"))
        assertNull(IpPolicy.parseV4("192.168.01.1"))
        assertEquals("192.168.1.38", IpPolicy.formatV4(IpPolicy.pickLanAddress(listOf(hex("7f000001"), hex("0a000002"), hex("c0a80126")))!!))
    }

    @Test fun urlPolicy_httpOnlyOnHomeNetwork() {
        val publicDns = UrlPolicy.Resolver { listOf(hex("08080808")) }
        val localDns = UrlPolicy.Resolver { listOf(hex("c0a8010a")) }
        assertTrue(UrlPolicy.allowed(UrlPolicy.parse("http://192.168.1.10:8123")!!, publicDns))
        assertFalse(UrlPolicy.allowed(UrlPolicy.parse("http://example.com")!!, publicDns))
        assertTrue(UrlPolicy.allowed(UrlPolicy.parse("http://homeassistant.local:8123")!!, localDns))
        assertTrue(UrlPolicy.allowed(UrlPolicy.parse("https://example.com")!!, publicDns))
        assertNull(UrlPolicy.parse("http://user:pw@host/"))
        assertNull(UrlPolicy.parse("file:///etc"))
    }

    @Test fun haUrlsAndAuth() {
        assertEquals("http://homeassistant.local:8123", HaUrls.normalizeBase("homeassistant.local:8123/"))
        assertEquals("https://ha.example.org", HaUrls.normalizeBase("https://ha.example.org:443"))
        assertNull(HaUrls.normalizeBase("javascript:alert(1)"))
        assertEquals("http://h/api/services/light/toggle", HaUrls.service("http://h", "light", "toggle"))
        assertNull(HaUrls.service("http://h", "light/../x", "toggle"))
        val token = "eyJhbGciOiJIUzI1NiJ9.abc.def"
        assertEquals("Bearer $token", HaUrls.authHeader(token))
        assertFalse(HaUrls.isValidToken("abc\r\nX-Evil: 1aaaaaaaaaaaaaa"))
    }

    @Test fun haParsingAndStateMapping() {
        val json = """[
          {"entity_id":"light.kitchen","state":"on","attributes":{"friendly_name":"Kitchen"}},
          {"entity_id":"switch.fan","state":"off","attributes":{}},
          {"entity_id":"scene.movie","state":"scening","attributes":{"friendly_name":"Movie night"}},
          {"entity_id":"climate.living","state":"heat","attributes":{"temperature":21.5,"target_temp_step":0.5,"min_temp":7,"max_temp":30}},
          {"entity_id":"media_player.tv","state":"playing","attributes":{"media_title":"News"}},
          {"entity_id":"sensor.temp","state":"20"},
          {"entity_id":"BAD ID","state":"on"}
        ]"""
        val list = HaParser.parseStates(json)
        assertEquals(listOf("light.kitchen", "switch.fan", "scene.movie", "climate.living", "media_player.tv"), list.map { it.entityId })
        assertEquals("On", StateText.of(list[0]))
        assertEquals("Off", StateText.of(list[1]))
        assertEquals("fan", list[1].name)
        assertEquals("Activate", StateText.of(list[2]))
        assertEquals("Heat · 21.5°", StateText.of(list[3]))
        assertEquals("Playing · News", StateText.of(list[4]))
        assertEquals("toggle", ServiceCall.forAction(list[0], LinkContract.ACTION_TOGGLE)!!.service)
        assertEquals("turn_on", ServiceCall.forAction(list[2], LinkContract.ACTION_ACTIVATE)!!.service)
        assertEquals(22.0, ServiceCall.forAction(list[3], LinkContract.ACTION_TEMP_UP)!!.body.getDouble("temperature"), 0.001)
        assertNull(ServiceCall.forAction(list[0], LinkContract.ACTION_TEMP_UP))
    }

    @Test fun haRetriesOnlyReads() {
        var calls = 0
        val failing = HaClient("http://h", "eyJhbGciOiJIUzI1NiJ9.abc.def", HaClient.Transport { _, _, h, _ ->
            calls++
            assertEquals("Bearer eyJhbGciOiJIUzI1NiJ9.abc.def", h["Authorization"])
            throw IOException("down")
        }, sleep = {})
        assertEquals(LinkContract.HA_UNREACHABLE, (failing.states() as HaClient.Result.Failed).state)
        assertEquals(1 + HaRetry.MAX_RETRIES, calls)
        calls = 0
        val e = HaParser.parseStates("""[{"entity_id":"light.a","state":"on"}]""")[0]
        failing.call(ServiceCall.forAction(e, "toggle")!!)
        assertEquals(1, calls) // never retried
        val auth = HaClient("http://h", "eyJhbGciOiJIUzI1NiJ9.abc.def", HaClient.Transport { _, _, _, _ -> SafeHttp.Response(401, ByteArray(0)) })
        assertEquals(LinkContract.HA_AUTH, (auth.states() as HaClient.Result.Failed).state)
        val b = Backoff()
        assertEquals(5_000L, b.nextDelayMs()); b.failure(); assertEquals(10_000L, b.nextDelayMs())
        repeat(6) { b.failure() }; assertEquals(60_000L, b.nextDelayMs()); b.success(); assertEquals(5_000L, b.nextDelayMs())
    }

    @Test fun pairing() {
        var n = 0
        val p = Pairing { size -> ByteArray(size) { (n++).toByte() } }
        assertEquals(Pairing.Result.NO_WINDOW, p.pair("123456", 0) {})
        val pin = p.openWindow(1000)
        assertEquals(6, pin.length)
        var token: String? = null
        assertEquals(Pairing.Result.WRONG, p.pair("000000".takeIf { it != pin } ?: "111111", 2000) {})
        assertEquals(Pairing.Result.OK, p.pair(pin, 3000) { token = it })
        assertTrue(p.isPaired(token)); assertFalse(p.isPaired("x".repeat(64)))
        assertEquals(Pairing.Result.NO_WINDOW, p.pair(pin, 4000) {}) // window closes after use
        p.openWindow(5000)
        repeat(4) { assertEquals(Pairing.Result.WRONG, p.pair("abcdef", 6000) {}) }
        assertEquals(Pairing.Result.LOCKED, p.pair("abcdef", 6000) {})
        val p2 = p.openWindow(7000)
        assertEquals(Pairing.Result.NO_WINDOW, p.pair(p2, 7000 + Pairing.WINDOW_MS) {})
        assertEquals(p.snapshot(), Pairing.decode(Pairing.encode(p.snapshot())))
    }

    private fun req(text: String) = HttpParser.read(ByteArrayInputStream(text.toByteArray()), LinkApi::maxBody)

    @Test fun httpParserLimits() {
        val r = req("POST /api/key HTTP/1.1\r\nHost: 192.168.1.38:8765\r\nContent-Length: 2\r\nCookie: a=1; h02link=xyz\r\n\r\n{}")
        assertEquals("/api/key", r.path); assertEquals("{}", r.bodyText()); assertEquals("xyz", r.cookie("h02link"))
        fun status(t: String) = try { req(t); 0 } catch (e: HttpError) { e.status }
        assertEquals(411, status("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"))
        assertEquals(413, status("POST /api/key HTTP/1.1\r\nContent-Length: 999999\r\n\r\n"))
        assertEquals(405, status("PUT / HTTP/1.1\r\n\r\n"))
        assertEquals(400, status("GET /../x HTTP/1.1\r\n\r\n"))
        assertEquals(431, status("GET / HTTP/1.1\r\nX: " + "a".repeat(9000) + "\r\n\r\n"))
        assertEquals(400, status("GET / HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n"))
    }

    @Test fun hostAndCsrfChecks() {
        assertTrue(LinkApi.hostOk("192.168.1.38:8765"))
        assertTrue(LinkApi.hostOk("[fe80::1]:8765"))
        assertFalse(LinkApi.hostOk("evil.example.com:8765")) // DNS rebinding
        assertFalse(LinkApi.hostOk("192.168.1.38:80"))
        assertFalse(LinkApi.hostOk("8.8.8.8:8765"))
        fun r(h: Map<String, String>) = HttpRequest("POST", "/api/key", "", h, ByteArray(0))
        val good = mapOf("host" to "192.168.1.38:8765", "x-m02-link" to "1", "content-type" to "application/json")
        assertNull(LinkApi.csrfProblem(r(good)))
        assertEquals("header", LinkApi.csrfProblem(r(good - "x-m02-link")))
        assertEquals("type", LinkApi.csrfProblem(r(good + ("content-type" to "text/plain"))))
        assertEquals("origin", LinkApi.csrfProblem(r(good + ("origin" to "http://evil.example"))))
        assertNull(LinkApi.csrfProblem(r(good + ("origin" to "http://192.168.1.38:8765"))))
        assertEquals("site", LinkApi.csrfProblem(r(good + ("sec-fetch-site" to "cross-site"))))
        val rl = RateLimiter(2.0, 1.0)
        assertTrue(rl.tryAcquire("a", 0)); assertTrue(rl.tryAcquire("a", 0)); assertFalse(rl.tryAcquire("a", 0)); assertTrue(rl.tryAcquire("a", 1000))
    }

    @Test fun contractValidation() {
        assertTrue(LinkContract.isOpenableUrl("https://example.com/a?b=1"))
        assertFalse(LinkContract.isOpenableUrl("javascript:alert(1)"))
        assertFalse(LinkContract.isOpenableUrl("intent://x#Intent;end"))
        assertFalse(LinkContract.isOpenableUrl("https://user@host/"))
        assertTrue(LinkContract.isValidPackage("com.netflix.ninja"))
        assertFalse(LinkContract.isValidPackage("com.x; rm"))
        assertEquals("ab", LinkContract.cleanText("a\u0000b\n"))
        assertEquals(LinkContract.Key.OK, LinkContract.Key.fromWire("ok"))
    }

    @Test fun updateCheck() {
        assertEquals("", UpdateCheck.DEFAULT_REPO) // disabled until the owner sets a repository
        assertNull(UpdateCheck.latestUrl(""))
        assertEquals("https://api.github.com/repos/o/r/releases/latest", UpdateCheck.latestUrl("o/r"))
        assertTrue(UpdateCheck.isNewer("v1.2.0", "1.1.9"))
        assertFalse(UpdateCheck.isNewer("v1.2", "1.2.0"))
        assertFalse(UpdateCheck.isNewer("nightly", "1.0"))
        val r = UpdateCheck.parse("""{"tag_name":"v0.2.0","draft":false,"prerelease":false,"assets":[
            {"name":"launcher-release.apk","browser_download_url":"https://github.com/o/r/releases/download/v0.2.0/launcher-release.apk","size":10,
             "digest":"sha256:${"a".repeat(64)}"}]}""")!!
        assertEquals("a".repeat(64), r.apk!!.sha256)
        assertTrue(UpdateCheck.available(r, "0.1.0"))
        assertFalse(UpdateCheck.available(r.copy(prerelease = true), "0.1.0"))
        assertTrue(UpdateCheck.due(0, 5, false)); assertFalse(UpdateCheck.due(10, 20, false))
        fun a(n: String) = UpdateCheck.Asset(n, "https://x/$n", 1, null)
        val release = listOf("M02-browser-1.0.1.apk", "M02-helper-1.0.1.apk", "M02-app-1.0.1.apk", "M02-link-1.0.1.apk").map(::a)
        assertEquals("M02-app-1.0.1.apk", UpdateCheck.pickApk(release)!!.name)
        val legacy = listOf("H02-browser-1.0.0.apk", "H02-h02-1.0.0.apk", "H02-store-1.0.0.apk").map(::a)
        assertEquals("H02-h02-1.0.0.apk", UpdateCheck.pickApk(legacy)!!.name)
    }

    @Test fun phonePageIsSmallAndSelfContained() {
        val html = File("src/main/assets/phone.html").readText()
        assertTrue(html.toByteArray().size < 150_000)
        assertFalse(html.contains("<script src")); assertFalse(html.contains("<link "))
        assertTrue(html.contains("X-M02-Link"))
    }

    @Test fun volumePercent_roundsAndReadsMutedAsZero() {
        assertEquals(50, dev.tvlauncher.link.web.LinkApi.volumePercent(50, 100, false))
        assertEquals(33, dev.tvlauncher.link.web.LinkApi.volumePercent(5, 15, false))
        assertEquals(0, dev.tvlauncher.link.web.LinkApi.volumePercent(10, 15, true))
        assertEquals(100, dev.tvlauncher.link.web.LinkApi.volumePercent(99, 15, false))
        assertEquals(0, dev.tvlauncher.link.web.LinkApi.volumePercent(3, 0, false))
    }

    @Test fun phonePage_hasTheRemoteParts() {
        val html = File("src/main/assets/phone.html").readText()
        listOf("/api/media", "/api/nowplaying", "/api/quick", "/api/power", "ok_long", "\"del\"", "navigator.vibrate", "manifest")
            .forEach { assertTrue(it, html.contains(it)) }
    }
}
