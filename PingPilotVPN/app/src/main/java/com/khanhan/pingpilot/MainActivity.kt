package com.khanhan.pingpilot

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.khanhan.pingpilot.ui.PingPilotApp
import com.khanhan.pingpilot.ui.theme.PingPilotTheme
import com.khanhan.pingpilot.vpn.LocalVpnService
import com.khanhan.pingpilot.vpn.VpnStatusStore

class MainActivity : ComponentActivity() {

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
    ) { /* The VPN can still run when notification permission is denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionWhenNeeded()

        setContent {
            PingPilotTheme {
                PingPilotApp(
                    onConnectRequested = ::requestVpnPermissionAndConnect,
                    onDisconnectRequested = ::stopLocalVpn
                )
            }
        }
    }

    private fun requestNotificationPermissionWhenNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
