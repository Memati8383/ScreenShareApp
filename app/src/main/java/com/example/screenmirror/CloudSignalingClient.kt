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
    private var client: OkHttpClient? = null
    private var ws: WebSocket? = null
    private val peerId = "${role}_${System.currentTimeMillis()}"
    private var registered = false
    @Volatile private var connected = false
    private var peerCount = 0
    private var closed = false
    private var reconnectAttempt = 0
    private val maxReconnectDelay = 30000L
    private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!connected || closed) return
            val ping = JSONObject().put("kind", "ping").toString()
            Log.d("CloudSig", "HEARTBEAT ping gonderiliyor")
            send(ping)
            heartbeatHandler.postDelayed(this, 25000L)
        }
    }
    private var rosterReceived = false
    private val rosterTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val rosterTimeoutRunnable = Runnable {
        if (!rosterReceived && connected && !closed) {
            Log.e("CloudSig", "ROSTER TIMEOUT: 10 sn icinde roster alinamadi!")
            onDisconnect?.invoke("Sunucu yanit vermiyor, yeniden baglaniliyor...")
            try { ws?.close(1000, "roster timeout") } catch (_: Exception) {}
        }
    }

    fun connect() {
        val url = "wss://wss.getlost.ovh"
        Log.i("CloudSig", "Baglaniliyor: $url, peer: $peerId")

        try {
            ws?.close(1000, "yeniden baglaniliyor")
            ws = null
        } catch (_: Exception) {}

        if (client == null) {
            client = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }

        onDisconnect?.invoke("Signaling sunucusuna baglaniliyor...")
        val request = Request.Builder().url(url).build()
        ws = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("CloudSig", "WS onOpen - HTTP ${response.code}, protocol=${response.protocol}")
                connected = true
                registered = false
                reconnectAttempt = 0
                onDisconnect?.invoke("Signaling baglandi, odaya katiliyor...")
                sendRegister()
                heartbeatHandler.postDelayed(heartbeatRunnable, 25000L)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("CloudSig", "MESAJ: $text")
                handle(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("CloudSig", "WS onFailure: ${t.message}", t)
                connected = false
                registered = false
                heartbeatHandler.removeCallbacksAndMessages(null)
                rosterTimeoutHandler.removeCallbacksAndMessages(null)
                onDisconnect?.invoke("Baglanti hatasi: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("CloudSig", "WS onClosed: code=$code, reason=$reason")
                connected = false
                registered = false
                heartbeatHandler.removeCallbacksAndMessages(null)
                rosterTimeoutHandler.removeCallbacksAndMessages(null)
                if (!closed) {
                    onDisconnect?.invoke("Baglanti kesildi ($code)")
                    scheduleReconnect()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("CloudSig", "WS onClosing: code=$code, reason=$reason")
                webSocket.close(code, reason)
            }
        })
    }

    private fun sendRegister() {
        val msg = JSONObject()
            .put("kind", "register")
            .put("roomId", room)
            .put("from", peerId)
            .put("announce", true)
            .toString()
        Log.i("CloudSig", "REGISTER gonderiliyor: $msg")
        val sent = send(msg)
        Log.i("CloudSig", "REGISTER gonderildi: sent=$sent, ws=${ws != null}, connected=$connected")
        onDisconnect?.invoke("Odaya katiliyor: $room")
        rosterReceived = false
        rosterTimeoutHandler.removeCallbacksAndMessages(null)
        rosterTimeoutHandler.postDelayed(rosterTimeoutRunnable, 10000L)
    }

    private fun handle(text: String) {
        try {
            val msg = JSONObject(text)
            Log.d("CloudSig", "MSG parsed: keys=${msg.keys().asSequence().toList()}")

            if (msg.has("sys")) {
                val sys = msg.getString("sys")
                Log.i("CloudSig", "SYS mesaji: $sys")
                when (sys) {
                    "roster" -> {
                        rosterReceived = true
                        rosterTimeoutHandler.removeCallbacksAndMessages(null)
                        val roster = msg.getJSONArray("roster")
                        val newCount = roster.length()
                        Log.i("CloudSig", "ROSTER: $newCount peer var (onceki: $peerCount)")
                        val viewers = if (newCount > 1) newCount - 1 else 0
                        onViewerCountChanged(viewers)
                        if (newCount > peerCount && newCount > 1) {
                            Log.i("CloudSig", "Baska peer katildi! onPeerJoined cagiriliyor")
                            onPeerJoined()
                        } else if (newCount < peerCount && peerCount > 1) {
                            Log.i("CloudSig", "Peer ayrildi! onPeerLeft cagiriliyor")
                            onPeerLeft()
                        }
                        peerCount = newCount
                    }
                    "error" -> {
                        val code = msg.optString("code", "unknown")
                        Log.e("CloudSig", "SUNUCU HATASI: sys=error, code=$code")
                        onDisconnect?.invoke("Sunucu hatasi: $code")
                    }
                    else -> {
                        Log.w("CloudSig", "Bilinmeyen sys mesaji: $sys")
                    }
                }
                return
            }

            if (msg.has("error")) {
                Log.e("CloudSig", "SUNUCU HATASI: ${msg.getString("error")}")
                return
            }

            val kind = msg.optString("kind", "")
            val from = msg.optString("from", "")

            if (from == peerId) {
                Log.d("CloudSig", "Kendinden gelen, atlandi")
                return
            }

            when (kind) {
                "desc" -> {
                    val payload = msg.getJSONObject("payload")
                    val sdpType = payload.getString("type")
                    val sdpStr = payload.getString("sdp")
                    Log.i("CloudSig", "DESC alindi: $sdpType from $from")

                    when (sdpType) {
                        "offer" -> {
                            onRemoteDescription(
                                SessionDescription(SessionDescription.Type.OFFER, sdpStr)
                            )
                        }
                        "answer" -> {
                            onRemoteDescription(
                                SessionDescription(SessionDescription.Type.ANSWER, sdpStr)
                            )
                        }
                    }
                }
                "ice" -> {
                    val payload = msg.getJSONObject("payload")
                    Log.i("CloudSig", "ICE alindi from $from")
                    onRemoteIce(
                        IceCandidate(
                            payload.optString("sdpMid", ""),
                            payload.optInt("sdpMLineIndex", 0),
                            payload.getString("candidate")
                        )
                    )
                }
                else -> {
                    Log.d("CloudSig", "Bilinmeyen kind: $kind")
                }
            }
        } catch (e: Exception) {
            Log.e("CloudSig", "Mesaj isleme hatasi: ${e.message}", e)
        }
    }

    fun sendOffer(sdp: SessionDescription) {
        val payload = JSONObject()
            .put("type", "offer")
            .put("sdp", sdp.description)

        val msg = JSONObject()
            .put("kind", "desc")
            .put("roomId", room)
            .put("from", peerId)
            .put("payload", payload)
            .toString()
        Log.i("CloudSig", "OFFER gonderiliyor")
        val sent = send(msg)
        Log.i("CloudSig", "OFFER gonderildi: $sent")
    }

    fun sendAnswer(sdp: SessionDescription) {
        val payload = JSONObject()
            .put("type", "answer")
            .put("sdp", sdp.description)

        val msg = JSONObject()
            .put("kind", "desc")
            .put("roomId", room)
            .put("from", peerId)
            .put("payload", payload)
            .toString()
        Log.i("CloudSig", "ANSWER gonderiliyor")
        val sent = send(msg)
        Log.i("CloudSig", "ANSWER gonderildi: $sent")
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
        Log.i("CloudSig", "ICE gonderiliyor")
        send(msg)
    }

    private fun send(text: String): Boolean {
        return try {
            val sent = ws?.send(text) ?: false
            if (!sent) {
                Log.w("CloudSig", "MESAJ GONDERILEMEDI (ws=$ws, connected=$connected): $text")
            }
            Log.d("CloudSig", "Gonderildi($sent): $text")
            sent
        } catch (e: Exception) {
            Log.e("CloudSig", "Gonderme hatasi: ${e.message}")
            false
        }
    }

    private fun scheduleReconnect() {
        if (closed) return
        val delay = minOf(1000L * (1 shl reconnectAttempt), maxReconnectDelay)
        reconnectAttempt++
        Log.i("CloudSig", "${delay}ms sonra yeniden baglanacak (deneme: $reconnectAttempt)")
        onDisconnect?.invoke("Yeniden baglaniliyor... (deneme: $reconnectAttempt)")
        reconnectHandler.postDelayed({
            if (!closed && !connected) {
                Log.i("CloudSig", "Yeniden baglaniyor...")
                connect()
            }
        }, delay)
    }

    fun close() {
        closed = true
        reconnectHandler.removeCallbacksAndMessages(null)
        heartbeatHandler.removeCallbacksAndMessages(null)
        rosterTimeoutHandler.removeCallbacksAndMessages(null)
        try {
            registered = false
            ws?.close(1000, "kapat")
            ws = null
            client?.dispatcher?.executorService?.shutdown()
            client?.connectionPool?.evictAll()
            client = null
        } catch (_: Exception) {}
    }
}
