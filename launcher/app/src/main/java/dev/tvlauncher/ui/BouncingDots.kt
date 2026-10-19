package dev.tvlauncher.ui

/** The old three-dot loader; every loader is now the brand [HorizonLoader] (kept so older call sites still build). */
@Deprecated("Use HorizonLoader", ReplaceWith("HorizonLoader"))
typealias BouncingDots = HorizonLoader
