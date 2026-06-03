package ai.ebbflow.baseline.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ai.ebbflow.baseline.app.R
import ai.ebbflow.baseline.app.bluetooth.Mw75Controller
import ai.ebbflow.baseline.app.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the MW75 EEG session alive while the app is in
 * use or backgrounded. Declared with foregroundServiceType="connectedDevice" in
 * the manifest. The UI starts/stops it via [start] / [stop]; it owns a single
 * [Mw75Controller] and stops itself when the session ends.
 */
class Mw75StreamingService : LifecycleService() {

    private var controller: Mw75Controller? = null
    private var sessionJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startStreaming()
            ACTION_STOP -> stopStreaming()
        }
        return START_NOT_STICKY
    }

    private fun startStreaming() {
        if (sessionJob?.isActive == true) return
        startInForeground()
        val ctrl = Mw75Controller(this, lifecycleScope).also { controller = it }
        sessionJob = lifecycleScope.launch {
            ctrl.start() // blocks until stopped or the link drops
            stopSelf()
        }
    }

    private fun stopStreaming() {
        controller?.stop() // start() then returns and the job calls stopSelf()
    }

    override fun onDestroy() {
        controller?.stop()
        super.onDestroy()
    }

    private fun startInForeground() {
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            },
        )
    }

    private fun buildNotification(): Notification {
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.streaming_notification_title))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(tap)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.streaming_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "eeg_streaming"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "ai.ebbflow.baseline.app.action.START"
        const val ACTION_STOP = "ai.ebbflow.baseline.app.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, Mw75StreamingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, Mw75StreamingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
