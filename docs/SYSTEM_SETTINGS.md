# System settings in the launcher

The launcher no longer opens the TV's (TCL / Android TV) Settings app for everyday tasks.
`:app` targets **API 28** on purpose: below 29, Android 10/11 still honours the legacy
`WifiManager` calls (`setWifiEnabled`, `addNetwork`, `enableNetwork`, `removeNetwork`,
`reconnect`, `getConfiguredNetworks`), so Wi-Fi runs inside the launcher. See `launcher/app/lint.xml`.

The only remaining doors into the TV's own screens are
`dev.tvlauncher.system.TvScreens` (the "Managed by the TV" rows under
Settings > TV & system > Advanced, which need two presses of OK, and the Location row) and
Safe mode's escape hatch (`safemode/SettingsEscape`), which always stays.

| Setting | In launcher? | How / limit |
|---|---|---|
| Wi-Fi on/off | Yes | `WifiManager.setWifiEnabled` (target 28). |
| Wi-Fi scan list (signal, lock, saved) | Yes | Scan results + saved networks; needs the runtime location grant (asked with the system dialog) and the TV's Location switch on. |
| Join open / WEP / WPA / WPA2 / WPA3 | Yes | Password typed with the TV keyboard (show/hide); `addNetwork` + `enableNetwork`. WPA3 only on Android 10+. Enterprise (802.1X) networks: not supported. |
| Rejoin a saved network | Yes | `enableNetwork(id)` works for any saved network, including ones the TV saved. |
| Forget | Partly | `removeNetwork` succeeds only for networks the launcher saved; networks saved by the TV say so in one line. |
| Reconnect, hidden networks | Yes | `reconnect`; hidden SSID + password sheet. |
| Join errors | Yes | One line: "Wrong password" (supplicant auth error or repeated handshake drop), "Out of range" (timeout, not in scan), "Couldn't connect". |
| Connection details (IP, speed, band, router, DNS, internet) | Yes | Settings > Wi-Fi & network > Network check. |
| Ethernet | Shown | Status and IP; static IP / proxy need system access. |
| Location switch | No | Only the TV can switch it; the row opens the TV's Location screen. |
| Bluetooth on/off, search, pair | Yes | `enable()/disable()`, discovery, `createBond`. |
| Bluetooth connect / disconnect / forget | Tried | Profile `connect`/`disconnect` and `removeBond` by reflection; a calm line when the TV refuses. Renaming: not offered. |
| Inputs (HDMI / AV) | Yes | Launcher Inputs panel. HDMI shows Connected / Standby / No signal (hotplug, CEC); analog ports show "Available" (no detection). |
| HDMI-CEC | Shown | State and CEC devices; switching CEC itself is the TV's. |
| About this TV | Yes | Model, Android and security update, storage, memory, screen, launcher version. |
| Date & time | Partly | Date, time zone, automatic time shown; launcher clock 12/24 h. Setting the time or zone needs system access. |
| Language & keyboard | Shown | Current and preferred languages, keyboard; changing them is the TV's. |
| Volume / mute | Yes | Settings > Sound > Volume & output (also Spotlight's Sound action) and the rows on TV & system; `AudioManager` (music stream); output shown. Sound modes and ARC set-up are the TV's. See "Sound: what is ours and what stays with the TV" below. |
| Apps | Yes | List, open, version, size, granted permissions, stop background work, uninstall user apps (system confirm dialog, `REQUEST_DELETE_PACKAGES`). Force stop and clear data need system access. |
| Storage & USB | Yes | Internal used/free; USB drives listed and browsable; "Safely remove" uses the TV's small remove dialog when it has one. |
| Power | Partly | "Turn off" opens the TV power menu through the Helper add-on; sleep timer opens it at the end (or dims to ambient). Apps cannot sleep the TV directly. "Power on" (open home after power on) is a row on the same page; Spotlight's Power and Sleep timer actions open this page. |
| Screensaver | Partly | Start now; select the launcher as screensaver only with the one-time secure-settings grant. |
| Accessibility | Yes / partly | Launcher text size, high contrast, reduce motion, sound feedback, bold text. Captions, colour correction / inversion, screen reader and Helper switches need the one-time `WRITE_SECURE_SETTINGS` grant; without it they show state only. |
| Parental PIN | Yes | Guards bringing hidden apps back (home menu, Settings > Home layout); hidden apps leave the Apps list while it is set. |
| Reset launcher | Yes | Clears launcher preferences (not the crash guard) and restarts home. |
| Picture & sound processing, resolution | No | "Managed by the TV" (TCL settings app). |
| Accessibility services screen | No | "Managed by the TV" (or the grant above). |
| System update | No | "Managed by the TV". |
| Factory reset | No | "Managed by the TV". |
| Usage access, notification access | No | Special app access screens of the TV (Insights, Now Playing, ambient chips). |
| Display over other apps | No | Special app access screen of the TV, for "Open home after power on" (TV & system > Power & sleep > Power on). |
| Setup (first run) | Partly | The setup checklist still hands the few OS-only choices to the TV: language, keyboard, time zone, accounts, default home app and the special access grants above. Wi-Fi in setup opens the launcher's own Wi-Fi & network page. |
| Everything else | No | "All TV settings" in Managed by the TV, and Safe mode. |

## Sound: what is ours and what stays with the TV

Each row says who owns a setting on the TCL 32S5403AF (Android 11 TV) and why. "Ours" means the
launcher (or Launcher Helper) draws the UI and changes the value itself. "TV only" means Android
gives a sideloaded app no API for it, so the only way is the TV's own menu.

### Sound

| Setting | Owner | Where | Notes |
|---|---|---|---|
| Media volume level | Ours | Settings > Sound > Volume & output > Volume; remote volume keys | `AudioManager.setStreamVolume(STREAM_MUSIC, level, 0)`; flags 0, so no system popup is requested. Read back after every change; if the TV does not take it, the page says "The TV is managing the volume for this output". |
| Mute | Ours | Volume & output > Mute; remote mute key | `adjustStreamVolume(STREAM_MUSIC, ADJUST_MUTE/UNMUTE, 0)`, read back with `isStreamMute`. |
| Volume popup (bar) | Ours, best effort | Drawn by the launcher (while it is in front) and by Launcher Helper (every app) | Needs the volume key to reach our code. See "Volume keys" below. Switch: Volume & output > Volume bar, and Launcher Helper > Use launcher volume bar (default on). |
| Where the sound plays (read) | Ours | Volume & output > Output, Advanced > Connected outputs | `MediaRouter.getSelectedRoute(ROUTE_TYPE_LIVE_AUDIO)` plus `AudioManager.getDevices(GET_DEVICES_OUTPUTS)`. |
| Switching output (speakers / ARC / optical / Bluetooth) | TV only | TCL Settings > Sound > Audio output | Selecting the media output is a system API on Android 11 (`setPreferredDeviceForStrategy`, MODIFY_AUDIO_ROUTING). Shown as "Managed by the TV". Pairing a Bluetooth speaker is ours (home Bluetooth panel); routing to it is automatic. |
| Left/right balance | TV only | TCL Settings > Sound | `master_balance` is a hidden system setting; apps cannot write it. Not shown in our pages. |
| Sound modes / sound style | TV only | TCL Settings > Sound | TCL middleware, no Android API. Advanced > "Sound modes and equaliser" opens the TV's page. |
| Equaliser | TV only | TCL Settings > Sound | Same. A per-app `android.media.audiofx.Equalizer` would only affect our own sounds, not apps or inputs. |
| Dolby Audio / DTS (Virtual:X, DRC) | TV only | TCL Settings > Sound | Vendor services (`com.dolby.android.audio.service`); no public API. |
| HDMI-CEC / ARC system audio control | TV only | TCL Settings > Sound / Inputs (T-Link) | `HdmiControlManager` is a system API behind HDMI_CEC (signature/privileged). When CEC system audio is on, Android's AudioService forwards our stream changes to the receiver itself; if it does not, our read-back fails and the key goes to the TV. |
| Picture modes, backlight | TV only | TCL Settings > Picture | TCL middleware. |
| UI sounds (launcher clicks) | Ours | Settings > Sound > UI sounds | Unchanged. |

### Volume keys: how the bar gets them, and the fallback

1. Launcher Helper's accessibility service filters keys (`flagRequestFilterKeyEvents`,
   `canRequestFilterKeyEvents`). If VOLUME_UP/DOWN/MUTE reach `onKeyEvent`, the helper applies the
   change, reads it back and only then consumes the key; otherwise it returns false and the TV
   handles the key as before.
2. While a launcher screen (home, Settings) is in front, the activity does the same in
   `dispatchKeyEvent`, so the bar works there without the helper.
3. Expected limit (AOSP Android 11, not yet verified on this TCL): on TV builds
   `PhoneWindowManager` handles volume keys itself (`mUseTvRouting`), and the accessibility
   `KeyboardInterceptor` asks the window policy about VOLUME_UP/DOWN before any accessibility
   service sees them. If TCL keeps that behaviour, UP/DOWN never reach either path, the TV shows its
   own popup, and only MUTE may be ours. The helper's settings line ("this TV keeps the volume keys
   for itself so far") shows which case applies. No app can hide the TV's popup in that case.
