# Provisioning a TV

`tvtool provision` sets up Mintaka (the M02 launcher) and its companion apps on any Android TV, Google TV or
Fire TV device you can reach with adb. One command does what would otherwise be a dozen
manual steps, and it is a **dry run unless you add `--apply`**.

After provisioning, the launcher's own first-run setup finishes everything else on the TV
with the remote (language, network, time and place, apps, look and feel, permissions).

## Requirements

- A computer with Python 3.10+ and the Android platform tools (`adb`) on the PATH.
- On the TV: Developer options and network debugging (ADB over network) switched on.
  Accept the "Allow debugging?" prompt on the TV the first time.
- The TV's IP address (Settings > Network on the TV).

## Usage

```
cd tools
# 1. See what would change (nothing is changed):
python3 -m tvtool provision --host <tv-ip> --apks ../launcher-apks

# 2. Make the changes:
python3 -m tvtool provision --host <tv-ip> --apks ../launcher-apks --apply

# 3. Optional: also turn off the TV's stock launcher, so the Home key opens M02
#    on TVs where the stock launcher is hard-wired to Home:
python3 -m tvtool provision --host <tv-ip> --apks ../launcher-apks --apply --take-over-home

# Only the power-on self-test (nothing else changes when everything is in place):
python3 -m tvtool provision --host <tv-ip> --self-test --apply
```

`--skip-self-test` leaves the power-on self-test out of the plan.

`--apks` is a folder with the APK files. Files are recognised by name: the release files
`M02-<module>-<version>.apk` (`M02-app-1.0.1.apk` is the launcher; `M02-helper-…`, `M02-browser-…`,
`M02-store-…`, `M02-weather-…`, `M02-link-…`), build outputs (`app-*.apk` or `launcher-*.apk` for the
launcher, `helper-*`, `browser-*`, `store-*`, `weather-*`, `link-*`), or `<package name>.apk`. Files from
releases before the rename to Mintaka (`H02-h02-1.0.0.apk`, `H02-link-1.0.0.apk`, …) still match. Any other
file in the folder is ignored. Without `--apks` nothing is
installed and the other steps run against the apps already on the TV.

## What it does

Every command is printed with a one-line reason before anything happens. Steps that are
already in place on the TV are left out, so running it twice is harmless.

| Step | Why |
| --- | --- |
| Install or update the APKs (`adb install -r`, data kept): **M02 Helper first, then M02**, then the other companions | The apps themselves. The launcher finds the Helper on its first start |
| After **every** install: `appops set dev.tvlauncher APP_AUTO_START allow`, `appops set dev.tvlauncher.helper APP_AUTO_START allow`, and the same with `APP_ASSOC_START` | TCL's vendor switches that let the apps start by themselves and start each other. TCL resets them to `ignore` on every `install -r`, so they are re-applied after each install and read back (`appops get`). TVs without these switches skip them with a warning |
| `pm grant dev.tvlauncher android.permission.WRITE_SECURE_SETTINGS` | Lets the launcher switch on Now Playing and the Helper by itself, also after updates and reboots |
| `pm grant dev.tvlauncher.helper android.permission.WRITE_SECURE_SETTINGS` | Lets the Helper keep its own service on, on TVs without an accessibility screen |
| `pm grant dev.tvlauncher android.permission.READ_EXTERNAL_STORAGE` | Your photos as wallpapers |
| `pm grant dev.tvlauncher android.permission.ACCESS_FINE_LOCATION` | Wi-Fi and Bluetooth lists of nearby networks and devices (position is never read) |
| `appops set dev.tvlauncher GET_USAGE_STATS allow` | Insights (stays on the TV) |
| `appops set dev.tvlauncher SYSTEM_ALERT_WINDOW allow` | "Display over other apps". On Android 11 this is what lets the launcher bring the home screen forward when the TV turns on. Nothing is drawn over other apps. Read back as `allow` |
| `appops set dev.tvlauncher.store REQUEST_INSTALL_PACKAGES allow` | Get Apps can install the apps you pick |
| `cmd notification allow_listener dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService` | Now Playing and ambient notifications |
| The Helper's accessibility service, in exactly this order: `settings put secure accessibility_enabled 0`, `settings delete secure enabled_accessibility_services`, `settings put secure enabled_accessibility_services <existing>:dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService`, `settings put secure accessibility_enabled 1` | The Helper's remote shortcuts. Services already enabled stay enabled. The off / clear / put back / on order clears Android's "Crashed services" mark, which otherwise keeps a Helper that was stopped once from ever being bound again |
| `dumpsys accessibility` | Checks that the Helper is listed under "Bound services". If not, the four steps above are repeated once; if it is still not bound the run ends with the warning *helper not running (TCL memory manager may stop it); launcher features still work* (a warning, not an error) |
| `settings put secure tv_user_setup_complete 1`, only when the setting exists and is `0` | Some TVs leave it at 0 after their own setup, and then the Home key does nothing |
| `cmd role add-role-holder android.app.role.HOME dev.tvlauncher` (Android 10+) or `cmd package set-home-activity` (older) | Makes M02 the home app |
| With `--take-over-home` only: `pm disable-user --user 0 <stock launcher>` | So a Home key wired to the stock launcher opens M02 |
| Self-test: `am start -a android.settings.SETTINGS`, a 15 s pause, `am broadcast -a tcl.sys.intent.action.STR_RESUME_COMPLETED -n dev.tvlauncher/.power.PowerOnReceiver` | Puts another app in front, then tells the launcher the TV just woke up (the same signal a TCL TV sends after standby). Expected in the log: `PowerOn: home in front via DIRECT` |
| Self-test clean-up: `run-as dev.tvlauncher rm -f shared_prefs/power_on.xml`, `am force-stop dev.tvlauncher`, `am start -n dev.tvlauncher/dev.tvlauncher.HomeActivity` | Forgets the test wake-up so it does not count against the launcher's limit of home pushes per wake. Needs a debuggable build; on a release build this only warns (the counter clears itself within 10 minutes). The "Open home after power on" switch returns to its default, on |

