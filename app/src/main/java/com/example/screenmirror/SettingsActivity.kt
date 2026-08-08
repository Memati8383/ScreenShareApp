package com.example.screenmirror

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var languageSpinner: Spinner
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var qualityStatsSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        toolbar.setNavigationOnClickListener { finish() }

        languageSpinner = findViewById(R.id.languageSpinner)
        notificationSwitch = findViewById(R.id.switchNotifications)
        qualityStatsSwitch = findViewById(R.id.switchQualityStats)

        val languages = arrayOf("\uD83C\uDDF9\uD83C\uDDF7 Türkçe", "\uD83C\uDDEC\uD83C\uDDE7 English")
        val languageCodes = arrayOf("tr", "en")

        val adapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        languageSpinner.adapter = adapter
        languageSpinner.setBackgroundResource(android.R.color.transparent)
        languageSpinner.setPopupBackgroundResource(R.drawable.bg_spinner_popup)
        languageSpinner.elevation = 8f

        val currentLang = AppSettings.getLanguage(this)
        val currentIndex = languageCodes.indexOf(currentLang).coerceAtLeast(0)
        languageSpinner.setSelection(currentIndex)

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val selected = languageCodes[pos]
                if (selected != AppSettings.getLanguage(this@SettingsActivity)) {
                    AppSettings.setLanguage(this@SettingsActivity, selected)
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
