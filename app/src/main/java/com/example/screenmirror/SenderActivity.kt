package com.example.screenmirror

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var statusDot: View
    private lateinit var ivRecord: ImageView
    private lateinit var tvRecord: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var panelVisible = true
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_sender)

        renderer = findViewById(R.id.senderSurface)
        controlPanel = findViewById(R.id.controlPanel)
        tvStatus = findViewById(R.id.tvSenderStatus)
        tvRoom = findViewById(R.id.tvSenderRoom)
        tvStats = findViewById(R.id.tvSenderStats)
        statusDot = findViewById(R.id.senderStatusDot)
        ivRecord = findViewById(R.id.ivRecord)
        tvRecord = findViewById(R.id.tvRecord)

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

        controlPanel.setOnClickListener { togglePanel() }

        findViewById<View>(R.id.btnScreenshot).setOnClickListener {
            takeScreenshot()
        }

        findViewById<View>(R.id.btnRecord).setOnClickListener {
            toggleRecording()
        }

        findViewById<View>(R.id.btnPause).setOnClickListener {
            Toast.makeText(this, "Duraklat/Devam", Toast.LENGTH_SHORT).show()
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
        isRecording = true
        ivRecord.setImageResource(R.drawable.ic_stop)
        ivRecord.setColorFilter(resources.getColor(R.color.status_bad, null))
        tvRecord.text = "Durdur"
        tvRecord.setTextColor(resources.getColor(R.color.status_bad, null))
        Toast.makeText(this, "Kayıt başladı", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        ivRecord.setImageResource(R.drawable.ic_record)
        ivRecord.clearColorFilter()
        tvRecord.text = "Kaydet"
        tvRecord.setTextColor(resources.getColor(R.color.text_secondary, null))
        Toast.makeText(this, "Kayıt durduruldu", Toast.LENGTH_SHORT).show()
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
            else -> state
        }

        val color = when {
            state.contains("CONNECTED") || state.contains("yayinda") ->
                resources.getColor(R.color.status_good, null)
            state.contains("DISCONNECTED") || state.contains("FAILED") || state.contains("HATA") ->
                resources.getColor(R.color.status_bad, null)
            else -> resources.getColor(R.color.status_warn, null)
        }
        statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            stopService(Intent(this, ScreenShareService::class.java))
        } catch (_: Exception) {}
        ScreenShareService.renderer = null
        ScreenShareService.onState = null
    }
}
