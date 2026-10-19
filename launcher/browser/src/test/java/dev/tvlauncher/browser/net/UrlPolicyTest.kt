package dev.tvlauncher.browser.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    @Test fun allows_httpHttpsAndAboutBlank() {
        assertTrue(UrlPolicy.isNavigationAllowed("https://example.com/a?b=1"))
        assertTrue(UrlPolicy.isNavigationAllowed("http://example.com"))
        assertTrue(UrlPolicy.isNavigationAllowed("HTTPS://EXAMPLE.COM"))
        assertTrue(UrlPolicy.isNavigationAllowed("about:blank"))
    }

    @Test fun blocks_dangerousSchemes() {
        listOf(
            "file:///etc/hosts", "content://contacts/people", "intent://x#Intent;end",
            "javascript:alert(1)", "JavaScript:alert(1)", "data:text/html,<b>x</b>",
            "about:config", "market://details?id=x", "chrome://settings", "tel:123", "",
            "https://", "https:///nohost", "  https://example.com"
        ).forEach { assertFalse(it, UrlPolicy.isNavigationAllowed(it)) }
    }

    @Test fun storable_requiresHttpAndLengthCap() {
        assertTrue(UrlPolicy.isStorable("https://a.com/" + "x".repeat(100)))
        assertFalse(UrlPolicy.isStorable("https://a.com/" + "x".repeat(UrlPolicy.MAX_URL)))
        assertFalse(UrlPolicy.isStorable("about:blank"))
        assertFalse(UrlPolicy.isStorable("javascript:1"))
    }
}
