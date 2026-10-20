# Horizon

A fast, minimal, glassy home screen for Android TV. Built and tested on a 1 GB TCL TV, so it stays smooth on weak
hardware and looks better on strong hardware.

On screen it is called **H02**; Horizon is the project name.

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Platform: Android TV](https://img.shields.io/badge/platform-Android%20TV-3ddc84.svg)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-Android%20Views-7f52ff.svg)](#tech-stack)
[![Version](https://img.shields.io/badge/version-1.0.1-4c8dff.svg)](CHANGELOG.md)
[![Release](https://img.shields.io/github/v/release/krishnaakshath/horizon)](../../releases/latest)
[![CI](https://github.com/krishnaakshath/horizon/actions/workflows/ci.yml/badge.svg)](https://github.com/krishnaakshath/horizon/actions/workflows/ci.yml)

| | | |
|---|---|---|
| ![Start-up animation with the H02 mark](docs/screenshots/01-startup.png) | ![Home screen with the app row, clock and weather](docs/screenshots/02-home.png) | ![Spotlight search over the home screen](docs/screenshots/03-spotlight.png) |
| Start-up | Home | Spotlight |
| ![Inputs panel](docs/screenshots/04-inputs-panel.png) | ![Settings page for TV and system](docs/screenshots/05-settings-tv-and-system.png) | ![Settings sidebar](docs/screenshots/06-settings-sidebar.png) |
| Inputs panel | TV and system settings | Settings |

## Features

**Home**
- One row of apps with each app's brand colour as the backdrop, a clock, weather and quick controls.
- Layouts: single row, two rows, grid, big hero and minimal.
- Continue watching: focus an app to see what you were watching (needs the TV's Watch Next list).
- Home composer: presets and your own groups of apps (Favorites, Recent apps, Apps and custom groups), in your order.

**Look**
- Glass styles: frosted, clear, solid and a liquid-glass-inspired lens, with an adjustable glass amount.
- Ready-made looks plus your own: corners, tint, accent, text size and tile icons, which can be saved to and loaded from look files.
- Text stays readable on any wallpaper; a bright-room palette, text sizes and reduced motion are available.
- Choice of start-up animations and app-launch transitions.

**Spotlight**
- Search apps, settings, recent items and the web, plus quick actions you can reorder.

**Settings that replace the TV's**
- Wi-Fi, Bluetooth, devices, inputs, sound, accessibility, controllers, profiles, speed, private DNS, sleep timer and
  focus mode, all inside the launcher.
- Time routines (for example change look or open an app at a set time).
- Cast and AirPlay page that shows what the TV already offers and how to use it.

**Screensaver and Now Playing**
- Screensaver with real time and weather, original wallpapers and your own picture folders; widgets for calendar,
  photo frame and album art.
- Now Playing: the cover art becomes the backdrop and a mini player stays on Home. Backdrop styles: cover, blurred
  cover, colour wash and artist banner.
- The screensaver follows the music: while a song plays it shows the cover, title, artist and progress.
- Optional Spotify artist pictures through H02 Link, with your own free Spotify app key (it stays on the TV).
- Room lighting that follows sunrise and sunset.

**Phone remote**
- Pair a phone by QR code and use it as a trackpad, keyboard and media remote from a web page (a PWA, nothing to
  install from a store). It also sends links to H02 Browser, runs smart-home favourites and saves an encrypted backup.

**Controllers**
- Game controllers and the TV remote work everywhere in the launcher.

**Setup and provisioning**
- First-run setup flow (language, network, time and place, apps, look, permissions) that can restore a saved setup
  from a USB stick.
- A provisioning tool that prepares a TV over adb with a dry run, an apply step and an undo script.

## Tech stack

| Area | Details |
|---|---|
| Language | Kotlin |
| UI | Plain Android Views (no Compose) |
| Build | Android Gradle Plugin 9.4.1, Gradle 9.8.1 (wrapper included), Kotlin built in via AGP |
| JDK | 17 (source and target compatibility 17) |
| SDK levels | All modules: minSdk 24, compileSdk 37. targetSdk 28 for `app` and `link`; 30 for `helper`, `browser`, `store`, `weather` |
| Runtime libraries | None in the launcher beyond AndroidX RecyclerView; no analytics, no ad or crash SDKs |
| Fonts | Inter (text) and Unbounded (mark), both SIL Open Font License |
| Weather | [Open-Meteo](https://open-meteo.com/) (no API key), fetched by the Weather companion only |
| Tests | About 2,380 JVM unit tests across the modules |
| Tooling | Python 3 (`tools/tvtool` provisioning, `tools/audit` layout audit), pytest |
| Architecture | Multi-module Gradle project, one APK per module, signature-protected IPC between them |

## Architecture

The project is six Android modules under `launcher/`.

| Module | Package | Role |
|---|---|---|
| `app` | `dev.tvlauncher` | The launcher: Home, Spotlight, Settings, screensaver, setup, safe mode |
| `helper` | `dev.tvlauncher.helper` | Optional accessibility service: double-Back, shortcut key |
| `browser` | `dev.tvlauncher.browser` | Web browser with a D-pad pointer |
| `store` | `dev.tvlauncher.store` | Install apps from links, F-Droid and GitHub |
| `weather` | `dev.tvlauncher.weather` | Weather, sunrise and sunset |
| `link` | `dev.tvlauncher.link` | Phone remote server and web page |

- **How they talk.** Modules never share code at run time. They exchange data through broadcasts and content
  providers protected by signature-level permissions (for example `WEATHER_REFRESH`, `SPOTLIGHT_SYNC`, `LINK`,
  `HELPER_ACTIONS`). Only apps signed with the same key can use them, which is why all APKs of a release must be
  signed with one key.
- **Launcher has no network.** The `app` module declares no INTERNET permission. Anything that needs the network
  lives in a companion app and hands results back over those protected channels.
- **Safe mode.** If the launcher crashes repeatedly, a crash guard switches to a small, separate safe-mode screen that
  lists apps and links to the TV's own settings, so the TV is never left without a way to start apps.
- **LITE and FULL.** The launcher picks a rendering tier for the device. FULL uses live blur, the glass lens and
  staggered animations; LITE uses cheaper fades and flat glass so 1 GB devices stay smooth.

More detail: [docs/](docs/README.md).

## Requirements

- Android TV, Google TV or Fire TV device running Android 7.0 or newer (minSdk 24).
- Tested on Android 11, 1 GB RAM, 32-bit ARM (TCL 32S5403AF).
- For adb install or provisioning: a computer on the same network with Android platform tools (`adb`), and
  network debugging switched on in the TV's Developer options.

## Install

### A. Quick: install the APKs

1. Download the APKs from the [latest release](../../releases/latest). Release files are named
   `H02-<module>-<version>.apk`.
2. Connect and install. The launcher (`app`) is required; the companions are optional.

```
adb connect <tv-ip>:5555
adb install -r H02-app-1.0.1.apk
adb install -r H02-browser-1.0.1.apk   # optional
adb install -r H02-store-1.0.1.apk     # optional
adb install -r H02-weather-1.0.1.apk   # optional
adb install -r H02-helper-1.0.1.apk    # optional
adb install -r H02-link-1.0.1.apk      # optional
```

Check the files against `SHA256SUMS.txt` from the release. Version 1.0.1 installs over 1.0.0 and keeps your settings:
package names did not change (the APKs must be signed with the same key).

### B. Provisioning tool (recommended for a full setup)

`tvtool provision` installs the APKs, grants permissions, turns on the Helper and can make Horizon the Home app. It is
a dry run unless you add `--apply`, and it writes an undo script before changing anything.

```
cd tools
python3 -m tvtool provision --host <tv-ip> --apks <folder-with-apks>           # dry run
python3 -m tvtool provision --host <tv-ip> --apks <folder-with-apks> --apply   # make the changes
python3 -m tvtool rollback  --host <tv-ip> --script scans-out/<undo-script>.sh # undo
```

TVs that hard-wire their own launcher to the Home key (TCL does) need `--take-over-home`, which disables the stock
launcher (it is never uninstalled). See [docs/PROVISIONING.md](docs/PROVISIONING.md).

### C. From source

JDK 17 and the Android SDK are required. The Gradle wrapper is included.

```
git clone https://github.com/krishnaakshath/horizon.git
cd horizon/launcher
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew testDebugUnitTest assembleDebug
```

Debug APKs appear in each module's `build/outputs/apk/debug/`. Release builds need your own keystore, and all modules
must be signed with the same key. The version of every module is set once, in `launcher/gradle.properties`. Gradle
names the outputs `<module>-release.apk`; they are published as `H02-<module>-<version>.apk`.

## First run

1. Press Home. If Horizon is not yet the default Home app, choose it when Android asks, or use provisioning.
2. The setup flow walks through language, network, time and place, apps, look and feel, and permissions, all with the
   remote. It can also restore a setup saved on a USB stick.
3. Settings pages that need a one-time permission say so and explain how to grant it.
4. If anything goes wrong, a repeated crash opens safe mode, and provisioning's undo script restores the previous state.

## Brand

| | |
|---|---|
| Name | **Horizon** |
| On-screen mark | **H02**; companions are H02 Browser, H02 Store, H02 Weather, H02 Helper and H02 Link |
| Splash | "H02" with a letter-spaced "HORIZON" caption over a horizon line and a rising sun |
| Mark face | Unbounded Medium (SIL OFL 1.1), a small subset bundled for the splash only |
| Icons and banners | Vector paths drawn from the same letters by `launcher/fonts/brand_art.py` and `companion_art.py` |
| Source of truth | `launcher/app/src/main/java/dev/tvlauncher/brand/Brand.kt`; `BrandStringsTest` keeps every app name in step |

Package names (`dev.tvlauncher.*`) are internal ids and do not follow the brand, so updates keep each TV's settings.

## Companion apps

| App | Package | Needs internet | Permissions (summary) |
|---|---|---|---|
| H02 (launcher) | `dev.tvlauncher` | No | Wi-Fi/Bluetooth state and control, location (for scans), calendar (widget), notification access (optional), overlay, boot, package removal requests |
| H02 Helper | `dev.tvlauncher.helper` | No | Accessibility service (foreground app name and key presses only; does not read screen content) |
| H02 Browser | `dev.tvlauncher.browser` | Yes | Internet, network state |
| H02 Store | `dev.tvlauncher.store` | Yes | Internet, network state, install and remove packages (system confirmation each time) |
| H02 Weather | `dev.tvlauncher.weather` | Yes | Internet, network state, boot (scheduled refresh) |
| H02 Link | `dev.tvlauncher.link` | Local network only | Internet (local server), network state, boot, foreground service |

## Privacy

- The launcher has no INTERNET permission. It cannot send anything off the device.
- No analytics, advertising or crash-reporting libraries are included in any module.
- Browser fetches the pages you open and search suggestions you ask for.
- Store fetches app listings and downloads from the sources you choose (links, F-Droid, GitHub).
- Weather sends your configured coordinates to Open-Meteo to get a forecast.
- Link serves a page on your home network, protected by a pairing code, and talks to the launcher over a protected
  local channel. Smart-home and update features that use the network are part of Link only.

## Compatibility and known limits

These mostly come from the TV's firmware, not from Horizon.

- The firmware keeps the volume keys and its volume pop-up, so apps cannot replace them.
- Some TVs stop background accessibility services. The Helper's extras (double-Back, controller Guide button from
  other apps) may pause on those TVs. Everything inside Horizon is unaffected.
- Picture and sound processing menus, system updates and factory reset belong to the TV and stay there.
- Apps only see what other apps publish. Continue watching and the cover-art backdrop depend on that.
- The phone remote works fully on Home and in Settings. In other apps, Home, volume, media keys and app launching
  work; Back needs the Helper, and the D-pad, OK and typing do not reach other apps.
- The Helper may be paused by the TV's memory manager; provisioning reports it, and launcher features keep working.
- Home sections draws Apps, Favorites, Recent apps and your groups. Other section types (Continue watching, Now playing,
  Weather, Smart home, Quick actions, Clock) are not drawn yet and cannot be switched on.
- Spotify artist pictures need H02 Link, your own Spotify app key and a network connection; otherwise the cover is used.
- AirPlay depends on the TV: the launcher does not receive AirPlay itself (see [docs/AIRPLAY.md](docs/AIRPLAY.md)).
- A fresh install of the launcher can be held by Play Protect on some TVs (it targets Android 9 for the Wi-Fi
  controls); choose Install anyway. See [docs/PROVISIONING.md](docs/PROVISIONING.md).
- Enterprise (802.1X) Wi-Fi is not supported; networks saved by the TV cannot always be forgotten from Horizon.
- Real-device testing so far is one TV model. Other devices should work but are less proven; please report problems.

See [docs/SYSTEM_SETTINGS.md](docs/SYSTEM_SETTINGS.md) and [docs/OS_PARITY.md](docs/OS_PARITY.md).

## Roadmap

- More device profiles for provisioning beyond the generic and TCL ones.
- More testing on real TVs and on higher-end hardware.
- Drawing the remaining Home section types (Continue watching, Now playing, Weather, Smart home).

No dates are promised. See the [changelog](CHANGELOG.md) for what has shipped.

## Contributing

Bug reports and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first and follow the
[Code of Conduct](CODE_OF_CONDUCT.md). Security issues: see [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE). Third-party notices are in the app (Settings, About & licenses) and in `launcher/app/src/main/res/raw/`.
Fonts are under the SIL Open Font License; sources and hashes are in [launcher/fonts/FONTS.md](launcher/fonts/FONTS.md).

## Not affiliated

Horizon is an independent project. It is not made by, endorsed by or connected to TCL, Google, Apple, Netflix, Spotify
or any other company or service named in the code or screenshots. All names and logos belong to their owners.
The name Horizon is used by other products (for example Meta Horizon and Liberty Global's Horizon TV platform); no
trademark is claimed to "Horizon" or "H02". Check before commercial use.
