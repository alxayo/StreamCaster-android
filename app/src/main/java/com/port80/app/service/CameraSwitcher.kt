package com.port80.app.service

import com.port80.app.data.model.CameraFacing
import com.port80.app.data.model.CameraInfo
import com.port80.app.util.RedactingLogger

/**
 * Handles switching between cameras during streaming.
 *
 * Supports both the simple front/back toggle (via [switchCamera]) and
 * explicit camera selection by ID (via [switchToCamera]).
 *
 * On devices with multiple rear cameras, [switchCamera] cycles through
 * rear cameras before switching to front.
 *
 * Rules:
 * - Only works when video is actively streaming
 * - No-op if the device only has one camera
 * - Idempotent: multiple rapid taps are debounced
 */
class CameraSwitcher(
    private val encoderBridge: EncoderBridge,
    private val availableCameras: List<CameraInfo> = emptyList()
) {
    companion object {
        private const val TAG = "CameraSwitcher"
        private const val MIN_SWITCH_INTERVAL_MS = 1000L
    }

    private var lastSwitchTime = 0L

    /** The Camera2 ID of the currently active camera, or null if unknown. */
    var currentCameraId: String? = null
        private set

    /**
     * Cycle to the next camera in the available list.
     * Order: all rear cameras (by focal length) → front → back to first rear.
     * Returns true if the switch was initiated, false if debounced or unavailable.
     */
    fun switchCamera(): Boolean {
        if (availableCameras.size <= 1) {
            // Single camera or no list — fall back to RootEncoder's default toggle
            return switchWithDebounce { encoderBridge.switchCamera() }
        }

        val currentIdx = availableCameras.indexOfFirst { it.id == currentCameraId }
        val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % availableCameras.size
        val nextCamera = availableCameras[nextIdx]

        return switchToCamera(nextCamera.id)
    }

    /**
     * Switch to a specific camera by Camera2 camera ID.
     * Returns true if the switch was initiated, false if debounced or already active.
     */
    fun switchToCamera(cameraId: String): Boolean {
        if (cameraId == currentCameraId) {
            RedactingLogger.d(TAG, "Already on camera $cameraId — no-op")
            return false
        }

        return switchWithDebounce {
            encoderBridge.switchCamera(cameraId)
            currentCameraId = cameraId
            RedactingLogger.d(TAG, "Switched to camera $cameraId")
        }
    }

    /** Whether the currently active camera is front-facing. */
    fun isFrontCameraActive(): Boolean {
        return availableCameras.find { it.id == currentCameraId }?.facing == CameraFacing.FRONT
    }

    /** Set the initial camera ID (e.g., after startPreview with a specific camera). */
    fun setInitialCamera(cameraId: String) {
        currentCameraId = cameraId
    }

    private fun switchWithDebounce(action: () -> Unit): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSwitchTime < MIN_SWITCH_INTERVAL_MS) {
            RedactingLogger.d(TAG, "Camera switch debounced")
            return false
        }
        lastSwitchTime = now
        action()
        return true
    }
}
