package com.example.screenmirror

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import android.widget.EditText

class MainActivity : AppCompatActivity() {

    private lateinit var mpm: MediaProjectionManager
    private lateinit var projectionLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var etRoom: EditText
    private lateinit var tvStatus: TextView
    private lateinit var btnShare: Button
    private lateinit var btnWatch: Button
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var btnHostMode: LinearLayout
    private lateinit var btnClientMode: LinearLayout
    private lateinit var tvSubtitle: TextView
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
        tvSubtitle = findViewById(R.id.tvSubtitle)

        projectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val intent = Intent(this, SenderActivity::class.java).apply {
                    putExtra("room", etRoom.text.toString().trim())
                    putExtra("resultCode", result.resultCode)
                    putExtra("projectionData", result.data)
                }
                startActivity(intent)
            }
        }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* permissions granted or denied */ }

        switchMode(true)

        findViewById<ImageView>(R.id.btnMenuDrawer).setOnClickListener {
            drawerLayout.open()
        }

        btnHostMode.setOnClickListener { switchMode(true) }
        btnClientMode.setOnClickListener { switchMode(false) }
        btnShare.setOnClickListener { startSender() }
        btnWatch.setOnClickListener { goToViewer() }

        findViewById<ImageView>(R.id.btnCopyRoom).setOnClickListener {
            val room = etRoom.text.toString().trim()
            if (room.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("room_name", room)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, getString(R.string.room_copied), Toast.LENGTH_SHORT).show()
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_recent -> {
                    startActivity(Intent(this, RecentActivity::class.java))
                    false
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    false
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

    override fun onResume() {
        super.onResume()
        bottomNav.menu.setGroupCheckable(0, true, true)
        bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true
    }

    private fun switchMode(hostMode: Boolean) {
        isHostMode = hostMode

        if (hostMode) {
            btnHostMode.setBackgroundResource(R.drawable.bg_card_active)
            btnClientMode.setBackgroundResource(R.drawable.bg_card_inactive)
            btnShare.visibility = View.VISIBLE
            btnWatch.visibility = View.GONE
            etRoom.hint = getString(R.string.main_room_hint_create)
            etRoom.setText(generateRoomCode())
            etRoom.isEnabled = false
        } else {
            btnClientMode.setBackgroundResource(R.drawable.bg_card_active)
            btnHostMode.setBackgroundResource(R.drawable.bg_card_inactive)
            btnShare.visibility = View.GONE
            btnWatch.visibility = View.VISIBLE
            etRoom.hint = getString(R.string.main_room_hint)
            etRoom.setText("")
            etRoom.isEnabled = true
        }

        updateCardIcons(hostMode)
    }

    private fun updateCardIcons(hostMode: Boolean) {
        val hostIcon = btnHostMode.getChildAt(0) as? ImageView
        val clientIcon = btnClientMode.getChildAt(0) as? ImageView

        hostIcon?.setImageResource(R.drawable.ic_cast)
        clientIcon?.setImageResource(R.drawable.ic_eye)

        if (hostMode) {
            hostIcon?.setColorFilter(getColor(R.color.accent))
            clientIcon?.setColorFilter(getColor(R.color.text_secondary))
        } else {
            hostIcon?.setColorFilter(getColor(R.color.text_secondary))
            clientIcon?.setColorFilter(getColor(R.color.accent))
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val part1 = (1..3).map { chars.random() }.joinToString("")
        val part2 = (1..3).map { chars.random() }.joinToString("")
        return "$part1-$part2"
    }

    private fun requestPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())
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
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun goToViewer() {
        if (!validateInput()) return
        val intent = Intent(this, ViewerActivity::class.java).apply {
            putExtra("room", etRoom.text.toString().trim())
        }
        startActivity(intent)
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
