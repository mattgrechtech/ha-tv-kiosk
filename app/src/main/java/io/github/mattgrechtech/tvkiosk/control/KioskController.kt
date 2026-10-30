package io.github.mattgrechtech.tvkiosk.control

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.speech.tts.TextToSpeech
import io.github.mattgrechtech.tvkiosk.overlay.Popup
import io.github.mattgrechtech.tvkiosk.overlay.PopupManager
import io.github.mattgrechtech.tvkiosk.settings.Keys
import io.github.mattgrechtech.tvkiosk.settings.Settings
import io.github.mattgrechtech.tvkiosk.web.KioskActivity
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.github.mattgrechtech.tvkiosk.diag.AppLog

/** What the dashboard activity offers to the controller while it exists. */
interface DashboardSurface {
    fun loadUrl(url: String)
    fun reload(hard: Boolean, reason: String)
    fun applyScreen(mode: KioskState.Screen, level: Int)
    fun setPointer(enabled: Boolean)
    /** Navigates inside the HA frontend without a reload. Returns false if the bridge isn't up. */
    fun navigateHa(path: String): Boolean
}

/**
 * Executes commands from the REST API, MQTT, the TvOverlay-compatible endpoint and adb.
 * Every command runs on the main thread; [executeBlocking] is for server threads.
 */
