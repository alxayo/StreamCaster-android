package com.port80.app.service

import com.port80.app.util.RedactingLogger
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for CameraSwitcher debounce logic.
 */
class CameraSwitcherTest {

    @Before
    fun setUp() {
        mockkObject(RedactingLogger)
        every { RedactingLogger.d(any(), any()) } just runs
        every { RedactingLogger.i(any(), any()) } just runs
        every { RedactingLogger.w(any(), any()) } just runs
        every { RedactingLogger.e(any(), any()) } just runs
        every { RedactingLogger.e(any(), any(), any<Throwable>()) } just runs
    }

    @After
    fun tearDown() {
        unmockkObject(RedactingLogger)
    }

    @Test
    fun `initial camera is back`() {
        val switcher = CameraSwitcher(StubEncoderBridge())
        assertFalse(switcher.isFrontCameraActive())
    }

    @Test
    fun `switch toggles camera`() {
        val switcher = CameraSwitcher(StubEncoderBridge())
        val result = switcher.switchCamera()
        assertTrue(result)
        assertTrue(switcher.isFrontCameraActive())
    }

    @Test
    fun `double switch returns to back camera after debounce`() {
        val switcher = CameraSwitcher(StubEncoderBridge())
        switcher.switchCamera() // → front
        assertTrue(switcher.isFrontCameraActive())
        // Second immediate switch is debounced, so state stays front
        val result = switcher.switchCamera()
        assertFalse("Rapid switch should be debounced", result)
        assertTrue("Should still be front after debounced switch", switcher.isFrontCameraActive())
    }

    @Test
    fun `switch delegates to encoder bridge`() {
        val encoder = mockk<EncoderBridge>(relaxed = true)
        val switcher = CameraSwitcher(encoder)
        switcher.switchCamera()
        verify(exactly = 1) { encoder.switchCamera() }
    }

    @Test
    fun `debounced switch does not call encoder bridge`() {
        val encoder = mockk<EncoderBridge>(relaxed = true)
        val switcher = CameraSwitcher(encoder)
        switcher.switchCamera()     // First call goes through
        switcher.switchCamera()     // Debounced — should not call encoder
        verify(exactly = 1) { encoder.switchCamera() }
    }
}
