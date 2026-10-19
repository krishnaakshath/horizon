package dev.tvlauncher.type

/**
 * Text for Settings -> About -> Third-party notices. Full licenses: res/raw/font_license_ofl.txt (Inter)
 * and res/raw/brand_font_license_ofl.txt (Unbounded).
 */
object FontNotices {
    const val THIRD_PARTY: String =
        "Inter typeface, Copyright (c) 2016 The Inter Project Authors " +
            "(https://github.com/rsms/inter). Licensed under the SIL Open Font License, " +
            "Version 1.1 (https://openfontlicense.org). Bundled as static, Latin-subset instances " +
            "(Inter Display Light, Inter Regular, Medium, SemiBold, Bold)."

    /** The brand wordmark face (dev.tvlauncher.brand.BrandFont). */
    const val BRAND: String =
        "Unbounded typeface, Copyright 2022 The Unbounded Project Authors " +
            "(https://github.com/googlefonts/unbounded). Licensed under the SIL Open Font License, " +
            "Version 1.1 (https://openfontlicense.org). Bundled as a static Medium instance subset to " +
            "Latin letters, digits and basic punctuation, for the wordmark only."
}
