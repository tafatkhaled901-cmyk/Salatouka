package com.salatuk.adhan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** L'alarme système sonne : on lance l'Adhan puis on programme la prière suivante. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(PrayerScheduler.EXTRA_ID) ?: return
        val prefs = Prefs.get(context)
        // Ne jamais jouer deux fois la même prière
        if (prefs.getString("lastFired", null) == id) {
            if (!id.startsWith("TEST-")) PrayerScheduler.scheduleNext(context)
            return
        }
        prefs.edit().putString("lastFired", id).apply()

        val svc = Intent(context, AdhanService::class.java)
            .setAction(AdhanService.ACTION_PLAY)
            .putExtras(intent)
        try {
            context.startForegroundService(svc)
        } catch (e: Exception) {
            android.util.Log.e("SalatukAlarm", "start service failed", e)
        }
        if (!id.startsWith("TEST-")) PrayerScheduler.scheduleNext(context)
    }
}
