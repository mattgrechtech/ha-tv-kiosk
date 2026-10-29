package io.github.mattgrechtech.tvkiosk.web

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import io.github.mattgrechtech.tvkiosk.KioskApp
import io.github.mattgrechtech.tvkiosk.R
import io.github.mattgrechtech.tvkiosk.ui.Dialogs
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Signs the TV in to Home Assistant once, through HA's normal login page. The resulting refresh
 * token is kept encrypted on the TV; the dashboard then gets short-lived access tokens from it.
 * With trusted_networks this page is usually just "pick the user".
 */
class LoginActivity : Activity() {

    private lateinit var app: KioskApp
    private lateinit var webView: WebView
    private val state = UUID.randomUUID().toString()
    private var finishing = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as KioskApp
        val base = app.settings.haBaseUrl()
        if (base == null) {
            Toast.makeText(this, R.string.login_no_url, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    intercept(base, request.url.toString())

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (intercept(base, url)) view.stopLoading()
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(HaUrls.authorizeUrl(base, state))
    }

    /** Catches HA's redirect back to us; returns true when the WebView shouldn't load it. */
    private fun intercept(base: String, url: String): Boolean {
        if (!url.startsWith(HaUrls.redirectUri(base))) return false
        if (finishing) return true
        val code = HaUrls.authCodeFrom(url, base, state)
        if (code == null) {
            Dialogs.message(this, getString(R.string.login_failed), getString(R.string.login_bad_redirect))
            return true
        }
        finishing = true
        thread(name = "ha-login") {
            val error = try {
                app.auth.completeLogin(base, code)
                null
            } catch (e: HaAuth.Failure) {
                e.message
            }
            runOnUiThread {
                if (error == null) {
                    Toast.makeText(this, R.string.login_done, Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    finishing = false
                    Dialogs.message(this, getString(R.string.login_failed), error)
                }
            }
        }
        return true
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
