package com.port80.app.service

import com.pedro.library.view.OpenGlView
import com.port80.app.data.model.StabilizationMode
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
     * Last sanitized connection/start failure detail intended for user-facing diagnostics.
     * Null when no active failure detail is available.
     */
    val lastFailureDetail: StateFlow<String?>

    /**
     * Start streaming using the given endpoint profile.
     * The service reads credentials and config internally — no secrets in this call.
     * No-op if already streaming or connecting.
     */
    fun startStream(profileId: String)

    /**
     * Start camera preview without streaming.
     * Creates a preview bridge and opens the camera so the user can frame the shot.
     * No-op if not Idle.
     */
    fun startPreviewOnly()

    /**
     * Stop the preview-only session.
     * Releases the camera and bridge. No-op if not in Previewing state.
     */
    fun stopPreviewOnly()

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
     * Switch to a specific camera by Camera2 camera ID.
     * No-op if video is not active or the camera ID is invalid.
     */
    fun switchCamera(cameraId: String)

    /**
     * Attach an OpenGlView surface for camera output display.
     * Call this when the view's surface is created. Safe to call multiple times.
     */
    fun attachPreviewSurface(openGlView: OpenGlView)

    /**
     * Detach the preview surface. Call this when SurfaceView is destroyed.
     * Streaming continues without preview — only the display stops.
     */
    fun detachPreviewSurface()

    /**
     * Notify the service that the preview surface dimensions changed (e.g., device rotation).
     * In Previewing state, restarts the preview with new dimensions after a debounce.
     * No-op during active streaming (orientation is locked).
     */
    fun onPreviewDimensionsChanged(width: Int, height: Int)

    /**
     * Set image stabilization mode. Applied to the active camera session.
     * Safe to call during preview or streaming.
     */
    fun setStabilizationMode(mode: StabilizationMode)
}