After each step the result is read back from the TV (for example
`settings get secure enabled_accessibility_services` or `appops get dev.tvlauncher
SYSTEM_ALERT_WINDOW`). A step that did not take effect stops the run. These only warn: the
notification listener and the home role (some TVs report them differently), vendor app-op
switches the TV does not have, the Helper not being bound after the retry, and the self-test.

## Safety rules

- Dry run by default. Nothing changes without `--apply`.
- Only M02's own packages and the stock launchers listed in the matched profile are
  ever touched. The plan is refused if any other package would be affected.
- A stock launcher is disabled only with `--take-over-home`, only if it is installed and
  enabled, and only after the TV confirms M02 is available as a home app. It is
  disabled, never uninstalled.
- Before the first change an undo script is written to `--out` (default `./scans-out/`),
  named `provision_undo_<model>_<time>.sh`. It reverses every planned step in reverse order
  (re-enables the stock launcher, restores the previous settings values, including both
  accessibility settings, puts every app op it changed back to the mode it had before
  (`APP_AUTO_START`, `APP_ASSOC_START`, `SYSTEM_ALERT_WINDOW`, `GET_USAGE_STATS`,
  `REQUEST_INSTALL_PACKAGES`), revokes the grants and uninstalls apps that were not installed
  before).
- The self-test only opens the TV's Settings (it changes nothing there) and sends the wake
  signal to M02's own receiver.

## Undo

```
python3 -m tvtool rollback --host <tv-ip> --script scans-out/provision_undo_32S5403AF_20261020-101500.sh
# or, from a shell with adb:
sh scans-out/provision_undo_32S5403AF_20261020-101500.sh
```

`rollback` refuses any line that is not one of the known undo commands, and any line for a
different host.

## Profiles

Profiles live in `profiles/provision/`. The most specific match wins: manufacturer and
model, then manufacturer only, then the generic fallback.

```json
{
  "name": "TCL 32S5403AF (Android TV 11)",
  "match": { "manufacturer": "TCL", "model": "32S5403AF" },
  "stock_launchers": ["com.google.android.tvlauncher", "com.google.android.leanbacklauncher"],
  "notes": ["Shown before the plan."]
}
```

- `match.manufacturer` / `match.model`: `ro.product.manufacturer` / `ro.product.model`
  (case-insensitive). Use `"match": {"fallback": true}` for the generic profile.
- `stock_launchers`: the only packages `--take-over-home` may disable. M02's own
  packages are rejected here.

Shipped profiles: `tcl-32S5403AF.json` and `generic-android-tv.json` (Google TV, Android TV
and Fire TV stock launchers).

## Known TV behaviour

- **TCL 32S5403AF (Android 11):** the Home key always opens the stock Google TV launcher,
  whatever the default home app is, so `--take-over-home` is needed for Home to open TV
  Launcher. The TV has no notification-access or accessibility screens, so those are granted
  by provisioning. `tv_user_setup_complete` can be left at `0`, which makes the Home key do
  nothing; provisioning sets it to `1`.
