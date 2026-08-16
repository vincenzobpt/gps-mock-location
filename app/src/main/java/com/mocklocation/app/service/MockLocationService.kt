package com.mocklocation.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.mocklocation.app.MainActivity
import com.mocklocation.app.MockLocationApp
import com.mocklocation.app.R
import com.mocklocation.app.model.SimulationState
import com.mocklocation.app.model.SimulationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Keeps the process alive (and visible, as Android requires) while fixes are
 * being injected. Without it the run dies as soon as the user leaves the app —
 * which is precisely when a mock-location app needs to be running.
 *
 * The service does not own the simulation; it observes [SimulationEngine] and
 * mirrors it into a live notification.
 */
class MockLocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine by lazy { MockLocationApp.engineOf(this) }
    private var notifyJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                engine.pause()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                engine.stop()
                return START_NOT_STICKY
            }
        }

        startInForeground(engine.state.value)

        if (notifyJob?.isActive != true) {
            notifyJob = scope.launch {
                // 1 Hz is plenty for a notification; the engine ticks up to 10×.
                while (isActive) {
                    val state = engine.state.value
                    if (state.status != SimulationStatus.PLAYING) {
                        stopSelf()
                        break
                    }
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
                    delay(1_000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stopForegroundCompat()
        super.onDestroy()
    }

    /** The system killed us — never leave a fake provider installed behind. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        engine.stop()
        super.onTaskRemoved(rootIntent)
    }

    // ── Notification ────────────────────────────────────────────

    private fun startInForeground(state: SimulationState) {
        val notification = buildNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: SimulationState): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progress = (state.progress * 100).roundToInt()
        val speed = state.speedKmh.roundToInt()
        val remaining = formatDistance(state.remainingMeters)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setContentTitle("Simulating · $speed km/h")
            .setContentText("$progress% · $remaining left · ${state.activeProviders.joinToString("/")}")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action("Pause", ACTION_PAUSE, 1))
            .addAction(action("Stop", ACTION_STOP, 2))
            .build()
    }

    private fun action(title: String, actionName: String, requestCode: Int): Notification.Action {
        val pending = PendingIntent.getService(
            this, requestCode,
            Intent(this, MockLocationService::class.java).setAction(actionName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(null as android.graphics.drawable.Icon?, title, pending).build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_simulation),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the running GPS simulation"
            setShowBadge(false)
            enableVibration(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun stopForegroundCompat() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    private fun formatDistance(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m"
        else String.format("%.1f km", meters / 1000.0)

    companion object {
        const val ACTION_START = "com.mocklocation.app.action.START"
        const val ACTION_PAUSE = "com.mocklocation.app.action.PAUSE"
        const val ACTION_STOP = "com.mocklocation.app.action.STOP"

        private const val CHANNEL_ID = "gps_simulation"
        private const val NOTIFICATION_ID = 4711
    }
}
