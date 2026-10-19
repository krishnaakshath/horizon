# Launcher as the TV shell: parity with the stock firmware

Status: **done** (in the launcher), **partial** (in the launcher with a stated limit),
**TV** (left to the TV, reachable from "Managed by the TV" or Safe mode),
**root** (not possible without root / platform signature).
"Grant" means the one-time `WRITE_SECURE_SETTINGS` grant from the provisioning tool.

| Area | Stock handled by | Launcher status | How |
|---|---|---|---|
| Power off / standby | Remote power key, TV power menu | partial | Settings > TV & system > Power: "Turn off" opens the TV power menu via the Helper add-on (accessibility global action). Direct sleep needs `DEVICE_POWER` (root). |
| Sleep timer | TCL settings | done | Power page: 15 min to 2 h; at the end the power menu opens (helper) or the screen dims to ambient. |
| Screensaver | Android TV settings | partial | Start now; choose the launcher's ambient as screensaver with the grant. |
| Input switching | TCL input menu | done | Inputs panel and Settings > Devices; HDMI real state, analog "Available". |
| HDMI-CEC | TCL settings | partial | State and CEC device names shown; switching CEC on/off is the TV's. |
| Live TV / channels / EPG | TCL TV app | partial | Settings > TV & system > Live TV opens the TCL TV app's live view, guide and channel list. Reading TvContract channels needs a system permission (root). |
| Casting receiver | Chromecast built-in / vendor | TV | Runs in the background on its own; nothing to show. |
| Voice assistant key | Firmware key mapping | TV | Key goes to the assistant directly; the launcher only has Spotlight on the Helper shortcut key. |
| Notifications | System UI | partial | Ambient chips and Now Playing with notification access (TV screen once; or the grant). |
| App install | Play Store | done | Get Apps companion and Discover. |
| App uninstall | TV settings | done | Settings > Apps, home tile menu (system confirm dialog). |
| App update | Play Store | TV | Play Store / Get Apps. |
| App info, stop | TV settings | partial | Settings > Apps: version, size, permissions in use, stop background work. Force stop / clear data: root. |
| App permissions | TV settings | partial | Shown per app; changing another app's permissions: root. |
| Accounts | TV settings | TV | Google account sign-in is the TV's. |
| Parental controls | TV settings | partial | Parental PIN for hidden apps in the launcher; content ratings / channel locks are the TV's. |
| Accessibility: text size | TV settings | done | Launcher text size (S to XL). |
| Accessibility: high contrast, bold | TV settings | done | Bright-room palette (solid panels, bold white text, thick focus ring). |
| Accessibility: reduce motion | TV settings | done | Launcher animations off. |
| Accessibility: captions | TV settings | partial | State shown; switch with the grant. Caption style: TV. |
| Accessibility: colour correction / inversion | TV settings | partial | With the grant. |
| Accessibility: screen reader | TV settings | partial | Row only when TalkBack is installed; switch with the grant. |
| Language | TV settings | partial | Shown; changing the system language: root. |
| Keyboard | TV settings | partial | Current keyboard shown; choosing another: TV. |
| Date & time, time zone | TV settings | partial | Shown; launcher clock 12/24 h. Setting time/zone: root. |
| Network: Wi-Fi | TV settings | done | Toggle, scan, join (password on the TV keyboard), saved networks, reconnect, hidden networks, details, one-line errors. Forget for networks the TV saved: TV. |
| Network: Ethernet, diagnostics | TV settings | partial | Status, IP, router, DNS, internet check. Static IP / proxy: root. |
| Bluetooth | TV settings | done | Toggle, search, pair, connect/disconnect (where the TV allows), forget. |
| Remote pairing | TCL pairing screen | partial | Bluetooth remotes pair in the Bluetooth page; TCL's RF/BLE auto-pair flow is the TV's. |
| Display / resolution / overscan | TV settings | TV | Managed by the TV. |
| Picture and sound modes | TCL settings | TV | Managed by the TV. |
| Audio output | TV settings | partial | Volume, mute and the current output shown; ARC / digital audio format: TV. |
| Storage | TV settings | done | Internal used/free. |
| USB drives | TV settings | partial | List, free space, browse; "Safely remove" through the TV's small remove dialog. |
| System update | TV settings | TV | Managed by the TV. |
| Factory reset | TV settings | TV | Managed by the TV. Reset launcher is in the launcher. |
| Developer options | TV settings | TV | Not offered in the launcher. |
| About | TV settings | done | About this TV. |

## Backlog (possible, not built yet)

- Notification access switch with the grant (`enabled_notification_listeners`), so Now Playing and
  ambient chips need no TV screen. Reason: touches two other modules' onboarding cards.
- Caption style (size, colours) with the grant (`accessibility_captioning_*` keys).
- Larger focus ring outside high contrast. Reason: needs a theme hook owned by the visual pass.
- Spotlight search hiding hidden apps while the parental PIN is set.
- Removing the remaining one-line computer commands from the Insights, Add-ons, Ambient and
  Wallpaper pages (pre-existing; replace with the grant where possible).
