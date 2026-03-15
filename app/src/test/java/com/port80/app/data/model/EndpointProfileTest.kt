package com.port80.app.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for EndpointProfile data class.
 * Verifies defaults and security-related properties.
 */
class EndpointProfileTest {

    @Test
    fun `optional fields default to null`() {
        val profile = EndpointProfile(
            id = "test-id",
            name = "Test",
            rtmpUrl = "rtmp://host/app",
            streamKey = "key123"
        )
        assertNull(profile.username)
        assertNull(profile.password)
        assertFalse(profile.isDefault)
    }

    @Test
    fun `profile with all fields`() {
        val profile = EndpointProfile(
            id = "id-1",
            name = "YouTube",
            rtmpUrl = "rtmps://a.rtmp.youtube.com/live2",
            streamKey = "xxxx-xxxx-xxxx",
            username = "user",
            password = "pass",
            isDefault = true
        )
        assertEquals("YouTube", profile.name)
        assertEquals("rtmps://a.rtmp.youtube.com/live2", profile.rtmpUrl)
        assertEquals("xxxx-xxxx-xxxx", profile.streamKey)
        assertEquals("user", profile.username)
        assertEquals("pass", profile.password)
        assertTrue(profile.isDefault)
    }

    @Test
    fun `toString contains streamKey - must use CredentialSanitizer before logging`() {
        val profile = EndpointProfile(
            id = "id-1",
            name = "Test",
            rtmpUrl = "rtmp://host/app",
            streamKey = "super_secret_key_12345"
        )
        // data class toString includes all fields — documents the risk
        val str = profile.toString()
        assertTrue(
            "toString includes streamKey (use CredentialSanitizer before logging!)",
            str.contains("super_secret_key_12345")
        )
    }

    @Test
    fun `toString contains password if set - must sanitize before logging`() {
        val profile = EndpointProfile(
            id = "id-1",
            name = "Test",
            rtmpUrl = "rtmp://host/app",
            streamKey = "key",
            password = "my_secret_pass"
        )
        val str = profile.toString()
        assertTrue(
            "toString includes password (use CredentialSanitizer before logging!)",
            str.contains("my_secret_pass")
        )
    }

    @Test
    fun `equality is based on all fields`() {
        val a = EndpointProfile("id", "Name", "rtmp://a", "key1")
        val b = EndpointProfile("id", "Name", "rtmp://a", "key1")
        assertEquals(a, b)
    }

    @Test
    fun `different ids are not equal`() {
        val a = EndpointProfile("id-1", "Name", "rtmp://a", "key1")
        val b = EndpointProfile("id-2", "Name", "rtmp://a", "key1")
        assertNotEquals(a, b)
    }

    @Test
    fun `different stream keys are not equal`() {
        val a = EndpointProfile("id", "Name", "rtmp://a", "key1")
        val b = EndpointProfile("id", "Name", "rtmp://a", "key2")
        assertNotEquals(a, b)
    }

    @Test
    fun `copy can change isDefault`() {
        val original = EndpointProfile("id", "Name", "rtmp://a", "key", isDefault = false)
        val updated = original.copy(isDefault = true)
        assertTrue(updated.isDefault)
        assertEquals(original.id, updated.id)
        assertEquals(original.streamKey, updated.streamKey)
    }

    @Test
    fun `id field is preserved`() {
        val profile = EndpointProfile(
            id = "550e8400-e29b-41d4-a716-446655440000",
            name = "Test",
            rtmpUrl = "rtmp://host/app",
            streamKey = "key"
        )
        assertEquals("550e8400-e29b-41d4-a716-446655440000", profile.id)
    }

    @Test
    fun `rtmpUrl accepts both rtmp and rtmps schemes`() {
        val rtmp = EndpointProfile("1", "RTMP", "rtmp://host/app", "key")
        val rtmps = EndpointProfile("2", "RTMPS", "rtmps://host/app", "key")
        assertTrue(rtmp.rtmpUrl.startsWith("rtmp://"))
        assertTrue(rtmps.rtmpUrl.startsWith("rtmps://"))
    }
}
