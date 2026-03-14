package com.port80.app.service

import android.util.Log
import android.view.SurfaceHolder

/**
 * A fake encoder bridge used during development and testing.
 * It doesn't actually stream — it just logs method calls.
 * This will be replaced by RtmpCamera2EncoderBridge in T-007b.
 */
class StubEncoderBridge : EncoderBridge {
    private val tag = "StubEncoderBridge"
    private var streaming = false

    override fun startPreview(holder: SurfaceHolder) {
        Log.d(tag, "startPreview() called")
    }

    override fun stopPreview() {
        Log.d(tag, "stopPreview() called")
    }

    override fun connect(url: String, streamKey: String) {
        Log.d(tag, "connect() called (URL redacted)")
        streaming = true
    }

    override fun disconnect() {
        Log.d(tag, "disconnect() called")
        streaming = false
    }

    override fun switchCamera() {
        Log.d(tag, "switchCamera() called")
    }

    override fun setVideoBitrateOnFly(bitrateKbps: Int) {
        Log.d(tag, "setVideoBitrateOnFly($bitrateKbps kbps)")
    }

    override fun release() {
        Log.d(tag, "release() called")
        streaming = false
    }

    override fun isStreaming(): Boolean = streaming
}
