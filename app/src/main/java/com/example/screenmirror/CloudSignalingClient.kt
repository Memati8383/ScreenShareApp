package com.example.screenmirror

import android.util.Log
import okhttp3.*
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

class CloudSignalingClient(
    private val room: String,
    private val role: String,
    private val onRemoteDescription: (SessionDescription) -> Unit,
    private val onRemoteIce: (IceCandidate) -> Unit,
    private val onPeerJoined: () -> Unit,
    private val onPeerLeft: () -> Unit = {},
    private val onViewerCountChanged: (Int) -> Unit = {},
    private val onDisconnect: ((String) -> Unit)? = null
) {
    private companion object {
        const val TAG = "CloudSig"
        const val WS_URL = "wss://wss.getlost.ovh"
        const val PONG_TIMEOUT_MS = 60000L
        const val HEARTBEAT_INTERVAL_MS = 25000L
        const val ROSTER_TIMEOUT_MS = 10000L
        const val CLOSE_CODE_NORMAL = 1000
        const val INITIAL_RECONNECT_DELAY_MS = 1000L
        const val MAX_RECONNECT_DELAY_MS = 30000L
        const val MAX_RECONNECT_ATTEMPTS = 20
        const val MAX_RECONNECT_SHIFT = 30
    }

    private var client: OkHttpClient? = null
    private var ws: WebSocket? = null
    private val peerId = "${role}_${java.util.UUID.randomUUID()}"
    private var registered = false
    @Volatile private var connected = false
    private var peerCount = 0
    private var closed = false
    private var reconnectAttempt = 0
    private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var lastPongTime = 0L
    private var rosterReceived = false
    private val rosterTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!connected || closed) return
            val now = System.currentTimeMillis()
            if (lastPongTime > 0 && now - lastPongTime > PONG_TIMEOUT_MS) {
                Log.e(TAG, "PONG alinmadi, yeniden baglaniliyor")
                onDisconnect?.invoke("Sunucu yanit vermiyor, yeniden baglaniliyor...")
                try { ws?.close(CLOSE_CODE_NORMAL, "pong timeout") } catch (_: Exception) {}
                return
            }
            sendPing()
            lastPongTime = now
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    private val rosterTimeoutRunnable = Runnable {
        if (!rosterReceived && connected && !closed) {
            Log.e(TAG, "ROSTER TIMEOUT: 10 sn icinde roster alinamadi!")
            onDisconnect?.invoke("Sunucu yanit vermiyor, yeniden baglaniliyor...")
            try { ws?.close(CLOSE_CODE_NORMAL, "roster timeout") } catch (_: Exception) {}
        }
    }

    fun connect() {
        Log.i(TAG, "Baglaniliyor: $WS_URL, peer: $peerId")
        closeExistingConnection()
        initHttpClient()
        onDisconnect?.invoke("Signaling sunucusuna baglaniliyor...")
        createWebSocket()
    }

    private fun closeExistingConnection() {
        try {
            ws?.close(CLOSE_CODE_NORMAL, "yeniden baglaniliyor")
            ws = null
        } catch (_: Exception) {}
    }

    private fun initHttpClient() {
        if (client == null) {
            client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }
    }

    private fun createWebSocket() {
        val request = Request.Builder().url(WS_URL).build()
        ws = client!!.newWebSocket(request, createWebSocketListener())
    }

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WS onOpen - HTTP ${response.code}, protocol=${response.protocol}")
            connected = true
            registered = false
            reconnectAttempt = 0
            onDisconnect?.invoke("Signaling baglandi, odaya katiliyor...")
            sendRegister()
            heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "MESAJ alindi (${text.length} bytes)")
            handle(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WS onFailure: ${t.message}", t)
            handleConnectionFailure(t.message)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WS onClosed: code=$code, reason=$reason")
            handleConnectionClosed(code, reason)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WS onClosing: code=$code, reason=$reason")
            webSocket.close(code, reason)
        }
    }

    private fun handleConnectionFailure(message: String?) {
        connected = false
        registered = false
        heartbeatHandler.removeCallbacksAndMessages(null)
        rosterTimeoutHandler.removeCallbacksAndMessages(null)
        onDisconnect?.invoke("Baglanti hatasi: $message")
        scheduleReconnect()
    }

    private fun handleConnectionClosed(code: Int, reason: String) {
        connected = false
        registered = false
        heartbeatHandler.removeCallbacksAndMessages(null)
        rosterTimeoutHandler.removeCallbacksAndMessages(null)
        if (!closed) {
            onDisconnect?.invoke("Baglanti kesildi ($code)")
            scheduleReconnect()
        }
    }

    private fun sendRegister() {
        val msg = JSONObject()
            .put("kind", "register")
            .put("roomId", room)
            .put("from", peerId)
            .put("announce", true)
            .toString()
        Log.i(TAG, "REGISTER gonderiliyor: roomId=$room")
        val sent = send(msg)
        Log.i(TAG, "REGISTER gonderildi: sent=$sent, ws=${ws != null}, connected=$connected")
        onDisconnect?.invoke("Odaya katiliyor: $room")
        startRosterTimeout()
    }

    private fun startRosterTimeout() {
        rosterReceived = false
        rosterTimeoutHandler.removeCallbacksAndMessages(null)
        rosterTimeoutHandler.postDelayed(rosterTimeoutRunnable, ROSTER_TIMEOUT_MS)
    }

    private fun handle(text: String) {
        try {
            val msg = JSONObject(text)
            Log.d(TAG, "MSG parsed: keys=${msg.keys().asSequence().toList()}")

            if (handleSystemMessage(msg)) return
            if (handleErrorMessage(msg)) return

            val kind = msg.optString("kind", "")
            val from = msg.optString("from", "")

            if (from == peerId) {
                Log.d(TAG, "Kendinden gelen, atlandi")
                return
            }

            handlePeerMessage(kind, from, msg)
        } catch (e: Exception) {
            Log.e(TAG, "Mesaj isleme hatasi: ${e.message}", e)
        }
    }

    private fun handleSystemMessage(msg: JSONObject): Boolean {
        if (!msg.has("sys")) return false

        val sys = msg.getString("sys")
        Log.i(TAG, "SYS mesaji: $sys")

        when (sys) {
            "roster" -> handleRoster(msg)
            "pong" -> handlePong()
            "error" -> handleServerError(msg)
            else -> Log.w(TAG, "Bilinmeyen sys mesaji: $sys")
        }
        return true
    }

    private fun handleRoster(msg: JSONObject) {
        rosterReceived = true
        rosterTimeoutHandler.removeCallbacksAndMessages(null)
        val roster = msg.getJSONArray("roster")
        val newCount = roster.length()
        Log.i(TAG, "ROSTER: $newCount peer var (onceki: $peerCount)")

        val viewers = if (newCount > 1) newCount - 1 else 0
        onViewerCountChanged(viewers)

        if (newCount > peerCount && newCount > 1) {
            Log.i(TAG, "Baska peer katildi! onPeerJoined cagiriliyor")
            onPeerJoined()
        } else if (newCount < peerCount && peerCount > 1) {
            Log.i(TAG, "Peer ayrildi! onPeerLeft cagiriliyor")
            onPeerLeft()
        }
        peerCount = newCount
    }

    private fun handlePong() {
        lastPongTime = System.currentTimeMillis()
        Log.d(TAG, "PONG alindi")
    }

    private fun handleServerError(msg: JSONObject) {
        val code = msg.optString("code", "unknown")
        Log.e(TAG, "SUNUCU HATASI: sys=error, code=$code")
        onDisconnect?.invoke("Sunucu hatasi: $code")
    }

    private fun handleErrorMessage(msg: JSONObject): Boolean {
        if (!msg.has("error")) return false
        Log.e(TAG, "SUNUCU HATASI: ${msg.getString("error")}")
        return true
    }

    private fun handlePeerMessage(kind: String, from: String, msg: JSONObject) {
        when (kind) {
            "desc" -> handleDescriptionMessage(from, msg)
            "ice" -> handleIceMessage(from, msg)
            else -> Log.d(TAG, "Bilinmeyen kind: $kind")
        }
    }

    private fun handleDescriptionMessage(from: String, msg: JSONObject) {
        val payload = msg.getJSONObject("payload")
        val sdpType = payload.getString("type")
        val sdpStr = payload.getString("sdp")
        Log.i(TAG, "DESC alindi: $sdpType from $from")

        val type = when (sdpType) {
            "offer" -> SessionDescription.Type.OFFER
            "answer" -> SessionDescription.Type.ANSWER
            else -> return
        }
        onRemoteDescription(SessionDescription(type, sdpStr))
    }

    private fun handleIceMessage(from: String, msg: JSONObject) {
        val payload = msg.getJSONObject("payload")
        Log.i(TAG, "ICE alindi from $from")
        onRemoteIce(
            IceCandidate(
                payload.optString("sdpMid", ""),
                payload.optInt("sdpMLineIndex", 0),
                payload.getString("candidate")
            )
        )
    }

    private fun sendPing() {
        val ping = JSONObject().put("kind", "ping").toString()
        Log.d(TAG, "HEARTBEAT ping gonderiliyor")
        send(ping)
    }

    fun sendOffer(sdp: SessionDescription) {
        val msg = createSDPMessage("offer", sdp.description)
        Log.i(TAG, "OFFER gonderiliyor")
        val sent = send(msg)
        Log.i(TAG, "OFFER gonderildi: sent=$sent")
    }

    fun sendAnswer(sdp: SessionDescription) {
        val msg = createSDPMessage("answer", sdp.description)
        Log.i(TAG, "ANSWER gonderiliyor")
        val sent = send(msg)
        Log.i(TAG, "ANSWER gonderildi: sent=$sent")
    }

    private fun createSDPMessage(type: String, sdp: String): String {
        val payload = JSONObject()
            .put("type", type)
            .put("sdp", sdp)

        return JSONObject()
            .put("kind", "desc")
            .put("roomId", room)
            .put("from", peerId)
            .put("payload", payload)
            .toString()
    }

    fun sendIce(candidate: IceCandidate) {
        val payload = JSONObject()
            .put("candidate", candidate.sdp)
            .put("sdpMid", candidate.sdpMid)
            .put("sdpMLineIndex", candidate.sdpMLineIndex)

        val msg = JSONObject()
            .put("kind", "ice")
            .put("roomId", room)
            .put("from", peerId)
            .put("payload", payload)
            .toString()
        Log.i(TAG, "ICE gonderiliyor")
        send(msg)
    }

    private fun send(text: String): Boolean {
        return try {
            val sent = ws?.send(text) ?: false
            if (!sent) {
                Log.w(TAG, "MESAJ GONDERILEMEDI (ws=$ws, connected=$connected)")
            }
            Log.d(TAG, "Gonderildi($sent)")
            sent
        } catch (e: Exception) {
            Log.e(TAG, "Gonderme hatasi: ${e.message}")
            false
        }
    }

    private fun scheduleReconnect() {
        if (closed) return
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Maksimum reconnect denemesine ulasildi ($MAX_RECONNECT_ATTEMPTS)")
            onDisconnect?.invoke("Yeniden baglanma limiti asildi")
            return
        }
        val delay = calculateReconnectDelay()
        reconnectAttempt++
        Log.i(TAG, "${delay}ms sonra yeniden baglanacak (deneme: $reconnectAttempt)")
        onDisconnect?.invoke("Yeniden baglaniliyor... (deneme: $reconnectAttempt)")
        reconnectHandler.postDelayed({
            if (!closed && !connected) {
                Log.i(TAG, "Yeniden baglaniyor...")
                connect()
            }
        }, delay)
    }

    private fun calculateReconnectDelay(): Long {
        val shift = minOf(reconnectAttempt, MAX_RECONNECT_SHIFT)
        return minOf(INITIAL_RECONNECT_DELAY_MS * (1L shl shift), MAX_RECONNECT_DELAY_MS)
    }

    fun close() {
        closed = true
        reconnectHandler.removeCallbacksAndMessages(null)
        heartbeatHandler.removeCallbacksAndMessages(null)
        rosterTimeoutHandler.removeCallbacksAndMessages(null)
        try {
            registered = false
            ws?.close(CLOSE_CODE_NORMAL, "kapat")
            ws = null
            client?.dispatcher?.executorService?.shutdown()
            client?.connectionPool?.evictAll()
            client = null
        } catch (_: Exception) {}
    }
}
