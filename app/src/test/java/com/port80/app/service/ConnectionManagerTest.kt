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

        connectionManager = ConnectionManager(
            connectivityManager = connectivityManager,
            powerManager = powerManager,
            reconnectPolicy = policy,
            scope = testScope
        )
        connectionManager.onStateChange = { stateChanges.add(it) }
    }

    @After
    fun tearDown() {
        unmockkObject(RedactingLogger)
    }

    @Test
    fun `backoff sequence is correct`() {
        connectionManager.onConnect = { false }
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        // First retry scheduled: 3s delay
        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges[0])

        // Each advanceTimeBy fires the retry, which fails and schedules the next
        testScope.advanceTimeBy(3_000L)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(1, 6_000L), stateChanges[1])

        testScope.advanceTimeBy(6_000L)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(2, 12_000L), stateChanges[2])

        testScope.advanceTimeBy(12_000L)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(3, 24_000L), stateChanges[3])

        testScope.advanceTimeBy(24_000L)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(4, 48_000L), stateChanges[4])

        // Caps at 60s
        testScope.advanceTimeBy(48_000L)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(5, 60_000L), stateChanges[5])
    }

    @Test
    fun `auth failure stops all retries`() {
        connectionManager.onConnect = { false }
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges.last())

        // Auth failure arrives before the retry timer fires
        connectionManager.onAuthFailure()
        testScope.runCurrent()

        assertEquals(StreamState.Stopped(StopReason.ERROR_AUTH), stateChanges.last())

        // Advance well past any pending retry — no further state changes
        val stateCount = stateChanges.size
        testScope.advanceTimeBy(120_000L)
        assertEquals(stateCount, stateChanges.size)
    }

    @Test
    fun `user stop cancels pending retries`() {
        connectionManager.onConnect = { false }
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges.last())

        // User hits stop
        connectionManager.stop()

        // Advance well past any pending retry — no further state changes
        val stateCount = stateChanges.size
        testScope.advanceTimeBy(120_000L)
        assertEquals(stateCount, stateChanges.size)
    }

    @Test
    fun `successful reconnect resets counter`() {
        var connectAttempt = 0
        connectionManager.onConnect = {
            connectAttempt++
            connectAttempt >= 3 // Succeed on 3rd attempt
        }
        connectionManager.start()
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges[0])

        // 1st retry fails → backoff to 6s
        testScope.advanceTimeBy(3_000L)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(1, 6_000L), stateChanges[1])

        // 2nd retry fails → backoff to 12s
        testScope.advanceTimeBy(6_000L)
        testScope.runCurrent()
        assertEquals(StreamState.Reconnecting(2, 12_000L), stateChanges[2])

        // 3rd retry succeeds → Live
        testScope.advanceTimeBy(12_000L)
        testScope.runCurrent()
        assertEquals(StreamState.Live(), stateChanges[3])

        // Simulate another connection loss — counter should start at 0 again
        connectAttempt = 0
        connectionManager.onConnectionLost()
        testScope.runCurrent()

        assertEquals(StreamState.Reconnecting(0, 3_000L), stateChanges.last())
    }
}
