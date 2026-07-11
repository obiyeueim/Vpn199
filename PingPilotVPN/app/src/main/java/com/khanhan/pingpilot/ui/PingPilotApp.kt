package com.khanhan.pingpilot.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var targetMenuOpen by remember { mutableStateOf(false) }
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
                        Text("PING PILOT", fontWeight = FontWeight.Black, letterSpacing = 1.8.sp)
                        Text(
                            "Local VPN demo & latency monitor",
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
            ConnectionHero(
                status = vpnUiState.status,
                isConnected = isConnected,
                isBusy = isBusy,
                errorMessage = vpnUiState.errorMessage,
                uptimeMs = vpnUiState.startedAtElapsedMs?.let { nowElapsed - it },
                onClick = {
                    if (isConnected) onDisconnectRequested() else onConnectRequested()
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Speed,
                    title = "PING HIỆN TẠI",
                    value = latestPing?.let { "$it ms" } ?: "--",
                    subtitle = latencyLabel(latestPing)
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Bolt,
                    title = "NGƯỠNG CẢNH BÁO",
                    value = "${threshold.toInt()} ms",
                    subtitle = if ((latestPing ?: 0) <= threshold) "Trong ngưỡng" else "Vượt ngưỡng"
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudQueue, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text("Máy chủ đo", fontWeight = FontWeight.Bold)
                    }

                    ExposedDropdownMenuBox(
                        expanded = targetMenuOpen,
                        onExpandedChange = { targetMenuOpen = !targetMenuOpen }
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = "${selectedTarget.name} · ${selectedTarget.host}:${selectedTarget.port}",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetMenuOpen)
                            },
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = targetMenuOpen,
                            onDismissRequest = { targetMenuOpen = false }
                        ) {
                            PingTargets.defaults.forEachIndexed { index, target ->
                                DropdownMenuItem(
                                    text = { Text("${target.name} · ${target.host}") },
                                    onClick = {
                                        selectedTargetIndex = index
                                        samples = emptyList()
                                        targetMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        "Cảnh báo khi ping vượt ${threshold.toInt()} ms",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        valueRange = 50f..300f,
                        steps = 9
                    )
                    pingError?.let {
                        Text(
                            "Lần đo gần nhất thất bại: $it",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Card(
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
                        Icon(Icons.Rounded.Speed, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text("Lịch sử độ trễ", fontWeight = FontWeight.Bold)
                    }
                    LatencyChart(samples = samples, threshold = threshold)
                    Text(
                        "Đo bằng thời gian thiết lập kết nối TCP, cập nhật mỗi 2 giây.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            InfoCard()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ConnectionHero(
    status: VpnStatus,
    isConnected: Boolean,
    isBusy: Boolean,
    errorMessage: String?,
    uptimeMs: Long?,
    onClick: () -> Unit
) {
    val pulse by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0.72f,
        animationSpec = tween(450),
        label = "connectionPulse"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        ),
        shape = RoundedCornerShape(30.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size((128 * pulse).dp)
                    .background(
                        if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surface,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onClick,
                    enabled = !isBusy,
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    )
                ) {
                    Icon(
                        Icons.Rounded.PowerSettingsNew,
                        contentDescription = if (isConnected) "Tắt VPN" else "Bật VPN",
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            AnimatedContent(targetState = status, label = "statusText") { current ->
                Text(
                    text = when (current) {
                        VpnStatus.DISCONNECTED -> "CHƯA KẾT NỐI"
                        VpnStatus.CONNECTING -> "ĐANG KẾT NỐI…"
                        VpnStatus.CONNECTED -> "VPN DEMO ĐANG BẬT"
                        VpnStatus.DISCONNECTING -> "ĐANG NGẮT…"
                        VpnStatus.ERROR -> "CÓ LỖI"
                    },
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
            }

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
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LatencyChart(samples: List<Int>, threshold: Float) {
    val lineColor = MaterialTheme.colorScheme.primary
    val thresholdColor = MaterialTheme.colorScheme.error.copy(alpha = 0.65f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (samples.size < 2) {
            Text(
                "Đang thu thập dữ liệu…",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                val maxMs = maxOf(350f, samples.maxOrNull()?.toFloat() ?: 350f)
                val thresholdY = size.height - (threshold.coerceAtMost(maxMs) / maxMs * size.height)

                repeat(4) { index ->
                    val y = size.height * index / 3f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                drawLine(
                    color = thresholdColor,
                    start = Offset(0f, thresholdY),
                    end = Offset(size.width, thresholdY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )

                val path = Path()
                samples.forEachIndexed { index, value ->
                    val x = if (samples.lastIndex == 0) 0f else size.width * index / samples.lastIndex
                    val y = size.height - (value.coerceAtMost(maxMs.toInt()) / maxMs * size.height)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
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
                    Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "Project này tạo VPN cục bộ thật nhưng chỉ định tuyến dải IP thử nghiệm 198.18.0.0/15. " +
                        "Nó không chuyển tiếp lưu lượng game, không tạo độ trễ giả và không thể bảo đảm giảm ping. " +
                        "Mục đo ping dùng để theo dõi chất lượng mạng hiện tại.",
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
