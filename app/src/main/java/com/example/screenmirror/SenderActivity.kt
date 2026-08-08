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

class SenderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROOM_CODE = "room_code"
        const val EXTRA_CAPTURE_WIDTH = "capture_width"
        const val EXTRA_CAPTURE_HEIGHT = "capture_height"
        const val EXTRA_CAPTURE_FPS = "capture_fps"
        private const val NOTIFICATION_CHANNEL_ID = "screen_share_channel"
    }

    private lateinit var tvSenderStatus: TextView
    private lateinit var tvSenderRoom: TextView
    private lateinit var tvSenderStats: TextView
    private lateinit var tvViewerCount: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvConnectionQuality: TextView
    private lateinit var btnScreenshot: View
    private lateinit var btnRecord: View
    private lateinit var btnFreeze: View
    private lateinit var btnQuality: View
    private lateinit var btnStop: Button
    private lateinit var controlPanel: View

    private var receiver: BroadcastReceiver? = null
    private var isSharing = false
    private var isRecording = false
    private var isFrozen = false
    private var startTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var statsCheckerRunnable: Runnable? = null
    private var recordRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)

        createNotificationChannel()

        tvSenderStatus = findViewById(R.id.tvSenderStatus)
        tvSenderRoom = findViewById(R.id.tvSenderRoom)
        tvSenderStats = findViewById(R.id.tvSenderStats)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvTimer = findViewById(R.id.tvTimer)
        tvConnectionQuality = findViewById(R.id.tvConnectionQuality)
        btnScreenshot = findViewById(R.id.btnScreenshot)
        btnRecord = findViewById(R.id.btnRecord)
        btnFreeze = findViewById(R.id.btnFreeze)
        btnQuality = findViewById(R.id.btnQuality)
        btnStop = findViewById(R.id.btnStop)
        controlPanel = findViewById(R.id.controlPanel)

        val toolbar = findViewById<MaterialToolbar?>(R.id.toolbar)

        val code = intent.getStringExtra(EXTRA_ROOM_CODE) ?: "000000"
        tvSenderRoom.text = "Oda: $code"

        val captureWidth = intent.getIntExtra(EXTRA_CAPTURE_WIDTH, 1280)
        val captureHeight = intent.getIntExtra(EXTRA_CAPTURE_HEIGHT, 720)
        val captureFps = intent.getIntExtra(EXTRA_CAPTURE_FPS, 30)

        ScreenShareService.captureWidth = captureWidth
        ScreenShareService.captureHeight = captureHeight
        ScreenShareService.captureFps = captureFps

        btnFreeze.setOnClickListener {
            ScreenShareService.isFrozen = !ScreenShareService.isFrozen
            isFrozen = ScreenShareService.isFrozen
            val freezeText = btnFreeze.findViewById<TextView>(R.id.tvFreeze)
            freezeText?.text = if (isFrozen) "Devam" else "Dondur"
            Toast.makeText(this, if (isFrozen) "Yayın duraklatıldı" else "Yayın devam ediyor", Toast.LENGTH_SHORT).show()
        }

        btnScreenshot.setOnClickListener { takeScreenshot() }

        btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        btnStop.setOnClickListener {
            if (isSharing) showStopConfirmation()
        }

        tvConnectionQuality.visibility = if (AppSettings.isQualityStatsEnabled(this)) View.VISIBLE else View.GONE

        startSharing()
        startStatsChecker()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Ekran Paylaşımı",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startSharing() {
        isSharing = true
        startTime = System.currentTimeMillis()
        tvSenderStatus.text = getString(R.string.status_live)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "com.example.screenmirror.PEER_LEFT" -> {
                        Toast.makeText(context, "İzleyici ayrıldı", Toast.LENGTH_SHORT).show()
                        val count = intent.getIntExtra("viewer_count", 0)
                        updateViewerCount(count)
                    }
                    "com.example.screenmirror.VIEWER_COUNT_CHANGED" -> {
                        val count = intent.getIntExtra("viewer_count", 0)
                        updateViewerCount(count)
                    }
                    "com.example.screenmirror.CONNECTION_QUALITY" -> {
                        val rtt = intent.getIntExtra("rtt", 0)
                        val fps = intent.getIntExtra("fps", 0)
                        val packetLoss = intent.getDoubleExtra("packet_loss", 0.0)
                        updateConnectionQuality(rtt, fps, packetLoss)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.example.screenmirror.PEER_LEFT")
            addAction("com.example.screenmirror.VIEWER_COUNT_CHANGED")
            addAction("com.example.screenmirror.CONNECTION_QUALITY")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), 1001)
    }

    private fun showStopConfirmation() {
        val dialogTheme = if (AppSettings.isDarkTheme(this)) {
            R.style.Theme_ScreenShare_Dialog
        } else {
            R.style.Theme_ScreenShare_Light_Dialog
        }
        AlertDialog.Builder(this, dialogTheme)
            .setTitle("Yayını Durdur")
            .setMessage("Yayını sonlandırmak istediğinize emin misiniz?")
            .setPositiveButton("Durdur") { _, _ ->
                stopService(Intent(this, ScreenShareService::class.java))
                isSharing = false
                finish()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun startStatsChecker() {
        statsCheckerRunnable = object : Runnable {
            override fun run() {
                if (!AppSettings.isQualityStatsEnabled(this@SenderActivity)) {
                    tvConnectionQuality.visibility = View.GONE
                    handler.postDelayed(this, 2000)
                    return
                }
                val intent = Intent("com.example.screenmirror.REQUEST_STATS").apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
                handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(statsCheckerRunnable!!, 2000)
    }

    private fun updateViewerCount(count: Int) {
        runOnUiThread {
            tvViewerCount.text = count.toString()
        }
    }

    private fun updateConnectionQuality(rtt: Int, fps: Int, packetLoss: Double) {
        runOnUiThread {
            if (!AppSettings.isQualityStatsEnabled(this)) {
                tvConnectionQuality.visibility = View.GONE
                return@runOnUiThread
            }

            tvConnectionQuality.visibility = View.VISIBLE
            tvConnectionQuality.text = "RTT: ${rtt}ms | FPS: $fps | Kayıp: %.1f%%".format(packetLoss)

            val color = when {
                rtt < 100 && packetLoss < 1 -> ContextCompat.getColor(this, R.color.dark_status_good)
                rtt < 200 && packetLoss < 3 -> ContextCompat.getColor(this, R.color.dark_status_warning)
                else -> ContextCompat.getColor(this, R.color.dark_status_error)
            }
            tvConnectionQuality.setTextColor(color)
        }
    }

    private fun takeScreenshot() {
        val screenView = window.decorView.rootView
        screenView.isDrawingCacheEnabled = true
        val bitmap = Bitmap.createBitmap(screenView.drawingCache)
        screenView.isDrawingCacheEnabled = false

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "screenmirror_$timestamp.png"

        val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_PICTURES
        )
        val screenmirrorDir = File(picturesDir, "ScreenMirror")
        if (!screenmirrorDir.exists()) {
            screenmirrorDir.mkdirs()
        }

        val file = File(screenmirrorDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        Toast.makeText(this, "Ekran görüntüsü kaydedildi: $filename", Toast.LENGTH_SHORT).show()
    }

    private fun startRecording() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), 1002)
    }

    private fun stopRecording() {
        sendBroadcast(Intent("com.example.screenmirror.STOP_RECORDING").apply {
            setPackage(packageName)
        })
        isRecording = false
        val recordText = btnRecord.findViewById<TextView>(R.id.tvRecord)
        recordText?.text = "Kaydet"
        tvTimer.visibility = View.GONE
        recordRunnable?.let { handler.removeCallbacks(it) }
        Toast.makeText(this, "Kayıt durduruldu", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenShareService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                putExtra(EXTRA_ROOM_CODE, intent.getStringExtra(EXTRA_ROOM_CODE))
            }
            startForegroundService(serviceIntent)
        }

        if (requestCode == 1002 && resultCode == RESULT_OK && data != null) {
            val recordIntent = Intent("com.example.screenmirror.START_RECORDING").apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                setPackage(packageName)
            }
            sendBroadcast(recordIntent)
            isRecording = true
            val recordText = btnRecord.findViewById<TextView>(R.id.tvRecord)
            recordText?.text = "Durdur"
            tvTimer.visibility = View.VISIBLE
            startRecordTimer()
            Toast.makeText(this, "Kayıt başladı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRecordTimer() {
        startTime = System.currentTimeMillis()
        recordRunnable = object : Runnable {
            override fun run() {
                if (!isRecording) return
                val elapsed = System.currentTimeMillis() - startTime
                val mins = elapsed / 60000
                val secs = (elapsed % 60000) / 1000
                tvTimer.text = String.format("%02d:%02d", mins, secs)
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(recordRunnable!!, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        receiver?.let { unregisterReceiver(it) }
        statsCheckerRunnable?.let { handler.removeCallbacks(it) }
        recordRunnable?.let { handler.removeCallbacks(it) }
    }
}
