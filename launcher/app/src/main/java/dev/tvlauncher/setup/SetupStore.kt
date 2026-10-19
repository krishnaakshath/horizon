package dev.tvlauncher.setup

import android.content.Context

/** SharedPreferences "setup". Never throws: any failure reads as a fresh TV. */
class SetupStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private fun ids(key: String): Set<StepId> = try {
        sp.getStringSet(key, emptySet()).orEmpty().mapNotNull { n -> StepId.values().firstOrNull { it.name == n } }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    private fun addId(key: String, id: StepId) = edit { putStringSet(key, ids(key).map { it.name }.toSet() + id.name) }

    private fun removeId(key: String, id: StepId) = edit { putStringSet(key, (ids(key) - id).map { it.name }.toSet()) }

    private inline fun edit(f: android.content.SharedPreferences.Editor.() -> Unit) {
        try { sp.edit().apply(f).apply() } catch (e: Exception) { }
    }

    fun memory() = SetupMemory(skipped = ids(SKIPPED), autoTried = ids(AUTO_TRIED), visited = ids(VISITED))

    fun skip(id: StepId) = addId(SKIPPED, id)
    fun unskip(id: StepId) = removeId(SKIPPED, id)
    fun markAutoTried(id: StepId) = addId(AUTO_TRIED, id)
    fun markVisited(id: StepId) = addId(VISITED, id)

    val completed: Boolean get() = try { sp.getBoolean(COMPLETED, false) } catch (e: Exception) { false }
    fun markCompleted() = edit { putBoolean(COMPLETED, true) }

    val autoStarts: Int get() = try { sp.getInt(AUTO_STARTS, 0) } catch (e: Exception) { 0 }
    /** commit(): counted even if the setup screen then takes the process down. */
    fun countAutoStart() { try { sp.edit().putInt(AUTO_STARTS, autoStarts + 1).commit() } catch (e: Exception) { } }

    val nowPlayingAutoDone: Boolean get() = try { sp.getBoolean(NP_DONE, false) } catch (e: Exception) { false }
    fun markNowPlayingAutoDone() = edit { putBoolean(NP_DONE, true) }

    fun asked(permission: String): Boolean = try { sp.getBoolean("asked:$permission", false) } catch (e: Exception) { false }
    fun markAsked(permission: String) = edit { putBoolean("asked:$permission", true) }

    private companion object {
        const val NAME = "setup"
        const val SKIPPED = "skipped"
        const val AUTO_TRIED = "auto_tried"
        const val VISITED = "visited"
        const val COMPLETED = "completed"
        const val AUTO_STARTS = "auto_starts"
        const val NP_DONE = "now_playing_auto"
    }
}
