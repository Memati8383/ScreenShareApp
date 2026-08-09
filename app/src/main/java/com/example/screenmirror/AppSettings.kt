package com.example.screenmirror

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import java.util.Locale

object AppSettings {
    private const val PREF_NAME = "app_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_QUALITY_STATS = "quality_stats_enabled"
    private const val KEY_CAPTURE_WIDTH = "capture_width"
    private const val KEY_CAPTURE_HEIGHT = "capture_height"
    private const val KEY_CAPTURE_FPS = "capture_fps"

    const val LANG_TR = "tr"
    const val LANG_EN = "en"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getLanguage(context: Context): String {
        return prefs(context).getString(KEY_LANGUAGE, LANG_TR) ?: LANG_TR
    }

    fun setLanguage(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()
        applyLanguage(context)
    }

    fun applyLanguage(context: Context) {
        val lang = getLanguage(context)
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val localeList = LocaleListCompat.forLanguageTags(lang)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun isNotificationsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_NOTIFICATIONS, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    fun isQualityStatsEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_QUALITY_STATS, true)
    }

    fun setQualityStatsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_QUALITY_STATS, enabled).apply()
    }

    // TURN credentials — encrypted via SecureCredentialStore
    fun getTurnUrl(context: Context): String {
        return SecureCredentialStore.decrypt(context, "turn_url", "turn:openrelay.metered.ca:443")
    }

    fun setTurnUrl(context: Context, url: String) {
        SecureCredentialStore.encrypt(context, "turn_url", url)
    }

    fun getTurnUser(context: Context): String {
        return SecureCredentialStore.decrypt(context, "turn_user", "openrelayproject")
    }

    fun setTurnUser(context: Context, user: String) {
        SecureCredentialStore.encrypt(context, "turn_user", user)
    }

    fun getTurnPass(context: Context): String {
        return SecureCredentialStore.decrypt(context, "turn_pass", "openrelayproject")
    }

    fun setTurnPass(context: Context, pass: String) {
        SecureCredentialStore.encrypt(context, "turn_pass", pass)
    }

    fun getCaptureWidth(context: Context): Int {
        return prefs(context).getInt(KEY_CAPTURE_WIDTH, 1280)
    }

    fun setCaptureWidth(context: Context, width: Int) {
        prefs(context).edit().putInt(KEY_CAPTURE_WIDTH, width).apply()
    }

    fun getCaptureHeight(context: Context): Int {
        return prefs(context).getInt(KEY_CAPTURE_HEIGHT, 720)
    }

    fun setCaptureHeight(context: Context, height: Int) {
        prefs(context).edit().putInt(KEY_CAPTURE_HEIGHT, height).apply()
    }

    fun getCaptureFps(context: Context): Int {
        return prefs(context).getInt(KEY_CAPTURE_FPS, 30)
    }

    fun setCaptureFps(context: Context, fps: Int) {
        prefs(context).edit().putInt(KEY_CAPTURE_FPS, fps).apply()
    }

    fun restartActivity(activity: Activity) {
        val intent = activity.intent
        activity.finish()
        activity.startActivity(intent)
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }
}
