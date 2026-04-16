package com.port80.app.service

import com.port80.app.data.model.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFailureMapperTest {

    @Test
    fun `mapFailureReason detects AUTH errors`() {
        assertEquals(StopReason.ERROR_AUTH, StreamFailureMapper.mapFailureReason("Auth failed"))
        assertEquals(StopReason.ERROR_AUTH, StreamFailureMapper.mapFailureReason("Authentication rejected"))
        assertEquals(StopReason.ERROR_AUTH, StreamFailureMapper.mapFailureReason("Stream key invalid (auth)"))
    }

    @Test
    fun `mapFailureReason detects AUDIO errors`() {
        assertEquals(StopReason.ERROR_AUDIO, StreamFailureMapper.mapFailureReason("Audio frame unavailable"))
        assertEquals(StopReason.ERROR_AUDIO, StreamFailureMapper.mapFailureReason("Microphone audio error"))
    }

    @Test
    fun `mapFailureReason detects CAMERA errors`() {
        assertEquals(StopReason.ERROR_CAMERA, StreamFailureMapper.mapFailureReason("Camera disconnected"))
        assertEquals(StopReason.ERROR_CAMERA, StreamFailureMapper.mapFailureReason("Preview surface invalid"))
    }

    @Test
    fun `mapFailureReason defaults to ENCODER error`() {
        assertEquals(StopReason.ERROR_ENCODER, StreamFailureMapper.mapFailureReason("Unknown error"))
        assertEquals(StopReason.ERROR_ENCODER, StreamFailureMapper.mapFailureReason("Network unreachable")) // Network errors often manifest as connection failures which we treat as generic encoder stops or handle via retry logic, but simplified here
    }

    @Test
    fun `buildFailureDetail sanitizes credentials`() {
        // Mock CredentialSanitizer behavior (it's static/object, so we assume its logic holds or use a real string)
        // Since CredentialSanitizer is likely simple logic, we can test that the failure detail message
        // includes user-friendly text.
        val detail = StreamFailureMapper.buildFailureDetail("rtmp://secret:key@host/app/str")
        // We expect generic message, not the URL
        assert(detail.contains("Could not connect"))
        assert(!detail.contains("secret:key"))
    }

    @Test
    fun `isRetryable returns false for auth errors`() {
        assertFalse(StreamFailureMapper.isRetryable("Auth failed"))
        assertFalse(StreamFailureMapper.isRetryable("Authentication rejected"))
    }

    @Test
    fun `isRetryable returns false for camera errors`() {
        assertFalse(StreamFailureMapper.isRetryable("Camera disconnected"))
        assertFalse(StreamFailureMapper.isRetryable("Preview surface invalid"))
    }

    @Test
    fun `isRetryable returns false for audio errors`() {
        assertFalse(StreamFailureMapper.isRetryable("Audio frame unavailable"))
        assertFalse(StreamFailureMapper.isRetryable("Microphone audio error"))
    }

    @Test
    fun `isRetryable returns false for encoder prep and camera init`() {
        assertFalse(StreamFailureMapper.isRetryable("ENCODER_PREP_FAILED"))
        assertFalse(StreamFailureMapper.isRetryable("CAMERA_NOT_INITIALIZED"))
        assertFalse(StreamFailureMapper.isRetryable("Malformed URL"))
    }

    @Test
    fun `isRetryable returns true for network errors`() {
        assertTrue(StreamFailureMapper.isRetryable("Connection refused"))
        assertTrue(StreamFailureMapper.isRetryable("Network unreachable"))
        assertTrue(StreamFailureMapper.isRetryable("java.net.SocketTimeoutException"))
        assertTrue(StreamFailureMapper.isRetryable("Unknown error"))
    }
}
