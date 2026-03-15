package com.port80.app.service

import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import com.port80.app.util.RedactingLogger

/**
 * Real implementation of [EncoderBridge] backed by RootEncoder's [RtmpCamera2].
 *
 * RtmpCamera2 is the main class from RootEncoder that handles:
 * - Camera2 API capture (video frames from the device camera)
 * - H.264 hardware encoding (compresses video for streaming)
 * - AAC audio encoding (compresses microphone audio)
 * - RTMP protocol (sends encoded data to the streaming server)
 *
 * This bridge translates our app's [EncoderBridge] interface into RtmpCamera2
 * calls, keeping the rest of the service layer decoupled from RootEncoder types.
 *
 * Lifecycle (matches StreamingService flow):
 * 1. [startPreview] — opens the camera and begins rendering frames
 * 2. [connect]      — configures encoders and starts RTMP streaming
 * 3. [disconnect]   — stops the RTMP stream (camera stays open)
 * 4. [stopPreview]  — stops camera capture
 * 5. [release]      — frees all resources
 *
 * @param connectChecker Callback interface for RTMP connection events. StreamingService
 *                       implements this to drive its state machine.
 */
class RtmpCamera2Bridge(
    private val connectChecker: ConnectChecker
) : EncoderBridge {

    companion object {
        private const val TAG = "RtmpCamera2Bridge"
    }

    /**
     * The main RootEncoder object. Created (or recreated) in [startPreview] using
     * the OpenGlView constructor so the camera renders to the display surface.
     */
    private var rtmpCamera2: RtmpCamera2? = null

    // ── Preview ──────────────────────────────────────────────────────────

    override fun startPreview(openGlView: OpenGlView) {
        RedactingLogger.d(TAG, "startPreview()")
        // Use the OpenGlView constructor so RtmpCamera2 renders frames to the screen.
        // A new instance is created each time (surface may have been recreated after
        // a configuration change or process-death recovery).
        rtmpCamera2 = RtmpCamera2(openGlView, connectChecker)
        RedactingLogger.d(TAG, "RtmpCamera2 instance created with OpenGlView")
        // Start camera capture — frames rendered to the OpenGlView provided above.
        rtmpCamera2?.startPreview()
    }

    override fun stopPreview() {
        RedactingLogger.d(TAG, "stopPreview()")
        rtmpCamera2?.stopPreview()
    }

    // ── Streaming ────────────────────────────────────────────────────────

    override fun connect(url: String, streamKey: String) {
        val camera = rtmpCamera2
        if (camera == null) {
            RedactingLogger.e(TAG, "connect() called before startPreview() — ignoring")
            return
        }

        // prepareVideo() and prepareAudio() configure the hardware encoders.
        // They must succeed before we can send data to the RTMP server.
        // The no-arg overloads use sensible defaults (640×480 @ 30 fps, 128 kbps AAC).
        val videoReady = camera.prepareVideo()
        val audioReady = camera.prepareAudio()

        if (!videoReady || !audioReady) {
            RedactingLogger.e(
                TAG,
                "Encoder preparation failed — video=$videoReady, audio=$audioReady"
            )
            // Notify the listener so StreamingService can transition to an error state.
            connectChecker.onConnectionFailed(
                "Encoder preparation failed (video=$videoReady, audio=$audioReady)"
            )
            return
        }

        // Build the full RTMP URL: rtmp://host/app/streamKey
        val fullUrl = if (streamKey.isNotBlank()) "$url/$streamKey" else url
        // RedactingLogger automatically strips the stream key from the URL
        RedactingLogger.i(TAG, "Connecting to $fullUrl")
        camera.startStream(fullUrl)
    }

    override fun disconnect() {
        RedactingLogger.d(TAG, "disconnect()")
        rtmpCamera2?.stopStream()
    }

    // ── Camera controls ──────────────────────────────────────────────────

    override fun switchCamera() {
        RedactingLogger.d(TAG, "switchCamera()")
        try {
            rtmpCamera2?.switchCamera()
        } catch (e: Exception) {
            // switchCamera() throws CameraOpenException if the target camera
            // can't be opened (e.g., already in use by another app).
            RedactingLogger.e(TAG, "Failed to switch camera", e)
        }
    }

    // ── Encoder tuning ───────────────────────────────────────────────────

    override fun setVideoBitrateOnFly(bitrateKbps: Int) {
        // RootEncoder expects bits-per-second; our interface uses kilobits-per-second.
        val bitrateBps = bitrateKbps * 1000
        RedactingLogger.d(TAG, "setVideoBitrateOnFly(${bitrateKbps} kbps → $bitrateBps bps)")
        rtmpCamera2?.setVideoBitrateOnFly(bitrateBps)
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun release() {
        RedactingLogger.d(TAG, "release()")
        // Tear down in reverse order: stop streaming first, then camera, then null out.
        rtmpCamera2?.let { camera ->
            if (camera.isStreaming) {
                camera.stopStream()
            }
            if (camera.isOnPreview) {
                camera.stopPreview()
            }
        }
        rtmpCamera2 = null
    }

    override fun isStreaming(): Boolean {
        return rtmpCamera2?.isStreaming == true
    }
}
