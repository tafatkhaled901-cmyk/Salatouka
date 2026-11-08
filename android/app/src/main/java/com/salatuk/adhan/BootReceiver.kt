package com.salatuk.adhan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Après un redémarrage, un changement d'heure ou une mise à jour : reprogrammer les alarmes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PrayerScheduler.scheduleNext(context)
    }
}
