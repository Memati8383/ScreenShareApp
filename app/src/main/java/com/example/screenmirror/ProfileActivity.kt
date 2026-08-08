package com.example.screenmirror

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.screenmirror.data.RoomHistoryManager
import com.google.android.material.appbar.MaterialToolbar

class ProfileActivity : AppCompatActivity() {

    private lateinit var layoutEmpty: LinearLayout
    private lateinit var layoutProfile: LinearLayout
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var tvUserName: TextView
    private lateinit var tvEmail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        layoutEmpty = findViewById(R.id.layoutEmpty)
        layoutProfile = findViewById(R.id.layoutProfile)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        tvUserName = findViewById(R.id.tvUserName)
        tvEmail = findViewById(R.id.tvEmail)

        loadProfile()

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveProfile() }
        findViewById<View>(R.id.btnEditProfile).setOnClickListener {
            Toast.makeText(this, "Profili düzenleme yakında", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnNotifications).setOnClickListener {
            Toast.makeText(this, "Bildirim ayarları yakında", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btnPrivacy).setOnClickListener {
            Toast.makeText(this, "Gizlilik ayarları yakında", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfile() {
        val prefs = getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        val name = prefs.getString("name", "") ?: ""
        val email = prefs.getString("email", "") ?: ""

        if (name.isNotEmpty()) {
            layoutEmpty.visibility = View.GONE
            layoutProfile.visibility = View.VISIBLE
            tvUserName.text = name
            tvEmail.text = email
            loadStats()
        } else {
            layoutEmpty.visibility = View.VISIBLE
            layoutProfile.visibility = View.GONE
        }
    }

    private fun saveProfile() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (name.isEmpty()) {
            etName.error = "Adınızı girin"
            return
        }
        if (email.isEmpty()) {
            etEmail.error = "E-posta girin"
            return
        }

        getSharedPreferences("user_profile", Context.MODE_PRIVATE)
            .edit()
            .putString("name", name)
            .putString("email", email)
            .apply()

        Toast.makeText(this, "Profil kaydedildi", Toast.LENGTH_SHORT).show()
        loadProfile()
    }

    private fun loadStats() {
        val manager = RoomHistoryManager(this)
        val history = manager.getAll()
        val count = history.size
        val totalMs = history.sumOf { it.duration }
        val totalMin = totalMs / 1000 / 60

        findViewById<TextView>(R.id.tvSessionCount).text = count.toString()
        findViewById<TextView>(R.id.tvTotalTime).text = if (totalMin > 0) "${totalMin}dk" else "0dk"
    }
}
