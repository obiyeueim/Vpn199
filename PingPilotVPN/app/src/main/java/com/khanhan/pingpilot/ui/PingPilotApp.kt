package com.khanhan.pingpilot.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khanhan.pingpilot.network.PingMonitor
import com.khanhan.pingpilot.network.PingTargets
import com.khanhan.pingpilot.network.latencyLabel
import com.khanhan.pingpilot.vpn.VpnStatus
import com.khanhan.pingpilot.vpn.VpnStatusStore
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingPilotApp(
    onConnectRequested: () -> Unit,
    onDisconnectRequested: () -> Unit
) {
    val vpnUiState by VpnStatusStore.state.collectAsState()
    var selectedTargetIndex by rememberSaveable { mutableIntStateOf(0) }
    var threshold by rememberSaveable { mutableFloatStateOf(200f) }
    var latestPing by remember { mutableStateOf<Int?>(null) }
    var pingError by remember { mutableStateOf<String?>(null) }
    var samples by remember { mutableStateOf(emptyList<Int>()) }
    var nowElapsed by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    val selectedTarget = PingTargets.defaults[selectedTargetIndex]
    val isConnected = vpnUiState.status == VpnStatus.CONNECTED
    val isBusy = vpnUiState.status == VpnStatus.CONNECTING ||
        vpnUiState.status == VpnStatus.DISCONNECTING

    LaunchedEffect(selectedTargetIndex) {
        while (true) {
            val result = PingMonitor.measure(selectedTarget)
            latestPing = result.latencyMs
            pingError = result.error
            result.latencyMs?.let { value ->
                samples = (samples + value).takeLast(30)
            }
            delay(2_000)
        }
    }

    LaunchedEffect(isConnected) {
        while (isConnected) {
            nowElapsed = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PING PILOT",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.8.sp
                        )
                        Text(
                            text = "Local VPN demo & latency monitor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ConnectionCard(
                status = vpnUiState.status,
                isConnected = isConnected,
                isBusy = isBusy,
                errorMessage = vpnUiState.errorMessage,
                uptimeMs = vpnUiState.startedAtElapsedMs?.let { nowElapsed - it },
                onClick = {
                    if (isConnected) onDisconnectRequested() else onConnectRequested()
                }
            )

            MetricCard(
                icon = Icons.Rounded.Speed,
                title = "PING HIỆN TẠI",
                value = latestPing?.let { "$it ms" } ?: "--",
                subtitle = latencyLabel(latestPing)
            )

            MetricCard(
                icon = Icons.Rounded.Bolt,
                title = "NGƯỠNG CẢNH BÁO",
                value = "${threshold.toInt()} ms",
                subtitle = if ((latestPing ?: 0) <= threshold) {
                    "Trong ngưỡng"
                } else {
                    "Vượt ngưỡng"
                }
            )

            ServerCard(
                selectedTargetIndex = selectedTargetIndex,
                threshold = threshold,
                pingError = pingError,
                onTargetSelected = { index ->
                    selectedTargetIndex = index
                    samples = emptyList()
                },
                onThresholdChanged = { threshold = it }
            )

            HistoryCard(samples = samples)
            InfoCard()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ConnectionCard(
    status: VpnStatus,
    isConnected: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    uptimeMs: Long?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = onClick,
                enabled = !isBusy,
                modifier = Modifier.size(104.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = if (isConnected) "Tắt VPN" else "Bật VPN",
                    modifier = Modifier.size(42.dp)
                )
            }

            Text(
                text = when (status) {
                    VpnStatus.DISCONNECTED -> "CHƯA KẾT NỐI"
                    VpnStatus.CONNECTING -> "ĐANG KẾT NỐI…"
                    VpnStatus.CONNECTED -> "VPN DEMO ĐANG BẬT"
                    VpnStatus.DISCONNECTING -> "ĐANG NGẮT…"
                    VpnStatus.ERROR -> "CÓ LỖI"
                },
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )

            Text(
                text = when {
                    errorMessage != null -> errorMessage
                    isConnected -> "Thời gian chạy: ${formatDuration(uptimeMs ?: 0L)}"
                    else -> "Nhấn nút để cấp quyền và bật dịch vụ VPN cục bộ"
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = if (errorMessage != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServerCard(
    selectedTargetIndex: Int,
    threshold: Float,
    pingError: String?,
    onTargetSelected: (Int) -> Unit,
    onThresholdChanged: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudQueue, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("Máy chủ đo", fontWeight = FontWeight.Bold)
            }

            PingTargets.defaults.forEachIndexed { index, target ->
                val selected = index == selectedTargetIndex
                Button(
                    onClick = { onTargetSelected(index) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                ) {
                    Text("${target.name} · ${target.host}:${target.port}")
                }
            }

            Text(
                text = "Cảnh báo khi ping vượt ${threshold.toInt()} ms",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = threshold,
                onValueChange = onThresholdChanged,
                valueRange = 50f..300f,
                steps = 9
            )

            pingError?.let { error ->
                Text(
                    text = "Lần đo gần nhất thất bại: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(samples: List<Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("Lịch sử độ trễ", fontWeight = FontWeight.Bold)
            }

            Text(
                text = if (samples.isEmpty()) {
                    "Đang thu thập dữ liệu…"
                } else {
                    samples.takeLast(12).joinToString("  •  ") { "$it ms" }
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Đo bằng thời gian thiết lập kết nối TCP, cập nhật mỗi 2 giây.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null)
                Spacer(Modifier.size(10.dp))
                Text("Thông tin quan trọng", fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = "Project tạo VPN cục bộ thật nhưng chỉ định tuyến dải IP thử nghiệm " +
                        "198.18.0.0/15. Ứng dụng không chuyển tiếp lưu lượng game, không tạo " +
                        "độ trễ giả và không thể bảo đảm giảm ping.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
