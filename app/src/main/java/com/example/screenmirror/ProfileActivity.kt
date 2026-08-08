package com.example.screenmirror

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ProfileActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var saveBtn: MaterialButton
    private lateinit var profileView: ScrollView
    private lateinit var createForm: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        prefs = getSharedPreferences("user_profile", Context.MODE_PRIVATE)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = getString(R.string.profile_title)

        toolbar.setNavigationOnClickListener { finish() }

        profileView = findViewById(R.id.profileView)
        createForm = findViewById(R.id.createForm)
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        saveBtn = findViewById(R.id.saveBtn)

        if (hasProfile()) {
            showProfile()
        } else {
            showCreateForm()
        }

        saveBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            if (name.isEmpty()) {
                nameInput.error = getString(R.string.profile_error_name)
                return@setOnClickListener
            }
            saveProfile(name, email)
            Toast.makeText(this, if (profileView.visibility == View.VISIBLE) getString(R.string.profile_updated) else getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
            showProfile()
        }

        findViewById<LinearLayout>(R.id.btnLogout).setOnClickListener {
            clearProfile()
            Toast.makeText(this, getString(R.string.profile_deleted), Toast.LENGTH_SHORT).show()
            showCreateForm()
        }

        findViewById<LinearLayout>(R.id.menuSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuHelp).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        animateElements()
    }

    private fun hasProfile(): Boolean {
        return prefs.contains("name")
    }

    private fun showProfile() {
        profileView.visibility = View.VISIBLE
        createForm.visibility = View.GONE

        val name = prefs.getString("name", "") ?: ""
        val email = prefs.getString("email", "") ?: ""
        val roomsHosted = prefs.getInt("rooms_hosted", 0)
        val hoursShared = prefs.getInt("hours_shared", 0)

        findViewById<TextView>(R.id.profileName).text = name
        findViewById<TextView>(R.id.profileEmail).text = email
        findViewById<TextView>(R.id.statRooms).text = roomsHosted.toString()
        findViewById<TextView>(R.id.statHours).text = hoursShared.toString()

        nameInput.setText(name)
        emailInput.setText(email)
    }

    private fun showCreateForm() {
        profileView.visibility = View.GONE
        createForm.visibility = View.VISIBLE
        nameInput.text.clear()
        emailInput.text.clear()
    }

    private fun saveProfile(name: String, email: String) {
        prefs.edit()
            .putString("name", name)
            .putString("email", email)
            .apply()
    }

    private fun clearProfile() {
        prefs.edit().clear().apply()
    }

    private fun animateElements() {
        val header = findViewById<LinearLayout>(R.id.profileHeader)
        val stats = findViewById<LinearLayout>(R.id.statsCard)
        val menu = findViewById<LinearLayout>(R.id.menuCard)

        listOf(header, stats, menu).forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 30f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay((index * 150).toLong())
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }
}
