package com.example.screenmirror

import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class SenderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROOM_CODE = "room"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_PROJECTION_DATA = "projectionData"
    }

    private lateinit var tvSenderStatus: TextView
    private lateinit var tvSenderRoom: TextView
    private lateinit var tvSenderStats: TextView
    private lateinit var tvViewerCount: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvConnectionQuality: TextView
    private lateinit var ivConnectionDot: ImageView
    private lateinit var btnScreenshot: View
    private lateinit var btnRecord: View
    private lateinit var btnFreeze: View
    private lateinit var btnQuality: View
    private lateinit var btnStop: Button
    private lateinit var controlPanel: View

    private lateinit var recordLauncher: ActivityResultLauncher<Intent>

    private var receiver: BroadcastReceiver? = null
    private var isSharing = false
    private var isRecording = false
    private var isFrozen = false
    private var startTime = 0L
    private var roomCode = ""

    private lateinit var skeletonHelper: SkeletonAnimHelper
    private lateinit var skeletonContainer: View
    private lateinit var skeletonIcon: View
    private lateinit var skeletonTitle: View
    private lateinit var skeletonSubtitle: View
    private lateinit var skeletonStatus: View
    private lateinit var skeletonProgress: View
    private lateinit var skeletonHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)

        createNotificationChannel()
        initSkeleton()

        tvSenderStatus = findViewById(R.id.tvSenderStatus)
        tvSenderRoom = findViewById(R.id.tvSenderRoom)
        tvSenderStats = findViewById(R.id.tvSenderStats)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvTimer = findViewById(R.id.tvTimer)
        tvConnectionQuality = findViewById(R.id.tvConnectionQuality)
        ivConnectionDot = findViewById(R.id.ivConnectionDot)
        btnScreenshot = findViewById(R.id.btnScreenshot)
        btnRecord = findViewById(R.id.btnRecord)
        btnFreeze = findViewById(R.id.btnFreeze)
        btnQuality = findViewById(R.id.btnQuality)
        btnStop = findViewById(R.id.btnStop)
        controlPanel = findViewById(R.id.controlPanel)

        recordLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenShareService::class.java).apply {
                    action = "com.example.screenmirror.START_RECORDING"
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                startService(serviceIntent)
                isRecording = true
                val recordText = btnRecord.findViewById<TextView>(R.id.tvRecord)
                recordText?.text = getString(R.string.sender_stop_record)
                tvTimer.visibility = View.VISIBLE
                startRecordTimer()
                Toast.makeText(this, getString(R.string.sender_record_started), Toast.LENGTH_SHORT).show()
            }
        }

        roomCode = intent.getStringExtra(EXTRA_ROOM_CODE) ?: "000000"
        tvSenderRoom.text = getString(R.string.sender_room_prefix, roomCode)

        val senderSurface = findViewById<org.webrtc.SurfaceViewRenderer>(R.id.senderSurface)
        ScreenShareService.renderer = senderSurface
        Log.i("SenderActivity", "renderer atandi: ${senderSurface != null}")

        btnFreeze.setOnClickListener {
            ScreenShareService.isFrozen = !ScreenShareService.isFrozen
            isFrozen = ScreenShareService.isFrozen
            val freezeText = btnFreeze.findViewById<TextView>(R.id.tvFreeze)
            freezeText?.text = if (isFrozen) getString(R.string.sender_unfreeze) else getString(R.string.sender_freeze)
            Toast.makeText(this, if (isFrozen) getString(R.string.sender_frozen) else getString(R.string.sender_resumed), Toast.LENGTH_SHORT).show()
        }

        btnScreenshot.setOnClickListener { takeScreenshot() }

        btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        btnStop.setOnClickListener {
            if (isSharing) showStopConfirmation()
        }

        tvConnectionQuality.visibility = if (AppSettings.isQualityStatsEnabled(this)) View.VISIBLE else View.GONE

        showSkeleton("Ekran paylaşımı başlatılıyor...")
        startService()
        registerBroadcastReceiver()
        startStatsChecker()
    }

    private fun initSkeleton() {
        skeletonHelper = SkeletonAnimHelper()
        skeletonContainer = findViewById(R.id.skeletonContainer)
        skeletonIcon = findViewById(R.id.skeletonIcon)
        skeletonTitle = findViewById(R.id.skeletonTitle)
        skeletonSubtitle = findViewById(R.id.skeletonSubtitle)
        skeletonStatus = findViewById(R.id.skeletonStatus)
        skeletonProgress = findViewById(R.id.skeletonProgress)
        skeletonHint = findViewById(R.id.skeletonHint)
    }

    private fun showSkeleton(hint: String) {
        skeletonContainer.visibility = View.VISIBLE
        skeletonHelper.updateHintText(skeletonHint, hint)
        skeletonHelper.startSkeletonAnimation(
            skeletonIcon, skeletonTitle, skeletonSubtitle, skeletonStatus
        )
    }

    private fun hideSkeleton() {
        skeletonHelper.hideSkeleton(skeletonContainer)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationConstants.CHANNEL_ID_SCREEN,
                NotificationConstants.CHANNEL_NAME_SCREEN,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startService() {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val projectionData = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)

        if (resultCode == 0 || projectionData == null) {
            showSkeleton("HATA: Ekran paylaşımı izni alınamadı")
            return
        }

        val serviceIntent = Intent(this, ScreenShareService::class.java).apply {
            putExtra("role", "sender")
            putExtra("room", roomCode)
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra("data", projectionData)
        }
        startForegroundService(serviceIntent)

        isSharing = true
        startTime = System.currentTimeMillis()
        tvSenderStatus.text = getString(R.string.status_live)
    }

    private fun registerBroadcastReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "com.example.screenmirror.PEER_LEFT" -> {
                        val count = intent.getIntExtra("viewer_count", 0)
                        updateViewerCount(count)
                    }
                    "com.example.screenmirror.VIEWER_COUNT_CHANGED" -> {
                        val count = intent.getIntExtra("viewer_count", 0)
                        updateViewerCount(count)
                    }
                    "com.example.screenmirror.CONNECTION_QUALITY" -> {
                        val quality = intent.getStringExtra("quality") ?: ""
                        val rtt = intent.getIntExtra("rtt", 0)
                        val fps = intent.getIntExtra("fps", 0)
                        val packetLoss = intent.getDoubleExtra("packet_loss", 0.0)
                        updateConnectionQuality(quality, rtt, fps, packetLoss)
                    }
                    "com.example.screenmirror.STATE_CHANGED" -> {
                        val state = intent.getStringExtra("state") ?: ""
                        tvSenderStats.text = state
                        if (state in listOf("WebRTC hazir", "Izleyici bekleniyor...", "Ekran yayinda", "Es cihaz baglandi", "Canli goruntu aliniyor")) {
                            hideSkeleton()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.example.screenmirror.PEER_LEFT")
            addAction("com.example.screenmirror.VIEWER_COUNT_CHANGED")
            addAction("com.example.screenmirror.CONNECTION_QUALITY")
            addAction("com.example.screenmirror.STATE_CHANGED")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    private fun showStopConfirmation() {
        AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.sender_stop_title))
            .setMessage(getString(R.string.sender_stop_msg))
            .setPositiveButton(getString(R.string.sender_stop_confirm)) { _, _ ->
                stopService(Intent(this, ScreenShareService::class.java))
                isSharing = false
                finish()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun startStatsChecker() {
        lifecycleScope.launch {
            while (isActive) {
                if (!AppSettings.isQualityStatsEnabled(this@SenderActivity)) {
                    tvConnectionQuality.visibility = View.GONE
                    delay(2000)
                    continue
                }
                val intent = Intent("com.example.screenmirror.REQUEST_STATS").apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
                delay(2000)
            }
        }
    }

    private fun updateViewerCount(count: Int) {
        runOnUiThread {
            tvViewerCount.text = count.toString()
        }
    }

    private fun updateConnectionQuality(quality: String, rtt: Int, fps: Int, packetLoss: Double) {
        runOnUiThread {
            if (!AppSettings.isQualityStatsEnabled(this)) {
                tvConnectionQuality.visibility = View.GONE
                ivConnectionDot.visibility = View.GONE
                return@runOnUiThread
            }

            tvConnectionQuality.visibility = View.VISIBLE
            ivConnectionDot.visibility = View.VISIBLE
            tvConnectionQuality.text = getString(R.string.quality_stats_format, rtt, fps, packetLoss)

            val (colorRes, iconRes) = when (quality) {
                "IYI" -> R.color.dark_status_good to R.drawable.ic_signal
                "ORTA" -> R.color.dark_status_warning to R.drawable.ic_signal
                else -> R.color.dark_status_error to R.drawable.ic_signal
            }

            val color = ContextCompat.getColor(this, colorRes)
            tvConnectionQuality.setTextColor(color)
            ivConnectionDot.setColorFilter(color)
        }
    }

    private fun takeScreenshot() {
        val screenView = window.decorView.rootView
        val bitmap = Bitmap.createBitmap(screenView.width, screenView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        screenView.draw(canvas)

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

        Toast.makeText(this, getString(R.string.sender_screenshot_saved, filename), Toast.LENGTH_SHORT).show()
    }

    private fun startRecording() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        recordLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopRecording() {
        val serviceIntent = Intent(this, ScreenShareService::class.java).apply {
            action = "com.example.screenmirror.STOP_RECORDING"
        }
        startService(serviceIntent)
        isRecording = false
        val recordText = btnRecord.findViewById<TextView>(R.id.tvRecord)
        recordText?.text = getString(R.string.sender_record)
        tvTimer.visibility = View.GONE
        Toast.makeText(this, getString(R.string.sender_record_stopped), Toast.LENGTH_SHORT).show()
    }

    private fun startRecordTimer() {
        startTime = System.currentTimeMillis()
        lifecycleScope.launch {
            while (isActive && isRecording) {
                val elapsed = System.currentTimeMillis() - startTime
                val mins = elapsed / 60000
                val secs = (elapsed % 60000) / 1000
                tvTimer.text = String.format("%02d:%02d", mins, secs)
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        skeletonHelper.stopAnimation()
        receiver?.let { unregisterReceiver(it) }
    }
}
