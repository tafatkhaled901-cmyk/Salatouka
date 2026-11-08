package com.salatuk.adhan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Calcule la prochaine prière à partir des horaires Aladhan (mis en cache pour 2 mois)
 * et programme UNE alarme système à l'heure exacte. Quand elle sonne, on programme la suivante.
 */
object PrayerScheduler {
    private const val TAG = "SalatukScheduler"
    const val EXTRA_ID = "id"
    const val EXTRA_KEY = "key"
    const val EXTRA_TYPE = "type"
    const val EXTRA_TIME = "time"
    private const val RC_PRAYER = 1001
    private const val RC_TEST = 1002

    val SALATS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    data class Ev(val id: String, val key: String, val at: Long, val time: String, val type: String)

    // ───────────── Horaires ─────────────

    private fun cacheName(ym: String) = "cal_$ym"

    private fun monthsToCover(): List<LocalDate> {
        val now = LocalDate.now()
        return listOf(now.withDayOfMonth(1), now.plusMonths(1).withDayOfMonth(1))
    }

    /** Télécharge les horaires du mois en cours et du suivant (appel réseau : jamais sur le thread principal). */
    fun refresh(ctx: Context): Boolean {
        val lat = Prefs.lat(ctx) ?: return false
        val lon = Prefs.lon(ctx) ?: return false
        val method = Prefs.method(ctx)
        val key = Prefs.locationKey(ctx)
        var ok = true
        for (m in monthsToCover()) {
            val ym = "%d_%02d".format(m.year, m.monthValue)
            val url = "https://api.aladhan.com/v1/calendar/${m.year}/${m.monthValue}" +
                "?latitude=$lat&longitude=$lon&method=$method"
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 20000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                if (json.optInt("code") != 200) { ok = false; continue }
                Prefs.get(ctx).edit()
                    .putString(cacheName(ym), body)
                    .putString(cacheName(ym) + "_key", key)
                    .apply()
            } catch (e: Exception) {
                Log.w(TAG, "refresh $ym failed", e)
                ok = false
            }
        }
        return ok
    }

    /** Toutes les prières connues (cache), triées. */
    fun events(ctx: Context): List<Ev> {
        val p = Prefs.get(ctx)
        val key = Prefs.locationKey(ctx)
        val voice = Prefs.voice(ctx)
        val fajrSpecial = Prefs.fajrSpecial(ctx)
        val out = ArrayList<Ev>()
        val dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        for (m in monthsToCover()) {
            val ym = "%d_%02d".format(m.year, m.monthValue)
            if (p.getString(cacheName(ym) + "_key", null) != key) continue
            val body = p.getString(cacheName(ym), null) ?: continue
            try {
                val data = JSONObject(body).getJSONArray("data")
                for (i in 0 until data.length()) {
                    val day = data.getJSONObject(i)
                    val timings = day.getJSONObject("timings")
                    val dateStr = day.getJSONObject("date").getJSONObject("gregorian").getString("date")
                    val tz = day.optJSONObject("meta")?.optString("timezone")
                    val zone = try { ZoneId.of(tz) } catch (e: Exception) { ZoneId.systemDefault() }
                    val date = LocalDate.parse(dateStr, dateFmt)
                    for (k in SALATS) {
                        val raw = timings.optString(k, "")
                        val hhmm = raw.substringBefore(" ").trim()
                        if (hhmm.length < 4) continue
                        val t = LocalTime.parse(if (hhmm.length == 4) "0$hhmm" else hhmm)
                        val at = ZonedDateTime.of(date, t, zone).toInstant().toEpochMilli()
                        val type = if (k == "Fajr" && fajrSpecial) "fajr" else voice
                        out.add(Ev("$k-${date}", k, at, hhmm, type))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "parse $ym failed", e)
            }
        }
        out.sortBy { it.at }
        return out
    }

    fun nextEvent(ctx: Context, afterMs: Long = System.currentTimeMillis() + 1000): Ev? =
        events(ctx).firstOrNull { it.at > afterMs }

    // ───────────── Alarmes ─────────────

    private fun alarmIntent(ctx: Context, ev: Ev, rc: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .putExtra(EXTRA_ID, ev.id)
            .putExtra(EXTRA_KEY, ev.key)
            .putExtra(EXTRA_TYPE, ev.type)
            .putExtra(EXTRA_TIME, ev.time)
        return PendingIntent.getBroadcast(ctx, rc, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun setExact(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // setAlarmClock = le plus fiable : ignoré par le mode Doze et l'économie de batterie
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(ctx)), pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Programme la prochaine prière. Télécharge les horaires si le cache est vide ou bientôt épuisé. */
    fun scheduleNext(ctx: Context) {
        val app = ctx.applicationContext
        val next = nextEvent(app)
        if (next != null) {
            setExact(app, next.at, alarmIntent(app, next, RC_PRAYER))
            Prefs.get(app).edit().putString("next", "${next.key} ${next.time}|${next.at}").apply()
            Log.i(TAG, "Next: ${next.id} type=${next.type}")
        }
        // Moins de 5 jours d'avance ou rien en cache → recharger en arrière-plan puis reprogrammer
        val last = events(app).lastOrNull()?.at ?: 0L
        if (next == null || last - System.currentTimeMillis() < 5L * 86_400_000L) {
            Thread {
                if (refresh(app)) {
                    val n2 = nextEvent(app)
                    if (n2 != null) {
                        setExact(app, n2.at, alarmIntent(app, n2, RC_PRAYER))
                        Prefs.get(app).edit().putString("next", "${n2.key} ${n2.time}|${n2.at}").apply()
                    }
                }
            }.start()
        }
    }

    /** Alarme de test dans [delaySec] secondes. */
    fun scheduleTest(ctx: Context, type: String, delaySec: Int) {
        val at = System.currentTimeMillis() + delaySec * 1000L
        val ev = Ev("TEST-$at", if (type == "fajr") "Fajr" else "TEST", at, "Test", type)
        setExact(ctx.applicationContext, at, alarmIntent(ctx.applicationContext, ev, RC_TEST))
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }
}
