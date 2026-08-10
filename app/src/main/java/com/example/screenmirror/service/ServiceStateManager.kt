package com.example.screenmirror.service

import com.example.screenmirror.model.ConnectionQuality
import com.example.screenmirror.model.ServiceEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class ServiceStateManager {

    private val _event = MutableSharedFlow<ServiceEvent>(extraBufferCapacity = 128)
    val event: SharedFlow<ServiceEvent> = _event.asSharedFlow()

    private val _state = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount.asStateFlow()

    private val _quality = MutableStateFlow(ConnectionQuality.GOOD)
    val quality: StateFlow<ConnectionQuality> = _quality.asStateFlow()

    fun emitEvent(serviceEvent: ServiceEvent) {
        _event.tryEmit(serviceEvent)
        when (serviceEvent) {
            is ServiceEvent.Error -> _state.value = ServiceState.Error(serviceEvent.type.displayMessage)
            is ServiceEvent.WebRtcReady -> _state.value = ServiceState.Ready
            is ServiceEvent.ScreenBroadcasting -> _state.value = ServiceState.Broadcasting
            is ServiceEvent.PeerConnected -> _state.value = ServiceState.Connected
            is ServiceEvent.LiveReceived -> _state.value = ServiceState.Viewing
            is ServiceEvent.BroadcastPaused -> _state.value = ServiceState.Paused
            is ServiceEvent.BroadcastResumed -> _state.value = ServiceState.Broadcasting
            is ServiceEvent.ViewerCountChanged -> _viewerCount.value = serviceEvent.count
            is ServiceEvent.ConnectionQualityChanged -> _quality.value = serviceEvent.quality
            is ServiceEvent.SenderDisconnected -> _state.value = ServiceState.Disconnected
            is ServiceEvent.ConnectionBroken -> _state.value = ServiceState.Disconnected
            else -> {}
        }
    }

    fun reset() {
        _state.value = ServiceState.Idle
        _viewerCount.value = 0
        _quality.value = ConnectionQuality.GOOD
    }
}

sealed class ServiceState {
    data object Idle : ServiceState()
    data object Ready : ServiceState()
    data object Broadcasting : ServiceState()
    data object Connected : ServiceState()
    data object Viewing : ServiceState()
    data object Paused : ServiceState()
    data object Disconnected : ServiceState()
    data class Error(val message: String) : ServiceState()
}
