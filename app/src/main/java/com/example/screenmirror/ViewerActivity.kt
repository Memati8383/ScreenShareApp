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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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

class ViewerActivity : AppCompatActivity() {

    private lateinit var renderer: SurfaceViewRenderer
    private lateinit var controlPanel: LinearLayout
    private lateinit var waitingOverlay: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvViewerCount: TextView
    private lateinit var tvViewerLabel: TextView
    private lateinit var statusDot: View
    private val handler = Handler(Looper.getMainLooper())
    private var panelVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_viewer)

        renderer = findViewById(R.id.viewerSurface)
        controlPanel = findViewById(R.id.controlPanel)
        waitingOverlay = findViewById(R.id.waitingOverlay)
        tvStatus = findViewById(R.id.tvViewerStatus)
        tvStats = findViewById(R.id.tvStats)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvViewerLabel = findViewById(R.id.tvViewerLabel)
        statusDot = findViewById(R.id.statusDot)

        val room = intent.getStringExtra("room") ?: ""

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

        renderer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                togglePanel()
            }
            true
        }

        findViewById<View>(R.id.btnDisconnect).setOnClickListener {
            stopService(Intent(this, ScreenShareService::class.java))
            finish()
        }

        findViewById<View>(R.id.btnScreenshot).setOnClickListener {
            takeScreenshot()
        }

        renderer.postDelayed({
            val svc = Intent(this, ScreenShareService::class.java).apply {
                putExtra("role", "viewer")
                putExtra("room", room)
            }
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
        }, 500)
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
            val filename = "ScreenMirror_Izleyici_$timestamp.png"

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

    private fun togglePanel() {
        panelVisible = !panelVisible
        controlPanel.visibility = if (panelVisible) View.VISIBLE else View.GONE
        if (panelVisible) {
            handler.postDelayed({ panelVisible = false; controlPanel.visibility = View.GONE }, 4000)
        }
    }

    private fun updateState(state: String) {
        when {
            state.contains("ICE: CONNECTED") -> {
                waitingOverlay.visibility = View.GONE
                tvStats.text = "Baglanti aktif"
                statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.status_good, null))
            }
            state.contains("ICE: DISCONNECTED") -> {
                tvStats.text = "Baglanti kesildi"
                statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.status_bad, null))
            }
            state.contains("ICE: FAILED") -> {
                tvStats.text = "Baglanti hatasi"
                statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.status_bad, null))
            }
            state.contains("ICE: CHECKING") -> {
                tvStats.text = "Baglanti kontrol ediliyor..."
                statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.status_warn, null))
            }
            state.contains("Es cihaz") -> {
                tvStats.text = "Yayinci baglandi, bekleniyor..."
            }
            state.contains("Canli goruntu") -> {
                waitingOverlay.visibility = View.GONE
                tvStats.text = "Canli goruntu"
            }
            state.contains("donduruldu") -> {
                tvStats.text = "Yayin donduruldu"
            }
            state.contains("devam ediyor") -> {
                tvStats.text = "Yayin devam ediyor"
            }
            state.contains("Izleyici ayrildi") -> {
                tvStats.text = "Izleyici ayrildi"
            }
            else -> {
                tvStatus.text = state
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            stopService(Intent(this, ScreenShareService::class.java))
        } catch (_: Exception) {}
        ScreenShareService.renderer = null
        ScreenShareService.onState = null
        ScreenShareService.onViewerCountChanged = null
    }
}
