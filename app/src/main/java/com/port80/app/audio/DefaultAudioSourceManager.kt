package com.port80.app.audio

import com.port80.app.service.EncoderBridge
import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls microphone mute/unmute state.
 *
 * When muted, we tell RootEncoder to stop sending audio data.
 * The video stream continues unaffected.
 *
 * Used by:
 * - Mute toggle button in UI and notification (T-029)
 * - Audio focus handler on phone call (T-022)
 */
class DefaultAudioSourceManager : AudioSourceManager {

    companion object {
        private const val TAG = "AudioSourceManager"
    }

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    // Reference to encoder bridge, set by StreamingService
    var encoderBridge: EncoderBridge? = null

    override fun mute() {
        if (_isMuted.value) return // Already muted — no-op
        _isMuted.value = true
        // TODO: Call encoderBridge to mute audio track when T-007b provides the API
        RedactingLogger.d(TAG, "Microphone muted")
    }

    override fun unmute() {
        if (!_isMuted.value) return // Already unmuted — no-op
        _isMuted.value = false
        // TODO: Call encoderBridge to unmute audio track
        RedactingLogger.d(TAG, "Microphone unmuted")
    }
}
