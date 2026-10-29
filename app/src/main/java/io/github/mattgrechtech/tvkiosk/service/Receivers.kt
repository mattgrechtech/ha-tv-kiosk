package io.github.mattgrechtech.tvkiosk.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import io.github.mattgrechtech.tvkiosk.KioskApp
import io.github.mattgrechtech.tvkiosk.settings.Keys
import io.github.mattgrechtech.tvkiosk.settings.SettingsJson
import io.github.mattgrechtech.tvkiosk.web.KioskActivity
import org.json.JSONObject
import java.io.File

/** Starts the service after boot or an app update, and the dashboard too when "Start at boot" is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as KioskApp
        KioskService.start(context)
        if (action == Intent.ACTION_BOOT_COMPLETED && app.settings[Keys.BOOT_START]) {
            try {
                // Allowed from the background only with "display over other apps".
                context.startActivity(Intent(context, KioskActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Log.w("BootReceiver", "Can't open the dashboard at boot", e)
            }
        }
    }
}

/**
 * Set-up over adb. The receiver requires android.permission.DUMP, which only the shell (adb) and
 * the system hold, so other apps on the TV can't use it. Results come back as the broadcast's
 * data, which `adb shell am broadcast` prints.
 *
 *   adb shell am broadcast -n <package>/io.github.mattgrechtech.tvkiosk.service.ProvisionReceiver \
 *       -a io.github.mattgrechtech.tvkiosk.CONFIGURE --es json '{"start_url":"http://..."}'
 *
 * Actions: CONFIGURE (extra `json`), EXPORT (extra `include_secrets` boolean), GET_TOKEN,
 * COMMAND (extras `command` and optional `json`). Instead of `json`, `json_b64` takes the JSON
 * base64-encoded, which survives any shell's quoting (Windows PowerShell 5.1 strips quotes).
 */
class ProvisionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as KioskApp
        val result: JSONObject = try {
            when (intent.action) {
                ACTION_CONFIGURE -> {
                    val json = jsonExtra(intent)
                    val outcome = SettingsJson.import(app.settings, json).toJson()
                    KioskService.start(context)
                    outcome.put("ok", true)
                }
                ACTION_EXPORT -> {
                    val includeSecrets = intent.getBooleanExtra("include_secrets", false)
                    val json = SettingsJson.export(app.settings, includeSecrets)
                    File(context.getExternalFilesDir(null), "settings.json").writeText(json.toString(2))
                    JSONObject().put("ok", true).put("settings", json)
                }
                ACTION_GET_TOKEN -> JSONObject().put("ok", true).put("token", app.settings[Keys.API_TOKEN])
                ACTION_COMMAND -> {
                    val command = intent.getStringExtra("command") ?: throw IllegalArgumentException("command extra required")
                    val args = jsonExtra(intent)
                    app.controller.execute(command, args).body
                }
                else -> JSONObject().put("ok", false).put("error", "unknown action ${intent.action}")
            }
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message ?: e.javaClass.simpleName)
        }
        Log.i(TAG, "${intent.action}: ${if (intent.action == ACTION_GET_TOKEN) "(token)" else result.toString()}")
        if (isOrderedBroadcast) {
            resultCode = if (result.optBoolean("ok")) 0 else 1
            resultData = result.toString()
        }
    }

    private fun jsonExtra(intent: Intent): JSONObject {
        intent.getStringExtra("json_b64")?.let {
            return JSONObject(String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8))
        }
        return JSONObject(intent.getStringExtra("json") ?: "{}")
    }

    companion object {
        private const val TAG = "ProvisionReceiver"
        const val ACTION_CONFIGURE = "io.github.mattgrechtech.tvkiosk.CONFIGURE"
        const val ACTION_EXPORT = "io.github.mattgrechtech.tvkiosk.EXPORT"
        const val ACTION_GET_TOKEN = "io.github.mattgrechtech.tvkiosk.GET_TOKEN"
        const val ACTION_COMMAND = "io.github.mattgrechtech.tvkiosk.COMMAND"
    }
}
