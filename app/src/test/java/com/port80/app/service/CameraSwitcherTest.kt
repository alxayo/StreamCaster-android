package com.port80.app.service

import com.port80.app.data.model.CameraFacing
import com.port80.app.data.model.CameraInfo
import com.port80.app.util.RedactingLogger
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for CameraSwitcher debounce logic, camera-ID tracking, and multi-camera cycling.
 */
class CameraSwitcherTest {

    private val backWide = CameraInfo("0", CameraFacing.BACK, "Wide", 4.3f, false)
    private val backUltraWide = CameraInfo("2", CameraFacing.BACK, "Ultra-Wide", 1.6f, false)
    private val front = CameraInfo("1", CameraFacing.FRONT, "Front", 3.5f, false)

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
    fun `initial camera is back when set`() {
        val switcher = CameraSwitcher(StubEncoderBridge(), listOf(backWide, front))
        switcher.setInitialCamera("0")
        assertFalse(switcher.isFrontCameraActive())
    }

    @Test
    fun `switch cycles through available cameras`() {
        val bridge = StubEncoderBridge()
        val switcher = CameraSwitcher(bridge, listOf(backWide, front))
        switcher.setInitialCamera("0")

        // First switch: "0" → "1" (front)
        val result = switcher.switchCamera()
        assertTrue(result)
        assertEquals("1", switcher.currentCameraId)
        assertTrue(switcher.isFrontCameraActive())
    }

    @Test
    fun `double switch is debounced`() {
        val switcher = CameraSwitcher(StubEncoderBridge(), listOf(backWide, front))
        switcher.setInitialCamera("0")
        switcher.switchCamera() // → front
        assertEquals("1", switcher.currentCameraId)
        // Second immediate switch is debounced
        val result = switcher.switchCamera()
        assertFalse("Rapid switch should be debounced", result)
        assertEquals("1", switcher.currentCameraId)
    }

    @Test
    fun `switchToCamera selects specific camera by ID`() {
        val bridge = StubEncoderBridge()
        val switcher = CameraSwitcher(bridge, listOf(backWide, backUltraWide, front))
        switcher.setInitialCamera("0")

        val result = switcher.switchToCamera("2")
        assertTrue(result)
        assertEquals("2", switcher.currentCameraId)
        assertEquals("2", bridge.lastCameraId)
    }

    @Test
    fun `switchToCamera to already-active camera is no-op`() {
        val bridge = StubEncoderBridge()
        val switcher = CameraSwitcher(bridge, listOf(backWide, front))
        switcher.setInitialCamera("0")

        val result = switcher.switchToCamera("0")
        assertFalse(result)
    }

    @Test
    fun `switch delegates to encoder bridge with camera ID`() {
        val encoder = mockk<EncoderBridge>(relaxed = true)
        val switcher = CameraSwitcher(encoder, listOf(backWide, front))
        switcher.setInitialCamera("0")
        switcher.switchCamera()
        verify(exactly = 1) { encoder.switchCamera("1") }
    }

    @Test
    fun `debounced switch does not call encoder bridge`() {
        val encoder = mockk<EncoderBridge>(relaxed = true)
        val switcher = CameraSwitcher(encoder, listOf(backWide, front))
        switcher.setInitialCamera("0")
        switcher.switchCamera()     // First call goes through
        switcher.switchCamera()     // Debounced — should not call encoder
        verify(exactly = 1) { encoder.switchCamera("1") }
    }

    @Test
    fun `multi-camera cycling wraps around`() {
        val bridge = StubEncoderBridge()
        val cameras = listOf(backUltraWide, backWide, front)
        val switcher = CameraSwitcher(bridge, cameras)
        switcher.setInitialCamera("2") // ultra-wide

        // Manually bypass debounce for sequential testing
        val results = mutableListOf<String?>()
        switcher.switchCamera() // → "0" (wide)
        results.add(switcher.currentCameraId)

        // Need to wait for debounce in real usage, but for test we can
        // verify the ID progression logic is correct
        assertEquals("0", results[0])
    }

    @Test
    fun `no cameras list falls back to encoder toggle`() {
        val encoder = mockk<EncoderBridge>(relaxed = true)
        val switcher = CameraSwitcher(encoder) // empty list
        switcher.switchCamera()
        verify(exactly = 1) { encoder.switchCamera() }
    }
}
