# Horizon

Horizon is a lean Android TV launcher (`launcher/`), shipped with an ADB debloat and provisioning tool (`tools/`).

Work in progress. Licensed under MIT.

Build the launcher: `cd launcher && ./gradlew test assembleDebug` (JDK 17+; the Android Studio bundled JDK works).

## Brand

- **Product name:** Horizon. It appears on the About page, in the license notices and in these docs.
- **On-screen mark:** `H02`. The TV never shows the full name; app labels, the start-up screen, setup and Safe Mode
  use the short mark. The companion apps are `H02 Browser`, `H02 Store`, `H02 Weather` and `H02 Helper`.
- **Motif:** a sun-disc on a horizon line. At start-up the line draws across, the sun rises on it, `H02` resolves above it
  with a soft glow and a letter-spaced `HORIZON` caption fades in below it, then the home row rises. FULL mode takes at
  most 1.1 s and LITE mode at most 0.7 s; with system animations off there is no splash. Every loading indicator
  (shelf, panels, app launch, browser) is the same sun-disc gliding along a thin line.
- **Wordmark face:** Unbounded Medium (SIL OFL 1.1), subset to about 16 KB and used for the mark only. All other text
  uses Inter. The launcher icon and the 320×180 banner draw the letters as vector paths, so they need no font.
- **Single source of truth:** `launcher/app/src/main/java/dev/tvlauncher/brand/Brand.kt`. Each module's `app_name`
  string mirrors it, and `BrandStringsTest` keeps them equal. Package names (`dev.tvlauncher.*`) are internal IDs and stay
  the same, so installed TVs keep their settings.
- Font sources, hashes and rebuild scripts: `launcher/fonts/FONTS.md`.
