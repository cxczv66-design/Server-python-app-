package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.model.LogLevel
import com.example.model.ServerStatus
import com.example.server.BridgeHttpServer
import com.example.server.BridgeStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ServerForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var httpServer: BridgeHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var statusObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                BridgeStateHolder.appendLog(LogLevel.INFO, "Service", "Stop requested from notification/UI")
                stopServerAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val port = intent?.getIntExtra(EXTRA_PORT, BridgeStateHolder.activePort.value)
                    ?: BridgeStateHolder.activePort.value
                startServer(port)
                return START_STICKY
            }
            else -> return START_STICKY
        }
    }

    private fun startServer(port: Int) {
        if (httpServer != null && BridgeStateHolder.serverStatus.value == ServerStatus.RUNNING) {
            return
        }

        BridgeStateHolder.setActivePort(port)
        BridgeStateHolder.setStatus(ServerStatus.STARTING)

        val initialNotification = buildNotification(port = port, wsClients = 0)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, initialNotification, serviceType)

        acquireWakeLock()

        serviceScope.launch {
            runCatching {
                httpServer?.stop()
                val server = BridgeHttpServer(port)
                server.start()
                httpServer = server

                BridgeStateHolder.onBroadcastRequested = { payload ->
                    server.broadcast(payload)
                }
                BridgeStateHolder.setStatus(ServerStatus.RUNNING)
                BridgeStateHolder.appendLog(
                    LogLevel.SUCCESS,
                    "KtorCIO",
                    "HTTP & WebSocket server listening on 0.0.0.0:$port",
                )
                observeStatusForNotification()
            }.onFailure { throwable ->
                val msg = throwable.message ?: "Failed to bind port $port"
                BridgeStateHolder.setStatus(ServerStatus.ERROR, msg)
                BridgeStateHolder.appendLog(LogLevel.ERROR, "KtorCIO", "Start failed: $msg")
                stopServerAndSelf()
            }
        }
    }

    private fun observeStatusForNotification() {
        statusObserverJob?.cancel()
        statusObserverJob = serviceScope.launch {
            combine(
                BridgeStateHolder.activePort,
                BridgeStateHolder.activeWsClients,
                BridgeStateHolder.serverStatus,
            ) { port, wsClients, status ->
                Triple(port, wsClients, status)
            }.collect { (port, wsClients, status) ->
                if (status == ServerStatus.RUNNING) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, buildNotification(port, wsClients))
                }
            }
        }
    }

    private fun stopServerAndSelf() {
        statusObserverJob?.cancel()
        BridgeStateHolder.onBroadcastRequested = null
        httpServer?.stop()
        httpServer = null
        BridgeStateHolder.setStatus(ServerStatus.STOPPED)
        BridgeStateHolder.appendLog(LogLevel.INFO, "KtorCIO", "Server stopped")
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScriptBridge::ServerWakeLock").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // 12h safety cap
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(port: Int, wsClients: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ServerForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val statusLine = "Listening on :$port • WS clients: $wsClients"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusLine)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.action_stop_server),
                stopIntent,
            )
            .build()
    }

    override fun onDestroy() {
        statusObserverJob?.cancel()
        BridgeStateHolder.onBroadcastRequested = null
        httpServer?.stop()
        httpServer = null
        if (BridgeStateHolder.serverStatus.value == ServerStatus.RUNNING) {
            BridgeStateHolder.setStatus(ServerStatus.STOPPED)
        }
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_SERVER"
        const val ACTION_STOP = "com.example.action.STOP_SERVER"
        const val EXTRA_PORT = "extra_port"
        private const val CHANNEL_ID = "bridge_server_channel"
        private const val NOTIFICATION_ID = 4201

        fun startIntent(context: Context, port: Int): Intent =
            Intent(context, ServerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, ServerForegroundService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
