package com.port80.app.service

import android.content.Context
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec as RootEncoderVideoCodec
import com.pedro.library.srt.SrtCamera2
import com.pedro.library.view.OpenGlView
import com.port80.app.data.model.SrtMode
import com.port80.app.data.model.StabilizationMode
import com.port80.app.data.model.VideoCodec
import com.port80.app.util.RedactingLogger

/**
 * [EncoderBridge] implementation backed by RootEncoder's [SrtCamera2].
 *
 * Handles SRT (Secure Reliable Transport) connections with support for:
 * - Caller, Listener, and Rendezvous modes
 * - Encryption via passphrase (AES)
 * - Configurable latency and stream ID
 * - H.264 and H.265 codecs (AV1 is NOT supported over SRT by RootEncoder)
 *
 * The SRT URL is built internally from [ConnectionParams.Srt] fields,
 * keeping protocol details encapsulated in the bridge.
 */
class SrtCamera2Bridge(
    private val connectChecker: ConnectChecker
) : EncoderBridge {

    companion object {
        private const val TAG = "SrtCamera2Bridge"
        /** Minimal SRT Access Control stream_id: publish mode, no resource filter. */
        private const val DEFAULT_SRT_STREAM_ID = "#!::m=publish"
    }

    private var srtCamera2: SrtCamera2? = null

    // ── Preview ──────────────────────────────────────────────────────────

    override fun startPreview(openGlView: OpenGlView) {
        doStartPreview(openGlView, cameraId = null)
    }

    override fun startPreview(openGlView: OpenGlView, cameraId: String) {
        doStartPreview(openGlView, cameraId = cameraId)
    }

    override fun startPreview(openGlView: OpenGlView, cameraId: String, width: Int, height: Int) {
        // width/height are surface dimensions used for orientation tracking only.
        // RootEncoder picks camera-native resolution internally.
        doStartPreview(openGlView, cameraId = cameraId)
    }

    private fun doStartPreview(openGlView: OpenGlView, cameraId: String?) {
        RedactingLogger.d(TAG, "startPreview(cameraId=${cameraId ?: "default"})")
        try {
            srtCamera2 = SrtCamera2(openGlView, connectChecker)
            RedactingLogger.d(TAG, "SrtCamera2 instance created with OpenGlView")
            if (cameraId != null) {
                srtCamera2?.startPreview(cameraId)
            } else {
                srtCamera2?.startPreview()
            }
            RedactingLogger.d(TAG, "startPreview() completed (isOnPreview=${srtCamera2?.isOnPreview == true})")
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "startPreview() failed", e)
            connectChecker.onConnectionFailed(
                "PREVIEW_START_FAILED: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    override fun stopPreview() {
        RedactingLogger.d(TAG, "stopPreview()")
        srtCamera2?.stopPreview()
    }

    override fun replaceViewWithBackground(context: Context) {
        RedactingLogger.d(TAG, "replaceViewWithBackground() — switching to headless mode")
        try {
            srtCamera2?.replaceView(context)
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "replaceViewWithBackground() failed, falling back to stopPreview", e)
            srtCamera2?.stopPreview()
        }
    }

    override fun replaceView(openGlView: OpenGlView) {
        RedactingLogger.d(TAG, "replaceView() — hot-swapping to new surface")
        try {
            srtCamera2?.replaceView(openGlView)
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "replaceView() failed", e)
        }
    }

    // ── Streaming ────────────────────────────────────────────────────────

    override fun connect(params: ConnectionParams, config: EncoderConfig) {
        require(params is ConnectionParams.Srt) { "SrtCamera2Bridge requires ConnectionParams.Srt" }
        require(config.videoCodec != VideoCodec.AV1) { "AV1 is not supported over SRT" }

        val camera = srtCamera2
        if (camera == null) {
            RedactingLogger.e(TAG, "connect() called before startPreview() — ignoring")
            connectChecker.onConnectionFailed("CAMERA_NOT_INITIALIZED: connect called before preview")
            return
        }

        RedactingLogger.d(
            TAG,
            "connect() begin (codec=${config.videoCodec}, mode=${params.mode}, isOnPreview=${camera.isOnPreview})"
        )

        // Set codec (H.264 or H.265 for SRT)
        camera.setVideoCodec(config.videoCodec.toRootEncoder())

        val videoReady: Boolean
        val audioReady: Boolean
        try {
            videoReady = camera.prepareVideo(
                config.orientedWidth,
                config.orientedHeight,
                config.fps,
                config.videoBitrateKbps * 1000,
                config.keyframeIntervalSec,
                config.rotation
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

        val srtUrl = buildSrtUrl(params)
        // Log without secrets — CredentialSanitizer handles passphrase redaction
        RedactingLogger.i(TAG, "Connecting to $srtUrl")
        try {
            camera.startStream(srtUrl)
            RedactingLogger.d(TAG, "startStream() invoked on SRT encoder")
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "startStream() failed", e)
            connectChecker.onConnectionFailed(
                "STREAM_START_FAILED: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    override fun disconnect() {
        val camera = srtCamera2
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
            srtCamera2?.switchCamera()
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Failed to switch camera", e)
        }
    }

    override fun switchCamera(cameraId: String) {
        RedactingLogger.d(TAG, "switchCamera(cameraId=$cameraId)")
        try {
            srtCamera2?.switchCamera(cameraId)
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Failed to switch to camera $cameraId", e)
        }
    }

    // ── Encoder tuning ───────────────────────────────────────────────────

    override fun setVideoBitrateOnFly(bitrateKbps: Int) {
        val bitrateBps = bitrateKbps * 1000
        RedactingLogger.d(TAG, "setVideoBitrateOnFly(${bitrateKbps} kbps → $bitrateBps bps)")
        srtCamera2?.setVideoBitrateOnFly(bitrateBps)
    }

    override fun setStabilizationMode(mode: StabilizationMode) {
        RedactingLogger.d(TAG, "setStabilizationMode($mode)")
        val camera = srtCamera2 ?: return
        try {
            when (mode) {
                StabilizationMode.OFF -> {
                    camera.disableVideoStabilization()
                    camera.disableOpticalVideoStabilization()
                }
                StabilizationMode.EIS -> {
                    camera.disableOpticalVideoStabilization()
                    camera.enableVideoStabilization()
                }
                StabilizationMode.OIS -> {
                    camera.disableVideoStabilization()
                    camera.enableOpticalVideoStabilization()
                }
            }
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Failed to set stabilization mode $mode", e)
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun release() {
        RedactingLogger.d(TAG, "release()")
        srtCamera2?.let { camera ->
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
        srtCamera2 = null
        RedactingLogger.d(TAG, "release() completed")
    }

    override fun isStreaming(): Boolean {
        return srtCamera2?.isStreaming == true
    }

    override fun setFpsListener(callback: (Int) -> Unit) {
        srtCamera2?.setFpsListener { fps -> callback(fps) }
    }

    // ── SRT URL builder ──────────────────────────────────────────────────

    /**
     * Build the SRT URL from typed parameters.
     * Format: srt://host:port?mode=caller&latency=120&passphrase=X&streamid=Y
     *
     * Always includes `streamid=` to prevent RootEncoder from falling back to
     * using the full query string as the SRT handshake stream_id. If the user
     * hasn't specified a stream ID, defaults to SRT Access Control publish mode.
     */
    private fun buildSrtUrl(params: ConnectionParams.Srt): String {
        val sb = StringBuilder("srt://${params.host}:${params.port}")
        val queryParams = mutableListOf<String>()

        queryParams.add("mode=${params.mode.toUrlParam()}")
        queryParams.add("latency=${params.latencyMs}")

        if (!params.passphrase.isNullOrBlank()) {
            queryParams.add("passphrase=${params.passphrase}")
        }

        // Always include streamid so RootEncoder sends it in the SRT handshake.
        // Without this, RootEncoder falls back to getFullPath() which returns the
        // entire query string (e.g. "mode=caller&latency=120") as the stream_id,
        // causing servers to reject the connection as "not a publish request".
        val streamId = if (!params.streamId.isNullOrBlank()) {
            params.streamId
        } else {
            DEFAULT_SRT_STREAM_ID
        }
        queryParams.add("streamid=$streamId")

        if (queryParams.isNotEmpty()) {
            sb.append("?")
            sb.append(queryParams.joinToString("&"))
        }
        return sb.toString()
    }
}

/** Map our VideoCodec enum to RootEncoder's VideoCodec enum. */
private fun VideoCodec.toRootEncoder(): RootEncoderVideoCodec = when (this) {
    VideoCodec.H264 -> RootEncoderVideoCodec.H264
    VideoCodec.H265 -> RootEncoderVideoCodec.H265
    VideoCodec.AV1 -> RootEncoderVideoCodec.AV1
}
