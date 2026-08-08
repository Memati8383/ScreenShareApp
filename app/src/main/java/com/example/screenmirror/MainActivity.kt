package com.example.screenmirror

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private val REQ_PROJECTION = 1001
    private val REQ_PERMISSIONS = 1002
    private lateinit var mpm: MediaProjectionManager

    private lateinit var etRoom: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnShare: Button
    private lateinit var btnWatch: Button
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var btnHostMode: LinearLayout
    private lateinit var btnClientMode: LinearLayout
    private var isHostMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        etRoom = findViewById(R.id.etRoom)
        tvStatus = findViewById(R.id.tvStatus)
        btnShare = findViewById(R.id.btnShare)
        btnWatch = findViewById(R.id.btnWatch)
        bottomNav = findViewById(R.id.bottomNav)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        btnHostMode = findViewById(R.id.btnHostMode)
        btnClientMode = findViewById(R.id.btnClientMode)

        updateCardStyles()

        findViewById<ImageView>(R.id.btnMenuDrawer).setOnClickListener {
            drawerLayout.open()
        }

        btnHostMode.setOnClickListener { switchMode(true) }
        btnClientMode.setOnClickListener { switchMode(false) }
        btnShare.setOnClickListener { startSender() }
        btnWatch.setOnClickListener { goToViewer() }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_recent -> {
                    startActivity(Intent(this, RecentActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }

        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawers()
            when (item.itemId) {
                R.id.drawer_home -> true
                R.id.drawer_help -> {
                    startActivity(Intent(this, HelpActivity::class.java))
                    true
                }
                R.id.drawer_feedback -> {
                    startActivity(Intent(this, FeedbackActivity::class.java))
                    true
                }
                R.id.drawer_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.drawer_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                R.id.drawer_share -> {
                    shareApp()
                    true
                }
                else -> false
            }
        }

        requestPermissions()
    }

    private fun updateCardStyles() {
        btnHostMode.setBackgroundResource(R.drawable.bg_card)
        btnClientMode.setBackgroundResource(R.drawable.bg_card)
    }

    private fun switchMode(hostMode: Boolean) {
        isHostMode = hostMode
        val tvSubtitle = findViewById<TextView>(R.id.tvSubtitle)

        if (hostMode) {
            btnHostMode.setBackgroundResource(R.drawable.bg_card_selected)
            btnClientMode.setBackgroundResource(R.drawable.bg_card)
            btnShare.visibility = View.VISIBLE
            btnWatch.visibility = View.GONE
            tvSubtitle.text = getString(R.string.main_share_desc)
        } else {
            btnClientMode.setBackgroundResource(R.drawable.bg_card_selected)
            btnHostMode.setBackgroundResource(R.drawable.bg_card)
            btnShare.visibility = View.GONE
            btnWatch.visibility = View.VISIBLE
            tvSubtitle.text = getString(R.string.main_watch_desc)
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQ_PERMISSIONS)
        }
    }

    private fun validateInput(): Boolean {
        val room = etRoom.text.toString().trim()
        if (room.isBlank()) {
            showStatus(getString(R.string.main_error_empty_room))
            return false
        }
        return true
    }

    private fun startSender() {
        if (!validateInput()) return
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    private fun goToViewer() {
        if (!validateInput()) return
        val intent = Intent(this, ViewerActivity::class.java).apply {
            putExtra("room", etRoom.text.toString().trim())
        }
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, SenderActivity::class.java).apply {
                putExtra("room", etRoom.text.toString().trim())
                putExtra("resultCode", resultCode)
                putExtra("projectionData", data)
            }
            startActivity(intent)
        }
    }

    private fun showStatus(msg: String) {
        tvStatus.text = msg
        tvStatus.visibility = View.VISIBLE
        tvStatus.postDelayed({ tvStatus.visibility = View.GONE }, 5000)
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser)))
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopService(Intent(this, ScreenShareService::class.java))
        } catch (_: Exception) {}
    }
}
