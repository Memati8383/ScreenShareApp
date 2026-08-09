package com.example.screenmirror.model

sealed class ServiceEvent {
    data object WebRtcReady : ServiceEvent()
    data object ViewerWaiting : ServiceEvent()
    data object ScreenBroadcasting : ServiceEvent()
    data object PeerConnected : ServiceEvent()
    data object LiveReceived : ServiceEvent()
    data object OfferSent : ServiceEvent()
    data object AnswerSent : ServiceEvent()
    data object BroadcastPaused : ServiceEvent()
    data object BroadcastResumed : ServiceEvent()
    data object BroadcastReady : ServiceEvent()
    data object RecordingStarted : ServiceEvent()
    data object RecordingStopped : ServiceEvent()
    data object SurfaceWaiting : ServiceEvent()
    data object SurfaceNotFound : ServiceEvent()
    data object ViewerLeft : ServiceEvent()
    data class StateChanged(val message: String) : ServiceEvent()
    data class ConnectionQualityChanged(
        val quality: ConnectionQuality,
        val rtt: Int,
        val fps: Int,
        val packetLoss: Double
    ) : ServiceEvent()
    data class ViewerCountChanged(val count: Int) : ServiceEvent()
    data class Error(val type: ErrorType, val detail: String = "") : ServiceEvent()
    data class RecordingError(val detail: String = "") : ServiceEvent()
    data class SdpError(val detail: String = "") : ServiceEvent()
    data class WebRtcError(val detail: String = "") : ServiceEvent()
    data class ConnectionBroken(val reason: String = "") : ServiceEvent()
    data class SenderDisconnected(val message: String = "") : ServiceEvent()
    data class SignalingStatus(val message: String) : ServiceEvent()
    data class RecordingProgress(val fileName: String) : ServiceEvent()
}
