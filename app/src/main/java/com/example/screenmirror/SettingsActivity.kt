package com.example.screenmirror

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var themeSwitch: SwitchMaterial
    private lateinit var languageSpinner: Spinner
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var qualityStatsSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        toolbar.setNavigationOnClickListener { finish() }

        themeSwitch = findViewById(R.id.themeSwitch)
        languageSpinner = findViewById(R.id.languageSpinner)
        notificationSwitch = findViewById(R.id.switchNotifications)
        qualityStatsSwitch = findViewById(R.id.switchQualityStats)

        themeSwitch.isChecked = AppSettings.isDarkTheme(this)

        val tvThemeValue = findViewById<TextView>(R.id.tvThemeValue)
        tvThemeValue.text = if (AppSettings.isDarkTheme(this)) getString(R.string.settings_theme_dark) else getString(R.string.settings_theme_light)

        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setTheme(this, if (isChecked) AppSettings.THEME_LIGHT else AppSettings.THEME_DARK)
            AppSettings.restartActivity(this)
        }

        val languages = arrayOf("Türkçe", "English")
        val languageCodes = arrayOf("tr", "en")

        val adapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        adapter.setDropDownViewResource(R.layout.spinner_item)
        languageSpinner.adapter = adapter

        val currentLang = AppSettings.getLanguage(this)
        val currentIndex = languageCodes.indexOf(currentLang).coerceAtLeast(0)
        languageSpinner.setSelection(currentIndex)

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val selected = languageCodes[pos]
                if (selected != AppSettings.getLanguage(this@SettingsActivity)) {
                    AppSettings.setLanguage(this@SettingsActivity, selected)
                    AppSettings.restartActivity(this@SettingsActivity)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        notificationSwitch.isChecked = AppSettings.isNotificationsEnabled(this)
        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setNotificationsEnabled(this, isChecked)
        }

        qualityStatsSwitch.isChecked = AppSettings.isQualityStatsEnabled(this)
        qualityStatsSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setQualityStatsEnabled(this, isChecked)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
