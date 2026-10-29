package io.github.mattgrechtech.tvkiosk

import android.app.Application
import android.os.Build
import io.github.mattgrechtech.tvkiosk.control.KioskController
import io.github.mattgrechtech.tvkiosk.control.KioskState
import io.github.mattgrechtech.tvkiosk.service.KioskService
import io.github.mattgrechtech.tvkiosk.settings.Keys
import io.github.mattgrechtech.tvkiosk.settings.Settings
import io.github.mattgrechtech.tvkiosk.settings.createSettings
import io.github.mattgrechtech.tvkiosk.web.HaAuth
import java.security.SecureRandom

class KioskApp : Application() {

    lateinit var settings: Settings
        private set
    lateinit var state: KioskState
        private set
    lateinit var controller: KioskController
        private set
    lateinit var auth: HaAuth
        private set

    override fun onCreate() {
        super.onCreate()
        settings = createSettings(this)
        ensureIdentity(settings)
        state = KioskState()
        controller = KioskController(this, settings, state)
        auth = HaAuth(settings)
        KioskService.start(this)
        // Turning a control channel on or off starts or stops the service.
        settings.addListener { changed ->
            if (changed.any { it in SERVICE_KEYS }) KioskService.start(this)
        }
    }

    companion object {
        private val SERVICE_KEYS = setOf(Keys.API_ENABLED.name, Keys.TVOVERLAY_COMPAT.name, Keys.MQTT_ENABLED.name)

        /** A new random token: 32 bytes as hex. */
        fun newToken(bytes: Int = 32): String {
            val buffer = ByteArray(bytes)
            SecureRandom().nextBytes(buffer)
            return buffer.joinToString("") { "%02x".format(it) }
        }

        private fun ensureIdentity(settings: Settings) {
            settings.edit {
                if (settings[Keys.DEVICE_ID].isEmpty()) put(Keys.DEVICE_ID, newToken(4))
                if (settings[Keys.API_TOKEN].isEmpty()) put(Keys.API_TOKEN, newToken())
                if (settings[Keys.DEVICE_NAME].isEmpty()) put(Keys.DEVICE_NAME, Build.MODEL ?: "TV")
            }
        }
    }
}
