package com.example.screenmirror

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.screenmirror.model.ConnectionQuality
import com.example.screenmirror.model.ServiceEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private var service: ScreenShareService? = null
    private var isBound = false
    private var startTime = 0L
    private var roomCode = ""
    private var isDisconnecting = false
    private var skeletonTimeoutJob: kotlinx.coroutines.Job? = null

    private lateinit var skeletonHelper: SkeletonAnimHelper
    private lateinit var waitingOverlay: View

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as ScreenShareService.LocalBinder
            service = localBinder.getService()
            isBound = true
            observeServiceEvents()
            Log.i("ViewerActivity", getString(R.string.state_service_connected))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            Log.i("ViewerActivity", getString(R.string.state_service_disconnected))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        NotificationHelper.createViewerChannel(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        initSkeleton()

        tvViewerStatus = findViewById(R.id.tvViewerStatus)
        tvStats = findViewById(R.id.tvStats)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvConnectionQuality = findViewById(R.id.tvConnectionQuality)
        ivConnectionDot = findViewById(R.id.ivConnectionDot)
        btnScreenshot = findViewById(R.id.btnScreenshot)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        controlPanel = findViewById(R.id.controlPanel)

        roomCode = intent.getStringExtra("room") ?: ""

        startTime = System.currentTimeMillis()

        tvConnectionQuality.visibility = if (AppSettings.isQualityStatsEnabled(this)) View.VISIBLE else View.GONE
        tvStats.text = getString(R.string.viewer_stats)

        waitingOverlay = findViewById(R.id.waitingOverlay)

        showSkeleton(getString(R.string.viewer_waiting))
        startViewerService()
        btnDisconnect.setOnClickListener { showDisconnectConfirmation() }
        btnScreenshot.setOnClickListener { takeScreenshot() }
    }

    private fun observeServiceEvents() {
        val svc = service ?: return
        lifecycleScope.launch {
            svc.getStateManager().event.collectLatest { event ->
                when (event) {
                    is ServiceEvent.WebRtcReady -> {
                        tvStats.text = getString(R.string.state_webrtc_ready)
                        hideSkeleton()
                        waitingOverlay.visibility = View.GONE
                    }
                    is ServiceEvent.PeerConnected -> {
                        tvStats.text = getString(R.string.state_peer_connected)
                        hideSkeleton()
                        waitingOverlay.visibility = View.GONE
                    }
                    is ServiceEvent.LiveReceived -> {
                        tvStats.text = getString(R.string.state_live_received)
                        hideSkeleton()
                        waitingOverlay.visibility = View.GONE
                    }
                    is ServiceEvent.OfferSent -> {
                        tvStats.text = getString(R.string.state_offer_sent)
                        hideSkeleton()
                        waitingOverlay.visibility = View.GONE
                    }
                    is ServiceEvent.AnswerSent -> {
                        tvStats.text = getString(R.string.state_answer_sent)
                        hideSkeleton()
                        waitingOverlay.visibility = View.GONE
                    }
                    is ServiceEvent.ConnectionBroken -> {
                        tvStats.text = getString(R.string.state_connection_broken)
                        hideSkeleton()
                    }
                    is ServiceEvent.ViewerCountChanged -> {
                        tvViewerCount.text = event.count.toString()
                    }
                    is ServiceEvent.ConnectionQualityChanged -> {
                        hideSkeleton()
                        updateConnectionQuality(event.quality, event.rtt, event.fps, event.packetLoss)
                    }
                    is ServiceEvent.SenderDisconnected -> {
                        if (!isDisconnecting) {
                            isDisconnecting = true
                            hideSkeleton()
                            tvViewerStatus.text = getString(R.string.status_disconnected)
                            Toast.makeText(this@ViewerActivity, getString(R.string.viewer_broadcast_ended), Toast.LENGTH_SHORT).show()
                            lifecycleScope.launch {
                                delay(2000)
                                finish()
                            }
                        }
                    }
                    is ServiceEvent.Error -> {
                        tvStats.text = event.type.displayMessage
                        hideSkeleton()
                    }
                    is ServiceEvent.SdpError -> {
                        tvStats.text = getString(R.string.state_sdp_error, event.detail)
                        hideSkeleton()
                    }
                    is ServiceEvent.WebRtcError -> {
                        tvStats.text = getString(R.string.state_webrtc_error, event.detail)
                        hideSkeleton()
                    }
                    is ServiceEvent.SignalingStatus -> {
                        tvStats.text = event.message
                        tvViewerStatus.text = event.message
                    }
                    is ServiceEvent.SurfaceWaiting -> {
                        tvStats.text = getString(R.string.state_surface_waiting)
                    }
                    is ServiceEvent.SurfaceNotFound -> {
                        tvStats.text = getString(R.string.state_surface_not_found)
                        hideSkeleton()
                    }
                    else -> {}
                }
            }
        }

        lifecycleScope.launch {
            svc.getStateManager().viewerCount.collectLatest { count ->
                tvViewerCount.text = count.toString()
            }
        }
    }

    private fun startViewerService() {
        val serviceIntent = Intent(this, ScreenShareService::class.java).apply {
            putExtra("role", "viewer")
            putExtra("room", roomCode)
        }
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun initSkeleton() {
        skeletonHelper = SkeletonAnimHelper()
        skeletonHelper.init(
            container = findViewById(R.id.skeletonContainer),
            icon = findViewById(R.id.skeletonIcon),
            title = findViewById(R.id.skeletonTitle),
            subtitle = findViewById(R.id.skeletonSubtitle),
            status = findViewById(R.id.skeletonStatus),
            hint = findViewById(R.id.skeletonHint)
        )
    }

    private fun showSkeleton(hint: String) {
        skeletonHelper.show(hint)
        startSkeletonTimeout()
    }

    private fun startSkeletonTimeout() {
        skeletonTimeoutJob?.cancel()
        skeletonTimeoutJob = lifecycleScope.launch {
            delay(15_000)
            val c = skeletonHelper.container
            if (!isDisconnecting && c != null && c.visibility == View.VISIBLE) {
                hideSkeleton()
                tvViewerStatus.text = getString(R.string.state_connection_timeout)
                Toast.makeText(this@ViewerActivity, getString(R.string.state_broadcast_not_found), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun hideSkeleton() {
        skeletonTimeoutJob?.cancel()
        skeletonHelper.hide()
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
        stopService(Intent(this, ScreenShareService::class.java))
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        finish()
    }

    private fun updateConnectionQuality(quality: ConnectionQuality, rtt: Int, fps: Int, packetLoss: Double) {
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
                ConnectionQuality.GOOD -> R.color.dark_status_good
                ConnectionQuality.MEDIUM -> R.color.dark_status_warning
                ConnectionQuality.BAD -> R.color.dark_status_error
            }
            val color = ContextCompat.getColor(this, colorRes)
            tvConnectionQuality.setTextColor(color)
            ivConnectionDot.setColorFilter(color)
        }
    }

    private fun takeScreenshot() {
        val screenView = window.decorView.rootView
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "screenmirror_viewer_$timestamp.png"
        val success = ScreenshotHelper.takeScreenshot(this, screenView, "screenmirror_viewer")
        ScreenshotHelper.showResult(this, success, filename)
    }

    override fun onDestroy() {
        super.onDestroy()
        skeletonTimeoutJob?.cancel()
        skeletonHelper.stopAnimation()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
