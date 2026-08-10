package com.example.screenmirror

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.screenmirror.model.ConnectionQuality
import com.example.screenmirror.model.ServiceEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ViewerActivity : AppCompatActivity() {

    private companion object {
        const val AUTO_HIDE_DELAY_MS = 3000L
        const val ANIMATION_DURATION_MS = 200L
        const val RECONNECT_DELAY_MS = 3000L
        const val SKELETON_TIMEOUT_MS = 15_000L
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 5f
        const val DEFAULT_SCALE = 1f
    }

    private lateinit var tvStats: TextView
    private lateinit var tvViewerCount: TextView
    private lateinit var tvConnectionQuality: TextView
    private lateinit var tvRoomName: TextView
    private lateinit var ivConnectionDot: ImageView
    private lateinit var btnScreenshot: View
    private lateinit var btnDisconnect: View
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var waitingOverlay: View
    private lateinit var notificationOverlay: View
    private lateinit var lottieNotification: LottieAnimationView
    private lateinit var tvNotificationTitle: TextView
    private lateinit var tvNotificationSubtitle: TextView
    private lateinit var viewerSurface: org.webrtc.SurfaceViewRenderer

    private var isScaling = false
    private var currentScale = 1f
    private lateinit var scaleDetector: ScaleGestureDetector

    private var service: ScreenShareService? = null
    private var isBound = false
    private var startTime = 0L
    private var roomCode = ""
    private var isDisconnecting = false
    private var skeletonTimeoutJob: kotlinx.coroutines.Job? = null

    private lateinit var skeletonHelper: SkeletonAnimHelper

    private val autoHideHandler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideBars() }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as ScreenShareService.LocalBinder
            service = localBinder.getService()
            isBound = true
            service?.setRenderer(viewerSurface)
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        NotificationHelper.createViewerChannel(this)
        initSkeleton()

        tvStats = findViewById(R.id.tvStats)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvConnectionQuality = findViewById(R.id.tvConnectionQuality)
        tvRoomName = findViewById(R.id.tvRoomName)
        ivConnectionDot = findViewById(R.id.ivConnectionDot)
        btnScreenshot = findViewById(R.id.btnScreenshot)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        waitingOverlay = findViewById(R.id.waitingOverlay)
        notificationOverlay = findViewById(R.id.notificationOverlay)
        lottieNotification = findViewById(R.id.lottieNotification)
        tvNotificationTitle = findViewById(R.id.tvNotificationTitle)
        tvNotificationSubtitle = findViewById(R.id.tvNotificationSubtitle)
        viewerSurface = findViewById(R.id.viewerSurface)

        roomCode = intent.getStringExtra("room") ?: ""
        tvRoomName.text = getString(R.string.sender_room_prefix, roomCode)

        startTime = System.currentTimeMillis()
        tvStats.text = getString(R.string.viewer_stats)

        showSkeleton(getString(R.string.viewer_waiting))
        startViewerService()

        tvConnectionQuality.visibility = if (AppSettings.isQualityStatsEnabled(this)) View.VISIBLE else View.GONE

        btnDisconnect.setOnClickListener {
            HapticHelper.mediumTap(this)
            showDisconnectConfirmation()
        }
        btnScreenshot.setOnClickListener {
            HapticHelper.lightTap(this)
            takeScreenshot()
        }

        setupAutoHide()
        showBars()

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = currentScale.coerceIn(MIN_SCALE, MAX_SCALE)
                viewerSurface.pivotX = detector.focusX
                viewerSurface.pivotY = detector.focusY
                viewerSurface.scaleX = currentScale
                viewerSurface.scaleY = currentScale
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                if (currentScale < DEFAULT_SCALE) {
                    currentScale = DEFAULT_SCALE
                    viewerSurface.scaleX = DEFAULT_SCALE
                    viewerSurface.scaleY = DEFAULT_SCALE
                    viewerSurface.pivotX = viewerSurface.width / 2f
                    viewerSurface.pivotY = viewerSurface.height / 2f
                }
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showDisconnectConfirmation()
            }
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (topBar.visibility == View.VISIBLE) {
                    hideBars()
                } else {
                    showBars()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun setupAutoHide() {
        // Touch handled in dispatchTouchEvent
    }

    private fun showBars() {
        topBar.alpha = 1f
        topBar.translationY = 0f
        topBar.visibility = View.VISIBLE
        bottomBar.alpha = 1f
        bottomBar.translationY = 0f
        bottomBar.visibility = View.VISIBLE
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun hideBars() {
        if (waitingOverlay.visibility == View.VISIBLE) return
        if (notificationOverlay.visibility == View.VISIBLE) return
        topBar.animate().alpha(0f).translationY(-topBar.height.toFloat()).setDuration(ANIMATION_DURATION_MS).withEndAction {
            topBar.visibility = View.GONE
        }
        bottomBar.animate().alpha(0f).translationY(bottomBar.height.toFloat()).setDuration(ANIMATION_DURATION_MS).withEndAction {
            bottomBar.visibility = View.GONE
        }
    }

    private fun showNotification(title: String, subtitle: String, animation: String) {
        runOnUiThread {
            notificationOverlay.visibility = View.VISIBLE
            tvNotificationTitle.text = title
            tvNotificationSubtitle.text = subtitle
            try {
                lottieNotification.setAnimation(animation)
                lottieNotification.playAnimation()
            } catch (_: Exception) {}
            topBar.visibility = View.GONE
            bottomBar.visibility = View.GONE
            notificationOverlay.setOnClickListener {
                hideNotification()
            }
        }
    }

    private fun hideNotification() {
        notificationOverlay.visibility = View.GONE
        showBars()
    }

    private fun observeServiceEvents() {
        val svc = service ?: return
        lifecycleScope.launch {
            svc.getStateManager().event.collectLatest { event ->
                when (event) {
                    is ServiceEvent.WebRtcReady -> {
                        tvStats.text = getString(R.string.state_webrtc_ready)
                        skeletonHelper.showWithAnimation(getString(R.string.state_webrtc_ready), "connecting.json")
                    }
                    is ServiceEvent.PeerConnected -> {
                        tvStats.text = getString(R.string.state_peer_connected)
                        HapticHelper.successTap(this@ViewerActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.state_peer_connected), "success_check.json")
                    }
                    is ServiceEvent.LiveReceived -> {
                        tvStats.text = getString(R.string.state_live_received)
                        hideSkeleton()
                        waitingOverlay.visibility = View.GONE
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        showBars()
                    }
                    is ServiceEvent.OfferSent -> {
                        tvStats.text = getString(R.string.state_offer_sent)
                        skeletonHelper.showWithAnimation(getString(R.string.state_offer_sent), "connecting.json")
                    }
                    is ServiceEvent.AnswerSent -> {
                        tvStats.text = getString(R.string.state_answer_sent)
                        skeletonHelper.showWithAnimation(getString(R.string.state_answer_sent), "connecting.json")
                    }
                    is ServiceEvent.ConnectionBroken -> {
                        tvStats.text = getString(R.string.state_connection_broken)
                        HapticHelper.errorTap(this@ViewerActivity)
                        showNotification(
                            getString(R.string.state_connection_broken),
                            getString(R.string.state_reconnecting, 1),
                            "disconnect.json"
                        )
                        lifecycleScope.launch {
                            delay(RECONNECT_DELAY_MS)
                            if (!isDisconnecting && service != null) {
                                hideNotification()
                                showSkeleton(getString(R.string.state_reconnecting, 1))
                            } else {
                                cleanupAndFinish()
                            }
                        }
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
                            HapticHelper.errorTap(this@ViewerActivity)
                            showNotification(
                                getString(R.string.viewer_broadcast_ended),
                                getString(R.string.state_yayinci_ayrildi),
                                "disconnect.json"
                            )
                            lifecycleScope.launch {
                                delay(RECONNECT_DELAY_MS)
                                finish()
                            }
                        }
                    }
                    is ServiceEvent.Error -> {
                        tvStats.text = event.type.displayMessage
                        HapticHelper.errorTap(this@ViewerActivity)
                        skeletonHelper.showWithAnimation(event.type.displayMessage, "error_cross.json")
                    }
                    is ServiceEvent.SdpError -> {
                        tvStats.text = getString(R.string.state_sdp_error, event.detail)
                        HapticHelper.errorTap(this@ViewerActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.state_sdp_error, event.detail), "error_cross.json")
                    }
                    is ServiceEvent.WebRtcError -> {
                        tvStats.text = getString(R.string.state_webrtc_error, event.detail)
                        HapticHelper.errorTap(this@ViewerActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.state_webrtc_error, event.detail), "error_cross.json")
                    }
                    is ServiceEvent.SignalingStatus -> {
                        tvStats.text = event.message
                    }
                    is ServiceEvent.SurfaceWaiting -> {
                        tvStats.text = getString(R.string.state_surface_waiting)
                    }
                    is ServiceEvent.SurfaceNotFound -> {
                        tvStats.text = getString(R.string.state_surface_not_found)
                        HapticHelper.errorTap(this@ViewerActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.state_surface_not_found), "error_cross.json")
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
            hint = findViewById(R.id.skeletonHint),
            lottie = findViewById(R.id.lottieLoading)
        )
    }

    private fun showSkeleton(hint: String) {
        skeletonHelper.show(hint)
        startSkeletonTimeout()
    }

    private fun startSkeletonTimeout() {
        skeletonTimeoutJob?.cancel()
        skeletonTimeoutJob = lifecycleScope.launch {
            delay(SKELETON_TIMEOUT_MS)
            val c = skeletonHelper.container
            if (!isDisconnecting && c != null && c.visibility == View.VISIBLE) {
                hideSkeleton()
                showNotification(
                    getString(R.string.state_connection_timeout),
                    getString(R.string.state_broadcast_not_found),
                    "error_cross.json"
                )
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
        if (isDisconnecting) return
        isDisconnecting = true
        autoHideHandler.removeCallbacks(autoHideRunnable)
        if (isBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
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

    override fun onResume() {
        super.onResume()
        if (isBound) {
            service?.reattachSink(viewerSurface)
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
        autoHideHandler.removeCallbacks(autoHideRunnable)
        skeletonTimeoutJob?.cancel()
        skeletonHelper.stopAnimation()
        if (isBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
            isBound = false
        }
    }
}
