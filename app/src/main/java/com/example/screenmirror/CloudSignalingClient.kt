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
    private val onPeerJoined: () -> Unit
) {
    private var client: OkHttpClient? = null
    private var ws: WebSocket? = null
    private val peerId = "${role}_${System.currentTimeMillis()}"
    private var registered = false
    private var connected = false
    private var peerCount = 0
    private var closed = false
    private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun connect() {
        val url = "wss://wss.getlost.ovh"
        Log.i("CloudSig", "Baglaniliyor: $url, peer: $peerId")

        client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        ws = client!!.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("CloudSig", "WS Baglandi, register gonderiliyor")
                connected = true
                registered = false
                sendRegister()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("CloudSig", "MESAJ: $text")
                handle(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("CloudSig", "HATA: ${t.message}", t)
                connected = false
                registered = false
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("CloudSig", "Kapandi: $code $reason")
                connected = false
                registered = false
                if (!closed) {
                    scheduleReconnect()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i("CloudSig", "Kapatiliyor: $code $reason")
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
        Log.i("CloudSig", "REGISTER: $msg")
        send(msg)
    }

    private fun handle(text: String) {
        try {
            val msg = JSONObject(text)

            if (msg.has("sys")) {
                val sys = msg.getString("sys")
                if (sys == "roster") {
                    val roster = msg.getJSONArray("roster")
                    val newCount = roster.length()
                    Log.i("CloudSig", "ROSTER: $newCount peer var (onceki: $peerCount)")
                    if (newCount > peerCount && newCount > 1) {
                        Log.i("CloudSig", "Baska peer katildi! onPeerJoined cagiriliyor")
                        onPeerJoined()
                    }
                    peerCount = newCount
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
        send(msg)
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
        send(msg)
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

    private fun send(text: String) {
        try {
            val sent = ws?.send(text)
            Log.d("CloudSig", "Gonderildi($sent): $text")
        } catch (e: Exception) {
            Log.e("CloudSig", "Gonderme hatasi: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (closed) return
        Log.i("CloudSig", "3 saniye sonra yeniden baglanacak...")
        reconnectHandler.postDelayed({
            if (!closed && !connected) {
                Log.i("CloudSig", "Yeniden baglaniyor...")
                connect()
            }
        }, 3000)
    }

    fun close() {
        closed = true
        try {
            registered = false
            ws?.close(1000, "kapat")
            client?.dispatcher?.executorService?.shutdown()
            client?.connectionPool?.evictAll()
        } catch (_: Exception) {}
    }
}
