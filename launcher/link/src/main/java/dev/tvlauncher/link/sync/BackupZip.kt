package dev.tvlauncher.link.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The backup archive: a zip holding exactly one file, the launcher's tvlauncher-config.json
 * (the same document Settings > Backup & restore writes), then encrypted ([BackupCrypto]).
 * Reading is strict: one entry with that exact name, at most [MAX_JSON] bytes unpacked.
 */
object BackupZip {
    const val ENTRY = "tvlauncher-config.json"
    const val FILE_NAME = "h02-backup.h02"
    const val MAX_JSON = 512 * 1024
    /** Largest encrypted file accepted from the phone or a server. */
    const val MAX_FILE = 1024 * 1024

    fun pack(json: String, timeMs: Long = 0L): ByteArray {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_JSON) { "config too large" }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            val e = ZipEntry(ENTRY)
            e.time = if (timeMs > 0) timeMs else 315_532_800_000L // 1980-01-01: reproducible archives
            z.putNextEntry(e)
            z.write(bytes)
            z.closeEntry()
        }
        return out.toByteArray()
    }

    /** The JSON text, or null when the archive is not exactly one config entry within the limits. */
    fun unpack(zip: ByteArray): String? = try {
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            val e = z.nextEntry ?: return null
            if (e.name != ENTRY || e.isDirectory) return null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = z.read(buf)
                if (n < 0) break
                if (out.size() + n > MAX_JSON) return null
                out.write(buf, 0, n)
            }
            if (z.nextEntry != null) return null
            String(out.toByteArray(), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    fun seal(json: String, passphrase: String): ByteArray = BackupCrypto.encrypt(pack(json), passphrase)

    /** Throws [BackupCrypto.BadPassphrase] / [BackupCrypto.BadFile]; null when the inside is not a config. */
    fun open(file: ByteArray, passphrase: String): String? {
        if (file.size > MAX_FILE) throw BackupCrypto.BadFile("too large")
        return unpack(BackupCrypto.decrypt(file, passphrase))
    }
}
