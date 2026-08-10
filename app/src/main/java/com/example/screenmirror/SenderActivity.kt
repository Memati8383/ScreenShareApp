package com.example.screenmirror

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.screenmirror.model.ConnectionQuality
import com.example.screenmirror.model.ServiceEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private var service: ScreenShareService? = null
    private var isBound = false
    private var isSharing = false
    private var isRecording = false
    private var isFrozen = false
    private var isDisconnecting = false
    private var startTime = 0L
    private var roomCode = ""

    private lateinit var skeletonHelper: SkeletonAnimHelper
    private lateinit var senderSurface: org.webrtc.SurfaceViewRenderer

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as ScreenShareService.LocalBinder
            service = localBinder.getService()
            isBound = true
            service?.setRenderer(senderSurface)
            observeServiceEvents()
            Log.i("SenderActivity", getString(R.string.state_service_connected))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            Log.i("SenderActivity", getString(R.string.state_service_disconnected))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val topBar = findViewById<View>(R.id.topBar)
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, statusBarHeight + 8, view.paddingRight, view.paddingBottom)
            insets
        }

        NotificationHelper.createScreenShareChannel(this)
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

        ViewCompat.setOnApplyWindowInsetsListener(controlPanel) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, navBarHeight + 28)
            insets
        }

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

        btnFreeze.setOnClickListener {
            HapticHelper.mediumTap(this)
            val serviceIntent = Intent(this, ScreenShareService::class.java).apply {
                action = "com.example.screenmirror.TOGGLE_FREEZE"
            }
            startService(serviceIntent)
            isFrozen = !isFrozen
            val freezeText = btnFreeze.findViewById<TextView>(R.id.tvFreeze)
            freezeText?.text = if (isFrozen) getString(R.string.sender_unfreeze) else getString(R.string.sender_freeze)
            Toast.makeText(this, if (isFrozen) getString(R.string.sender_frozen) else getString(R.string.sender_resumed), Toast.LENGTH_SHORT).show()
        }

        btnScreenshot.setOnClickListener {
            HapticHelper.lightTap(this)
            takeScreenshot()
        }

        btnRecord.setOnClickListener {
            HapticHelper.mediumTap(this)
            if (isRecording) stopRecording() else startRecording()
        }

        btnQuality.setOnClickListener {
            HapticHelper.mediumTap(this)
            showQualityDialog()
        }

        btnStop.setOnClickListener {
            HapticHelper.heavyTap(this)
            if (isSharing) showStopConfirmation()
        }

        tvConnectionQuality.visibility = if (AppSettings.isQualityStatsEnabled(this)) View.VISIBLE else View.GONE

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSharing) showStopConfirmation()
            }
        })

        senderSurface = findViewById(R.id.senderSurface)

        showSkeleton(getString(R.string.state_screen_sharing_starting))
        startService()
        startStatsChecker()
    }

    private fun observeServiceEvents() {
        val svc = service ?: return
        lifecycleScope.launch {
            svc.getStateManager().event.collectLatest { event ->
                when (event) {
                    is ServiceEvent.WebRtcReady -> {
                        tvSenderStats.text = getString(R.string.state_webrtc_ready)
                        skeletonHelper.showWithAnimation(getString(R.string.state_webrtc_ready), "connecting.json")
                    }
                    is ServiceEvent.ViewerWaiting -> {
                        tvSenderStats.text = getString(R.string.state_viewer_waiting)
                        skeletonHelper.showWithAnimation(getString(R.string.state_viewer_waiting), "screen_share.json")
                    }
                    is ServiceEvent.ScreenBroadcasting -> {
                        tvSenderStats.text = getString(R.string.state_screen_broadcasting)
                        skeletonHelper.showWithAnimation(getString(R.string.state_screen_broadcasting), "broadcast_live.json")
                    }
                    is ServiceEvent.PeerConnected -> {
                        tvSenderStats.text = getString(R.string.state_peer_connected)
                        HapticHelper.successTap(this@SenderActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.state_peer_connected), "success_check.json")
                    }
                    is ServiceEvent.LiveReceived -> {
                        tvSenderStats.text = getString(R.string.state_live_received)
                        hideSkeleton()
                    }
                    is ServiceEvent.OfferSent -> {
                        tvSenderStats.text = getString(R.string.state_offer_sent)
                    }
                    is ServiceEvent.AnswerSent -> {
                        tvSenderStats.text = getString(R.string.state_answer_sent)
                    }
                    is ServiceEvent.BroadcastPaused -> {
                        tvSenderStats.text = getString(R.string.state_broadcast_paused)
                    }
                    is ServiceEvent.BroadcastResumed -> {
                        tvSenderStats.text = getString(R.string.state_broadcast_resumed)
                    }
                    is ServiceEvent.ConnectionBroken -> {
                        tvSenderStats.text = getString(R.string.state_connection_broken)
                        HapticHelper.errorTap(this@SenderActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.state_connection_broken), "disconnect.json")
                        lifecycleScope.launch {
                            delay(3000)
                            cleanupAndFinish()
                        }
                    }
                    is ServiceEvent.ViewerCountChanged -> {
                        tvViewerCount.text = event.count.toString()
                    }
                    is ServiceEvent.ConnectionQualityChanged -> {
                        updateConnectionQuality(event.quality, event.rtt, event.fps, event.packetLoss)
                    }
                    is ServiceEvent.Error -> {
                        tvSenderStats.text = event.type.displayMessage
                        HapticHelper.errorTap(this@SenderActivity)
                        skeletonHelper.showWithAnimation(event.type.displayMessage, "error_cross.json")
                    }
                    is ServiceEvent.SdpError -> {
                        tvSenderStats.text = getString(R.string.state_sdp_error, event.detail)
                        HapticHelper.errorTap(this@SenderActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.state_sdp_error, event.detail), "error_cross.json")
                    }
                    is ServiceEvent.WebRtcError -> {
                        tvSenderStats.text = getString(R.string.state_webrtc_error, event.detail)
                        HapticHelper.errorTap(this@SenderActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.state_webrtc_error, event.detail), "error_cross.json")
                    }
                    is ServiceEvent.SignalingStatus -> {
                        tvSenderStats.text = event.message
                    }
                    is ServiceEvent.SurfaceWaiting -> {
                        tvSenderStats.text = getString(R.string.state_surface_waiting)
                    }
                    is ServiceEvent.SurfaceNotFound -> {
                        tvSenderStats.text = getString(R.string.state_surface_not_found)
                        HapticHelper.errorTap(this@SenderActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.state_surface_not_found), "error_cross.json")
                    }
                    is ServiceEvent.RecordingStarted -> {
                        isRecording = true
                        tvTimer.visibility = View.VISIBLE
                        HapticHelper.mediumTap(this@SenderActivity)
                        startRecordTimer()
                    }
                    is ServiceEvent.RecordingStopped -> {
                        isRecording = false
                        tvTimer.visibility = View.GONE
                    }
                    is ServiceEvent.SenderDisconnected -> {
                        tvSenderStats.text = event.message
                        HapticHelper.errorTap(this@SenderActivity)
                        skeletonHelper.showWithAnimation(event.message, "disconnect.json")
                        lifecycleScope.launch {
                            delay(2000)
                            cleanupAndFinish()
                        }
                    }
                    is ServiceEvent.ViewerLeft -> {
                        tvSenderStats.text = getString(R.string.sender_peer_left)
                        HapticHelper.mediumTap(this@SenderActivity)
                        skeletonHelper.showWithAnimation(getString(R.string.sender_peer_left), "disconnect.json")
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
    }

    private fun hideSkeleton() {
        skeletonHelper.hide()
    }

    private fun startService() {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val projectionData = getParcelableExtraCompat(intent, EXTRA_PROJECTION_DATA)

        if (resultCode == 0 || projectionData == null) {
            showSkeleton(getString(R.string.state_screen_permission_error))
            return
        }

        val serviceIntent = Intent(this, ScreenShareService::class.java).apply {
            putExtra("role", "sender")
            putExtra("room", roomCode)
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra("data", projectionData)
        }
        startForegroundService(serviceIntent)

        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        isSharing = true
        startTime = System.currentTimeMillis()
        tvSenderStatus.text = getString(R.string.status_live)
    }

    @Suppress("DEPRECATION")
    private fun getParcelableExtraCompat(intent: Intent, key: String): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(key, Intent::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    private fun showQualityDialog() {
        val resolutions = arrayOf("480p (854x480)", "720p (1280x720)", "1080p (1920x1080)", "1440p (2560x1440)")
        val widths = intArrayOf(854, 1280, 1920, 2560)
        val heights = intArrayOf(480, 720, 1080, 1440)
        val fpsOptions = arrayOf("15 FPS", "24 FPS", "30 FPS", "60 FPS")
        val fpsValues = intArrayOf(15, 24, 30, 60)

        val currentWidth = AppSettings.getCaptureWidth(this)
        val currentFps = AppSettings.getCaptureFps(this)
        val currentResIndex = widths.indexOf(currentWidth).coerceAtLeast(1)
        val currentFpsIndex = fpsValues.indexOf(currentFps).coerceAtLeast(2)

        var selectedResIndex = currentResIndex
        var selectedFpsIndex = currentFpsIndex

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
        }

        val resLabel = android.widget.TextView(this).apply {
            text = getString(R.string.sender_quality_resolution)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 14f
        }
        layout.addView(resLabel)

        val resSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@SenderActivity, android.R.layout.simple_spinner_dropdown_item, resolutions)
            setSelection(currentResIndex)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedResIndex = position
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        layout.addView(resSpinner)

        val spacer = android.view.View(this).apply { layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 24) }
        layout.addView(spacer)

        val fpsLabel = android.widget.TextView(this).apply {
            text = getString(R.string.sender_quality_fps)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 14f
        }
        layout.addView(fpsLabel)

        val fpsSpinner = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@SenderActivity, android.R.layout.simple_spinner_dropdown_item, fpsOptions)
            setSelection(currentFpsIndex)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedFpsIndex = position
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        layout.addView(fpsSpinner)

        AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.sender_quality))
            .setView(layout)
            .setPositiveButton(getString(R.string.sender_quality_apply)) { _, _ ->
                AppSettings.setCaptureWidth(this, widths[selectedResIndex])
                AppSettings.setCaptureHeight(this, heights[selectedResIndex])
                AppSettings.setCaptureFps(this, fpsValues[selectedFpsIndex])

                val serviceIntent = Intent(this, ScreenShareService::class.java).apply {
                    action = "com.example.screenmirror.CHANGE_QUALITY"
                    putExtra("width", widths[selectedResIndex])
                    putExtra("height", heights[selectedResIndex])
                    putExtra("fps", fpsValues[selectedFpsIndex])
                }
                startService(serviceIntent)
                Toast.makeText(this, getString(R.string.sender_quality_changed, resolutions[selectedResIndex], fpsOptions[selectedFpsIndex]), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showStopConfirmation() {
        AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.sender_stop_title))
            .setMessage(getString(R.string.sender_stop_msg))
            .setPositiveButton(getString(R.string.sender_stop_confirm)) { _, _ ->
                cleanupAndFinish()
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun cleanupAndFinish() {
        if (isDisconnecting) return
        isDisconnecting = true
        if (isBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
            isBound = false
        }
        stopService(Intent(this, ScreenShareService::class.java))
        isSharing = false
        finish()
    }

    private fun startStatsChecker() {
        lifecycleScope.launch {
            while (isActive) {
                if (!AppSettings.isQualityStatsEnabled(this@SenderActivity)) {
                    tvConnectionQuality.visibility = View.GONE
                    delay(2000)
                    continue
                }
                delay(2000)
            }
        }
    }

    private fun updateConnectionQuality(quality: ConnectionQuality, rtt: Int, fps: Int, packetLoss: Double) {
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
                ConnectionQuality.GOOD -> R.color.dark_status_good to R.drawable.ic_signal
                ConnectionQuality.MEDIUM -> R.color.dark_status_warning to R.drawable.ic_signal
                ConnectionQuality.BAD -> R.color.dark_status_error to R.drawable.ic_signal
            }

            val color = ContextCompat.getColor(this, colorRes)
            tvConnectionQuality.setTextColor(color)
            ivConnectionDot.setColorFilter(color)
        }
    }

    private fun takeScreenshot() {
        val screenView = window.decorView.rootView
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "screenmirror_$timestamp.png"
        val success = ScreenshotHelper.takeScreenshot(this, screenView, "screenmirror")
        ScreenshotHelper.showResult(this, success, filename)
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
        if (isBound) {
            try { unbindService(serviceConnection) } catch (_: Exception) {}
            isBound = false
        }
    }
}
