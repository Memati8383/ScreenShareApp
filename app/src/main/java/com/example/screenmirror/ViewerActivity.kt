package com.example.screenmirror

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ViewerActivity : AppCompatActivity() {

    private lateinit var tvViewerStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvViewerCount: TextView
    private lateinit var tvConnectionQuality: TextView
    private lateinit var btnScreenshot: View
    private lateinit var btnDisconnect: Button
    private lateinit var controlPanel: View

    private var receiver: BroadcastReceiver? = null
    private var startTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var statsCheckerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        createNotificationChannel()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvViewerStatus = findViewById(R.id.tvViewerStatus)
        tvStats = findViewById(R.id.tvStats)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvConnectionQuality = findViewById(R.id.tvConnectionQuality)
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
                "viewer_channel",
                "Ekran İzleme",
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
                            Toast.makeText(context, "Yayın sona erdi", Toast.LENGTH_SHORT).show()
                            handler.postDelayed({ finish() }, 2000)
                        }
                    }
                    "com.example.screenmirror.VIEWER_COUNT_CHANGED" -> {
                        val count = intent.getIntExtra("viewer_count", 0)
                        runOnUiThread { tvViewerCount.text = count.toString() }
                    }
                    "com.example.screenmirror.CONNECTION_QUALITY" -> {
                        val rtt = intent.getIntExtra("rtt", 0)
                        val fps = intent.getIntExtra("fps", 0)
                        val packetLoss = intent.getDoubleExtra("packet_loss", 0.0)
                        runOnUiThread {
                            if (!AppSettings.isQualityStatsEnabled(this@ViewerActivity)) {
                                tvConnectionQuality.visibility = View.GONE
                                return@runOnUiThread
                            }
                            tvConnectionQuality.visibility = View.VISIBLE
                            tvConnectionQuality.text = "RTT: ${rtt}ms | FPS: $fps | Kayıp: %.1f%%".format(packetLoss)

                            val color = when {
                                rtt < 100 && packetLoss < 1 -> ContextCompat.getColor(context, R.color.dark_status_good)
                                rtt < 200 && packetLoss < 3 -> ContextCompat.getColor(context, R.color.dark_status_warning)
                                else -> ContextCompat.getColor(context, R.color.dark_status_error)
                            }
                            tvConnectionQuality.setTextColor(color)
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
        val dialogTheme = if (AppSettings.isDarkTheme(this)) {
            R.style.Theme_ScreenShare_Dialog
        } else {
            R.style.Theme_ScreenShare_Light_Dialog
        }
        AlertDialog.Builder(this, dialogTheme)
            .setTitle("Bağlantıyı Kes")
            .setMessage("Yayından ayrılmak istediğinize emin misiniz?")
            .setPositiveButton("Ayrıl") { _, _ -> cleanupAndFinish() }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun cleanupAndFinish() {
        sendBroadcast(Intent("com.example.screenmirror.DISCONNECT_VIEWER").apply {
            setPackage(packageName)
        })
        try { receiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        statsCheckerRunnable?.let { handler.removeCallbacks(it) }
        finish()
    }

    private fun startStatsChecker() {
        statsCheckerRunnable = object : Runnable {
            override fun run() {
                if (!AppSettings.isQualityStatsEnabled(this@ViewerActivity)) {
                    tvConnectionQuality.visibility = View.GONE
                    handler.postDelayed(this, 2000)
                    return
                }
                sendBroadcast(Intent("com.example.screenmirror.REQUEST_STATS").apply {
                    setPackage(packageName)
                })
                handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(statsCheckerRunnable!!, 2000)
    }

    private fun takeScreenshot() {
        val screenView = window.decorView.rootView
        screenView.isDrawingCacheEnabled = true
        val bitmap = Bitmap.createBitmap(screenView.drawingCache)
        screenView.isDrawingCacheEnabled = false

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
        Toast.makeText(this, "Ekran görüntüsü kaydedildi: $filename", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { receiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        statsCheckerRunnable?.let { handler.removeCallbacks(it) }
    }
}
