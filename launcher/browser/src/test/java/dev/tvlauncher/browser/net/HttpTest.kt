package dev.tvlauncher.browser.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class HttpTest {
    private class FakeConn(
        override val responseCode: Int, private val headers: Map<String, String> = emptyMap(),
        override val contentType: String? = null, private val body: ByteArray = ByteArray(0),
        override val contentLength: Long = -1
    ) : HttpConn {
        var read = 0
        var disconnected = false
        override fun configure(timeoutMs: Int) {}
        override fun header(name: String) = headers[name]
        override fun body(): InputStream = object : ByteArrayInputStream(body) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) read += it }
        }
        override fun disconnect() { disconnected = true }
    }

    private val public = listOf(byteArrayOf(8, 8, 8, 8))
    private val private = listOf(byteArrayOf(192.toByte(), 168.toByte(), 1, 1))
    private val img = { t: String -> t.startsWith("image/") }

    private fun http(conns: Map<String, FakeConn>, opened: MutableList<String> = ArrayList(), resolve: (String) -> List<ByteArray>? = { public }) =
        Http(open = { u -> opened.add(u); conns[u] }, resolve = resolve)

    @Test fun ok_image() {
        val r = http(mapOf("https://a.com/i.png" to FakeConn(200, contentType = "image/png", body = byteArrayOf(1, 2, 3))))
            .get("https://a.com/i.png", 256, 4000, img)
        assertNotNull(r)
        assertArrayEquals(byteArrayOf(1, 2, 3), r!!.bytes)
    }

    @Test fun redirectToHttp_isUpgradedConsistently() {
        val opened = ArrayList<String>()
        val r = http(mapOf(
            "https://a.com/i.png" to FakeConn(301, mapOf("Location" to "http://b.com/j.png")),
            "https://b.com/j.png" to FakeConn(200, contentType = "image/png", body = byteArrayOf(9))
        ), opened).get("https://a.com/i.png", 256, 4000, img)
        assertEquals("https://b.com/j.png", r!!.finalUrl)
        assertEquals(listOf("https://a.com/i.png", "https://b.com/j.png"), opened)
    }

    @Test fun relativeRedirect_resolved() {
        val r = http(mapOf(
            "https://a.com/x/i.png" to FakeConn(302, mapOf("Location" to "../y.png")),
            "https://a.com/y.png" to FakeConn(200, contentType = "image/png", body = byteArrayOf(1))
        )).get("https://a.com/x/i.png", 256, 4000, img)
        assertEquals("https://a.com/y.png", r!!.finalUrl)
    }

    @Test fun moreThan3Redirects_refused() {
        val conns = (0..4).associate { "https://a.com/$it" to FakeConn(302, mapOf("Location" to "https://a.com/${it + 1}")) } +
            ("https://a.com/5" to FakeConn(200, contentType = "image/png", body = byteArrayOf(1)))
        assertNull(http(conns).get("https://a.com/0", 256, 4000, img))
        val three = (0..2).associate { "https://a.com/$it" to FakeConn(302, mapOf("Location" to "https://a.com/${it + 1}")) } +
            ("https://a.com/3" to FakeConn(200, contentType = "image/png", body = byteArrayOf(1)))
        assertNotNull(http(three).get("https://a.com/0", 256, 4000, img))
    }

    @Test fun oversizeBody_abortedAtCap() {
        val c = FakeConn(200, contentType = "image/png", body = ByteArray(300 * 1024))
        assertNull(http(mapOf("https://a.com/big" to c)).get("https://a.com/big", 256 * 1024, 4000, img))
        assertTrue("stopped near the cap, read=${c.read}", c.read <= 256 * 1024 + 8 * 1024)
        assertTrue(c.disconnected)
    }

    @Test fun declaredLengthOverCap_refusedWithoutReading() {
        val c = FakeConn(200, contentType = "image/png", body = ByteArray(10), contentLength = 999_999)
        assertNull(http(mapOf("https://a.com/big" to c)).get("https://a.com/big", 1024, 4000, img))
        assertEquals(0, c.read)
    }

    @Test fun truncateOk_returnsHead() {
        val r = http(mapOf("https://a.com/" to FakeConn(200, contentType = "text/html", body = ByteArray(5000) { 65 })))
            .get("https://a.com/", 1000, 4000, { true }, truncateOk = true)
        assertEquals(1000, r!!.bytes.size)
        assertTrue(r.truncated)
    }

    @Test fun nonImageContentType_rejected() {
        assertNull(http(mapOf("https://a.com/i" to FakeConn(200, contentType = "text/html", body = byteArrayOf(1))))
            .get("https://a.com/i", 256, 4000, img))
        assertNull(http(mapOf("https://a.com/i" to FakeConn(200, contentType = null, body = byteArrayOf(1))))
            .get("https://a.com/i", 256, 4000, img))
    }

    @Test fun httpStart_refused() {
        val opened = ArrayList<String>()
        assertNull(http(emptyMap(), opened).get("http://a.com/i.png", 256, 4000, img))
        assertTrue(opened.isEmpty())
    }

    @Test fun privateHost_refused_beforeConnecting() {
        val opened = ArrayList<String>()
        assertNull(http(mapOf("https://nas.lan/i.png" to FakeConn(200, contentType = "image/png")), opened) { private }
            .get("https://nas.lan/i.png", 256, 4000, img))
        assertTrue(opened.isEmpty())
    }

    @Test fun redirectToPrivateHost_refused() {
        val opened = ArrayList<String>()
        val h = http(mapOf(
            "https://a.com/i" to FakeConn(302, mapOf("Location" to "https://router.lan/i")),
            "https://router.lan/i" to FakeConn(200, contentType = "image/png", body = byteArrayOf(1))
        ), opened) { host -> if (host == "router.lan") private else public }
        assertNull(h.get("https://a.com/i", 256, 4000, img))
        assertEquals(listOf("https://a.com/i"), opened)
    }

    @Test fun unresolvableHost_refused() {
        assertNull(http(emptyMap()) { null }.get("https://a.com/i", 256, 4000, img))
    }

    @Test fun ipLiteralHost_checkedToo() {
        val opened = ArrayList<String>()
        assertNull(http(emptyMap(), opened) { private }.get("https://192.168.1.1/i.png", 256, 4000, img))
        assertTrue(opened.isEmpty())
    }
}
