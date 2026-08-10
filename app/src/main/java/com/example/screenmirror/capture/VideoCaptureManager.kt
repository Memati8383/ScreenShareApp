package com.example.screenmirror.capture

import android.content.Context
import android.content.Intent
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class VideoCaptureManager(
    private val context: Context,
    private val eglBase: EglBase,
    private val factory: PeerConnectionFactory,
    private val onCapturerReady: (VideoTrack) -> Unit,
    private val onCapturerError: (String) -> Unit
) {

    companion object {
        private const val TAG = "VideoCaptureMgr"
    }

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var capturer: ScreenCapturerAndroid? = null
    private var localTrack: VideoTrack? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null

    @Volatile
    private var isCapturing = false

    var captureWidth = 1280
    var captureHeight = 720
    var captureFps = 30

    fun startCapture(resultCode: Int, data: Intent?, peerConnection: PeerConnection?) {
        if (isCapturing) {
            Log.w(TAG, "startCapture: zaten yakalaniyor, atlandi")
            return
        }
        if (data == null) {
            Log.e(TAG, "startCapture: data NULL!")
            onCapturerError("Projection data is null")
            return
        }

        try {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoSource = factory.createVideoSource(true)
            capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.w(TAG, "MediaProjection durduruldu")
                    stopCapture()
                }
            })
            capturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            capturer?.startCapture(captureWidth, captureHeight, captureFps)

            localTrack = factory.createVideoTrack("screen0", videoSource)
            peerConnection?.addTrack(localTrack, listOf("stream0"))

            isCapturing = true
            Log.i(TAG, "startCapture basarili: ${captureWidth}x${captureHeight} @ ${captureFps}fps")
            onCapturerReady(localTrack!!)
        } catch (e: Exception) {
            Log.e(TAG, "startCapture HATA", e)
            onCapturerError(e.message ?: "Unknown capture error")
        }
    }

    fun changeQuality(newWidth: Int, newHeight: Int, newFps: Int) {
        captureWidth = newWidth
        captureHeight = newHeight
        captureFps = newFps
        Log.i(TAG, "Kalite degistirildi: ${newWidth}x${newHeight} @ ${newFps}fps")
        try {
            capturer?.changeCaptureFormat(newWidth, newHeight, newFps)
        } catch (e: Exception) {
            Log.e(TAG, "Kalite degisikligi hatasi", e)
        }
    }

    fun toggleFreeze(isFrozen: Boolean): Boolean {
        try {
            localTrack?.setEnabled(!isFrozen)
            Log.i(TAG, if (isFrozen) "Yayin donduruldu" else "Yayin devam ediyor")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Dondurme hatasi", e)
            return false
        }
    }

    fun stopCapture() {
        if (!isCapturing) return
        isCapturing = false

        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        capturer = null

        try { videoSource?.dispose() } catch (_: Exception) {}
        videoSource = null

        try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
        surfaceTextureHelper = null

        localTrack = null

        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null

        Log.i(TAG, "stopCapture tamamlandi")
    }

    fun getLocalTrack(): VideoTrack? = localTrack

    fun isCapturing(): Boolean = isCapturing
}
