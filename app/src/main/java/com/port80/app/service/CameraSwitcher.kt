package com.port80.app.service

import com.port80.app.util.RedactingLogger

/**
 * Handles switching between front and back camera during streaming.
 *
 * Camera switching is done through RtmpCamera2's switchCamera() method,
 * which handles the Camera2 session teardown and recreation internally.
 *
 * Rules:
 * - Only works when video is actively streaming
 * - No-op if the device only has one camera
 * - Idempotent: multiple rapid taps don't cause issues
 */
class CameraSwitcher(
    private val encoderBridge: EncoderBridge
) {
    companion object {
        private const val TAG = "CameraSwitcher"
        private const val MIN_SWITCH_INTERVAL_MS = 1000L // Debounce rapid switches
    }

    private var lastSwitchTime = 0L
    private var isFrontCamera = false

    /**
     * Switch to the other camera.
     * Returns true if the switch was initiated, false if debounced.
     */
    fun switchCamera(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < MIN_SWITCH_INTERVAL_MS) {
            RedactingLogger.d(TAG, "Camera switch debounced")
            return false
        }

        lastSwitchTime = now
        isFrontCamera = !isFrontCamera
        encoderBridge.switchCamera()
        RedactingLogger.d(TAG, "Switched to ${if (isFrontCamera) "front" else "back"} camera")
        return true
    }

    /** Get whether the front camera is currently active. */
    fun isFrontCameraActive(): Boolean = isFrontCamera
}
