package io.github.mattgrechtech.tvkiosk.web

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.mattgrechtech.tvkiosk.BuildConfig
import io.github.mattgrechtech.tvkiosk.settings.Settings
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * The bridge HA's frontend uses to talk to an app that embeds it: external auth (tokens) and the
 * external bus (capabilities, connection status, kiosk mode, navigation).
 * https://contributors.home-assistant.io/frontend/reference/external-auth/
 * https://contributors.home-assistant.io/frontend/reference/external-bus/
 *
 * Security: the bridge must only ever answer the configured HA origin. With WebMessageListener
 * (`externalAppV2`) the WebView enforces that; the fallback `externalApp` interface is visible to
 * every page, so each call checks the origin of the page that is loaded.
 */
class HaBridge(
    private val webView: WebView,
    private val haOrigin: String,
    private val auth: HaAuth,
    private val events: Events,
) {
    interface Events {
        fun onConnectionStatus(status: String)
        fun onFrontendLoaded()
        fun onShowSettings()
        fun onAuthFailed(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var nextId = 1

    /** Origin of the page currently loaded; updated by the WebViewClient on every navigation. */
    @Volatile var pageOrigin: String? = null

    /** True once the frontend has asked for our config, i.e. its bus is up. */
    @Volatile var busReady = false
        private set

    @SuppressLint("JavascriptInterface", "AddJavascriptInterface")
    fun install() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "externalAppV2", setOf(haOrigin)) { _, message, origin, isMainFrame, _ ->
                if (!isMainFrame || Settings.originOf(origin.toString()) != haOrigin) return@addWebMessageListener
                val data = message.data ?: return@addWebMessageListener
                onV2Message(data)
            }
        } else {
            webView.addJavascriptInterface(V1(), "externalApp")
        }
    }

    /** Called when the WebView starts a new page; the frontend will ask for config again. */
    fun onPageStarted(url: String) {
        pageOrigin = Settings.originOf(url)
        busReady = false
    }

    fun setKioskMode(enable: Boolean) = sendCommand("kiosk_mode/set", JSONObject().put("enable", enable))

    fun navigate(path: String): Boolean {
        if (!busReady) return false
        sendCommand("navigate", JSONObject().put("path", path))
        return true
    }

    fun shutdown() {
        worker.shutdownNow()
    }

    private fun onV2Message(data: String) {
        val message = try {
            JSONObject(data)
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable bridge message")
            return
        }
        val payload = message.optJSONObject("payload") ?: JSONObject()
        when (message.optString("type")) {
            "getExternalAuth" -> getExternalAuth(payload)
            "revokeExternalAuth" -> revokeExternalAuth(payload)
            "externalBus" -> onBusMessage(payload)
        }
    }

    private inner class V1 {
        @JavascriptInterface
        fun getExternalAuth(payload: String) {
            if (trusted()) parse(payload)?.let { main.post { this@HaBridge.getExternalAuth(it) } }
        }

        @JavascriptInterface
        fun revokeExternalAuth(payload: String) {
            if (trusted()) parse(payload)?.let { main.post { this@HaBridge.revokeExternalAuth(it) } }
        }

        @JavascriptInterface
        fun externalBus(payload: String) {
            if (trusted()) parse(payload)?.let { main.post { onBusMessage(it) } }
        }

        private fun trusted(): Boolean {
            val ok = pageOrigin == haOrigin
            if (!ok) Log.w(TAG, "Ignored a bridge call from a page that isn't Home Assistant")
            return ok
        }

        private fun parse(payload: String) = try {
            JSONObject(payload)
        } catch (e: Exception) {
            null
        }
    }

    private fun getExternalAuth(payload: JSONObject) {
        val callback = payload.optString("callback")
        if (callback != AUTH_CALLBACK) {
            Log.w(TAG, "Unexpected auth callback name, ignored")
            return
        }
        val force = payload.optBoolean("force", false)
        worker.execute {
            val js = try {
                val token = auth.accessToken(force)
                val body = JSONObject().put("access_token", token.accessToken).put("expires_in", token.expiresInSeconds)
                "window.$AUTH_CALLBACK(true, $body);"
            } catch (e: HaAuth.Failure) {
                main.post { events.onAuthFailed(e.message ?: "Sign-in failed") }
                "window.$AUTH_CALLBACK(false);"
            }
            main.post { evaluate(js) }
        }
    }

    private fun revokeExternalAuth(payload: JSONObject) {
        val callback = payload.optString("callback")
        if (callback != REVOKE_CALLBACK) return
        worker.execute {
            auth.signOut()
            main.post {
                evaluate("window.$REVOKE_CALLBACK(true);")
                events.onAuthFailed("Signed out from Home Assistant: open settings to sign in again")
            }
        }
    }

    private fun onBusMessage(message: JSONObject) {
        val id = message.optInt("id")
        when (message.optString("type")) {
            "config/get" -> {
                busReady = true
                reply(id, JSONObject()
                    .put("hasSettingsScreen", true)
                    .put("hasSidebar", false)
                    .put("canWriteTag", false)
                    .put("hasExoPlayer", false)
                    .put("canCommissionMatter", false)
                    .put("hasAssist", false)
                    .put("hasBarCodeScanner", 0)
                    .put("appVersion", BuildConfig.VERSION_NAME))
            }
            "entity/add_to/get_actions" -> reply(id, JSONObject().put("actions", org.json.JSONArray()))
            "connection-status" -> {
                val status = message.optJSONObject("payload")?.optString("event").orEmpty()
                if (status.isNotEmpty()) events.onConnectionStatus(status)
            }
            "frontend/loaded" -> events.onFrontendLoaded()
            "config_screen/show" -> events.onShowSettings()
            "result" -> if (!message.optBoolean("success", true)) {
                Log.i(TAG, "Frontend refused command $id: ${message.optJSONObject("error")}")
            }
            // Everything else (haptics, barcode, Matter, more-info events...) doesn't apply to a TV.
        }
    }

    private fun reply(id: Int, result: JSONObject) {
        sendRaw(JSONObject().put("id", id).put("type", "result").put("success", true).put("result", result))
    }

    private fun sendCommand(command: String, payload: JSONObject) {
        sendRaw(JSONObject().put("id", nextId++).put("type", "command").put("command", command).put("payload", payload))
    }

    private fun sendRaw(message: JSONObject) {
        if (pageOrigin != haOrigin) return
        evaluate("window.externalBus && window.externalBus($message);")
    }

    private fun evaluate(js: String) {
        // Never hand a token to a page that isn't Home Assistant.
        if (pageOrigin != haOrigin) return
        webView.evaluateJavascript(js, null)
    }

    private companion object {
        const val TAG = "HaBridge"
        const val AUTH_CALLBACK = "externalAuthSetToken"
        const val REVOKE_CALLBACK = "externalAuthRevokeToken"
    }
}
