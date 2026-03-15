package com.port80.app.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for StreamConfig default values.
 * Ensures all defaults match the specification.
 */
class StreamConfigTest {

    @Test
    fun `default resolution is 720p`() {
        val config = StreamConfig(profileId = "test")
        assertEquals(1280, config.resolution.width)
        assertEquals(720, config.resolution.height)
    }

    @Test
    fun `default fps is 30`() {
        val config = StreamConfig(profileId = "test")
        assertEquals(30, config.fps)
    }

    @Test
    fun `default video bitrate is 2500 kbps`() {
        val config = StreamConfig(profileId = "test")
        assertEquals(2500, config.videoBitrateKbps)
    }

    @Test
    fun `default audio bitrate is 128 kbps`() {
        val config = StreamConfig(profileId = "test")
        assertEquals(128, config.audioBitrateKbps)
    }

    @Test
    fun `default sample rate is 44100`() {
        val config = StreamConfig(profileId = "test")
        assertEquals(44100, config.audioSampleRate)
    }

    @Test
    fun `default stereo is true`() {
        val config = StreamConfig(profileId = "test")
        assertTrue(config.stereo)
    }

    @Test
    fun `default keyframe interval is 2 seconds`() {
        val config = StreamConfig(profileId = "test")
        assertEquals(2, config.keyframeIntervalSec)
    }

    @Test
    fun `ABR is enabled by default`() {
        val config = StreamConfig(profileId = "test")
        assertTrue(config.abrEnabled)
    }

    @Test
    fun `local recording is disabled by default`() {
        val config = StreamConfig(profileId = "test")
        assertFalse(config.localRecordingEnabled)
    }

    @Test
    fun `video and audio are enabled by default`() {
        val config = StreamConfig(profileId = "test")
        assertTrue(config.videoEnabled)
        assertTrue(config.audioEnabled)
    }

    @Test
    fun `custom values override defaults`() {
        val config = StreamConfig(
            profileId = "custom",
            videoEnabled = false,
            audioEnabled = true,
            resolution = Resolution(1920, 1080),
            fps = 60,
            videoBitrateKbps = 6000,
            audioBitrateKbps = 192,
            audioSampleRate = 48000,
            stereo = false,
            keyframeIntervalSec = 1,
            abrEnabled = false,
            localRecordingEnabled = true
        )
        assertFalse(config.videoEnabled)
        assertEquals(Resolution(1920, 1080), config.resolution)
        assertEquals(60, config.fps)
        assertEquals(6000, config.videoBitrateKbps)
        assertEquals(192, config.audioBitrateKbps)
        assertEquals(48000, config.audioSampleRate)
        assertFalse(config.stereo)
        assertEquals(1, config.keyframeIntervalSec)
        assertFalse(config.abrEnabled)
        assertTrue(config.localRecordingEnabled)
    }

    @Test
    fun `data class equality works`() {
        val a = StreamConfig(profileId = "p1")
        val b = StreamConfig(profileId = "p1")
        assertEquals(a, b)
    }

    @Test
    fun `data class copy preserves profileId`() {
        val original = StreamConfig(profileId = "p1")
        val modified = original.copy(fps = 60)
        assertEquals("p1", modified.profileId)
        assertEquals(60, modified.fps)
        assertEquals(original.resolution, modified.resolution)
    }
}
