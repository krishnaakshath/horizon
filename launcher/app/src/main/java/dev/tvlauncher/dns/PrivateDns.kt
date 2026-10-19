package dev.tvlauncher.dns

/**
 * Android's system-wide Private DNS (DNS over TLS, port 853), as stored in Settings.Global:
 * `private_dns_mode` = "off" | "opportunistic" | "hostname" and `private_dns_specifier` = the
 * resolver host for "hostname". Pure.
 */
object PrivateDns {
    const val KEY_MODE = "private_dns_mode"
    const val KEY_SPECIFIER = "private_dns_specifier"
    const val MODE_OFF = "off"
    const val MODE_OPPORTUNISTIC = "opportunistic"
    const val MODE_HOSTNAME = "hostname"

    const val EXPLAIN = "Works across all apps"
    const val NEEDS_SETUP = "Finish setup to enable"

    /** The owner's choices, in menu order. */
    enum class Choice(val label: String, val mode: String, val host: String?, val note: String) {
        AUTOMATIC("Automatic", MODE_OPPORTUNISTIC, null, "Uses encrypted DNS when your network offers it"),
        CLOUDFLARE("Cloudflare", MODE_HOSTNAME, "one.one.one.one", "1.1.1.1, fast and private"),
        CLOUDFLARE_SECURITY("Cloudflare Security", MODE_HOSTNAME, "security.cloudflare-dns.com", "Blocks malware"),
        CLOUDFLARE_FAMILY("Cloudflare Family", MODE_HOSTNAME, "family.cloudflare-dns.com", "Blocks malware and adult content"),
        ADGUARD("AdGuard DNS", MODE_HOSTNAME, "dns.adguard-dns.com", "Blocks ads and trackers"),
        GOOGLE("Google", MODE_HOSTNAME, "dns.google", "Google Public DNS"),
        OFF("Off", MODE_OFF, null, "Your network's own DNS, unencrypted")
    }

    /** What the TV is set to now. [custom] is a hostname that is not one of [Choice]. */
    data class Current(val choice: Choice?, val custom: String?) {
        val label: String get() = choice?.label ?: custom?.let { "Custom ($it)" } ?: Choice.AUTOMATIC.label
    }

    private val HOST = Regex("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")

    fun isValidHost(h: String?): Boolean = h != null && HOST.matches(h)

    /** Choice for a known resolver host (case-insensitive, trailing dot allowed; 1dot1dot1dot1 alias). */
    fun choiceForHost(host: String?): Choice? {
        val h = host?.trim()?.trimEnd('.')?.lowercase() ?: return null
        if (h == "1dot1dot1dot1.cloudflare-dns.com" || h == "cloudflare-dns.com") return Choice.CLOUDFLARE
        if (h == "dns.adguard.com") return Choice.ADGUARD // the older name of the same default resolver
        return Choice.values().firstOrNull { it.host == h }
    }

    /**
     * Parses the two settings. Unset or unknown mode is Android's default ("opportunistic", or
     * [defaultMode] from `private_dns_default_mode` when the TV sets one).
     */
    fun parse(mode: String?, specifier: String?, defaultMode: String? = null): Current {
        val m = mode?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: defaultMode?.trim()?.lowercase()
        return when (m) {
            MODE_OFF -> Current(Choice.OFF, null)
            MODE_HOSTNAME -> {
                val known = choiceForHost(specifier)
                when {
                    known != null -> Current(known, null)
                    isValidHost(specifier?.trim()) -> Current(null, specifier!!.trim())
                    else -> Current(Choice.AUTOMATIC, null) // "hostname" without a usable host: Android falls back
                }
            }
            else -> Current(Choice.AUTOMATIC, null)
        }
    }

    /**
     * The writes for [choice], in order (specifier before mode, so the resolver never switches to
     * strict mode with the old host). Automatic and Off leave the stored host alone, so going back
     * to a resolver later needs no re-typing.
     */
    fun writes(choice: Choice): List<Pair<String, String>> = buildList {
        choice.host?.let { add(KEY_SPECIFIER to it) }
        add(KEY_MODE to choice.mode)
    }

