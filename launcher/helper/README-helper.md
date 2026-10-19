# Launcher Helper

H02 Helper: optional companion app for the Horizon launcher (`dev.tvlauncher.helper`). It provides:

- Double-Back to leave the foreground app (the first Back passes through untouched; a second Back within the window, default 500 ms, sends you Home).
- One user-chosen shortcut key that opens the launcher Spotlight (`dev.tvlauncher.action.SPOTLIGHT`).

Double-Back is on by default (it only works while the service is enabled); the shortcut key is chosen in the app. The app has no INTERNET permission and its accessibility service does not read screen content (`canRetrieveWindowContent=false`); it only learns the foreground package name from window-change events and sees key presses.

## Enable

Recommended: `tvtool provision` (see docs/PROVISIONING.md) enables the service and grants the Helper
WRITE_SECURE_SETTINGS, after which the Helper can switch itself on. Manual commands:

```
adb install -r helper-debug.apk
adb shell settings put secure enabled_accessibility_services dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService
adb shell settings put secure accessibility_enabled 1
```

If other accessibility services are already enabled, append with a colon instead of overwriting (read the current value with `adb shell settings get secure enabled_accessibility_services`).

Then open "Launcher Helper" from the launcher, turn on Double-Back and/or learn a shortcut key.

## Disable

```
adb shell settings put secure enabled_accessibility_services ""
```

(This clears all enabled accessibility services; restore others if you use them.) Or uninstall the app.

## Android 13+ restricted settings caveat

On Android 13 and later, sideloaded apps may be blocked from being enabled in the accessibility settings screen ("Restricted setting"). Enabling via the ADB `settings put` lines above, or installing with `adb install`, is expected to work around this. UNVERIFIED on TV firmware.

## Other unverified points

- Whether the TV firmware delivers a given remote key to accessibility key filtering (Home never does; some system keys are consumed earlier). Use "Learn key" to test.
- Whether starting the launcher from the service is allowed from the background on this firmware.

## Build

```
cd launcher && ./gradlew :helper:testDebugUnitTest :helper:assembleDebug
```
