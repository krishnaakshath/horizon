# Cast & AirPlay in Mintaka

Mintaka is a launcher. It does not receive casts or AirPlay streams itself. What it does is
**assist**: it finds the receivers your TV already has, tells you plainly whether each one is
there, and shows how to use them.

## What Mintaka does

- **Settings > Connections > Cast & AirPlay** shows:
  - **TV name**: the name phones and computers show when you pick a screen (the TV's device
    name, else its Bluetooth name). It is read-only here; rename the TV in the TV's own settings.
  - **Same network**: "Connected" when the TV is on Wi-Fi or Ethernet. The network name is never
    shown. The phone has to be on the same network.
  - **Chromecast**: "Ready" when the Google Cast receiver (Chromecast built-in) is installed.
  - **AirPlay**: "Ready" when the TV maker exposes an AirPlay receiver as a TV input.
  - **Open AirPlay** (only when AirPlay is there): switches to that input the same way the Inputs
    panel switches to an HDMI port (`TvContract.buildChannelUriForPassthroughInput`).
  - **Get a receiver app** (only when AirPlay is not there): opens Kodi through Get Apps or Google
    Play, the same way Discover does. Kodi is already in the Discover catalog and plays videos and
    music sent from phone apps over DLNA/UPnP.
  - **How to cast**: plain-words steps for iPhone/iPad/Mac, Android and computers, limited to the
    receivers this TV actually has.
- **Inputs panel**: a TV input that is the maker's AirPlay receiver shows as its own "AirPlay"
  row (after the physical ports) with a calm "Ready for iPhone, iPad and Mac" line, instead of a
  generic "Available" port.
- **Spotlight**: a "Cast & AirPlay" quick action (hidden by default; turn it on in Settings >
  Home > Spotlight), and the settings page is a search hit for "cast", "AirPlay" or "mirroring".

Nothing on this page changes a TV setting or starts another app on its own.

## Why Mintaka does not ship its own AirPlay receiver

- **Screen mirroring from iPhone needs FairPlay.** Apple's mirroring and most video streams are
  protected with FairPlay. Implementing or bundling it is not something an open MIT project can do
  legitimately, and Mintaka does not try.
- **Licensing.** The working open receivers (UxPlay, RPiPlay, shairport-sync and friends) are
  GPL. Mintaka's code is MIT; copying or linking GPL receiver code into the launcher would change
  the licence of the whole app. Mintaka keeps them at arm's length.
- **A launcher should not run a media server.** A receiver needs network listeners, the INTERNET
  permission, codecs and a foreground service. Mintaka has none of these on purpose.
- **Certified receivers exist.** Many TVs have AirPlay 2 from the maker (Apple-certified) and
  almost every Google TV / Android TV has Chromecast built-in. Mintaka surfaces those.

## Options when the TV has no AirPlay

1. **Use Cast instead.** YouTube, Netflix, Disney+, Spotify and most video apps on iPhone have a
   Cast button that works with Chromecast built-in.
2. **A receiver app.** Kodi (open source, in Discover) receives videos and music from phone apps
   over DLNA/UPnP. Paid AirPlay receiver apps exist on Google Play; Mintaka does not list them
   because they are closed source and their AirPlay support varies.
3. **A separate box.** A small Linux computer (e.g. a Raspberry Pi) running
   [UxPlay](https://github.com/FDH2/UxPlay) on an HDMI port gives iPhone screen mirroring. That
   is a separate GPL project and is not part of Mintaka; the Inputs panel then shows it like any
   other HDMI device.
4. **An Apple TV** on an HDMI port.

## What the reference TV (TCL, Android 11) exposes

Checked read-only (adb `dumpsys`, `pm list packages`, and an mDNS browse from a computer on the
same network):

- **Google Cast receiver: present.** `com.google.android.apps.mediashell` with
  `CastReceiverService` running; the TV advertises `_googlecast._tcp` with the friendly name equal
  to the TV's device name.
- **AirPlay: not exposed.** The TV registers four TV inputs (tuner, AV, HDMI 1, HDMI 2), none of
  them AirPlay; no package or service mentions AirPlay or RAOP, and the TV does not advertise
  `_airplay._tcp` or `_raop._tcp`. TCL's own TV app has an internal check for an AirPlay source,
  but this model has none. Mintaka therefore shows "AirPlay: Not on this TV" and offers Kodi.
- **Miracast / DLNA: no receiver package found.**

## Not done (yet)

- **A "Casting" toast when a session starts.** Detecting a Cast session would need Now Playing's
  notification access and a verified session from `com.google.android.apps.mediashell`; it was
  not verified on the TV, so it is left out rather than guessed.
