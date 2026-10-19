package dev.tvlauncher.store.core

/**
 * Scans manifest XML events for TV signals: an activity / activity-alias whose intent-filter has the
 * LEANBACK_LAUNCHER category, the android.software.leanback uses-feature, and an application banner.
 * The event source on device is the APK's binary manifest via XmlResourceParser; attribute keys are local
 * names without the android: prefix.
 */
object LeanbackDetector {

    sealed class Event {
        data class Start(val name: String, val attrs: Map<String, String>) : Event()
        data class End(val name: String) : Event()
    }

    data class Result(
        val packageName: String?,
        val leanbackLauncher: Boolean,
        val leanbackFeature: Boolean,
        val banner: Boolean,
        /** Declares an activity with the phone LAUNCHER category. */
        val launcher: Boolean = false,
    ) {
        /** Shows up in a launcher (phone or TV), so our `<queries>` would see it once installed. */
        val launchable: Boolean get() = leanbackLauncher || launcher

        val tvReady: Boolean get() = leanbackLauncher || leanbackFeature
    }

    const val LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"
    const val LAUNCHER = "android.intent.category.LAUNCHER"

    fun scan(events: Sequence<Event>): Result {
        var pkg: String? = null
        var launcher = false
        var phoneLauncher = false
        var feature = false
        var banner = false
        val stack = ArrayList<String>()
        for (e in events) {
            when (e) {
                is Event.Start -> {
                    when (e.name) {
                        "manifest" -> if (stack.isEmpty()) pkg = e.attrs["package"]
                        "uses-feature" -> if (e.attrs["name"] == FdroidIndexParser.LEANBACK) feature = true
                        "application" -> if (!e.attrs["banner"].isNullOrEmpty()) banner = true
                        "category" -> {
                            val inFilter = stack.lastOrNull() == "intent-filter"
                            val owner = stack.getOrNull(stack.size - 2)
                            if (inFilter && (owner == "activity" || owner == "activity-alias")) {
                                if (e.attrs["name"] == LEANBACK_LAUNCHER) launcher = true
                                if (e.attrs["name"] == LAUNCHER) phoneLauncher = true
                            }
                        }
                    }
                    stack += e.name
                }
                is Event.End -> {
                    val idx = stack.lastIndexOf(e.name)
                    if (idx >= 0) while (stack.size > idx) stack.removeAt(stack.size - 1)
                }
            }
        }
        return Result(pkg, launcher, feature, banner, phoneLauncher)
    }
}
