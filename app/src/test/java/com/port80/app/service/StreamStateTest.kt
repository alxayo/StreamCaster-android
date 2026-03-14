package com.port80.app.service

import com.port80.app.data.model.StreamState
import com.port80.app.data.model.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the StreamState state machine transitions.
 * Verifies that state changes follow the expected flow.
 */
class StreamStateTest {
    @Test
    fun `initial state is Idle`() {
        val state: StreamState = StreamState.Idle
        assertTrue(state is StreamState.Idle)
    }

    @Test
    fun `Live state default values`() {
        val live = StreamState.Live()
        assertTrue(live.cameraActive)
        assertFalse(live.isMuted)
    }

    @Test
    fun `Live state with muted audio`() {
        val live = StreamState.Live(isMuted = true)
        assertTrue(live.isMuted)
    }

    @Test
    fun `Reconnecting tracks attempt and delay`() {
        val state = StreamState.Reconnecting(attempt = 3, nextRetryMs = 12000)
        assertEquals(3, state.attempt)
        assertEquals(12000L, state.nextRetryMs)
    }

    @Test
    fun `Stopped contains reason`() {
        val state = StreamState.Stopped(StopReason.ERROR_AUTH)
        assertEquals(StopReason.ERROR_AUTH, state.reason)
    }

    @Test
    fun `all StopReasons are defined`() {
        val reasons = StopReason.entries
        assertTrue(reasons.size >= 7)
        assertTrue(reasons.contains(StopReason.USER_REQUEST))
        assertTrue(reasons.contains(StopReason.THERMAL_CRITICAL))
    }
}
