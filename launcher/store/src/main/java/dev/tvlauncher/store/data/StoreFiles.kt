package dev.tvlauncher.store.data

import android.content.Context
import dev.tvlauncher.store.core.InstalledRecord
import dev.tvlauncher.store.core.InstalledRegistry
import dev.tvlauncher.store.core.LinkHistory
import dev.tvlauncher.store.core.RepoList
import java.io.File

/** Small JSON files in the app-private filesDir. Writes are atomic (temp file + rename). */
class StoreFiles(context: Context) {

    private val dir: File = context.filesDir

    private fun read(name: String): String? = try {
        File(dir, name).takeIf { it.isFile && it.length() < 4 * 1024 * 1024 }?.readText()
    } catch (_: Exception) {
        null
    }

    fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(File(dir, name))) {
            File(dir, name).writeText(text)
            tmp.delete()
        }
    }

    fun file(name: String): File = File(dir, name)

    var installed: List<InstalledRecord>
        get() = InstalledRegistry.decode(read(INSTALLED))
        set(v) = write(INSTALLED, InstalledRegistry.encode(v))

    var linkHistory: List<String>
        get() = LinkHistory.decode(read(HISTORY))
        set(v) = write(HISTORY, LinkHistory.encode(v))

    var repos: List<String>
        get() = RepoList.decode(read(REPOS))
        set(v) = write(REPOS, RepoList.encode(v))

    fun readCatalogJson(): String? = read(FdroidRepository.CATALOG_FILE)

    companion object {
        const val INSTALLED = "installed.json"
        const val HISTORY = "link-history.json"
        const val REPOS = "github-repos.json"
    }
}
