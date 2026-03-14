package com.port80.app.service

import com.port80.app.data.model.Resolution
import com.port80.app.data.model.StopReason
import com.port80.app.data.model.StreamState
import com.port80.app.util.RedactingLogger
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EncoderControllerTest {

    private lateinit var bridge: EncoderBridge
    private lateinit var controller: EncoderController
    private lateinit var testScope: TestScope
    private var fakeTimeMs: Long = 100_000L
    private var capturedState: StreamState? = null

    @Before
    fun setUp() {
        mockkObject(RedactingLogger)
        every { RedactingLogger.d(any(), any()) } just Runs
        every { RedactingLogger.i(any(), any()) } just Runs
        every { RedactingLogger.w(any(), any()) } just Runs
        every { RedactingLogger.e(any(), any()) } just Runs
        every { RedactingLogger.e(any(), any(), any()) } just Runs

        bridge = mockk(relaxed = true)
        testScope = TestScope()
        capturedState = null
        fakeTimeMs = 100_000L

        controller = EncoderController(
            encoderBridge = bridge,
            scope = testScope,
            onStateChange = { capturedState = it },
            clock = { fakeTimeMs }
        )
    }

    @After
    fun tearDown() {
        unmockkObject(RedactingLogger)
    }

    // ── Bitrate-only changes ──────────────────────────────────

    @Test
    fun `bitrate-only ABR change applies instantly`() = testScope.runTest {
        controller.requestAbrChange(bitrateKbps = 1500)

        verify(exactly = 1) { bridge.setVideoBitrateOnFly(1500) }
        assertEquals(1500, controller.effectiveQuality.value.bitrateKbps)
    }

    @Test
    fun `bitrate-only change does not trigger cooldown`() = testScope.runTest {
        controller.requestAbrChange(bitrateKbps = 1500)
        controller.requestAbrChange(bitrateKbps = 1200)
        controller.requestAbrChange(bitrateKbps = 800)

        verify(exactly = 1) { bridge.setVideoBitrateOnFly(1500) }
        verify(exactly = 1) { bridge.setVideoBitrateOnFly(1200) }
        verify(exactly = 1) { bridge.setVideoBitrateOnFly(800) }
        assertEquals(800, controller.effectiveQuality.value.bitrateKbps)
    }

    @Test
    fun `bitrate-only change preserves current resolution and fps`() = testScope.runTest {
        val defaults = controller.effectiveQuality.value

        controller.requestAbrChange(bitrateKbps = 1000)

        val quality = controller.effectiveQuality.value
        assertEquals(1000, quality.bitrateKbps)
        assertEquals(defaults.resolution, quality.resolution)
        assertEquals(defaults.fps, quality.fps)
    }

    // ── Cooldown enforcement ──────────────────────────────────

    @Test
    fun `first resolution change applies immediately`() = testScope.runTest {
        val newRes = Resolution(854, 480)
        controller.requestAbrChange(bitrateKbps = 1000, resolution = newRes, fps = 24)

        val quality = controller.effectiveQuality.value
        assertEquals(newRes, quality.resolution)
        assertEquals(24, quality.fps)
        assertEquals(1000, quality.bitrateKbps)
    }

    @Test
    fun `second resolution change within cooldown is queued`() = testScope.runTest {
        val res480 = Resolution(854, 480)
        val res360 = Resolution(640, 360)

        // First change applies immediately
        controller.requestAbrChange(bitrateKbps = 1000, resolution = res480, fps = 24)
        assertEquals(res480, controller.effectiveQuality.value.resolution)

        // Advance time just a bit (still within cooldown)
        fakeTimeMs += 5_000L

        // Second change should be queued
        controller.requestAbrChange(bitrateKbps = 800, resolution = res360, fps = 20)

        // Quality is still the first change
        assertEquals(res480, controller.effectiveQuality.value.resolution)
        assertEquals(1000, controller.effectiveQuality.value.bitrateKbps)
    }

    @Test
    fun `queued change applies after cooldown expires`() = testScope.runTest {
        val res480 = Resolution(854, 480)
        val res360 = Resolution(640, 360)

        controller.requestAbrChange(bitrateKbps = 1000, resolution = res480, fps = 24)

        // Advance time partway through cooldown
        fakeTimeMs += 5_000L
        controller.requestAbrChange(bitrateKbps = 800, resolution = res360, fps = 20)

        // Advance clock past cooldown and let coroutine run
        fakeTimeMs += EncoderController.RESTART_COOLDOWN_MS
        advanceUntilIdle()

        // Now the queued change should be applied
        val quality = controller.effectiveQuality.value
        assertEquals(res360, quality.resolution)
        assertEquals(20, quality.fps)
        assertEquals(800, quality.bitrateKbps)
    }

    @Test
    fun `change after cooldown applies immediately`() = testScope.runTest {
        val res480 = Resolution(854, 480)
        val res360 = Resolution(640, 360)

        controller.requestAbrChange(bitrateKbps = 1000, resolution = res480, fps = 24)

        // Wait out the full cooldown
        fakeTimeMs += EncoderController.RESTART_COOLDOWN_MS

        controller.requestAbrChange(bitrateKbps = 800, resolution = res360, fps = 20)

        // Applied immediately — no need to advanceUntilIdle
        assertEquals(res360, controller.effectiveQuality.value.resolution)
    }

    // ── Coalescing (latest wins) ──────────────────────────────

    @Test
    fun `multiple queued changes coalesce to latest`() = testScope.runTest {
        val res480 = Resolution(854, 480)
        val res360 = Resolution(640, 360)
        val res240 = Resolution(426, 240)

        // First change applies immediately, starts cooldown
        controller.requestAbrChange(bitrateKbps = 1000, resolution = res480, fps = 24)

        // Queue several changes during cooldown
        fakeTimeMs += 1_000L
        controller.requestAbrChange(bitrateKbps = 800, resolution = res360, fps = 20)
        fakeTimeMs += 1_000L
        controller.requestThermalChange(resolution = res240, fps = 15, bitrateKbps = 500)

        // Let cooldown expire
        fakeTimeMs += EncoderController.RESTART_COOLDOWN_MS
        advanceUntilIdle()

        // Only the last (thermal) change should have been applied
        val quality = controller.effectiveQuality.value
        assertEquals(res240, quality.resolution)
        assertEquals(15, quality.fps)
        assertEquals(500, quality.bitrateKbps)
    }

    // ── Concurrent safety ─────────────────────────────────────

    @Test
    fun `concurrent requests do not crash`() = testScope.runTest {
        val jobs = (1..20).map { i ->
            launch {
                if (i % 3 == 0) {
                    controller.requestThermalChange(
                        resolution = Resolution(640, 360),
                        fps = 20,
                        bitrateKbps = 500 + i * 10
                    )
                } else {
                    controller.requestAbrChange(bitrateKbps = 1000 + i * 100)
                }
            }
        }
        jobs.forEach { it.join() }

        // No crash — effective quality reflects some applied value
        assertTrue(controller.effectiveQuality.value.bitrateKbps > 0)
    }

    // ── Cancel ────────────────────────────────────────────────

    @Test
    fun `cancel clears pending changes`() = testScope.runTest {
        val res480 = Resolution(854, 480)
        val res360 = Resolution(640, 360)

        controller.requestAbrChange(bitrateKbps = 1000, resolution = res480, fps = 24)
        fakeTimeMs += 1_000L
        controller.requestAbrChange(bitrateKbps = 800, resolution = res360, fps = 20)

        // Cancel before cooldown expires
        controller.cancel()

        // Let cooldown timer fire — should be cancelled
        fakeTimeMs += EncoderController.RESTART_COOLDOWN_MS
        advanceUntilIdle()

        // Still at the first change
        assertEquals(res480, controller.effectiveQuality.value.resolution)
        assertEquals(1000, controller.effectiveQuality.value.bitrateKbps)
    }

    // ── Error handling ────────────────────────────────────────

    @Test
    fun `encoder restart failure triggers ERROR_ENCODER state`() = testScope.runTest {
        every { bridge.setVideoBitrateOnFly(any()) } throws RuntimeException("codec died")

        controller.requestAbrChange(
            bitrateKbps = 1000,
            resolution = Resolution(854, 480),
            fps = 24
        )

        assertTrue(capturedState is StreamState.Stopped)
        assertEquals(
            StopReason.ERROR_ENCODER,
            (capturedState as StreamState.Stopped).reason
        )
    }

    // ── Thermal changes ───────────────────────────────────────

    @Test
    fun `thermal change applies immediately when no cooldown`() = testScope.runTest {
        val res480 = Resolution(854, 480)

        controller.requestThermalChange(
            resolution = res480,
            fps = 24,
            bitrateKbps = 1000
        )

        val quality = controller.effectiveQuality.value
        assertEquals(res480, quality.resolution)
        assertEquals(24, quality.fps)
        assertEquals(1000, quality.bitrateKbps)
    }

    @Test
    fun `thermal change respects cooldown after ABR restart`() = testScope.runTest {
        val res480 = Resolution(854, 480)
        val res360 = Resolution(640, 360)

        // ABR restart starts cooldown
        controller.requestAbrChange(bitrateKbps = 1000, resolution = res480, fps = 24)

        // Thermal change during cooldown is queued
        fakeTimeMs += 5_000L
        controller.requestThermalChange(resolution = res360, fps = 20, bitrateKbps = 800)

        // Not applied yet
        assertEquals(res480, controller.effectiveQuality.value.resolution)
    }
}
