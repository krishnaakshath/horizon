# Changelog

All notable changes are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Time routines: scheduled actions with list and edit pages.
- Screensaver and home widgets: calendar, photo frame and album art.
- Phone remote as a web app (PWA): trackpad, live typing, media keys, quick actions and backup.
- Cast and AirPlay page under Settings > Connections.
- Choice of start-up animations, loaders and app-launch transitions.
- Look engine with an adjustable glass amount.
- Home composer to choose and order Home content.
- Spotify artwork for Now Playing.
- Refreshed artwork for the Browser and Store apps.

## [1.0.0]

First public release: a fast, glassy home screen for Android TV, built on a 1 GB TCL TV.

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

[Unreleased]: https://github.com/krishnaakshath/lean-tv-launcher/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/krishnaakshath/lean-tv-launcher/releases/tag/v1.0.0
