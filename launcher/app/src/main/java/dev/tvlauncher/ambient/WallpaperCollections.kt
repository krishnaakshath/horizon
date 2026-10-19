package dev.tvlauncher.ambient

import java.io.File

/**
 * The built-in wallpaper catalog, grouped into collections (pure; the art itself is drawn by
 * [BuiltInWallpapers]). Every built-in is original, procedurally drawn art: no characters, names
 * or logos of any series. The "Anime" collection is named by mood only.
 */
object WallpaperCatalog {
    data class Meta(val id: String, val title: String, val baseColor: Int, val collection: String)

    const val CLASSIC = "Classic"
    const val ANIME = "Anime"

    val ALL = listOf(
        Meta("aurora", "Aurora", 0xFF0A1020.toInt(), CLASSIC),
        Meta("dusk", "Dusk", 0xFF1B1440.toInt(), CLASSIC),
        Meta("ocean", "Ocean", 0xFF021A2B.toInt(), CLASSIC),
        Meta("graphite", "Graphite", 0xFF121418.toInt(), CLASSIC),
        Meta("nebula", "Nebula", 0xFF05060D.toInt(), CLASSIC),
        Meta("mono", "Mono", 0xFF0B0D12.toInt(), CLASSIC),
        Meta("golden_aura", "Golden Aura", 0xFF07080F.toInt(), ANIME),
        Meta("cursed_night", "Cursed Night", 0xFF07040E.toInt(), ANIME),
        Meta("beyond_walls", "Beyond the Walls", 0xFF1A1020.toInt(), ANIME),
        Meta("leaf_dusk", "Leaf Village Dusk", 0xFF2A1410.toInt(), ANIME),
        Meta("petal_night", "Petal Night", 0xFF070B1C.toInt(), ANIME),
        Meta("neon_rain", "Neon Rain", 0xFF05060C.toInt(), ANIME),
        Meta("moonlit_sea", "Moonlit Sea", 0xFF040A18.toInt(), ANIME)
    )

    val IDS: List<String> = ALL.map { it.id }
    val COLLECTIONS: List<String> = ALL.map { it.collection }.distinct()

    fun meta(id: String): Meta = ALL.firstOrNull { it.id == id } ?: ALL[0]

    fun inCollection(name: String): List<Meta> = ALL.filter { it.collection == name }
}

/** A folder of the owner's pictures shown as one collection in the picker. */
data class UserCollection(val name: String, val folder: File, val files: List<File>)

/**
 * Owner collections: one folder per collection, the folder name is the collection name.
 *
 * Where (see [UserWallpapers.roots]):
 *  - on the TV: Pictures/TVLauncher/<Collection>/ (needs the "Photos and media" permission),
 *  - on a USB stick: TVLauncher/<Collection>/ or Pictures/TVLauncher/<Collection>/ (same permission),
 *  - the launcher's own folder Android/data/dev.tvlauncher/files/wallpapers/<Collection>/ (no permission;
 *    loose pictures there are "My pictures").
 * Pure apart from listing directories; call off the main thread.
 */
object CollectionScanner {
    const val MAX_COLLECTIONS = 24
    const val MY_PICTURES = "My pictures"

    /** A scan root: [dir] holds collection folders; [looseName] names its loose pictures (null: ignored); [suffix] marks USB. */
    data class Root(val dir: File, val looseName: String? = null, val suffix: String = "")

    fun scan(roots: List<Root>, list: (File) -> List<File> = UserWallpapers::list): List<UserCollection> {
        val out = ArrayList<UserCollection>()
        val seen = HashSet<String>()
        for (root in roots) {
            val dir = root.dir
            if (root.looseName != null) {
                val loose = list(dir)
                if (loose.isNotEmpty()) out += UserCollection(unique(root.looseName + root.suffix, seen), dir, loose)
            }
            val subs = try { dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } } catch (e: SecurityException) { null }
                ?: continue
            for (sub in subs.sortedBy { it.name.lowercase() }) {
                val files = list(sub)
                if (files.isEmpty()) continue
                out += UserCollection(unique(displayName(sub.name) + root.suffix, seen), sub, files)
                if (out.size >= MAX_COLLECTIONS) return out
            }
        }
        return out
    }

    /** "dragon_ball-z" -> "dragon ball z"; trimmed, at most 40 chars, never blank. */
    fun displayName(folder: String): String =
        folder.replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ").trim().take(40).ifBlank { "Pictures" }

    private fun unique(name: String, seen: MutableSet<String>): String {
        var n = name
        var i = 2
        while (!seen.add(n.lowercase())) n = "$name ($i)".also { i++ }
        return n
    }
}
