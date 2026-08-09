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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.webrtc.*
import java.util.concurrent.Executors
import com.example.screenmirror.data.RoomHistory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenShareService : Service() {

    companion object {
        private const val TAG = "ScreenShareSvc"

        @Volatile var pendingResultCode: Int = 0
        @Volatile var pendingData: Intent? = null
        @Volatile var renderer: SurfaceViewRenderer? = null
        private var factoryInitialized = false
        @Volatile var onState: ((String) -> Unit)? = null
        @Volatile var onViewerCountChanged: ((Int) -> Unit)? = null
        @Volatile var onConnectionQuality: ((String) -> Unit)? = null

        @Volatile var captureWidth: Int = 1280
        @Volatile var captureHeight: Int = 720
        @Volatile var captureFps: Int = 30
        @Volatile var isFrozen: Boolean = false
        @Volatile var onRecordingStateChanged: ((Boolean) -> Unit)? = null
    }

    private val roomHistoryManager by lazy {
        (application as ScreenMirrorApp).roomHistoryManager
    }

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
    private var participantCount = 1
    private var statsCheckerRunnable: Runnable? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved - uygulama silindi")
        super.onTaskRemoved(rootIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val ch = NotificationChannel(
                NotificationConstants.CHANNEL_ID_SCREEN,
                NotificationConstants.CHANNEL_NAME_SCREEN,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
            Log.i(TAG, "onCreate - kanal olusturuldu")
        } catch (e: Exception) {
            Log.e(TAG, "onCreate hatasi", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        try {
            if (intent != null) {
                val action = intent.action
                when (action) {
                    "com.example.screenmirror.START_RECORDING" -> {
                        val resultCode = intent.getIntExtra("resultCode", 0)
                        @Suppress("DEPRECATION")
                        val data = intent.getParcelableExtra<Intent>("data")
                        executor.execute { startRecording(resultCode, data) }
                        return START_STICKY
                    }
                    "com.example.screenmirror.STOP_RECORDING" -> {
                        executor.execute { stopRecording() }
                        return START_STICKY
                    }
                }

                role = intent.getStringExtra("role") ?: "viewer"
                roomId = intent.getStringExtra("room") ?: "oda1"
                startTime = System.currentTimeMillis()
                Log.i(TAG, "role=$role room=$roomId")

                if (role == "sender") {
                    val rc = intent.getIntExtra("resultCode", 0)
                    @Suppress("DEPRECATION")
                    val d = intent.getParcelableExtra<Intent>("data")
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

            showNotification(if (role == "sender") "Yayin aktif" else "Izleme aktif")

            if (role == "sender") {
                showBroadcastStartNotification()
            }

            val r = renderer
            if (r == null) {
                Log.e(TAG, "renderer NULL")
                postState("HATA: SurfaceView bulunamadi")
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
                postState("Surface bekleniyor...")
                sv?.holder?.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) {
                        Log.i(TAG, "surfaceCreated - WebRTC baslatiliyor")
                        mainHandler.post { startWebRtc(r) }
                    }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {
                        Log.w(TAG, "surfaceDestroyed")
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand hatasi", e)
            postState("HATA: ${e.message}")
        }
        return START_STICKY
    }

    private fun showNotification(text: String) {
        try {
            val notif = Notification.Builder(this, NotificationConstants.CHANNEL_ID_SCREEN)
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
                startForeground(NotificationConstants.NOTIFICATION_ID_FOREGROUND, notif, type)
            } else {
                startForeground(NotificationConstants.NOTIFICATION_ID_FOREGROUND, notif)
            }
        } catch (e: Exception) {
            Log.e(TAG, "foreground hatasi", e)
        }
    }

    private fun showJoinNotification() {
        try {
            val notif = Notification.Builder(this, NotificationConstants.CHANNEL_ID_SCREEN)
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle("Screen Mirror")
                .setContentText("Birisi odaya katildi!")
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NotificationConstants.NOTIFICATION_ID_JOIN, notif)
        } catch (e: Exception) {
            Log.e(TAG, "bildirim hatasi", e)
        }
    }

    private fun showBroadcastStartNotification() {
        try {
            val notif = Notification.Builder(this, NotificationConstants.CHANNEL_ID_SCREEN)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Screen Mirror")
                .setContentText("Yayın başlatıldı - Bağlantı bekleniyor")
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NotificationConstants.NOTIFICATION_ID_BROADCAST, notif)
        } catch (e: Exception) {
            Log.e(TAG, "bildirim hatasi", e)
        }
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

            peerConnection = factory!!.createPeerConnection(config, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    Log.i(TAG, "ICE: $state")
                    postState("ICE: $state")
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onDataChannel(channel: DataChannel) {}
                override fun onRenegotiationNeeded() {}
                override fun onIceCandidate(candidate: IceCandidate) {
                    cloudSignaling?.sendIce(candidate)
                }
                override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {}
                override fun onTrack(transceiver: RtpTransceiver) {
                    val track = transceiver.receiver.track()
                    if (track is VideoTrack) {
                        remoteTrack = track
                        mainHandler.post { renderer?.let { remoteTrack?.addSink(it) } }
                        postState("Canli goruntu aliniyor")
                    }
                }
            })
            Log.i(TAG, "peerConnection OK")

            cloudSignaling = CloudSignalingClient(
                roomId, role,
                onRemoteDescription = { sdp ->
                    Log.i(TAG, "Cloud: remote sdp alindi: ${sdp.type}")
                    postState("Es cihaz baglandi")
                    executor.execute { handleRemoteDescription(sdp) }
                },
                onRemoteIce = { c -> executor.execute { handleRemoteIce(c) } },
                onPeerJoined = {
                    postState("Es cihaz baglandi")
                    participantCount = 2
                    showJoinNotification()
                    if (role == "sender") {
                        if (localTrack != null) {
                            executor.execute { createOffer() }
                        } else {
                            Log.i(TAG, "sender: localTrack henuz hazir degil, offer bekleniyor")
                            offerPending = true
                        }
                    }
                },
                onPeerLeft = {
                    participantCount = 1
                    postState("Izleyici ayrildi")
                },
                onViewerCountChanged = { count ->
                    postViewerCount(count)
                }
            )
            Log.i(TAG, "cloud signaling objesi olusturuldu")

            webRtcReady = true
            postState("WebRTC hazir")

            startStatsChecker()

            if (role == "sender" && pendingResultCode != 0 && pendingData != null) {
                val code = pendingResultCode; val data = pendingData
                pendingResultCode = 0; pendingData = null
                executor.execute {
                    cloudSignaling?.connect()
                    Log.i(TAG, "sender: signaling baglandi, capture baslatiliyor...")
                    Thread.sleep(2000)
                    startCapture(code, data)
                    Log.i(TAG, "sender: capture baslatildi, izleyici bekleniyor")
                    postState("Izleyici bekleniyor...")
                }
            } else {
                cloudSignaling?.connect()
                Log.i(TAG, "viewer: signaling baglandi, offer bekleniyor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "startWebRtc HATA", e)
            postState("WebRTC hatasi: ${e.message}")
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
            postState("Ekran yayinda")

            if (offerPending && role == "sender") {
                offerPending = false
                Log.i(TAG, "sender: localTrack hazir, beklenen offer olusturuluyor")
                createOffer()
            }
        } catch (e: Exception) {
            Log.e(TAG, "startCapture HATA", e)
        }
    }

    fun toggleFreeze() {
        isFrozen = !isFrozen
        if (isFrozen) {
            try { capturer?.stopCapture() } catch (_: Exception) {}
            postState("Yayin donduruldu")
        } else {
            if (pendingResultCode != 0 || pendingData != null) {
            } else {
                postState("Yayin devam ediyor")
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
            mainHandler.post { onRecordingStateChanged?.invoke(true) }
            postState("Kayit baslatildi")
            Log.i(TAG, "Kayit baslatildi: ${recordingFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Kayit baslatma hatasi", e)
            postState("Kayit hatasi: ${e.message}")
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            mainHandler.post { onRecordingStateChanged?.invoke(false) }
            postState("Kayit durduruldu: ${recordingFile?.name}")
            Log.i(TAG, "Kayit durduruldu: ${recordingFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Kayit durdurma hatasi", e)
            postState("Kayit durdurma hatasi")
        }
    }

    fun isRecording(): Boolean = isRecording

    private fun handleRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(sdpObserver(
            onSetSuccess = {
                remoteDescriptionSet = true
                pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
                pendingCandidates.clear()
                if (sdp.type == SessionDescription.Type.OFFER) {
                    peerConnection?.createAnswer(sdpObserver(
                        onCreateSuccess = { ans ->
                            peerConnection?.setLocalDescription(sdpObserver(
                                onSetSuccess = {
                                    cloudSignaling?.sendAnswer(ans)
                                }
                            ), ans)
                        }
                    ), MediaConstraints())
                }
            }
        ), sdp)
    }

    private var remoteDescriptionSet = false
    private val pendingCandidates = mutableListOf<IceCandidate>()

    private fun handleRemoteIce(candidate: IceCandidate) {
        if (remoteDescriptionSet) peerConnection?.addIceCandidate(candidate)
        else pendingCandidates.add(candidate)
    }

    private fun createOffer() {
        if (localTrack == null) return
        peerConnection?.createOffer(sdpObserver(
            onCreateSuccess = { offer ->
                peerConnection?.setLocalDescription(sdpObserver(
                    onSetSuccess = {
                        cloudSignaling?.sendOffer(offer)
                    }
                ), offer)
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

    private fun postState(s: String) {
        Log.i(TAG, s)
        mainHandler.post { onState?.invoke(s) }
        sendBroadcast(Intent("com.example.screenmirror.STATE_CHANGED").apply {
            putExtra("state", s)
            setPackage(packageName)
        })
    }

    private fun postViewerCount(count: Int) {
        mainHandler.post { onViewerCountChanged?.invoke(count) }
        sendBroadcast(Intent("com.example.screenmirror.VIEWER_COUNT_CHANGED").apply {
            putExtra("viewer_count", count)
            setPackage(packageName)
        })
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

                    val quality = when {
                        packetLoss > 5 || roundTripTime > 0.5 -> "KOTU"
                        packetLoss > 2 || roundTripTime > 0.3 -> "ORTA"
                        else -> "IYI"
                    }

                    if (quality == "KOTU" && captureWidth > 854) {
                        captureWidth = 854
                        captureHeight = 480
                        capturer?.changeCaptureFormat(captureWidth, captureHeight, captureFps)
                        Log.i(TAG, "Kalite dusuruldu: ${captureWidth}x${captureHeight}")
                    } else if (quality == "IYI" && captureWidth < 1280) {
                        captureWidth = 1280
                        captureHeight = 720
                        capturer?.changeCaptureFormat(captureWidth, captureHeight, captureFps)
                        Log.i(TAG, "Kalite artirildi: ${captureWidth}x${captureHeight}")
                    }

                    val statsText = String.format(
                        "RTT: %.0fms | Kayip: %.1f%% | FPS: %d",
                        roundTripTime * 1000, packetLoss, framesPerSecond
                    )

                    mainHandler.post {
                        onConnectionQuality?.invoke("$quality|$statsText")
                    }

                    sendBroadcast(Intent("com.example.screenmirror.CONNECTION_QUALITY").apply {
                        putExtra("quality", quality)
                        putExtra("rtt", (roundTripTime * 1000).toInt())
                        putExtra("fps", framesPerSecond)
                        putExtra("packet_loss", packetLoss)
                        setPackage(packageName)
                    })
                }
                mainHandler.postDelayed(this, 2000)
            }
        }
        mainHandler.postDelayed(statsCheckerRunnable!!, 3000)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()

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
                    val room = RoomHistory(
                        roomName = roomId,
                        role = role,
                        duration = duration,
                        participantCount = participantCount,
                        startTime = startTime,
                        endTime = System.currentTimeMillis()
                    )
                    roomHistoryManager.saveRoom(room)
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
            }
        } catch (_: Exception) {}

        try { executor.shutdownNow() } catch (_: Exception) {}

        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(NotificationConstants.NOTIFICATION_ID_FOREGROUND)
        } catch (_: Exception) {}

        mainHandler.removeCallbacksAndMessages(null)

        Log.i(TAG, "onDestroy - temizlik tamamlandi")
    }
}
