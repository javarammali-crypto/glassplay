package com.glassplay.app.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.glassplay.app.Graph
import com.glassplay.app.MainActivity
import com.glassplay.app.data.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while torrents download in the background. Direct-link downloads
 * don't need it: Android's DownloadManager runs those by itself.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(Graph.downloads.items.value.filter(::isRunningTorrent)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        if (watcher == null) {
            watcher = scope.launch {
                Graph.downloads.items.collect { list ->
                    val running = list.filter(::isRunningTorrent)
                    if (running.isEmpty()) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        manager.notify(NOTIFICATION_ID, notification(running))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Android 15 caps background data-sync work; stop cleanly instead of being killed. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun isRunningTorrent(item: DownloadItem) = item.isTorrent && item.isActive

    private fun notification(running: List<DownloadItem>): android.app.Notification {
        val total = running.sumOf { it.totalBytes }
        val done = running.sumOf { it.doneBytes }
        val percent = if (total > 0) (done * 100 / total).toInt() else 0
        val title = if (running.size == 1) {
            listOfNotNull(running[0].name, running[0].episodeLabel).joinToString(" · ")
        } else {
            "Downloading ${running.size} videos"
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.ifBlank { "Downloading" })
            .setContentText(if (total > 0) "$percent% downloaded" else "Getting ready…")
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (e: Exception) {
                // Android refuses while the app is in the background; downloads still run while it's open.
            }
        }
    }
}
