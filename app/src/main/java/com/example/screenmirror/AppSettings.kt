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

    fun restartActivity(activity: Activity) {
        val intent = activity.intent
        activity.finish()
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0)
    }
}
