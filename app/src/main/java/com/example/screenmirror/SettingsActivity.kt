package com.example.screenmirror

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var switchNotifications: SwitchMaterial
    private lateinit var switchQualityStats: SwitchMaterial
    private lateinit var tvThemeValue: TextView
    private lateinit var tvLanguageValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvThemeValue = findViewById(R.id.tvThemeValue)
        tvLanguageValue = findViewById(R.id.tvLanguageValue)
        switchNotifications = findViewById(R.id.switchNotifications)
        switchQualityStats = findViewById(R.id.switchQualityStats)

        loadSettings()

        findViewById<LinearLayout>(R.id.btnTheme).setOnClickListener {
            showThemeDialog()
        }

        findViewById<LinearLayout>(R.id.btnLanguage).setOnClickListener {
            showLanguageDialog()
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setNotificationsEnabled(this, isChecked)
        }

        switchQualityStats.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setQualityStatsEnabled(this, isChecked)
        }
    }

    private fun loadSettings() {
        val theme = AppSettings.getTheme(this)
        tvThemeValue.text = if (theme == AppSettings.THEME_DARK) {
            getString(R.string.settings_theme_dark)
        } else {
            getString(R.string.settings_theme_light)
        }

        val lang = AppSettings.getLanguage(this)
        tvLanguageValue.text = if (lang == AppSettings.LANG_TR) "Türkçe" else "English"

        switchNotifications.isChecked = AppSettings.isNotificationsEnabled(this)
        switchQualityStats.isChecked = AppSettings.isQualityStatsEnabled(this)
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.settings_theme_dark),
            getString(R.string.settings_theme_light)
        )
        val currentTheme = AppSettings.getTheme(this)
        val checkedItem = if (currentTheme == AppSettings.THEME_DARK) 0 else 1

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                val newTheme = if (which == 0) AppSettings.THEME_DARK else AppSettings.THEME_LIGHT
                AppSettings.setTheme(this, newTheme)
                tvThemeValue.text = themes[which]
                dialog.dismiss()
                restartActivity()
            }
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("Türkçe", "English")
        val currentLang = AppSettings.getLanguage(this)
        val checkedItem = if (currentLang == AppSettings.LANG_TR) 0 else 1

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val newLang = if (which == 0) AppSettings.LANG_TR else AppSettings.LANG_EN
                AppSettings.setLanguage(this, newLang)
                tvLanguageValue.text = languages[which]
                dialog.dismiss()
                restartActivity()
            }
            .show()
    }

    private fun restartActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        finish()
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
}
