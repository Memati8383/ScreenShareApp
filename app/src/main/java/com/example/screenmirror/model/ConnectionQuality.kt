package com.example.screenmirror.model

import androidx.annotation.StringRes
import com.example.screenmirror.R

enum class ConnectionQuality(@StringRes val labelRes: Int) {
    GOOD(R.string.quality_good),
    MEDIUM(R.string.quality_medium),
    BAD(R.string.quality_bad);

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
