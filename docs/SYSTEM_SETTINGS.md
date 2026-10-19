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
| Volume / mute | Yes | `AudioManager` (music stream); output shown. Sound modes and ARC set-up are the TV's. |
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
