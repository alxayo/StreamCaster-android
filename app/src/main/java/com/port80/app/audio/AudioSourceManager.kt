package com.port80.app.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for controlling the microphone mute state.
 * Used by both the mute toggle (T-029) and audio focus handler (T-022).
 * The StreamingService owns the implementation.
 */
interface AudioSourceManager {
    /** Whether the microphone is currently muted. Observed by UI and notification. */
    val isMuted: StateFlow<Boolean>

    /** Mute the microphone. Does nothing if already muted. */
    fun mute()

    /** Unmute the microphone. Does nothing if already unmuted. */
    fun unmute()
}
