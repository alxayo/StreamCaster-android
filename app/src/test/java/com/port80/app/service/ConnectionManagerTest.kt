package com.port80.app.service

import android.net.ConnectivityManager
import android.os.PowerManager
import com.port80.app.data.model.StopReason
import com.port80.app.data.model.StreamState
import com.port80.app.util.RedactingLogger
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for ConnectionManager reconnection logic.
 * Uses TestScope to control coroutine timing and verify backoff behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    private val powerManager = mockk<PowerManager>()

    private val policy = ExponentialBackoffReconnectPolicy(
        baseDelayMs = 3_000L, maxDelayMs = 60_000L, jitterFactor = 0.0
    )

    private lateinit var connectionManager: ConnectionManager
    private val stateChanges = mutableListOf<StreamState>()
    private var reconnectRequestCount = 0
    private var exhaustedReason: StopReason? = null

    @Before
    fun setUp() {
        // Mock RedactingLogger to avoid android.util.Log calls in unit tests
        mockkObject(RedactingLogger)
        every { RedactingLogger.d(any(), any()) } just runs
        every { RedactingLogger.i(any(), any()) } just runs
        every { RedactingLogger.w(any(), any()) } just runs
        every { RedactingLogger.e(any(), any()) } just runs
        every { RedactingLogger.e(any(), any(), any<Throwable>()) } just runs

        every { powerManager.isDeviceIdleMode } returns false

        reconnectRequestCount = 0
        exhaustedReason = null

        connectionManager = ConnectionManager(
            connectivityManager = connectivityManager,
            powerManager = powerManager,
            reconnectPolicy = policy,
            scope = testScope
        )
        connectionManager.onStateChanged = { stateChanges.add(it) }
        connectionManager.requestReconnect = { reconnectRequestCount++ }
        connectionManager.onReconnectExhausted = { exhaustedReason = it }
    }

    @After
    fun tearDown() {
        unmockkObject(RedactingLogger)
    }

    @Test
    fun `backoff sequence emits correct Reconnecting states`() {
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        // First retry scheduled: 3s delay
        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges[0])

        // Timer fires → reconnect requested → simulate failure
        testScope.advanceTimeBy(3_000L)
        testScope.runCurrent()
        assertEquals(1, reconnectRequestCount)

        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(1, 6_000L), stateChanges[1])

        testScope.advanceTimeBy(6_000L)
        testScope.runCurrent()
        assertEquals(2, reconnectRequestCount)

        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(2, 12_000L), stateChanges[2])

        testScope.advanceTimeBy(12_000L)
        testScope.runCurrent()
        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(3, 24_000L), stateChanges[3])

        testScope.advanceTimeBy(24_000L)
        testScope.runCurrent()
        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(4, 48_000L), stateChanges[4])

        // Caps at 60s
        testScope.advanceTimeBy(48_000L)
        testScope.runCurrent()
        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(5, 60_000L), stateChanges[5])
    }

    @Test
    fun `auth failure stops all retries`() {
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges.last())

        // Auth failure arrives before the retry timer fires
        connectionManager.onAuthFailure()
        testScope.runCurrent()

        // Advance well past any pending retry — no further state changes or requests
        val stateCount = stateChanges.size
        val requestCount = reconnectRequestCount
        testScope.advanceTimeBy(120_000L)
        testScope.runCurrent()
        assertEquals(stateCount, stateChanges.size)
        assertEquals(requestCount, reconnectRequestCount)
    }

    @Test
    fun `user stop cancels pending retries`() {
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges.last())

        // User hits stop
        connectionManager.stop()

        // Advance well past any pending retry — no further state changes
        val stateCount = stateChanges.size
        testScope.advanceTimeBy(120_000L)
        testScope.runCurrent()
        assertEquals(stateCount, stateChanges.size)
        assertFalse(connectionManager.isStarted)
    }

    @Test
    fun `successful reconnect resets counter`() {
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges[0])

        // 1st retry fires → fail
        testScope.advanceTimeBy(3_000L)
        testScope.runCurrent()
        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(1, 6_000L), stateChanges[1])

        // 2nd retry fires → fail
        testScope.advanceTimeBy(6_000L)
        testScope.runCurrent()
        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(2, 12_000L), stateChanges[2])

        // 3rd retry fires → success!
        testScope.advanceTimeBy(12_000L)
        testScope.runCurrent()
        connectionManager.notifyReconnectResult(true)
        testScope.runCurrent()

        // No Stopped or Live emitted by CM — service handles that
        // Simulate another connection loss — counter should start at 0 again
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges.last())
    }

    @Test
    fun `duplicate connection loss does not reset pending retry`() {
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals(listOf(StreamState.Reconnecting(0, 3_000L)), stateChanges)

        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals("duplicate loss should not reschedule the retry", 1, stateChanges.size)

        testScope.advanceTimeBy(3_000L)
        testScope.runCurrent()

        assertEquals(1, reconnectRequestCount)
    }

    @Test
    fun `max retries exhausted fires onReconnectExhausted`() {
        val limitedPolicy = ExponentialBackoffReconnectPolicy(
            baseDelayMs = 1_000L, maxDelayMs = 60_000L, maxAttempts = 2, jitterFactor = 0.0
        )
        connectionManager = ConnectionManager(
            connectivityManager = connectivityManager,
            powerManager = powerManager,
            reconnectPolicy = limitedPolicy,
            scope = testScope
        )
        connectionManager.onStateChanged = { stateChanges.add(it) }
        connectionManager.requestReconnect = { reconnectRequestCount++ }
        connectionManager.onReconnectExhausted = { exhaustedReason = it }

        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        // Attempt 0
        testScope.advanceTimeBy(1_000L)
        testScope.runCurrent()
        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()

        // Attempt 1
        testScope.advanceTimeBy(2_000L)
        testScope.runCurrent()
        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()

        // Attempt 2 → shouldRetry(2) returns false → exhausted
        assertEquals(StopReason.ERROR_ENCODER, exhaustedReason)
    }

    @Test
    fun `only one attempt in flight at a time`() {
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        // Timer fires → first request
        testScope.advanceTimeBy(3_000L)
        testScope.runCurrent()
        assertEquals(1, reconnectRequestCount)

        // Simulate network callback before notifyReconnectResult
        // This should NOT fire a second request since one is in flight
        connectionManager.onConnectionLost()
        testScope.runCurrent()
        assertEquals("should not fire second request while one is in flight", 1, reconnectRequestCount)

        // Now resolve the in-flight attempt
        connectionManager.notifyReconnectResult(false)
        testScope.runCurrent()

        // Next retry scheduled — timer fires
        testScope.advanceTimeBy(6_000L)
        testScope.runCurrent()
        assertEquals(2, reconnectRequestCount)
    }

    @Test
    fun `stop cancels in-flight attempt tracking`() {
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        testScope.advanceTimeBy(3_000L)
        testScope.runCurrent()
        assertEquals(1, reconnectRequestCount)

        // Stop while attempt is in flight
        connectionManager.stop()

        // notifyReconnectResult after stop should be a no-op
        connectionManager.notifyReconnectResult(true)
        testScope.runCurrent()

        assertFalse(connectionManager.isStarted)
    }
}
