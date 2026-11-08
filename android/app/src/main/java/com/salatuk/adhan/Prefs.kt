package com.salatuk.adhan

import android.content.Context
import android.content.SharedPreferences

/** Réglages envoyés par la page (ville, méthode, voix) + horaires en cache. */
object Prefs {
    private const val NAME = "salatuk"

    fun get(ctx: Context): SharedPreferences =
        ctx.applicationContext.createDeviceProtectedStorageContext()
            .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun lat(ctx: Context): Double? = get(ctx).getString("lat", null)?.toDoubleOrNull()
    fun lon(ctx: Context): Double? = get(ctx).getString("lon", null)?.toDoubleOrNull()
    fun method(ctx: Context): Int = get(ctx).getInt("method", 3)
    fun voice(ctx: Context): String = get(ctx).getString("voice", "makkah") ?: "makkah"
    fun fajrSpecial(ctx: Context): Boolean = get(ctx).getBoolean("fajrSpecial", true)
    fun city(ctx: Context): String = get(ctx).getString("city", "") ?: ""

    /** Clé qui identifie le lieu + la méthode : si elle change, le cache des horaires est jeté. */
    fun locationKey(ctx: Context): String {
        val la = lat(ctx) ?: return ""
        val lo = lon(ctx) ?: return ""
        return String.format(java.util.Locale.US, "%.3f_%.3f_%d", la, lo, method(ctx))
    }
}
