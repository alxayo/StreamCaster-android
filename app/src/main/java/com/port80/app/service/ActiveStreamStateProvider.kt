package com.port80.app.service

import com.port80.app.data.model.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny read-only signal used by screens that need to know whether the service
 * currently owns the camera. The QR scanner reads this before opening CameraX
 * so it does not fight RootEncoder for camera access.
 */
interface ActiveStreamStateProvider {
    /** True whenever StreamingService is in a state that may own camera resources. */
    val isStreamActive: StateFlow<Boolean>
}

/**
 * Mutable implementation owned by dependency injection and updated only by
 * StreamingService. Keeping mutation internal preserves the service as the
 * real source of truth while giving settings UI a simple gate to observe.
 */
@Singleton
class MutableActiveStreamStateProvider @Inject constructor() : ActiveStreamStateProvider {
    private val _isStreamActive = MutableStateFlow(false)
    override val isStreamActive: StateFlow<Boolean> = _isStreamActive.asStateFlow()

    /**
     * Convert the detailed service state into the single yes/no answer the QR
     * scanner needs. Previewing counts as active because RootEncoder owns the
     * camera even though bytes are not being sent to the server yet.
     */
    fun publish(state: StreamState) {
        _isStreamActive.value = when (state) {
            StreamState.Idle,
            is StreamState.Stopped -> false
            is StreamState.Previewing,
            StreamState.Connecting,
            is StreamState.Live,
            is StreamState.Reconnecting,
            StreamState.Stopping -> true
        }
    }
}