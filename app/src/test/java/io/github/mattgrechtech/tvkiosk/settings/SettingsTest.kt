package io.github.mattgrechtech.tvkiosk.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun getString(key: String) = values[key]
    override fun putAll(values: Map<String, String?>) {
        for ((k, v) in values) if (v == null) this.values.remove(k) else this.values[k] = v
    }
}

class SettingsTest {

    private val plain = MemoryStore()
    private val secrets = MemoryStore()
    private val settings = Settings(plain, secrets)

    @Test fun defaultsWhenUnset() {
        assertEquals("", settings[Keys.START_URL])
        assertEquals(2424, settings[Keys.API_PORT])
        assertTrue(settings[Keys.API_ENABLED])
        assertEquals("none", settings[Keys.AUTH_MODE])
    }

    @Test fun secretsGoToTheSecretStore() {
        settings[Keys.API_TOKEN] = "abc"
        settings[Keys.START_URL] = "http://ha.test:8123/"
        assertEquals("abc", secrets.values["api_token"])
        assertNull(plain.values["api_token"])
        assertEquals("http://ha.test:8123/", plain.values["start_url"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidValuesAreRefused() {
        settings[Keys.API_PORT] = 70000
    }

    @Test fun listenersHearChangedKeysOnce() {
        val heard = mutableListOf<Set<String>>()
        settings.addListener { heard += it }
        settings.edit {
            put(Keys.API_PORT, 3000)
            put(Keys.MQTT_HOST, "broker")
        }
        assertEquals(listOf(setOf("api_port", "mqtt_host")), heard)
    }

    @Test fun originOf() {
        assertEquals("http://ha.test:8123", Settings.originOf("http://HA.test:8123/lovelace/0?kiosk"))
        assertEquals("https://ha.test", Settings.originOf("https://ha.test"))
        assertNull(Settings.originOf("ftp://ha.test"))
        assertNull(Settings.originOf(""))
    }

    @Test fun haBaseUrlPrefersTheExplicitOne() {
        settings[Keys.START_URL] = "http://dash.test:8123/house?tv=1"
        assertEquals("http://dash.test:8123", settings.haBaseUrl())
        settings[Keys.HA_URL] = "http://ha.test:8123/"
        assertEquals("http://ha.test:8123", settings.haBaseUrl())
    }

    @Test fun exportLeavesSecretsOutUnlessAsked() {
        settings[Keys.API_TOKEN] = "secret-token"
        settings[Keys.HA_REFRESH_TOKEN] = "refresh"
        val withoutSecrets = SettingsJson.export(settings, includeSecrets = false)
        assertFalse(withoutSecrets.has("api_token"))
        assertTrue(withoutSecrets.has("start_url"))
        val withSecrets = SettingsJson.export(settings, includeSecrets = true)
        assertEquals("secret-token", withSecrets.getString("api_token"))
        assertFalse("the refresh token never leaves", withSecrets.has("ha_refresh_token"))
    }

    @Test fun importAppliesValidAndReportsTheRest() {
        val result = SettingsJson.import(settings, JSONObject("""
            {
              "start_url": "http://ha.test:8123/lovelace/0",
              "api_port": "2500",
              "mqtt_enabled": "on",
              "keep_screen_on": false,
              "auth_mode": "magic",
              "nightly_reload_hour": 24,
              "device_id": "hijack",
              "made_up": 1,
              "mqtt_port": true
            }
        """.trimIndent()))

        assertEquals(setOf("start_url", "api_port", "mqtt_enabled", "keep_screen_on"), result.applied.toSet())
        assertEquals(setOf("auth_mode", "nightly_reload_hour", "device_id", "made_up", "mqtt_port"), result.rejected.keys)
        assertEquals(2500, settings[Keys.API_PORT])
        assertTrue(settings[Keys.MQTT_ENABLED])
        assertFalse(settings[Keys.KEEP_SCREEN_ON])
        assertEquals("none", settings[Keys.AUTH_MODE])
    }

    @Test fun importRoundTripsAnExport() {
        settings.edit {
            put(Keys.START_URL, "http://ha.test:8123/house?tv=1")
            put(Keys.NIGHTLY_RELOAD_HOUR, 3)
            put(Keys.MQTT_BASE_TOPIC, "kiosk")
        }
        val copy = Settings(MemoryStore(), MemoryStore())
        val result = SettingsJson.import(copy, SettingsJson.export(settings, includeSecrets = false))
        assertTrue(result.rejected.toString(), result.rejected.isEmpty())
        assertEquals("http://ha.test:8123/house?tv=1", copy[Keys.START_URL])
        assertEquals(3, copy[Keys.NIGHTLY_RELOAD_HOUR])
        assertEquals("kiosk", copy[Keys.MQTT_BASE_TOPIC])
    }

    @Test fun pinMustBeDigits() {
        val result = SettingsJson.import(settings, JSONObject("""{"settings_pin":"12ab"}"""))
        assertEquals(setOf("settings_pin"), result.rejected.keys)
        SettingsJson.import(settings, JSONObject("""{"settings_pin":"2468"}"""))
        assertEquals("2468", settings[Keys.SETTINGS_PIN])
    }
}
