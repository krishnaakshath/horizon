# System settings: what is ours and what stays with the TV

Each row says who owns a setting on the TCL 32S5403AF (Android 11 TV) and why. "Ours" means the
launcher (or Launcher Helper) draws the UI and changes the value itself. "TV only" means Android
gives a sideloaded app no API for it, so the only way is the TV's own menu.

## Sound

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

## Volume keys: how the bar gets them, and the fallback

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
