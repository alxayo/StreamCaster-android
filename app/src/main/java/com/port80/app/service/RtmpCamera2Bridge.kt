package com.port80.app.service

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec as RootEncoderVideoCodec
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import com.port80.app.data.model.VideoCodec
import com.port80.app.util.RedactingLogger

/**
 * Real implementation of [EncoderBridge] backed by RootEncoder's [RtmpCamera2].
 *
 * Handles RTMP and RTMPS connections. For Enhanced RTMP, sets the codec to
 * H.265 or AV1 via RootEncoder's [RootEncoderVideoCodec] enum before preparing
 * the video encoder.
 *
 * Lifecycle (matches StreamingService flow):
 * 1. [startPreview] — opens the camera and begins rendering frames
 * 2. [connect]      — configures encoders with codec/resolution/bitrate and starts streaming
 * 3. [disconnect]   — stops the stream (camera stays open)
 * 4. [stopPreview]  — stops camera capture
 * 5. [release]      — frees all resources
 */
class RtmpCamera2Bridge(
    private val connectChecker: ConnectChecker
) : EncoderBridge {

    companion object {
        private const val TAG = "RtmpCamera2Bridge"
    }

    private var rtmpCamera2: RtmpCamera2? = null

    // ── Preview ──────────────────────────────────────────────────────────

    override fun startPreview(openGlView: OpenGlView) {
        RedactingLogger.d(TAG, "startPreview()")
        try {
            rtmpCamera2 = RtmpCamera2(openGlView, connectChecker)
            RedactingLogger.d(TAG, "RtmpCamera2 instance created with OpenGlView")
            rtmpCamera2?.startPreview()
            RedactingLogger.d(TAG, "startPreview() completed (isOnPreview=${rtmpCamera2?.isOnPreview == true})")
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "startPreview() failed", e)
            connectChecker.onConnectionFailed(
                "PREVIEW_START_FAILED: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    override fun stopPreview() {
        RedactingLogger.d(TAG, "stopPreview()")
        rtmpCamera2?.stopPreview()
    }

    override fun replaceViewWithBackground(context: Context) {
        RedactingLogger.d(TAG, "replaceViewWithBackground() — switching to headless mode")
        try {
            rtmpCamera2?.replaceView(context)
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "replaceViewWithBackground() failed, falling back to stopPreview", e)
            rtmpCamera2?.stopPreview()
        }
    }

    override fun replaceView(openGlView: OpenGlView) {
        RedactingLogger.d(TAG, "replaceView() — hot-swapping to new surface")
        try {
            rtmpCamera2?.replaceView(openGlView)
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "replaceView() failed", e)
        }
    }

    // ── Streaming ────────────────────────────────────────────────────────

    override fun connect(params: ConnectionParams, config: EncoderConfig) {
        require(params is ConnectionParams.Rtmp) { "RtmpCamera2Bridge requires ConnectionParams.Rtmp" }

        val camera = rtmpCamera2
        if (camera == null) {
            RedactingLogger.e(TAG, "connect() called before startPreview() — ignoring")
            connectChecker.onConnectionFailed("CAMERA_NOT_INITIALIZED: connect called before preview")
            return
        }

        RedactingLogger.d(
            TAG,
            "connect() begin (codec=${config.videoCodec}, isOnPreview=${camera.isOnPreview})"
        )

        // Set the video codec for Enhanced RTMP (H.265/AV1)
        camera.setVideoCodec(config.videoCodec.toRootEncoder())

        val videoReady: Boolean
        val audioReady: Boolean
        try {
            videoReady = camera.prepareVideo(
                config.width,
                config.height,
                config.fps,
                config.videoBitrateKbps * 1000,
                config.keyframeIntervalSec
            )
            audioReady = camera.prepareAudio(
                config.audioBitrateKbps * 1000,
                config.audioSampleRate,
                config.stereo
            )
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Encoder prepare threw exception", e)
            connectChecker.onConnectionFailed(
                "ENCODER_PREP_EXCEPTION: ${e.javaClass.simpleName}: ${e.message}"
            )
            return
        }

        if (!videoReady || !audioReady) {
            RedactingLogger.e(TAG, "Encoder preparation failed — video=$videoReady, audio=$audioReady")
            connectChecker.onConnectionFailed("ENCODER_PREP_FAILED(video=$videoReady,audio=$audioReady)")
            return
        }

        val fullUrl = if (params.streamKey.isNotBlank()) "${params.baseUrl}/${params.streamKey}" else params.baseUrl
        RedactingLogger.i(TAG, "Connecting to $fullUrl")
        try {
            camera.startStream(fullUrl)
            RedactingLogger.d(TAG, "startStream() invoked on encoder")
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "startStream() failed", e)
            connectChecker.onConnectionFailed(
                "STREAM_START_FAILED: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    override fun disconnect() {
        val camera = rtmpCamera2
        RedactingLogger.d(
            TAG,
            "disconnect() (hasCamera=${camera != null}, isStreaming=${camera?.isStreaming == true})"
        )
        camera?.stopStream()
    }

    // ── Camera controls ──────────────────────────────────────────────────

    override fun switchCamera() {
        RedactingLogger.d(TAG, "switchCamera()")
        try {
            rtmpCamera2?.switchCamera()
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Failed to switch camera", e)
        }
    }

    // ── Encoder tuning ───────────────────────────────────────────────────

    override fun setVideoBitrateOnFly(bitrateKbps: Int) {
        val bitrateBps = bitrateKbps * 1000
        RedactingLogger.d(TAG, "setVideoBitrateOnFly(${bitrateKbps} kbps → $bitrateBps bps)")
        rtmpCamera2?.setVideoBitrateOnFly(bitrateBps)
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun release() {
        RedactingLogger.d(TAG, "release()")
        rtmpCamera2?.let { camera ->
            RedactingLogger.d(
                TAG,
                "release() begin (isStreaming=${camera.isStreaming}, isOnPreview=${camera.isOnPreview})"
            )
            if (camera.isStreaming) {
                camera.stopStream()
            }
            if (camera.isOnPreview) {
                camera.stopPreview()
            }
        }
        rtmpCamera2 = null
        RedactingLogger.d(TAG, "release() completed")
    }

    override fun isStreaming(): Boolean {
        return rtmpCamera2?.isStreaming == true
    }
}

/** Map our VideoCodec enum to RootEncoder's VideoCodec enum. */
private fun VideoCodec.toRootEncoder(): RootEncoderVideoCodec = when (this) {
    VideoCodec.H264 -> RootEncoderVideoCodec.H264
    VideoCodec.H265 -> RootEncoderVideoCodec.H265
    VideoCodec.AV1 -> RootEncoderVideoCodec.AV1
}
