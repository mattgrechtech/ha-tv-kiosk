# Changelog

## Unreleased

Tested on a TCL Google TV (Android 14).

- Pop-up settings: size (small, medium, large), background opacity, larger text, default corner, time on screen and how many stack per corner, with a sample that shows each change. Each pop-up can override them (`size`, `opacity`, `text_size`).
- Pop-up buttons (`actions`): open a Home Assistant page or a web page, or close. Back on the dashboard closes the newest pop-up first. An opt-in setting lets pop-ups with buttons take the remote over other apps while they're up. Buttons are ignored on the token-less TvOverlay-compatible port.
- First-run setup, laid out like the TV's own setup: choose Home Assistant or a web page. Home Assistant is found on the network by itself (mDNS), checked, signed in to, and a dashboard chosen, with no URL typing. "Run setup again" is in Settings.
- Web mode: show any web page, with no Home Assistant sign-in or bridge. Home Assistant-only settings are hidden.
- More pages: rotate through several pages (HA dashboards or any web pages) every 15 s to 1 h. Using the remote holds the current page; the quick menu has "Next page". HA pages on the same server switch instantly, without a reload.
- Night screen: the screen goes black between set hours (pop-ups still show); any button wakes it for 5 minutes.
- New settings: `mode`, `pages` (one URL per line, or a JSON array), `rotate_seconds`, `screen_off_from`, `screen_off_to`.
- Restarts itself after a crash (stops after 3 crashes in 10 minutes), says so with a pop-up, and keeps the crash details.
- A log on the TV with tokens blanked, readable in Settings > Status and about, over the API (`/api/v1/logs`, `/api/v1/diagnostics`) and over adb (`DIAGNOSTICS`).
- Home Assistant's dashboard header is hidden too, not just the sidebar (HA's own kiosk mode only hides the sidebar).
- Fixed: the request to hide HA's sidebar could arrive before the frontend was ready and be dropped; it's now retried.
- Pop-ups with camera snapshots show over any app (tested over YouTube).
- Remote control: "jump between buttons" is the new default. Arrows move a highlight between the page's clickable items (found automatically, inside Home Assistant's components too), OK presses, Back closes an open Home Assistant pop-up first. The pointer stays available.
- Settings rebuilt as a left rail of categories with the settings on the right, like the TV's own settings. Every change saves straight away; no Save button.
- "Choose a dashboard" lists your Home Assistant dashboards and panels (over HA's WebSocket API), so no URL typing.
- Sign-in reworked: a Sign in button on the dashboard when needed, a sign-in screen with progress, errors and a "Signed in" step, then straight back to the dashboard.
- Fixed: a second service start killed the app on Android 14.

First code drop, not yet built or tested on a TV.

- Full-screen WebView dashboard with recovery: retry with back-off, renderer crash and freeze recovery, reload on network return, on long HA disconnects and nightly.
- HA frontend bridge (external auth and external bus): sign in once on the TV or a long-lived token, HA kiosk mode, in-app navigation, connection status.
- Remote: hold OK or Menu for the quick menu; optional on-screen pointer; Back goes back, then home.
- Pop-ups over other apps (TvOverlay-compatible fields), stacked per corner, replace by id.
- REST API with token, Host check and rate limiting; optional TvOverlay-compatible `/notify` endpoint.
- MQTT with Home Assistant discovery.
- Settings screen for the remote, encrypted secrets, JSON import/export, settings PIN.
- adb provisioning (receiver protected by the DUMP permission) and PowerShell/bash scripts.
- Boot start, foreground service, TV banner and icons.
