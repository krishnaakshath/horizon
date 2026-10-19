package dev.tvlauncher.browser.net

/**
 * Only plain search phrases are sent for suggestions. Anything that looks like a URL, host name
 * or IP literal (e.g. a LAN address being typed) never leaves the device.
 */
object SuggestionGate {
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){1,3}(:\\d+)?(/.*)?$")
    private val HOSTLIKE = Regex("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/\\S*)?$")
    private val LOCALHOST = Regex("^localhost(:\\d+)?(/\\S*)?$", RegexOption.IGNORE_CASE)

    fun shouldQuery(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.contains("://")) return false
        if (t.any { it.isWhitespace() }) return true // phrases with spaces are searches
        if (IPV4.matches(t) || LOCALHOST.matches(t)) return false
        if (t.startsWith("[") || looksLikeIpv6(t)) return false
        if (HOSTLIKE.matches(t)) return false
        return true
    }

    private fun looksLikeIpv6(t: String): Boolean =
        t.count { it == ':' } >= 2 && t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' || it == '%' }
}
