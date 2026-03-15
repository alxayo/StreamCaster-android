package com.port80.app.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Resolution data class.
 */
class ResolutionTest {

    @Test
    fun `toString returns WxH format`() {
        assertEquals("1280x720", Resolution(1280, 720).toString())
        assertEquals("1920x1080", Resolution(1920, 1080).toString())
        assertEquals("854x480", Resolution(854, 480).toString())
        assertEquals("640x360", Resolution(640, 360).toString())
    }

    @Test
    fun `label returns height with p suffix`() {
        assertEquals("720p", Resolution(1280, 720).label)
        assertEquals("1080p", Resolution(1920, 1080).label)
        assertEquals("480p", Resolution(854, 480).label)
        assertEquals("360p", Resolution(640, 360).label)
    }

    @Test
    fun `equality works correctly`() {
        assertEquals(Resolution(1280, 720), Resolution(1280, 720))
        assertNotEquals(Resolution(1280, 720), Resolution(1920, 1080))
    }

    @Test
    fun `different width same height are not equal`() {
        assertNotEquals(Resolution(1280, 720), Resolution(1000, 720))
    }

    @Test
    fun `copy produces equal resolution`() {
        val original = Resolution(1280, 720)
        val copy = original.copy()
        assertEquals(original, copy)
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val a = Resolution(1920, 1080)
        val b = Resolution(1920, 1080)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `can be used as map key`() {
        val map = mapOf(
            Resolution(1280, 720) to "720p",
            Resolution(1920, 1080) to "1080p"
        )
        assertEquals("720p", map[Resolution(1280, 720)])
        assertEquals("1080p", map[Resolution(1920, 1080)])
        assertNull(map[Resolution(640, 360)])
    }
}
