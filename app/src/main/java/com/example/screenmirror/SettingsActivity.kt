package com.example.screenmirror

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var languageSpinner: Spinner
    private lateinit var notificationSwitch: SwitchMaterial
    private lateinit var qualityStatsSwitch: SwitchMaterial
    private lateinit var etTurnUrl: android.widget.EditText
    private lateinit var etTurnUser: android.widget.EditText
    private lateinit var etTurnPass: android.widget.EditText
    private lateinit var resolutionSpinner: Spinner
    private lateinit var fpsSpinner: Spinner

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
        etTurnUrl = findViewById(R.id.etTurnUrl)
        etTurnUser = findViewById(R.id.etTurnUser)
        etTurnPass = findViewById(R.id.etTurnPass)
        resolutionSpinner = findViewById(R.id.resolutionSpinner)
        fpsSpinner = findViewById(R.id.fpsSpinner)

        val languages = arrayOf("\uD83C\uDDF9\uD83C\uDDF7 Türkçe", "\uD83C\uDDEC\uD83C\uDDE7 English")
        val languageCodes = arrayOf("tr", "en")

        val adapter = ArrayAdapter(this, R.layout.spinner_item, languages)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        languageSpinner.adapter = adapter
        languageSpinner.setBackgroundResource(android.R.color.transparent)

        try {
            val popup = Spinner::class.java.getDeclaredField("mPopup")
            popup.isAccessible = true
            val listPopup = popup.get(languageSpinner)
            val bgField = listPopup.javaClass.getDeclaredField("mPopup")
            bgField.isAccessible = true
            val popupWindow = bgField.get(listPopup)
            val setBg = popupWindow.javaClass.getMethod("setBackgroundDrawable", android.graphics.drawable.Drawable::class.java)
            setBg.invoke(popupWindow, ColorDrawable(android.graphics.Color.TRANSPARENT))
        } catch (_: Exception) {}

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

        etTurnUrl.setText(AppSettings.getTurnUrl(this))
        etTurnUser.setText(AppSettings.getTurnUser(this))
        etTurnPass.setText(AppSettings.getTurnPass(this))

        val resolutions = arrayOf("480p (854x480)", "720p (1280x720)", "1080p (1920x1080)", "1440p (2560x1440)")
        val resolutionWidths = intArrayOf(854, 1280, 1920, 2560)
        val resolutionHeights = intArrayOf(480, 720, 1080, 1440)

        val resAdapter = ArrayAdapter(this, R.layout.spinner_item, resolutions)
        resAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        resolutionSpinner.adapter = resAdapter
        resolutionSpinner.setBackgroundResource(android.R.color.transparent)

        val currentWidth = AppSettings.getCaptureWidth(this)
        val currentResIndex = resolutionWidths.indexOf(currentWidth).coerceAtLeast(1)
        resolutionSpinner.setSelection(currentResIndex)

        resolutionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                AppSettings.setCaptureWidth(this@SettingsActivity, resolutionWidths[pos])
                AppSettings.setCaptureHeight(this@SettingsActivity, resolutionHeights[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val fpsOptions = arrayOf("15 FPS", "24 FPS", "30 FPS", "60 FPS")
        val fpsValues = intArrayOf(15, 24, 30, 60)

        val fpsAdapter = ArrayAdapter(this, R.layout.spinner_item, fpsOptions)
        fpsAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        fpsSpinner.adapter = fpsAdapter
        fpsSpinner.setBackgroundResource(android.R.color.transparent)

        val currentFps = AppSettings.getCaptureFps(this)
        val currentFpsIndex = fpsValues.indexOf(currentFps).coerceAtLeast(2)
        fpsSpinner.setSelection(currentFpsIndex)

        fpsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                AppSettings.setCaptureFps(this@SettingsActivity, fpsValues[pos])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onPause() {
        super.onPause()
        AppSettings.setTurnUrl(this, etTurnUrl.text.toString().trim())
        AppSettings.setTurnUser(this, etTurnUser.text.toString().trim())
        AppSettings.setTurnPass(this, etTurnPass.text.toString().trim())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
