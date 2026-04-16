package com.port80.app.service

import com.port80.app.data.model.VideoCodec
import com.port80.app.util.RedactingLogger
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for orientation-aware encoder configuration and preview dimension handling.
 *
 * Covers:
 * - EncoderConfig oriented dimensions (portrait/landscape)
 * - StubEncoderBridge startPreview with dimensions
 * - EncoderConfig rotation values
 */
class OrientationTest {

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

    // ── EncoderConfig orientation tests ──────────────────────────────

    @Test
    fun `EncoderConfig defaults keep width and height for oriented fields`() {
        val config = EncoderConfig(width = 1280, height = 720)
        assertEquals(1280, config.orientedWidth)
        assertEquals(720, config.orientedHeight)
        assertEquals(0, config.rotation)
    }

    @Test
    fun `EncoderConfig with portrait orientation swaps dimensions`() {
        val config = EncoderConfig(
            width = 1280,
            height = 720,
            orientedWidth = 720,
            orientedHeight = 1280,
            rotation = 90
        )
        assertEquals(720, config.orientedWidth)
        assertEquals(1280, config.orientedHeight)
        assertEquals(90, config.rotation)
        // Original normalized dimensions remain unchanged
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
    }

    @Test
    fun `EncoderConfig with landscape orientation preserves dimensions`() {
        val config = EncoderConfig(
            width = 1920,
            height = 1080,
            orientedWidth = 1920,
            orientedHeight = 1080,
            rotation = 0
        )
        assertEquals(1920, config.orientedWidth)
        assertEquals(1080, config.orientedHeight)
        assertEquals(0, config.rotation)
    }

    @Test
    fun `EncoderConfig copy preserves orientation fields`() {
        val original = EncoderConfig(
            width = 1280,
            height = 720,
            orientedWidth = 720,
            orientedHeight = 1280,
            rotation = 90
        )
        val copied = original.copy(videoBitrateKbps = 3000)
        assertEquals(720, copied.orientedWidth)
        assertEquals(1280, copied.orientedHeight)
        assertEquals(90, copied.rotation)
        assertEquals(3000, copied.videoBitrateKbps)
    }

    @Test
    fun `EncoderConfig with all codecs and rotation`() {
        for (codec in VideoCodec.entries) {
            val config = EncoderConfig(
                videoCodec = codec,
                orientedWidth = 720,
                orientedHeight = 1280,
                rotation = 90
            )
            assertEquals("$codec should have orientedWidth=720", 720, config.orientedWidth)
            assertEquals("$codec should have rotation=90", 90, config.rotation)
        }
    }

    // ── StubEncoderBridge dimension-aware preview tests ──────────────

    @Test
    fun `StubEncoderBridge startPreview with dimensions records camera ID`() {
        val bridge = StubEncoderBridge()
        bridge.startPreview(mockk(relaxed = true), "0", 1280, 720)
        assertEquals("0", bridge.lastCameraId)
    }

    @Test
    fun `StubEncoderBridge startPreview without dimensions still works`() {
        val bridge = StubEncoderBridge()
        bridge.startPreview(mockk(relaxed = true), "1")
        assertEquals("1", bridge.lastCameraId)
    }

    // ── Orientation derivation logic tests ───────────────────────────

    @Test
    fun `portrait detection from surface dimensions`() {
        // Simulate what StreamingService.buildEncoderConfig does
        val surfaceWidth = 1080
        val surfaceHeight = 2340
        val resWidth = 1280
        val resHeight = 720

        val isPortrait = surfaceHeight > surfaceWidth
        assertTrue(isPortrait)

        val orientedWidth = if (isPortrait) minOf(resWidth, resHeight) else maxOf(resWidth, resHeight)
        val orientedHeight = if (isPortrait) maxOf(resWidth, resHeight) else minOf(resWidth, resHeight)
        val rotation = if (isPortrait) 90 else 0

        assertEquals(720, orientedWidth)
        assertEquals(1280, orientedHeight)
        assertEquals(90, rotation)
    }

    @Test
    fun `landscape detection from surface dimensions`() {
        val surfaceWidth = 2340
        val surfaceHeight = 1080
        val resWidth = 1280
        val resHeight = 720

        val isPortrait = surfaceHeight > surfaceWidth
        assertFalse(isPortrait)

        val orientedWidth = if (isPortrait) minOf(resWidth, resHeight) else maxOf(resWidth, resHeight)
        val orientedHeight = if (isPortrait) maxOf(resWidth, resHeight) else minOf(resWidth, resHeight)
        val rotation = if (isPortrait) 90 else 0

        assertEquals(1280, orientedWidth)
        assertEquals(720, orientedHeight)
        assertEquals(0, rotation)
    }

    @Test
    fun `square surface defaults to landscape`() {
        val surfaceWidth = 1080
        val surfaceHeight = 1080

        val isPortrait = surfaceHeight > surfaceWidth
        assertFalse(isPortrait)

        val rotation = if (isPortrait) 90 else 0
        assertEquals(0, rotation)
    }

    @Test
    fun `zero surface dimensions default to landscape`() {
        // Before surface is attached, dimensions are 0x0
        val surfaceWidth = 0
        val surfaceHeight = 0

        // With the fallback logic, 0x0 triggers system configuration check.
        // Without system config context, the derivation uses surface dims only.
        val isPortrait = surfaceHeight > surfaceWidth
        assertFalse(isPortrait)
    }

    @Test
    fun `zero surface dimensions with portrait config fallback`() {
        // Simulates the fallback path in buildEncoderConfig when surface dims are 0x0
        // and resources.configuration.orientation reports PORTRAIT
        val surfaceWidth = 0
        val surfaceHeight = 0
        val configOrientation = android.content.res.Configuration.ORIENTATION_PORTRAIT

        val isPortrait = if (surfaceWidth > 0 || surfaceHeight > 0) {
            surfaceHeight > surfaceWidth
        } else {
            configOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        }
        assertTrue("Should fall back to portrait from system configuration", isPortrait)

        val resWidth = 1280
        val resHeight = 720
        val orientedWidth = if (isPortrait) minOf(resWidth, resHeight) else maxOf(resWidth, resHeight)
        val orientedHeight = if (isPortrait) maxOf(resWidth, resHeight) else minOf(resWidth, resHeight)
        val rotation = if (isPortrait) 90 else 0

        assertEquals(720, orientedWidth)
        assertEquals(1280, orientedHeight)
        assertEquals(90, rotation)
    }

    @Test
    fun `zero surface dimensions with landscape config fallback`() {
        val surfaceWidth = 0
        val surfaceHeight = 0
        val configOrientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE

        val isPortrait = if (surfaceWidth > 0 || surfaceHeight > 0) {
            surfaceHeight > surfaceWidth
        } else {
            configOrientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        }
        assertFalse("Should fall back to landscape from system configuration", isPortrait)
    }
}
