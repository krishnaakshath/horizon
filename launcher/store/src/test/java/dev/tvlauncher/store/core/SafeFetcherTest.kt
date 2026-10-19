package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SafeFetcherTest {

    private val any = SafeFetcher.HopRule { _, _ -> true }

    private fun expectPolicy(c: FakeConnector, url: String, rule: SafeFetcher.HopRule = any): PolicyError {
        try {
            SafeFetcher(c).open(url, rule)
            fail("expected PolicyError")
            throw AssertionError()
        } catch (e: PolicyError) {
            return e
        }
    }

    @Test fun followsHttpsRedirects() {
        val c = FakeConnector()
        c.redirect("https://a.example/x.apk", "https://b.example/y.apk")
        c.ok("https://b.example/y.apk", "hi".toByteArray())
        val r = SafeFetcher(c).open("https://a.example/x.apk", any)
        assertEquals(200, r.code)
        assertEquals(listOf("https://a.example/x.apk", "https://b.example/y.apk"), c.opened)
    }

    @Test fun httpsToHttpRedirectIsRefusedBeforeConnecting() {
        val c = FakeConnector()
        c.redirect("https://a.example/x.apk", "http://a.example/x.apk")
        expectPolicy(c, "https://a.example/x.apk")
        assertEquals(listOf("https://a.example/x.apk"), c.opened)
    }

    @Test fun httpStartUrlIsRefusedWithoutAnyConnection() {
        val c = FakeConnector()
        expectPolicy(c, "http://a.example/x.apk")
        assertTrue(c.opened.isEmpty())
        assertTrue(c.resolved.isEmpty())
    }

    @Test fun protocolRelativeRedirectIsRevalidated() {
        val c = FakeConnector()
        c.redirect("https://a.example/x.apk", "//evil.example/x.apk")
        c.ok("https://evil.example/x.apk", "x".toByteArray())
        // allowed by an open rule (it's https), refused by a source rule that pins the host
        assertEquals(200, SafeFetcher(c).open("https://a.example/x.apk", any).code)
        val c2 = FakeConnector()
        c2.redirect("https://a.example/x.apk", "//evil.example/x.apk")
        expectPolicy(c2, "https://a.example/x.apk") { u, _ -> u.startsWith("https://a.example/") }
        assertEquals(1, c2.opened.size)
    }

    @Test fun backslashRedirectTrickIsRefused() {
        val c = FakeConnector()
        c.redirect("https://a.example/x.apk", "/\\evil.example/x.apk")
        expectPolicy(c, "https://a.example/x.apk")
        assertEquals(1, c.opened.size)
    }

    @Test fun moreThanFiveRedirectsIsRefused() {
        val c = FakeConnector()
        for (i in 0 until 6) c.redirect("https://a.example/$i", "https://a.example/${i + 1}")
        c.ok("https://a.example/6", "x".toByteArray())
        val e = expectPolicy(c, "https://a.example/0")
        assertTrue(e.message!!.contains("Too many"))
        // exactly five is fine
        val c5 = FakeConnector()
        for (i in 0 until 5) c5.redirect("https://a.example/$i", "https://a.example/${i + 1}")
        c5.ok("https://a.example/5", "x".toByteArray())
        assertEquals(200, SafeFetcher(c5).open("https://a.example/0", any).code)
    }

    @Test fun crossHostRedirectForAllowlistedSourceIsRevalidatedPerHop() {
        val c = FakeConnector()
        val start = "https://github.com/o/r/releases/download/v1/a.apk"
        c.redirect(start, "https://raw.githubusercontent.com/o/r/main/a.apk")
        val rule = SafeFetcher.HopRule { u, hop ->
            if (hop == 0) u == start else HostAllowlist.isGithubRedirectTarget(u)
        }
        expectPolicy(c, start, rule)
        val ok = FakeConnector()
        ok.redirect(start, "https://release-assets.githubusercontent.com/x?sig=1")
        ok.ok("https://release-assets.githubusercontent.com/x?sig=1", "x".toByteArray())
        assertEquals(200, SafeFetcher(ok).open(start, rule).code)
    }

    @Test fun privateResolvedAddressIsRefusedAtEveryHop() {
        val c = FakeConnector()
        c.dns["intra.example"] = listOf(byteArrayOf(10, 0, 0, 5))
        expectPolicy(c, "https://intra.example/x.apk")
        assertTrue(c.opened.isEmpty())

        val c2 = FakeConnector()
        c2.dns["rebind.example"] = listOf(byteArrayOf(93, 184.toByte(), 216.toByte(), 34), byteArrayOf(127, 0, 0, 1))
        c2.redirect("https://a.example/x.apk", "https://rebind.example/x.apk")
        expectPolicy(c2, "https://a.example/x.apk")
        assertEquals(listOf("https://a.example/x.apk"), c2.opened)
    }

    @Test fun redirectToPrivateLiteralIsRefused() {
        val c = FakeConnector()
        c.redirect("https://a.example/x.apk", "https://169.254.169.254/latest/meta-data")
        expectPolicy(c, "https://a.example/x.apk")
        assertEquals(1, c.opened.size)
    }

    @Test fun httpErrorsCarryCodeAndBody() {
        val c = FakeConnector()
        c.routes["https://a.example/x"] = FakeConnector.Spec(403, headers = mapOf("X-RateLimit-Remaining" to listOf("0")))
        try {
            SafeFetcher(c).open("https://a.example/x", any)
            fail()
        } catch (e: HttpError) {
            assertEquals(403, e.code)
            assertEquals("0", e.header("x-ratelimit-remaining"))
            assertTrue(e.body!!.contains("nope"))
        }
    }
}
