package com.khanhan.pingpilot

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.khanhan.pingpilot.qa.FloatingQaOverlayService
import com.khanhan.pingpilot.ui.PingPilotApp
import com.khanhan.pingpilot.ui.theme.PingPilotTheme
import com.khanhan.pingpilot.vpn.LocalVpnService
import com.khanhan.pingpilot.vpn.VpnStatusStore

class MainActivity : ComponentActivity() {

    private var overlayPermissionRequested = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startLocalVpn()
        } else {
            VpnStatusStore.setError("Bạn chưa cấp quyền tạo VPN.")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Foreground services can still run when notification permission is denied. */ }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startQaOverlayIfPermitted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionWhenNeeded()
        requestQaOverlayPermissionOrStart()

        setContent {
            PingPilotTheme {
                PingPilotApp(
                    onConnectRequested = ::requestVpnPermissionAndConnect,
                    onDisconnectRequested = ::stopLocalVpn
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startQaOverlayIfPermitted()
    }

    private fun requestNotificationPermissionWhenNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestQaOverlayPermissionOrStart() {
        if (Settings.canDrawOverlays(this)) {
            startQaOverlayIfPermitted()
            return
        }

        if (overlayPermissionRequested) {
            return
        }

        overlayPermissionRequested = true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun startQaOverlayIfPermitted() {
        if (!Settings.canDrawOverlays(this)) {
            return
        }

        val intent = Intent(this, FloatingQaOverlayService::class.java)
            .setAction(FloatingQaOverlayService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestVpnPermissionAndConnect() {
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent == null) {
            startLocalVpn()
        } else {
            vpnPermissionLauncher.launch(permissionIntent)
        }
    }

    private fun startLocalVpn() {
        val intent = Intent(this, LocalVpnService::class.java)
            .setAction(LocalVpnService.ACTION_CONNECT)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopLocalVpn() {
        val intent = Intent(this, LocalVpnService::class.java)
            .setAction(LocalVpnService.ACTION_DISCONNECT)
        startService(intent)
    }
}
