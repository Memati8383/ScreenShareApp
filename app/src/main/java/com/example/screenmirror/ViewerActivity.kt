package com.example.screenmirror

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.SurfaceViewRenderer

class ViewerActivity : AppCompatActivity() {

    private lateinit var renderer: SurfaceViewRenderer
    private lateinit var controlPanel: LinearLayout
    private lateinit var waitingOverlay: LinearLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
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
        statusDot = findViewById(R.id.statusDot)

        val room = intent.getStringExtra("room") ?: ""

        ScreenShareService.renderer = renderer
        ScreenShareService.onState = { s ->
            handler.post { updateState(s) }
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

        renderer.postDelayed({
            val svc = Intent(this, ScreenShareService::class.java).apply {
                putExtra("role", "viewer")
                putExtra("room", room)
            }
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
        }, 500)
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
    }
}
