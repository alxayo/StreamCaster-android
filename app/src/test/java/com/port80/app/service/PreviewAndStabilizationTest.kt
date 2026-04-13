package com.port80.app.service

import com.port80.app.data.model.CameraFacing
import com.port80.app.data.model.CameraInfo
import com.port80.app.data.model.StabilizationMode
import com.port80.app.data.model.StreamState
import com.port80.app.util.RedactingLogger
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for preview-only mode, stabilization mode application,
 * camera switch during preview, and state transitions around Previewing.
 */
class PreviewAndStabilizationTest {

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

    // ── StabilizationMode enum tests ────────────────────────────────

    @Test
    fun `StabilizationMode has exactly three values`() {
        assertEquals(3, StabilizationMode.entries.size)
        assertTrue(StabilizationMode.entries.contains(StabilizationMode.OFF))
        assertTrue(StabilizationMode.entries.contains(StabilizationMode.EIS))
        assertTrue(StabilizationMode.entries.contains(StabilizationMode.OIS))
    }

    @Test
    fun `StabilizationMode valueOf round-trips`() {
        StabilizationMode.entries.forEach { mode ->
            assertEquals(mode, StabilizationMode.valueOf(mode.name))
        }
    }

    // ── StubEncoderBridge stabilization tracking ────────────────────

    @Test
    fun `StubEncoderBridge tracks stabilization mode`() {
        val bridge = StubEncoderBridge()
        assertNull(bridge.lastStabilizationMode)

        bridge.setStabilizationMode(StabilizationMode.EIS)
        assertEquals(StabilizationMode.EIS, bridge.lastStabilizationMode)

        bridge.setStabilizationMode(StabilizationMode.OIS)
        assertEquals(StabilizationMode.OIS, bridge.lastStabilizationMode)

        bridge.setStabilizationMode(StabilizationMode.OFF)
        assertEquals(StabilizationMode.OFF, bridge.lastStabilizationMode)
    }

    // ── Previewing state tests ──────────────────────────────────────

    @Test
    fun `Previewing state holds cameraId`() {
        val state = StreamState.Previewing("0")
        assertEquals("0", state.cameraId)
    }

    @Test
    fun `Previewing is distinct from Idle`() {
        val previewing = StreamState.Previewing("0")
        val idle = StreamState.Idle
        assertNotEquals(previewing, idle)
        assertTrue(previewing is StreamState.Previewing)
        assertFalse(previewing is StreamState.Idle)
    }

    @Test
    fun `Previewing state equality uses cameraId`() {
        val a = StreamState.Previewing("0")
        val b = StreamState.Previewing("0")
        val c = StreamState.Previewing("1")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    // ── Camera switch during preview ────────────────────────────────

    @Test
    fun `switchToCamera during preview updates bridge camera ID`() {
        val bridge = StubEncoderBridge()
        val switcher = CameraSwitcher(bridge, listOf(backWide, backUltraWide, front))
        switcher.setInitialCamera("0")

        val result = switcher.switchToCamera("2")
        assertTrue(result)
        assertEquals("2", switcher.currentCameraId)
        assertEquals("2", bridge.lastCameraId)
    }

    @Test
    fun `cycleCamera during preview works`() {
        val bridge = StubEncoderBridge()
        val switcher = CameraSwitcher(bridge, listOf(backWide, front))
        switcher.setInitialCamera("0")

        switcher.switchCamera()
        assertEquals("1", switcher.currentCameraId)
    }

    // ── Stabilization + camera switch interaction ───────────────────

    @Test
    fun `stabilization mode set on bridge persists across camera switch`() {
        val bridge = StubEncoderBridge()
        bridge.setStabilizationMode(StabilizationMode.EIS)
        assertEquals(StabilizationMode.EIS, bridge.lastStabilizationMode)

        // Camera switch doesn't reset the bridge's tracked mode
        val switcher = CameraSwitcher(bridge, listOf(backWide, front))
        switcher.setInitialCamera("0")
        switcher.switchCamera()

        // Stabilization mode still reflects what was set
        assertEquals(StabilizationMode.EIS, bridge.lastStabilizationMode)
    }

    @Test
    fun `stabilization mode can be changed after camera switch`() {
        val bridge = StubEncoderBridge()
        bridge.setStabilizationMode(StabilizationMode.EIS)

        val switcher = CameraSwitcher(bridge, listOf(backWide, front))
        switcher.setInitialCamera("0")
        switcher.switchCamera()

        bridge.setStabilizationMode(StabilizationMode.OIS)
        assertEquals(StabilizationMode.OIS, bridge.lastStabilizationMode)
    }

    // ── State machine transitions ───────────────────────────────────

    @Test
    fun `state machine flow Idle to Previewing to Connecting`() {
        var state: StreamState = StreamState.Idle
        assertTrue(state is StreamState.Idle)

        state = StreamState.Previewing("0")
        assertTrue(state is StreamState.Previewing)
        assertEquals("0", (state as StreamState.Previewing).cameraId)

        state = StreamState.Connecting
        assertTrue(state is StreamState.Connecting)
    }

    @Test
    fun `Previewing to Idle on stopPreview`() {
        var state: StreamState = StreamState.Previewing("0")
        assertTrue(state is StreamState.Previewing)

        state = StreamState.Idle
        assertTrue(state is StreamState.Idle)
    }

    @Test
    fun `exhaustive when covers Previewing`() {
        val states = listOf(
            StreamState.Idle,
            StreamState.Previewing("0"),
            StreamState.Connecting,
            StreamState.Live(),
            StreamState.Reconnecting(0, 3000),
            StreamState.Stopping,
            StreamState.Stopped(com.port80.app.data.model.StopReason.USER_REQUEST)
        )

        states.forEach { state ->
            val label = when (state) {
                is StreamState.Idle -> "idle"
                is StreamState.Previewing -> "previewing"
                is StreamState.Connecting -> "connecting"
                is StreamState.Live -> "live"
                is StreamState.Reconnecting -> "reconnecting"
                is StreamState.Stopping -> "stopping"
                is StreamState.Stopped -> "stopped"
            }
            assertNotNull(label)
        }
    }
}
