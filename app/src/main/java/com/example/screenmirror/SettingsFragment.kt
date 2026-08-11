package com.example.screenmirror

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "app_settings"
        clearStaleKeys()
        setPreferencesFromResource(R.xml.preferences, rootKey)
        initTurnSummaries()
        setupLanguagePreference()
        setupResolutionSpinner()
        setupFpsSpinner()
        setupTurnPreferences()
        setupResetPreference()
        setupHapticPreference()
    }

    private fun clearStaleKeys() {
        val prefs = requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var changed = false
        val staleIntKeys = listOf("capture_width", "capture_height", "capture_fps")
        for (key in staleIntKeys) {
            if (prefs.contains(key)) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    private fun initTurnSummaries() {
        val context = requireContext()
        findPreference<EditTextPreference>("turn_url")?.summary = AppSettings.getTurnUrl(context)
        findPreference<EditTextPreference>("turn_user")?.summary = AppSettings.getTurnUser(context)
        findPreference<EditTextPreference>("turn_pass")?.summary = AppSettings.getTurnPass(context)
    }

    private fun setupLanguagePreference() {
        val languagePref = findPreference<ListPreference>("language") ?: return
        languagePref.setOnPreferenceChangeListener { _, newValue ->
            val lang = newValue as String
            AppSettings.setLanguage(requireActivity(), lang)
            true
        }
    }

    private fun setupResolutionSpinner() {
        val spinnerPref = findPreference<SpinnerPreference>("capture_resolution") ?: return

        spinnerPref.setEntries(arrayOf("480p", "720p", "1080p", "1440p"))
        spinnerPref.setEntryValues(arrayOf("854x480", "1280x720", "1920x1080", "2560x1440"))

        val currentWidth = AppSettings.getCaptureWidth(requireContext())
        val currentHeight = AppSettings.getCaptureHeight(requireContext())
        spinnerPref.setCurrentValue("${currentWidth}x${currentHeight}")

        spinnerPref.setOnValueChangedListener { value ->
            val width = value.substringBefore("x").toIntOrNull() ?: return@setOnValueChangedListener
            val height = value.substringAfter("x").toIntOrNull() ?: return@setOnValueChangedListener
            AppSettings.setCaptureWidth(requireActivity(), width)
            AppSettings.setCaptureHeight(requireActivity(), height)
        }
    }

    private fun setupFpsSpinner() {
        val spinnerPref = findPreference<SpinnerPreference>("capture_fps") ?: return

        spinnerPref.setEntries(arrayOf("15 FPS", "24 FPS", "30 FPS", "60 FPS"))
        spinnerPref.setEntryValues(arrayOf("15", "24", "30", "60"))

        val currentFps = AppSettings.getCaptureFps(requireContext())
        spinnerPref.setCurrentValue(currentFps.toString())

        spinnerPref.setOnValueChangedListener { value ->
            val fps = value.toIntOrNull() ?: return@setOnValueChangedListener
            AppSettings.setCaptureFps(requireActivity(), fps)
        }
    }

    private fun setupTurnPreferences() {
        setupTurnEditText("turn_url", InputType.TYPE_TEXT_VARIATION_URI) { url ->
            validateTurnUrl(url)
        }
        setupTurnEditText("turn_user", InputType.TYPE_CLASS_TEXT) { user ->
            validateTurnField(user, getString(R.string.settings_turn_user))
        }
        setupTurnEditText("turn_pass", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) { pass ->
            validateTurnField(pass, getString(R.string.settings_turn_pass))
        }
    }

    private fun setupTurnEditText(key: String, inputType: Int, validator: (String) -> String?) {
        val pref = findPreference<EditTextPreference>(key) ?: return

        pref.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = inputType
            editText.setSelection(editText.text.length)
            editText.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_text_primary))
            editText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.dark_text_hint))
            editText.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_glass_input)
            editText.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
        }

        pref.text = getTurnCurrentValue(key)
        pref.summary = getTurnCurrentValue(key)

        pref.setOnPreferenceChangeListener { preference, newValue ->
            val value = (newValue as? String)?.trim() ?: ""
            val error = validator(value)
            if (error != null) {
                AlertDialog.Builder(requireContext(), R.style.Theme_ScreenShare_PreferenceDialog)
                    .setTitle(getString(R.string.settings_validation_error))
                    .setMessage(error)
                    .setPositiveButton(getString(R.string.btn_ok), null)
                    .show()
                false
            } else {
                saveTurnValue(key, value)
                preference.summary = value
                false
            }
        }
    }

    private fun getTurnCurrentValue(key: String): String {
        return when (key) {
            "turn_url" -> AppSettings.getTurnUrl(requireContext())
            "turn_user" -> AppSettings.getTurnUser(requireContext())
            "turn_pass" -> AppSettings.getTurnPass(requireContext())
            else -> ""
        }
    }

    private fun saveTurnValue(key: String, value: String) {
        when (key) {
            "turn_url" -> AppSettings.setTurnUrl(requireContext(), value)
            "turn_user" -> AppSettings.setTurnUser(requireContext(), value)
            "turn_pass" -> AppSettings.setTurnPass(requireContext(), value)
        }
    }

    private fun validateTurnUrl(url: String): String? {
        if (url.isBlank()) {
            return getString(R.string.settings_error_turn_url_empty)
        }
        val trimmed = url.trim()
        if (!trimmed.startsWith("turn:") && !trimmed.startsWith("stun:")) {
            return getString(R.string.settings_error_turn_url_format)
        }
        val afterPrefix = trimmed.substringAfter(":")
        if (!afterPrefix.contains(":")) {
            return getString(R.string.settings_error_turn_url_host)
        }
        return null
    }

    private fun validateTurnField(value: String, fieldName: String): String? {
        if (value.isBlank()) {
            return getString(R.string.settings_error_turn_field_empty, fieldName)
        }
        return null
    }

    private fun setupResetPreference() {
        val resetPref = findPreference<Preference>("reset_all") ?: return
        resetPref.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext(), R.style.Theme_ScreenShare_PreferenceDialog)
                .setTitle(getString(R.string.settings_reset_all_title))
                .setMessage(getString(R.string.settings_reset_all_msg))
                .setPositiveButton(getString(R.string.settings_reset_confirm)) { _, _ ->
                    resetAllSettings()
                }
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show()
            true
        }
    }

    private fun setupHapticPreference() {
        val hapticPref = findPreference<SwitchPreferenceCompat>("haptic_enabled") ?: return
        hapticPref.setOnPreferenceChangeListener { _, newValue ->
            HapticHelper.lightTap(requireActivity())
            AppSettings.setHapticEnabled(requireActivity(), newValue as Boolean)
            true
        }
    }

    private fun resetAllSettings() {
        val context = requireActivity()

        AppSettings.setLanguage(context, AppSettings.LANG_TR)
        AppSettings.setNotificationsEnabled(context, true)
        AppSettings.setQualityStatsEnabled(context, true)
        AppSettings.setHapticEnabled(context, true)
        AppSettings.setCaptureWidth(context, 1280)
        AppSettings.setCaptureHeight(context, 720)
        AppSettings.setCaptureFps(context, 30)
        AppSettings.setTurnUrl(context, "turn:openrelay.metered.ca:443")
        AppSettings.setTurnUser(context, "openrelayproject")
        AppSettings.setTurnPass(context, "openrelayproject")

        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().clear().apply()

        preferenceScreen.removeAll()
        setPreferencesFromResource(R.xml.preferences, null)
        initTurnSummaries()
        setupLanguagePreference()
        setupResolutionSpinner()
        setupFpsSpinner()
        setupTurnPreferences()
        setupResetPreference()
        setupHapticPreference()

        com.google.android.material.snackbar.Snackbar.make(
            requireView(),
            getString(R.string.settings_reset_done),
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
