package com.example.screenmirror

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ViewerActivity : AppCompatActivity() {

    private lateinit var tvViewerStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvViewerCount: TextView
    private lateinit var tvConnectionQuality: TextView
    private lateinit var ivConnectionDot: ImageView
    private lateinit var btnScreenshot: View
    private lateinit var btnDisconnect: Button
    private lateinit var controlPanel: View

    private var receiver: BroadcastReceiver? = null
    private var startTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        createNotificationChannel()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvViewerStatus = findViewById(R.id.tvViewerStatus)
        tvStats = findViewById(R.id.tvStats)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvConnectionQuality = findViewById(R.id.tvConnectionQuality)
        ivConnectionDot = findViewById(R.id.ivConnectionDot)
        btnScreenshot = findViewById(R.id.btnScreenshot)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        controlPanel = findViewById(R.id.controlPanel)

        startTime = System.currentTimeMillis()
        registerReceiver()
        startStatsChecker()

        tvConnectionQuality.visibility = if (AppSettings.isQualityStatsEnabled(this)) View.VISIBLE else View.GONE

        tvStats.text = getString(R.string.viewer_stats)

        btnDisconnect.setOnClickListener { showDisconnectConfirmation() }
        btnScreenshot.setOnClickListener { takeScreenshot() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationConstants.CHANNEL_ID_VIEWER,
                NotificationConstants.CHANNEL_NAME_VIEWER,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "com.example.screenmirror.SENDER_DISCONNECTED" -> {
                        runOnUiThread {
                            tvViewerStatus.text = getString(R.string.status_disconnected)
                            Toast.makeText(context, getString(R.string.viewer_broadcast_ended), Toast.LENGTH_SHORT).show()
                            lifecycleScope.launch {
                                delay(2000)
                                finish()
                            }
                        }
                    }
                    "com.example.screenmirror.VIEWER_COUNT_CHANGED" -> {
                        val count = intent.getIntExtra("viewer_count", 0)
                        runOnUiThread { tvViewerCount.text = count.toString() }
                    }
                    "com.example.screenmirror.CONNECTION_QUALITY" -> {
                        val quality = intent.getStringExtra("quality") ?: ""
                        val rtt = intent.getIntExtra("rtt", 0)
                        val fps = intent.getIntExtra("fps", 0)
                        val packetLoss = intent.getDoubleExtra("packet_loss", 0.0)
                        runOnUiThread {
                            if (!AppSettings.isQualityStatsEnabled(this@ViewerActivity)) {
                                tvConnectionQuality.visibility = View.GONE
                                ivConnectionDot.visibility = View.GONE
                                return@runOnUiThread
                            }
                            tvConnectionQuality.visibility = View.VISIBLE
                            ivConnectionDot.visibility = View.VISIBLE
                            tvConnectionQuality.text = getString(R.string.quality_stats_format, rtt, fps, packetLoss)

                            val colorRes = when (quality) {
                                "IYI" -> R.color.dark_status_good
                                "ORTA" -> R.color.dark_status_warning
                                else -> R.color.dark_status_error
                            }
                            val color = ContextCompat.getColor(context, colorRes)
                            tvConnectionQuality.setTextColor(color)
                            ivConnectionDot.setColorFilter(color)
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.example.screenmirror.SENDER_DISCONNECTED")
            addAction("com.example.screenmirror.VIEWER_COUNT_CHANGED")
            addAction("com.example.screenmirror.CONNECTION_QUALITY")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun showDisconnectConfirmation() {
        AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.viewer_disconnect_title))
            .setMessage(getString(R.string.viewer_disconnect_msg))
            .setPositiveButton(getString(R.string.viewer_disconnect_confirm)) { _, _ -> cleanupAndFinish() }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun cleanupAndFinish() {
        sendBroadcast(Intent("com.example.screenmirror.DISCONNECT_VIEWER").apply {
            setPackage(packageName)
        })
        try { receiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        finish()
    }

    private fun startStatsChecker() {
        lifecycleScope.launch {
            while (isActive) {
                if (!AppSettings.isQualityStatsEnabled(this@ViewerActivity)) {
                    tvConnectionQuality.visibility = View.GONE
                    delay(2000)
                    continue
                }
                sendBroadcast(Intent("com.example.screenmirror.REQUEST_STATS").apply {
                    setPackage(packageName)
                })
                delay(2000)
            }
        }
    }

    private fun takeScreenshot() {
        val screenView = window.decorView.rootView
        val bitmap = Bitmap.createBitmap(screenView.width, screenView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        screenView.draw(canvas)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "screenmirror_viewer_$timestamp.png"

        val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_PICTURES
        )
        val screenmirrorDir = File(picturesDir, "ScreenMirror")
        if (!screenmirrorDir.exists()) screenmirrorDir.mkdirs()

        val file = File(screenmirrorDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Toast.makeText(this, getString(R.string.viewer_screenshot_saved, filename), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { receiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
    }
}
