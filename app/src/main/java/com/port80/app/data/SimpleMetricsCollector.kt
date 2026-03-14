package com.port80.app.data

import com.port80.app.util.RedactingLogger
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple in-memory metrics collector for stream diagnostics.
 * Uses atomic counters for thread safety.
 * These metrics are for internal use only — not sent to any analytics service.
 */
@Singleton
class SimpleMetricsCollector @Inject constructor() : MetricsCollector {

    companion object {
        private const val TAG = "Metrics"
    }

    private val streamCount = AtomicInteger(0)
    private val totalDurationMs = AtomicLong(0)
    private val droppedFrames = AtomicLong(0)
    private val reconnectAttempts = AtomicInteger(0)
    private val reconnectSuccesses = AtomicInteger(0)

    override fun onStreamStarted() {
        streamCount.incrementAndGet()
        RedactingLogger.d(TAG, "Stream #${streamCount.get()} started")
    }

    override fun onStreamEnded(durationMs: Long) {
        totalDurationMs.addAndGet(durationMs)
        RedactingLogger.d(TAG, "Stream ended. Duration: ${durationMs / 1000}s. Total streams: ${streamCount.get()}")
    }

    override fun onFrameDropped() {
        droppedFrames.incrementAndGet()
    }

    override fun onReconnectAttempt(attemptNumber: Int) {
        reconnectAttempts.incrementAndGet()
    }

    override fun onReconnectSuccess() {
        reconnectSuccesses.incrementAndGet()
        RedactingLogger.d(TAG, "Reconnect succeeded (${reconnectSuccesses.get()}/${reconnectAttempts.get()} total)")
    }

    override fun reset() {
        droppedFrames.set(0)
        reconnectAttempts.set(0)
        reconnectSuccesses.set(0)
    }
}
