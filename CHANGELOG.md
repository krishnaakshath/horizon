# Changelog

All notable changes are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

Nothing yet.

## [1.0.1]

The product is now called **Mintaka** (named after a star in Orion's belt), with the on-screen mark **M02**.

### Changed
- New name: Mintaka, short mark M02. App names are M02, M02 Browser, M02 Store, M02 Weather, M02 Helper and M02 Link;
  the screensaver and notification-access entries are M02 Ambient and M02 ambient notifications.
- The splash, icon and banners show "M02" with a letter-spaced "MINTAKA" caption. Weather, Helper and Link get TV
  banners in the same look as Store and Browser.
- Setup says "Welcome to M02" and "M02 is ready"; safe mode is "M02 safe mode"; the About page names Mintaka and
  where the name comes from. The default look preset is called Mintaka.
- The phone page is "M02 Remote". Release files are named `M02-<module>-<version>.apk`.
- Every module is version 1.0.1 (version code 2), set once in `launcher/gradle.properties`.
- Package names, settings and provider ids are unchanged, so 1.0.1 installs over 1.0.0 and keeps every setting
  (with the same signing key).
- Look files are saved in "M02 Looks"; looks in the old folder are still listed. Provisioning and Link's update check
  still recognise APK files from 1.0.0, and Link still restores backups made by 1.0.0.
- Settings > Appearance: "Glass style" became the Look page (looks, glass style, glass amount, corners, tint, accent
  colour, text size), and "Start-up and loading" is its own page.
- Settings > Home screen > Now Playing: "Show on Ambient" became "Screensaver follows the music" (Full screen, Small
  card or Off), plus a Style row.
- Link targets Android 11 (API 30), so Play Protect no longer blocks a fresh install of Link.

### Added
- Screensaver follows the music: while a song plays (or is paused for less than 10 minutes) the screensaver shows the
  cover, title, artist, a progress line and the app. Tracks crossfade (not on LITE); the dim and pixel shift still apply.
- Now Playing backdrop styles for Home and the screensaver: Cover, Blurred cover (default), Colour wash only and
  Artist banner. Text contrast stays at 4.5:1 or better in every style.
- Optional Spotify artwork: with your own free Spotify app key (entered on the TV or from the phone page, kept only in
  M02 Link and never backed up), Link fetches the artist picture for the Artist banner style.
- Look engine: presets (Mintaka, Clear, Cinema, Bright room, Classic), glass amount 0-100 %, corners, tint and accent
  colour, a live preview, and your own looks that can be copied to and loaded from look files (USB stick or Pictures).
- Liquid-glass lens on FULL devices: glass panels bend and slightly colour-fringe what is behind them; LITE devices
  keep flat glass. Focus on top-bar pills, Settings entries and rows springs a little.
- Home composer (Settings > Home screen > Home sections): presets Classic, Favorites + Apps, Favorites + Recent and
  Everything, up to four custom app groups, order and on/off per section, with a live preview.
- Time routines: scheduled actions (open ambient, room lighting, switch profile, bedtime on or off, open an app,
  focus mode) with days, time, "Try it now" and the last-run result.
- Ambient widgets: calendar (next three events, optional calendar permission), photo frame and album art.
- Start-up and loading choices: start-up animation (Sunrise, Minimal fade, Aurora sweep, Off), when it plays, loading
  indicator (Sun on a line, Pulse ring, Dots), app-launch animation (Brand colour, Fade, Zoom from tile, Off),
  duration and a preview.
- Cast & AirPlay page (Settings > Connections): the TV name, whether Chromecast is ready, whether the TV has an AirPlay
  input, and how to cast from each kind of device. An AirPlay input, when the TV has one, also shows in the Inputs panel.
- Phone remote (M02 Link) as an installable web app: D-pad, trackpad, live typing, media controls with progress,
  volume, an app grid, quick actions, power menu (with the Helper), sending links to M02 Browser, Home Assistant
  favourites and an encrypted backup and restore. Pair with a QR code and PIN from Settings > System > Phone remote.
- Settings > System > Smart home (through M02 Link) and an optional update check under About (off by default).
- Provisioning stops with a clear message when Play Protect holds an install; docs/PROVISIONING.md has a
  Troubleshooting section.

### Fixed
- Safe mode shows a focus highlight on its first row as soon as it opens.
- Top-bar pills are at least 40 dp tall, so they are easy to land on with the remote.
- The glass text scrim fades out at its edge instead of showing a hard inner rectangle.
- The Recent apps strip dims the whole screen, like Spotlight, instead of letting the shelf show through.
- Settings sidebar entries scrolled past the edge are clipped to the card.
- "Home screen" has its own sidebar icon; percentages read "18%" everywhere.
- The Inputs panel header says "2 in standby" when the ports are in standby, not "2 connected".
- Random-key stress tests can no longer switch off Wi-Fi or Bluetooth, forget networks or devices, or change the volume.
- The layout audit opens Settings on a known page and can no longer press OK, LEFT or RIGHT on a live control.
- `:link` passes lint.

### Known limits
- The TV keeps its own volume keys and volume pop-up; the launcher cannot replace them.
- Some TVs stop or pause the Helper in the background (TCL's memory manager does); its extras (double-Back, the
  controller Guide button and Back from the phone remote in other apps) may pause there. Launcher features keep working.
- The phone remote works fully on Home and in Settings. In other apps, Home, volume, media keys and app launching work;
  Back needs the Helper, and the D-pad, OK and typing do not reach other apps.
- Home sections draws Apps, Favorites, Recent apps and custom groups only. Continue watching, Now playing, Weather,
  Smart home, Quick actions, Get more apps and Clock are not drawn yet and cannot be switched on.
- Spotify pictures need M02 Link, your own key and a network connection; otherwise the cover is used.
- The launcher does not receive AirPlay; it shows what the TV offers. The TV tested so far has no AirPlay input.
- A fresh install of the launcher can be held by Play Protect (it targets Android 9 for the Wi-Fi controls); choose
  Install anyway on the TV. Updates are not held.
- A "casting now" notice was left out: it could not be verified on the test TV.
- Real-device testing so far is one TV (TCL 32S5403AF, Android 11, 1 GB).

## [1.0.0]

First public release, as Horizon (renamed Mintaka): a fast, glassy home screen for Android TV, built on a 1 GB TCL TV.

### Added
- Home with five layouts, brand-colour backdrops, clock, weather and quick controls.
- Spotlight search for apps, settings, recent items and the web.
- Glass styles, text-size and bright-room options.
- Settings that replace the TV's own: Wi-Fi, Bluetooth, devices, inputs, sound, accessibility, controllers, profiles,
  speed, private DNS, sleep timer and focus mode.
- Screensaver with real time and weather; Now Playing backdrop and mini player.
- First-run setup, safe mode, and room lighting that follows sunrise and sunset.
- Companion apps: Browser, Store, Weather, Helper and Link.
- `tvtool provision` with dry run, apply and undo script, plus the layout audit tool.

### Known limits
- The TV keeps its own volume keys and pop-up.
- Some TVs stop the Helper in the background; its extras may pause there.
- The phone remote cannot press keys inside other apps without the Helper.

[Unreleased]: https://github.com/krishnaakshath/mintaka/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/krishnaakshath/mintaka/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/krishnaakshath/mintaka/releases/tag/v1.0.0
