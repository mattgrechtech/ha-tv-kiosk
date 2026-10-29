# Third-party notices

Libraries bundled in the app:

| Component | Version | Licence | Source |
|---|---|---|---|
| AndroidX WebKit | 1.12.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/webkit |
| Eclipse Paho MQTT Java client (mqttv3) | 1.2.5 | EPL-2.0 or EDL-1.0 (BSD-3-Clause), at the user's choice | https://github.com/eclipse/paho.mqtt.java |
| ZXing core | 3.5.3 | Apache-2.0 | https://github.com/zxing/zxing |
| Kotlin standard library | 2.0.21 | Apache-2.0 | https://github.com/JetBrains/kotlin |

Test-only (not shipped): JUnit 4.13.2 (EPL-1.0), JSON-java 20240303 (Public Domain).

No code has been copied from other projects so far. When code is adapted from another project (for example Kite AOS, WallPanel or the Home Assistant Companion app, all Apache-2.0), list it here with its licence and keep its copyright notice in the file.

Interface compatibility: the `/notify` endpoint accepts the same JSON fields as TvOverlay's public REST API, so existing automations keep working. No TvOverlay code is used. Home Assistant's external auth and external bus protocols are implemented from Home Assistant's public developer documentation.
