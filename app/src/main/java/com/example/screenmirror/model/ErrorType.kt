package com.example.screenmirror.model

enum class ErrorType {
    WEBRTC_INIT,
    SDP_NEGOTIATION,
    ICE_CONNECTION,
    SIGNALING_CONNECTION,
    SIGNALING_TIMEOUT,
    SCREEN_CAPTURE,
    RECORDING,
    PERMISSION_DENIED,
    SURFACE_NOT_READY,
    PEER_CONNECTION_NULL,
    UNKNOWN;

    val displayMessage: String
        get() = when (this) {
            WEBRTC_INIT -> "WebRTC baslatma hatasi"
            SDP_NEGOTIATION -> "SDP muzakere hatasi"
            ICE_CONNECTION -> "ICE baglanti hatasi"
            SIGNALING_CONNECTION -> "Sinyalleme baglanti hatasi"
            SIGNALING_TIMEOUT -> "Sinyalleme zaman asimi"
            SCREEN_CAPTURE -> "Ekran yakalama hatasi"
            RECORDING -> "Kayit hatasi"
            PERMISSION_DENIED -> "Izin reddedildi"
            SURFACE_NOT_READY -> "Yuzey henuz hazir degil"
            PEER_CONNECTION_NULL -> "Es baglantisi kurulamadi"
            UNKNOWN -> "Bilinmeyen hata"
        }
}