class KioskController(
    private val context: Context,
    private val settings: Settings,
    val state: KioskState,
) {
    private val main = Handler(Looper.getMainLooper())
    private var surface: DashboardSurface? = null
    /** A URL asked for while no dashboard existed; the activity picks it up when it starts. */
    private var pendingUrl: String? = null
    val popups = PopupManager(context)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null

    fun attach(surface: DashboardSurface) {
        this.surface = surface
    }

    fun detach(surface: DashboardSurface) {
        if (this.surface === surface) this.surface = null
    }

    fun takePendingUrl(): String? = pendingUrl.also { pendingUrl = null }

    fun executeBlocking(name: String, args: JSONObject, timeoutSeconds: Long = 10): CommandResult {
        if (Looper.myLooper() == Looper.getMainLooper()) return execute(name, args)
        var result: CommandResult? = null
        val done = CountDownLatch(1)
        main.post {
            result = try {
                execute(name, args)
            } catch (e: Exception) {
                AppLog.e(TAG, "Command $name failed", e)
                CommandResult.error(500, e.message ?: e.javaClass.simpleName)
            }
            done.countDown()
        }
        return if (done.await(timeoutSeconds, TimeUnit.SECONDS)) result!!
        else CommandResult.error(504, "timed out")
    }

    fun execute(name: String, args: JSONObject): CommandResult {
        val result = dispatch(name, args)
        AppLog.i(TAG, "Command $name -> ${result.status}" + (result.body.optString("error").takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""))
        return result
    }

    private fun dispatch(name: String, args: JSONObject): CommandResult = when (name) {
        "state" -> CommandResult.ok(state.toJson(context))
        "url" -> loadUrl(args)
        "start_page" -> {
            val start = settings[Keys.START_URL]
            if (start.isEmpty()) CommandResult.error(409, "no start URL set") else loadUrl(JSONObject().put("url", start))
        }
        "navigate" -> navigate(args)
        "reload" -> reload(args)
        "screen" -> screen(args)
        "wake" -> wake()
        "pointer" -> pointer(args)
        "notify" -> notify(args)
        "notify_clear" -> {
            popups.clear(args.optString("id").ifEmpty { null })
            CommandResult.ok()
        }
        "launch" -> launch(args)
        "volume" -> volume(args)
        "tts" -> speak(args)
        "sound" -> sound(args)
        "stop_sound" -> {
            stopSound()
            tts?.stop()
            CommandResult.ok()
        }
        else -> CommandResult.error(404, "unknown command: $name")
    }

    private fun loadUrl(args: JSONObject): CommandResult {
        val url = args.optString("url").trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return CommandResult.error(400, "url must start with http:// or https://")
        }
        val current = surface
        if (current != null) current.loadUrl(url) else {
            pendingUrl = url
            bringToFront()
        }
        return CommandResult.ok()
    }

    private fun navigate(args: JSONObject): CommandResult {
        val path = args.optString("path")
        if (!path.startsWith("/")) return CommandResult.error(400, "path must start with /")
        val current = surface ?: return CommandResult.error(409, "the dashboard isn't open")
        if (current.navigateHa(path)) return CommandResult.ok()
        // No bridge (e.g. auth mode "none"): fall back to a full load on the HA origin.
        val base = settings.haBaseUrl() ?: return CommandResult.error(409, "no Home Assistant URL set")
        current.loadUrl(base + path)
        return CommandResult.ok(JSONObject().put("note", "loaded as a new page"))
    }

    private fun reload(args: JSONObject): CommandResult {
        val current = surface ?: return CommandResult.error(409, "the dashboard isn't open")
        current.reload(args.optBoolean("hard", false), "command")
        return CommandResult.ok()
    }

    private fun screen(args: JSONObject): CommandResult {
        val mode = when (args.optString("mode").lowercase(Locale.ROOT)) {
            "on" -> KioskState.Screen.ON
            "dim" -> KioskState.Screen.DIM
            "blank", "off" -> KioskState.Screen.BLANK
            else -> return CommandResult.error(400, "mode must be on, dim or blank")
        }
        val level = when (mode) {
            KioskState.Screen.ON -> 0
            KioskState.Screen.BLANK -> 100
            KioskState.Screen.DIM -> args.optInt("level", 50).coerceIn(5, 95)
        }
        setScreen(mode, level)
        return CommandResult.ok()
    }

    fun setScreen(mode: KioskState.Screen, level: Int) {
        state.update { screen(mode, level) }
        surface?.applyScreen(mode, level)
    }

    private fun wake(): CommandResult {
        setScreen(KioskState.Screen.ON, 0)
        val started = bringToFront()
        val body = JSONObject()
        if (!started) body.put("warning", "no permission to start from the background: grant 'display over other apps'")
        return CommandResult.ok(body)
    }

    /**
     * Brings the dashboard to the front. Android 10+ only allows this from the background when the
     * app may draw over other apps (or already has a visible window), so report whether it can.
     */
    fun bringToFront(): Boolean {
        val intent = Intent(context, KioskActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        return try {
            context.startActivity(intent)
            state.dashboardVisible || AndroidSettings.canDrawOverlays(context)
        } catch (e: Exception) {
            AppLog.w(TAG, "Can't start the dashboard", e)
            false
        }
    }

    private fun pointer(args: JSONObject): CommandResult {
        val enabled = if (args.has("enabled")) args.optBoolean("enabled") else !state.pointerEnabled
        val current = surface ?: return CommandResult.error(409, "the dashboard isn't open")
        current.setPointer(enabled)
        return CommandResult.ok(JSONObject().put("pointer", enabled))
    }

    private fun notify(args: JSONObject): CommandResult {
        val popup = Popup.fromJson(args) ?: return CommandResult.error(400, "a title or message is required")
        return when (popups.show(popup)) {
            PopupManager.Shown.OVER_EVERYTHING -> CommandResult.ok(JSONObject().put("id", popup.id))
            PopupManager.Shown.IN_DASHBOARD -> CommandResult.ok(
                JSONObject().put("id", popup.id)
                    .put("warning", "shown only while the dashboard is open: grant 'display over other apps'")
            )
            PopupManager.Shown.NOWHERE -> CommandResult.error(
                409, "can't show pop-ups over other apps: grant 'display over other apps' (see the install guide)"
            )
        }
    }

    private fun launch(args: JSONObject): CommandResult {
        val pkg = args.optString("package").trim()
        if (pkg.isEmpty()) return CommandResult.error(400, "package is required")
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            ?: return CommandResult.error(404, "no launchable app with package $pkg")
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            CommandResult.ok()
        } catch (e: Exception) {
            CommandResult.error(500, "can't start $pkg: ${e.message}")
        }
    }

    private fun volume(args: JSONObject): CommandResult {
        if (!args.has("level")) return CommandResult.error(400, "level (0-100) is required")
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val level = args.optInt("level").coerceIn(0, 100)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(level / 100f * max), 0)
        return CommandResult.ok()
    }

    private fun speak(args: JSONObject): CommandResult {
        val message = args.optString("message").trim()
        if (message.isEmpty()) return CommandResult.error(400, "message is required")
        val language = args.optString("language").ifEmpty { null }
        val engine = tts
        if (engine != null && ttsReady) {
            language?.let { engine.setLanguage(Locale.forLanguageTag(it)) }
            engine.speak(message, TextToSpeech.QUEUE_ADD, null, "say-${System.nanoTime()}")
            return CommandResult.ok()
        }
        if (engine == null) {
            // First use: start the engine and speak once it's ready.
            tts = TextToSpeech(context) { status ->
                main.post {
                    ttsReady = status == TextToSpeech.SUCCESS
                    if (ttsReady) {
                        language?.let { tts?.setLanguage(Locale.forLanguageTag(it)) }
                        tts?.speak(message, TextToSpeech.QUEUE_ADD, null, "say-first")
                    } else {
                        AppLog.w(TAG, "No text-to-speech engine available (status $status)")
                        state.update { error("No text-to-speech engine on this TV; send a sound URL instead") }
                        tts?.shutdown()
                        tts = null
                    }
                }
            }
            return CommandResult(202, JSONObject().put("ok", true).put("note", "starting the speech engine"))
        }
        return CommandResult.error(503, "the speech engine is still starting, try again")
    }

    private fun sound(args: JSONObject): CommandResult {
        val url = args.optString("url").trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return CommandResult.error(400, "url must start with http:// or https://")
        }
        stopSound()
        if (args.has("volume")) volume(JSONObject().put("level", args.optInt("volume")))
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setOnPreparedListener { it.start() }
        mp.setOnCompletionListener { if (player === it) stopSound() }
        mp.setOnErrorListener { p, what, extra ->
            state.update { error("Sound failed ($what/$extra): $url") }
            if (player === p) stopSound()
            true
        }
        return try {
            mp.setDataSource(url)
            mp.prepareAsync()
            CommandResult(202, JSONObject().put("ok", true))
        } catch (e: Exception) {
            stopSound()
            CommandResult.error(400, "can't play $url: ${e.message}")
        }
    }

    private fun stopSound() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }

    companion object {
        private const val TAG = "KioskController"

        /** Whether the settings ask for a dashboard at all. */
        fun hasStartUrl(settings: Settings) = settings[Keys.START_URL].isNotEmpty()
    }
}
