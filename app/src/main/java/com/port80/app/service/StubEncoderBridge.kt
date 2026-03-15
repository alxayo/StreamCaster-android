package com.port80.app.service

import com.port80.app.util.RedactingLogger
import com.pedro.library.view.OpenGlView

/**
 * A fake encoder bridge used during development and testing.
 * It doesn't actually stream — it just logs method calls.
 * This will be replaced by RtmpCamera2EncoderBridge in T-007b.
 */
class StubEncoderBridge : EncoderBridge {
    private companion object {
        private const val TAG = "StubEncoderBridge"
    }
    private var streaming = false

    override fun startPreview(openGlView: OpenGlView) {
        RedactingLogger.d(TAG, "startPreview() called")
    }

    override fun stopPreview() {
        RedactingLogger.d(TAG, "stopPreview() called")
    }

    override fun connect(url: String, streamKey: String) {
        RedactingLogger.d(TAG, "connect() called (URL redacted)")
        streaming = true
    }

    override fun disconnect() {
        RedactingLogger.d(TAG, "disconnect() called")
        streaming = false
    }

    override fun switchCamera() {
        RedactingLogger.d(TAG, "switchCamera() called")
    }

    override fun setVideoBitrateOnFly(bitrateKbps: Int) {
        RedactingLogger.d(TAG, "setVideoBitrateOnFly($bitrateKbps kbps)")
    }

    override fun release() {
        RedactingLogger.d(TAG, "release() called")
        streaming = false
    }

    override fun isStreaming(): Boolean = streaming
}
