package com.khanhan.pingpilot.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class VpnUiState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val startedAtElapsedMs: Long? = null,
    val errorMessage: String? = null
)

object VpnStatusStore {
    private val mutableState = MutableStateFlow(VpnUiState())
    val state: StateFlow<VpnUiState> = mutableState.asStateFlow()

    fun setConnecting() {
        mutableState.value = VpnUiState(status = VpnStatus.CONNECTING)
    }

    fun setConnected(startedAtElapsedMs: Long) {
        mutableState.value = VpnUiState(
            status = VpnStatus.CONNECTED,
            startedAtElapsedMs = startedAtElapsedMs
        )
    }

    fun setDisconnecting() {
        mutableState.value = mutableState.value.copy(status = VpnStatus.DISCONNECTING)
    }

    fun setDisconnected() {
        mutableState.value = VpnUiState(status = VpnStatus.DISCONNECTED)
    }

    fun setError(message: String) {
        mutableState.value = VpnUiState(
            status = VpnStatus.ERROR,
            errorMessage = message
        )
    }
}
