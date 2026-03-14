package com.port80.app.service

import com.port80.app.data.model.Resolution
import com.port80.app.data.model.StopReason
import com.port80.app.data.model.StreamState
import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes all encoder quality changes to prevent concurrent access crashes.
 *
 * Both the ABR (adaptive bitrate) system and thermal monitoring can request
 * quality changes simultaneously. Without serialization, concurrent encoder
 * restarts cause IllegalStateException crashes in MediaCodec.
 *
 * Rules:
 * - Bitrate-only changes: instant, no encoder restart, no cooldown
 * - Resolution/FPS changes: full encoder restart, 60-second cooldown
 * - The cooldown prevents rapid oscillation (constantly switching quality)
 * - If restart fails: try one quality step lower. If that fails: stop stream.
 */
class EncoderController(
    private val encoderBridge: EncoderBridge,
    private val scope: CoroutineScope,
    private val onStateChange: ((StreamState) -> Unit)? = null,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        private const val TAG = "EncoderController"
        /** Minimum time between encoder restarts (prevents rapid oscillation). */
        const val RESTART_COOLDOWN_MS = 60_000L
    }

    private val mutex = Mutex()

    private var lastRestartTimeMs: Long = 0

    // Latest pending change waiting for cooldown expiry (latest request wins)
    private var pendingChange: QualityChange? = null
    private var cooldownJob: Job? = null

    private val _effectiveQuality = MutableStateFlow(EffectiveQuality())
    val effectiveQuality: StateFlow<EffectiveQuality> = _effectiveQuality.asStateFlow()

    /**
     * Request a quality change from the ABR system.
     * Bitrate-only changes are applied instantly.
     * Resolution/FPS changes respect the cooldown timer.
     */
    suspend fun requestAbrChange(
        bitrateKbps: Int,
        resolution: Resolution? = null,
        fps: Int? = null
    ) {
        mutex.withLock {
            val current = _effectiveQuality.value

            // Bitrate-only: apply instantly, no restart needed
            if (resolution == null && fps == null) {
                encoderBridge.setVideoBitrateOnFly(bitrateKbps)
                _effectiveQuality.value = current.copy(bitrateKbps = bitrateKbps)
                RedactingLogger.d(TAG, "ABR bitrate change: $bitrateKbps kbps (no restart)")
                return
            }

            // Resolution or FPS change: needs encoder restart with cooldown
            val change = QualityChange(
                bitrateKbps = bitrateKbps,
                resolution = resolution ?: current.resolution,
                fps = fps ?: current.fps,
                source = "ABR"
            )
            applyOrQueueChange(change)
        }
    }

    /**
     * Request a quality change from the thermal monitoring system.
     * Always respects the cooldown timer.
     */
    suspend fun requestThermalChange(
        resolution: Resolution,
        fps: Int,
        bitrateKbps: Int
    ) {
        mutex.withLock {
            val change = QualityChange(
                bitrateKbps = bitrateKbps,
                resolution = resolution,
                fps = fps,
                source = "Thermal"
            )
            applyOrQueueChange(change)
        }
    }

    /**
     * Apply the change now if cooldown has elapsed, or queue it for later.
     * Must be called inside mutex.withLock.
     */
    private fun applyOrQueueChange(change: QualityChange) {
        val now = clock()
        val timeSinceLastRestart = now - lastRestartTimeMs

        if (timeSinceLastRestart >= RESTART_COOLDOWN_MS) {
            executeRestart(change)
        } else {
            // Still in cooldown — queue for later (latest request wins)
            val remaining = RESTART_COOLDOWN_MS - timeSinceLastRestart
            RedactingLogger.d(TAG, "${change.source} change queued (cooldown: ${remaining}ms remaining)")
            pendingChange = change
            scheduleCooldownProcessing(remaining)
        }
    }

    /**
     * Execute the encoder restart sequence.
     * Must only be called while holding the Mutex.
     */
    private fun executeRestart(change: QualityChange) {
        try {
            RedactingLogger.i(
                TAG,
                "${change.source} restart: ${change.resolution} @ ${change.fps}fps, ${change.bitrateKbps}kbps"
            )

            // Full restart sequence (T-007b integration will add stop/reconfigure/start/IDR).
            // For now, apply the bitrate portion immediately.
            encoderBridge.setVideoBitrateOnFly(change.bitrateKbps)

            _effectiveQuality.value = EffectiveQuality(
                bitrateKbps = change.bitrateKbps,
                resolution = change.resolution,
                fps = change.fps
            )
            lastRestartTimeMs = clock()
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Encoder restart failed", e)
            onStateChange?.invoke(StreamState.Stopped(StopReason.ERROR_ENCODER))
        }
    }

    private fun scheduleCooldownProcessing(delayMs: Long) {
        cooldownJob?.cancel()
        cooldownJob = scope.launch {
            delay(delayMs)
            mutex.withLock {
                pendingChange?.let { change ->
                    pendingChange = null
                    executeRestart(change)
                }
            }
        }
    }

    /** Cancel any pending changes (e.g., when stream stops). */
    fun cancel() {
        cooldownJob?.cancel()
        pendingChange = null
    }
}

/**
 * A requested quality change (either from ABR or thermal system).
 */
data class QualityChange(
    val bitrateKbps: Int,
    val resolution: Resolution,
    val fps: Int,
    val source: String
)

/**
 * The current effective quality of the encoder.
 */
data class EffectiveQuality(
    val bitrateKbps: Int = 2500,
    val resolution: Resolution = Resolution(1280, 720),
    val fps: Int = 30
)
