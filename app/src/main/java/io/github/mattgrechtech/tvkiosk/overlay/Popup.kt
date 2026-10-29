package io.github.mattgrechtech.tvkiosk.overlay

import org.json.JSONObject
import java.util.UUID

/**
 * A pop-up notification. Field names follow TvOverlay's `/notify` API so the same payload works
 * for both apps (`title`, `message`, `source`, `image`, `largeIcon`, `smallIcon`, `smallIconColor`,
 * `corner`, `duration`, `id`), plus the shorter `icon` and `color` used by this app's own API.
 */
data class Popup(
    val id: String,
    val title: String,
    val message: String,
    val source: String,
    /** URL, base64 or a data: URI. */
    val image: String?,
    /** `mdi:name`, URL or base64. */
    val icon: String?,
    /** ARGB colour for the icon and the accent strip. */
    val color: Int?,
    val corner: Corner,
    val durationSeconds: Int,
) {
    enum class Corner(val apiName: String) {
        TOP_START("top_start"), TOP_END("top_end"), BOTTOM_START("bottom_start"), BOTTOM_END("bottom_end");

        companion object {
            fun parse(value: String?): Corner? = entries.firstOrNull { it.apiName == value?.trim()?.lowercase() }
        }
    }

    companion object {
        const val DEFAULT_DURATION = 10
        const val MAX_DURATION = 600

        /** Returns null when there is nothing to show (no title and no message). */
        fun fromJson(json: JSONObject): Popup? {
            val title = text(json, "title")
            val message = text(json, "message")
            if (title.isEmpty() && message.isEmpty()) return null

            val duration = if (json.has("duration")) json.optInt("duration", DEFAULT_DURATION) else DEFAULT_DURATION
            return Popup(
                id = text(json, "id").ifEmpty { UUID.randomUUID().toString() },
                title = title,
                message = message,
                source = text(json, "source"),
                image = firstNonBlank(json, "image", "largeIcon"),
                icon = firstNonBlank(json, "icon", "smallIcon"),
                color = parseColor(firstNonBlank(json, "color", "smallIconColor")),
                corner = Corner.parse(text(json, "corner")) ?: Corner.TOP_END,
                durationSeconds = duration.coerceIn(1, MAX_DURATION),
            )
        }

        /** A trimmed string field; "" when missing or JSON null (optString would give "null"). */
        private fun text(json: JSONObject, name: String): String =
            if (json.isNull(name)) "" else json.optString(name).trim()

        private fun firstNonBlank(json: JSONObject, vararg names: String): String? =
            names.map { text(json, it) }.firstOrNull { it.isNotEmpty() }

        /** "#RRGGBB", "RRGGBB", "#AARRGGBB" or "AARRGGBB"; null if absent or invalid. */
        fun parseColor(value: String?): Int? {
            val hex = value?.trim()?.removePrefix("#") ?: return null
            if (hex.length != 6 && hex.length != 8) return null
            val parsed = hex.toLongOrNull(16) ?: return null
            return (if (hex.length == 6) parsed or 0xFF000000L else parsed).toInt()
        }
    }
}
