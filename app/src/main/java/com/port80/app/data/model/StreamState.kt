package com.port80.app.data.model

/**
 * Authoritative stream state, owned exclusively by StreamingService.
 * The UI layer observes this as a read-only StateFlow — it never modifies it directly.
 *
 * The state machine follows this flow:
 *   Idle → Connecting → Live → Stopping → Stopped
 *                     ↘ Reconnecting ↗
 */
sealed class StreamState {
    /** No stream active. Ready to start. */
    data object Idle : StreamState()

    /** RTMP handshake in progress — waiting for server to accept connection. */
    data object Connecting : StreamState()

    /**
     * Actively streaming to the RTMP server.
     * @param cameraActive false when the OS has revoked camera access in the background
     * @param isMuted true when audio is muted (stored here for instant UI/notification updates)
     */
    data class Live(
        val cameraActive: Boolean = true,
        val isMuted: Boolean = false
    ) : StreamState()

    /**
     * Network connection was lost — trying to reconnect automatically.
     * @param attempt current retry attempt number (starts at 0)
     * @param nextRetryMs milliseconds until the next retry attempt
     */
    data class Reconnecting(val attempt: Int, val nextRetryMs: Long) : StreamState()

    /** Graceful shutdown is in progress (finalizing recording, closing connection). */
    data object Stopping : StreamState()

    /**
     * Stream has ended. Check [reason] to understand why.
     * @param reason why the stream stopped (user action, error, thermal, etc.)
     */
    data class Stopped(val reason: StopReason) : StreamState()
}
