package com.port80.app.service

import android.content.Context
import com.port80.app.data.model.StabilizationMode
import com.port80.app.util.RedactingLogger
import com.pedro.library.view.OpenGlView

/**
 * A fake encoder bridge used during development and testing.
 * It doesn't actually stream — it just logs method calls.
 */
class StubEncoderBridge : EncoderBridge {
    private companion object {
        private const val TAG = "StubEncoderBridge"
    }
    private var streaming = false

    /** Last camera ID passed to [switchCamera] or [startPreview], for test assertions. */
    var lastCameraId: String? = null
        private set

    /** Last stabilization mode set via [setStabilizationMode], for test assertions. */
    var lastStabilizationMode: StabilizationMode? = null
        private set

    override fun startPreview(openGlView: OpenGlView) {
        RedactingLogger.d(TAG, "startPreview() called")
    }

    override fun startPreview(openGlView: OpenGlView, cameraId: String) {
        RedactingLogger.d(TAG, "startPreview(cameraId=$cameraId) called")
        lastCameraId = cameraId
    }

    override fun startPreview(openGlView: OpenGlView, cameraId: String, width: Int, height: Int) {
        RedactingLogger.d(TAG, "startPreview(cameraId=$cameraId, ${width}x${height}) called")
        lastCameraId = cameraId
    }

    override fun stopPreview() {
        RedactingLogger.d(TAG, "stopPreview() called")
    }

    override fun replaceViewWithBackground(context: Context) {
        RedactingLogger.d(TAG, "replaceViewWithBackground() called")
    }

    override fun replaceView(openGlView: OpenGlView) {
        RedactingLogger.d(TAG, "replaceView() called")
    }

    override fun connect(params: ConnectionParams, config: EncoderConfig) {
        RedactingLogger.d(TAG, "connect() called (params redacted)")
        streaming = true
    }

    override fun disconnect() {
        RedactingLogger.d(TAG, "disconnect() called")
        streaming = false
    }

    override fun switchCamera() {
        RedactingLogger.d(TAG, "switchCamera() called")
    }

    override fun switchCamera(cameraId: String) {
        RedactingLogger.d(TAG, "switchCamera(cameraId=$cameraId) called")
        lastCameraId = cameraId
    }

    override fun setVideoBitrateOnFly(bitrateKbps: Int) {
        RedactingLogger.d(TAG, "setVideoBitrateOnFly($bitrateKbps kbps)")
    }

    override fun setStabilizationMode(mode: StabilizationMode) {
        RedactingLogger.d(TAG, "setStabilizationMode($mode)")
        lastStabilizationMode = mode
    }

    override fun release() {
        RedactingLogger.d(TAG, "release() called")
        streaming = false
    }

    override fun isStreaming(): Boolean = streaming
}
