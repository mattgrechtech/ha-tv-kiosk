# TV Kiosk (working name)

**A free, open-source Android TV / Google TV app that shows a Home Assistant dashboard full screen and lets Home Assistant control the TV.** One app instead of a kiosk browser, adb scripts and a separate pop-up app.

> Status: **pre-alpha, not yet built or tested on a TV.** The code is written; the first build and real-TV tests come next ([docs/build-setup.md](docs/build-setup.md)). Companion to [ha-tv-kit](https://github.com/mattgrechtech/ha-tv-kit).

## What it does

- **Dashboard:** any HA dashboard full screen, signed in without typing a password on the TV, HA's header and sidebar hidden (HA's own kiosk mode, no extra card).
- **Stays up:** retries failed loads with back-off; replaces a crashed or frozen WebView; reloads when the network comes back, when HA stays unreachable, and optionally every night.
- **Remote-friendly:** Back goes back, then home. Hold **OK** (or press Menu) for a quick menu: reload, pointer, start page, settings. An optional on-screen **pointer** for cards that the D-pad can't reach.
- **Pop-ups over any app:** doorbell snapshots, bin night, alerts, in any corner, stacked, replaceable by id. Speaks TvOverlay's `/notify` format.
- **Home Assistant control:** load a page, navigate, reload, dim or blank the screen, wake, pop-ups, volume, speech, sounds, open another TV app, read state (page, connection, memory, uptime, WebView version).
  - **REST API** with a token, LAN only.
  - **MQTT** with HA discovery: the TV shows up as a device with buttons, a screen select, a dim slider and diagnostics.
- **Set up over adb** in one script: install, permissions, start URL, token.
- **Small and private:** no cloud, no accounts, no analytics, no Google services. Three small libraries.

Not included on purpose: remote shell or script injection, camera motion detection, device-owner lockdown, replacing the TV launcher. See the [plan](https://github.com/mattgrechtech/ha-tv-kit/blob/main/docs/plans/tv-kiosk-app.md) for why.

## Quick start

1. Build: [docs/build-setup.md](docs/build-setup.md) (JDK 17 + Android command-line tools, no Android Studio needed).
2. Install and configure on a TV with network debugging on:
   ```powershell
   .\tools\provision.ps1 -Tv 192.168.1.60 -StartUrl "http://192.168.1.50:8123/house?tv=1"
   ```
   ```bash
   ./tools/provision.sh 192.168.1.60 "http://192.168.1.50:8123/house?tv=1"
   ```
3. Sign-in: either HA's `trusted_networks` for the TV (see the kit's [TV dashboard guide](https://github.com/mattgrechtech/ha-tv-kit/blob/main/docs/04-tv-dashboard.md)), or Settings → Sign-in → *Sign in once on this TV*, then *Sign in to Home Assistant*.
4. Connect Home Assistant: [docs/home-assistant.md](docs/home-assistant.md).

The example IPs are placeholders: `192.168.1.50` for Home Assistant, `192.168.1.60` for a TV.

## Docs

- [API](docs/api.md): REST, MQTT topics and entities, adb.
- [Home Assistant set-up](docs/home-assistant.md): REST commands, MQTT, moving the kit's pop-ups over, wake on doorbell.
- [Build set-up](docs/build-setup.md).
- [Security](SECURITY.md).

## Requirements

Android TV / Google TV on **Android 10 or newer**. Also installs on Android tablets.

## Layout

```
app/src/main/java/io/github/mattgrechtech/tvkiosk/
  KioskApp.kt          app start: settings, state, controller, sign-in
  settings/            typed settings, encrypted secrets, JSON import/export
  web/                 dashboard activity, HA frontend bridge, sign-in, pointer
  control/             command execution and reported state
  overlay/             pop-ups
  api/                 HTTP server, REST routes, TvOverlay-compatible endpoint
  mqtt/                MQTT client and HA discovery
  service/             foreground service, boot and adb receivers
  ui/                  settings screen and dialogs
app/src/test/          unit tests for everything that doesn't need Android
tools/                 provisioning scripts, banner/icon generator
docs/
```

## Licence

[Apache-2.0](LICENSE). Third-party components: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Not affiliated with Home Assistant, Nabu Casa, Google, TvOverlay or Fully Kiosk Browser.
