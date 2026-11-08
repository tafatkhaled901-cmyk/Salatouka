package com.salatuk.adhan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/** Joue l'Adhan (fichier MP3 inclus dans l'appli) avec une notification et un bouton « Arrêter ». */
class AdhanService : Service() {

    companion object {
        const val ACTION_PLAY = "com.salatuk.adhan.PLAY"
        const val ACTION_STOP = "com.salatuk.adhan.STOP"
        const val CHANNEL = "adhan_v1"
        private const val NOTIF_ID = 42
        @Volatile var isPlaying = false
        @Volatile var currentId: String? = null

        val NAMES = mapOf(
            "Fajr" to ("El Fajr" to "الفجر"),
            "Dhuhr" to ("El Dohr" to "الظهر"),
            "Asr" to ("El Asr" to "العصر"),
            "Maghrib" to ("El Maghreb" to "المغرب"),
            "Isha" to ("El Icha" to "العشاء"),
            "TEST" to ("Test Adhan" to "اختبار"),
        )

        fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "Adhan", NotificationManager.IMPORTANCE_HIGH)
                ch.description = "Appel à la prière"
                ch.setSound(null, null)          // le son est joué par l'appli elle-même
                ch.enableVibration(true)
                ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                ch.setBypassDnd(true)
                nm.createNotificationChannel(ch)
            }
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP))
        }
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAdhan()
            return START_NOT_STICKY
        }
        val key = intent?.getStringExtra(PrayerScheduler.EXTRA_KEY) ?: "TEST"
        val type = intent?.getStringExtra(PrayerScheduler.EXTRA_TYPE) ?: Prefs.voice(this)
        val time = intent?.getStringExtra(PrayerScheduler.EXTRA_TIME) ?: ""
        val id = intent?.getStringExtra(PrayerScheduler.EXTRA_ID) ?: "TEST"

        ensureChannel(this)
        val notif = buildNotification(key, time, id.startsWith("TEST-"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        currentId = id
        play(type)
        return START_NOT_STICKY
    }

    private fun rawFor(type: String): Int = when (type) {
        "fajr" -> R.raw.adhan_fajr
        "madinah" -> R.raw.adhan_madinah
        "egypt" -> R.raw.adhan_egypt
        else -> R.raw.adhan_makkah
    }

    private fun play(type: String) {
        releasePlayer()
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Salatuk:adhan").apply {
                acquire(10 * 60 * 1000L)
            }
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)          // volume « Alarme » : sonne même en silencieux
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setDataSource(this, Uri.parse("android.resource://$packageName/${rawFor(type)}"))
            mp.setOnCompletionListener { stopAdhan() }
            mp.setOnErrorListener { _, _, _ -> stopAdhan(); true }
            mp.prepare()
            mp.start()
            player = mp
            isPlaying = true
        } catch (e: Exception) {
            Log.e("SalatukAdhan", "play failed", e)
            stopAdhan()
        }
    }

    private fun buildNotification(key: String, time: String, isTest: Boolean): Notification {
        val (fr, ar) = NAMES[key] ?: (key to "")
        val title = if (isTest) "🔔 Test · $fr · $ar" else "🕌 $fr · $ar"
        val text = "حان وقت الصلاة" + if (time.isNotEmpty() && !isTest) " — $time" else ""

        val open = PendingIntent.getActivity(
            this, 10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 11,
            Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_adhan)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setDeleteIntent(stop)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "⏹ Arrêter l'Adhan", stop).build())
            .build()
    }

    private fun releasePlayer() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    private fun stopAdhan() {
        releasePlayer()
        isPlaying = false
        currentId = null
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        isPlaying = false
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        super.onDestroy()
    }
}
