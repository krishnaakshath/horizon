package dev.tvlauncher.appsettings

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.brand.Brand
import dev.tvlauncher.settings.Branding

/** Version, licenses, this TV's profile and the non-affiliation notice. */
object AboutSection : SettingsSection, SpecifiedPage {
    const val ID = "about"
    override val id = ID
    override val title = "About & licenses"
    override val iconRes = R.drawable.ic_set_about
    override val group = SettingsGroup.SYSTEM
    override val description = Branding.NAME
    override val spec = PageSpec(ID, listOf("about.version", "about.license", "about.notices", "about.tv", dev.tvlauncher.link.UpdateRow.ID))

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        page.row("about.version", Brand.NAME, RowKind.Info(version(context)),
            subtitle = Brand.TAGLINE, iconRes = R.drawable.ic_set_about)
        page.row("about.license", "License", RowKind.Action(Branding.LICENSE) {
            nav.push("License") { c, n -> textPage(c, n, R.raw.license_mit) }
        }, iconRes = R.drawable.ic_set_license)
        page.row("about.notices", "Third-party notices", RowKind.Action {
            nav.push("Third-party notices") { c, n -> textPage(c, n, R.raw.third_party_notices) }
        }, subtitle = "AndroidX and Kotlin (Apache-2.0), Inter and Unbounded fonts (OFL)", iconRes = R.drawable.ic_set_license)
        page.row("about.tv", "This TV", RowKind.Action(AboutText.ram(nav.profile.totalRamKb)) {
            nav.push("This TV") { c, n -> thisTv(c, n) }
        }, subtitle = "Memory, processor, effects and resolution", iconRes = R.drawable.ic_sc_device)
        dev.tvlauncher.link.UpdateRow.add(page, context, nav) // only with M02 Link installed
        page.note(Brand.STORY)
        page.note("© ${Branding.COPYRIGHT_YEAR} ${Branding.COPYRIGHT_HOLDER}")
        page.note(Branding.DISCLAIMER)
        return page
    }

    private fun thisTv(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val profile = nav.profile
        page.row("about.ram", "Memory", RowKind.Info(AboutText.ram(profile.totalRamKb)), iconRes = R.drawable.ic_sc_device)
        page.row("about.abi", "Processor", RowKind.Info(profile.abi), iconRes = R.drawable.ic_sc_developer)
        page.row("about.effects", "Effects", RowKind.Info(AboutText.effects(profile.effectsMode)),
            subtitle = "Animations now: ${AboutText.motion(nav.style.motion)}", iconRes = R.drawable.ic_set_motion)
        page.row("about.resolution", "Resolution", RowKind.Info("${profile.widthPx} × ${profile.heightPx}"),
            iconRes = R.drawable.ic_sc_display)
        return page
    }

    internal fun textPage(context: Context, nav: SettingsNav, raw: Int): View {
        val page = TextPage(context, nav.style)
        nav.background({
            try {
                context.resources.openRawResource(raw).bufferedReader().use { it.readText() }
                    .replace(Branding.HOLDER_TOKEN, Branding.COPYRIGHT_HOLDER)
            } catch (e: Exception) {
                "Could not read this text."
            }
        }) { text -> page.setText(text) }
        return page
    }

    private fun version(context: Context): String = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        "${info.versionName} (${info.versionCode})"
    } catch (e: PackageManager.NameNotFoundException) {
        "Unknown"
    }
}
