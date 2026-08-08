package com.example.screenmirror

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.SurfaceViewRenderer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SenderActivity : AppCompatActivity() {

    private lateinit var renderer: SurfaceViewRenderer
    private lateinit var controlPanel: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvRoom: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvViewerCount: TextView
    private lateinit var tvViewerLabel: TextView
    private lateinit var statusDot: View
    private lateinit var ivRecord: ImageView
    private lateinit var tvRecord: TextView
    private lateinit var ivFreeze: ImageView
    private lateinit var tvFreeze: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var panelVisible = true
    private var isRecording = false
    private var isFrozen = false
    private var recordingStartTime = 0L
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && recordingStartTime > 0) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                val seconds = (elapsed / 1000).toInt()
                val mins = seconds / 60
                val secs = seconds % 60
                tvTimer.text = String.format("%02d:%02d", mins, secs)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_sender)

        renderer = findViewById(R.id.senderSurface)
        controlPanel = findViewById(R.id.controlPanel)
        tvStatus = findViewById(R.id.tvSenderStatus)
        tvRoom = findViewById(R.id.tvSenderRoom)
        tvStats = findViewById(R.id.tvSenderStats)
        tvTimer = findViewById(R.id.tvTimer)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvViewerLabel = findViewById(R.id.tvViewerLabel)
        statusDot = findViewById(R.id.senderStatusDot)
        ivRecord = findViewById(R.id.ivRecord)
        tvRecord = findViewById(R.id.tvRecord)
        ivFreeze = findViewById(R.id.ivFreeze)
        tvFreeze = findViewById(R.id.tvFreeze)

        val room = intent.getStringExtra("room") ?: ""
        tvRoom.text = room

        val resultCode = intent.getIntExtra("resultCode", 0)
        @Suppress("DEPRECATION")
        val projectionData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("projectionData", Intent::class.java)
        } else {
            intent.getParcelableExtra("projectionData")
        }

        ScreenShareService.renderer = renderer
        ScreenShareService.onState = { s ->
            handler.post { updateState(s) }
        }
        ScreenShareService.onViewerCountChanged = { count ->
            handler.post {
                tvViewerCount.text = count.toString()
                tvViewerLabel.text = if (count == 1) "İzleyici" else "İzleyici"
            }
        }

        controlPanel.setOnClickListener { togglePanel() }

        findViewById<View>(R.id.btnScreenshot).setOnClickListener {
            takeScreenshot()
        }

        findViewById<View>(R.id.btnRecord).setOnClickListener {
            toggleRecording()
        }

        findViewById<View>(R.id.btnFreeze).setOnClickListener {
            toggleFreeze()
        }

        findViewById<View>(R.id.btnQuality).setOnClickListener {
            showQualityDialog()
        }

        findViewById<View>(R.id.btnStop).setOnClickListener {
            if (isRecording) {
                stopRecording()
            }
            stopService(Intent(this, ScreenShareService::class.java))
            finish()
        }

        renderer.postDelayed({
            startServiceWithProjection(resultCode, projectionData)
        }, 500)

        handler.postDelayed({ panelVisible = false; controlPanel.visibility = View.GONE }, 4000)
    }

    private fun showQualityDialog() {
        val qualities = arrayOf(
            "HD (1280x720)",
            "SD (854x480)",
            "Dusuk (640x360)"
        )
        val sizes = arrayOf(
            intArrayOf(1280, 720),
            intArrayOf(854, 480),
            intArrayOf(640, 360)
        )
        val fpsOptions = arrayOf("30 FPS", "24 FPS", "15 FPS")
        val fpsValues = intArrayOf(30, 24, 15)

        AlertDialog.Builder(this)
            .setTitle("Yayin Kalitesi")
            .setItems(qualities) { _, which ->
                ScreenShareService.captureWidth = sizes[which][0]
                ScreenShareService.captureHeight = sizes[which][1]

                AlertDialog.Builder(this)
                    .setTitle("Kare Hizi")
                    .setItems(fpsOptions) { _, fpsWhich ->
                        ScreenShareService.captureFps = fpsValues[fpsWhich]
                        tvStats.text = "${sizes[which][0]}x${sizes[which][1]} @ ${fpsValues[fpsWhich]}fps"
                    }
                    .show()
            }
            .show()
    }

    private fun takeScreenshot() {
        try {
            val bitmap = Bitmap.createBitmap(renderer.width, renderer.height, Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(renderer, null, bitmap, { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    saveScreenshot(bitmap)
                } else {
                    handler.post { Toast.makeText(this, "Ekran goruntusu alinamadi", Toast.LENGTH_SHORT).show() }
                }
            }, handler)
        } catch (e: Exception) {
            Toast.makeText(this, "Ekran goruntusu alinamadi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "ScreenMirror_$timestamp.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ScreenMirror")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { os ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                    }
                }
            } else {
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val dir = File(path, "ScreenMirror")
                dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file).use { os ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
            }

            bitmap.recycle()
            handler.post { Toast.makeText(this, "Ekran goruntusu kaydedildi", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            handler.post { Toast.makeText(this, "Ekran goruntusu kaydedilemedi", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "ScreenMirror_$timestamp.mp4"

            val dir = File(getExternalFilesDir(null), "Recordings")
            dir.mkdirs()
            recordingFile = File(dir, filename)

            mediaRecorder = if (Build.VERSION.SDK_INT >= 31) {
                MediaRecorder()
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(ScreenShareService.captureWidth, ScreenShareService.captureHeight)
                setVideoFrameRate(ScreenShareService.captureFps)
                setVideoEncodingBitRate(6_000_000)
                setOutputFile(recordingFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            handler.post(timerRunnable)

            ivRecord.setImageResource(R.drawable.ic_stop)
            ivRecord.setColorFilter(resources.getColor(R.color.status_bad, null))
            tvRecord.text = "Durdur"
            tvRecord.setTextColor(resources.getColor(R.color.status_bad, null))
            tvTimer.visibility = View.VISIBLE

            Toast.makeText(this, "Kayit baslatildi", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Kayit baslatilamadi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (_: Exception) {
            try { mediaRecorder?.release() } catch (_: Exception) {}
        }
        mediaRecorder = null

        isRecording = false
        recordingStartTime = 0
        handler.removeCallbacks(timerRunnable)

        ivRecord.setImageResource(R.drawable.ic_record)
        ivRecord.clearColorFilter()
        tvRecord.text = "Kaydet"
        tvRecord.setTextColor(resources.getColor(R.color.text_secondary, null))
        tvTimer.visibility = View.GONE
        tvTimer.text = "00:00"

        Toast.makeText(this, "Kayit kaydedildi: ${recordingFile?.name}", Toast.LENGTH_SHORT).show()
    }

    private fun toggleFreeze() {
        isFrozen = !isFrozen
        ScreenShareService.isFrozen = isFrozen
        if (isFrozen) {
            ivFreeze.setImageResource(R.drawable.ic_play)
            tvFreeze.text = "Devam"
            Toast.makeText(this, "Yayin donduruldu", Toast.LENGTH_SHORT).show()
        } else {
            ivFreeze.setImageResource(R.drawable.ic_frozen)
            tvFreeze.text = "Dondur"
            Toast.makeText(this, "Yayin devam ediyor", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startServiceWithProjection(resultCode: Int, data: Intent?) {
        if (data == null) {
            Toast.makeText(this, "Ekran paylasim izni alinamadi", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        ScreenShareService.pendingResultCode = resultCode
        ScreenShareService.pendingData = data

        val svc = Intent(this, ScreenShareService::class.java).apply {
            putExtra("role", "sender")
            putExtra("room", intent.getStringExtra("room") ?: "")
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
    }

    private fun togglePanel() {
        panelVisible = !panelVisible
        controlPanel.visibility = if (panelVisible) View.VISIBLE else View.GONE
        if (panelVisible) {
            handler.postDelayed({ panelVisible = false; controlPanel.visibility = View.GONE }, 3000)
        }
    }

    private fun updateState(state: String) {
        tvStatus.text = when {
            state.contains("ICE: CONNECTED") -> "Baglanti kuruldu"
            state.contains("ICE: DISCONNECTED") -> "Baglanti kesildi"
            state.contains("ICE: FAILED") -> "Baglanti hatasi"
            state.contains("Ekran yayinda") -> "Ekran paylasiliyor"
            state.contains("hazir") || state.contains("Hazir") -> "Hazir, izleyici bekleniyor"
            state.contains("donduruldu") -> "Yayin donduruldu"
            state.contains("devam ediyor") -> "Yayin devam ediyor"
            state.contains("Izleyici ayrildi") -> "Izleyici ayrildi"
            else -> state
        }

        val color = when {
            state.contains("CONNECTED") || state.contains("yayinda") ->
                resources.getColor(R.color.status_good, null)
            state.contains("DISCONNECTED") || state.contains("FAILED") || state.contains("HATA") ->
                resources.getColor(R.color.status_bad, null)
            state.contains("donduruldu") ->
                resources.getColor(R.color.status_warn, null)
            else -> resources.getColor(R.color.status_warn, null)
        }
        statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (_: Exception) {}
            mediaRecorder = null
        }
        try {
            stopService(Intent(this, ScreenShareService::class.java))
        } catch (_: Exception) {}
        ScreenShareService.renderer = null
        ScreenShareService.onState = null
        ScreenShareService.onViewerCountChanged = null
    }
}
