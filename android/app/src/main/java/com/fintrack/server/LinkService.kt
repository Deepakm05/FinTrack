package com.fintrack.server

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
import android.util.Log
import com.fintrack.data.Prefs
import com.fintrack.ui.MainActivity
import java.net.Inet4Address
import java.net.NetworkInterface

/** Keeps the PC link server running while the switch in the app is ON. Shows a notification. */
class LinkService : Service() {

    private var server: ApiServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val prefs = Prefs(this)
        val host = if (prefs.wifiMode) "0.0.0.0" else "127.0.0.1"
        startInForeground(if (prefs.wifiMode) "Wi-Fi: ${wifiAddress() ?: "no Wi-Fi"}:${Prefs.PORT}" else "USB only (adb forward)")

        server?.stop()
        server = try {
            ApiServer(this, host, Prefs.PORT).also { it.start(SOCKET_TIMEOUT_MS, false) }
        } catch (e: Exception) {
            Log.e("FinTrack", "Could not start PC link server", e)
            lastError = e.message
            stopSelf()
            return START_NOT_STICKY
        }
        lastError = null
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        running = false
        super.onDestroy()
    }

    private fun startInForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "PC link", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LinkService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("FinTrack PC link is ON")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL = "pc_link"
        private const val NOTIFICATION_ID = 1
        private const val SOCKET_TIMEOUT_MS = 30_000
        const val ACTION_STOP = "com.fintrack.STOP_LINK"

        @Volatile var running = false
            private set
        @Volatile var lastError: String? = null
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LinkService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LinkService::class.java))
        }

        /** Phone's Wi-Fi IPv4 address, e.g. 192.168.1.5. */
        fun wifiAddress(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedByDescending { it.name.startsWith("wlan") }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
