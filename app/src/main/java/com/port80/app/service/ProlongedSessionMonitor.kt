package com.port80.app.service

import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.*

/**
 * Monitors long-running streaming sessions for potential issues.
 *
 * After extended streaming, devices may experience:
 * - Memory pressure from accumulated buffers
 * - Thermal issues from sustained encoder workload
 * - Battery drain
 *
 * This monitor logs warnings at regular intervals so operators
 * can identify sessions that might need attention.
 */
class ProlongedSessionMonitor(
    private val scope: CoroutineScope,
    private val onWarning: (String) -> Unit
) {
    companion object {
        private const val TAG = "SessionMonitor"
        private const val CHECK_INTERVAL_MS = 300_000L // Check every 5 minutes
        private const val WARNING_THRESHOLD_MS = 3_600_000L // Warn after 1 hour
        private const val CRITICAL_THRESHOLD_MS = 14_400_000L // Critical after 4 hours
    }

    private var monitorJob: Job? = null
    private var startTimeMs: Long = 0

    /**
     * Start monitoring the session duration.
     */
    fun start() {
        startTimeMs = System.currentTimeMillis()
        monitorJob = scope.launch {
            while (isActive) {
                delay(CHECK_INTERVAL_MS)
                val durationMs = System.currentTimeMillis() - startTimeMs

                when {
                    durationMs >= CRITICAL_THRESHOLD_MS -> {
                        val hours = durationMs / 3_600_000
                        val msg = "Stream running for ${hours}h+ — check device temperature and battery"
                        RedactingLogger.w(TAG, msg)
                        onWarning(msg)
                    }
                    durationMs >= WARNING_THRESHOLD_MS -> {
                        val minutes = durationMs / 60_000
                        RedactingLogger.i(TAG, "Stream running for ${minutes} minutes")
                    }
                }
            }
        }
    }

    /**
     * Stop monitoring.
     */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        val durationMs = System.currentTimeMillis() - startTimeMs
        RedactingLogger.d(TAG, "Session ended after ${durationMs / 1000}s")
    }
}
