package dev.vereda.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.vereda.app.MainActivity
import dev.vereda.app.R
import dev.vereda.app.VeredaApp
import dev.vereda.app.data.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MeshService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as VeredaApp).graph.repository.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val repository = (application as VeredaApp).graph.repository
        try {
            startInForeground(notification(getString(R.string.mesh_searching)))
            repository.ensureStarted()
        } catch (error: SecurityException) {
            repository.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        watch?.cancel()
        watch = scope.launch {
            combine(repository.linkCount, repository.phase) { links, _ -> links }.collect { links ->
                val text = when (links) {
                    0 -> getString(R.string.mesh_searching)
                    1 -> getString(R.string.radio_one)
                    else -> getString(R.string.radio_many, links)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, notification(text))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun serviceType(): Int {
        val reach = (application as VeredaApp).graph.profile.reach.value
        return if (reach == Profile.REACH_INTERNET) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
    }

    private fun startInForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            serviceType(),
        )
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.mesh_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.channel_keeps_radio)
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MeshService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vereda)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, getString(R.string.mesh_stop), stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "dev.vereda.app.STOP"
        private const val CHANNEL_ID = "vereda.mesh"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MeshService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MeshService::class.java).setAction(ACTION_STOP))
        }
    }
}
