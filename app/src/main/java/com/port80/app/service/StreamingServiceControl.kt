package com.port80.app.service

import android.view.SurfaceHolder
import com.port80.app.data.model.StreamState
import com.port80.app.data.model.StreamStats
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract exposed by StreamingService to bound clients (ViewModels).
 * The ViewModel calls these methods to control the stream, but never
 * modifies stream state directly — that's the service's job.
 *
 * All methods are idempotent: calling stopStream() when already stopped is a no-op.
 */
interface StreamingServiceControl {
    /** Observe the current stream state (Idle, Connecting, Live, etc.). */
    val streamState: StateFlow<StreamState>

    /** Observe real-time stream statistics (bitrate, fps, duration, etc.). */
    val streamStats: StateFlow<StreamStats>

    /**
     * Start streaming using the given endpoint profile.
     * The service reads credentials and config internally — no secrets in this call.
     * No-op if already streaming or connecting.
     */
    fun startStream(profileId: String)

    /**
     * Stop the active stream and cancel any reconnect attempts.
     * No-op if already stopped or idle.
     */
    fun stopStream()

    /** Toggle audio mute on/off. No-op if no audio track is active. */
    fun toggleMute()

    /** Switch between front and back camera. No-op if video is not active. */
    fun switchCamera()

    /**
     * Attach a preview surface for camera output display.
     * Call this when SurfaceView is created. Safe to call multiple times.
     */
    fun attachPreviewSurface(holder: SurfaceHolder)

    /**
     * Detach the preview surface. Call this when SurfaceView is destroyed.
     * Streaming continues without preview — only the display stops.
     */
    fun detachPreviewSurface()
}
