package io.github.mattgrechtech.tvkiosk.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.mattgrechtech.tvkiosk.KioskApp
import io.github.mattgrechtech.tvkiosk.R
import io.github.mattgrechtech.tvkiosk.control.KioskState
import io.github.mattgrechtech.tvkiosk.settings.Key
import io.github.mattgrechtech.tvkiosk.settings.Keys
import io.github.mattgrechtech.tvkiosk.settings.SettingsJson
import io.github.mattgrechtech.tvkiosk.web.LoginActivity
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * Settings for a remote: one scrolling list of fields, switches and buttons, saved together.
 * Secret fields start empty; leaving one empty keeps the stored value.
 */
class SettingsActivity : Activity() {

    private lateinit var app: KioskApp
    private lateinit var list: LinearLayout
    private val readers = mutableListOf<(JSONObject) -> Unit>()
    private var dirty = false
    private lateinit var startUrlField: EditText
    private lateinit var haUrlField: EditText
    private lateinit var authStatus: TextView
    private lateinit var signInButton: Button
    private lateinit var signOutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as KioskApp

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(64), dp(32), dp(64), dp(48))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(TvUi.BACKGROUND)
            addView(list)
        }
        setContentView(scroll)
        build()
    }

    private fun build() {
        heading(getString(R.string.app_name) + " " + getString(R.string.settings_title))
        buttonRow(
            getString(R.string.settings_save) to { save(closeAfter = true) },
            getString(R.string.settings_close) to { closeAsking() },
        )

        section(R.string.section_dashboard)
        startUrlField = text(Keys.START_URL, R.string.label_start_url, hint = "http://homeassistant.local:8123/lovelace/0", uri = true)
        haUrlField = text(Keys.HA_URL, R.string.label_ha_url, hint = getString(R.string.hint_ha_url), uri = true)

        section(R.string.section_sign_in)
        authStatus = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, dp(4), 0, dp(4))
        }
        list.addView(authStatus, fullWidth())
        signInButton = button(getString(R.string.settings_sign_in), primary = true) { signIn() }
        signOutButton = button(getString(R.string.settings_sign_out)) { signOut() }
        button(getString(R.string.settings_sign_in_options)) { signInOptions() }
        updateAuthStatus()

        section(R.string.section_display)
        switch(Keys.HA_KIOSK_MODE, R.string.label_ha_kiosk_mode)
        switch(Keys.KEEP_SCREEN_ON, R.string.label_keep_screen_on)
        switch(Keys.BOOT_START, R.string.label_boot_start)
        switch(Keys.POINTER_DEFAULT, R.string.label_pointer_default)

        section(R.string.section_recovery)
        switch(Keys.RELOAD_ON_NETWORK, R.string.label_reload_on_network)
        number(Keys.RELOAD_ON_DISCONNECT_SECONDS, R.string.label_reload_on_disconnect)
        number(Keys.NIGHTLY_RELOAD_HOUR, R.string.label_nightly_reload)

        section(R.string.section_api)
        switch(Keys.API_ENABLED, R.string.label_api_enabled)
        number(Keys.API_PORT, R.string.label_api_port)
        buttonRow(
            getString(R.string.settings_show_token) to { showToken() },
            getString(R.string.settings_new_token) to { newToken() },
        )
        switch(Keys.TVOVERLAY_COMPAT, R.string.label_tvoverlay_compat)
        number(Keys.TVOVERLAY_PORT, R.string.label_tvoverlay_port)

        section(R.string.section_mqtt)
        switch(Keys.MQTT_ENABLED, R.string.label_mqtt_enabled)
        text(Keys.MQTT_HOST, R.string.label_mqtt_host, hint = "192.168.1.50")
        number(Keys.MQTT_PORT, R.string.label_mqtt_port)
        switch(Keys.MQTT_TLS, R.string.label_mqtt_tls)
        text(Keys.MQTT_USER, R.string.label_mqtt_user)
        secret(Keys.MQTT_PASSWORD, R.string.label_mqtt_password)
        text(Keys.MQTT_BASE_TOPIC, R.string.label_mqtt_base_topic)
        text(Keys.MQTT_DISCOVERY_PREFIX, R.string.label_mqtt_discovery_prefix)

        section(R.string.section_device)
        text(Keys.DEVICE_NAME, R.string.label_device_name)
        secret(Keys.SETTINGS_PIN, R.string.label_settings_pin, numeric = true)
        buttonRow(
            getString(R.string.settings_overlay_permission) to { openOverlayPermission() },
            getString(R.string.settings_test_popup) to { testPopup() },
        )
        buttonRow(
            getString(R.string.settings_export) to { exportSettings() },
            getString(R.string.settings_import) to { importSettings() },
        )
        button(getString(R.string.settings_status)) { showStatus() }

        buttonRow(
            getString(R.string.settings_save) to { save(closeAfter = true) },
            getString(R.string.settings_close) to { closeAsking() },
        )
        list.getChildAt(1)?.requestFocus()
    }

    // ---- rows ------------------------------------------------------------------------------

    private fun heading(value: String) {
        list.addView(TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setPadding(0, 0, 0, dp(16))
        })
    }

    private fun section(label: Int) {
        list.addView(TextView(this).apply {
            setText(label)
            setTextColor(0xFF03A9F4.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(0, dp(28), 0, dp(8))
        })
    }

    private fun labelView(text: Int): TextView = TextView(this).apply {
        setText(text)
        setTextColor(0xFFBDBDBD.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(0, dp(10), 0, dp(2))
    }

    private fun edit(value: String, hintText: String?, inputType: Int): EditText = EditText(this).apply {
        setText(value)
        hint = hintText
        this.inputType = inputType
        setSingleLine(true)
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF757575.toInt())
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { dirty = true }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun text(key: Key<String>, label: Int, hint: String? = null, uri: Boolean = false): EditText {
        list.addView(labelView(label))
        val type = InputType.TYPE_CLASS_TEXT or (if (uri) InputType.TYPE_TEXT_VARIATION_URI else 0)
        val field = edit(app.settings[key], hint, type)
        list.addView(field, fullWidth())
        readers += { it.put(key.name, field.text.toString().trim()) }
        return field
    }

    private fun number(key: Key<Int>, label: Int) {
        list.addView(labelView(label))
        val field = edit(app.settings[key].toString(), null, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
        list.addView(field, fullWidth())
        readers += { it.put(key.name, field.text.toString().trim()) }
    }

    private fun secret(key: Key<String>, label: Int, numeric: Boolean = false) {
        list.addView(labelView(label))
        val isSet = app.settings[key].isNotEmpty()
        val type = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val field = edit("", getString(if (isSet) R.string.hint_secret_set else R.string.hint_secret_unset), type)
        list.addView(field, fullWidth())
        readers += {
            val value = field.text.toString().trim()
            // Empty keeps the stored value; "-" clears it.
            if (value.isNotEmpty()) it.put(key.name, if (value == "-") "" else value)
        }
    }

    private fun switch(key: Key<Boolean>, label: Int) {
        val view = Switch(this).apply {
            setText(label)
            isChecked = app.settings[key]
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, _ -> dirty = true }
        }
        list.addView(view, fullWidth())
        readers += { it.put(key.name, view.isChecked) }
    }

    private fun button(text: String, primary: Boolean = false, onClick: () -> Unit): Button {
        val view = TvUi.button(this, text, primary, onClick)
        list.addView(view, fullWidth().apply { topMargin = dp(8) })
        return view
    }

    private fun buttonRow(vararg buttons: Pair<String, () -> Unit>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        for ((text, onClick) in buttons) {
            row.addView(TvUi.button(this, text, onClick = onClick),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
        }
        list.addView(row, fullWidth().apply { topMargin = dp(8) })
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    // ---- actions ---------------------------------------------------------------------------

    private fun collect(): JSONObject {
        val json = JSONObject()
        readers.forEach { it(json) }
        return json
    }

    /** Saves every field; returns false and explains when a value is rejected. */
    private fun save(closeAfter: Boolean): Boolean {
        val result = SettingsJson.import(app.settings, collect())
        if (result.rejected.isNotEmpty()) {
            val lines = result.rejected.entries.joinToString("\n") { (k, v) -> "$k: $v" }
            Dialogs.message(this, getString(R.string.settings_not_saved), lines)
            return false
        }
        dirty = false
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        if (closeAfter) finish()
        return true
    }

    private fun closeAsking() {
        if (!dirty) {
            finish()
            return
        }
        Dialogs.list(this, getString(R.string.settings_unsaved), listOf(
            getString(R.string.settings_save) to { save(closeAfter = true); Unit },
            getString(R.string.settings_discard) to { finish() },
        ))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        closeAsking()
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the sign-in screen: show the result straight away.
        if (::authStatus.isInitialized) updateAuthStatus()
    }

    // ---- sign-in -------------------------------------------------------------------------------

    /** The sign-in status in plain words, and only the buttons that make sense right now. */
    private fun updateAuthStatus() {
        val mode = app.settings[Keys.AUTH_MODE]
        val signedIn = mode == "login" && app.settings[Keys.HA_REFRESH_TOKEN].isNotEmpty()
        val hasToken = app.settings[Keys.HA_LONG_LIVED_TOKEN].isNotEmpty()
        val (text, colour) = when {
            signedIn -> getString(R.string.auth_status_signed_in) to TvUi.SUCCESS
            mode == "login" -> getString(R.string.auth_status_not_signed_in) to TvUi.WARNING
            mode == "token" && hasToken -> getString(R.string.auth_status_token) to TvUi.SUCCESS
            mode == "token" -> getString(R.string.auth_status_token_missing) to TvUi.WARNING
            else -> getString(R.string.auth_status_page) to TvUi.TEXT_SECONDARY
        }
        authStatus.text = text
        authStatus.setTextColor(colour)
        signInButton.text = getString(
            when {
                signedIn -> R.string.settings_sign_in_again
                mode == "login" -> R.string.settings_sign_in
                else -> R.string.settings_sign_in_instead
            }
        )
        signOutButton.visibility = if (signedIn) android.view.View.VISIBLE else android.view.View.GONE
    }

    /** Saves just the address fields (sign-in needs them), then opens HA's login page. */
    private fun signIn() {
        val urls = JSONObject()
            .put(Keys.START_URL.name, startUrlField.text.toString().trim())
            .put(Keys.HA_URL.name, haUrlField.text.toString().trim())
        val result = SettingsJson.import(app.settings, urls)
        if (result.rejected.isNotEmpty()) {
            Dialogs.message(this, getString(R.string.settings_not_saved),
                result.rejected.entries.joinToString("\n") { (k, v) -> "$k: $v" })
            return
        }
        if (app.settings.haBaseUrl() == null) {
            Dialogs.message(this, getString(R.string.settings_sign_in), getString(R.string.login_no_url))
            startUrlField.requestFocus()
            return
        }
        startActivity(Intent(this, LoginActivity::class.java))
    }

    private fun signOut() {
        Dialogs.confirm(this, getString(R.string.settings_sign_out), getString(R.string.sign_out_confirm)) {
            thread(name = "ha-sign-out") {
                app.auth.signOut()
                runOnUiThread { updateAuthStatus() }
            }
        }
    }

    private fun signInOptions() {
        Dialogs.list(this, getString(R.string.settings_sign_in_options), listOf(
            getString(R.string.auth_login) to { signIn() },
            getString(R.string.auth_token) to { askForToken() },
            getString(R.string.auth_none) to {
                app.settings[Keys.AUTH_MODE] = "none"
                updateAuthStatus()
            },
        ))
    }

    private fun askForToken() {
        Dialogs.input(this, getString(R.string.auth_token), getString(R.string.token_dialog_message), password = true) { value ->
            if (value.isEmpty()) return@input
            app.settings.edit {
                put(Keys.HA_LONG_LIVED_TOKEN, value)
                put(Keys.AUTH_MODE, "token")
            }
            updateAuthStatus()
            Dialogs.message(this, getString(R.string.auth_token), getString(R.string.token_saved))
        }
    }

    private fun apiAddress(): String {
        val ip = KioskState.localIpv4() ?: "<tv-ip>"
        return "http://$ip:${app.settings[Keys.API_PORT]}"
    }

    private fun showToken() {
        val token = app.settings[Keys.API_TOKEN]
        val content = JSONObject().put("url", apiAddress()).put("token", token).toString()
        Dialogs.qr(this, getString(R.string.settings_show_token), content,
            getString(R.string.token_caption, apiAddress(), token))
    }

    private fun newToken() {
        Dialogs.confirm(this, getString(R.string.settings_new_token), getString(R.string.new_token_confirm)) {
            app.settings[Keys.API_TOKEN] = KioskApp.newToken()
            showToken()
        }
    }

    private fun openOverlayPermission() {
        val intent = Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Many TVs have no screen for this permission.
            Dialogs.message(this, getString(R.string.settings_overlay_permission),
                getString(R.string.overlay_adb, packageName))
        }
    }

    private fun testPopup() {
        val result = app.controller.execute("notify", JSONObject()
            .put("title", getString(R.string.app_name))
            .put("message", getString(R.string.test_popup_message))
            .put("icon", "mdi:television")
            .put("duration", 8))
        if (!result.ok) Dialogs.message(this, getString(R.string.settings_test_popup), result.body.optString("error"))
    }

    private fun exportFile() = File(getExternalFilesDir(null), "settings.json")

    private fun exportSettings() {
        val file = exportFile()
        file.writeText(SettingsJson.export(app.settings, includeSecrets = false).toString(2))
        Dialogs.message(this, getString(R.string.settings_export), getString(R.string.export_done, file.absolutePath))
    }

    private fun importSettings() {
        val file = exportFile()
        if (!file.exists()) {
            Dialogs.message(this, getString(R.string.settings_import), getString(R.string.import_missing, file.absolutePath))
            return
        }
        val result = try {
            SettingsJson.import(app.settings, JSONObject(file.readText()))
        } catch (e: Exception) {
            Dialogs.message(this, getString(R.string.settings_import), e.message ?: "Unreadable file")
            return
        }
        Dialogs.message(this, getString(R.string.settings_import), result.toJson().toString(2))
        recreate()
    }

    private fun showStatus() {
        Dialogs.message(this, getString(R.string.settings_status), app.state.toJson(this).toString(2))
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)
}
