package com.port80.app.data

/**
 * Interface for collecting internal stream metrics and diagnostics.
 * These metrics are for internal monitoring, not exposed to analytics services.
 * Implementation provided in T-042.
 */
interface MetricsCollector {
    /** Record that a stream session started. */
    fun onStreamStarted()

    /** Record that a stream session ended, with total duration in milliseconds. */
    fun onStreamEnded(durationMs: Long)

    /** Record a dropped frame event. */
    fun onFrameDropped()

    /** Record a reconnection attempt. */
    fun onReconnectAttempt(attemptNumber: Int)

    /** Record a successful reconnection. */
    fun onReconnectSuccess()

    /** Reset all counters (called at the start of each new stream). */
    fun reset()
}
