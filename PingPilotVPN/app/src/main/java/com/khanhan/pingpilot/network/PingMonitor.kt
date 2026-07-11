package com.khanhan.pingpilot.network

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

data class PingTarget(
    val name: String,
    val host: String,
    val port: Int
)

data class PingResult(
    val latencyMs: Int?,
    val error: String? = null
)

object PingTargets {
    val defaults = listOf(
        PingTarget(name = "Cloudflare", host = "1.1.1.1", port = 443),
        PingTarget(name = "Google DNS", host = "8.8.8.8", port = 53),
        PingTarget(name = "Quad9", host = "9.9.9.9", port = 53)
    )
}

object PingMonitor {
    suspend fun measure(target: PingTarget, timeoutMs: Int = 2_500): PingResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val started = SystemClock.elapsedRealtimeNanos()
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.connect(InetSocketAddress(target.host, target.port), timeoutMs)
                }
                val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                PingResult(latencyMs = elapsedMs.roundToInt().coerceAtLeast(1))
            }.getOrElse { throwable ->
                PingResult(
                    latencyMs = null,
                    error = throwable.message ?: throwable.javaClass.simpleName
                )
            }
        }
}

fun latencyLabel(latencyMs: Int?): String = when {
    latencyMs == null -> "Không có dữ liệu"
    latencyMs < 60 -> "Rất tốt"
    latencyMs < 100 -> "Tốt"
    latencyMs <= 200 -> "Trung bình"
    else -> "Cao"
}
