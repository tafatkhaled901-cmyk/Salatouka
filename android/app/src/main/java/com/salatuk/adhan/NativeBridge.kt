package com.salatuk.adhan

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Fonctions appelables depuis la page web : window.SalatukNative.xxx() */
class NativeBridge(private val ctx: Context) {

    /** La page envoie la ville, la méthode et la voix choisies. */
    @JavascriptInterface
    fun configure(json: String) {
        try {
            val o = JSONObject(json)
            val p = Prefs.get(ctx)
            val oldKey = Prefs.locationKey(ctx)
            p.edit()
                .putString("lat", o.optDouble("lat").toString())
                .putString("lon", o.optDouble("lon").toString())
                .putInt("method", o.optInt("method", 3))
                .putString("voice", o.optString("voice", "makkah"))
                .putBoolean("fajrSpecial", o.optBoolean("fajrSpecial", true))
                .putString("city", o.optString("city", ""))
                .apply()
            val changed = oldKey != Prefs.locationKey(ctx)
            Thread {
                if (changed || PrayerScheduler.events(ctx).isEmpty()) PrayerScheduler.refresh(ctx)
                PrayerScheduler.scheduleNext(ctx)
            }.start()
        } catch (e: Exception) {
            android.util.Log.e("SalatukBridge", "configure", e)
        }
    }

    @JavascriptInterface
    fun test(type: String, delaySec: Int) {
        PrayerScheduler.scheduleTest(ctx, type, if (delaySec < 5) 15 else delaySec)
    }

    @JavascriptInterface
    fun stop() = AdhanService.stop(ctx)

    @JavascriptInterface
    fun isPlaying(): Boolean = AdhanService.isPlaying

    @JavascriptInterface
    fun version(): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    /** État pour l'écran Diagnostic. */
    @JavascriptInterface
    fun status(): String {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val next = Prefs.get(ctx).getString("next", "") ?: ""
        val evs = PrayerScheduler.events(ctx)
        val daysAhead = if (evs.isEmpty()) 0 else
            ((evs.last().at - System.currentTimeMillis()) / 86_400_000L).toInt()
        return JSONObject()
            .put("version", version())
            .put("exactAlarms", PrayerScheduler.canExact(ctx))
            .put("notifications", nm.areNotificationsEnabled())
            .put("battery", pm.isIgnoringBatteryOptimizations(ctx.packageName))
            .put("next", next.substringBefore("|"))
            .put("daysAhead", daysAhead)
            .put("city", Prefs.city(ctx))
            .toString()
    }

    @JavascriptInterface
    fun openBatterySettings() {
        try {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    @JavascriptInterface
    fun openAlarmSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else openAppSettings()
        } catch (e: Exception) { openAppSettings() }
    }

    @JavascriptInterface
    fun openAppSettings() {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
