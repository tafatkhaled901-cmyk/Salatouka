package com.salatuk.adhan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/** Affiche l'interface Salatuk (le site) et lui donne accès aux alarmes Android. */
class MainActivity : Activity() {

    companion object {
        const val URL = "https://tafatkhaled901-cmyk.github.io/Salatouka/index.html"
        private const val RC_PERMS = 7
    }

    private lateinit var web: WebView
    private var pendingGeo: Pair<String, GeolocationPermissions.Callback>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdhanService.ensureChannel(this)

        web = WebView(this)
        web.setBackgroundColor(0xFF0A0F1E.toInt())
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setGeolocationEnabled(true)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.addJavascriptInterface(NativeBridge(applicationContext), "SalatukNative")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                // Liens externes (GitHub, WhatsApp…) → navigateur
                if (u.host != null && !u.host!!.endsWith("github.io")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                    return true
                }
                return false
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (hasLocation()) callback.invoke(origin, true, false)
                else {
                    pendingGeo = origin to callback
                    requestPermissions(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION), RC_PERMS)
                }
            }
        }

        if (savedInstanceState == null) web.loadUrl(URL) else web.restoreState(savedInstanceState)

        askPermissionsOnce()
        PrayerScheduler.scheduleNext(this)
    }

    private fun hasLocation() =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun askPermissionsOnce() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), RC_PERMS + 1)
        }
        val prefs = Prefs.get(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("askedBattery", false)) {
            prefs.edit().putBoolean("askedBattery", true).apply()
            AlertDialog.Builder(this)
                .setTitle("Adhan à l'heure, même écran éteint")
                .setMessage("Pour que l'Adhan sonne toujours, autorisez Salatuk à fonctionner sans restriction de batterie.")
                .setPositiveButton("Autoriser") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName")))
                    } catch (_: Exception) {}
                }
                .setNegativeButton("Plus tard", null)
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_PERMS) {
            pendingGeo?.let { (origin, cb) -> cb.invoke(origin, hasLocation(), false) }
            pendingGeo = null
        }
    }

    override fun onResume() {
        super.onResume()
        PrayerScheduler.scheduleNext(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }
}