    /**
     * Writes that put the previous values back (after a failed check); null means "unset".
     * Leaving strict mode goes first, so the TV never sits in strict mode with a stale host.
     */
    fun restore(previousMode: String?, previousSpecifier: String?): List<Pair<String, String?>> =
        if (previousMode?.trim()?.lowercase() == MODE_HOSTNAME) listOf(KEY_SPECIFIER to previousSpecifier, KEY_MODE to previousMode)
        else listOf(KEY_MODE to previousMode, KEY_SPECIFIER to previousSpecifier)

    /** The picker's row value for [c] while [current] is set. */
    fun marker(c: Choice, current: Current): String = if (current.choice == c) "On" else ""

    fun chooseSubtitle(canWrite: Boolean): String = if (canWrite) EXPLAIN else NEEDS_SETUP
}

/**
 * After switching to a resolver, Android validates it. In strict ("hostname") mode an
 * unreachable resolver means no DNS at all, so the page checks the network's state for a few
 * seconds and puts the previous setting back if the network loses its validated internet. Pure.
 */
object DnsCheck {
    const val WAIT_MS = 15_000L
    const val POLL_MS = 1_500L

    enum class Outcome { OK, WAIT, REVERT, NO_NETWORK, UNCHECKED }

    /**
     * [validatedBefore]: the network had working internet before the change (if not, a failure
     * afterwards proves nothing and nothing is reverted). [serverName] / [validated] are the
     * active network's LinkProperties.privateDnsServerName and NET_CAPABILITY_VALIDATED now.
     */
    fun decide(
        choice: PrivateDns.Choice,
        hasNetwork: Boolean,
        validatedBefore: Boolean,
        serverName: String?,
        validated: Boolean,
        elapsedMs: Long
    ): Outcome {
        if (choice.mode != PrivateDns.MODE_HOSTNAME) return Outcome.UNCHECKED
        if (!hasNetwork) return Outcome.NO_NETWORK
        val sameHost = serverName?.trimEnd('.')?.equals(choice.host, ignoreCase = true) == true
        if (sameHost && validated) return Outcome.OK
        if (elapsedMs < WAIT_MS) return Outcome.WAIT
        return if (validatedBefore) Outcome.REVERT else Outcome.UNCHECKED
    }

    fun message(choice: PrivateDns.Choice, o: Outcome): String = when (o) {
        Outcome.OK -> "${choice.label} is working"
        Outcome.WAIT -> "Checking…"
        Outcome.REVERT -> "Couldn't reach ${choice.label} on this network, so the previous setting is back"
        Outcome.NO_NETWORK -> "Saved. It applies when the TV is online"
        Outcome.UNCHECKED -> "Saved"
    }

    /** The "Check" row: what the network reports now. */
    fun status(current: PrivateDns.Current, hasNetwork: Boolean, active: Boolean, serverName: String?, validated: Boolean): String = when {
        !hasNetwork -> "Not connected"
        current.choice == PrivateDns.Choice.OFF -> if (validated) "Online, Private DNS off" else "No internet"
        serverName != null && validated -> "Encrypted via $serverName"
        serverName != null -> "Can't reach $serverName"
        active && validated -> "Encrypted (network's resolver)"
        validated -> "Online, not encrypted"
        else -> "No internet"
    }
}

/** Cloudflare's own app ("1.1.1.1 + WARP"): a full VPN tunnel we can only open, never control. Pure. */
object Warp {
    /** Verified on Google Play (HTTP 200) on [dev.tvlauncher.discover.PopularApps.VERIFIED_ON]; phone/tablet listing. */
    const val PACKAGE = "com.cloudflare.onedotonedotonedotone"
    const val CATALOG_ID = "cloudflare_warp"
    const val TITLE = "Cloudflare WARP"

    fun value(installed: Boolean): String = if (installed) "Open" else "Get"

    fun subtitle(installed: Boolean, vpnActive: Boolean): String = when {
        vpnActive -> "Full VPN tunnel · a VPN is on now"
        installed -> "Full VPN tunnel. Turn it on inside the app"
        else -> "Full VPN tunnel. A phone app; may be awkward with a remote"
    }
}
