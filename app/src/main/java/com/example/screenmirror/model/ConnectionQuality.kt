package com.example.screenmirror.model

enum class ConnectionQuality {
    GOOD,
    MEDIUM,
    BAD;

    val label: String
        get() = when (this) {
            GOOD -> "IYI"
            MEDIUM -> "ORTA"
            BAD -> "KOTU"
        }

    companion object {
        fun fromStats(packetLoss: Double, roundTripTime: Double): ConnectionQuality = when {
            packetLoss > 5 || roundTripTime > 0.5 -> BAD
            packetLoss > 2 || roundTripTime > 0.3 -> MEDIUM
            else -> GOOD
        }
    }
}
