package com.port80.app.service

import com.port80.app.util.RedactingLogger

/**
 * Handles mid-stream transitions between media modes:
 * - Video+Audio → Audio-only (when camera is revoked or user disables video)
 * - Audio-only → Video+Audio (when camera is re-acquired)
 *
 * Rules from spec MC-01:
 * - Video→Audio downgrade is permitted mid-session
 * - Audio→Video upgrade requires camera reacquire and encoder re-init
 */
class MediaModeTransition(
    private val encoderBridge: EncoderBridge
) {
    companion object {
        private const val TAG = "MediaModeTransition"
    }

    enum class MediaMode {
        VIDEO_AND_AUDIO,
        AUDIO_ONLY,
        VIDEO_ONLY
    }

    var currentMode = MediaMode.VIDEO_AND_AUDIO
        private set

    /**
     * Transition to audio-only mode (stop sending video).
     * Used when camera is revoked in the background.
     */
    fun transitionToAudioOnly() {
        if (currentMode == MediaMode.AUDIO_ONLY) return

        encoderBridge.stopPreview()
        currentMode = MediaMode.AUDIO_ONLY
        RedactingLogger.i(TAG, "Transitioned to audio-only mode")
    }

    /**
     * Transition back to video+audio mode.
     * Requires camera to be available again.
     * @return true if transition succeeded
     */
    fun transitionToVideoAndAudio(): Boolean {
        if (currentMode == MediaMode.VIDEO_AND_AUDIO) return true

        return try {
            currentMode = MediaMode.VIDEO_AND_AUDIO
            RedactingLogger.i(TAG, "Transitioned to video+audio mode")
            true
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Failed to transition to video+audio", e)
            false
        }
    }
}
