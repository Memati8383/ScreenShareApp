package com.example.screenmirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import com.example.screenmirror.data.RoomHistory
import com.example.screenmirror.data.RoomHistoryManager

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
    }

    private var role = "viewer"
    private var roomId = ""

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var cloudSignaling: CloudSignalingClient? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var localTrack: VideoTrack? = null

    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val pendingIceCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val remoteDescriptionSet = ConcurrentHashMap<String, Boolean>()

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webRtcReady = false
    private var startTime = 0L
    private var participantCount = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved - uygulama silindi")
        super.onTaskRemoved(rootIntent)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        try {
            val ch = NotificationChannel("screen", "ScreenShare", NotificationManager.IMPORTANCE_DEFAULT)
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
                role = intent.getStringExtra("role") ?: "viewer"
                roomId = intent.getStringExtra("room") ?: "oda1"
                startTime = System.currentTimeMillis()

                if (role == "sender") {
                    val code = intent.getIntExtra("resultCode", 0)
                    val data = intent.getParcelableExtra<Intent>("data")
                    if (code != 0 && data != null) {
                        pendingResultCode = code
                        pendingData = data
                        Log.i(TAG, "sender projection data alindi: code=$code")
                    }
                }

                Log.i(TAG, "role=$role room=$roomId")
            }

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
            val notif = Notification.Builder(this, "screen")
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
                startForeground(1, notif, type)
            } else {
                startForeground(1, notif)
            }
        } catch (e: Exception) {
            Log.e(TAG, "foreground hatasi", e)
        }
    }

    private fun showJoinNotification() {
        try {
            val notif = Notification.Builder(this, "screen")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle("Screen Mirror")
                .setContentText("Birisi odaya katildi!")
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(2, notif)
        } catch (e: Exception) {
            Log.e(TAG, "bildirim hatasi", e)
        }
    }

    private fun showBroadcastStartNotification() {
        try {
            val notif = Notification.Builder(this, "screen")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Screen Mirror")
                .setContentText("Yayın başlatıldı - Bağlantı bekleniyor")
                .setAutoCancel(true)
                .build()
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(3, notif)
        } catch (e: Exception) {
            Log.e(TAG, "bildirim hatasi", e)
        }
    }

    private fun initFactory() {
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
    }

    private fun getRtcConfig(): PeerConnection.RTCConfiguration {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
        )
        val config = PeerConnection.RTCConfiguration(iceServers)
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        return config
    }

    private fun createPeerConnectionForPeer(targetPeerId: String): PeerConnection? {
        val f = factory ?: return null

        val pc = f.createPeerConnection(getRtcConfig(), object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.i(TAG, "ICE [$targetPeerId]: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED) {
                    Log.i(TAG, "Peer baglantisi kesildi: $targetPeerId")
                    cleanupPeerConnection(targetPeerId)
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onIceCandidate(candidate: IceCandidate) {
                cloudSignaling?.sendIce(candidate, targetPeerId)
            }
            override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {}
            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track()
                if (track is VideoTrack) {
                    mainHandler.post {
                        renderer?.let { track.addSink(it) }
                    }
                    postState("Canli goruntu aliniyor")
                }
            }
        })

        if (pc != null) {
            peerConnections[targetPeerId] = pc
            pendingIceCandidates[targetPeerId] = mutableListOf()
            remoteDescriptionSet[targetPeerId] = false
            Log.i(TAG, "PeerConnection olusturuldu: $targetPeerId")
        }
        return pc
    }

    private fun cleanupPeerConnection(targetPeerId: String) {
        peerConnections.remove(targetPeerId)?.let { pc ->
            try { pc.close() } catch (_: Exception) {}
        }
        pendingIceCandidates.remove(targetPeerId)
        remoteDescriptionSet.remove(targetPeerId)
        Log.i(TAG, "PeerConnection temizlendi: $targetPeerId")
    }

    private fun handlePeerDescription(fromPeerId: String, sdp: SessionDescription) {
        var pc = peerConnections[fromPeerId]

        if (sdp.type == SessionDescription.Type.OFFER && pc == null) {
            Log.i(TAG, "Offer alindi, PeerConnection olusturuluyor: $fromPeerId")
            pc = createPeerConnectionForPeer(fromPeerId)
        }

        if (pc == null) {
            Log.e(TAG, "PeerConnection bulunamadi: $fromPeerId")
            return
        }

        pc.setRemoteDescription(sdpObserver(
            onSetSuccess = {
                remoteDescriptionSet[fromPeerId] = true
                pendingIceCandidates[fromPeerId]?.forEach { candidate ->
                    pc.addIceCandidate(candidate)
                }
                pendingIceCandidates[fromPeerId]?.clear()

                if (sdp.type == SessionDescription.Type.OFFER) {
                    Log.i(TAG, "Offer alindi, answer olusturuluyor: $fromPeerId")
                    pc.createAnswer(sdpObserver(
                        onCreateSuccess = { ans ->
                            pc.setLocalDescription(sdpObserver(
                                onSetSuccess = {
                                    Log.i(TAG, "Answer gonderildi: $fromPeerId")
                                    cloudSignaling?.sendAnswer(ans, fromPeerId)
                                }
                            ), ans)
                        }
                    ), MediaConstraints())
                }
            },
            onSetFailure = { error ->
                Log.e(TAG, "setRemoteDescription hatasi: $error")
            }
        ), sdp)
    }

    private fun handlePeerIce(fromPeerId: String, candidate: IceCandidate) {
        val pc = peerConnections[fromPeerId]
        if (pc != null && remoteDescriptionSet[fromPeerId] == true) {
            pc.addIceCandidate(candidate)
        } else {
            pendingIceCandidates[fromPeerId]?.add(candidate)
        }
    }

    private fun createOfferForPeer(targetPeerId: String) {
        val pc = peerConnections[targetPeerId]
        if (pc == null || localTrack == null) return

        pc.createOffer(sdpObserver(
            onCreateSuccess = { offer ->
                pc.setLocalDescription(sdpObserver(
                    onSetSuccess = {
                        cloudSignaling?.sendOffer(offer, targetPeerId)
                    }
                ), offer)
            }
        ), MediaConstraints())
    }

    private fun startWebRtc(r: SurfaceViewRenderer) {
        Log.i(TAG, "startWebRtc basliyor")
        try {
            eglBase = EglBase.create()
            r.init(eglBase!!.eglBaseContext, null)
            r.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            r.setZOrderMediaOverlay(true)
            Log.i(TAG, "renderer init OK")

            initFactory()

            cloudSignaling = CloudSignalingClient(
                room = roomId,
                role = role,
                onRemoteDescription = { peerId, sdp ->
                    Log.i(TAG, "Cloud: remote sdp alindi: ${sdp.type} from $peerId")
                    postState("Es cihaz baglandi")
                    executor.execute { handlePeerDescription(peerId, sdp) }
                },
                onRemoteIce = { peerId, candidate ->
                    executor.execute { handlePeerIce(peerId, candidate) }
                },
                onPeerJoined = { peerId ->
                    Log.i(TAG, "Peer katildi: $peerId")
                    postState("Yeni izleyici: $peerId")
                    showJoinNotification()
                    participantCount++

                    if (role == "sender") {
                        executor.execute {
                            val pc = createPeerConnectionForPeer(peerId)
                            if (pc != null && localTrack != null) {
                                pc.addTrack(localTrack, listOf("stream0"))
                                createOfferForPeer(peerId)
                            } else if (pc != null) {
                                Log.i(TAG, "localTrack henuz hazir, offer bekleniyor")
                            }
                        }
                    }
                },
                onPeerLeft = { peerId ->
                    Log.i(TAG, "Peer ayrildi: $peerId")
                    participantCount = maxOf(1, participantCount - 1)
                    executor.execute { cleanupPeerConnection(peerId) }
                },
                onViewerCountChanged = { count ->
                    mainHandler.post { onViewerCountChanged?.invoke(count) }
                }
            )
            Log.i(TAG, "cloud signaling objesi olusturuldu")

            webRtcReady = true
            postState("WebRTC hazir")

            startStatsChecker()

            cloudSignaling?.connect()

            if (role == "sender" && pendingResultCode != 0 && pendingData != null) {
                val code = pendingResultCode; val data = pendingData
                pendingResultCode = 0; pendingData = null
                executor.execute {
                    startCapture(code, data)
                    Log.i(TAG, "sender: capture baslatildi, izleyici bekleniyor")
                    postState("Izleyici bekleniyor...")
                }
            } else {
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
            Log.i(TAG, "capture baslatildi, localTrack olusturuldu")

            peerConnections.forEach { (peerId, pc) ->
                try {
                    pc.addTrack(localTrack, listOf("stream0"))
                    createOfferForPeer(peerId)
                    Log.i(TAG, "localTrack eklendi: $peerId")
                } catch (e: Exception) {
                    Log.e(TAG, "localTrack ekleme hatasi: $peerId", e)
                }
            }

            postState("Ekran yayinda")
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
        try {
            val intent = Intent("com.example.screenmirror.STATE_CHANGED").apply {
                putExtra("state", s)
                setPackage(packageName)
            }
            sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    private fun startStatsChecker() {
        val statsRunnable = object : Runnable {
            override fun run() {
                if (!webRtcReady) return

                var totalFps = 0
                var totalPacketLoss = 0.0
                var totalRtt = 0.0
                var activeConnections = 0

                peerConnections.forEach { (peerId, pc) ->
                    pc.getStats { report ->
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

                        totalFps += framesPerSecond
                        totalPacketLoss += packetLoss
                        totalRtt += roundTripTime
                        activeConnections++
                    }
                }

                if (activeConnections > 0) {
                    val avgRtt = totalRtt / activeConnections
                    val avgPacketLoss = totalPacketLoss / activeConnections

                    val quality = when {
                        avgPacketLoss > 5 || avgRtt > 0.5 -> "KOTU"
                        avgPacketLoss > 2 || avgRtt > 0.3 -> "ORTA"
                        else -> "IYI"
                    }

                    val statsText = String.format(
                        "RTT: %.0fms | Kayip: %.1f%% | FPS: %d | Izleyici: %d",
                        avgRtt * 1000, avgPacketLoss, totalFps, peerConnections.size
                    )

                    mainHandler.post {
                        onConnectionQuality?.invoke("$quality|$statsText")
                    }
                }
                mainHandler.postDelayed(this, 2000)
            }
        }
        mainHandler.postDelayed(statsRunnable, 3000)
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()

        if (startTime > 0 && roomId.isNotEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            if (duration > 1000) {
                try {
                    val historyManager = RoomHistoryManager(applicationContext)
                    val room = RoomHistory(
                        roomName = roomId,
                        role = role,
                        duration = duration,
                        participantCount = participantCount,
                        startTime = startTime,
                        endTime = System.currentTimeMillis()
                    )
                    historyManager.saveRoom(room)
                    Log.i(TAG, "Room history kaydedildi: $roomId")
                } catch (e: Exception) {
                    Log.e(TAG, "Room history kaydetme hatasi", e)
                }
            }
        }

        webRtcReady = false
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

        peerConnections.forEach { (peerId, pc) ->
            try { pc.close() } catch (_: Exception) {}
        }
        peerConnections.clear()
        pendingIceCandidates.clear()
        remoteDescriptionSet.clear()

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
            nm.cancel(1)
        } catch (_: Exception) {}

        mainHandler.removeCallbacksAndMessages(null)

        Log.i(TAG, "onDestroy - temizlik tamamlandi")
    }
}
