package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    private fun ok(url: String) = assertTrue("expected allowed: $url", UrlPolicy.isAllowedApkUrl(url))
    private fun no(url: String) = assertFalse("expected rejected: $url", UrlPolicy.isAllowedApkUrl(url))

    @Test fun acceptsPlainHttpsUrls() {
        ok("https://example.org/app.apk")
        ok("https://f-droid.org/repo/org.videolan.vlc_13070206.apk")
        ok("https://github.com/owner/repo/releases/download/v1/app-release.apk")
        ok("https://cdn.example.com:443/path/app.apk?token=abc#frag")
        ok("HTTPS://Example.ORG/App.apk")
    }

    @Test fun rejectsNonHttpsSchemes() {
        no("http://example.org/app.apk")
        no("ftp://example.org/app.apk")
        no("file:///sdcard/app.apk")
        no("content://x/app.apk")
        no("javascript:alert(1)")
        no("//example.org/app.apk")
        no("example.org/app.apk")
        no("")
        no("   ")
    }

    @Test fun rejectsUserInfo() {
        no("https://github.com@evil.com/x.apk")
        no("https://user:pass@example.org/x.apk")
        no("https://@example.org/x.apk")
    }

    @Test fun rejectsNonDefaultPorts() {
        no("https://example.org:8443/x.apk")
        no("https://example.org:80/x.apk")
        no("https://example.org:/x.apk")
        ok("https://example.org:443/x.apk")
    }

    @Test fun rejectsLocalAndPrivateHosts() {
        no("https://localhost/x.apk")
        no("https://LOCALHOST./x.apk")
        no("https://foo.localhost/x.apk")
        no("https://printer.local/x.apk")
        no("https://nas.internal/x.apk")
        no("https://router.home.arpa/x.apk")
        no("https://intranet/x.apk")
        no("https://127.0.0.1/x.apk")
        no("https://127.1.2.3/x.apk")
        no("https://10.0.0.5/x.apk")
        no("https://172.16.0.1/x.apk")
        no("https://172.31.255.255/x.apk")
        no("https://192.168.1.10/x.apk")
        no("https://169.254.169.254/latest/meta-data")
        no("https://100.64.0.1/x.apk")
        no("https://0.0.0.0/x.apk")
        no("https://255.255.255.255/x.apk")
        no("https://224.0.0.1/x.apk")
    }

    @Test fun rejectsObfuscatedIpLiterals() {
        no("https://2130706433/x.apk")
        no("https://0x7f000001/x.apk")
        no("https://0177.0.0.1/x.apk")
        no("https://127.1/x.apk")
        no("https://0x7f.0.0.1/x.apk")
    }

    @Test fun rejectsIpv6Literals() {
        no("https://[::1]/x.apk")
        no("https://[fe80::1]/x.apk")
        no("https://[2001:db8::1]/x.apk")
    }

    @Test fun publicIpv4LiteralIsAllowed() {
        ok("https://93.184.216.34/x.apk")
        ok("https://172.32.0.1/x.apk")
    }

    @Test fun rejectsNonAsciiAndControlCharacters() {
        no("https://exаmple.org/x.apk") // Cyrillic a
        no("https://example.org/x .apk")
        no("https://example.org/x\n.apk")
        no("https://example.org\\@evil.com/x.apk")
        no("https://example.org/‮kpa.exe")
    }

    @Test fun enforcesMaximumLength() {
        val base = "https://example.org/"
        val fits = base + "a".repeat(2048 - base.length)
        assertEquals(2048, fits.length)
        ok(fits)
        no(fits + "a")
    }

    @Test fun longerLimitCanBeUsedForRedirectHops() {
        val long = "https://example.org/" + "a".repeat(3000)
        no(long)
        assertTrue(UrlPolicy.check(long, maxLength = 8192) is UrlPolicy.Verdict.Allowed)
    }

    @Test fun verdictCarriesNormalisedHostAndReadableReason() {
        val v = UrlPolicy.check("https://Example.ORG/app.apk")
        assertEquals("example.org", (v as UrlPolicy.Verdict.Allowed).host)
        val r = UrlPolicy.check("http://example.org/app.apk")
        assertTrue((r as UrlPolicy.Verdict.Rejected).reason.contains("https", ignoreCase = true))
    }

    @Test fun rejectsMissingHostAndOddLabels() {
        no("https:///x.apk")
        no("https://-bad.example/x.apk")
        no("https://exa_mple.org/x.apk")
        no("https://example..org/x.apk")
    }
}
