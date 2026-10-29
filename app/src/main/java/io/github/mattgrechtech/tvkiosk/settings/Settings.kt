package io.github.mattgrechtech.tvkiosk.settings

/**
 * Typed settings. Plain values live in one store, secrets (tokens, passwords, PIN) in another
 * that is encrypted at rest. Everything that reads or writes settings goes through [Settings],
 * and every key is declared once in [Keys], so import/export, provisioning and the settings
 * screen can't drift apart.
 */

/** A minimal key-value store, so settings logic can be unit-tested without Android. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putAll(values: Map<String, String?>)
}

class Key<T : Any>(
    val name: String,
    val default: T,
    val secret: Boolean = false,
    /** Returns an error message, or null when the value is acceptable. */
    val check: (T) -> String? = { null },
)

object Keys {
    private fun port(value: Int) = if (value in 1..65535) null else "must be a port number (1-65535)"
    private fun url(value: String) =
        if (value.isEmpty() || value.startsWith("http://") || value.startsWith("https://")) null
        else "must start with http:// or https://"

    // Dashboard
    val START_URL = Key("start_url", "", check = ::url)
    /** Home Assistant base URL. Empty means "the origin of the start URL". */
    val HA_URL = Key("ha_url", "", check = ::url)
    /** login: sign in once in the app (default); token: long-lived token; none: the page handles login. */
    val AUTH_MODE = Key("auth_mode", "login", check = { if (it in AUTH_MODES) null else "must be one of $AUTH_MODES" })
    val HA_KIOSK_MODE = Key("ha_kiosk_mode", true)
    val KEEP_SCREEN_ON = Key("keep_screen_on", true)
    val BOOT_START = Key("boot_start", false)
    val POINTER_DEFAULT = Key("pointer_default", false)

    // Recovery
    val RELOAD_ON_NETWORK = Key("reload_on_network", true)
    val RELOAD_ON_DISCONNECT_SECONDS = Key("reload_on_disconnect_seconds", 60, check = {
        if (it == 0 || it in 10..3600) null else "must be 0 (off) or 10-3600"
    })
    val NIGHTLY_RELOAD_HOUR = Key("nightly_reload_hour", -1, check = {
        if (it in -1..23) null else "must be -1 (off) or an hour 0-23"
    })

    // Local API
    val API_ENABLED = Key("api_enabled", true)
    val API_PORT = Key("api_port", 2424, check = ::port)
    val TVOVERLAY_COMPAT = Key("tvoverlay_compat", false)
    val TVOVERLAY_PORT = Key("tvoverlay_port", 5001, check = ::port)

    // MQTT
    val MQTT_ENABLED = Key("mqtt_enabled", false)
    val MQTT_HOST = Key("mqtt_host", "")
    val MQTT_PORT = Key("mqtt_port", 1883, check = ::port)
    val MQTT_TLS = Key("mqtt_tls", false)
    val MQTT_USER = Key("mqtt_user", "")
    val MQTT_BASE_TOPIC = Key("mqtt_base_topic", "tvkiosk", check = { topicSegment(it) })
    val MQTT_DISCOVERY_PREFIX = Key("mqtt_discovery_prefix", "homeassistant", check = { topicSegment(it) })

    // Device
    val DEVICE_NAME = Key("device_name", "")
    val DEVICE_ID = Key("device_id", "")

    // Secrets
    val API_TOKEN = Key("api_token", "", secret = true)
    val MQTT_PASSWORD = Key("mqtt_password", "", secret = true)
    val HA_LONG_LIVED_TOKEN = Key("ha_long_lived_token", "", secret = true)
    val HA_REFRESH_TOKEN = Key("ha_refresh_token", "", secret = true)
    val SETTINGS_PIN = Key("settings_pin", "", secret = true, check = {
        if (it.isEmpty() || (it.length in 4..8 && it.all(Char::isDigit))) null else "must be 4-8 digits"
    })

    val AUTH_MODES = listOf("none", "login", "token")

    private fun topicSegment(value: String) =
        if (value.isNotEmpty() && value.none { it in "+#/ " }) null else "must be one topic level, no + # / or spaces"

