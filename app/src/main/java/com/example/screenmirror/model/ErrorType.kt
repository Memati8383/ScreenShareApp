package com.example.screenmirror.model

import androidx.annotation.StringRes
import com.example.screenmirror.R

enum class ErrorType(@StringRes val messageRes: Int) {
    WEBRTC_INIT(R.string.error_webrtc_init),
    SDP_NEGOTIATION(R.string.error_sdp_negotiation),
    ICE_CONNECTION(R.string.error_ice_connection),
    SIGNALING_CONNECTION(R.string.error_signaling_connection),
    SIGNALING_TIMEOUT(R.string.error_signaling_timeout),
    SCREEN_CAPTURE(R.string.error_screen_capture),
    RECORDING(R.string.error_recording),
    PERMISSION_DENIED(R.string.error_permission_denied),
    SURFACE_NOT_READY(R.string.error_surface_not_ready),
    PEER_CONNECTION_NULL(R.string.error_peer_connection_null),
    UNKNOWN(R.string.error_unknown);

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
