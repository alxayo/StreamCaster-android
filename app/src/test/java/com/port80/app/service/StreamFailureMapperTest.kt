package com.port80.app.service

import com.port80.app.data.model.StopReason
import org.junit.Assert.assertEquals
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
}
