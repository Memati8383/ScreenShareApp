package com.example.screenmirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.webrtc.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import com.example.screenmirror.data.RoomHistory
import com.example.screenmirror.model.ConnectionQuality
import com.example.screenmirror.model.ErrorType
import com.example.screenmirror.model.ServiceEvent
import com.example.screenmirror.service.ServiceStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenShareService : Service() {

    companion object {
        private const val TAG = "ScreenShareSvc"
        private const val CHANNEL_ID = "screen_share_channel"
        private const val CHANNEL_NAME = "Screen Share"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_ID_JOIN = 2
        private const val NOTIFICATION_ID_BROADCAST = 3
    }

    inner class LocalBinder : Binder() {
        fun getService(): ScreenShareService = this@ScreenShareService
        fun getStateManager(): ServiceStateManager = stateManager
    }

    private val binder = LocalBinder()
    private val stateManager = ServiceStateManager()

    private var role = "viewer"
    private var roomId = ""

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var cloudSignaling: CloudSignalingClient? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var localTrack: VideoTrack? = null
    private var remoteTrack: VideoTrack? = null

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingFile: File? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webRtcReady = false
    private var offerPending = false
    private var startTime = 0L
    @Volatile private var participantCount = 1
    private val lock = Any()
    private var statsCheckerRunnable: Runnable? = null

    private var pendingResultCode: Int = 0
    private var pendingData: Intent? = null
    private var renderer: SurfaceViewRenderer? = null
    private var factoryInitialized = false
    private var captureWidth = 1280
    private var captureHeight = 720
    private var captureFps = 30
    private var isFrozen = false

    @Volatile private var remoteDescriptionSet = false
    private val pendingCandidates = CopyOnWriteArrayList<IceCandidate>()

    fun getStateManager(): ServiceStateManager = stateManager

    fun setRenderer(surfaceRenderer: SurfaceViewRenderer) {
        renderer = surfaceRenderer
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved")
        super.onTaskRemoved(rootIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        try {
            if (intent != null) {
                val action = intent.action
                when (action) {
                    "com.example.screenmirror.START_RECORDING" -> {
                        val resultCode = intent.getIntExtra("resultCode", 0)
                        val data = getParcelableExtraCompat(intent, "data")
                        executor.execute { startRecording(resultCode, data) }
                        return START_STICKY
                    }
                    "com.example.screenmirror.STOP_RECORDING" -> {
                        executor.execute { stopRecording() }
                        return START_STICKY
                    }
                    "com.example.screenmirror.TOGGLE_FREEZE" -> {
                        executor.execute { toggleFreeze() }
                        return START_STICKY
                    }
                }

                role = intent.getStringExtra("role") ?: "viewer"
                roomId = intent.getStringExtra("room") ?: "oda1"
                startTime = System.currentTimeMillis()
                Log.i(TAG, "role=$role room=$roomId")

                if (role == "sender") {
                    val rc = intent.getIntExtra("resultCode", 0)
                    val d = getParcelableExtraCompat(intent, "data")
                    if (rc != 0 && d != null) {
                        pendingResultCode = rc
                        pendingData = d
                        Log.i(TAG, "sender: resultCode=$rc data mevcut")
                    }
                }
            }

            captureWidth = AppSettings.getCaptureWidth(this)
            captureHeight = AppSettings.getCaptureHeight(this)
            captureFps = AppSettings.getCaptureFps(this)

            val notificationText = if (role == "sender")
                getString(R.string.notif_broadcast_active)
            else
                getString(R.string.notif_viewing_active)
            showNotification(notificationText)

            if (role == "sender") {
                showBroadcastStartNotification()
            }

            val r = renderer
            if (r == null) {
                Log.e(TAG, "renderer NULL, 5 kez 1sn aralikla denenecek")
                stateManager.emitEvent(ServiceEvent.SurfaceWaiting)
                retryRenderer(1, 5)
                return START_STICKY
            }

            if (webRtcReady) {
                Log.i(TAG, "WebRTC zaten baslatilmis")
                if (role == "sender" && pendingResultCode != 0 && pendingData != null) {
                    val code = pendingResultCode; val data = pendingData
                    pendingResultCode = 0; pendingData = null
                    executor.execute { startCapture(code, data) }
                }
                return START_STICKY
            }

            val sv = r as? SurfaceView
            val surfaceReady = sv?.holder?.surface?.isValid == true
            Log.i(TAG, "surface ready = $surfaceReady")

            if (surfaceReady) {
                startWebRtc(r)
            } else {
                Log.i(TAG, "surface hazir degil, bekleniyor...")
                stateManager.emitEvent(ServiceEvent.SurfaceWaiting)
                var surfaceCallbackFired = false
                sv?.holder?.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) {
                        Log.i(TAG, "surfaceCreated - WebRTC baslatiliyor")
                        surfaceCallbackFired = true
                        mainHandler.post { startWebRtc(r) }
                    }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {
                        Log.w(TAG, "surfaceDestroyed")
                    }
                })
                mainHandler.postDelayed({
                    if (!surfaceCallbackFired && !webRtcReady) {
                        Log.w(TAG, "surface callback 3sn icinde tetiklenmedi, yeniden deneniyor")
                        val retryRenderer = renderer
                        if (retryRenderer != null) {
                            startWebRtc(retryRenderer)
                        }
                    }
                }, 3000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand hatasi", e)
            stateManager.emitEvent(ServiceEvent.Error(ErrorType.UNKNOWN, e.message ?: "Unknown"))
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun getParcelableExtraCompat(intent: Intent, key: String): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(key, Intent::class.java)
        } else {
            intent.getParcelableExtra(key)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showNotification(text: String) {
        try {
            val notif = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("ScreenShare")
                .setContentText(text)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                val type = if (role == "sender")
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                else
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                startForeground(NOTIFICATION_ID, notif, type)
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } catch (e: Exception) {
            Log.e(TAG, "foreground hatasi", e)
        }
    }

    private fun showJoinNotification() {
        try {
            val notif = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle("Screen Mirror")
                .setContentText(getString(R.string.notif_joined))
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_JOIN, notif)
        } catch (e: Exception) {
            Log.e(TAG, "bildirim hatasi", e)
        }
    }

    private fun showBroadcastStartNotification() {
        try {
            val notif = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Screen Mirror")
                .setContentText(getString(R.string.notif_broadcast_started))
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_BROADCAST, notif)
        } catch (e: Exception) {
            Log.e(TAG, "bildirim hatasi", e)
        }
    }

    private fun retryRenderer(attempt: Int, maxAttempts: Int) {
        mainHandler.postDelayed({
            val retry = renderer
            if (retry != null) {
                Log.i(TAG, "renderer bulundu ($attempt/$maxAttempts), WebRTC baslatiliyor")
                startWebRtc(retry)
            } else if (attempt < maxAttempts) {
                Log.w(TAG, "renderer hala NULL ($attempt/$maxAttempts), tekrar deneniyor...")
                retryRenderer(attempt + 1, maxAttempts)
            } else {
                Log.e(TAG, "renderer $maxAttempts deneme sonra hala NULL")
                stateManager.emitEvent(ServiceEvent.SurfaceNotFound)
            }
        }, 1000)
    }

    private fun startWebRtc(r: SurfaceViewRenderer) {
        Log.i(TAG, "startWebRtc basliyor")
        try {
            remoteDescriptionSet = false
            pendingCandidates.clear()

            eglBase = EglBase.create()
            r.init(eglBase!!.eglBaseContext, null)
            r.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            r.setZOrderMediaOverlay(true)
            Log.i(TAG, "renderer init OK")

            if (!factoryInitialized) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(this)
                        .setFieldTrials("WebRTC-Network-DisableNetworkMonitor/Enabled/")
                        .createInitializationOptions()
                )
                factoryInitialized = true
                Log.i(TAG, "PeerConnectionFactory initialize OK")
            }

            factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
                .createPeerConnectionFactory()
            Log.i(TAG, "factory OK")

            val iceServers = mutableListOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
            )

            val turnUrl = AppSettings.getTurnUrl(this)
            val turnUser = AppSettings.getTurnUser(this)
            val turnPass = AppSettings.getTurnPass(this)
            if (turnUrl.isNotEmpty()) {
                iceServers.add(
                    PeerConnection.IceServer.builder(turnUrl)
                        .setUsername(turnUser)
                        .setPassword(turnPass)
                        .createIceServer()
                )
                Log.i(TAG, "TURN sunucusu eklendi: $turnUrl")
            }

            val config = PeerConnection.RTCConfiguration(iceServers)
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

            peerConnection = factory!!.createPeerConnection(config, createPeerObserver())
            Log.i(TAG, "peerConnection OK")

            cloudSignaling = CloudSignalingClient(
                roomId, role,
                onRemoteDescription = { sdp ->
                    Log.i(TAG, "Cloud: remote sdp alindi: ${sdp.type}")
                    stateManager.emitEvent(ServiceEvent.PeerConnected)
                    executor.execute { handleRemoteDescription(sdp) }
                },
                onRemoteIce = { c -> executor.execute { handleRemoteIce(c) } },
                onPeerJoined = {
                    stateManager.emitEvent(ServiceEvent.PeerConnected)
                    participantCount = 2
                    showJoinNotification()
                    if (role == "sender") {
                        if (localTrack != null) {
                            executor.execute { createOffer() }
                        } else {
                            Log.i(TAG, "sender: localTrack henuz hazir degil, offer bekleniyor")
                            offerPending = true
                            mainHandler.postDelayed({
                                if (offerPending && role == "sender") {
                                    Log.w(TAG, "sender: offer hala bekleniyor, yeniden deneniyor")
                                    if (localTrack != null) {
                                        offerPending = false
                                        executor.execute { createOffer() }
                                    }
                                }
                            }, 5000)
                        }
                    }
                },
                onPeerLeft = {
                    participantCount = 1
                    stateManager.emitEvent(ServiceEvent.ViewerLeft)
                    if (role == "viewer") {
                        stateManager.emitEvent(ServiceEvent.SenderDisconnected("Yayinci ayrildi"))
                    }
                },
                onViewerCountChanged = { count ->
                    stateManager.emitEvent(ServiceEvent.ViewerCountChanged(count))
                },
                onDisconnect = { reason ->
                    stateManager.emitEvent(ServiceEvent.SignalingStatus(reason))
                }
            )
            Log.i(TAG, "cloud signaling objesi olusturuldu")

            webRtcReady = true
            stateManager.emitEvent(ServiceEvent.WebRtcReady)

            startStatsChecker()

            if (role == "sender" && pendingResultCode != 0 && pendingData != null) {
                val code = pendingResultCode; val data = pendingData
                pendingResultCode = 0; pendingData = null
                executor.execute {
                    cloudSignaling?.connect()
                    Log.i(TAG, "sender: signaling baglandi, capture baslatiliyor...")
                    startCapture(code, data)
                    Log.i(TAG, "sender: capture baslatildi, izleyici bekleniyor")
                    stateManager.emitEvent(ServiceEvent.ViewerWaiting)
                }
            } else {
                cloudSignaling?.connect()
                Log.i(TAG, "viewer: signaling baglandi, offer bekleniyor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "startWebRtc HATA", e)
            stateManager.emitEvent(ServiceEvent.WebRtcError(e.message ?: "Unknown"))
        }
    }

    private fun createPeerObserver() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.i(TAG, "ICE: $state")
            when (state) {
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.FAILED -> {
                    Log.w(TAG, "ICE baglantisi koptu: $state")
                    stateManager.emitEvent(ServiceEvent.ConnectionBroken(state.name))
                    if (role == "viewer") {
                        stateManager.emitEvent(ServiceEvent.SenderDisconnected("Yayinci baglantisi koptu"))
                    }
                }
                PeerConnection.IceConnectionState.CONNECTED -> {
                    stateManager.emitEvent(ServiceEvent.PeerConnected)
                }
                else -> {}
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {
            Log.i(TAG, "Renegotiation needed")
            if (role == "sender" && localTrack != null) {
                executor.execute { createOffer() }
            }
        }
        override fun onIceCandidate(candidate: IceCandidate) {
            cloudSignaling?.sendIce(candidate)
        }
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            val track = receiver.track()
            if (track is VideoTrack) {
                remoteTrack = track
                mainHandler.post {
                    renderer?.let { r -> remoteTrack?.addSink(r) }
                }
                stateManager.emitEvent(ServiceEvent.LiveReceived)
            }
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) {
                remoteTrack = track
                mainHandler.post {
                    renderer?.let { r -> remoteTrack?.addSink(r) }
                }
                stateManager.emitEvent(ServiceEvent.LiveReceived)
            }
        }
    }

    private fun startCapture(resultCode: Int, data: Intent?) {
        Log.i(TAG, "startCapture")
        if (data == null) return
        try {
            val eb = eglBase ?: return
            val f = factory ?: return

            val w = captureWidth
            val h = captureHeight
            Log.i(TAG, "screen: ${w}x${h} @ ${captureFps}fps")

            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eb.eglBaseContext)
            videoSource = f.createVideoSource(true)
            capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            })
            capturer?.initialize(surfaceTextureHelper, this, videoSource?.capturerObserver)
            capturer?.startCapture(w, h, captureFps)
            localTrack = f.createVideoTrack("screen0", videoSource)
            peerConnection?.addTrack(localTrack, listOf("stream0"))
            Log.i(TAG, "capture baslatildi, localTrack eklenmis")
            stateManager.emitEvent(ServiceEvent.ScreenBroadcasting)

            if (offerPending && role == "sender") {
                offerPending = false
                Log.i(TAG, "sender: localTrack hazir, beklenen offer olusturuluyor")
                createOffer()
            }
        } catch (e: Exception) {
            Log.e(TAG, "startCapture HATA", e)
            stateManager.emitEvent(ServiceEvent.Error(ErrorType.SCREEN_CAPTURE, e.message ?: ""))
        }
    }

    fun toggleFreeze() {
        isFrozen = !isFrozen
        if (isFrozen) {
            try { capturer?.stopCapture() } catch (_: Exception) {}
            stateManager.emitEvent(ServiceEvent.BroadcastPaused)
        } else {
            if (capturer != null) {
                try {
                    capturer?.startCapture(captureWidth, captureHeight, captureFps)
                    stateManager.emitEvent(ServiceEvent.BroadcastResumed)
                } catch (e: Exception) {
                    Log.e(TAG, "Yeniden baslatma hatasi", e)
                    stateManager.emitEvent(ServiceEvent.WebRtcError(e.message ?: "Unknown"))
                }
            } else {
                stateManager.emitEvent(ServiceEvent.BroadcastReady)
            }
        }
    }

    fun startRecording(resultCode: Int, data: Intent?) {
        if (isRecording) return
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            val mediaProjection = projectionManager.getMediaProjection(resultCode, data ?: return)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MOVIES
            )
            val screenmirrorDir = File(picturesDir, "ScreenMirror")
            if (!screenmirrorDir.exists()) screenmirrorDir.mkdirs()

            recordingFile = File(screenmirrorDir, "screenmirror_$timestamp.mp4")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(captureWidth, captureHeight)
                setVideoFrameRate(captureFps)
                setVideoEncodingBitRate(captureWidth * captureHeight * 3)
                setOutputFile(recordingFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            stateManager.emitEvent(ServiceEvent.RecordingStarted)
            Log.i(TAG, "Kayit baslatildi: ${recordingFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Kayit baslatma hatasi", e)
            stateManager.emitEvent(ServiceEvent.RecordingError(e.message ?: "Unknown"))
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            stateManager.emitEvent(ServiceEvent.RecordingStopped)
            stateManager.emitEvent(ServiceEvent.RecordingProgress(recordingFile?.name ?: ""))
            Log.i(TAG, "Kayit durduruldu: ${recordingFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Kayit durdurma hatasi", e)
            stateManager.emitEvent(ServiceEvent.RecordingError(e.message ?: "Unknown"))
        }
    }

    fun isRecording(): Boolean = isRecording

    private fun handleRemoteDescription(sdp: SessionDescription) {
        try {
            val sdpTypeStr = if (sdp.type == SessionDescription.Type.OFFER) "offer" else "answer"
            Log.i(TAG, "handleRemoteDescription: $sdpTypeStr alindi")
            if (sdp.type == SessionDescription.Type.OFFER) {
                stateManager.emitEvent(ServiceEvent.OfferSent)
            } else {
                stateManager.emitEvent(ServiceEvent.AnswerSent)
            }
            if (peerConnection == null) {
                Log.e(TAG, "handleRemoteDescription: peerConnection null!")
                stateManager.emitEvent(ServiceEvent.WebRtcError("peerConnection null"))
                return
            }
            peerConnection?.setRemoteDescription(sdpObserver(
                onSetSuccess = {
                    remoteDescriptionSet = true
                    Log.i(TAG, "setRemoteDescription basarili ($sdpTypeStr)")
                    pendingCandidates.forEach { candidate ->
                        try {
                            peerConnection?.addIceCandidate(candidate)
                        } catch (e: Exception) {
                            Log.e(TAG, "Pending ICE candidate ekleme hatasi", e)
                        }
                    }
                    pendingCandidates.clear()
                    if (sdp.type == SessionDescription.Type.OFFER) {
                        Log.i(TAG, "Answer olusturuluyor...")
                        peerConnection?.createAnswer(sdpObserver(
                            onCreateSuccess = { ans ->
                                Log.i(TAG, "Answer olusturuldu, localDescription ayarlaniyor")
                                peerConnection?.setLocalDescription(sdpObserver(
                                    onSetSuccess = {
                                        Log.i(TAG, "Answer localDescription ayarlandi, gonderiliyor")
                                        cloudSignaling?.sendAnswer(ans)
                                        stateManager.emitEvent(ServiceEvent.AnswerSent)
                                    },
                                    onSetFailure = { error ->
                                        Log.e(TAG, "setLocalDescription (answer) basarisiz: $error")
                                        stateManager.emitEvent(ServiceEvent.SdpError(error ?: "Unknown"))
                                    }
                                ), ans)
                            },
                            onCreateFailure = { error ->
                                Log.e(TAG, "createAnswer basarisiz: $error")
                                stateManager.emitEvent(ServiceEvent.SdpError(error ?: "Unknown"))
                            }
                        ), MediaConstraints())
                    }
                },
                onSetFailure = { error ->
                    Log.e(TAG, "setRemoteDescription basarisiz: $error")
                    stateManager.emitEvent(ServiceEvent.SdpError(error ?: "Unknown"))
                }
            ), sdp)
        } catch (e: Exception) {
            Log.e(TAG, "handleRemoteDescription HATA", e)
            stateManager.emitEvent(ServiceEvent.SdpError(e.message ?: "Unknown"))
        }
    }

    private fun handleRemoteIce(candidate: IceCandidate) {
        try {
            if (remoteDescriptionSet) {
                peerConnection?.addIceCandidate(candidate)
            } else {
                pendingCandidates.add(candidate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ICE candidate ekleme hatasi", e)
        }
    }

    private fun createOffer() {
        if (localTrack == null) {
            Log.w(TAG, "createOffer: localTrack null, offer olusturulamadi")
            stateManager.emitEvent(ServiceEvent.ScreenBroadcasting)
            return
        }
        if (peerConnection == null) {
            Log.w(TAG, "createOffer: peerConnection null")
            stateManager.emitEvent(ServiceEvent.WebRtcError("peerConnection null"))
            return
        }
        Log.i(TAG, "createOffer basliyor")
        stateManager.emitEvent(ServiceEvent.OfferSent)
        peerConnection?.createOffer(sdpObserver(
            onCreateSuccess = { offer ->
                Log.i(TAG, "createOffer basarili, localDescription ayarlaniyor")
                peerConnection?.setLocalDescription(sdpObserver(
                    onSetSuccess = {
                        Log.i(TAG, "localDescription ayarlandi, offer gonderiliyor")
                        cloudSignaling?.sendOffer(offer)
                        stateManager.emitEvent(ServiceEvent.OfferSent)
                    },
                    onSetFailure = { error ->
                        Log.e(TAG, "setLocalDescription (offer) basarisiz: $error")
                        stateManager.emitEvent(ServiceEvent.SdpError(error ?: "Unknown"))
                    }
                ), offer)
            },
            onCreateFailure = { error ->
                Log.e(TAG, "createOffer basarisiz: $error")
                stateManager.emitEvent(ServiceEvent.SdpError(error ?: "Unknown"))
            }
        ), MediaConstraints())
    }

    private fun sdpObserver(
        onCreateSuccess: (SessionDescription) -> Unit = {},
        onCreateFailure: (String) -> Unit = { Log.e(TAG, "SDP hata: $it") },
        onSetSuccess: () -> Unit = {},
        onSetFailure: (String) -> Unit = { Log.e(TAG, "SDP set hata: $it") }
    ) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) { sdp?.let(onCreateSuccess) }
        override fun onCreateFailure(error: String?) { error?.let(onCreateFailure) }
        override fun onSetSuccess() { onSetSuccess() }
        override fun onSetFailure(error: String?) { error?.let(onSetFailure) }
    }

    private fun startStatsChecker() {
        statsCheckerRunnable = object : Runnable {
            override fun run() {
                if (!webRtcReady || peerConnection == null) return
                peerConnection?.getStats { report ->
                    var framesPerSecond = 0
                    var packetLoss = 0.0
                    var roundTripTime = 0.0

                    report.statsMap.forEach { (key, stats) ->
                        when (stats.type) {
                            "inbound-rtp" -> {
                                framesPerSecond = stats.members["framesPerSecond"] as? Int ?: 0
                                val packetsLost = stats.members["packetsLost"] as? Long ?: 0
                                val packetsReceived = stats.members["packetsReceived"] as? Long ?: 1
                                if (packetsReceived > 0) {
                                    packetLoss = (packetsLost.toDouble() / packetsReceived) * 100
                                }
                            }
                            "candidate-pair" -> {
                                val state = stats.members["state"] as? String
                                if (state == "succeeded") {
                                    roundTripTime = stats.members["currentRoundTripTime"] as? Double ?: 0.0
                                }
                            }
                        }
                    }

                    val quality = ConnectionQuality.fromStats(packetLoss, roundTripTime)

                    synchronized(lock) {
                        if (quality == ConnectionQuality.BAD && captureWidth > 854) {
                            captureWidth = 854
                            captureHeight = 480
                            capturer?.changeCaptureFormat(captureWidth, captureHeight, captureFps)
                            Log.i(TAG, "Kalite dusuruldu: ${captureWidth}x${captureHeight}")
                        } else if (quality == ConnectionQuality.GOOD && captureWidth < 1280) {
                            captureWidth = 1280
                            captureHeight = 720
                            capturer?.changeCaptureFormat(captureWidth, captureHeight, captureFps)
                            Log.i(TAG, "Kalite artirildi: ${captureWidth}x${captureHeight}")
                        }
                    }

                    stateManager.emitEvent(ServiceEvent.ConnectionQualityChanged(
                        quality = quality,
                        rtt = (roundTripTime * 1000).toInt(),
                        fps = framesPerSecond,
                        packetLoss = packetLoss
                    ))
                }
                mainHandler.postDelayed(this, 2000)
            }
        }
        mainHandler.postDelayed(statsCheckerRunnable!!, 3000)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()

        if (role == "sender") {
            stateManager.emitEvent(ServiceEvent.SenderDisconnected("Servis sonlandirildi"))
        }

        statsCheckerRunnable?.let { mainHandler.removeCallbacks(it) }

        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
            } catch (e: Exception) {
                Log.e(TAG, "Kayit temizleme hatasi", e)
            }
        }

        if (startTime > 0 && roomId.isNotEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            if (duration > 1000) {
                try {
                    val app = application as ScreenMirrorApp
                    val room = RoomHistory(
                        roomName = roomId,
                        role = role,
                        duration = duration,
                        participantCount = participantCount,
                        startTime = startTime,
                        endTime = System.currentTimeMillis()
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        app.roomHistoryManager.saveRoom(room)
                    }
                    Log.i(TAG, "Room history kaydedildi: $roomId")
                } catch (e: Exception) {
                    Log.e(TAG, "Room history kaydetme hatasi", e)
                }
            }
        }

        webRtcReady = false
        offerPending = false
        pendingResultCode = 0
        pendingData = null

        try { cloudSignaling?.close() } catch (_: Exception) {}
        cloudSignaling = null

        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        capturer = null

        try { videoSource?.dispose() } catch (_: Exception) {}
        videoSource = null

        try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
        surfaceTextureHelper = null

        localTrack = null

        try {
            val r = renderer
            if (r != null) remoteTrack?.removeSink(r)
        } catch (_: Exception) {}
        remoteTrack = null

        try { peerConnection?.close() } catch (_: Exception) {}
        peerConnection = null

        try { factory?.dispose() } catch (_: Exception) {}
        factory = null
        factoryInitialized = false

        try { eglBase?.release() } catch (_: Exception) {}
        eglBase = null

        try {
            mainHandler.post {
                try { renderer?.release() } catch (_: Exception) {}
                renderer = null
            }
        } catch (_: Exception) {}

        try { executor.shutdownNow() } catch (_: Exception) {}

        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        mainHandler.removeCallbacksAndMessages(null)

        Log.i(TAG, "onDestroy - temizlik tamamlandi")
    }
}
