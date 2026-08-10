package com.example.screenmirror

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticHelper {

    enum class HapticType {
        LIGHT,    // Buton tıklamaları
        MEDIUM,   // Önemli işlemler
        SUCCESS,  // Başarılı bağlantı
        ERROR,    // Hata durumları
        HEAVY     // Kritik uyarılar
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

            val effect = when (type) {
                HapticType.LIGHT -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
                    } else {
                        @Suppress("DEPRECATION")
                        VibrationEffect.createOneShot(10, 255)
                    }
                }
                HapticType.MEDIUM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                    } else {
                        @Suppress("DEPRECATION")
                        VibrationEffect.createOneShot(20, 255)
                    }
                }
                HapticType.SUCCESS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val timings = longArrayOf(0, 10, 50, 10)
                        val amplitudes = intArrayOf(0, 128, 255, 0)
                        VibrationEffect.createWaveform(timings, amplitudes, -1)
                    } else {
                        @Suppress("DEPRECATION")
                        VibrationEffect.createOneShot(30, 200)
                    }
                }
                HapticType.ERROR -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val timings = longArrayOf(0, 30, 50, 30, 50, 30)
                        val amplitudes = intArrayOf(0, 255, 0, 200, 0, 150)
                        VibrationEffect.createWaveform(timings, amplitudes, -1)
                    } else {
                        @Suppress("DEPRECATION")
                        VibrationEffect.createOneShot(100, 255)
                    }
                }
                HapticType.HEAVY -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    } else {
                        @Suppress("DEPRECATION")
                        VibrationEffect.createOneShot(50, 255)
                    }
                }
            }

            vibrator.vibrate(effect)
        } catch (_: SecurityException) {
            // VIBRATE permission missing - skip silently
        }
    }

    fun lightTap(context: Context) {
        performHaptic(context, HapticType.LIGHT)
    }

    fun mediumTap(context: Context) {
        performHaptic(context, HapticType.MEDIUM)
    }

    fun successTap(context: Context) {
        performHaptic(context, HapticType.SUCCESS)
    }

    fun errorTap(context: Context) {
        performHaptic(context, HapticType.ERROR)
    }

    fun heavyTap(context: Context) {
        performHaptic(context, HapticType.HEAVY)
    }
}