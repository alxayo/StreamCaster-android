package com.port80.app.service

import com.port80.app.util.RedactingLogger
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for MediaModeTransition.
 * Verifies mid-stream transitions between video+audio and audio-only modes.
 */
class MediaModeTransitionTest {

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
    fun `initial mode is VIDEO_AND_AUDIO`() {
        val transition = MediaModeTransition(StubEncoderBridge())
        assertEquals(MediaModeTransition.MediaMode.VIDEO_AND_AUDIO, transition.currentMode)
    }

    @Test
    fun `transition to audio-only changes mode`() {
        val transition = MediaModeTransition(StubEncoderBridge())
        transition.transitionToAudioOnly()
        assertEquals(MediaModeTransition.MediaMode.AUDIO_ONLY, transition.currentMode)
    }

    @Test
    fun `transition to audio-only is idempotent`() {
        val transition = MediaModeTransition(StubEncoderBridge())
        transition.transitionToAudioOnly()
        transition.transitionToAudioOnly() // Should not throw or change state
        assertEquals(MediaModeTransition.MediaMode.AUDIO_ONLY, transition.currentMode)
    }

    @Test
    fun `transition back to video-and-audio works`() {
        val transition = MediaModeTransition(StubEncoderBridge())
        transition.transitionToAudioOnly()
        val success = transition.transitionToVideoAndAudio()
        assertTrue(success)
        assertEquals(MediaModeTransition.MediaMode.VIDEO_AND_AUDIO, transition.currentMode)
    }

    @Test
    fun `transition to video-and-audio when already in that mode returns true`() {
        val transition = MediaModeTransition(StubEncoderBridge())
        // Already in VIDEO_AND_AUDIO
        val success = transition.transitionToVideoAndAudio()
        assertTrue(success)
        assertEquals(MediaModeTransition.MediaMode.VIDEO_AND_AUDIO, transition.currentMode)
    }

    @Test
    fun `transition to audio-only calls stopPreview on encoder`() {
        val encoder = mockk<EncoderBridge>(relaxed = true)
        val transition = MediaModeTransition(encoder)
        transition.transitionToAudioOnly()
        verify(exactly = 1) { encoder.stopPreview() }
    }

    @Test
    fun `idempotent audio-only does not call stopPreview twice`() {
        val encoder = mockk<EncoderBridge>(relaxed = true)
        val transition = MediaModeTransition(encoder)
        transition.transitionToAudioOnly()
        transition.transitionToAudioOnly()
        verify(exactly = 1) { encoder.stopPreview() }
    }

    @Test
    fun `round-trip transition works`() {
        val transition = MediaModeTransition(StubEncoderBridge())
        assertEquals(MediaModeTransition.MediaMode.VIDEO_AND_AUDIO, transition.currentMode)

        transition.transitionToAudioOnly()
        assertEquals(MediaModeTransition.MediaMode.AUDIO_ONLY, transition.currentMode)

        transition.transitionToVideoAndAudio()
        assertEquals(MediaModeTransition.MediaMode.VIDEO_AND_AUDIO, transition.currentMode)

        transition.transitionToAudioOnly()
        assertEquals(MediaModeTransition.MediaMode.AUDIO_ONLY, transition.currentMode)
    }
}
