# API

Three ways to control the app, all LAN-only: a REST API, MQTT, and adb. They run the same commands.

## REST API

- Base: `http://<tv-ip>:2424/api/v1` (port in settings).
- Every request needs `Authorization: Bearer <token>`. The token is shown in Settings → *Show API address and token* (with a QR code), or over adb (see below). Ten wrong tokens from one address in a minute block that address for a minute.
- The `Host` header must be the TV's IP address (or `localhost`). This stops a web page in someone's browser from reaching the API through DNS tricks. Normal clients (curl, Home Assistant) send it automatically.
- Bodies are JSON with a `Content-Length` (no chunked uploads), up to 4 MB.
- Answers are JSON with `"ok": true|false` and, on failure, `"error"`.

```bash
curl -H "Authorization: Bearer $TOKEN" http://192.168.1.60:2424/api/v1/state
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"title":"Front door","message":"Someone is there","icon":"mdi:doorbell"}' \
     http://192.168.1.60:2424/api/v1/notify
```

| Route | Body | What it does |
|---|---|---|
| `GET /state` | | Everything below under [State](#state) |
| `POST /url` | `{"url"}` | Load a page (http/https only) |
| `POST /navigate` | `{"path"}` | Go to an HA page without a reload, e.g. `/lovelace/1` (falls back to a full load) |
| `POST /reload` | `{"hard": false}` | Reload; `hard` clears the cache first |
| `POST /screen` | `{"mode": "on"\|"dim"\|"blank", "level": 50}` | Dim (5–95 %) or blank the dashboard with a black layer. The backlight stays on: TVs don't let apps control it |
| `POST /wake` | | Screen back on, dashboard to the front |
| `POST /pointer` | `{"enabled": true}` | On-screen pointer on/off (omit `enabled` to toggle) |
| `POST /notify` | see [Pop-ups](#pop-ups) | Show a pop-up |
| `POST /notify/clear` | `{"id"}` | Remove one pop-up, or all without `id` |
| `POST /launch` | `{"package"}` | Open another TV app, e.g. `com.google.android.youtube.tv` |
| `POST /volume` | `{"level": 0-100}` | Media volume |
| `POST /tts` | `{"message", "language": "en-AU"}` | Speak with the TV's text-to-speech engine, if it has one |
| `POST /sound` | `{"url", "volume"}` | Play a sound or HA TTS audio URL |
| `POST /sound/stop` | | Stop sound and speech |
| `GET /settings` | | Settings as JSON (no secrets) |
| `POST /settings` | partial settings JSON | Change settings; answers which keys were applied and which were rejected |

Pop-up settings: `popup_size`, `popup_opacity`, `popup_text_size`, `popup_corner`, `popup_duration`, `popup_max_per_corner` (1-3), `popup_take_remote` (true/false).

Settings for what the TV shows: `mode` (`home_assistant` or `web`), `start_url`, `pages` (more pages to rotate through: one URL per line, or a JSON array), `rotate_seconds` (0 = off, else 10-86400), `screen_off_from` / `screen_off_to` (hours 0-23 when the screen goes black; `screen_off_from` -1 = never). For example `{"pages": ["http://192.168.1.50:8123/cameras"], "rotate_seconds": 60}`.

Status codes: `200` done, `202` started (speech, sound), `400` bad input, `401` token, `404` unknown route, `405` wrong method, `409` can't right now (e.g. the dashboard isn't open), `421` wrong Host, `429` blocked for bad tokens.

### Pop-ups

| Field | Also accepted as | Meaning |
|---|---|---|
| `title` | | Heading (title or message required) |
| `message` | | Body text |
| `source` | | Small grey line under the message |
| `image` | `largeIcon` | Picture across the top: URL, base64 or `data:` URI (a camera's `entity_picture` link works) |
| `icon` | `smallIcon` | `mdi:name`, URL or base64. MDI names show as a matching emoji for now |
| `color` | `smallIconColor` | Accent colour, `#RRGGBB` or `#AARRGGBB` |
| `corner` | | `top_start`, `top_end`, `bottom_start`, `bottom_end` |
| `duration` | | Seconds, 1–600 |
| `size` | | `small`, `medium`, `large` |
| `opacity` | | Background opacity in percent, 40–100. Images and text stay solid |
| `text_size` | | `normal` or `large` |
| `actions` | | Up to 3 buttons: `[{"label": "Open camera", "navigate": "/lovelace/cameras"}]`. `navigate` opens a Home Assistant path, `url` opens a web page, neither just closes the pop-up. A Close button is always added |
| `id` | | Same id replaces the pop-up on screen; `POST /notify/clear` with it closes it early |

Anything left out comes from Settings → Pop-ups and API → Look and behaviour (defaults: top right, 10 s, medium, 93 %, normal text).

Pop-ups appear over any app when the app may "display over other apps" (the provisioning script grants it). Without that permission they only show while the dashboard is open, and the answer includes a `warning`.

**Buttons and the remote.** Pop-ups never take the remote from the app in front, with one exception: a pop-up with `actions` takes it while the dashboard is in front, and over other apps too if *Pop-ups with buttons can take the remote* is on. Then OK presses a button and Back closes the pop-up; the remote goes back as soon as it closes or times out. Back on the dashboard always closes the newest pop-up first.

### TvOverlay-compatible endpoint

Off by default. When on (Settings → Local API), `POST http://<tv-ip>:5001/notify` accepts TvOverlay's fields, so automations written for TvOverlay work by changing nothing, or only the port. Like TvOverlay, it has **no token**: anyone on the network can show pop-ups, nothing else. For that reason `actions` are ignored there; use the token-protected `/api/v1/notify` for pop-ups with buttons. Don't run it and TvOverlay on the same port.

## State

```json
{
  "url": "http://192.168.1.50:8123/house?tv=1&external_auth=1",
  "title": "House",
  "screen": "on", "dim_level": 0,
  "dashboard_visible": true, "pointer": false,
  "ha_connection": "connected",
  "last_error": "", "last_reload_reason": "network back", "last_reload_at": 1793000000, "reload_count": 3,
  "app_uptime_s": 86400, "device_uptime_s": 172800, "app_started_at": 1793000000, "app_started_iso": "2026-10-30T09:00:00Z",
  "memory_available_mb": 812, "memory_total_mb": 1946, "memory_low": false, "app_memory_mb": 210,
  "overlay_permission": true, "ip": "192.168.1.60",
  "app_version": "0.1.0", "webview_version": "130.0.6723.102", "webview_package": "com.google.android.webview",
  "android_version": "12", "android_sdk": 31, "model": "...", "manufacturer": "..."
}
```

`ha_connection` comes from HA's frontend through the app bridge, so it is only known when the app manages sign-in (auth mode `login` or `token`); otherwise it stays `unknown`.

## MQTT

Settings → MQTT: broker address, port, user and password (create a dedicated HA user for it), optional TLS (the broker's certificate must be trusted by Android; self-signed certificates aren't supported yet).

The TV appears in Home Assistant as one device through MQTT discovery, with:

| Entity | Type |
|---|---|
| Reload, Reload and clear cache, Wake, Go to start page | buttons |
| Screen (on / dim / blank) | select |
| Dim level | number |
| Pointer | switch |
| Page (current URL; set it to load a page) | text |
| Pop-up | notify (plain text, or a JSON pop-up) |
| Dashboard visible, Can show pop-ups over apps | binary sensors |
| HA connection, last error, app started, reloads, app memory, memory available, WebView version, IP | diagnostic sensors |

Topics, with `<base>` = base topic (default `tvkiosk`) and `<id>` = the device id:

- `<base>/<id>/availability`: `online`/`offline` (retained; `offline` is the last will).
- `<base>/<id>/state`: the [State](#state) JSON (retained), refreshed on changes and every minute.
- `<base>/<id>/cmd/<command>`: commands. The payload is the same JSON as the REST body, for `url`, `navigate`, `reload`, `screen`, `wake`, `start_page`, `pointer`, `notify`, `notify_clear`, `launch`, `volume`, `tts`, `sound`, `stop_sound`. `notify`, `tts`, `url` and `navigate` also take plain text. Retained command messages are ignored.

## adb

For set-up scripts. The receiver needs a permission only adb (the shell) has.

```bash
PKG=io.github.mattgrechtech.tvkiosk.debug     # release builds: without .debug
R=$PKG/io.github.mattgrechtech.tvkiosk.service.ProvisionReceiver
A=io.github.mattgrechtech.tvkiosk

adb shell am broadcast -n $R -a $A.CONFIGURE --es json '{"start_url":"http://192.168.1.50:8123/house?tv=1"}'
adb shell am broadcast -n $R -a $A.GET_TOKEN
adb shell am broadcast -n $R -a $A.EXPORT                       # also writes Android/data/$PKG/files/settings.json
adb shell am broadcast -n $R -a $A.COMMAND --es command reload
```

`--es json_b64 <base64>` works wherever `--es json` does; the provisioning scripts use it because PowerShell 5.1 strips the quotes in JSON.

## Remote keys and your own pages

In the default "jump between buttons" remote mode, the app moves its own highlight between a page's clickable items. A page with its own remote navigation can take the keys instead:

- Put `data-tvkiosk-keys="page"` on the element that holds your controls (it works through shadow roots). While focus is inside it, the arrow keys go to the page as normal key events, OK as Enter, and Back as an Escape keydown (the app doesn't go back in history). Move focus out of it, for example back to a button outside it, to hand the keys back to the app.
- A focused slider, select or text field gets Left and Right without any marking; Up and Down still move the app's highlight.
