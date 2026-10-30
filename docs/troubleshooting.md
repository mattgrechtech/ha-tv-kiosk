# Troubleshooting

First, look at Settings → Status and about: the last error, the last crash and *Show log* usually say what's wrong. *Save diagnostics* writes a file for a bug report (no passwords or tokens in it); it's also at `GET /api/v1/diagnostics`.

## Setup and sign-in

**Setup doesn't find Home Assistant.** The TV must be on the same network (not a guest Wi-Fi), and some routers block the discovery messages (mDNS) between Wi-Fi and wired devices. Choose *Type the address* and enter it, for example `http://192.168.1.50:8123`.

**"That didn't work" when checking the address.** Check the port (usually 8123) and `http` versus `https`. If Home Assistant is behind a reverse proxy, use the address the TV can reach on your network.

**The sign-in page keeps coming back.** Home Assistant may refuse the TV's sign-in after its user was changed or the refresh token was deleted in Home Assistant (Profile → Security → Refresh tokens). Sign in again from Settings → Sign-in. For a TV that should never ask, add it to Home Assistant's `trusted_networks` (see ha-tv-kit's [TV dashboard guide](https://github.com/mattgrechtech/ha-tv-kit/blob/main/docs/04-tv-dashboard.md)).

**A blank or white page.** Usually the page failed to load: the panel says why and retries by itself. If it stays blank, update *Android System WebView* (or Chrome) from the Play Store; the app needs a WebView from the last few years.

## Pop-ups

**Pop-ups only show on the dashboard, not over other apps.** The app needs "display over other apps". Settings → Pop-ups and API shows whether it has it. Many TVs have no screen for this permission; grant it once over adb:

```bash
adb shell appops set io.github.mattgrechtech.tvkiosk SYSTEM_ALERT_WINDOW allow
```

**Pop-ups from TvOverlay automations don't arrive.** Turn on *TvOverlay-compatible pop-ups* (Settings → Pop-ups and API) and make sure TvOverlay itself isn't running on that TV: both use port 5001.

**A camera picture doesn't show.** `image` must be a full URL the TV can reach, with a fresh token: prefix the camera's `entity_picture` with your Home Assistant address when the automation runs, not before.

## Remote

**A button on the page can't be reached.** Switch the remote to *Pointer* in the quick menu (hold OK), or set Settings → Screen and remote → Remote to *Pointer* for that TV.

**The arrow keys do nothing on my own page.** In "jump between buttons" mode the app moves its own highlight. A page with its own remote navigation can take the keys: see [Remote keys and your own pages](api.md#remote-keys-and-your-own-pages).

## Forgotten settings PIN

The PIN only protects the settings screen on the TV. To remove it, any of:

- from Home Assistant or a computer, with the API token: `POST /api/v1/settings` with `{"settings_pin": ""}`;
- over adb: `adb shell am broadcast -n io.github.mattgrechtech.tvkiosk/.service.ProvisionReceiver -a io.github.mattgrechtech.tvkiosk.CONFIGURE --es json '{"settings_pin":""}'`;
- in the TV's own settings: Apps → TV Kiosk → Clear data (this resets everything, including the sign-in).

## Updating and uninstalling

**Update:** install the new APK over the old one (`adb install -r`, or open it with a file manager or the Downloader app). Settings and the sign-in are kept. A debug build (`.debug`) and a release build are separate apps and can be installed side by side.

**Uninstall:** uninstall from the TV's settings, then in Home Assistant remove the TV's refresh token (your TV user → Profile → Security → Refresh tokens) and, if you used MQTT, delete the device under the MQTT integration.