    val ALL: List<Key<*>> = listOf(
        START_URL, HA_URL, AUTH_MODE, HA_KIOSK_MODE, KEEP_SCREEN_ON, BOOT_START, POINTER_DEFAULT,
        RELOAD_ON_NETWORK, RELOAD_ON_DISCONNECT_SECONDS, NIGHTLY_RELOAD_HOUR,
        API_ENABLED, API_PORT, TVOVERLAY_COMPAT, TVOVERLAY_PORT,
        MQTT_ENABLED, MQTT_HOST, MQTT_PORT, MQTT_TLS, MQTT_USER, MQTT_BASE_TOPIC, MQTT_DISCOVERY_PREFIX,
        DEVICE_NAME, DEVICE_ID,
        API_TOKEN, MQTT_PASSWORD, HA_LONG_LIVED_TOKEN, HA_REFRESH_TOKEN, SETTINGS_PIN,
    )

    /** Keys that are internal state, not user settings: never imported. */
    val NOT_IMPORTABLE: Set<Key<*>> = setOf(DEVICE_ID, HA_REFRESH_TOKEN)

    fun byName(name: String): Key<*>? = ALL.firstOrNull { it.name == name }
}

class Settings(private val plain: KeyValueStore, private val secrets: KeyValueStore) {

    private val listeners = mutableListOf<(Set<String>) -> Unit>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: Key<T>): T {
        val raw = store(key).getString(key.name) ?: return key.default
        return (decode(key, raw) ?: key.default) as T
    }

    operator fun <T : Any> set(key: Key<T>, value: T) = edit { put(key, value) }

    /** Applies several changes and notifies listeners once. */
    fun edit(block: Editor.() -> Unit) {
        val editor = Editor()
        editor.block()
        if (editor.plainChanges.isNotEmpty()) plain.putAll(editor.plainChanges)
        if (editor.secretChanges.isNotEmpty()) secrets.putAll(editor.secretChanges)
        val changed = editor.plainChanges.keys + editor.secretChanges.keys
        if (changed.isNotEmpty()) synchronized(listeners) { listeners.toList() }.forEach { it(changed) }
    }

    fun addListener(listener: (Set<String>) -> Unit) = synchronized(listeners) { listeners += listener }

    fun removeListener(listener: (Set<String>) -> Unit) = synchronized(listeners) { listeners -= listener }

    /** The Home Assistant origin the app trusts: the HA URL if set, else the start URL's origin. */
    fun haBaseUrl(): String? {
        val explicit = get(Keys.HA_URL).trimEnd('/')
        if (explicit.isNotEmpty()) return originOf(explicit)
        return originOf(get(Keys.START_URL))
    }

    inner class Editor {
        internal val plainChanges = mutableMapOf<String, String?>()
        internal val secretChanges = mutableMapOf<String, String?>()

        /** Records [value] unless it equals the current one, so listeners only hear real changes. */
        fun <T : Any> put(key: Key<T>, value: T) {
            key.check(value)?.let { throw IllegalArgumentException("${key.name} $it") }
            if (this@Settings[key] == value) return
            (if (key.secret) secretChanges else plainChanges)[key.name] = value.toString()
        }

        fun clear(key: Key<*>) {
            (if (key.secret) secretChanges else plainChanges)[key.name] = null
        }
    }

    private fun store(key: Key<*>) = if (key.secret) secrets else plain

    companion object {
        /** Parses a stored or imported string for [key], or null if it isn't valid for the key's type. */
        fun decode(key: Key<*>, raw: String): Any? = when (key.default) {
            is String -> raw
            is Int -> raw.trim().toIntOrNull()
            is Boolean -> when (raw.trim().lowercase()) {
                "true", "1", "on", "yes" -> true
                "false", "0", "off", "no" -> false
                else -> null
            }
            else -> null
        }

        /** "http://host:8123/path?x" -> "http://host:8123", or null if it isn't an http(s) URL. */
        fun originOf(url: String): String? {
            val match = Regex("^(https?)://([^/?#]+)", RegexOption.IGNORE_CASE).find(url.trim()) ?: return null
            return "${match.groupValues[1].lowercase()}://${match.groupValues[2].lowercase()}"
        }
    }
}
