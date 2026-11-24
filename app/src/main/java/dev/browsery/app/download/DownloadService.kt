package dev.browsery.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.browsery.app.BrowseryApp
import dev.browsery.app.MainActivity
import dev.browsery.app.R

/** Service latar depan (dataSync) yang hidup hanya selama ada unduhan aktif/antre. */
class DownloadService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (refresh()) handler.postDelayed(this, 1000)
        }
    }

    private val manager: DownloadManager get() = (application as BrowseryApp).downloads

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.dl_notif_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this,
                ID,
                build(manager.summary()),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    /** Android 15+ membatasi service dataSync (~6 jam): jeda unduhan agar bisa dilanjutkan, lalu berhenti. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        manager.pauseAll()
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun refresh(): Boolean {
        val s = manager.summary()
        if (s.count == 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return false
        }
        getSystemService(NotificationManager::class.java).notify(ID, build(s))
        return true
    }

    private fun build(s: Summary): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.dl_notif_title, maxOf(s.count, 1)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (s.total > 0) {
            val pct = (s.downloaded * 100 / s.total).toInt().coerceIn(0, 100)
            b.setContentText(getString(R.string.dl_notif_text, Format.bytes(s.downloaded), Format.bytes(s.total)))
            b.setProgress(100, pct, false)
        } else {
            b.setContentText(Format.bytes(s.downloaded))
            b.setProgress(0, 0, true)
        }
        return b.build()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val ID = 1001
    }
}
