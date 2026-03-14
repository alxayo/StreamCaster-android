package com.port80.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ExponentialBackoffReconnectPolicy.
 * Verifies the backoff sequence, jitter bounds, and retry limits.
 */
class ReconnectPolicyTest {
    @Test
    fun `backoff increases exponentially`() {
        // Use zero jitter for predictable testing
        val policy = ExponentialBackoffReconnectPolicy(
            baseDelayMs = 3000, maxDelayMs = 60000, jitterFactor = 0.0
        )
        assertEquals(3000L, policy.nextDelayMs(0))
        assertEquals(6000L, policy.nextDelayMs(1))
        assertEquals(12000L, policy.nextDelayMs(2))
        assertEquals(24000L, policy.nextDelayMs(3))
        assertEquals(48000L, policy.nextDelayMs(4))
    }

    @Test
    fun `backoff caps at maxDelay`() {
        val policy = ExponentialBackoffReconnectPolicy(
            baseDelayMs = 3000, maxDelayMs = 60000, jitterFactor = 0.0
        )
        assertEquals(60000L, policy.nextDelayMs(5))
        assertEquals(60000L, policy.nextDelayMs(10))
        assertEquals(60000L, policy.nextDelayMs(100))
    }

    @Test
    fun `jitter stays within bounds`() {
        val policy = ExponentialBackoffReconnectPolicy(
            baseDelayMs = 3000, maxDelayMs = 60000, jitterFactor = 0.2
        )
        // Run multiple times to test jitter range
        repeat(100) {
            val delay = policy.nextDelayMs(0) // base = 3000, jitter = +/-600
            assertTrue("Delay $delay should be >= 2400", delay >= 2400)
            assertTrue("Delay $delay should be <= 3600", delay <= 3600)
        }
    }

    @Test
    fun `shouldRetry respects maxAttempts`() {
        val policy = ExponentialBackoffReconnectPolicy(maxAttempts = 5)
        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(4))
        assertFalse(policy.shouldRetry(5))
    }

    @Test
    fun `unlimited retries by default`() {
        val policy = ExponentialBackoffReconnectPolicy()
        assertTrue(policy.shouldRetry(1000))
    }
}
