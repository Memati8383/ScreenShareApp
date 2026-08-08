package com.example.screenmirror

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    private const val PREF_NAME = "app_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_QUALITY_STATS = "quality_stats_enabled"

    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"
    const val LANG_TR = "tr"
    const val LANG_EN = "en"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getTheme(context: Context): String {
        return prefs(context).getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    fun setTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }

    fun getLanguage(context: Context): String {
        return prefs(context).getString(KEY_LANGUAGE, LANG_TR) ?: LANG_TR
    }

    fun setLanguage(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()
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

    fun applyTheme(context: Context) {
        val theme = getTheme(context)
        when (theme) {
            THEME_LIGHT -> context.setTheme(R.style.Theme_ScreenShare_Light)
            else -> context.setTheme(R.style.Theme_ScreenShare)
        }
    }

    fun applyLanguage(context: Context): Context {
        val lang = getLanguage(context)
        val locale = java.util.Locale(lang)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
