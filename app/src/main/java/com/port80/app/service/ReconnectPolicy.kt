package com.port80.app.service

/**
 * Defines how the app retries after a network disconnection.
 * The default implementation uses exponential backoff with random jitter
 * to avoid all clients reconnecting to the server at the same time.
 */
interface ReconnectPolicy {
    /**
     * Calculate how long to wait before the next retry attempt.
     * @param attempt retry attempt number (0 = first retry)
     * @return delay in milliseconds (includes random jitter)
     */
    fun nextDelayMs(attempt: Int): Long

    /**
     * Check if we should try again.
     * @param attempt current attempt number
     * @return true if another retry is allowed
     */
    fun shouldRetry(attempt: Int): Boolean

    /** Reset the retry counter (call after a successful reconnection). */
    fun reset()
}

/**
 * Exponential backoff with jitter for RTMP reconnection.
 *
 * The delay doubles each attempt: 3s → 6s → 12s → 24s → 48s → 60s (cap).
 * Random jitter of ±20% prevents "thundering herd" when many clients reconnect.
 *
 * Example: attempt 0 = ~3s, attempt 1 = ~6s, attempt 2 = ~12s, ...
 */
class ExponentialBackoffReconnectPolicy(
    private val baseDelayMs: Long = 3_000L,
    private val maxDelayMs: Long = 60_000L,
    private val maxAttempts: Int = Int.MAX_VALUE,
    private val jitterFactor: Double = 0.2
) : ReconnectPolicy {

    override fun nextDelayMs(attempt: Int): Long {
        // Double the base delay for each attempt: 3s, 6s, 12s, 24s, 48s...
        // coerceAtMost(20) prevents overflow from shifting too many bits
        val exponentialDelay = (baseDelayMs * (1L shl attempt.coerceAtMost(20)))
            .coerceAtMost(maxDelayMs)

        // Add random jitter (±20%) so clients don't all retry at once
        val jitterRange = (exponentialDelay * jitterFactor).toLong()
        val jitter = if (jitterRange > 0) {
            kotlin.random.Random.nextLong(-jitterRange, jitterRange + 1)
        } else 0L

        return (exponentialDelay + jitter).coerceAtLeast(0L)
    }

    override fun shouldRetry(attempt: Int): Boolean = attempt < maxAttempts

    override fun reset() {
        // This implementation is stateless — nothing to reset.
        // Subclasses could track adaptive state here.
    }
}
