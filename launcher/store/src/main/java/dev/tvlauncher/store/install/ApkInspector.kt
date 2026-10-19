package dev.tvlauncher.store.install

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.drawable.Drawable
import android.os.Build
import dev.tvlauncher.store.core.ApkFacts
import dev.tvlauncher.store.core.FdroidIndexParser
import dev.tvlauncher.store.core.LeanbackDetector
import dev.tvlauncher.store.core.Sha256Stream
import org.xmlpull.v1.XmlPullParser
import java.io.File

/** What the confirmation screen shows about a downloaded APK. */
class Inspected(
    val facts: ApkFacts,
    val label: String,
    val icon: Drawable?,
    val fileSize: Long,
    val sha256: String,
    val hashVerified: Boolean,
)

/**
 * Reads a downloaded APK with PackageManager.getPackageArchiveInfo (permissions + signing certificates),
 * resolves label/icon with sourceDir pointed at the file, scans the binary manifest for a LEANBACK_LAUNCHER
 * activity, and looks up the installed version/signers for update and signature checks.
 */
class ApkInspector(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    @Suppress("DEPRECATION")
    fun inspect(
        file: File,
        sha256: String,
        hashVerified: Boolean,
        expectedPackage: String?,
        expectedSigner: String?,
    ): Inspected {
        val path = file.absolutePath
        var flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS
        flags = if (Build.VERSION.SDK_INT >= 28) {
            flags or PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        } else {
            flags or PackageManager.GET_SIGNATURES
        }
        val info: PackageInfo? = try {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
            else pm.getPackageArchiveInfo(path, flags)
        } catch (_: Exception) {
            null
        }
        val sdk = Build.VERSION.SDK_INT
        if (info == null) {
            val facts = ApkFacts(null, null, 0, 1, emptySet(), emptyList(), null, null, null,
                expectedPackage, expectedSigner, hashVerified, sdk)
            return Inspected(facts, file.name, null, file.length(), sha256, hashVerified)
        }
        val app = info.applicationInfo
        var label = info.packageName
        var icon: Drawable? = null
        if (app != null) {
            app.sourceDir = path
            app.publicSourceDir = path
            try { label = app.loadLabel(pm).toString().trim().ifEmpty { info.packageName } } catch (_: Exception) {}
            try { icon = app.loadIcon(pm) } catch (_: Exception) {}
        }
        val scan = scanManifest(info)
        val featureLeanback = info.reqFeatures?.any { it.name == FdroidIndexParser.LEANBACK } == true
        val tvReady: Boolean? = when {
            scan != null -> scan.tvReady || featureLeanback
            featureLeanback -> true
            else -> null
        }
        val installed = installedInfo(info.packageName)
        val facts = ApkFacts(
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = versionCodeOf(info),
            minSdk = app?.minSdkVersion ?: 1,
            signers = signersOf(info),
            permissions = info.requestedPermissions?.toList() ?: emptyList(),
            tvReady = tvReady,
            installedVersionCode = installed?.let { versionCodeOf(it) },
            installedSigners = installed?.let { signersOf(it) },
            expectedPackage = expectedPackage,
            expectedSigner = expectedSigner,
            sourceHadHash = hashVerified,
            deviceSdk = sdk,
        )
        return Inspected(facts, label.take(80), icon, file.length(), sha256, hashVerified)
    }

    @Suppress("DEPRECATION")
    fun installedInfo(packageName: String): PackageInfo? = try {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else pm.getPackageInfo(packageName, flags)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Exception) {
        null
    }

    /** Manifest scan through the archive's own Resources; null when it can't be read reliably. */
    private fun scanManifest(info: PackageInfo): LeanbackDetector.Result? {
        val app = info.applicationInfo ?: return null
        return try {
            val res = pm.getResourcesForApplication(app)
            res.assets.openXmlResourceParser("AndroidManifest.xml").use { p ->
                val events = sequence {
                    var type = p.eventType
                    while (type != XmlPullParser.END_DOCUMENT) {
                        if (type == XmlPullParser.START_TAG) {
                            val attrs = HashMap<String, String>(4)
                            p.getAttributeValue(ANDROID_NS, "name")?.let { attrs["name"] = it }
                            p.getAttributeValue(null, "package")?.let { attrs["package"] = it }
                            if (p.name == "application" && p.getAttributeResourceValue(ANDROID_NS, "banner", 0) != 0) {
                                attrs["banner"] = "1"
                            }
                            yield(LeanbackDetector.Event.Start(p.name, attrs))
                        } else if (type == XmlPullParser.END_TAG) {
                            yield(LeanbackDetector.Event.End(p.name))
                        }
                        type = p.next()
                    }
                }
                val r = LeanbackDetector.scan(events)
                // Guard against having read some other manifest (e.g. the framework's).
                if (r.packageName == info.packageName) r else null
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        @Suppress("DEPRECATION")
        fun versionCodeOf(info: PackageInfo): Long =
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

        @Suppress("DEPRECATION")
        fun signersOf(info: PackageInfo): Set<String> {
            val sigs = ArrayList<Signature>()
            if (Build.VERSION.SDK_INT >= 28) {
                val si = info.signingInfo
                if (si != null) {
                    if (si.hasMultipleSigners()) si.apkContentsSigners?.let { sigs += it }
                    else si.signingCertificateHistory?.let { sigs += it }
                }
            }
            if (sigs.isEmpty()) info.signatures?.let { sigs += it }
            return sigs.map { Sha256Stream.of(it.toByteArray()) }.toSet()
        }
    }
}
