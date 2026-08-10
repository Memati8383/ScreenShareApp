package com.example.screenmirror

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticHelper {

    private object Constants {
        const val MAX_AMPLITUDE = 255
        const val HALF_AMPLITUDE = 128
        const val LIGHT_DURATION_MS = 10L
        const val MEDIUM_DURATION_MS = 20L
        const val SUCCESS_DURATION_MS = 30L
        const val ERROR_DURATION_MS = 100L
        const val HEAVY_DURATION_MS = 50L
        const val FALLBACK_AMPLITUDE_200 = 200
        const val FALLBACK_AMPLITUDE_150 = 150
    }

    enum class HapticType {
        LIGHT,
        MEDIUM,
        SUCCESS,
        ERROR,
        HEAVY
    }

    private fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun performHaptic(context: Context, type: HapticType) {
        try {
            if (!AppSettings.isHapticEnabled(context)) return

            val vibrator = getVibrator(context)
            if (!vibrator.hasVibrator()) return

            val effect = createEffect(type)
            vibrator.vibrate(effect)
        } catch (_: SecurityException) {
            // VIBRATE permission missing - skip silently
        }
    }

    private fun createEffect(type: HapticType): VibrationEffect {
        return when (type) {
            HapticType.LIGHT -> createOneShot(Constants.LIGHT_DURATION_MS)
            HapticType.MEDIUM -> createOneShot(Constants.MEDIUM_DURATION_MS)
            HapticType.SUCCESS -> createSuccessEffect()
            HapticType.ERROR -> createErrorEffect()
            HapticType.HEAVY -> createOneShot(Constants.HEAVY_DURATION_MS)
        }
    }

    private fun createOneShot(durationMs: Long): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createOneShot(durationMs, Constants.MAX_AMPLITUDE)
        }
    }

    private fun createSuccessEffect(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, Constants.LIGHT_DURATION_MS, 50, Constants.LIGHT_DURATION_MS)
            val amplitudes = intArrayOf(0, Constants.HALF_AMPLITUDE, Constants.MAX_AMPLITUDE, 0)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createOneShot(Constants.SUCCESS_DURATION_MS, Constants.FALLBACK_AMPLITUDE_200)
        }
    }

    private fun createErrorEffect(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 30, 50, 30, 50, 30)
            val amplitudes = intArrayOf(0, Constants.MAX_AMPLITUDE, 0, Constants.FALLBACK_AMPLITUDE_200, 0, Constants.FALLBACK_AMPLITUDE_150)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createOneShot(Constants.ERROR_DURATION_MS, Constants.MAX_AMPLITUDE)
        }
    }

    fun lightTap(context: Context) = performHaptic(context, HapticType.LIGHT)

    fun mediumTap(context: Context) = performHaptic(context, HapticType.MEDIUM)

    fun successTap(context: Context) = performHaptic(context, HapticType.SUCCESS)

    fun errorTap(context: Context) = performHaptic(context, HapticType.ERROR)

    fun heavyTap(context: Context) = performHaptic(context, HapticType.HEAVY)
}
