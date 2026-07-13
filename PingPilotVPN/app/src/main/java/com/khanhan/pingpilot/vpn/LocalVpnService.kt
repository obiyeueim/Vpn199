package com.khanhan.pingpilot.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.khanhan.pingpilot.MainActivity
import com.khanhan.pingpilot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocalVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelInterface: ParcelFileDescriptor? = null
    private var connectionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
            else -> stopSelf()
        }
        return Service.START_NOT_STICKY
    }

    private fun connect() {
        if (tunnelInterface != null || connectionJob?.isActive == true) return

        VpnStatusStore.setConnecting()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )

        connectionJob = serviceScope.launch {
            runCatching {
                val builder = Builder()
                    .setSession(getString(R.string.app_name))
                    .setMtu(1500)
                    .addAddress(DEMO_ADDRESS, 32)
                    // This route is reserved for benchmarks/testing. Ordinary Internet
                    // traffic stays on the device's original Wi-Fi or mobile network.
                    .addRoute(DEMO_ROUTE, DEMO_ROUTE_PREFIX)
                    .allowBypass()

                // Explicit split tunneling: the game packages below never enter this
                // VPN interface, even if more routes are added in a future build.
                applyGameBypass(builder)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }

                builder.establish()
                    ?: error("Android không thể tạo giao diện VPN.")
            }.onSuccess { descriptor ->
                tunnelInterface = descriptor
                VpnStatusStore.setConnected(SystemClock.elapsedRealtime())
            }.onFailure { throwable ->
                tunnelInterface?.closeQuietly()
                tunnelInterface = null
                VpnStatusStore.setError(throwable.message ?: "Không thể bật VPN.")
                stopForegroundAndSelf()
            }
        }
    }

    private fun applyGameBypass(builder: Builder) {
        GAME_BYPASS_PACKAGES.forEach { packageName ->
            try {
                builder.addDisallowedApplication(packageName)
                Log.i(TAG, "VPN bypass enabled for $packageName")
            } catch (_: PackageManager.NameNotFoundException) {
                // The package is not installed on this device. This is expected when
                // the user only has one Free Fire edition installed.
                Log.d(TAG, "Bypass package not installed: $packageName")
            }
        }
    }

    private fun disconnect() {
        VpnStatusStore.setDisconnecting()
        connectionJob?.cancel()
        connectionJob = null
        tunnelInterface?.closeQuietly()
        tunnelInterface = null
        VpnStatusStore.setDisconnected()
        stopForegroundAndSelf()
    }

    override fun onRevoke() {
        disconnect()
        super.onRevoke()
    }

    override fun onDestroy() {
        connectionJob?.cancel()
        tunnelInterface?.closeQuietly()
        tunnelInterface = null
        serviceScope.cancel()
        VpnStatusStore.setDisconnected()
        super.onDestroy()
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.vpn_notification_title))
        .setContentText(getString(R.string.vpn_notification_text))
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            0,
            getString(R.string.vpn_disconnect),
            PendingIntent.getService(
                this,
                1,
                Intent(this, LocalVpnService::class.java).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.vpn_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ParcelFileDescriptor.closeQuietly() {
        runCatching { close() }
    }

    companion object {
        const val ACTION_CONNECT = "com.khanhan.pingpilot.action.CONNECT"
        const val ACTION_DISCONNECT = "com.khanhan.pingpilot.action.DISCONNECT"

        private const val TAG = "PingPilotVPN"
        private const val CHANNEL_ID = "ping_pilot_vpn"
        private const val NOTIFICATION_ID = 1001
        private const val DEMO_ADDRESS = "10.66.0.2"
        private const val DEMO_ROUTE = "198.18.0.0"
        private const val DEMO_ROUTE_PREFIX = 15

        private val GAME_BYPASS_PACKAGES = listOf(
            "com.dts.freefireth",
            "com.dts.freefiremax"
        )
    }
}
