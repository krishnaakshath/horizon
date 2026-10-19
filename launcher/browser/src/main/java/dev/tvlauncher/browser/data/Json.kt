package dev.tvlauncher.browser.data

import java.io.File

/** Small, safe file helpers for the JSON stores (atomic writes, bounded reads). */
internal object JsonFiles {
    const val MAX_FILE = 512 * 1024

    /** File text, or null when missing; "" when unreadable/too big (treated as corrupt). */
    fun read(file: File): String? = try {
        if (!file.exists()) null else if (file.length() > MAX_FILE) "" else file.readText()
    } catch (e: Exception) {
        ""
    }

    fun writeAtomic(file: File, text: String) = AtomicText.write(FileOps.Real, file, text)
}
