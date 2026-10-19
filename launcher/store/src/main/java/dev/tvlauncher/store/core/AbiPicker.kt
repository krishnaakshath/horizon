package dev.tvlauncher.store.core

/**
 * Chooses which APK of a release suits this device, from the file name alone. Builds for an ABI the device
 * does not support and unsigned builds are dropped; the device's primary ABI ranks first, then its other ABIs,
 * then universal builds, then builds without any ABI marker. Debug builds sink below release builds.
 */
object AbiPicker {

    const val UNIVERSAL = "universal"

    private val tokens: List<Pair<Regex, String>> = listOf(
        Regex("(^|[^a-z0-9])(arm64[-_]?v8a|arm64|aarch64|armv8)([^a-z0-9]|$)") to "arm64-v8a",
        Regex("(^|[^a-z0-9])(armeabi[-_]?v7a|armv7a?|arm32|armeabi|arm)([^a-z0-9]|$)") to "armeabi-v7a",
        Regex("(^|[^a-z0-9])(x86[-_]64|x64|amd64)([^a-z0-9]|$)") to "x86_64",
        Regex("(^|[^a-z0-9])(x86|i686|i386)([^a-z0-9]|$)") to "x86",
        Regex("(^|[^a-z0-9])(universal|all|fat|noarch)([^a-z0-9]|$)") to UNIVERSAL,
    )

    /** The ABI named in [fileName], [UNIVERSAL], or null when the name carries no ABI hint. */
    fun abiOf(fileName: String): String? {
        val n = fileName.lowercase()
        for ((re, abi) in tokens) if (re.containsMatchIn(n)) return abi
        return null
    }

    fun isApkName(name: String): Boolean = name.lowercase().endsWith(".apk")

    /** Compatible candidates, best first. [supportedAbis] is Build.SUPPORTED_ABIS order (primary first). */
    fun <T> rank(items: List<T>, supportedAbis: List<String>, nameOf: (T) -> String): List<T> {
        val supported = supportedAbis.map { normalise(it) }
        return items.mapNotNull { item ->
            val name = nameOf(item)
            if (!isApkName(name)) return@mapNotNull null
            val lower = name.lowercase()
            if (lower.contains("unsigned")) return@mapNotNull null
            val abi = abiOf(name)
            var score = when (abi) {
                null -> 400
                UNIVERSAL -> 500
                else -> {
                    val idx = supported.indexOf(abi)
                    if (idx < 0) return@mapNotNull null
                    1000 - idx * 100
                }
            }
            if (Regex("(^|[^a-z0-9])debug([^a-z0-9]|$)").containsMatchIn(lower)) score -= 350
            item to score
        }.sortedByDescending { it.second }.map { it.first }
    }

    private fun normalise(abi: String): String = when (abi.lowercase()) {
        "armeabi" -> "armeabi-v7a-legacy"
        else -> abi.lowercase()
    }
}
