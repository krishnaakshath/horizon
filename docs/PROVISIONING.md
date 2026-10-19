# Provisioning a TV

`tvtool provision` sets up Horizon (the H02 launcher) and its companion apps on any Android TV, Google TV or
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
python3 -m tvtool provision --host 192.168.1.50 --apks ../launcher-apks

# 2. Make the changes:
python3 -m tvtool provision --host 192.168.1.50 --apks ../launcher-apks --apply

# 3. Optional: also turn off the TV's stock launcher, so the Home key opens H02
#    on TVs where the stock launcher is hard-wired to Home:
python3 -m tvtool provision --host 192.168.1.50 --apks ../launcher-apks --apply --take-over-home
```

`--apks` is a folder with the APK files. Files are recognised by name (`app-*.apk` or
`launcher-*.apk` for the launcher, `helper-*`, `browser-*`, `store-*`, `weather-*`, or
`<package name>.apk`). Any other file in the folder is ignored. Without `--apks` nothing is
installed and the other steps run against the apps already on the TV.

## What it does

Every command is printed with a one-line reason before anything happens. Steps that are
already in place on the TV are left out, so running it twice is harmless.

| Step | Why |
| --- | --- |
| Install or update the launcher and companion APKs (`adb install -r`, data kept) | The apps themselves |
| `pm grant dev.tvlauncher android.permission.WRITE_SECURE_SETTINGS` | Lets the launcher switch on Now Playing and the Helper by itself, also after updates and reboots |
| `pm grant dev.tvlauncher.helper android.permission.WRITE_SECURE_SETTINGS` | Lets the Helper keep its own service on, on TVs without an accessibility screen |
| `pm grant dev.tvlauncher android.permission.READ_EXTERNAL_STORAGE` | Your photos as wallpapers |
| `pm grant dev.tvlauncher android.permission.ACCESS_FINE_LOCATION` | Wi-Fi and Bluetooth lists of nearby networks and devices (position is never read) |
| `appops set dev.tvlauncher GET_USAGE_STATS allow` | Insights (stays on the TV) |
| `appops set dev.tvlauncher.store REQUEST_INSTALL_PACKAGES allow` | Get Apps can install the apps you pick |
| `cmd notification allow_listener dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService` | Now Playing and ambient notifications |
| `settings put secure enabled_accessibility_services <existing>:dev.tvlauncher.helper/dev.tvlauncher.helper.HelperService` and `accessibility_enabled 1` | The Helper's remote shortcuts. Services already enabled stay enabled |
| `settings put secure tv_user_setup_complete 1`, only when the setting exists and is `0` | Some TVs leave it at 0 after their own setup, and then the Home key does nothing |
| `cmd role add-role-holder android.app.role.HOME dev.tvlauncher` (Android 10+) or `cmd package set-home-activity` (older) | Makes H02 the home app |
| With `--take-over-home` only: `pm disable-user --user 0 <stock launcher>` | So a Home key wired to the stock launcher opens H02 |

After each step the result is read back from the TV (for example
`settings get secure enabled_accessibility_services`). A step that did not take effect stops
the run. Two checks only warn, because some TVs report them differently: the notification
listener and the home role.

## Safety rules

- Dry run by default. Nothing changes without `--apply`.
- Only H02's own packages and the stock launchers listed in the matched profile are
  ever touched. The plan is refused if any other package would be affected.
- A stock launcher is disabled only with `--take-over-home`, only if it is installed and
  enabled, and only after the TV confirms H02 is available as a home app. It is
  disabled, never uninstalled.
- Before the first change an undo script is written to `--out` (default `./scans-out/`),
  named `provision_undo_<model>_<time>.sh`. It reverses every planned step in reverse order
  (re-enables the stock launcher, restores previous settings values, revokes the grants and
  uninstalls apps that were not installed before).

## Undo

```
python3 -m tvtool rollback --host 192.168.1.50 --script scans-out/provision_undo_32S5403AF_20261020-101500.sh
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
- `stock_launchers`: the only packages `--take-over-home` may disable. H02's own
  packages are rejected here.

Shipped profiles: `tcl-32S5403AF.json` and `generic-android-tv.json` (Google TV, Android TV
and Fire TV stock launchers).

## Known TV behaviour

- **TCL 32S5403AF (Android 11):** the Home key always opens the stock Google TV launcher,
  whatever the default home app is, so `--take-over-home` is needed for Home to open TV
  Launcher. The TV has no notification-access or accessibility screens, so those are granted
  by provisioning. `tv_user_setup_complete` can be left at `0`, which makes the Home key do
  nothing; provisioning sets it to `1`.
- Some firmware ignores a written `enabled_notification_listeners` value; `cmd notification
  allow_listener` is the reliable way and is what provisioning uses.

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