- **TCL app ops:** `APP_AUTO_START` and `APP_ASSOC_START` are reset to `ignore` by every
  `adb install -r`, for M02 and for the Helper. Re-run provisioning (or at least
  re-apply both ops for both apps) after every update.
- **TCL power-on:** a direct start of the home screen after standby works only with
  "Display over other apps" (`SYSTEM_ALERT_WINDOW`) allowed, so the launcher tries that
  first, then the Helper, then one last direct start, each checked 1.5 s later.
- **TCL memory manager:** it can stop the Helper at any time. Android 11 then marks the
  accessibility service as crashed and does not bind it again until it is switched off and
  on in the order above.
- Some firmware ignores a written `enabled_notification_listeners` value; `cmd notification
  allow_listener` is the reliable way and is what provisioning uses.

## Troubleshooting

### "Unsafe app blocked" / Google Play Protect holds an install

A fresh install of an app that Play Protect flags (for example one built for an older Android
version) waits on the TV for a choice in Play Protect's dialog, and `adb install` waits with it.
`tvtool provision` notices the dialog (it checks the TV's focused window every few seconds while an
install runs) and stops with:

> Google Play Protect is holding the install on the TV ... choose 'Install anyway', then run this command again.

What to do:

1. On the TV, select **More details**, then **Install anyway**. (Choosing **OK** cancels the install.)
2. Run the same `tvtool provision ... --apply` command again. Steps already in place are skipped.

Updates (`install -r` over an installed app) are not held this way. All M02 companions target a
recent Android version so they are not flagged; the launcher itself targets 28 on purpose (see
docs/SYSTEM_SETTINGS.md), so its *first* install may show the dialog.

Manual option, only if you understand the trade-off: Play Protect's check of apps installed over
adb can be switched off with

```
adb shell settings put global verifier_verify_adb_installs 0
```

`tvtool` never runs this for you: it turns off a security check for every app installed over adb,
not just M02. Turn it back on afterwards with the same command and `1`.

## On the TV afterwards

- The launcher re-applies its automatic configuration on every start and after each reboot
  (notification listener, Helper service, `tv_user_setup_complete`), using the
  `WRITE_SECURE_SETTINGS` grant. It never changes the system's home choice by itself.
- Settings > System > Setup re-runs the first-run setup. Settings > System > Backup & restore
  saves the whole configuration as `tvlauncher-config.json` on a USB stick (or the TV), and
  a second TV can restore it from its first-run setup.

## Launcher Helper

The Helper is an accessibility service that sees remote key presses in every app (it does not
read screen content). It needs:

- the service enabled (`enabled_accessibility_services` plus `accessibility_enabled 1`),
  done by provisioning, by the launcher's setup, or by the Helper itself once it holds
  `WRITE_SECURE_SETTINGS`;
- firmware that delivers the key to accessibility key filtering (Home never is; most other
  remote keys are). Use "Learn key" in the Helper to test a key.

Double-Back (Back twice quickly leaves the current app for Home) is on by default; the
shortcut key that opens Spotlight is chosen in the Helper app.

### The honest limit on TCL TVs

On TCL TVs the memory manager can stop the Helper. When it does, the Helper's extras in
**other apps** drop out until it runs again: double-Back to Home, the shortcut key
and the game controller's Guide button going to Home. **M02 itself
is not affected**: the home screen, power-on (which uses "Display over other apps", not the
Helper) and everything inside the launcher keep working. Re-running provisioning switches the
Helper back on. The Helper's own screen and the launcher's Settings say the same in plain
words.

## Manual equivalents

If you prefer not to use the tool, these adb commands do the same one-time steps (TV and
computer on the same network, `adb connect <tv-ip>` first).

| What | Command |
| --- | --- |
| Notification access (ambient chips, Now Playing) | `adb shell cmd notification allow_listener dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService` |
| Usage access (Insights) | `adb shell appops set dev.tvlauncher GET_USAGE_STATS allow` |
| Ambient as the screensaver | `adb shell settings put secure screensaver_components dev.tvlauncher/dev.tvlauncher.ambient.AmbientDream`, then `screensaver_enabled 1` and `screensaver_activate_on_sleep 1` |
| Location for Wi-Fi and Bluetooth scans | `adb shell pm grant dev.tvlauncher android.permission.ACCESS_FINE_LOCATION` |
| Copy a wallpaper | `adb push photo.jpg /sdcard/Android/data/dev.tvlauncher/files/wallpapers/` |
| Install an add-on | `adb install <file>.apk` |
