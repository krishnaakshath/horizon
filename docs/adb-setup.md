# One-time setup from a computer (adb)

The launcher's screens never show developer commands. On TVs that lack a system screen for a
permission, Settings says "Needs a one-time setup — see Settings > System > Setup". Until that
setup flow exists, these are the equivalent commands (run once, TV and computer on the same
network, `adb connect <tv-ip>` first).

| What | Command |
| --- | --- |
| Notification access (ambient chips, Now Playing) | `adb shell cmd notification allow_listener dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService` |
| Usage access (Insights) | `adb shell appops set dev.tvlauncher GET_USAGE_STATS allow` |
| Ambient as the screensaver | `adb shell settings put secure screensaver_components dev.tvlauncher/dev.tvlauncher.ambient.AmbientDream` |
| | `adb shell settings put secure screensaver_enabled 1` |
| | `adb shell settings put secure screensaver_activate_on_sleep 1` |
| Location for Wi-Fi / Bluetooth scans | `adb shell pm grant dev.tvlauncher android.permission.ACCESS_FINE_LOCATION` |
| | `adb shell settings put secure location_mode 3` |
| Copy a wallpaper | `adb push photo.jpg /sdcard/Android/data/dev.tvlauncher/files/wallpapers/` |
| Install an add-on | `adb install <file>.apk` |

The constants behind these lines stay in code (AmbientDream, UsageAccess, NetworkSettings,
NotificationAccessText.adbLine, UserWallpapers.ADB_PUSH) for tests and the setup flow.
